import SwiftUI

/// What the Ask pipeline is doing right now. Each step is real: rephrasing runs only with Apple
/// Intelligence, writing only when the on-device model writes an answer.
enum AskStage: Int, CaseIterable, Comparable {
    case understanding, searching, reading, writing

    var key: String {
        switch self {
        case .understanding: return "ask_stage_understanding"
        case .searching: return "ask_stage_searching"
        case .reading: return "ask_stage_reading"
        case .writing: return "ask_stage_writing"
        }
    }

    static func < (a: AskStage, b: AskStage) -> Bool { a.rawValue < b.rawValue }
}

/// The waiting card: the logo breathing inside a turning gold ring, the current step shimmering, and the
/// steps already done with a check. Under Reduce Motion the ring and logo stay still.
struct ThinkingView: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let stage: AskStage
    let done: [AskStage]
    var onStop: () -> Void
    @State private var spin = false
    @State private var breathe = false

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .stroke(AngularGradient(colors: [Theme.gold.opacity(0), Theme.gold, Theme.gold.opacity(0)], center: .center), lineWidth: 3)
                    .rotationEffect(.degrees(spin ? 360 : 0))
                Image("LogoMark").resizable().scaledToFit().frame(width: 26, height: 26)
                    .scaleEffect(breathe ? 1.08 : 0.92)
                    .opacity(breathe ? 1 : 0.75)
            }
            .frame(width: 44, height: 44)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                ForEach(done, id: \.self) { s in
                    Label(l10n.t(s.key), systemImage: "checkmark").font(.footnote).foregroundStyle(Theme.ink.opacity(0.7))
                        .transition(.opacity)
                }
                Text(l10n.t(stage.key) + "…").font(.subheadline.weight(.semibold))
                    .modifier(Shimmer(active: !reduceMotion))
                    .id(stage)
                    .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity), removal: .opacity))
                    .accessibilityAddTraits(.updatesFrequently)
            }
            .animation(reduceMotion ? nil : Theme.spring, value: stage)
            Spacer(minLength: 0)
            Button(l10n.t("stop"), action: onStop).glassButton()
        }
        .glassCard(padding: 12)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) { spin = true }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { breathe = true }
        }
    }
}

/// A soft light sweeping across text.
private struct Shimmer: ViewModifier {
    let active: Bool
    @State private var x: CGFloat = -1

    func body(content: Content) -> some View {
        if active {
            content
                .overlay {
                    GeometryReader { g in
                        LinearGradient(colors: [.clear, Theme.gold.opacity(0.9), .clear], startPoint: .leading, endPoint: .trailing)
                            .frame(width: g.size.width * 0.5)
                            .offset(x: x * g.size.width * 1.5)
                    }
                    .mask(content)
                    .allowsHitTesting(false)
                }
                .onAppear { withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { x = 1 } }
        } else {
            content
        }
    }
}
