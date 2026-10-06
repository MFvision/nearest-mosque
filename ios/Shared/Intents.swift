import AppIntents
import Foundation
import NMCore

extension Notification.Name {
    /// Posted (in the app) when a shortcut, Siri or a control asks for the Qibla.
    static let openQibla = Notification.Name("NearMosque.openQibla")
}

/// Opens the app on the Qibla view (Siri, Shortcuts, the Action button, Control Center).
struct OpenQiblaIntent: AppIntent {
    static var title: LocalizedStringResource = "Show the Qibla"
    static var description = IntentDescription("Opens Near Mosque on the Qibla direction.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(true, forKey: "openQiblaOnLaunch")
        NotificationCenter.default.post(name: .openQibla, object: nil)
        return .result()
    }
}

/// "When is the next prayer?": answered on the device from the city and method chosen in the app.
struct NextPrayerIntent: AppIntent {
    static var title: LocalizedStringResource = "Next prayer"
    static var description = IntentDescription("Says which prayer is next, when, and how long until it.")

    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        guard let s = SharedStore.read(), let next = PrayerCalculator().nextPrayer(s.days(Date()), now: Date()) else {
            let text = SharedState.fallbackNoCity
            return .result(value: text, dialog: IntentDialog(stringLiteral: text))
        }
        let left = Duration.seconds(Int(max(0, next.at.timeIntervalSinceNow)))
            .formatted(.units(allowed: [.hours, .minutes], width: .wide, maximumUnitCount: 2).locale(s.locale))
        let text = s.t("siri_next_prayer", s.t("prayer_\(next.event.rawValue)"), s.time(next.at), left)
        return .result(value: text, dialog: IntentDialog(stringLiteral: text))
    }
}

extension SharedState {
    static let fallbackNoCity = "Open Near Mosque and choose your city first."
}
