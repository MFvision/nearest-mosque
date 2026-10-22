import NMCore
import SwiftUI
import UIKit
import UserNotifications
#if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
import WatchConnectivity
#endif

/// What keeps the alerts that are on from reaching the user (AppModel.alertBlock).
enum AlertBlock: Equatable {
    /// Notifications not asked for yet, or turned off for the app.
    case notificationsUndecided, notificationsDenied
    /// iPhone and iPad with iOS 26: alarms (the Adhan ringing even on silent) not asked for yet, or turned off.
    case alarmsUndecided, alarmsDenied
}

/// The banner on the prayer screen and in Prayer & Audio: what is wrong, and one button that asks (where the system
/// has not asked yet) or opens Settings.
struct AlertAccessNotice: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.openURL) private var openURL
    let block: AlertBlock

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(l10n.t(text.title), systemImage: text.icon).font(.headline)
            Text(l10n.t(text.body)).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            Button(l10n.t(text.button)) {
                switch block {
                case .notificationsUndecided, .alarmsUndecided:
                    Task { await app.askAlertAccess() }
                case .notificationsDenied:
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                case .alarmsDenied:
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
            .prominentButton()
        }
    }

    private var text: (icon: String, title: String, body: String, button: String) {
        switch block {
        case .notificationsUndecided: return ("bell.badge", "alerts_ask_title", "alerts_ask_body", "onb2_allow")
        case .notificationsDenied: return ("bell.slash", "alerts_off_title", "alerts_off_body", "open_settings")
        case .alarmsUndecided: return ("alarm", "alarms_ask_title", "alarms_ask_body", "alarms_allow")
        case .alarmsDenied: return ("alarm", "alarms_off_title", "alarms_off_body", "open_settings")
        }
    }
}

/// Settings → Prayer & Audio → Check my alerts: what the device allows that decides whether an alert arrives, read
/// again each time the page shows, and the next five alerts set, with their time, kind and sound. Nothing leaves
/// the device.
struct AlertCheckView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.openURL) private var openURL
    @State private var report: AlertReport?

    var body: some View {
        Form {
            if let block = app.alertBlock {
                Section { AlertAccessNotice(block: block) }
            }
            if let r = report {
                Section {
                    row("check_notifications", value: access(r.notifications), problem: r.notifications == .denied ? "notifications_denied" : nil)
                    if r.sounds != .notSupported {
                        row("check_sounds", value: r.sounds == .enabled ? "check_on" : "check_off", problem: r.sounds == .disabled ? "check_sounds_off" : nil)
                    }
                    if r.alarms != .unavailable {
                        row("check_alarms", value: access(r.alarms), problem: r.alarms == .denied ? "check_alarms_off" : nil)
                    }
                    row("check_time_sensitive", value: r.timeSensitive == .enabled ? "check_on" : "check_off",
                        problem: r.timeSensitive == .enabled ? nil : "check_time_sensitive_off")
                    row("check_summary", value: r.summary == .enabled ? "check_on" : "check_off", problem: r.summary == .enabled ? "check_summary_on" : nil)
                    row("check_background", value: r.backgroundRefresh == .available ? "check_on" : "check_off",
                        problem: r.backgroundRefresh == .available ? nil : "check_background_off")
                    row("check_low_power", value: r.lowPower ? "check_on" : "check_off", problem: r.lowPower ? "check_background_off" : nil)
                    Button(l10n.t("open_settings")) {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                    }
                } header: { Text(l10n.t("check_status")) }
                Section {
                    if r.next.isEmpty {
                        Text(l10n.t("check_nothing")).foregroundStyle(.secondary)
                    }
                    ForEach(r.next) { n in nextRow(n) }
                } header: { Text(l10n.t("check_next")) }
                notes(r)
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(l10n.t("check_alerts"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await read() }
        // Back from Settings: read again.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in Task { await read() } }
    }

    private func read() async {
        await app.refreshAlertAccess()
        report = await AlertReport.read(app)
    }

    private func row(_ title: String, value: String, problem: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(l10n.t(title))
                Spacer(minLength: 8)
                if problem != nil {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange).accessibilityHidden(true)
                }
                Text(l10n.t(value)).foregroundStyle(.secondary)
            }
            if let problem {
                Text(l10n.t(problem)).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func nextRow(_ n: AlertReport.Next) -> some View {
        HStack(spacing: 12) {
            Image(systemName: n.alarm ? "alarm.fill" : "bell.fill").foregroundStyle(Theme.accent).frame(minWidth: 24).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(n.title).lineLimit(2)
                Text(l10n.t(n.alarm ? "check_kind_alarm" : "check_kind_notification") + " · " + soundName(n.sound))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Text(when(n.at)).monospacedDigit().foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    /// One line each on the silent switch and on a worn Apple Watch (where the device has them).
    @ViewBuilder private func notes(_ r: AlertReport) -> some View {
        #if !targetEnvironment(macCatalyst) && !os(visionOS)
        Section {
            Label(l10n.t(r.alarms == .unavailable ? "check_silent" : "check_silent_alarm"), systemImage: "bell.slash")
                .font(.footnote).foregroundStyle(.secondary)
            #if canImport(WatchConnectivity)
            if WCSession.isSupported(), WatchPairing.shared.state != .unpaired {
                Label(l10n.t("check_watch"), systemImage: "applewatch").font(.footnote).foregroundStyle(.secondary)
            }
            #endif
        }
        #endif
    }

    private func access(_ s: UNAuthorizationStatus) -> String {
        switch s {
        case .notDetermined: return "check_not_asked"
        case .denied: return "check_not_allowed"
        default: return "check_allowed"
        }
    }

    private func access(_ p: PrayerAlarms.Permission) -> String {
        switch p {
        case .notDetermined: return "check_not_asked"
        case .denied, .unavailable: return "check_not_allowed"
        case .authorized: return "check_allowed"
        }
    }

    /// "Thu 5:12 PM" in the prayer city's time zone.
    private func when(_ d: Date) -> String {
        let zone = app.settings.location?.zone ?? .current
        return d.formatted(Date.FormatStyle(locale: l10n.locale, calendar: Calendar(identifier: .gregorian), timeZone: zone).weekday(.abbreviated).hour().minute())
    }

    /// The sound as the user knows it (the file names come from Reminders and PrayerAlarms).
    private func soundName(_ file: String?) -> String {
        guard let file, file != "default" else { return l10n.t("check_sound_default") }
        if file == "alarm" { return l10n.t("check_sound_alarm") }
        if file.hasPrefix("adhan_") || file.hasPrefix("fajr_") {
            let recording = file.hasSuffix("_short") ? "adhan_short" : file.contains("madinah") ? "source_madinah" : "source_makkah"
            return l10n.t("adhan_section") + " · " + l10n.t(recording)
        }
        return l10n.t(file.hasPrefix("voice_") ? "check_sound_voice" : "check_sound_recitation")
    }
}

/// What "Check my alerts" shows, read from the system when the page opens.
struct AlertReport {
    struct Next: Identifiable {
        let id: String
        let at: Date
        let title: String
        let alarm: Bool
        let sound: String?
    }

    var notifications: UNAuthorizationStatus
    var sounds: UNNotificationSetting
    var timeSensitive: UNNotificationSetting
    /// On: the app's notifications wait for the Scheduled Summary.
    var summary: UNNotificationSetting
    var alarms: PrayerAlarms.Permission
    var backgroundRefresh: UIBackgroundRefreshStatus
    var lowPower: Bool
    /// The next five alerts set: notifications and alarms together, soonest first.
    var next: [Next]

    @MainActor
    static func read(_ app: AppModel) async -> AlertReport {
        let center = UNUserNotificationCenter.current()
        let s = await center.notificationSettings()
        let now = Date()
        let l10n = app.l10n
        var next: [Next] = await center.pendingNotificationRequests().compactMap { r -> Next? in
            // The time the rebuild set (a time-interval trigger reports one counted from now instead).
            let at = (r.content.userInfo["at"] as? Double).map(Date.init(timeIntervalSince1970:))
                ?? (r.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
            // An alarm's silent notification: the alarm itself is listed below.
            guard let at, at > now, r.content.userInfo["alarm"] as? Bool != true else { return nil }
            return Next(id: r.identifier, at: at, title: r.content.title, alarm: false, sound: r.content.userInfo["sound"] as? String)
        }
        next += PrayerAlarms.upcoming(after: now).compactMap { a -> Next? in
            guard let e = PrayerEvent(rawValue: a.event) else { return nil }
            let title = a.kind == AlertKind.adhan.rawValue ? l10n.t("reminder_title", l10n.t(Format.prayerKey(e))) : l10n.t("alerts_fajr_alarm")
            return Next(id: a.id, at: a.at, title: title, alarm: true, sound: a.sound)
        }
        return AlertReport(notifications: s.authorizationStatus, sounds: s.soundSetting, timeSensitive: s.timeSensitiveSetting,
                           summary: s.scheduledDeliverySetting, alarms: PrayerAlarms.permission,
                           backgroundRefresh: UIApplication.shared.backgroundRefreshStatus,
                           lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled, next: Array(next.sorted { $0.at < $1.at }.prefix(5)))
    }
}
