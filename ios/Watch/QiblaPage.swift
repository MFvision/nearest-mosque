import CoreLocation
import NMCore
import SwiftUI
import WatchKit

/// The live Qibla compass on the wrist: the dial turns with the watch, the Kaaba sits where the Qibla is, and the
/// watch taps the wrist when you face it (Apple Watch Series 5 and later have a compass). Without a compass the
/// dial shows the Qibla from north.
struct QiblaPage: View {
    @Environment(WatchModel.self) private var model
    @State private var compass = WatchCompass()
    @State private var wasFacing = false

    static let facingDegrees = 5.0

    var body: some View {
        let l10n = model.l10n
        Group {
            if let s = model.state, let here = s.location {
                let bearing = Qibla.bearing(from: here)
                let heading = compass.heading
                let turn = heading.map { foldDegrees(bearing - $0) }
                let facing = turn.map { abs($0) <= Self.facingDegrees } ?? false
                VStack(spacing: 4) {
                    QiblaWatchDial(bearing: bearing, heading: heading, facing: facing, accent: model.accent, north: s.t("compass_north"))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Text(hint(s, turn: turn, facing: facing))
                        .font(.headline).foregroundStyle(facing ? model.accent : .white)
                        .shadow(color: .black.opacity(0.5), radius: 3)
                        .lineLimit(2).minimumScaleFactor(0.6).multilineTextAlignment(.center)
                    Text(s.t("qibla_bearing", Format.degrees(bearing, locale: s.locale)))
                        .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
                }
                .onChange(of: facing) { _, f in
                    if f && !wasFacing && model.settings.qiblaHaptic { WKInterfaceDevice.current().play(.success) }
                    wasFacing = f
                }
                .accessibilityElement(children: .combine)
            } else {
                WaitingView()
            }
        }
        .watchSky()
        .navigationTitle(l10n.t("qibla"))
        .onAppear { compass.start() }
        .onDisappear { compass.stop() }
    }

    private func hint(_ s: SharedState, turn: Double?, facing: Bool) -> String {
        guard let turn else {
            return compass.available ? s.t("qibla_calibrate") : s.t("qibla_from_north_hint")
        }
        if facing { return s.t("qibla_facing_short") }
        if abs(turn) < 25 { return s.t(turn > 0 ? "qibla_go_right_little" : "qibla_go_left_little") }
        return s.t(turn > 0 ? "qibla_go_right" : "qibla_go_left")
    }
}

/// The compass ring with N, the Kaaba on the rim at the Qibla, and the arrow straight up (the way the watch points).
/// The ring turns by the heading; without one it stays north-up.
struct QiblaWatchDial: View {
    let bearing: Double
    let heading: Double?
    let facing: Bool
    let accent: Color
    let north: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var ripple = false

    var body: some View {
        GeometryReader { g in
            let d = min(g.size.width, g.size.height)
            let r = d / 2
            let c = CGPoint(x: g.size.width / 2, y: g.size.height / 2)
            let ring = -(heading ?? 0)
            ZStack {
                // A ripple of light while you face the Qibla.
                Circle().stroke(accent, lineWidth: 2).frame(width: d, height: d)
                    .scaleEffect(ripple ? 1.18 : 1).opacity(facing ? (ripple ? 0 : 0.9) : 0)
                Circle().fill(.ultraThinMaterial).frame(width: d, height: d)
                Circle().fill(RadialGradient(colors: [accent.opacity(facing ? 0.35 : 0.12), .clear], center: .center, startRadius: 0, endRadius: d / 2))
                    .frame(width: d, height: d)
                Circle().stroke(facing ? AnyShapeStyle(accent) : AnyShapeStyle(LinearGradient(colors: [.white.opacity(0.5), .white.opacity(0.12)], startPoint: .top, endPoint: .bottom)),
                                lineWidth: facing ? 3 : 1.5)
                    .frame(width: d - 4, height: d - 4)
                    .shadow(color: facing ? accent.opacity(0.8) : .clear, radius: 8)
                ForEach(0..<36) { i in
                    Capsule().fill(Color.white.opacity(i % 9 == 0 ? 0.8 : 0.3))
                        .frame(width: i % 9 == 0 ? 2 : 1, height: i % 9 == 0 ? 8 : 4)
                        .offset(y: -r + 8)
                        .rotationEffect(.degrees(Double(i) * 10 + ring))
                }
                Text(north).font(.system(size: max(10, d * 0.09), weight: .bold)).foregroundStyle(accent)
                    .position(point(c, r - d * 0.16, ring))
                // The Qibla on the rim.
                ZStack {
                    Circle().fill(accent.opacity(facing ? 0.55 : 0.3)).frame(width: d * 0.24, height: d * 0.24).blur(radius: 4)
                    WatchKaaba().frame(width: d * 0.17, height: d * 0.17)
                }
                .position(point(c, r - d * 0.12, bearing + ring))
                // Where the watch points (top of the screen when there is a compass); from north without one.
                Image(systemName: "location.north.fill")
                    .font(.system(size: d * 0.22, weight: .bold))
                    .foregroundStyle(facing ? accent : .white)
                    .rotationEffect(.degrees(heading == nil ? bearing : 0))
                    .position(c)
            }
            .animation(.easeOut(duration: 0.25), value: heading ?? 0)
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeOut(duration: 1.6).repeatForever(autoreverses: false)) { ripple = true }
        }
        .environment(\.layoutDirection, .leftToRight)
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private func point(_ c: CGPoint, _ radius: CGFloat, _ degrees: Double) -> CGPoint {
        let a = degrees * .pi / 180
        return CGPoint(x: c.x + radius * CGFloat(sin(a)), y: c.y - radius * CGFloat(cos(a)))
    }
}

/// The watch's compass (true north when the location is known, magnetic otherwise), read only while the page shows.
@MainActor
@Observable
final class WatchCompass: NSObject, CLLocationManagerDelegate {
    @ObservationIgnored private let manager = CLLocationManager()
    private(set) var heading: Double?
    var available: Bool {
        #if DEBUG
        if UserDefaults.standard.object(forKey: "demoHeading") != nil { return true }
        #endif
        return CLLocationManager.headingAvailable()
    }

    func start() {
        #if DEBUG
        // Screenshots in CI (the simulator has no compass): `-demoHeading 241`.
        if UserDefaults.standard.object(forKey: "demoHeading") != nil {
            heading = UserDefaults.standard.double(forKey: "demoHeading")
            return
        }
        #endif
        guard available else { return }
        manager.delegate = self
        manager.headingFilter = 1
        manager.startUpdatingHeading()
    }

    func stop() {
        manager.stopUpdatingHeading()
        heading = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let ok = newHeading.headingAccuracy >= 0
        let value = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        Task { @MainActor in self.heading = ok ? value : nil }
    }
}
