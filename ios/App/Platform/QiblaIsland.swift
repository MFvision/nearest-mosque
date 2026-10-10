#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import CoreLocation
import NMCore

/// Experimental: the Qibla compass in the Dynamic Island (QiblaActivityAttributes), started from the Qibla
/// widget. Reads the true heading in the background for two minutes (iOS shows the location indicator), sends
/// the arrow to the activity about once a second, then stops reading and ends the activity.
@MainActor
final class QiblaIsland: NSObject, CLLocationManagerDelegate {
    static let shared = QiblaIsland()
    static let seconds: TimeInterval = 120

    private let manager = CLLocationManager()
    private var activity: Activity<QiblaActivityAttributes>?
    private var background: CLBackgroundActivitySession?
    private var bearing = 0.0
    private var lastSent = Date.distantPast
    private var lastState: QiblaActivityAttributes.ContentState?
    private var stopTask: Task<Void, Never>?

    func start() async {
        let model = AppModel.shared
        guard ActivityAuthorizationInfo().areActivitiesEnabled, CLLocationManager.headingAvailable(),
              let loc = model.settings.location else { return }
        await stop()
        let l10n = model.l10n
        bearing = Qibla.bearing(from: loc.location)
        let attributes = QiblaActivityAttributes(city: loc.name, title: l10n.t("qibla"),
                                                 bearingText: Format.degrees(bearing, locale: l10n.locale) + "°",
                                                 fromNorth: l10n.t("qibla_bearing", Format.degrees(bearing, locale: l10n.locale)),
                                                 rtl: l10n.isRTL, style: model.settings.widgetLook)
        let initial = QiblaActivityAttributes.ContentState(turn: nil, facing: false, hint: l10n.t("qibla_move_phone"))
        activity = try? Activity.request(attributes: attributes,
                                         content: ActivityContent(state: initial, staleDate: Date().addingTimeInterval(Self.seconds + 30)), pushType: nil)
        guard activity != nil else { return }
        background = CLBackgroundActivitySession()
        manager.delegate = self
        manager.headingFilter = 2
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
        stopTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(Self.seconds))
            await self?.stop()
        }
    }

    func stop() async {
        stopTask?.cancel()
        stopTask = nil
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        background?.invalidate()
        background = nil
        for a in Activity<QiblaActivityAttributes>.activities { await a.end(nil, dismissalPolicy: .immediate) }
        activity = nil
        lastState = nil
    }

    private func send(heading: Double) {
        let l10n = AppModel.shared.l10n
        let turn = Angles.normalize180(bearing - heading)
        let a = abs(turn)
        let hint = a <= 5 ? l10n.t("qibla_facing_short")
            : turn > 0 ? l10n.t(a < 30 ? "qibla_go_right_little" : "qibla_go_right")
            : l10n.t(a < 30 ? "qibla_go_left_little" : "qibla_go_left")
        let state = QiblaActivityAttributes.ContentState(turn: turn, facing: a <= 5, hint: hint)
        // About once a second, or at once when the facing state changes.
        guard state.facing != lastState?.facing || Date().timeIntervalSince(lastSent) >= 1 else { return }
        lastSent = Date()
        lastState = state
        let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(Self.seconds + 30))
        Task { await activity?.update(content) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateHeading h: CLHeading) {
        // True heading only (it needs a location); never show magnetic north as the Qibla reference.
        guard h.trueHeading >= 0 else { return }
        let heading = h.trueHeading
        Task { @MainActor in self.send(heading: heading) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {}
    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {}
}
#endif
