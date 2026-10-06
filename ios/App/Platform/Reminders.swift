import Foundation
import NMCore
import SwiftUI
import UserNotifications
#if canImport(AlarmKit)
import AlarmKit
#endif

/// Local prayer reminders. iOS allows 64 pending notifications per app, so the next 60 alerts (in time
/// order, from up to 14 days) are scheduled and rebuilt whenever the app opens or settings change. The
/// Fajr alarm uses AlarmKit on iOS 26 (it rings until stopped, even on silent); earlier iOS gets a
/// notification instead. Standard sounds only; no background audio is kept alive.
enum Reminders {
    static let maxPending = 60

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
                           l10n: Localization, now: Date = Date()) async -> Int {
        let center = UNUserNotificationCenter.current()
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
            switch a.kind {
            case .atPrayer, .fajrAlarm:
                content.title = l10n.t("reminder_title", name)
                content.body = l10n.t("reminder_body", loc.name, time)
            case .before:
                content.title = l10n.t("reminder_before_title", name, alerts.minutesBefore)
                content.body = l10n.t("reminder_body", loc.name, time)
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
        return count
    }
}

/// The Fajr alarm through AlarmKit (iOS 26): the next week of alarms, replaced on every reschedule.
enum FajrAlarms {
    private static let key = "fajrAlarmIds"

    /// True when AlarmKit took the alarms (so no notification is needed for them).
    @discardableResult
    static func reschedule(_ alarms: [PlannedAlert], l10n: Localization) async -> Bool {
        #if canImport(AlarmKit)
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

#if canImport(AlarmKit)
@available(iOS 26.0, *)
struct FajrAlarmMetadata: AlarmMetadata {}
#endif
