#if canImport(CoreMotion)
import CoreMotion
#endif
import NMCore
import SwiftUI
import WidgetKit

/// The companion on the Prayer screen: the lantern's flame flickers and the heart's light sways with the phone.
/// "I prayed" marks the prayer in effect now (once); the companion brightens. It only knows what you mark.
struct CompanionCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    let snap: PrayerSnapshot
    @State private var level = 0.55
    @State private var bounce = false
    @State private var tilt = CompanionTilt()

    private var kind: CompanionKind {
        #if DEBUG
        if let demo = UserDefaults.standard.string(forKey: "demoCompanion").flatMap(CompanionKind.init(rawValue:)) { return demo }
        #endif
        return CompanionKind(rawValue: model.settings.companion) ?? .lantern
    }

    private var markable: (PrayerEvent, CivilDate)? { CompanionLog.markable(today: snap.today, current: snap.current, now: snap.now) }

    var body: some View {
        let mood = CompanionLog.mood(level)
        HStack(spacing: 16) {
            TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion || scenePhase != .active)) { ctx in
                let t = ctx.date.timeIntervalSinceReferenceDate
                // A blink of about 0.15 s every 4 to 5 seconds.
                let blink = !reduceMotion && t.truncatingRemainder(dividingBy: 4.7) < 0.15
                CompanionFigure(kind: kind, level: level, phase: reduceMotion ? 0 : t, tilt: reduceMotion ? 0 : tilt.angle, blink: blink)
            }
            .frame(width: 96, height: 104)
            .scaleEffect(bounce ? 1.12 : 1)
            .animation(.spring(response: 0.35, dampingFraction: 0.45), value: bounce)
            .animation(.easeInOut(duration: 0.8), value: level)

            VStack(alignment: .leading, spacing: 8) {
                Text(l10n.t(mood == .happy ? "companion_happy" : mood == .quiet ? "companion_quiet" : "companion_sad"))
                    .font(.body.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
                if let m = markable {
                    let (e, date) = m
                    let name = l10n.t(Format.prayerKey(e))
                    let done = CompanionLog.checkedIn(e.rawValue, on: date)
                    Button {
                        CompanionLog.checkIn(e.rawValue, on: date)
                        level = CompanionLog.level(zone: snap.location?.zone ?? .current)
                        bounce = true
                        Task { try? await Task.sleep(for: .milliseconds(300)); bounce = false }
                        WidgetCenter.shared.reloadAllTimelines()
                    } label: {
                        Label(l10n.t(done ? "companion_marked" : "companion_mark", name), systemImage: done ? "checkmark.circle.fill" : "hands.and.sparkles")
                            .font(.subheadline.weight(.semibold))
                            .frame(minHeight: 44)
                    }
                    .glassButton()
                    .disabled(done)
                    .sensoryFeedback(.success, trigger: done)
                }
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(Theme.ink)
        .glassCard()
        .onAppear {
            level = CompanionLog.level(zone: snap.location?.zone ?? .current)
            if kind == .heart && !reduceMotion { tilt.start() }
        }
        .onDisappear { tilt.stop() }
        .onChange(of: scenePhase) { _, p in
            if p == .active { level = CompanionLog.level(zone: snap.location?.zone ?? .current) }
        }
        .accessibilityElement(children: .contain)
    }
}

/// The phone's roll, smoothed, so the heart's light stays level as the phone tilts. Read only while the card is
/// on screen; nothing is stored.
@Observable
final class CompanionTilt {
    var angle = 0.0
    #if canImport(CoreMotion) && !os(macOS)
    @ObservationIgnored private let manager = CMMotionManager()
    #endif

    func start() {
        #if canImport(CoreMotion) && !os(macOS)
        guard manager.isDeviceMotionAvailable, !manager.isDeviceMotionActive else { return }
        manager.deviceMotionUpdateInterval = 1 / 30
        manager.startDeviceMotionUpdates(to: .main) { [weak self] m, _ in
            guard let self, let g = m?.gravity else { return }
            let roll: Double = -atan2(g.x, -g.y)
            angle += (roll - angle) * 0.15
        }
        #endif
    }

    func stop() {
        #if canImport(CoreMotion) && !os(macOS)
        manager.stopDeviceMotionUpdates()
        #endif
        angle = 0
    }
}
