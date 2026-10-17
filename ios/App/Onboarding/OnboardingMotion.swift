import NMCore
import SwiftUI

/// The onboarding's motion (shared/design/tokens.json "motion"): three springs, the press, a step entering, a chip
/// flying into the header, a bump and a pulse. Under Reduce Motion every one of them becomes a short crossfade.
enum OnbMotion {
    /// A step entering, a press released, the header (springResponse 0.42, springDamping 0.86).
    static let spring = Theme.spring
    /// A chip flying in, a check popping, a bump, the style ring (springBouncyResponse 0.52, springBouncyDamping 0.62).
    static let bouncy = Animation.spring(response: 0.52, dampingFraction: 0.62)
    /// The sun, the horizon, a shadow growing, the drag returning, the Friday skyline (springSoftResponse 0.9,
    /// springSoftDamping 1.0).
    static let soft = Animation.spring(response: 0.9, dampingFraction: 1.0)
    /// What every motion becomes under Reduce Motion.
    static let fade = Animation.easeInOut(duration: 0.2)
    /// A press going in (pressInMs 90); it comes out with the spring.
    static let pressIn = Animation.easeOut(duration: 0.09)
    /// The old step fading out before the next one (stepLeaveMs 200).
    static let leave = Animation.easeIn(duration: Double(OnboardingTiming.leave) / 1000)

    /// `animation`, or the crossfade under Reduce Motion.
    static func motion(_ animation: Animation, reduce: Bool) -> Animation { reduce ? fade : animation }
}

/// Every button and row of the onboarding: down to 0.96 in 90 ms while pressed, back with the spring; a row also
/// brightens a little. Not under Reduce Motion (the brightening stays).
struct OnbPressStyle: ButtonStyle {
    /// The shape that brightens while pressed (nil: none, for capsules that already have their own).
    var shape: AnyShape?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .overlay {
                if let shape {
                    shape.fill(Theme.ink.opacity(configuration.isPressed ? 0.08 : 0)).allowsHitTesting(false)
                }
            }
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.96 : 1)
            .animation(configuration.isPressed ? OnbMotion.pressIn : OnbMotion.spring, value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == OnbPressStyle {
    static var onbPress: OnbPressStyle { OnbPressStyle() }
    static func onbPress(_ shape: some Shape) -> OnbPressStyle { OnbPressStyle(shape: AnyShape(shape)) }
}

/// One child of a step entering: from 24 points below and transparent, with the spring, `order` × 40 ms after the
/// first (title, body, controls, button). Under Reduce Motion it only fades in; in still screenshots it is there at once.
struct OnbEnter: ViewModifier {
    let order: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onb) private var onb
    @State private var shown = false

    func body(content: Content) -> some View {
        let visible = shown || onb.still
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible || reduceMotion ? 0 : 24)
            .onAppear {
                guard !visible else { return }
                let delay = Double(order * OnboardingTiming.stagger) / 1000
                withAnimation(reduceMotion ? OnbMotion.fade : OnbMotion.spring.delay(delay)) { shown = true }
            }
    }
}

/// A chip flying up into "YOUR DAY": from far below (and from the panel's side on big screens), a little larger,
/// with the bouncy spring; opaque within 220 ms. It leaves by fading and shrinking.
struct ChipFly: ViewModifier {
    let active: Bool
    let from: CGSize

    func body(content: Content) -> some View {
        content
            .scaleEffect(active ? 1.25 : 1)
            .offset(x: active ? from.width : 0, y: active ? from.height : 0)
    }
}

extension AnyTransition {
    /// See ChipFly; a crossfade under Reduce Motion.
    static func chipFly(from: CGSize, reduce: Bool) -> AnyTransition {
        if reduce { return .opacity.animation(OnbMotion.fade) }
        let fly = AnyTransition.modifier(active: ChipFly(active: true, from: from), identity: ChipFly(active: false, from: from))
            .animation(OnbMotion.bouncy)
            .combined(with: .opacity.animation(.easeOut(duration: 0.22)))
        let leave = AnyTransition.opacity.combined(with: .scale(scale: 0.8)).animation(.easeIn(duration: 0.2))
        return .asymmetric(insertion: fly, removal: leave)
    }

    /// The check on a chosen row or card: from a fifth of its size, with the bouncy spring.
    static func checkPop(reduce: Bool) -> AnyTransition {
        reduce ? .opacity : .scale(scale: 0.2).combined(with: .opacity)
    }
}

enum OnbPulsePhase { case idle, start, end }
enum OnbBumpPhase { case idle, small, back }

/// A ring growing out of what was just chosen (2 to 18 points), fading from the accent at 75% to nothing in 650 ms.
/// Nothing under Reduce Motion.
struct OnbPulse<S: Shape>: ViewModifier {
    let shape: S
    let trigger: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.overlay {
            if !reduceMotion {
                Color.clear
                    .phaseAnimator([OnbPulsePhase.idle, .start, .end], trigger: trigger) { view, phase in
                        let grow: CGFloat = phase == .end ? 16 : 0
                        view.overlay {
                            shape.stroke(Theme.gold.opacity(phase == .start ? 0.75 : 0), lineWidth: 2 + grow)
                                .padding(-(1 + grow / 2))
                        }
                    } animation: { phase in
                        phase == .end ? .easeOut(duration: 0.65) : nil
                    }
                    .allowsHitTesting(false)
            }
        }
    }
}

/// A chip popping again when its answer changes: from 0.8 back to full size with the bouncy spring.
struct OnbBump: ViewModifier {
    let trigger: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.phaseAnimator([OnbBumpPhase.idle, .small, .back], trigger: trigger) { view, phase in
                view.scaleEffect(phase == .small ? 0.8 : 1)
            } animation: { phase in
                phase == .back ? OnbMotion.bouncy : nil
            }
        }
    }
}

extension View {
    func onbEnter(_ order: Int) -> some View { modifier(OnbEnter(order: order)) }
    func onbPulse(_ shape: some Shape, trigger: Int) -> some View { modifier(OnbPulse(shape: shape, trigger: trigger)) }
    func onbBump(_ trigger: Int) -> some View { modifier(OnbBump(trigger: trigger)) }
}
