import AppIntents
import NMCore
import RelevanceKit
import SwiftUI
import WidgetKit

// "Prayer soon": a widget that only shows in the Smart Stack, from half an hour before each prayer until ten
// minutes after it (relevance clues, watchOS 26).

struct PrayerSoonIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "widget_kind_soon"
    static var isDiscoverable = false

    @Parameter(title: "widget_kind_next")
    var prayer: String?

    @Parameter(title: "widget_kind_next")
    var at: Date?

    init() {}

    init(prayer: String, at: Date) {
        self.prayer = prayer
        self.at = at
    }
}

struct PrayerSoonEntry: RelevanceEntry {
    let state: SharedState?
    let event: PrayerEvent?
    let at: Date?
}

struct PrayerSoonProvider: RelevanceEntriesProvider {
    func placeholder(context: Context) -> PrayerSoonEntry {
        PrayerSoonEntry(state: .watchPreview, event: .maghrib, at: Date().addingTimeInterval(900))
    }

    func entry(configuration: PrayerSoonIntent, context: Context) async throws -> PrayerSoonEntry {
        if context.isPreview { return placeholder(context: context) }
        return PrayerSoonEntry(state: SharedStore.read(), event: configuration.prayer.flatMap(PrayerEvent.init(rawValue:)), at: configuration.at)
    }

    /// The prayers of the next two days, each relevant from 30 minutes before to 10 minutes after its time.
    func relevance() async -> WidgetRelevance<PrayerSoonIntent> {
        guard let s = SharedStore.read() else { return WidgetRelevance([]) }
        let now = Date()
        var seen = Set<Date>()
        var attributes: [WidgetRelevanceAttribute<PrayerSoonIntent>] = []
        for day in s.days(now) + s.days(now.addingTimeInterval(86_400)) {
            for e in PrayerEvent.prayers {
                guard let at = day[e], at.addingTimeInterval(600) > now, at < now.addingTimeInterval(2 * 86_400), seen.insert(at).inserted else { continue }
                attributes.append(WidgetRelevanceAttribute(configuration: PrayerSoonIntent(prayer: e.rawValue, at: at),
                                                           context: .date(range: at.addingTimeInterval(-1800)...at.addingTimeInterval(600), kind: .scheduled)))
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
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: e.watchSymbol).foregroundStyle(accent).widgetAccentable()
                    Text(s.t("prayer_\(e.rawValue)")).font(.headline).lineLimit(1)
                    Spacer(minLength: 2)
                    Text(s.time(at)).font(.headline.monospacedDigit())
                }
                if at > Date() {
                    Text(timerInterval: Date()...at, countsDown: true).font(.title3.monospacedDigit()).foregroundStyle(accent)
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
