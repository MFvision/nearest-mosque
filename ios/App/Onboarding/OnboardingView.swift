import NMCore
import SwiftUI
import UIKit

/// "First Light", the welcome flow: promise, place, Asr, Adhan, Friday, weather, look, then the day built from those
/// answers, what the app offers beyond itself, exactly what leaves the device, and done. Each answer is saved through
/// the real setting the moment it is given and flies up into the "YOUR DAY" header as a chip; the sun (the moon at
/// night) is one disc through the whole flow. One column on iPhone (and iPad in portrait or split view); two panes on
/// iPad, Mac and Apple Vision Pro, the sky and the day on the leading side and the step in a glass panel. Under Reduce
/// Motion every transition is a short crossfade. The order and the rules are NMCore's OnboardingFlow.
struct OnboardingView: View {
    @Environment(\.appModel) private var app
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// "system", "light" or "dark" (Settings → Appearance, and the look step).
    @AppStorage("appearance") private var appearance = "system"
    @State private var model = OnboardingModel(app: AppModel.shared)
    /// The promise sun has risen from behind the ridge (250 ms after the flow appears).
    @State private var risen = false

    var body: some View {
        GeometryReader { geo in
            let phone = OnbContext.isPhone
            let frame = OnbFrame(size: geo.size, insets: geo.safeAreaInsets, rtl: l10n.isRTL, phone: phone)
            let onb = OnbContext(wide: frame.wide, phone: phone, still: model.still)
            screen(frame)
                .environment(\.onb, onb)
        }
        // The root places the sun in plain coordinates (left to right in every language); the step, the header and
        // the top bar set the reading direction again.
        .environment(\.layoutDirection, .leftToRight)
        .foregroundStyle(Theme.ink)
        .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
        .onAppear {
            model.reduceMotion = reduceMotion
            model.start()
            withAnimation((reduceMotion ? OnbMotion.fade : OnbMotion.soft).delay(0.25)) { risen = true }
        }
        .onDisappear { model.stop() }
        .onChange(of: reduceMotion) { _, r in model.reduceMotion = r }
        .onChange(of: app.settings.location) { old, new in if new != old { model.placeChanged(new) } }
        .sensoryFeedback(.selection, trigger: model.flights)
        .sensoryFeedback(.selection, trigger: model.flips)
        .sensoryFeedback(.success, trigger: model.allow) { _, new in new == .done }
        .sheet(item: $model.sheet) { sheet in
            sheetView(sheet)
                .environment(\.locale, l10n.locale)
                .environment(\.layoutDirection, l10n.layoutDirection)
        }
        #if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
        // Whether a watch is paired (the extras step's watch card); nothing waits for it.
        .task { WatchBridge.shared.activate() }
        #endif
        #if os(visionOS)
        .ornament(attachmentAnchor: .scene(.bottom)) {
            PageDots(count: model.flow.steps.count, index: model.flow.index(model.step) ?? 0)
                .environment(\.layoutDirection, l10n.layoutDirection)
        }
        #endif
    }

    // MARK: Layers

    @ViewBuilder private func screen(_ frame: OnbFrame) -> some View {
        let period = model.skyPeriod(wide: frame.wide)
        let weather = model.skyWeather()
        let look = WeatherLook.of(weather?.symbol, night: period.hasStars)
        let sky = SkyScene.sky(period, weather: weather, dark: scheme == .dark)
        let horizon = frame.horizon(model.step)
        ZStack(alignment: .topLeading) {
            // The skyline rises on done (on big screens the leading pane draws its own, see OnbPaneSkyline).
            let skyline = !frame.wide && model.step == .done
            // The horizon moves with the soft spring; under Reduce Motion the sky at the new horizon fades in instead.
            SkyScene(period: period, weather: weather, horizon: horizon, skyline: skyline,
                     weatherFade: model.step == .weather ? Double(OnboardingTiming.previewFade) / 1000 : 2, still: model.still)
                .animation(.easeInOut(duration: model.dragging ? 0.45 : 1.1), value: period)
                .animation(reduceMotion ? nil : OnbMotion.soft, value: horizon)
                .animation(.easeInOut(duration: 1.4).delay(0.5), value: skyline)
                .ignoresSafeArea()
                .id(reduceMotion ? horizon : 0)
                .transition(.opacity.animation(OnbMotion.fade))
            if !frame.wide { readability(sky, look: look) }
            // One disc: behind the steps on the promise and done, in front of the header elsewhere.
            hero(frame, period: period, glow: look.glowStrength)
                .zIndex(model.heroLow ? 1 : 3)
            Group {
                if frame.wide { wideLayout(frame) } else { phoneLayout(frame) }
            }
            .environment(\.layoutDirection, l10n.layoutDirection)
            .environment(\.sky, sky)
            .zIndex(2)
        }
        .frame(width: frame.size.width, height: frame.size.height)
        .coordinateSpace(.named(OnbFrame.space))
    }

    /// The text on the sky stays readable: a scrim at the bottom on the promise and done, a soft band behind the text
    /// in dark mode elsewhere (stronger over the bright day and Asr skies).
    @ViewBuilder private func readability(_ sky: Sky, look: WeatherLook) -> some View {
        let low = sky.low.mix(with: .black, by: 0.45)
        if model.step == .promise || model.step == .done {
            LinearGradient(stops: sky.dark ? [
                .init(color: low.opacity(0), location: 0.5), .init(color: low.opacity(0.55), location: 0.7), .init(color: low.opacity(0.85), location: 1),
            ] : [
                .init(color: .white.opacity(0), location: 0.5), .init(color: .white.opacity(0.5), location: 0.7), .init(color: .white.opacity(0.78), location: 1),
            ], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
            .allowsHitTesting(false)
            .transition(.opacity)
        } else if sky.dark {
            let strength: Double = {
                switch sky.period {
                case .day, .sunrise, .asr: return 0.5
                case .maghrib, .fajr: return 0.2
                default: return 0
                }
            }() * look.glowStrength
            LinearGradient(stops: [
                .init(color: low.opacity(0), location: 0.38), .init(color: low.opacity(strength), location: 0.45),
                .init(color: low.opacity(strength), location: 0.56), .init(color: low.opacity(0), location: 0.63),
            ], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
    }

    /// The sun or the moon, at its place for the step (the promise sun, the header's slot, on the arc, its true place
    /// when done), gliding with the soft spring; under Reduce Motion it fades out and in again at the new place. On
    /// the promise and done it sets behind the ridge; on the day, below the arc's ends.
    @ViewBuilder private func hero(_ frame: OnbFrame, period: SkyPeriod, glow: Double) -> some View {
        let p = model.heroPlacement(frame, period: period)
        ZStack(alignment: .topLeading) {
            // On the promise it first rises 120 points as it fades in (only fades in under Reduce Motion).
            let rising = model.step == .promise && !risen && !model.still
            HeroDisc(moon: p.moon, halo: p.halo, light: scheme == .light)
                .scaleEffect(p.size / HeroDisc.base)
                .opacity((p.big ? 0.55 + 0.45 * glow : 1) * (rising ? 0 : 1))
                .position(x: p.center.x, y: p.center.y + (rising && !reduceMotion ? 120 : 0))
                .id(reduceMotion ? [p.kind, model.arcJumps] : [0])
                .transition(.opacity.animation(OnbMotion.fade))
            if let text = p.bubble {
                Text(text)
                    .font(.footnote.weight(.semibold)).monospacedDigit()
                    .lineLimit(1)
                    .padding(.horizontal, 11).padding(.vertical, 6)
                    .glass(Capsule())
                    .fixedSize()
                    .position(x: min(max(p.center.x, p.bubbleMin), p.bubbleMax), y: p.center.y - 44)
                    .transition(.opacity)
            }
        }
        .animation(model.dragging || reduceMotion ? nil : OnbMotion.soft, value: p.center)
        .animation(model.dragging || reduceMotion ? nil : OnbMotion.soft, value: p.size)
        .frame(width: frame.size.width, height: frame.size.height)
        .mask(alignment: .topLeading) {
            Path(CGRect(x: -4000, y: -4000, width: 8000 + frame.size.width, height: 4000 + p.cut))
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: One column (iPhone, iPad in portrait or split view)

    @ViewBuilder private func phoneLayout(_ frame: OnbFrame) -> some View {
        let step = model.step
        let header = OnboardingFlow.showsHeader(step)
        ZStack(alignment: .top) {
            stepHost
                .padding(.horizontal, step == .promise || step == .done ? 28 : 24)
                .padding(.top, step == .promise ? 48 : step == .done ? 8 : 110)
                .padding(.bottom, 12)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            if header {
                OnbHeader(model: model)
                    .padding(.horizontal, 16)
                    .padding(.top, 2)
                    .frame(maxWidth: 560)
                    .transition(headerTransition)
            }
            topBar
                .padding(.horizontal, 16)
                .padding(.top, header ? 58 : 2)
                .frame(maxWidth: 560)
        }
        .animation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion), value: header)
    }

    // MARK: Two panes (iPad, Mac, Apple Vision Pro)

    @ViewBuilder private func wideLayout(_ frame: OnbFrame) -> some View {
        let step = model.step
        HStack(spacing: 0) {
            ZStack(alignment: .top) {
                if step == .friday || step == .done {
                    OnbPaneSkyline(frame: frame, horizon: frame.horizon(step))
                        .transition(.opacity)
                }
                if [.day, .privacy, .done].contains(step), model.hasArc {
                    OnbArcPane(model: model, frame: frame)
                        .transition(.opacity)
                }
                if OnboardingFlow.showsHeader(step) {
                    OnbHeader(model: model)
                        .padding(.horizontal, 24)
                        .padding(.top, 18)
                        .transition(headerTransition)
                }
            }
            .frame(width: frame.paneWidth)
            .frame(maxHeight: .infinity, alignment: .top)
            VStack(spacing: 0) {
                let bar = model.flow.back(step) != nil || model.flow.skip(step) != nil || step == .promise
                if bar { topBar.frame(height: 44) }
                stepHost
            }
            .padding(.top, model.flow.back(step) != nil || model.flow.skip(step) != nil || step == .promise ? 16 : 28)
            .padding(.horizontal, 32)
            .padding(.bottom, 28)
            .frame(maxWidth: 440)
            .frame(minHeight: max(0, min(360, frame.size.height - 48)))
            .glass(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .animation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion), value: model.pageHeight)
            .padding(.vertical, 24)
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .animation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion), value: OnboardingFlow.showsHeader(step))
    }

    /// The header fades away when done: up 14, a little smaller, blurred (450 ms).
    private var headerTransition: AnyTransition {
        reduceMotion ? .opacity : .opacity.combined(with: .offset(y: -14)).combined(with: .scale(scale: 0.97))
    }

    // MARK: The step

    private var stepHost: some View {
        Group {
            switch model.step {
            case .promise: PromiseStep(model: model)
            case .place: PlaceStep(model: model)
            case .asr: AsrStep(model: model)
            case .adhan: AdhanStep(model: model)
            case .friday: FridayStep(model: model)
            case .weather: WeatherStep(model: model)
            case .look: LookStep(model: model)
            case .building: BuildingStep(model: model)
            case .day: DayStep(model: model)
            case .extras: ExtrasStep(model: model)
            case .more: MoreStep(model: model)
            case .privacy: PrivacyStep(model: model)
            case .done: DoneStep(model: model) { finish() }
            }
        }
        .id(model.step)
        .opacity(model.leaving ? 0 : 1)
        .offset(y: model.leaving && !reduceMotion ? -12 : 0)
        .blur(radius: model.leaving && !reduceMotion ? 6 : 0)
    }

    /// Back (where there is a step to go back to), Skip (on the questions after the place and on the showcase), and
    /// the language on the promise.
    private var topBar: some View {
        HStack(spacing: 8) {
            if model.flow.back(model.step) != nil {
                Button { model.back() } label: {
                    Image(systemName: "chevron.backward")
                        .font(.body.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.onbPress)
                .glass(Circle())
                .hoverEffect(.highlight)
                .keyboardShortcut(.cancelAction)
                .accessibilityLabel(l10n.t("onb_back"))
                .transition(.opacity)
            }
            Spacer(minLength: 0)
            if model.flow.skip(model.step) != nil {
                Button { model.skip() } label: {
                    Text(l10n.t("onb_skip"))
                        .font(.body.weight(.semibold))
                        .lineLimit(1)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .contentShape(Capsule())
                }
                .buttonStyle(.onbPress)
                .hoverEffect(.highlight)
                .modifier(OnSky())
                .transition(.opacity)
            }
            if model.step == .promise {
                LanguageMenu()
                    .transition(.opacity)
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
        .animation(OnbMotion.fade, value: model.step)
    }

    @ViewBuilder private func sheetView(_ sheet: OnbSheet) -> some View {
        switch sheet {
        case .city:
            CityPickerView()
        case .methods:
            MethodListSheet(model: model)
        case .audio:
            NavigationStack {
                PrayerAudioSettingsView()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button { model.sheet = nil } label: { Label(l10n.t("done"), systemImage: "checkmark") }
                        }
                    }
            }
        }
    }

    private func finish() {
        model.stop()
        app.settings.onboarded = true
        dismiss()
    }
}

// MARK: - Context and geometry

/// What the steps need to know about where they are drawn.
struct OnbContext: Equatable {
    /// Two panes (iPad, Mac, Apple Vision Pro in a wide window).
    var wide = false
    /// iPhone: the strings that say "phone"; iPad, Mac and Vision Pro use the "_device" variants.
    var phone = true
    /// Debug screenshots (-demoOnboardingStill): no timers, no entering motion.
    var still = false

    @MainActor static var isPhone: Bool { UIDevice.current.userInterfaceIdiom == .phone }

    /// `key`, or its "_device" variant away from a phone.
    func device(_ key: String) -> String { phone ? key : key + "_device" }
}

private struct OnbContextKey: EnvironmentKey { static let defaultValue = OnbContext() }

extension EnvironmentValues {
    var onb: OnbContext {
        get { self[OnbContextKey.self] }
        set { self[OnbContextKey.self] = newValue }
    }
}

/// The screen in the root's coordinates: the safe area's size, the insets around it (the sky reaches under them), and
/// the two panes on big screens (the leading one 58% wide, on the right in right-to-left languages).
struct OnbFrame {
    static let space = "onboarding"
    let size: CGSize
    let insets: EdgeInsets
    let rtl: Bool
    /// An iPhone keeps one column, also turned sideways.
    var phone = false

    var fullHeight: CGFloat { size.height + insets.top + insets.bottom }
    var wide: Bool { !phone && size.width >= 700 && size.width > size.height * 1.05 }
    var paneWidth: CGFloat { (size.width * 0.58).rounded() }
    var paneMinX: CGFloat { rtl ? size.width - paneWidth : 0 }
    var paneMidX: CGFloat { wide ? paneMinX + paneWidth / 2 : size.width / 2 }

    /// A height given as a fraction of the whole screen, in the root's coordinates (which start below the top inset).
    func y(_ fraction: CGFloat) -> CGFloat { fraction * fullHeight - insets.top }

    /// The horizon as a fraction of the whole screen: low on the promise and done, higher on the questions; on big
    /// screens it sits below the large arc on the day, privacy and done.
    func horizon(_ step: OnboardingStep) -> CGFloat {
        if wide {
            switch step {
            case .promise: return 0.80
            case .day, .privacy, .done:
                let base = insets.top + size.height * 0.56
                return min(1 - 70 / max(fullHeight, 1), (base + 200) / max(fullHeight, 1))
            default: return 0.70
            }
        }
        return step == .promise || step == .done ? 0.78 : 0.64
    }
}

/// The day's arc: a circle arc with a 70° half-angle over the chord, its ends 34 points above the box's bottom (the
/// labels go below). Time runs in the reading direction. Used by the arc views and by the sun riding it.
struct OnbArcGeometry: Equatable {
    static let halfAngle = 70.0
    let size: CGSize
    let chord: CGFloat
    let rtl: Bool

    // Plain Double trigonometry converted once: mixing CGFloat and Double in sin/cos is ambiguous for some compilers.
    private static let sinHalf = CGFloat(sin(halfAngle * .pi / 180))
    private static let cosHalf = CGFloat(cos(halfAngle * .pi / 180))

    var radius: CGFloat { chord / (2 * Self.sinHalf) }
    var rise: CGFloat { radius * (1 - Self.cosHalf) }
    var base: CGFloat { size.height - 34 }
    var center: CGPoint { CGPoint(x: size.width / 2, y: base + radius * Self.cosHalf) }

    /// The box height that leaves room for the disc above the arc.
    static func height(chord: CGFloat) -> CGFloat {
        OnbArcGeometry(size: .zero, chord: chord, rtl: false).rise + 76
    }

    func angle(_ fraction: Double) -> Double { (rtl ? -1 : 1) * (-Self.halfAngle + 2 * Self.halfAngle * fraction) }

    func point(_ fraction: Double, radius r: CGFloat? = nil) -> CGPoint {
        let a = angle(fraction) * .pi / 180
        let rr = r ?? radius
        return CGPoint(x: center.x + rr * CGFloat(sin(a)), y: center.y - rr * CGFloat(cos(a)))
    }

    /// The fraction under a finger at `x` (past the ends it keeps growing, so a drag can go over the edge).
    func fraction(atX x: CGFloat) -> Double {
        let u = Double((x - center.x) / radius) * (rtl ? -1 : 1)
        let lim = sin(Self.halfAngle * .pi / 180)
        let theta: Double = abs(u) <= lim ? asin(u) * 180 / .pi : (u > 0 ? 1 : -1) * (Self.halfAngle + (abs(u) - lim) * 180 / .pi)
        return (theta + Self.halfAngle) / (2 * Self.halfAngle)
    }
}

/// Where the sun or the moon is drawn.
struct HeroPlacement: Equatable {
    var center: CGPoint
    var size: CGFloat
    var moon: Bool
    /// The promise and done: the big disc with its breathing halo, dimmed under cloud.
    var big: Bool
    var halo: Bool { big }
    /// Below this the disc is hidden (behind the ridge, below the arc's ends).
    var cut: CGFloat
    /// For Reduce Motion: a new kind of place fades in instead of gliding.
    var kind: Int
    /// "Maghrib · 6:12 PM" above the disc while it is dragged, kept inside the pane.
    var bubble: String?
    var bubbleMin: CGFloat = 0
    var bubbleMax: CGFloat = 0
}

enum OnbSheet: String, Identifiable {
    case city, methods, audio
    var id: String { rawValue }
}

enum AllowPhase: Equatable { case idle, working, done }

/// The answers' chips in the header, in their order.
enum OnbChip: String, CaseIterable {
    case city, method, asr, adhan, friday, weather, look
}

// MARK: - Kept from the tour

/// Language choice on the promise: the device language or any supported language.
struct LanguageMenu: View {
    @Environment(\.l10n) private var l10n

    var body: some View {
        @Bindable var l10n = l10n
        Menu {
            Button { l10n.override = nil } label: {
                if l10n.override == nil { Label(l10n.t("use_device_language"), systemImage: "checkmark") } else { Text(l10n.t("use_device_language")) }
            }
            ForEach(Localization.supported, id: \.self) { code in
                Button { l10n.override = code } label: {
                    let name = Localization.nativeNames[code] ?? code
                    if l10n.override == code { Label(name, systemImage: "checkmark") } else { Text(name) }
                }
            }
        } label: {
            Label(Localization.nativeNames[l10n.language] ?? l10n.language, systemImage: "globe")
                .font(.subheadline.weight(.medium))
                .frame(minHeight: 30)
        }
        .glassButton()
        .accessibilityLabel(l10n.t("language"))
        .accessibilityValue(Localization.nativeNames[l10n.language] ?? l10n.language)
    }
}

/// Where in the flow: an ornament below the window on Apple Vision Pro.
struct PageDots: View {
    @Environment(\.l10n) private var l10n
    let count: Int
    let index: Int
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { i in
                Capsule().fill(i == index ? Theme.gold : Theme.ink.opacity(0.4))
                    .frame(width: i == index ? 22 : 7, height: 7)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .glass(Capsule())
        .animation(Theme.spring, value: index)
        .accessibilityElement()
        .accessibilityLabel(l10n.t("onb_page_a11y", index + 1, count))
    }
}
