import SwiftUI

/// The tasbih: turn the Digital Crown, tap the circle, or double-tap with two fingers (Series 9, Ultra 2 and later)
/// to count. A light tap on the wrist at each count and a stronger one every 33.
struct TasbihPage: View {
    @Environment(WatchModel.self) private var model
    @State private var crown = 0.0
    @State private var lastStep = 0
    @State private var bump = false

    var body: some View {
        let count = model.extras.tasbih
        let inRound = count % 33
        let progress = count > 0 && inRound == 0 ? 1 : Double(inRound) / 33
        let l10n = model.l10n
        VStack(spacing: 6) {
            Button {
                count1()
            } label: {
                ZStack {
                    Circle().fill(Color.white.opacity(0.08))
                    Circle().stroke(Color.white.opacity(0.2), lineWidth: 6)
                    Circle().trim(from: 0, to: progress)
                        .stroke(model.accent, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text(count.formatted(.number.locale(l10n.locale).grouping(.never)))
                        .font(.system(size: 44, weight: .heavy, design: .rounded)).monospacedDigit()
                        .minimumScaleFactor(0.4).lineLimit(1).padding(14)
                        .scaleEffect(bump ? 1.08 : 1)
                }
                .environment(\.layoutDirection, .leftToRight)
            }
            .buttonStyle(.plain)
            .handGestureShortcut(.primaryAction)
            .accessibilityLabel(l10n.t("tasbih_count_a11y", count))
            Text(l10n.t("watch_tasbih_hint")).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.7)
        }
        .focusable()
        .digitalCrownRotation($crown, from: -1_000_000, through: 1_000_000, by: 1, sensitivity: .low, isContinuous: true, isHapticFeedbackEnabled: false)
        .onChange(of: crown) { _, v in
            // Each step forward counts one; turning back does nothing.
            let step = Int(v.rounded(.down))
            if step > lastStep { for _ in 0..<min(step - lastStep, 5) { count1() } }
            lastStep = step
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { model.resetTasbih() } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel(l10n.t("tasbih_reset"))
            }
        }
        .navigationTitle(l10n.t("widget_kind_tasbih"))
        .onDisappear { model.publish() }
    }

    private func count1() {
        model.countTasbih()
        bump = true
        withAnimation(.spring(response: 0.25, dampingFraction: 0.5)) { bump = false }
    }
}
