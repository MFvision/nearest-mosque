import SwiftUI

/// The tasbih: 33 beads round the count that light up one by one. Turn the Digital Crown, tap the circle, or
/// double-tap with two fingers (Series 9, Ultra 2 and later) to count; a light tap on the wrist at each count and
/// a stronger one every 33. Above the number, the dhikr of the round (subḥān Allāh, al-ḥamdu lillāh, Allāhu akbar).
struct TasbihPage: View {
    @Environment(WatchModel.self) private var model
    @State private var crown = 0.0
    @State private var lastStep = 0
    @State private var bump = false

    /// The words themselves, the same in every language.
    static let dhikr = ["سُبْحَانَ ٱللَّٰهِ", "ٱلْحَمْدُ لِلَّٰهِ", "ٱللَّٰهُ أَكْبَرُ"]

    var body: some View {
        let count = model.extras.tasbih
        let inRound = count % 33
        let lit = count > 0 && inRound == 0 ? 33 : inRound
        let round = (max(0, count - 1) / 33) % 3
        let l10n = model.l10n
        VStack(spacing: 4) {
            Button {
                count1()
            } label: {
                GeometryReader { g in
                    let d = min(g.size.width, g.size.height)
                    let r = d / 2 - 5
                    ZStack {
                        Circle().fill(.ultraThinMaterial)
                        Circle().fill(RadialGradient(colors: [model.accent.opacity(0.28), .clear], center: .center, startRadius: 0, endRadius: d / 2))
                        ForEach(0..<33, id: \.self) { i in
                            let on = i < lit
                            Circle()
                                .fill(on ? AnyShapeStyle(RadialGradient(colors: [.white, model.accent], center: .center, startRadius: 0, endRadius: 4))
                                         : AnyShapeStyle(Color.white.opacity(0.18)))
                                .frame(width: on ? 7 : 5, height: on ? 7 : 5)
                                .shadow(color: on ? model.accent.opacity(0.9) : .clear, radius: 3)
                                .offset(y: -r)
                                .rotationEffect(.degrees(Double(i) * 360 / 33))
                        }
                        VStack(spacing: 0) {
                            Text(Self.dhikr[round]).font(.system(size: 13, weight: .semibold)).foregroundStyle(model.accent)
                                .environment(\.layoutDirection, .rightToLeft)
                            Text(count.formatted(.number.locale(l10n.locale).grouping(.never)))
                                .font(.system(size: 42, weight: .heavy, design: .rounded)).monospacedDigit()
                                .minimumScaleFactor(0.4).lineLimit(1)
                                .contentTransition(.numericText(value: Double(count)))
                                .scaleEffect(bump ? 1.08 : 1)
                        }
                        .padding(18)
                    }
                    .frame(width: d, height: d)
                    .position(x: g.size.width / 2, y: g.size.height / 2)
                }
                .environment(\.layoutDirection, .leftToRight)
            }
            .buttonStyle(.plain)
            .handGestureShortcut(.primaryAction)
            .accessibilityLabel(l10n.t("tasbih_count_a11y", count))
            HStack(spacing: 6) {
                Text(l10n.t("watch_tasbih_hint")).font(.caption2).foregroundStyle(.white.opacity(0.85))
                    .shadow(color: .black.opacity(0.6), radius: 2)
                    .lineLimit(2).minimumScaleFactor(0.7)
                Spacer(minLength: 0)
                Button { model.resetTasbih() } label: { Image(systemName: "arrow.clockwise") }
                    .buttonStyle(.plain)
                    .foregroundStyle(model.accent)
                    .frame(width: 32, height: 32)
                    .accessibilityLabel(l10n.t("tasbih_reset"))
            }
        }
        .focusable()
        .digitalCrownRotation($crown, from: -1_000_000, through: 1_000_000, by: 1, sensitivity: .low, isContinuous: true, isHapticFeedbackEnabled: false)
        .onChange(of: crown) { _, v in
            // Each step forward counts one; turning back does nothing.
            let step = Int(v.rounded(.down))
            if step > lastStep { for _ in 0..<min(step - lastStep, 5) { count1() } }
            lastStep = step
        }
        .watchSky()
        .navigationTitle(l10n.t("widget_kind_tasbih"))
        .onDisappear { model.publish() }
    }

    private func count1() {
        withAnimation(.snappy) { model.countTasbih() }
        bump = true
        withAnimation(.spring(response: 0.25, dampingFraction: 0.5)) { bump = false }
    }
}
