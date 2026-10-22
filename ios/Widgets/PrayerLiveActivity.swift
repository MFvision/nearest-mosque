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
            // Before its time the Adhan or iqama counted down to; once it has passed, the next Adhan.
            let v = s.shown(stale: context.isStale)
            let gold = IslandTint.of(a.style)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(v.prayer).font(.headline).lineLimit(1)
                    } icon: {
                        Image(systemName: v.symbol).foregroundStyle(gold)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(v.time).font(.headline.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.8)).lineLimit(1).minimumScaleFactor(0.7).padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    // Once the Adhan time has come, "Time for Asr" takes this line, so the bottom keeps room for the buttons.
                    Text((v.label != nil ? v.title : a.nextLabel) + " · " + s.city)
                        .font(.caption).foregroundStyle(.white.opacity(0.7)).lineLimit(1).minimumScaleFactor(0.8)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 6) {
                        if let label = v.label, let r = v.range {
                            // The Adhan time has come ("Time for Asr" is on the center line): the countdown to the iqama.
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
                                Text(timerInterval: r, countsDown: true)
                                    .font(.system(size: 28, weight: .semibold, design: .rounded).monospacedDigit())
                                    .foregroundStyle(gold)
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                            ProgressView(timerInterval: r.lowerBound...max(r.lowerBound.addingTimeInterval(1), r.upperBound), countsDown: false) {
                                EmptyView()
                            } currentValueLabel: {
                                EmptyView()
                            }
                            .tint(gold)
                        } else if let r = v.range {
                            Text(timerInterval: r, countsDown: true)
                                .font(.system(size: 30, weight: .semibold, design: .rounded).monospacedDigit())
                                .foregroundStyle(gold)
                                .multilineTextAlignment(.center)
                                .environment(\.layoutDirection, .leftToRight)
                            ProgressView(timerInterval: r.lowerBound...max(r.lowerBound.addingTimeInterval(1), r.upperBound), countsDown: false) {
                                EmptyView()
                            } currentValueLabel: {
                                EmptyView()
                            }
                            .tint(gold)
                        } else {
                            Text(v.title).font(.title2.weight(.semibold)).foregroundStyle(gold)
                        }
                        HStack(spacing: 8) {
                            Link(destination: URL(string: "nearmosque://qibla")!) {
                                Label(a.qiblaLabel, systemImage: "location.north.line.fill").font(.subheadline.weight(.semibold))
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                    .frame(maxWidth: .infinity, minHeight: 32)
                                    .foregroundStyle(Color(red: 0.03, green: 0.11, blue: 0.16))
                                    .background(gold, in: Capsule())
                            }
                            Link(destination: URL(string: "nearmosque://mosques")!) {
                                Label(a.mosqueLabel, systemImage: "building.columns").font(.subheadline.weight(.semibold))
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                    .frame(maxWidth: .infinity, minHeight: 32)
                                    .foregroundStyle(.white)
                                    .background(Color.white.opacity(0.15), in: Capsule())
                            }
                        }
                    }
                    .environment(\.layoutDirection, a.rtl ? .rightToLeft : .leftToRight)
                }
            } compactLeading: {
                HStack(spacing: 4) {
                    Image(systemName: v.symbol).foregroundStyle(gold)
                    Text(v.prayer).font(.caption.weight(.semibold)).lineLimit(1).frame(maxWidth: 60)
                }
            } compactTrailing: {
                Group {
                    if let r = v.range {
                        Text(timerInterval: r, countsDown: true)
                    } else {
                        Text(v.time).lineLimit(1).minimumScaleFactor(0.7)
                    }
                }
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(gold)
                .frame(maxWidth: 56)
                .multilineTextAlignment(.trailing)
            } minimal: {
                if let r = v.range {
                    ProgressView(timerInterval: r.lowerBound...max(r.lowerBound.addingTimeInterval(1), r.upperBound), countsDown: true) {
                        EmptyView()
                    } currentValueLabel: {
                        Image(systemName: v.symbol).font(.system(size: 10, weight: .bold))
                    }
                    .progressViewStyle(.circular)
                    .tint(gold)
                } else {
                    Image(systemName: v.symbol).font(.system(size: 10, weight: .bold)).foregroundStyle(gold)
                }
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
            let v = state.shown(stale: stale)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: v.symbol).foregroundStyle(gold)
                    Text(v.title).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
                    Spacer(minLength: 2)
                    Text(v.time).font(.caption.monospacedDigit()).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
                }
                if let range = v.range {
                    if let label = v.label {
                        Text(label).font(.caption2.weight(.semibold)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Text(timerInterval: range, countsDown: true)
                        .font(.system(.title2, design: .rounded).weight(.semibold).monospacedDigit())
                        .foregroundStyle(gold)
                        .environment(\.layoutDirection, .leftToRight)
                    ProgressView(timerInterval: range.lowerBound...max(range.lowerBound.addingTimeInterval(1), range.upperBound), countsDown: false) {
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
