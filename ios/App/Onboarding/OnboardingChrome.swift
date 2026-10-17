import NMCore
import SwiftUI

// The onboarding's pieces: the sun and moon, the "YOUR DAY" header with its chips, the glass rows and switches, the
// page that scrolls with its main button pinned, the Friday picture and the Allow button that turns into a check.

/// Colours of the onboarding beyond the theme (shared/design/tokens.json "color": sunCore, sunMid, sunEdge,
/// moonlight, moonlightLight).
enum OnbColor {
    static let sunCore = Color(hex: 0xFFF6DA)
    static let sunMid = Color(hex: 0xF7C76A)
    static let sunEdge = Color(hex: 0xE9A23B)
    static let moonlight = Color(hex: 0xC9D8FF)
    static let moonlightLight = Color(hex: 0x8EA4D6)
    /// Secondary text on the sky and on glass: 80% white in dark mode, the token grey on the light skies.
    static let secondary = Theme.dynamic(dark: .white.opacity(0.8), light: Theme.textSecondaryLight)
    static let chip = Theme.dynamic(dark: .white.opacity(0.14), light: .white.opacity(0.62))
    static let line = Theme.dynamic(dark: .white.opacity(0.28), light: Theme.textLight.opacity(0.10))
    static let placeholder = Theme.dynamic(dark: .white.opacity(0.36), light: Theme.textLight.opacity(0.26))
    static let track = Theme.dynamic(dark: .white.opacity(0.26), light: Theme.textLight.opacity(0.20))
    /// The "could not find you" line (error red on the light skies).
    static let error = Theme.dynamic(dark: Color(hex: 0xF2B8B5), light: Color(hex: 0xB3261E))
}

// MARK: - The sun and the moon

/// The disc that stays through the flow: a sun of three golds with a glow, or a crescent of moonlight; on the promise
/// and done a soft halo breathes (5.5 s), still under Reduce Motion. Drawn at 92 points and scaled.
struct HeroDisc: View {
    static let base: CGFloat = 92
    let moon: Bool
    var halo = false
    var light = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breathe = false

    var body: some View {
        let s = Self.base
        ZStack {
            Circle()
                .fill(RadialGradient(colors: [(moon ? OnbColor.moonlight.opacity(0.26) : OnbColor.sunMid.opacity(0.38)), .clear],
                                     center: .center, startRadius: 0, endRadius: s * 0.87))
                .frame(width: s * 1.74, height: s * 1.74)
                .scaleEffect(breathe ? 1.08 : 1)
                .opacity(halo ? 1 : 0)
            Circle()
                .fill(RadialGradient(stops: [.init(color: OnbColor.sunCore, location: 0), .init(color: OnbColor.sunMid, location: 0.58),
                                             .init(color: OnbColor.sunEdge, location: 1)],
                                     center: UnitPoint(x: 0.5, y: 0.46), startRadius: 0, endRadius: s / 2))
                .frame(width: s, height: s)
                .shadow(color: OnbColor.sunMid.opacity(0.45), radius: 30)
                .opacity(moon ? 0 : 1)
            Crescent()
                .fill(light ? OnbColor.moonlightLight : OnbColor.moonlight)
                .frame(width: s, height: s)
                .rotationEffect(.degrees(-22))
                .shadow(color: (light ? OnbColor.moonlightLight : OnbColor.moonlight).opacity(0.6), radius: 16)
                .opacity(moon ? 1 : 0)
        }
        .frame(width: s, height: s)
        .animation(.easeInOut(duration: 0.7), value: moon)
        .animation(.easeInOut(duration: 0.7), value: halo)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.75).repeatForever(autoreverses: true)) { breathe = true }
        }
        .accessibilityHidden(true)
    }
}

/// A crescent: the disc less the same disc moved up and to the leading side, so the lit edge is low on the right.
struct Crescent: Shape {
    func path(in r: CGRect) -> Path {
        // Drawn for the 92-point disc and scaled.
        let k = r.width / 92
        return Path(ellipseIn: r).subtracting(Path(ellipseIn: r.offsetBy(dx: -21 * k, dy: -7 * k)))
    }
}

// MARK: - Header

/// "YOUR DAY": a glass capsule with the sun's slot, the eyebrow and the answers as chips (scrolling sideways when they
/// do not fit). One element for VoiceOver ("Your day: Riyadh, Umm al-Qura, Standard, …").
struct OnbHeader: View {
    let model: OnboardingModel
    @Environment(\.l10n) private var l10n
    @Environment(\.onb) private var onb
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let slots = model.headerSlots()
        let filled = slots.filter { $0.placeholder == nil }
        HStack(spacing: 8) {
            // The sun sits here (OnboardingView draws it, from this frame).
            Color.clear
                .frame(width: 22, height: 22)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(OnbFrame.space)) } action: { model.slotFrame = $0 }
            Text(l10n.t("onb2_your_day"))
                .font(.system(size: 11, weight: .bold))
                // Capitals and letter spacing only for scripts that have them (never on Arabic script).
                .textCase(l10n.isRTL ? nil : .uppercase)
                .tracking(l10n.isRTL ? 0 : 0.88)
                .foregroundStyle(Theme.accent)
                .lineLimit(1)
                .fixedSize()
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    HStack(spacing: 6) {
                        ForEach(slots) { slot in
                            Group {
                                if let w = slot.placeholder {
                                    Capsule()
                                        .strokeBorder(OnbColor.placeholder, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                                        .frame(width: w, height: onb.wide ? 30 : 28)
                                        .transition(.opacity)
                                } else {
                                    OnbChipView(chip: slot.chip, text: slot.text)
                                        .onbBump(model.bumps[slot.chip] ?? 0)
                                        .transition(.chipFly(from: CGSize(width: onb.wide ? 150 : 0, height: onb.wide ? 300 : 260), reduce: reduceMotion))
                                }
                            }
                            .id(slot.id)
                        }
                    }
                    .padding(.leading, 2)
                    .padding(.trailing, 12)
                    .frame(maxHeight: .infinity)
                }
                .scrollIndicators(.hidden)
                // Chips fly in from far below the capsule: not clipped vertically, only faded at the sides.
                .scrollClipDisabled()
                .mask {
                    HStack(spacing: 0) {
                        LinearGradient(colors: [.clear, .black], startPoint: .leading, endPoint: .trailing).frame(width: 10)
                        Rectangle()
                        LinearGradient(colors: [.black, .clear], startPoint: .leading, endPoint: .trailing).frame(width: 10)
                    }
                    .padding(.vertical, -400)
                    .environment(\.layoutDirection, .leftToRight)
                }
                .onChange(of: filled.count) { _, _ in
                    guard let last = filled.last else { return }
                    withAnimation(OnbMotion.motion(OnbMotion.spring, reduce: reduceMotion)) { proxy.scrollTo(last.id, anchor: .trailing) }
                }
            }
        }
        .padding(.leading, 12)
        .frame(height: onb.wide ? 50 : 46)
        .frame(maxWidth: .infinity)
        .glass(Capsule())
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(l10n.t("onb2_your_day") + ": " + filled.map(\.text).joined(separator: l10n.isRTL ? "، " : ", "))
    }
}

/// One answer in the header: its symbol in the accent and its name (at most 140 points, then an ellipsis).
struct OnbChipView: View {
    let chip: OnbChip
    let text: String
    @Environment(\.onb) private var onb

    var body: some View {
        // The header scrolls sideways, so its chips are offered unlimited width: cap it here so long names truncate.
        let cap = OnbMaxWidth(width: onb.wide ? 150 : 140)
        cap {
            HStack(spacing: 5) {
                OnbIconImage(icon: Self.icon(chip), size: 13)
                    .foregroundStyle(Theme.accent)
                Text(text)
                    .font(.footnote.weight(.semibold))
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .padding(.leading, 8)
            .padding(.trailing, 10)
            .frame(height: onb.wide ? 30 : 28)
            .background(OnbColor.chip, in: Capsule())
            .overlay(Capsule().strokeBorder(OnbColor.line, lineWidth: 1))
        }
    }

    static func icon(_ chip: OnbChip) -> OnbIcon {
        switch chip {
        case .city: return .symbol("mappin.and.ellipse")
        case .method: return .symbol("angle")
        case .asr: return .symbol("sun.min")
        case .adhan: return .symbol("waveform")
        case .friday: return .asset("MosqueTab")
        case .weather: return .symbol("cloud.sun")
        case .look: return .symbol("paintpalette")
        }
    }
}

/// Offers its content at most `width`, even inside a horizontal scroll view (which offers unlimited width).
struct OnbMaxWidth: Layout {
    let width: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let v = subviews.first else { return .zero }
        return v.sizeThatFits(ProposedViewSize(width: min(proposal.width ?? width, width), height: proposal.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: bounds.origin, proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
    }
}

// MARK: - Icons

/// An SF Symbol or one of the app's template images (the mosque).
enum OnbIcon: Equatable {
    case symbol(String)
    case asset(String)
}

struct OnbIconImage: View {
    let icon: OnbIcon
    var size: CGFloat = 18

    var body: some View {
        switch icon {
        case let .symbol(name):
            Image(systemName: name).font(.system(size: size, weight: .semibold))
        case let .asset(name):
            Image(decorative: name).renderingMode(.template).resizable().scaledToFit().frame(width: size * 1.15, height: size * 1.15)
        }
    }
}

/// The accent icon in a soft accent circle, at the start of rows, cards and tiles.
struct OnbIconCircle: View {
    let icon: OnbIcon
    var size: CGFloat = 40

    var body: some View {
        OnbIconImage(icon: icon, size: size * 0.45)
            .foregroundStyle(Theme.accent)
            .frame(width: size, height: size)
            .background(Theme.gold.opacity(0.18), in: Circle())
            .accessibilityHidden(true)
    }
}

// MARK: - Text

/// Text on the sky in dark mode gets a soft shadow (on glass it does not need one).
struct OnSky: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.onb) private var onb

    func body(content: Content) -> some View {
        content.shadow(color: .black.opacity(scheme == .dark && !onb.wide ? 0.3 : 0), radius: 7)
    }
}

struct OnbTitle: View {
    let text: String
    var large = false

    var body: some View {
        Text(text)
            .font(large ? .largeTitle.bold() : .title.bold())
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
            .modifier(OnSky())
    }
}

struct OnbBodyText: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.body)
            .foregroundStyle(OnbColor.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(OnSky())
    }
}

struct OnbFootnote: View {
    let text: String
    var color: Color = OnbColor.secondary

    var body: some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(color)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(OnSky())
    }
}

/// A small accent label above a title ("BUILT FROM YOUR ANSWERS"): capitals only for scripts that have them.
struct OnbEyebrow: View {
    let text: String
    @Environment(\.l10n) private var l10n

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .textCase(l10n.isRTL ? nil : .uppercase)
            .tracking(l10n.isRTL ? 0 : 0.96)
            .foregroundStyle(Theme.accent)
            .modifier(OnSky())
    }
}

// MARK: - Page

/// A step's page: the content scrolls when it does not fit (from the top, or from the bottom on the promise and
/// done), the main button stays below it. On big screens the glass panel hugs the content.
struct OnbPage<Content: View, Footer: View>: View {
    let model: OnboardingModel
    var bottom = false
    @ViewBuilder var content: Content
    @ViewBuilder var footer: Footer
    @Environment(\.onb) private var onb

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { geo in
                ScrollView {
                    content
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, 4)
                        .frame(minHeight: onb.wide ? 0 : geo.size.height, alignment: bottom ? .bottom : .top)
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { if onb.wide { model.pageHeight = $0 } }
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            .frame(maxHeight: onb.wide && model.pageHeight > 0 ? model.pageHeight : .infinity)
            footer
                .padding(.top, 12)
        }
    }
}

/// The step's main button: gold, full width, two lines at most; Return presses it on a keyboard.
struct OnbPrimaryButton: View {
    let title: String
    var shortcut = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 30)
        }
        .prominentButton()
        .controlSize(.large)
        .keyboardShortcut(shortcut ? .defaultAction : nil)
        .onbEnter(4)
    }
}

/// A full-width glass button (the second of two equal choices).
struct OnbSecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 30)
        }
        .glassButton()
        .controlSize(.large)
    }
}

// MARK: - Rows

/// The check on what is chosen: a gold disc that pops in.
struct OnbCheck: View {
    var size: CGFloat = 24

    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: size * 0.5, weight: .bold))
            .foregroundStyle(Theme.navyNight)
            .frame(width: size, height: size)
            .background(Theme.gold, in: Circle())
            .accessibilityHidden(true)
    }
}

/// A glass row that does something: the accent icon, a title and a line under it, and at its end a chevron, a
/// spinner or the check. Chosen: ringed in the accent; a pulse grows out of it when just chosen.
struct OnbRow<Trailing: View>: View {
    let icon: OnbIcon
    let title: String
    var subtitle: String?
    var selected = false
    var pulse = 0
    let action: () -> Void
    @ViewBuilder var trailing: Trailing
    @Environment(\.onb) private var onb

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button(action: action) {
            HStack(spacing: 14) {
                OnbIconCircle(icon: icon)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    if let subtitle {
                        Text(subtitle).font(.footnote).foregroundStyle(OnbColor.secondary)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                trailing.frame(minWidth: 24, minHeight: 24)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
            .contentShape(shape)
        }
        .buttonStyle(.onbPress(shape))
        .glass(shape)
        .overlay { if selected { shape.strokeBorder(Theme.gold, lineWidth: 2).allowsHitTesting(false) } }
        .onbPulse(shape, trigger: pulse)
        .hoverEffect(.highlight)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// A glass row with a switch; the whole row turns it on and off (VoiceOver and Full Keyboard Access see a switch).
struct OnbSwitchRow: View {
    var icon: OnbIcon?
    let title: String
    var subtitle: String?
    let isOn: Bool
    let set: (Bool) -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button { set(!isOn) } label: {
            HStack(spacing: 14) {
                if let icon { OnbIconCircle(icon: icon) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    if let subtitle {
                        Text(subtitle).font(.footnote).foregroundStyle(OnbColor.secondary)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Toggle("", isOn: .constant(isOn))
                    .toggleStyle(.switch)
                    .labelsHidden()
                    .tint(Theme.gold)
                    .allowsHitTesting(false)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
            .contentShape(shape)
        }
        .buttonStyle(.onbPress(shape))
        .glass(shape)
        .hoverEffect(.highlight)
        .accessibilityRepresentation {
            Toggle(isOn: Binding(get: { isOn }, set: set)) {
                Text(title)
            }
            .accessibilityHint(subtitle ?? "")
        }
    }
}

/// A glass card that only shows something (the extras and more steps).
struct OnbCard<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

// MARK: - Friday picture

/// The Friday step on one column: midday sky with a soft glow and the mosque skyline rising in (no sun disc: the
/// sun stays in the header). Never mirrored; hidden from VoiceOver.
struct FridayArt: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onb) private var onb
    @State private var risen = false

    var body: some View {
        let sky = Sky.of(.day, dark: scheme == .dark)
        let up = risen || onb.still
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let sw = w * 0.62, sh = min(h * 0.84, sw / 1.9)
            ZStack(alignment: .bottom) {
                LinearGradient(colors: [sky.top, sky.mid, sky.low], startPoint: .top, endPoint: .bottom)
                RadialGradient(colors: [sky.glow.opacity(0.75), sky.glow.opacity(0)], center: UnitPoint(x: 0.5, y: 0.18),
                               startRadius: 0, endRadius: w * 0.5)
                MosqueSkyline()
                    .fill(sky.dark ? sky.low.mix(with: .black, by: 0.35) : Theme.textLight.opacity(0.22))
                    .frame(width: sw, height: sh)
                    .offset(y: up || reduceMotion ? 0 : 12)
                    .opacity(up ? 1 : 0)
            }
            .frame(width: w, height: h)
        }
        .frame(height: 132)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(OnbColor.line, lineWidth: 1))
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
        .onAppear {
            guard !up else { return }
            withAnimation(OnbMotion.motion(OnbMotion.soft, reduce: reduceMotion).delay(0.12)) { risen = true }
        }
    }
}

/// Big screens: the mosque on the far ridge of the leading pane, under the midday sky on Friday and when done. It rises
/// 12 points with the soft spring after 120 ms (fades in under Reduce Motion). Never mirrored; hidden from VoiceOver.
struct OnbPaneSkyline: View {
    let frame: OnbFrame
    /// The horizon as a fraction of the whole screen (as the sky draws it).
    let horizon: CGFloat
    @Environment(\.sky) private var sky
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onb) private var onb
    @State private var risen = false

    var body: some View {
        let up = risen || onb.still
        let width = frame.paneWidth * 0.5
        let height = min(frame.fullHeight * 0.15, width / 1.9)
        // On the far ridge, as SkyBackdrop places its skyline.
        let bottom = frame.y(horizon) - frame.fullHeight * 0.13 + frame.fullHeight * 0.15
        MosqueSkyline()
            .fill(sky.low.mix(with: .black, by: 0.25 * sky.shade))
            .frame(width: width, height: height)
            .position(x: frame.paneWidth / 2, y: bottom - height / 2 + (up || reduceMotion ? 0 : 12))
            .opacity(up ? 1 : 0)
            .frame(width: frame.paneWidth, height: frame.size.height, alignment: .topLeading)
            .environment(\.layoutDirection, .leftToRight)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear {
                guard !up else { return }
                withAnimation(OnbMotion.motion(OnbMotion.soft, reduce: reduceMotion).delay(0.12)) { risen = true }
            }
    }
}

// MARK: - Allow

/// The privacy step's button: pressed, it shrinks into a 54-point circle with a spinner, then a check pops.
struct OnbMorphButton: View {
    let title: String
    let phase: AllowPhase
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .opacity(phase == .idle ? 1 : 0)
                if phase == .working {
                    ProgressView().tint(Theme.navyNight).transition(.opacity)
                }
                if phase == .done {
                    Image(systemName: "checkmark")
                        .font(.title3.weight(.bold))
                        .transition(.checkPop(reduce: reduceMotion))
                }
            }
            .frame(maxWidth: phase == .idle ? .infinity : 30, minHeight: 30)
        }
        .prominentButton()
        .controlSize(.large)
        .frame(maxWidth: phase == .idle ? .infinity : 54)
        .keyboardShortcut(.defaultAction)
        .allowsHitTesting(phase == .idle)
        .accessibilityLabel(title)
    }
}
