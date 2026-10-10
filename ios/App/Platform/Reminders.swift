import Foundation
import NMCore
import SwiftUI
import UserNotifications
#if canImport(AlarmKit) && !targetEnvironment(macCatalyst)
import AlarmKit
#endif

/// Local prayer reminders. iOS allows 64 pending notifications per app, so the next 60 alerts (in time
/// order, from up to 14 days) are scheduled and rebuilt whenever the app opens or settings change. The
/// Fajr alarm uses AlarmKit on iOS 26 (it rings until stopped, even on silent); earlier iOS and the Mac
/// get a notification instead. Standard sounds only; no background audio is kept alive.
enum Reminders {
    /// Prayer alerts; Al-Kahf (1) and the bedtime verses (7, one per weekday) take the rest of iOS's 64.
    static let maxPending = 52

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
        }
        let alarms = plan.filter { $0.kind == .fajrAlarm }
        let useAlarmKit = await FajrAlarms.reschedule(alarms, l10n: l10n)
        guard await authorized() else { return 0 }
        var count = 0
        for a in plan where a.kind != .fajrAlarm || !useAlarmKit {
            if count >= maxPending { break }
            let content = UNMutableNotificationContent()
            let name = l10n.t(Format.prayerKey(a.event))
            let time = Format.time(a.kind == .before || a.kind == .friday || a.kind == .suhoor ? (days.first { $0.date == a.date }?[a.event] ?? a.at) : a.at,
                                   zone: loc.zone, locale: l10n.locale)
            content.threadIdentifier = "prayers-\(a.date)"
            content.userInfo = ["event": a.event.rawValue, "date": "\(a.date)"]
            switch a.kind {
            case .atPrayer, .fajrAlarm:
                content.title = l10n.t("reminder_title", name)
                content.body = l10n.t("reminder_body", loc.name, time)
                content.categoryIdentifier = Category.prayer.rawValue
                content.relevanceScore = 1
            case .before:
                content.title = l10n.t("reminder_before_title", name, alerts.minutesBefore)
                content.body = l10n.t("reminder_body", loc.name, time)
                if let mosqueLine { content.subtitle = mosqueLine }
                content.categoryIdentifier = Category.before.rawValue
                content.relevanceScore = 0.6
            case .friday:
                content.title = l10n.t("reminder_friday_title")
                content.body = l10n.t("reminder_friday_body", time)
            case .suhoor:
                content.title = l10n.t("reminder_suhoor_title")
                content.body = l10n.t("reminder_suhoor_body", time)
            case .iftar:
                content.title = l10n.t("reminder_iftar_title")
                content.body = l10n.t("reminder_body", loc.name, time)
            }
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, a.at.timeIntervalSince(now)), repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "alert-\(a.kind.rawValue)-\(a.date)-\(a.event.rawValue)", content: content, trigger: trigger))
            count += 1
        }
        count += await scheduleVerses(alerts: alerts, zone: loc.zone, l10n: l10n, verse: verse)
        return count
    }

    /// Friday Al-Kahf (repeats every Friday at 9:00) and the bedtime verse (one repeating alert per weekday,
    /// alternating Ayat al-Kursi and the last two verses of Al-Baqarah). The verse text comes from the Qur'an on
    /// the phone, always with its reference; the English translation is added for English readers.
    private static func scheduleVerses(alerts: AlertSettings, zone: TimeZone, l10n: Localization, verse: (String) -> String?) async -> Int {
        let center = UNUserNotificationCenter.current()
        var count = 0
        func text(_ ids: [String], _ ref: String) -> String? {
            let parts = ids.compactMap(verse)
            guard !parts.isEmpty else { return nil }
            return parts.joined(separator: " ") + " (" + ref + ")"
        }
        let surah = { (n: Int, a: String) in l10n.t("reference_quran", n, Int(a) ?? 1) }
        if alerts.kahf, let body = text(["quran:18:1"], surah(18, "1")) {
            let c = UNMutableNotificationContent()
            c.title = l10n.t("notif_kahf_title")
            c.body = body
            c.sound = .default
            c.categoryIdentifier = Category.verse.rawValue
            c.threadIdentifier = "verses"
            var when = DateComponents(hour: AlertSettings.kahfHour, minute: 0, weekday: 6)
            when.timeZone = zone
            try? await center.add(UNNotificationRequest(identifier: "verse-kahf", content: c, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)))
            count += 1
        }
        if let minutes = alerts.bedtimeMinutes {
            let kursi = text(["quran:2:255"], surah(2, "255"))
            let baqarah = text(["quran:2:285", "quran:2:286"], l10n.t("reference_quran", 2, 285) + "–286")
            for weekday in 1...7 {
                guard let body = weekday % 2 == 1 ? kursi : baqarah else { continue }
                let c = UNMutableNotificationContent()
                c.title = l10n.t("notif_sleep_title")
                c.body = body
                c.sound = .default
                c.categoryIdentifier = Category.verse.rawValue
                c.threadIdentifier = "verses"
                var when = DateComponents(hour: minutes / 60, minute: minutes % 60, weekday: weekday)
                when.timeZone = zone
                try? await center.add(UNNotificationRequest(identifier: "verse-sleep-\(weekday)", content: c,
                                                            trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: true)))
                count += 1
            }
        }
        return count
    }

    /// "Remind me in 5 minutes": the same alert again, once, five minutes later.
    static func snooze(_ content: UNNotificationContent) async {
        guard let copy = content.mutableCopy() as? UNMutableNotificationContent else { return }
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(
            identifier: "snooze-\(UUID().uuidString)", content: copy, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5 * 60, repeats: false)))
    }

    /// "Prayed": that prayer's remaining alerts today are cancelled, and the day is noted on the phone (for the
    /// lantern). Nothing leaves the phone.
    static func markPrayed(event: String, date: String) {
        let center = UNUserNotificationCenter.current()
        let ids = AlertKind.allIdentifiers.map { "alert-\($0)-\(date)-\(event)" }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
        var log = UserDefaults.standard.stringArray(forKey: "prayedLog") ?? []
        log.append("\(date):\(event)")
        UserDefaults.standard.set(Array(log.suffix(200)), forKey: "prayedLog")
    }
}

extension AlertKind {
    static let allIdentifiers = [AlertKind.atPrayer, .before, .friday, .suhoor, .iftar, .fajrAlarm].map(\.rawValue)
}

/// Taps and buttons on the app's notifications.
final class NotificationHandler: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationHandler()

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let content = response.notification.request.content
        let event = content.userInfo["event"] as? String ?? ""
        let date = content.userInfo["date"] as? String ?? ""
        switch Reminders.Action(rawValue: response.actionIdentifier) {
        case .prayed: Reminders.markPrayed(event: event, date: date)
        case .snooze: await Reminders.snooze(content)
        case .qibla: await MainActor.run { PendingRoute.open(URL(string: "nearmosque://qibla")!) }
        case .mosques: await MainActor.run { PendingRoute.open(URL(string: "nearmosque://mosques")!) }
        case nil: await MainActor.run { PendingRoute.open(URL(string: "nearmosque://prayer")!) }
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
