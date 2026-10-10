#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import SwiftUI
import WidgetKit

/// The Qibla compass in the Dynamic Island (experimental), drawn like the Qibla ring in the app: the Kaaba fixed
/// at the top of an arc, a gold dot on the arc where the Qibla is, a glowing stretch from the dot to the Kaaba
/// (how far to turn), and the logo's arrow in the middle turning with the phone. Everything glows gold when you
/// face the Qibla. Content sits in Apple's island regions, which keep clear of the camera on every iPhone.
struct QiblaLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: QiblaActivityAttributes.self) { context in
            QiblaLockCard(attributes: context.attributes, state: context.state)
                .activityBackgroundTint(.clear)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "nearmosque://qibla"))
        } dynamicIsland: { context in
            let s = context.state, a = context.attributes
            let tint = IslandTint.of(a.style)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        WidgetKaaba().frame(width: 26, height: 26)
                            .shadow(color: s.facing ? tint.opacity(0.9) : .clear, radius: 8)
                        Text(a.title).font(.headline).foregroundStyle(.white).lineLimit(1)
                    }
                    .padding(.leading, 6)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 0) {
                        Text(a.bearingText).font(.system(.title3, design: .rounded).weight(.bold).monospacedDigit()).foregroundStyle(tint)
                        Text(a.city).font(.caption2).foregroundStyle(.white.opacity(0.6)).lineLimit(1)
                    }
                    .padding(.trailing, 6)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 4) {
                        QiblaArcDial(turn: s.turn, facing: s.facing, tint: tint, onDark: true)
                            .frame(height: 92)
                        Text(s.hint)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(s.facing ? tint : .white)
                            .lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .environment(\.layoutDirection, a.rtl ? .rightToLeft : .leftToRight)
                }
            } compactLeading: {
                IslandArrow(turn: s.turn, facing: s.facing, tint: tint, size: 22)
                    .padding(.leading, 2)
            } compactTrailing: {
                if s.facing {
                    WidgetKaaba().frame(width: 20, height: 20).shadow(color: tint, radius: 6)
                } else {
                    HStack(spacing: 2) {
                        Image(systemName: (s.turn ?? 0) >= 0 ? "arrow.turn.up.right" : "arrow.turn.up.left")
                            .font(.system(size: 10, weight: .bold))
                        Text(s.turn.map { "\(Int(abs($0).rounded()))°" } ?? "–")
                            .font(.system(.caption, design: .rounded).weight(.bold).monospacedDigit())
                    }
                    .foregroundStyle(tint)
                }
            } minimal: {
                ZStack {
                    Circle().stroke(s.facing ? tint : Color.white.opacity(0.35), lineWidth: 2)
                    IslandArrow(turn: s.turn, facing: s.facing, tint: tint, size: 14)
                }
            }
            .widgetURL(URL(string: "nearmosque://qibla"))
            .keylineTint(tint)
        }
    }
}
#endif
