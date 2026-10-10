#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import SwiftUI
import WidgetKit

/// The Qibla compass in the Dynamic Island (experimental): an arrow that turns with the phone towards the
/// Qibla, gold when you face it.
struct QiblaLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: QiblaActivityAttributes.self) { context in
            QiblaIslandCard(attributes: context.attributes, state: context.state)
                .padding(16)
                .activityBackgroundTint(Color(red: 0.03, green: 0.07, blue: 0.12))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "nearmosque://qibla"))
        } dynamicIsland: { context in
            let s = context.state, a = context.attributes
            let gold = Color(red: 0.89, green: 0.75, blue: 0.39)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    QiblaIslandCard(attributes: a, state: s)
                }
            } compactLeading: {
                QiblaArrow(turn: s.turn, facing: s.facing, size: 20)
            } compactTrailing: {
                Text(s.turn.map { "\(Int(abs($0).rounded()))°" } ?? "–")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(s.facing ? gold : .white)
            } minimal: {
                QiblaArrow(turn: s.turn, facing: s.facing, size: 18)
            }
            .widgetURL(URL(string: "nearmosque://qibla"))
            .keylineTint(gold)
        }
    }
}

/// The arrow: straight up means you face the Qibla; it points the way to turn.
struct QiblaArrow: View {
    let turn: Double?
    let facing: Bool
    let size: CGFloat

    var body: some View {
        let gold = Color(red: 0.89, green: 0.75, blue: 0.39)
        Image(systemName: facing ? "location.north.circle.fill" : "location.north.fill")
            .font(.system(size: size * 0.8, weight: .bold))
            .foregroundStyle(facing ? gold : .white)
            .rotationEffect(.degrees(turn ?? 0))
            .opacity(turn == nil ? 0.5 : 1)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// The expanded view and Lock Screen card: a dial with the arrow, and what to do in words.
struct QiblaIslandCard: View {
    let attributes: QiblaActivityAttributes
    let state: QiblaActivityAttributes.ContentState

    var body: some View {
        let gold = Color(red: 0.89, green: 0.75, blue: 0.39)
        HStack(spacing: 16) {
            ZStack {
                Circle().stroke(Color.white.opacity(0.25), lineWidth: 3)
                if state.facing { Circle().fill(gold.opacity(0.2)) }
                QiblaArrow(turn: state.turn, facing: state.facing, size: 46)
            }
            .frame(width: 72, height: 72)
            .environment(\.layoutDirection, .leftToRight)
            VStack(alignment: .leading, spacing: 4) {
                Text(attributes.title + " · " + attributes.city).font(.caption).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                Text(state.hint).font(.title3.weight(.semibold)).foregroundStyle(state.facing ? gold : .white).lineLimit(2).minimumScaleFactor(0.8)
                Text(attributes.bearingText).font(.caption).foregroundStyle(.white.opacity(0.7))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .environment(\.layoutDirection, attributes.rtl ? .rightToLeft : .leftToRight)
        .accessibilityElement(children: .combine)
    }
}
#endif
