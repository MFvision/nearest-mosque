import AppIntents
import NMCore
import RelevanceKit
import SwiftUI
import WidgetKit

// "Prayer soon": a widget that only shows in the Smart Stack, from half an hour before each prayer until ten
// minutes after it (relevance clues, watchOS 26). The countdown and "Time for X" are two configurations with
// their own time ranges, so watchOS draws the second when the prayer's time comes rather than leaving 0:00.

struct PrayerSoonIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "widget_kind_soon"
    static var isDiscoverable = false

    @Parameter(title: "widget_kind_next")
    var prayer: String?

    @Parameter(title: "widget_kind_next")
    var at: Date?

    /// The prayer's time has come: "Time for X" rather than the countdown.
    @Parameter(title: "widget_kind_soon")
    var started: Bool?

    init() {}

    init(prayer: String, at: Date, started: Bool = false) {
        self.prayer = prayer
        self.at = at
        self.started = started
    }
}

struct PrayerSoonEntry: RelevanceEntry {
    let state: SharedState?
    let event: PrayerEvent?
    let at: Date?
    var started = false
}

struct PrayerSoonProvider: RelevanceEntriesProvider {
    func placeholder(context: Context) -> PrayerSoonEntry {
        PrayerSoonEntry(state: .watchPreview, event: .maghrib, at: Date().addingTimeInterval(900))
    }

    func entry(configuration: PrayerSoonIntent, context: Context) async throws -> PrayerSoonEntry {
        if context.isPreview { return placeholder(context: context) }
        return PrayerSoonEntry(state: SharedStore.read(), event: configuration.prayer.flatMap(PrayerEvent.init(rawValue:)), at: configuration.at,
                               started: configuration.started ?? false)
    }

    /// The prayers of the next two days: the countdown for the 30 minutes before each, then "Time for X" for 10 minutes.
    func relevance() async -> WidgetRelevance<PrayerSoonIntent> {
        guard let s = SharedStore.read() else { return WidgetRelevance([]) }
        let now = Date()
        var seen = Set<Date>()
        var attributes: [WidgetRelevanceAttribute<PrayerSoonIntent>] = []
        for day in s.days(now) + s.days(now.addingTimeInterval(86_400)) {
            for e in PrayerEvent.prayers {
                guard let at = day[e], at.addingTimeInterval(600) > now, at < now.addingTimeInterval(2 * 86_400), seen.insert(at).inserted else { continue }
                if at > now {
                    // Ends a second before the prayer's time, so the two never overlap.
                    attributes.append(WidgetRelevanceAttribute(configuration: PrayerSoonIntent(prayer: e.rawValue, at: at),
                                                               context: .date(range: at.addingTimeInterval(-1800)...at.addingTimeInterval(-1), kind: .scheduled)))
                }
                attributes.append(WidgetRelevanceAttribute(configuration: PrayerSoonIntent(prayer: e.rawValue, at: at, started: true),
                                                           context: .date(range: at...at.addingTimeInterval(600), kind: .scheduled)))
            }
        }
        return WidgetRelevance(attributes)
    }
}

struct PrayerSoonView: View {
    let entry: PrayerSoonEntry

    var body: some View {
        if let s = entry.state, let e = entry.event, let at = entry.at {
            let accent = WatchColors.accent(s)
            let now = Date()
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: e.watchSymbol).foregroundStyle(accent).widgetAccentable()
                    Text(s.t("prayer_\(e.rawValue)")).font(.headline).lineLimit(1)
                    Spacer(minLength: 2)
                    Text(s.time(at)).font(.headline.monospacedDigit())
                }
                // A countdown drawn late (after the prayer's time) also says "Time for X".
                if !entry.started && at > now {
                    Text(timerInterval: now...at, countsDown: true).font(.title3.monospacedDigit()).foregroundStyle(accent)
                } else {
                    Text(s.t("reminder_title", s.t("prayer_\(e.rawValue)"))).font(.body).foregroundStyle(accent).lineLimit(1)
                }
                Text(s.name).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .widgetURL(WatchScreen.prayer.url)
        } else {
            NoCityView()
        }
    }
}

struct PrayerSoonWidget: Widget {
    var body: some WidgetConfiguration {
        RelevanceConfiguration(kind: "WatchPrayerSoon", provider: PrayerSoonProvider()) { entry in
            PrayerSoonView(entry: entry)
                .containerBackground(for: .widget) { Color.clear }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_soon"))
        .description(LocalizedStringResource("widget_kind_soon_desc"))
    }
}
