import BackgroundTasks
import Foundation
import NMCore
import SwiftUI
import UserNotifications
import WidgetKit
#if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import ActivityKit
import AlarmKit
import AppIntents
#endif

/// Local prayer reminders. iOS allows 64 pending notifications per app: the next day's alerts, then the Adhan
/// (or the alert at prayer time) as far ahead as 14 days, then the rest (AlertPlanner.budget). They are rebuilt
/// when the app comes to the screen (at most hourly), at midnight, when settings change, or when iOS gives the app a
/// background refresh. The Adhan and the spoken reminders are notification sounds (iOS plays at most 30 seconds);
/// opening an Adhan notification plays the whole Adhan in the app. On iPhone and iPad with iOS 26 and alarms allowed,
/// the Adhan and the Fajr alarm are alarms instead (PrayerAlarms: they ring until stopped, even on silent), each with a
/// silent notification at the same moment that carries the buttons; earlier iOS, the Mac and Vision Pro get
/// notifications.
enum Reminders {
    /// Prayer alerts; the four repeating reminders (Al-Kahf, Salli Ala Rasulillah, Qur'an, bedtime) take the rest.
    static let maxPending = 60

    /// Notification categories: the buttons under each kind of alert.
    /// The `Silence` ones add "Silence until after the prayer" (iPhone, with silence during prayer on).
    enum Category: String { case prayer, before, verse, prayerSilence, iqamaSilence }
    enum Action: String { case prayed, snooze, qibla, mosques, silence }

    /// Registers the buttons in the current language (again whenever the language changes).
    static func registerCategories(_ l10n: Localization) {
        let prayed = UNNotificationAction(identifier: Action.prayed.rawValue, title: l10n.t("notif_prayed"), options: [],
                                          icon: UNNotificationActionIcon(systemImageName: "checkmark.circle"))
        let snooze = UNNotificationAction(identifier: Action.snooze.rawValue, title: l10n.t("notif_snooze"), options: [],
                                          icon: UNNotificationActionIcon(systemImageName: "clock.arrow.circlepath"))
        let qibla = UNNotificationAction(identifier: Action.qibla.rawValue, title: l10n.t("qibla"), options: [.foreground],
                                         icon: UNNotificationActionIcon(systemImageName: "location.north.line"))
        let mosques = UNNotificationAction(identifier: Action.mosques.rawValue, title: l10n.t("tab_mosques"), options: [.foreground],
                                           icon: UNNotificationActionIcon(systemImageName: "building.columns"))
        var categories: Set<UNNotificationCategory> = [
            UNNotificationCategory(identifier: Category.prayer.rawValue, actions: [prayed, snooze, qibla, mosques], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.before.rawValue, actions: [qibla, mosques], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.verse.rawValue, actions: [], intentIdentifiers: []),
        ]
        if SilenceShortcutRunner.supported {
            // Opens the app, which runs the user's Shortcut (the silence button first, so it is never cut off).
            let silence = UNNotificationAction(identifier: Action.silence.rawValue, title: l10n.t("silence_now"), options: [.foreground],
                                               icon: UNNotificationActionIcon(systemImageName: "moon.fill"))
            categories.insert(UNNotificationCategory(identifier: Category.prayerSilence.rawValue, actions: [silence, prayed, snooze, qibla, mosques], intentIdentifiers: []))
            categories.insert(UNNotificationCategory(identifier: Category.iqamaSilence.rawValue, actions: [silence, qibla, mosques], intentIdentifiers: []))
        }
        UNUserNotificationCenter.current().setNotificationCategories(categories)
    }

    static func authorized() async -> Bool {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        return s.authorizationStatus == .authorized || s.authorizationStatus == .provisional
    }

    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Kinds that repeat on their own (calendar triggers), so they are not taken from the plan.
    private static let repeating: Set<AlertKind> = [.salawat, .quran, .sleep, .kahf]

    /// Rebuilds the alarms and the app's notifications; returns the number of notifications scheduled. AppModel runs
    /// one rebuild at a time. Everything that waits comes first; the pending notifications are then replaced in one
    /// go with nothing to wait for in between, so a rebuild cut short leaves the old ones, and a snooze is kept.
    @discardableResult
    static func reschedule(location: PrayerLocation?, settings: PrayerSettings, enabled: Set<PrayerEvent>, alerts: AlertSettings,
                           iqama: IqamaSettings = IqamaSettings(enabled: false), silence: SilenceSettings = SilenceSettings(), l10n: Localization,
                           mosqueLine: String? = nil, verse: (String) -> String? = { _ in nil }, now: Date = Date()) async -> Int {
        let center = UNUserNotificationCenter.current()
        // The silence button runs a Shortcut: only where the Shortcuts app opens it.
        let silence = SilenceShortcutRunner.supported ? silence : SilenceSettings()
        registerCategories(l10n)
        let pending = await center.pendingNotificationRequests().map(\.identifier)
        let old = pending.filter { $0.hasPrefix("alert-") || $0.hasPrefix("verse-") }
        let room = maxPending - pending.filter { $0.hasPrefix("snooze-") }.count
        guard let loc = location else {
            _ = await PrayerAlarms.reschedule([], alerts: alerts, silence: silence, iqama: iqama, l10n: l10n, now: now)
            center.removePendingNotificationRequests(withIdentifiers: old)
            return 0
        }
        let calc = PrayerCalculator()
        let today = CivilDate.of(now, in: loc.zone)
        let days = (0..<14).map { calc.schedule(loc.location, date: today.adding(days: $0), zone: loc.zone, settings: settings) }
        // Marked as prayed today (a notification's Prayed, the app or the widget): what is left of that prayer, such
        // as its iqama, stays cancelled.
        let prayed = Set(PrayerEvent.prayers.filter { CompanionLog.checkedIn($0.rawValue, on: today) })
        let plan = AlertPlanner.plan(days, now: now, atPrayer: enabled, alerts: alerts, iqama: iqama) {
            HijriCalendar.isRamadan($0, adjustmentDays: settings.hijriAdjustmentDays)
        }.filter { a in
            !repeating.contains(a.kind) && !(a.date == today && prayed.contains(a.event) && !AlertPlanner.firstKinds.contains(a.kind))
        }
        // Cut short (the background refresh ran out of time): the alerts and alarms stay as they are.
        if Task.isCancelled { return 0 }
        // Alarms need no notification permission; an alert that got one is at most its silent notification (below).
        let alarmed = await PrayerAlarms.reschedule(plan, alerts: alerts, silence: silence, iqama: iqama, l10n: l10n, now: now)
        let alarmedAdhans = plan.filter { $0.kind == .adhan && alarmed.contains($0.identifier) }
        // An alarm has no Prayed or Remind me of its own: a silent notification at the same moment carries the Adhan
        // notification's buttons, and opened it carries the whole Adhan on in the app. One per alarm (a Fajr alarm
        // that rings as the Fajr Adhan is one alarm).
        let companions = Set(plan.filter { a in
            alarmed.contains(a.identifier) && (a.kind == .adhan || (a.kind == .fajrAlarm && !alarmedAdhans.contains { $0.event == a.event && $0.at == a.at }))
        }.map(\.identifier))
        guard await authorized() else {
            center.removePendingNotificationRequests(withIdentifiers: old)
            return 0
        }
        let voice = alerts.voice ?? VoiceLanguage.forInterface(l10n.language)
        var built: [(PlannedAlert, UNMutableNotificationContent)] = []
        // The next day's silent notifications count with the sounding alerts; later ones only take the room those leave,
        // so a button never pushes out an alert that makes a sound.
        let edge = now.addingTimeInterval(24 * 3600)
        let first = AlertPlanner.budget(plan.filter { !alarmed.contains($0.identifier) || (companions.contains($0.identifier) && $0.at <= edge) },
                                        limit: room, now: now)
        let later = plan.filter { companions.contains($0.identifier) && $0.at > edge }.sorted { $0.at < $1.at }.prefix(max(0, room - first.count))
        for a in first + Array(later) {
            let content = UNMutableNotificationContent()
            let name = l10n.t(Format.prayerKey(a.event))
            let prayerAt = days.first { $0.date == a.date }?[a.event] ?? a.at
            let time = Format.time(prayerAt, zone: loc.zone, locale: l10n.locale)
            content.threadIdentifier = "prayers-\(a.date)"
            // The time and the sound, for "Check my alerts" (iOS gives back neither).
            content.userInfo = ["event": a.event.rawValue, "date": "\(a.date)", "kind": a.kind.rawValue, "at": a.at.timeIntervalSince1970, "sound": "default"]
            content.sound = .default
            switch a.kind {
            case .atPrayer where a.event == .sunrise:
                content.title = l10n.t("notif_sunrise_title")
                content.body = l10n.t("reminder_body", loc.name, time)
                content.categoryIdentifier = Category.before.rawValue
            case .atPrayer, .fajrAlarm, .adhan:
                content.title = l10n.t("reminder_title", name)
                content.body = l10n.t("reminder_body", loc.name, time)
                content.categoryIdentifier = Category.prayer.rawValue
                if silence.offersAction(a.kind, event: a.event, date: a.date, iqama: iqama) { content.categoryIdentifier = Category.prayerSilence.rawValue }
                content.relevanceScore = 1
                // Through a Focus (Sleep at Fajr) and the Scheduled Summary, where the user lets Time Sensitive through.
                content.interruptionLevel = .timeSensitive
                if a.kind == .adhan {
                    let file = AudioCatalog.adhan(a.event, alerts)
                    content.sound = AudioCatalog.sound(file)
                    content.userInfo["adhan"] = file
                    content.userInfo["source"] = (a.event == .fajr ? alerts.fajrAdhanSource : alerts.adhanSource).rawValue
                    content.userInfo["sound"] = AudioCatalog.available(file) ?? "default"
                }
            case .before, .before10, .before5, .near:
                let minutes = Int((prayerAt.timeIntervalSince(a.at) / 60).rounded())
                content.title = a.kind == .near ? l10n.t("notif_near_title", name) : l10n.t("notif_minutes_until", minutes, name)
                content.body = l10n.t("reminder_body", loc.name, time)
                if let mosqueLine { content.subtitle = mosqueLine }
                content.categoryIdentifier = Category.before.rawValue
                content.relevanceScore = 0.6
                content.interruptionLevel = .timeSensitive
                let file = AudioCatalog.voice(a.kind, a.event, voice)
                content.sound = AudioCatalog.sound(file)
                content.userInfo["sound"] = AudioCatalog.available(file) ?? "default"
            case .friday:
                content.title = l10n.t("reminder_friday_title")
                content.body = l10n.t("reminder_friday_body", time)
            case .suhoor:
                content.title = l10n.t("reminder_suhoor_title")
                content.body = l10n.t("reminder_suhoor_body", time)
            case .iftar:
                content.title = l10n.t("reminder_iftar_title")
                content.body = l10n.t("reminder_body", loc.name, time)
            case .iqama:
                // A plain sound: the Adhan has already been called.
                content.title = l10n.t("notif_iqama_title", name)
                content.body = l10n.t("notif_iqama_body")
                content.subtitle = l10n.t("iqama_at", Format.time(a.at, zone: loc.zone, locale: l10n.locale))
                // After an Adhan alarm: Silence again where it began at the Adhan (the alarm may have been stopped).
                let rang = alarmedAdhans.contains { $0.event == a.event && $0.date == a.date }
                let silences = silence.offersAction(a.kind, event: a.event, date: a.date, iqama: iqama)
                    || (rang && silence.offersAction(.adhan, event: a.event, date: a.date, iqama: iqama))
                content.categoryIdentifier = (silences ? Category.iqamaSilence : .before).rawValue
                content.relevanceScore = 0.8
                // Not through the user's own silence when it has begun by then (from the Adhan, and on Friday).
                let silenced = silence.enabled && silence.includes(a.event) && (silence.start == .adhan || (a.event == .dhuhr && a.date.isFriday))
                content.interruptionLevel = silenced ? .active : .timeSensitive
            case .salawat, .quran, .sleep, .kahf:
                continue
            }
            if companions.contains(a.identifier) {
                // The alarm makes the sound; still Time Sensitive, so its buttons are there through a Focus.
                content.sound = nil
                content.userInfo["alarm"] = true
            }
            built.append((a, content))
        }
        let verses = verseRequests(alerts: alerts, zone: loc.zone, l10n: l10n, verse: verse)
        // From here on nothing waits.
        center.removePendingNotificationRequests(withIdentifiers: old)
        for (a, content) in built {
            // Counted from now, so no alert fires late by the time the rebuild took.
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, a.at.timeIntervalSinceNow), repeats: false)
            center.add(UNNotificationRequest(identifier: a.identifier, content: content, trigger: trigger)) { _ in }
        }
        for r in verses { center.add(r) { _ in } }
        return built.count + verses.count
    }

    /// The repeating reminders, each with its own switch: Friday Al-Kahf (9:00), Friday Salli Ala Rasulillah
    /// (Al-Ahzab 33:56 recited), the daily Qur'an reminder and the bedtime verse (Ar-Ra'd 13:28 recited). Verse
    /// text comes from the Qur'an on the phone, always with its reference.
    private static func verseRequests(alerts: AlertSettings, zone: TimeZone, l10n: Localization, verse: (String) -> String?) -> [UNNotificationRequest] {
        var out: [UNNotificationRequest] = []
        func text(_ ids: [String], _ ref: String) -> String? {
            let parts = ids.compactMap(verse)
            guard !parts.isEmpty else { return nil }
            return parts.joined(separator: " ") + " (" + ref + ")"
        }
        func add(_ id: String, title: String, body: String, sound: String?, minutes: Int, weekday: Int?) {
            let c = UNMutableNotificationContent()
            c.title = title
            c.body = body
            c.sound = AudioCatalog.sound(sound)
            c.userInfo = ["sound": AudioCatalog.available(sound) ?? "default"]
            c.categoryIdentifier = Category.verse.rawValue
            c.threadIdentifier = "verses"
            var when = DateComponents(hour: minutes / 60, minute: minutes % 60)
            when.weekday = weekday
            when.timeZone = zone
            out.append(UNNotificationRequest(identifier: id, content: c, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)))
        }
        let ref = { (n: Int, a: Int) in l10n.t("reference_quran", n, a) }
        if alerts.kahf, let body = text(["quran:18:1"], ref(18, 1)) {
            add("verse-kahf", title: l10n.t("notif_kahf_title"), body: body, sound: nil, minutes: AlertSettings.kahfHour * 60, weekday: 6)
        }
        if alerts.salawat {
            add("verse-salawat", title: l10n.t("notif_salawat_title"), body: text(["quran:33:56"], ref(33, 56)) ?? l10n.t("salawat_note"),
                sound: AudioCatalog.salawat(alerts.reciter), minutes: alerts.salawatMinutes, weekday: 6)
        }
        let evening = text(["quran:13:28"], ref(13, 28))
        if alerts.quran {
            add("verse-quran", title: l10n.t("notif_quran_title"), body: l10n.t("notif_quran_body") + (evening.map { "\n" + $0 } ?? ""),
                sound: AudioCatalog.evening(alerts.reciter), minutes: alerts.quranMinutes, weekday: nil)
        }
        if let minutes = alerts.bedtimeMinutes {
            add("verse-sleep", title: l10n.t("notif_sleep_title"), body: evening ?? l10n.t("alerts_sleep_note"),
                sound: AudioCatalog.evening(alerts.reciter), minutes: minutes, weekday: nil)
        }
        return out
    }

    /// "Remind me in 5 minutes": the same alert again, once, five minutes later.
    static func snooze(_ content: UNNotificationContent) async {
        guard let copy = content.mutableCopy() as? UNMutableNotificationContent else { return }
        copy.userInfo["at"] = Date().addingTimeInterval(5 * 60).timeIntervalSince1970
        if copy.userInfo["alarm"] as? Bool == true {
            // Its alarm made the sound; the reminder five minutes later makes its own.
            copy.userInfo["alarm"] = nil
            copy.sound = AudioCatalog.sound(copy.userInfo["adhan"] as? String)
            copy.interruptionLevel = .timeSensitive
        }
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(
            identifier: "snooze-\(UUID().uuidString)", content: copy, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5 * 60, repeats: false)))
    }

    /// "Prayed": that prayer's remaining alerts today are cancelled, and the companion (the lantern or the heart)
    /// is told. Nothing leaves the phone.
    static func markPrayed(event: String, date: String, delivered: Date) {
        let center = UNUserNotificationCenter.current()
        let ids = AlertKind.allIdentifiers.map { "alert-\($0)-\(date)-\(event)" }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
        CompanionLog.checkIn(event, on: CivilDate.of(delivered, in: SharedStore.read()?.zone ?? .current))
        WidgetCenter.shared.reloadAllTimelines()
    }
}

extension AlertKind {
    static let allIdentifiers = [AlertKind.atPrayer, .before, .friday, .suhoor, .iftar, .fajrAlarm, .adhan, .before10, .before5, .near, .iqama].map(\.rawValue)
}

extension PlannedAlert {
    /// Its notification's identifier, the same on every rebuild.
    var identifier: String { "alert-\(kind.rawValue)-\(date)-\(event.rawValue)" }
}

/// Taps and buttons on the app's notifications. The completion-handler forms, each handler called on the main
/// thread: with the async forms the system's completion ran on a background thread, and iOS ended the app when
/// a notification was opened (TestFlight build 140 on iOS 27).
final class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationHandler()

    /// With the app open at prayer time, the whole Adhan plays in the app instead of the 30-second sound.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let info = notification.request.content.userInfo
        Task { @MainActor in
            if info["alarm"] as? Bool == true {
                // An alarm's silent notification: the alarm is ringing.
                completionHandler([.banner, .list])
            } else if let file = info["adhan"] as? String {
                AdhanPlayer.shared.play(file, title: Self.adhanTitle(info["source"] as? String), adhan: true)
                completionHandler([.banner, .list])
            } else {
                completionHandler([.banner, .list, .sound])
            }
        }
    }

    /// The playing bar's title, from the AdhanSource raw value.
    @MainActor static func adhanTitle(_ raw: String?) -> String {
        let l10n = AppModel.shared.l10n
        let source = AdhanSource(rawValue: raw ?? "") ?? .makkah
        return l10n.t("adhan_playing", l10n.t(source == .makkah ? "source_makkah" : "source_madinah"))
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let content = response.notification.request.content
        let event = content.userInfo["event"] as? String ?? ""
        let date = content.userInfo["date"] as? String ?? ""
        let delivered = response.notification.date
        let action = Reminders.Action(rawValue: response.actionIdentifier)
        Task { @MainActor in
            // A button on an alarm's silent notification also stops that alarm, if it is still ringing.
            if content.userInfo["alarm"] as? Bool == true, let at = content.userInfo["at"] as? Double {
                PrayerAlarms.stopRinging(at: Date(timeIntervalSince1970: at))
            }
            switch action {
            case .prayed: Reminders.markPrayed(event: event, date: date, delivered: delivered)
            case .snooze: await Reminders.snooze(content)
            case .qibla: PendingRoute.open(URL(string: "nearmosque://qibla")!)
            case .mosques: PendingRoute.open(URL(string: "nearmosque://mosques")!)
            case .silence:
                // Too late for this prayer's silence: only the prayer screen, as a tap on the notification opens
                // (without carrying the Adhan on).
                if !SilenceShortcutRunner.runFromNotification(event: event, date: date) { PendingRoute.open(URL(string: "nearmosque://prayer")!) }
            case nil:
                // Opening an Adhan notification carries the Adhan on in the app, from where it would be now.
                if let file = content.userInfo["adhan"] as? String {
                    AdhanPlayer.shared.play(file, title: Self.adhanTitle(content.userInfo["source"] as? String), adhan: true, offset: Date().timeIntervalSince(delivered))
                }
                PendingRoute.open(URL(string: "nearmosque://prayer")!)
            }
            // The Lock Screen countdown moves on too (a button may have woken the app only for this).
            await LiveCountdown.refresh(AppModel.shared)
            completionHandler()
        }
    }
}

/// The Adhan and the Fajr alarm through AlarmKit (iPhone and iPad, iOS 26): each prayer with the Adhan on rings as an
/// alarm playing its Adhan, even on silent, with Stop and one more button: Silence where the silence during prayer
/// begins at that Adhan, else the whole Adhan in the app (the alarm plays the first 30 seconds); the Fajr alarm rings
/// with the alarm tone. They cover the next few days and are replaced on every rebuild; later prayers keep their
/// notification until a rebuild moves the days on. Alarms not allowed, the Mac, Vision Pro and iOS before 26:
/// notifications only.
enum PrayerAlarms {
    enum Permission { case unavailable, notDetermined, denied, authorized }

    /// Days ahead with an alarm for each Adhan, and for the Fajr alarm (a week, as before).
    static let adhanDays = 4
    static let fajrAlarmDays = 7
    private static let key = "prayerAlarms"
    /// Builds 147 and earlier kept their Fajr alarms' ids here.
    private static let oldKey = "fajrAlarmIds"

    /// One alarm set, for "Check my alerts" (AlarmKit gives back only its time).
    struct Record: Codable {
        let id: String
        let at: Date
        let event: String
        /// AlertKind raw value: the Adhan, or the Fajr alarm.
        let kind: Int
        /// The sound file, or "alarm" for the alarm tone.
        let sound: String
    }

    static var permission: Permission {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if #available(iOS 26.0, *) {
            switch AlarmManager.shared.authorizationState {
            case .notDetermined: return .notDetermined
            case .authorized: return .authorized
            case .denied: return .denied
            @unknown default: return .denied
            }
        }
        #endif
        return .unavailable
    }

    /// Asks where the system has not asked yet (it asks only once; the answer stays). True when allowed.
    @discardableResult
    static func requestAuthorization() async -> Bool {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if #available(iOS 26.0, *) {
            let manager = AlarmManager.shared
            guard manager.authorizationState == .notDetermined else { return manager.authorizationState == .authorized }
            return (try? await manager.requestAuthorization()) == .authorized
        }
        #endif
        return false
    }

    /// Replaces the app's alarms with one for each Adhan and Fajr alarm of `plan` in their days (a Fajr alarm at the
    /// Fajr time itself, with the Fajr Adhan on, is one alarm playing the Adhan). Returns the identifiers of the planned
    /// alerts that got an alarm: those become silent notifications with the buttons, the others sounding ones. Never
    /// asks (see requestAuthorization).
    static func reschedule(_ plan: [PlannedAlert], alerts: AlertSettings, silence: SilenceSettings, iqama: IqamaSettings,
                           l10n: Localization, now: Date) async -> Set<String> {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if #available(iOS 26.0, *) {
            let manager = AlarmManager.shared
            // Every alarm the app owns goes, also any left by an older build or a rebuild cut short; one ringing now stays.
            let all = (try? manager.alarms) ?? []
            let ringing = Set(all.filter { if case .alerting = $0.state { return true } else { return false } }.map(\.id))
            let noted = ((UserDefaults.standard.stringArray(forKey: oldKey) ?? []) + scheduled.map(\.id)).compactMap(UUID.init(uuidString:))
            for id in Set(all.map(\.id) + noted).subtracting(ringing) { try? manager.cancel(id: id) }
            UserDefaults.standard.removeObject(forKey: oldKey)
            guard manager.authorizationState == .authorized else {
                save([])
                return []
            }
            var covered: Set<String> = []
            // A ringing alarm stays, and so does its record (its silent notification's buttons stop it).
            var records = scheduled.filter { UUID(uuidString: $0.id).map(ringing.contains) ?? false }
            for a in plan where a.kind == .adhan || a.kind == .fajrAlarm {
                let days = a.kind == .adhan ? adhanDays : fajrAlarmDays
                guard !covered.contains(a.identifier), a.at <= now.addingTimeInterval(Double(days) * 86400) else { continue }
                let twin = plan.first { $0.at == a.at && $0.event == a.event && $0.kind != a.kind && ($0.kind == .adhan || $0.kind == .fajrAlarm) }
                let adhan = a.kind == .adhan || twin != nil
                let file = adhan ? AudioCatalog.available(AudioCatalog.adhan(a.event, alerts)) : nil
                let name = l10n.t(Format.prayerKey(a.event))
                let id = UUID()
                // What the Adhan notification's buttons did: Silence where the silence begins here, else the whole
                // Adhan in the app (not with the short Adhan, which the alarm plays whole). Prayed and Remind me are on
                // its silent notification.
                let silences = adhan && silence.offersAction(.adhan, event: a.event, date: a.date, iqama: iqama)
                let full = adhan && !silences && !alerts.shortAdhan
                let second: AlarmButton? = silences
                    ? AlarmButton(text: LocalizedStringResource(stringLiteral: l10n.t("alarm_silence")), textColor: .white, systemImageName: "moon.fill")
                    : full ? AlarmButton(text: LocalizedStringResource(stringLiteral: l10n.t("alarm_full_adhan")), textColor: .white,
                                         systemImageName: "speaker.wave.2.fill") : nil
                let openIntent: PrayerAlarmOpenIntent? = second == nil ? nil : PrayerAlarmOpenIntent(
                    alarmID: id.uuidString, event: a.event.rawValue, date: "\(a.date)", silence: silences, file: AudioCatalog.adhan(a.event, alerts),
                    source: (a.event == .fajr ? alerts.fajrAdhanSource : alerts.adhanSource).rawValue, at: a.at)
                let alert = AlarmPresentation.Alert(
                    title: LocalizedStringResource(stringLiteral: adhan ? l10n.t("reminder_title", name) : name),
                    stopButton: AlarmButton(text: LocalizedStringResource(stringLiteral: l10n.t("alarm_stop")), textColor: .white, systemImageName: "stop.circle"),
                    secondaryButton: second, secondaryButtonBehavior: second == nil ? nil : .custom)
                let attributes = AlarmAttributes<PrayerAlarmMetadata>(presentation: AlarmPresentation(alert: alert), tintColor: Color(hex: 0xD4A843))
                let stop = PrayerAlarmStopIntent(alarmID: id.uuidString)
                do {
                    if let file {
                        _ = try await manager.schedule(id: id, configuration: .alarm(schedule: .fixed(a.at), attributes: attributes, stopIntent: stop,
                                                                                     secondaryIntent: openIntent, sound: .named(file + ".caf")))
                    } else {
                        _ = try await manager.schedule(id: id, configuration: .alarm(schedule: .fixed(a.at), attributes: attributes, stopIntent: stop,
                                                                                     secondaryIntent: openIntent))
                    }
                } catch {
                    // Not set: this one stays a notification.
                    continue
                }
                covered.insert(a.identifier)
                if let twin { covered.insert(twin.identifier) }
                records.append(Record(id: id.uuidString, at: a.at, event: a.event.rawValue,
                                      kind: (adhan ? AlertKind.adhan : AlertKind.fajrAlarm).rawValue, sound: file ?? "alarm"))
            }
            save(records)
            return covered
        }
        #endif
        return []
    }

    /// Stops the app's alarm set for `at` if it is ringing now (never another prayer's).
    static func stopRinging(at: Date) {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if #available(iOS 26.0, *) {
            let ids = Set(scheduled.filter { abs($0.at.timeIntervalSince(at)) < 1 }.compactMap { UUID(uuidString: $0.id) })
            for alarm in (try? AlarmManager.shared.alarms) ?? [] where ids.contains(alarm.id) {
                if case .alerting = alarm.state { try? AlarmManager.shared.stop(id: alarm.id) }
            }
        }
        #endif
    }

    /// The alarms set that are still to ring, soonest first (for "Check my alerts").
    static func upcoming(after now: Date) -> [Record] {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        if #available(iOS 26.0, *) {
            let live = Set(((try? AlarmManager.shared.alarms) ?? []).map(\.id.uuidString))
            return scheduled.filter { $0.at > now && live.contains($0.id) }.sorted { $0.at < $1.at }
        }
        #endif
        return []
    }

    private static var scheduled: [Record] {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([Record].self, from: $0) } ?? []
    }

    private static func save(_ records: [Record]) {
        UserDefaults.standard.set(try? JSONEncoder().encode(records), forKey: key)
    }
}

#if canImport(AlarmKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
@available(iOS 26.0, *)
struct PrayerAlarmMetadata: AlarmMetadata {}

/// Stop on a prayer alarm: the alarm stops and the Lock Screen countdown moves on (runs in the app, in the background).
struct PrayerAlarmStopIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "alarm_stop"
    static var isDiscoverable = false

    @Parameter(title: "alarm_stop")
    var alarmID: String

    init(alarmID: String) {
        self.alarmID = alarmID
    }

    init() {
        self.alarmID = ""
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        if #available(iOS 26.0, *), let id = UUID(uuidString: alarmID) { try? AlarmManager.shared.stop(id: id) }
        await LiveCountdown.refresh(AppModel.shared)
        return .result()
    }
}

/// The prayer alarm's second button (opens the app): Silence runs the user's Shortcut as the notification button
/// does; otherwise the whole Adhan carries on in the app from where the alarm is now.
struct PrayerAlarmOpenIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "alarm_full_adhan"
    static var isDiscoverable = false
    static var openAppWhenRun = true

    @Parameter(title: "alarm_stop")
    var alarmID: String
    @Parameter(title: "alarm_stop")
    var event: String
    /// The prayer's day, as the notifications' userInfo writes it.
    @Parameter(title: "alarm_stop")
    var date: String
    @Parameter(title: "alarm_stop")
    var silence: Bool
    /// The Adhan's file and its AdhanSource raw value.
    @Parameter(title: "alarm_stop")
    var file: String
    @Parameter(title: "alarm_stop")
    var source: String
    @Parameter(title: "alarm_stop")
    var at: Date

    init(alarmID: String, event: String, date: String, silence: Bool, file: String, source: String, at: Date) {
        self.alarmID = alarmID
        self.event = event
        self.date = date
        self.silence = silence
        self.file = file
        self.source = source
        self.at = at
    }

    init() {
        self.init(alarmID: "", event: "", date: "", silence: false, file: "", source: "", at: Date())
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        if #available(iOS 26.0, *), let id = UUID(uuidString: alarmID) { try? AlarmManager.shared.stop(id: id) }
        if silence {
            // Too late for this prayer's silence: the prayer screen, as for the notification button.
            if !SilenceShortcutRunner.runFromNotification(event: event, date: date) { PendingRoute.open(URL(string: "nearmosque://prayer")!) }
        } else {
            AdhanPlayer.shared.play(file, title: NotificationHandler.adhanTitle(source), adhan: true, offset: Date().timeIntervalSince(at))
            PendingRoute.open(URL(string: "nearmosque://prayer")!)
        }
        await LiveCountdown.refresh(AppModel.shared)
        return .result()
    }
}
#endif

/// A background refresh about twice a day (when iOS allows) rebuilds the reminders, so they keep going when
/// the app has not been opened for days, and moves the Lock Screen countdown on. Nothing is downloaded.
enum ReminderRefresh {
    static let id = "sa.zood.nearmosque.reminders"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: id, using: nil) { task in
            submit()
            let work = Task { @MainActor in
                // Never rebuild from defaults: if the saved settings cannot be read now, leave the reminders as they are.
                if UserDefaults.standard.data(forKey: "settings") != nil {
                    await AppModel.shared.rescheduleReminders()
                    await LiveCountdown.refresh(AppModel.shared)
                }
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = {
                work.cancel()
                // A rebuild not yet committed then leaves the reminders as they were.
                Task { @MainActor in AppModel.shared.cancelRescheduling() }
            }
        }
    }

    static func submit() {
        let r = BGAppRefreshTaskRequest(identifier: id)
        r.earliestBeginDate = Date().addingTimeInterval(10 * 3600)
        try? BGTaskScheduler.shared.submit(r)
    }
}
