import BackgroundTasks
import Foundation
import NMCore
import SwiftUI
import UserNotifications
import WidgetKit
#if canImport(AlarmKit) && !targetEnvironment(macCatalyst)
import AlarmKit
#endif

/// Local prayer reminders. iOS allows 64 pending notifications per app, so the nearest alerts (in time
/// order, from up to 14 days) are scheduled and rebuilt whenever the app opens, settings change, or iOS gives
/// the app a background refresh. The Adhan and the spoken reminders are notification sounds (iOS plays at most
/// 30 seconds); opening an Adhan notification plays the whole Adhan in the app. The Fajr alarm uses AlarmKit on
/// iOS 26 (it rings until stopped, even on silent); earlier iOS and the Mac get a notification instead.
enum Reminders {
    /// Prayer alerts; the four repeating reminders (Al-Kahf, Salli Ala Rasulillah, Qur'an, bedtime) take the rest.
    static let maxPending = 60

    /// Notification categories: the buttons under each kind of alert.
    enum Category: String { case prayer, before, verse }
    enum Action: String { case prayed, snooze, qibla, mosques }

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
        UNUserNotificationCenter.current().setNotificationCategories([
            UNNotificationCategory(identifier: Category.prayer.rawValue, actions: [prayed, snooze, qibla, mosques], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.before.rawValue, actions: [qibla, mosques], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.verse.rawValue, actions: [], intentIdentifiers: []),
        ])
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

    /// Returns the number of notifications scheduled.
    @discardableResult
    static func reschedule(location: PrayerLocation?, settings: PrayerSettings, enabled: Set<PrayerEvent>, alerts: AlertSettings,
                           l10n: Localization, mosqueLine: String? = nil, verse: (String) -> String? = { _ in nil },
                           now: Date = Date()) async -> Int {
        let center = UNUserNotificationCenter.current()
        registerCategories(l10n)
        center.removeAllPendingNotificationRequests()
        guard let loc = location else { await FajrAlarms.reschedule([], l10n: l10n); return 0 }
        let calc = PrayerCalculator()
        let today = CivilDate.of(now, in: loc.zone)
        let days = (0..<14).map { calc.schedule(loc.location, date: today.adding(days: $0), zone: loc.zone, settings: settings) }
        let plan = AlertPlanner.plan(days, now: now, atPrayer: enabled, alerts: alerts) {
            HijriCalendar.isRamadan($0, adjustmentDays: settings.hijriAdjustmentDays)
        }.filter { !repeating.contains($0.kind) }
        let alarms = plan.filter { $0.kind == .fajrAlarm }
        let useAlarmKit = await FajrAlarms.reschedule(alarms, l10n: l10n)
        guard await authorized() else { return 0 }
        let voice = alerts.voice ?? VoiceLanguage.forInterface(l10n.language)
        var count = 0
        for a in plan where a.kind != .fajrAlarm || !useAlarmKit {
            if count >= maxPending { break }
            let content = UNMutableNotificationContent()
            let name = l10n.t(Format.prayerKey(a.event))
            let prayerAt = days.first { $0.date == a.date }?[a.event] ?? a.at
            let time = Format.time(prayerAt, zone: loc.zone, locale: l10n.locale)
            content.threadIdentifier = "prayers-\(a.date)"
            content.userInfo = ["event": a.event.rawValue, "date": "\(a.date)", "kind": a.kind.rawValue]
            content.sound = .default
            switch a.kind {
            case .atPrayer, .fajrAlarm, .adhan:
                content.title = l10n.t("reminder_title", name)
                content.body = l10n.t("reminder_body", loc.name, time)
                content.categoryIdentifier = Category.prayer.rawValue
                content.relevanceScore = 1
                if a.kind == .adhan {
                    let file = AudioCatalog.adhan(a.event, alerts)
                    content.sound = AudioCatalog.sound(file)
                    content.userInfo["adhan"] = file
                    content.userInfo["source"] = (a.event == .fajr ? alerts.fajrAdhanSource : alerts.adhanSource).rawValue
                }
            case .before, .before10, .before5, .near:
                let minutes = Int((prayerAt.timeIntervalSince(a.at) / 60).rounded())
                content.title = a.kind == .near ? l10n.t("notif_near_title", name) : l10n.t("notif_minutes_until", minutes, name)
                content.body = l10n.t("reminder_body", loc.name, time)
                if let mosqueLine { content.subtitle = mosqueLine }
                content.categoryIdentifier = Category.before.rawValue
                content.relevanceScore = 0.6
                content.sound = AudioCatalog.sound(AudioCatalog.voice(a.kind, a.event, voice))
            case .friday:
                content.title = l10n.t("reminder_friday_title")
                content.body = l10n.t("reminder_friday_body", time)
            case .suhoor:
                content.title = l10n.t("reminder_suhoor_title")
                content.body = l10n.t("reminder_suhoor_body", time)
            case .iftar:
                content.title = l10n.t("reminder_iftar_title")
                content.body = l10n.t("reminder_body", loc.name, time)
            case .salawat, .quran, .sleep, .kahf:
                continue
            }
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, a.at.timeIntervalSince(now)), repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "alert-\(a.kind.rawValue)-\(a.date)-\(a.event.rawValue)", content: content, trigger: trigger))
            count += 1
        }
        count += await scheduleVerses(alerts: alerts, zone: loc.zone, l10n: l10n, verse: verse)
        return count
    }

    /// The repeating reminders, each with its own switch: Friday Al-Kahf (9:00), Friday Salli Ala Rasulillah
    /// (Al-Ahzab 33:56 recited), the daily Qur'an reminder and the bedtime verse (Ar-Ra'd 13:28 recited). Verse
    /// text comes from the Qur'an on the phone, always with its reference.
    private static func scheduleVerses(alerts: AlertSettings, zone: TimeZone, l10n: Localization, verse: (String) -> String?) async -> Int {
        let center = UNUserNotificationCenter.current()
        var count = 0
        func text(_ ids: [String], _ ref: String) -> String? {
            let parts = ids.compactMap(verse)
            guard !parts.isEmpty else { return nil }
            return parts.joined(separator: " ") + " (" + ref + ")"
        }
        func add(_ id: String, title: String, body: String, sound: String?, minutes: Int, weekday: Int?) async {
            let c = UNMutableNotificationContent()
            c.title = title
            c.body = body
            c.sound = AudioCatalog.sound(sound)
            c.categoryIdentifier = Category.verse.rawValue
            c.threadIdentifier = "verses"
            var when = DateComponents(hour: minutes / 60, minute: minutes % 60)
            when.weekday = weekday
            when.timeZone = zone
            try? await center.add(UNNotificationRequest(identifier: id, content: c, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)))
            count += 1
        }
        let ref = { (n: Int, a: Int) in l10n.t("reference_quran", n, a) }
        if alerts.kahf, let body = text(["quran:18:1"], ref(18, 1)) {
            await add("verse-kahf", title: l10n.t("notif_kahf_title"), body: body, sound: nil, minutes: AlertSettings.kahfHour * 60, weekday: 6)
        }
        if alerts.salawat {
            await add("verse-salawat", title: l10n.t("notif_salawat_title"), body: text(["quran:33:56"], ref(33, 56)) ?? l10n.t("salawat_note"),
                      sound: AudioCatalog.salawat(alerts.reciter), minutes: alerts.salawatMinutes, weekday: 6)
        }
        let evening = text(["quran:13:28"], ref(13, 28))
        if alerts.quran {
            await add("verse-quran", title: l10n.t("notif_quran_title"), body: l10n.t("notif_quran_body") + (evening.map { "\n" + $0 } ?? ""),
                      sound: AudioCatalog.evening(alerts.reciter), minutes: alerts.quranMinutes, weekday: nil)
        }
        if let minutes = alerts.bedtimeMinutes {
            await add("verse-sleep", title: l10n.t("notif_sleep_title"), body: evening ?? l10n.t("alerts_sleep_note"),
                      sound: AudioCatalog.evening(alerts.reciter), minutes: minutes, weekday: nil)
        }
        return count
    }

    /// "Remind me in 5 minutes": the same alert again, once, five minutes later.
    static func snooze(_ content: UNNotificationContent) async {
        guard let copy = content.mutableCopy() as? UNMutableNotificationContent else { return }
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
    static let allIdentifiers = [AlertKind.atPrayer, .before, .friday, .suhoor, .iftar, .fajrAlarm, .adhan, .before10, .before5, .near].map(\.rawValue)
}

/// Taps and buttons on the app's notifications.
final class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationHandler()

    /// With the app open at prayer time, the whole Adhan plays in the app instead of the 30-second sound.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        let info = notification.request.content.userInfo
        if let file = info["adhan"] as? String {
            await MainActor.run { AdhanPlayer.shared.play(file, title: Self.adhanTitle(info), adhan: true) }
            return [.banner, .list]
        }
        return [.banner, .list, .sound]
    }

    @MainActor private static func adhanTitle(_ info: [AnyHashable: Any]) -> String {
        let l10n = AppModel.shared.l10n
        let source = AdhanSource(rawValue: info["source"] as? String ?? "") ?? .makkah
        return l10n.t("adhan_playing", l10n.t(source == .makkah ? "source_makkah" : "source_madinah"))
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let content = response.notification.request.content
        let event = content.userInfo["event"] as? String ?? ""
        let date = content.userInfo["date"] as? String ?? ""
        switch Reminders.Action(rawValue: response.actionIdentifier) {
        case .prayed: Reminders.markPrayed(event: event, date: date, delivered: response.notification.date)
        case .snooze: await Reminders.snooze(content)
        case .qibla: await MainActor.run { PendingRoute.open(URL(string: "nearmosque://qibla")!) }
        case .mosques: await MainActor.run { PendingRoute.open(URL(string: "nearmosque://mosques")!) }
        case nil:
            // Opening an Adhan notification carries the Adhan on in the app, from where it would be now.
            if let file = content.userInfo["adhan"] as? String {
                let offset = Date().timeIntervalSince(response.notification.date)
                await MainActor.run { AdhanPlayer.shared.play(file, title: Self.adhanTitle(content.userInfo), adhan: true, offset: offset) }
            }
            await MainActor.run { PendingRoute.open(URL(string: "nearmosque://prayer")!) }
        }
    }
}

/// The Fajr alarm through AlarmKit (iOS 26): the next week of alarms, replaced on every reschedule.
enum FajrAlarms {
    private static let key = "fajrAlarmIds"

    /// True when AlarmKit took the alarms (so no notification is needed for them).
    @discardableResult
    static func reschedule(_ alarms: [PlannedAlert], l10n: Localization) async -> Bool {
        #if canImport(AlarmKit) && !targetEnvironment(macCatalyst)
        if #available(iOS 26.0, *) {
            let manager = AlarmManager.shared
            for s in UserDefaults.standard.stringArray(forKey: key) ?? [] {
                if let id = UUID(uuidString: s) { try? manager.cancel(id: id) }
            }
            UserDefaults.standard.set([String](), forKey: key)
            guard !alarms.isEmpty else { return true }
            var state = manager.authorizationState
            if state == .notDetermined { state = (try? await manager.requestAuthorization()) ?? .denied }
            guard state == .authorized else { return false }
            var ids: [String] = []
            for a in alarms.prefix(7) {
                let alert = AlarmPresentation.Alert(
                    title: LocalizedStringResource(stringLiteral: l10n.t("prayer_fajr")),
                    stopButton: AlarmButton(text: LocalizedStringResource(stringLiteral: l10n.t("alarm_stop")), textColor: .white, systemImageName: "stop.circle"))
                let attributes = AlarmAttributes<FajrAlarmMetadata>(presentation: AlarmPresentation(alert: alert), tintColor: Color(hex: 0xD4A843))
                let id = UUID()
                do {
                    _ = try await manager.schedule(id: id, configuration: .alarm(schedule: .fixed(a.at), attributes: attributes))
                    ids.append(id.uuidString)
                } catch {
                    continue
                }
            }
            UserDefaults.standard.set(ids, forKey: key)
            return true
        }
        #endif
        return false
    }
}

#if canImport(AlarmKit) && !targetEnvironment(macCatalyst)
@available(iOS 26.0, *)
struct FajrAlarmMetadata: AlarmMetadata {}
#endif

/// A background refresh about twice a day (when iOS allows) rebuilds the reminders, so they keep going when
/// the app has not been opened for days. Nothing is downloaded.
enum ReminderRefresh {
    static let id = "sa.zood.nearmosque.reminders"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: id, using: nil) { task in
            submit()
            let work = Task { @MainActor in
                await AppModel.shared.rescheduleReminders()
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func submit() {
        let r = BGAppRefreshTaskRequest(identifier: id)
        r.earliestBeginDate = Date().addingTimeInterval(10 * 3600)
        try? BGTaskScheduler.shared.submit(r)
    }
}
