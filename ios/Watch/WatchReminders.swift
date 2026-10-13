import Foundation
import NMCore
import UserNotifications

/// The watch's own reminders at prayer time, for when the iPhone stays behind (Settings → Reminders on this watch).
/// Reminders from the iPhone app already show on the watch, so these are off unless turned on.
enum WatchReminders {
    private static let prefix = "nm.watch."
    /// Ten days of five prayers stays under the 64 pending notifications an app may have.
    static let days = 10

    static func reschedule(state: SharedState?, events: [PrayerEvent], enabled: Bool) async {
        let center = UNUserNotificationCenter.current()
        let ours = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: ours)
        guard enabled, let state, let here = state.location, !events.isEmpty else { return }
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .notDetermined {
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
        } else if settings.authorizationStatus == .denied {
            return
        }
        let calc = PrayerCalculator()
        let now = Date()
        let today = CivilDate.of(now, in: state.zone)
        for d in 0..<days {
            let day = calc.schedule(here, date: today.adding(days: d), zone: state.zone, settings: state.prayer)
            for e in events {
                guard let at = day[e], at > now else { continue }
                let content = UNMutableNotificationContent()
                content.title = state.t("reminder_title", state.t("prayer_\(e.rawValue)"))
                content.body = state.t("reminder_body", state.name, state.time(at))
                content.sound = .default
                content.threadIdentifier = "prayer"
                let parts = Calendar(identifier: .gregorian).dateComponents(in: state.zone, from: at)
                let trigger = UNCalendarNotificationTrigger(dateMatching: DateComponents(timeZone: state.zone, year: parts.year, month: parts.month,
                                                                                         day: parts.day, hour: parts.hour, minute: parts.minute), repeats: false)
                try? await center.add(UNNotificationRequest(identifier: "\(prefix)\(e.rawValue).\(Int(at.timeIntervalSince1970))", content: content, trigger: trigger))
            }
        }
    }
}
