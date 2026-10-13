#if canImport(CoreMotion)
import CoreMotion
#endif
import NMCore
import SwiftUI
import WidgetKit

/// The companion on the Prayer screen, alive: the lantern swings on its ring as the phone tilts and its flame
/// flickers and throws embers; the heart's liquid stays level, sloshes and bubbles. A tap makes the flame flare or
/// the heart beat. "I prayed" marks the prayer in effect now (once); the companion brightens. It only knows what
/// you mark. Without a motion sensor it sways gently on its own; with Reduce Motion it still follows the phone,
/// more gently, with its bubbles and embers.
struct CompanionCard: View {
    @Environment(\.appModel) private var model
    @Environment(\.l10n) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    let snap: PrayerSnapshot
    @State private var level = 0.55
    @State private var bounce = false
    /// One motion reader for the app (Apple advises a single CMMotionManager; this view is re-created every second).
    private var motion: CompanionMotion { .shared }
    @State private var physics = CompanionPhysics()
    @State private var taps = 0

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
            TimelineView(.animation(minimumInterval: 1 / 60, paused: scenePhase != .active)) { ctx in
                let t = ctx.date.timeIntervalSinceReferenceDate
                let m = motion.sample(time: t)
                CompanionFigure(kind: kind, pose: physics.step(time: t, kind: kind, level: level, roll: m.roll, jolt: m.jolt, gentle: reduceMotion))
            }
            .frame(width: 112, height: 124)
            .contentShape(Rectangle())
            .onTapGesture {
                physics.tap(kind: kind)
                taps += 1
            }
            .sensoryFeedback(.impact(weight: .light), trigger: taps)
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
                        physics.tap(kind: kind)
                        Task { try? await Task.sleep(for: .milliseconds(300)); bounce = false }
                        WidgetCenter.shared.reloadAllTimelines()
                        // The watch's companion fills up too.
                        model.shareWithWidgets()
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
            motion.start()
        }
        .onDisappear { motion.stop() }
        .onChange(of: scenePhase) { _, p in
            if p == .active {
                level = CompanionLog.level(zone: snap.location?.zone ?? .current)
                motion.start()
            } else {
                motion.stop()
            }
        }
        .accessibilityElement(children: .contain)
    }
}

/// The phone's roll and sideways jolts, read each frame while the card is on screen (nothing is stored). With
/// no motion sensor (simulator, Mac) the companion sways a little on its own so it never looks frozen.
final class CompanionMotion {
    static let shared = CompanionMotion()
    #if canImport(CoreMotion) && !os(macOS) && !os(visionOS)
    private let manager = CMMotionManager()
    #endif
    /// CI recordings: `-demoCompanionMotion YES` tilts it back and forth with a jolt now and then.
    #if DEBUG
    private let demo = UserDefaults.standard.bool(forKey: "demoCompanionMotion")
    #else
    private let demo = false
    #endif

    func start() {
        #if canImport(CoreMotion) && !os(macOS) && !os(visionOS)
        guard manager.isDeviceMotionAvailable, !manager.isDeviceMotionActive else { return }
        manager.deviceMotionUpdateInterval = 1 / 60
        manager.startDeviceMotionUpdates()
        #endif
    }

    func stop() {
        #if canImport(CoreMotion) && !os(macOS) && !os(visionOS)
        manager.stopDeviceMotionUpdates()
        #endif
    }

    func sample(time t: Double) -> (roll: Double, jolt: Double) {
        if demo {
            let roll: Double = 0.45 * sin(t * 1.2)
            let jolt: Double = t.truncatingRemainder(dividingBy: 4) < 0.08 ? 0.6 : 0
            return (roll, jolt)
        }
        #if canImport(CoreMotion) && !os(macOS) && !os(visionOS)
        if let m = manager.deviceMotion {
            // Sideways tilt, upright or lying flat.
            return (asin(max(-1, min(1, m.gravity.x))), m.userAcceleration.x)
        }
        #endif
        return (0.05 * sin(t * 0.8), 0)
    }
}
