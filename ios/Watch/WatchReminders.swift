import Foundation
import NMCore
import UserNotifications

/// The watch's own reminders at prayer time, for when the iPhone stays behind (Settings → Reminders on this watch).
/// Reminders from the iPhone app already show on the watch, so these are off unless turned on.
enum WatchReminders {
    private static let prefix = "nm.watch."
    /// Ten days of five prayers stays under the 64 pending notifications an app may have.
    static let days = 10

    /// What a run found: reminders set (or none wanted), notifications off for the app, or just refused at the prompt.
    enum Outcome { case ok, denied, refused }

    /// Adds the new reminders before taking any old one out, so a run cut short (the app suspended) never leaves fewer
    /// than before; when nothing has changed it leaves everything as it is.
    static func reschedule(state: SharedState?, events: [PrayerEvent], enabled: Bool) async -> Outcome {
        let center = UNUserNotificationCenter.current()
        let pending = Set(await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) })
        guard enabled, let state, let here = state.location, !events.isEmpty else {
            if !pending.isEmpty { center.removePendingNotificationRequests(withIdentifiers: Array(pending)) }
            return .ok
        }
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .notDetermined {
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return .refused }
        } else if settings.authorizationStatus == .denied {
            // The ones set stay: they show again as soon as notifications are allowed.
            return .denied
        }
        let calc = PrayerCalculator()
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = state.zone
        let now = Date()
        let today = CivilDate.of(now, in: state.zone)
        var wanted: [(slot: String, request: UNNotificationRequest)] = []
        for d in 0..<days {
            let day = calc.schedule(here, date: today.adding(days: d), zone: state.zone, settings: state.prayer)
            for e in PrayerEvent.prayers where events.contains(e) {
                guard let at = day[e], at > now else { continue }
                let content = UNMutableNotificationContent()
                content.title = state.t("reminder_title", state.t("prayer_\(e.rawValue)"))
                content.body = state.t("reminder_body", state.name, state.time(at))
                content.sound = .default
                content.threadIdentifier = "prayer"
                // Comes through a Focus (Sleep at Fajr, for one) that allows Time Sensitive notifications.
                content.interruptionLevel = .timeSensitive
                // The calendar goes in the components too, or they are read in the watch's own (Buddhist, Hijri...).
                let p = cal.dateComponents([.year, .month, .day, .hour, .minute], from: at)
                let trigger = UNCalendarNotificationTrigger(dateMatching: DateComponents(calendar: cal, timeZone: state.zone, year: p.year, month: p.month,
                                                                                         day: p.day, hour: p.hour, minute: p.minute), repeats: false)
                // The prayer and its time, then a mark of the text, so a new text replaces the reminder.
                let slot = "\(prefix)\(e.rawValue).\(Int(at.timeIntervalSince1970))"
                wanted.append((slot, UNNotificationRequest(identifier: "\(slot).\(mark(content))", content: content, trigger: trigger)))
            }
        }
        var keep = Set<String>()
        for (slot, request) in wanted where !pending.contains(request.identifier) {
            // A newer run has started and takes over from here.
            if Task.isCancelled { return .ok }
            let old = pending.filter { $0 == slot || $0.hasPrefix(slot + ".") }
            do {
                try await center.add(request)
                // The same prayer and time with the older text goes once the new one is in.
                if !old.isEmpty { center.removePendingNotificationRequests(withIdentifiers: Array(old)) }
            } catch {
                keep.formUnion(old)
            }
        }
        // Then the ones no longer wanted (a prayer turned off, a time that moved).
        let gone = pending.subtracting(wanted.map { $0.request.identifier }).subtracting(keep)
        if !gone.isEmpty { center.removePendingNotificationRequests(withIdentifiers: Array(gone)) }
        return .ok
    }

    /// A short mark of the reminder's text (String's own hash changes at every launch). The leading number goes up
    /// when the request itself changes, so the old ones are replaced (2: the calendar in the trigger, Time Sensitive).
    private static func mark(_ c: UNNotificationContent) -> String {
        var h: UInt32 = 2_166_136_261
        for b in "2|\(c.title)|\(c.body)".utf8 { h = (h ^ UInt32(b)) &* 16_777_619 }
        return String(h, radix: 36)
    }
}
