#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import NMCore
import SwiftUI
import WidgetKit

/// Lock Screen and Dynamic Island views for the next-prayer countdown (PrayerActivityAttributes).
struct PrayerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PrayerActivityAttributes.self) { context in
            PrayerLockScreenView(attributes: context.attributes, state: context.state, stale: context.isStale)
                .activityBackgroundTint(.clear)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "nearmosque://prayer"))
        } dynamicIsland: { context in
            let s = context.state, a = context.attributes
            let gold = Color(red: 0.89, green: 0.75, blue: 0.39)
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
    }
}

/// The Lock Screen card: the sky of the hour, the next prayer with a live countdown and progress bar, and
/// today's prayers along the bottom with the next one lit.
struct PrayerLockScreenView: View {
    let attributes: PrayerActivityAttributes
    let state: PrayerActivityAttributes.ContentState
    let stale: Bool

    var body: some View {
        let p = WidgetPalette.of(.sky, period: PrayerEvent(rawValue: state.period))
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(state.city, systemImage: "mappin.and.ellipse").font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                Spacer()
                Text(state.timeText).font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(p.secondary)
            }
            HStack(alignment: .firstTextBaseline) {
                Label {
                    Text(stale ? state.nowTitle : state.prayer).font(.title2.weight(.bold)).lineLimit(1).minimumScaleFactor(0.7)
                } icon: {
                    Image(systemName: state.symbol).foregroundStyle(p.accent)
                }
                .foregroundStyle(p.ink)
                Spacer(minLength: 8)
                if !stale {
                    Text(timerInterval: state.start...max(state.start, state.at), countsDown: true)
                        .font(.system(size: 30, weight: .semibold, design: .rounded).monospacedDigit())
                        .foregroundStyle(p.accent)
                        .multilineTextAlignment(.trailing)
                        .environment(\.layoutDirection, .leftToRight)
                        .frame(maxWidth: 140, alignment: .trailing)
                }
            }
            if !stale {
                ProgressView(timerInterval: state.start...max(state.start.addingTimeInterval(1), state.at), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(p.accent)
            }
            HStack(spacing: 4) {
                ForEach(Array(state.slots.enumerated()), id: \.offset) { i, slot in
                    let on = i == state.nextIndex
                    VStack(spacing: 2) {
                        Text(slot.name).font(.caption2.weight(on ? .bold : .regular)).lineLimit(1).minimumScaleFactor(0.7)
                        Text(slot.time).font(.caption2.monospacedDigit().weight(on ? .bold : .regular)).lineLimit(1).minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(on ? p.onPill : p.ink.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .background(on ? p.pill : Color.clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }
        }
        .padding(16)
        .background(p.background)
        .environment(\.layoutDirection, attributes.rtl ? .rightToLeft : .leftToRight)
    }
}
#endif
