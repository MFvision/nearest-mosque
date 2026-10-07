import AppIntents
import NMCore
import SwiftUI
import WidgetKit

// The views, entry and looks are in Shared/WidgetViews.swift (also used by the app's debug gallery).

// MARK: - Configuration

struct PrayerWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Prayer times"
    static var description = IntentDescription("The next prayer with a countdown, and today's times.")

    @Parameter(title: "Style", default: .cream)
    var look: WidgetLook
}

// MARK: - Timeline

struct PrayerProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> PrayerEntry {
        PrayerEntry.make(Date(), look: .cream, state: SharedState.preview)
    }

    func snapshot(for configuration: PrayerWidgetIntent, in context: Context) async -> PrayerEntry {
        PrayerEntry.make(Date(), look: configuration.look, state: SharedStore.read() ?? (context.isPreview ? SharedState.preview : nil))
    }

    /// One entry now and one at each prayer time in the next day (countdowns and bars move on their own).
    func timeline(for configuration: PrayerWidgetIntent, in context: Context) async -> Timeline<PrayerEntry> {
        let now = Date()
        let state = SharedStore.read()
        var entries = [PrayerEntry.make(now, look: configuration.look, state: state)]
        if let state {
            let horizon = now.addingTimeInterval(26 * 3600)
            let times = state.days(now).flatMap { d in PrayerEvent.allCases.compactMap { d[$0] } }
                + state.days(now.addingTimeInterval(86_400)).flatMap { d in PrayerEvent.allCases.compactMap { d[$0] } }
            // Midnight too, so the dates change on time.
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = state.zone
            let midnight = cal.startOfDay(for: now.addingTimeInterval(86_400))
            for t in Set(times + [midnight]).sorted() where t > now && t < horizon {
                entries.append(PrayerEntry.make(t.addingTimeInterval(1), look: configuration.look, state: state))
            }
        }
        let next = entries.count > 1 ? entries.last!.date : now.addingTimeInterval(3600)
        return Timeline(entries: entries, policy: .after(next))
    }
}

// MARK: - Widgets

/// Reads the widget family and draws the shared view on the chosen look's background.
struct WidgetFrame: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry
    let kind: PrayerWidgetKind

    var body: some View {
        let p = WidgetPalette.of(entry.look)
        PrayerWidgetView(entry: entry, kind: kind, family: family)
            .containerBackground(for: .widget) {
                ZStack(alignment: .bottomTrailing) {
                    p.background
                    Image("MosqueTab").renderingMode(.template).resizable().scaledToFit()
                        .frame(width: 90, height: 90).foregroundStyle(p.accent.opacity(0.08)).offset(x: 14, y: 14)
                }
            }
            .widgetURL(URL(string: "nearmosque://prayer"))
    }
}

struct PrayerWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "PrayerTimes", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            WidgetFrame(entry: entry, kind: .next)
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_next"))
        .description(LocalizedStringResource("widget_kind_next_desc"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "PrayerCountdown", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            WidgetFrame(entry: entry, kind: .countdown)
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_countdown"))
        .description(LocalizedStringResource("widget_kind_countdown_desc"))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "TodayPrayers", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            WidgetFrame(entry: entry, kind: .today)
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_today"))
        .description(LocalizedStringResource("widget_kind_today_desc"))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

/// Control Center and Lock Screen control: opens the Qibla.
struct QiblaControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "QiblaControl") {
            ControlWidgetButton(action: OpenQiblaIntent()) {
                Label("Qibla", systemImage: "location.north.line.fill")
            }
        }
        .displayName("Qibla")
    }
}

@main
struct NearMosqueWidgets: WidgetBundle {
    var body: some Widget {
        PrayerWidget()
        CountdownWidget()
        TodayWidget()
        QiblaControl()
    }
}
