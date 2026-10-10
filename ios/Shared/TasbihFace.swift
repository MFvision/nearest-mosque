import SwiftUI
import WidgetKit

/// The tasbih counter's face, shared by the widget and the app's debug gallery: the whole count as one large
/// rounded number (no words, like a hand counter), a big round button whose ring fills over each 33, and a
/// reset arrow. The widget wraps the circle and the arrow in its count and reset intents.
struct TasbihFace: View {
    let count: Int
    let palette: WidgetPalette
    let family: WidgetFamily
    let locale: Locale
    let a11y: String
    let resetLabel: String
    var wrapTap: (AnyView) -> AnyView = { $0 }
    var wrapReset: (AnyView) -> AnyView = { $0 }
    static let round = 33

    var body: some View {
        let p = palette
        let inRound = count % Self.round
        let progress = count > 0 && inRound == 0 ? 1 : Double(inRound) / Double(Self.round)
        let number = count.formatted(.number.locale(locale).grouping(.never))
        Group {
            switch family {
            #if !targetEnvironment(macCatalyst)
            case .accessoryCircular:
                wrapTap(AnyView(
                    ZStack {
                        AccessoryWidgetBackground()
                        Circle().trim(from: 0, to: progress).stroke(style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90)).padding(3)
                        Text(number).font(.system(size: 16, weight: .heavy, design: .rounded)).minimumScaleFactor(0.5).padding(6)
                    }
                ))
                .accessibilityLabel(a11y)
            #endif
            case .systemMedium:
                HStack(spacing: 16) {
                    VStack(alignment: .leading) {
                        reset(p)
                        Spacer(minLength: 0)
                        Text(number).font(.system(size: 64, weight: .heavy, design: .rounded)).monospacedDigit()
                            .foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.4)
                            .shadow(color: .black.opacity(0.18), radius: 6, y: 3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    tapCircle(p, progress: progress)
                }
            default:
                VStack(spacing: 8) {
                    HStack(alignment: .top, spacing: 4) {
                        reset(p)
                        Spacer(minLength: 0)
                        Text(number).font(.system(size: 44, weight: .heavy, design: .rounded)).monospacedDigit()
                            .foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.4)
                            .shadow(color: .black.opacity(0.18), radius: 5, y: 2)
                    }
                    tapCircle(p, progress: progress)
                }
            }
        }
        // A counter reads the same way in every language.
        .environment(\.layoutDirection, .leftToRight)
    }

    private func reset(_ p: WidgetPalette) -> some View {
        wrapReset(AnyView(
            Image(systemName: "arrow.clockwise")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(p.ink.opacity(0.85))
                .frame(width: 44, height: 44, alignment: .topLeading)
                .contentShape(Rectangle())
        ))
        .accessibilityLabel(resetLabel)
    }

    /// The big round button with the progress ring of the current 33.
    private func tapCircle(_ p: WidgetPalette, progress: Double) -> some View {
        wrapTap(AnyView(
            ZStack {
                Circle().fill(p.ink.opacity(0.10))
                Circle().stroke(p.ink.opacity(0.22), lineWidth: 6)
                Circle().trim(from: 0, to: progress)
                    .stroke(p.accent, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Circle().fill(p.ink.opacity(0.18)).padding(14)
            }
            .shadow(color: p.accent.opacity(0.35), radius: 10)
            .aspectRatio(1, contentMode: .fit)
            .contentShape(Circle())
        ))
        .accessibilityLabel(a11y)
    }
}
