#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import ActivityKit
import NMCore
import SwiftUI
import WidgetKit

/// Lock Screen and Dynamic Island views for the next-prayer countdown (PrayerActivityAttributes).
struct PrayerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            PrayerActivityContent(attributes: context.attributes, state: context.state, stale: context.isStale)
                .activityBackgroundTint(.clear)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "nearmosque://prayer"))
        } dynamicIsland: { context in
            let s = context.state, a = context.attributes
            let gold = IslandTint.of(a.style)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(s.prayer).font(.headline).lineLimit(1)
                    } icon: {
                        Image(systemName: s.symbol).foregroundStyle(gold)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(s.timeText).font(.headline.monospacedDigit()).foregroundStyle(.white.opacity(0.8)).padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(a.nextLabel + " · " + s.city).font(.caption).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        if context.isStale {
                            Text(s.nowTitle).font(.title2.weight(.semibold)).foregroundStyle(gold)
                        } else {
                            Text(timerInterval: s.start...max(s.start, s.at), countsDown: true)
                                .font(.system(size: 34, weight: .semibold, design: .rounded).monospacedDigit())
                                .foregroundStyle(gold)
                                .multilineTextAlignment(.center)
                                .environment(\.layoutDirection, .leftToRight)
                            ProgressView(timerInterval: s.start...max(s.start.addingTimeInterval(1), s.at), countsDown: false) {
                                EmptyView()
                            } currentValueLabel: {
                                EmptyView()
                            }
                            .tint(gold)
                        }
                        HStack(spacing: 8) {
                            Link(destination: URL(string: "nearmosque://qibla")!) {
                                Label(a.qiblaLabel, systemImage: "location.north.line.fill").font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 36)
                                    .foregroundStyle(Color(red: 0.03, green: 0.11, blue: 0.16))
                                    .background(gold, in: Capsule())
                            }
                            Link(destination: URL(string: "nearmosque://mosques")!) {
                                Label(a.mosqueLabel, systemImage: "building.columns").font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity, minHeight: 36)
                                    .foregroundStyle(.white)
                                    .background(Color.white.opacity(0.15), in: Capsule())
                            }
                        }
                    }
                    .environment(\.layoutDirection, a.rtl ? .rightToLeft : .leftToRight)
                }
            } compactLeading: {
                HStack(spacing: 4) {
                    Image(systemName: s.symbol).foregroundStyle(gold)
                    Text(s.prayer).font(.caption.weight(.semibold)).lineLimit(1).frame(maxWidth: 60)
                }
            } compactTrailing: {
                Text(timerInterval: s.start...max(s.start, s.at), countsDown: true)
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(gold)
                    .frame(maxWidth: 56)
                    .multilineTextAlignment(.trailing)
            } minimal: {
                ProgressView(timerInterval: s.start...max(s.start.addingTimeInterval(1), s.at), countsDown: true) {
                    EmptyView()
                } currentValueLabel: {
                    Image(systemName: s.symbol).font(.system(size: 10, weight: .bold))
                }
                .progressViewStyle(.circular)
                .tint(gold)
            }
            .widgetURL(URL(string: "nearmosque://prayer"))
            .keylineTint(gold)
        }
        // Also in the Apple Watch Smart Stack, with a layout made for the watch.
        .supplementalActivityFamilies([.small])
    }
}

/// The Lock Screen card, or on Apple Watch (the small family) the next prayer, its time and the countdown.
struct PrayerActivityContent: View {
    @Environment(\.activityFamily) private var family
    let attributes: PrayerActivityAttributes
    let state: PrayerActivityAttributes.ContentState
    let stale: Bool

    var body: some View {
        if family == .small {
            let gold = IslandTint.of(attributes.style)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: state.symbol).foregroundStyle(gold)
                    Text(stale ? state.nowTitle : state.prayer).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
                    Spacer(minLength: 2)
                    Text(state.timeText).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                if !stale {
                    Text(timerInterval: state.start...max(state.start, state.at), countsDown: true)
                        .font(.system(.title2, design: .rounded).weight(.semibold).monospacedDigit())
                        .foregroundStyle(gold)
                        .environment(\.layoutDirection, .leftToRight)
                    ProgressView(timerInterval: state.start...max(state.start.addingTimeInterval(1), state.at), countsDown: false) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .tint(gold)
                }
                Text(state.city).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .environment(\.layoutDirection, attributes.rtl ? .rightToLeft : .leftToRight)
        } else {
            PrayerLockScreenView(attributes: attributes, state: state, stale: stale)
        }
    }
}
#endif
