import AppIntents
import NMCore
import SwiftUI
import WidgetKit

// Complications for Apple's watch faces and widgets for the Smart Stack. They read what the watch app saved
// (SharedState and WatchExtras in the watch's keychain group); nothing is calculated on the iPhone.

struct WatchEntry: TimelineEntry {
    let date: Date
    let state: SharedState?
    let extras: WatchExtras
    let today: DaySchedule?
    /// What the countdown runs to: the next Adhan, or after an Adhan its iqama (`isIqama`).
    let next: Upcoming?
    /// The next Adhan, also while the countdown runs to an iqama.
    let nextAdhan: Upcoming?
    let stage: PrayerMoment.Stage
    let relevance: TimelineEntryRelevance?

    static func make(_ date: Date, state: SharedState?, extras: WatchExtras) -> WatchEntry {
        guard let state else {
            return WatchEntry(date: date, state: nil, extras: extras, today: nil, next: nil, nextAdhan: nil, stage: .upcoming, relevance: nil)
        }
        let days = state.days(date)
        let m = PrayerCalculator().moment(days, now: date, iqama: state.iqamaSettings)
        // After an Adhan, the countdown to its iqama; otherwise to the next Adhan.
        let next = m.map { m in
            m.stage == .toIqama ? Upcoming(event: m.event, at: m.until, periodStart: m.adhan, isTomorrow: false) : m.next
        }
        // Higher in the Smart Stack in the half hour before a prayer.
        let soon = m.map { $0.next.at.timeIntervalSince(date) <= 1800 } ?? false
        return WatchEntry(date: date, state: state, extras: extras, today: days.count == 3 ? days[1] : nil, next: next, nextAdhan: m?.next,
                          stage: m?.stage ?? .upcoming, relevance: TimelineEntryRelevance(score: soon ? 80 : 10, duration: soon ? 1800 : 0))
    }

    /// The countdown runs to the iqama of the prayer just called.
    var isIqama: Bool { stage == .toIqama }

    /// The time beside the prayer's name: its Adhan, also while the countdown runs to its iqama.
    func prayerTime(_ n: Upcoming) -> Date { isIqama ? (n.periodStart ?? n.at) : n.at }

    /// Elapsed fraction of the current prayer period.
    var progress: Double {
        guard let n = next, let start = n.periodStart else { return 0 }
        let total = n.at.timeIntervalSince(start)
        return total > 0 ? min(1, max(0, date.timeIntervalSince(start) / total)) : 0
    }

    var accent: Color { WatchColors.accent(state) }
    func name(_ e: PrayerEvent) -> String { state?.t("prayer_\(e.rawValue)") ?? e.rawValue.capitalized }
}

struct WatchProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchEntry { WatchEntry.make(Date(), state: .watchPreview, extras: WatchExtras()) }

    func getSnapshot(in context: Context, completion: @escaping (WatchEntry) -> Void) {
        completion(WatchEntry.make(Date(), state: SharedStore.read() ?? (context.isPreview ? .watchPreview : nil), extras: WatchExtras.read()))
    }

    /// One entry now, one at each prayer in the next day, and one half an hour before each (for the Smart Stack).
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchEntry>) -> Void) {
        let now = Date()
        let state = SharedStore.read()
        let extras = WatchExtras.read()
        var entries = [WatchEntry.make(now, state: state, extras: extras)]
        if let state {
            let days = state.days(now) + state.days(now.addingTimeInterval(86_400))
            let times = days.flatMap { d in PrayerEvent.allCases.compactMap { d[$0] } }
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = state.zone
            let midnight = cal.startOfDay(for: now.addingTimeInterval(86_400))
            let iqamas = PrayerCalculator().stageChanges(days, after: now, iqama: state.iqamaSettings).map { $0.addingTimeInterval(1) }
            let marks = times.flatMap { [$0.addingTimeInterval(1), $0.addingTimeInterval(-1800)] } + iqamas + [midnight.addingTimeInterval(1)]
            for t in Set(marks).sorted() where t > now && t < now.addingTimeInterval(26 * 3600) {
                entries.append(WatchEntry.make(t, state: state, extras: extras))
            }
        }
        completion(Timeline(entries: entries, policy: .after(entries.count > 1 ? entries.last!.date : now.addingTimeInterval(3600))))
    }
}

extension SharedState {
    /// The gallery preview before the watch app has run.
    static let watchPreview = SharedState(name: "Madinah", latitude: 24.4672, longitude: 39.6111, zoneId: "Asia/Riyadh",
                                          prayer: PrayerSettings(method: .UMM_AL_QURA), language: "en", hijriAdjustmentDays: 0,
                                          reminders: ["fajr", "maghrib"],
                                          mosques: [SharedMosque(id: "preview-1", name: "Masjid an-Nabawi", meters: 450, bearing: 40)])
}

/// Shown until the watch app has the city.
struct NoCityView: View {
    @Environment(\.widgetFamily) private var family
    var body: some View {
        switch family {
        case .accessoryInline: Label("Near Mosque", systemImage: "building.columns")
        case .accessoryRectangular:
            VStack(alignment: .leading) {
                Label("Near Mosque", systemImage: "building.columns").font(.headline)
                Text(LocalizedStringResource("widget_choose_city")).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        default:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "building.columns").font(.title3)
            }
        }
    }
}

// MARK: - Next prayer

struct NextPrayerView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchEntry

    var body: some View {
        if let s = entry.state, let next = entry.next {
            let name = entry.name(next.event)
            // Waiting for the iqama, its time is named as such ("Iqama 12:10"), never put beside the prayer's name.
            let short = entry.isIqama ? s.t("iqama_at", s.time(next.at)) : "\(name) \(s.time(next.at))"
            switch family {
            case .accessoryInline:
                Label(entry.isIqama ? "\(name) · \(short)" : short, systemImage: next.event.watchSymbol)
            case .accessoryCorner:
                Image(systemName: next.event.watchSymbol).font(.title3).widgetAccentable()
                    .widgetLabel {
                        Text(short)
                    }
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Image(systemName: next.event.watchSymbol).widgetAccentable().foregroundStyle(entry.accent)
                        Text(name).font(.headline).lineLimit(1)
                        Spacer(minLength: 2)
                        Text(s.time(entry.prayerTime(next))).font(.headline.monospacedDigit()).lineLimit(1)
                    }
                    HStack(spacing: 4) {
                        if entry.isIqama { Text(s.t("iqama_in")).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                        Text(timerInterval: entry.date...max(entry.date, next.at), countsDown: true)
                            .font(.body.monospacedDigit()).foregroundStyle(entry.accent).widgetAccentable()
                    }
                    Text(s.name).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            default:
                Gauge(value: entry.progress) {
                    Image(systemName: next.event.watchSymbol)
                } currentValueLabel: {
                    VStack(spacing: 0) {
                        if entry.isIqama {
                            Text(s.t("iqama_section")).font(.system(size: 8, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.5).widgetAccentable()
                        } else {
                            Image(systemName: next.event.watchSymbol).font(.system(size: 10, weight: .semibold)).widgetAccentable()
                        }
                        Text(s.timeParts(next.at).time).font(.system(size: 13, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                    }
                }
                .gaugeStyle(.accessoryCircularCapacity)
                .tint(entry.accent)
            }
        } else {
            NoCityView()
        }
    }
}

// MARK: - Countdown

struct CountdownView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchEntry

    var body: some View {
        if let s = entry.state, let next = entry.next {
            let start = min(next.periodStart ?? entry.date, entry.date)
            switch family {
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Label(entry.name(next.event), systemImage: next.event.watchSymbol).font(.headline).lineLimit(1)
                        Spacer(minLength: 2)
                        Text(s.time(entry.prayerTime(next))).font(.headline.monospacedDigit())
                    }
                    ProgressView(timerInterval: start...max(start.addingTimeInterval(1), next.at), countsDown: false) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .tint(entry.accent).widgetAccentable()
                    HStack(spacing: 4) {
                        if entry.isIqama { Text(s.t("iqama_in")).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                        Text(timerInterval: entry.date...max(entry.date, next.at), countsDown: true).font(.caption.monospacedDigit())
                    }
                }
            case .accessoryCorner:
                Text(timerInterval: entry.date...max(entry.date, next.at), countsDown: true).font(.caption.monospacedDigit())
                    .widgetCurvesContent()
                    .widgetLabel { Text(entry.isIqama ? s.t("iqama_in") : entry.name(next.event)) }
            default:
                ZStack {
                    AccessoryWidgetBackground()
                    VStack(spacing: 0) {
                        if entry.isIqama {
                            Text(s.t("iqama_section")).font(.system(size: 8, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.5).widgetAccentable()
                        } else {
                            Image(systemName: next.event.watchSymbol).font(.system(size: 11, weight: .semibold)).widgetAccentable()
                        }
                        Text(timerInterval: entry.date...max(entry.date, next.at), countsDown: true)
                            .font(.system(size: 12, weight: .semibold).monospacedDigit()).multilineTextAlignment(.center)
                            .minimumScaleFactor(0.6).lineLimit(1).padding(.horizontal, 4)
                    }
                }
            }
        } else {
            NoCityView()
        }
    }
}

// MARK: - Today's prayers

struct TodayView: View {
    let entry: WatchEntry

    var body: some View {
        if let s = entry.state, let today = entry.today {
            let events = PrayerEvent.prayers
            VStack(alignment: .leading, spacing: 1) {
                ForEach([Array(events.prefix(3)), Array(events.suffix(2))], id: \.self) { line in
                    HStack(spacing: 6) {
                        ForEach(line, id: \.self) { e in
                            if let at = today[e] {
                                let isNext = entry.nextAdhan?.event == e && entry.nextAdhan?.isTomorrow == false
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(entry.name(e)).font(.system(size: 11, weight: isNext ? .bold : .regular)).lineLimit(1).minimumScaleFactor(0.6)
                                    Text(s.timeParts(at).time).font(.system(size: 14, weight: .semibold).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.6)
                                }
                                .foregroundStyle(isNext ? entry.accent : .primary)
                                .widgetAccentable(isNext)
                                .opacity(at <= entry.date ? 0.55 : 1)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            NoCityView()
        }
    }
}

// MARK: - Qibla

struct QiblaComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchEntry

    var body: some View {
        if let s = entry.state, let here = s.location {
            let b = Qibla.bearing(from: here)
            let deg = (Int(b.rounded()) % 360).formatted(.number.locale(s.locale))
            switch family {
            case .accessoryCorner:
                Image(systemName: "location.north.fill").font(.title3).rotationEffect(.degrees(b)).widgetAccentable()
                    .widgetLabel { Text("\(s.t("qibla")) \(deg)°") }
            case .accessoryInline:
                Label("\(s.t("qibla")) \(deg)°", systemImage: "location.north.line.fill")
            default:
                ZStack {
                    AccessoryWidgetBackground()
                    Text(s.t("compass_north")).font(.system(size: 9, weight: .bold)).offset(y: -17)
                    Image(systemName: "location.north.fill").font(.system(size: 18, weight: .bold)).rotationEffect(.degrees(b)).widgetAccentable()
                }
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(s.t("qibla_bearing", deg))
            }
        } else {
            NoCityView()
        }
    }
}

// MARK: - Tasbih

struct TasbihComplicationView: View {
    let entry: WatchEntry

    var body: some View {
        let count = entry.extras.tasbih
        let inRound = count % 33
        let progress = count > 0 && inRound == 0 ? 1 : Double(inRound) / 33
        ZStack {
            AccessoryWidgetBackground()
            Circle().trim(from: 0, to: progress).stroke(style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                .padding(3).widgetAccentable()
            Text(count.formatted(.number.locale(entry.state?.locale ?? .current).grouping(.never)))
                .font(.system(size: 16, weight: .heavy, design: .rounded)).minimumScaleFactor(0.5).padding(6)
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityLabel(entry.state?.t("tasbih_count_a11y", count) ?? "\(count)")
    }
}

// MARK: - Nearest mosque

struct MosqueComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchEntry

    var body: some View {
        if let s = entry.state, let m = s.mosques?.first {
            switch family {
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 1) {
                    Label(s.t("widget_kind_mosque"), systemImage: "building.columns").font(.caption2).foregroundStyle(.secondary)
                    Text(m.name).font(.headline).lineLimit(1)
                    HStack(spacing: 4) {
                        Image(systemName: "location.north.fill").rotationEffect(.degrees(m.bearing)).widgetAccentable()
                        Text(s.distance(m.meters)).font(.body.monospacedDigit())
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            default:
                ZStack {
                    AccessoryWidgetBackground()
                    VStack(spacing: 0) {
                        Image(systemName: "building.columns.fill").font(.system(size: 12)).widgetAccentable()
                        Text(s.distance(m.meters)).font(.system(size: 11, weight: .semibold)).minimumScaleFactor(0.5).lineLimit(1).padding(.horizontal, 3)
                    }
                }
            }
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "building.columns").font(.title3)
            }
        }
    }
}

// MARK: - Companion

struct CompanionComplicationView: View {
    let entry: WatchEntry

    var body: some View {
        let k = entry.state?.companion.flatMap(CompanionKind.init(rawValue:)) ?? .lantern
        ZStack {
            AccessoryWidgetBackground()
            CompanionFigure(kind: k == .none ? .lantern : k, level: entry.extras.companionLevel).padding(5)
        }
    }
}

// MARK: - Widgets

/// Wraps a complication view with its background and the screen a tap opens.
struct ComplicationFrame<Content: View>: View {
    let screen: WatchScreen
    @ViewBuilder var content: Content
    var body: some View {
        content
            .containerBackground(for: .widget) { Color.clear }
            .widgetURL(screen.url)
    }
}

struct WatchNextPrayer: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchNextPrayer", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .prayer) { NextPrayerView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_next"))
        .description(LocalizedStringResource("widget_kind_next_desc"))
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular, .accessoryInline])
    }
}

struct WatchCountdown: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchCountdown", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .prayer) { CountdownView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_countdown"))
        .description(LocalizedStringResource("widget_kind_countdown_desc"))
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular])
    }
}

struct WatchToday: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchToday", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .prayer) { TodayView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_today"))
        .description(LocalizedStringResource("widget_kind_today_desc"))
        .supportedFamilies([.accessoryRectangular])
    }
}

struct WatchQibla: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchQibla", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .qibla) { QiblaComplicationView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_qibla"))
        .description(LocalizedStringResource("widget_kind_qibla_desc"))
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryInline])
    }
}

struct WatchTasbih: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchTasbih", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .tasbih) { TasbihComplicationView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_tasbih"))
        .description(LocalizedStringResource("widget_kind_tasbih_desc"))
        .supportedFamilies([.accessoryCircular])
    }
}

struct WatchMosque: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchMosque", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .mosques) { MosqueComplicationView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_mosque"))
        .description(LocalizedStringResource("widget_kind_mosque_desc"))
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}

struct WatchCompanion: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchCompanion", provider: WatchProvider()) { e in
            ComplicationFrame(screen: .prayer) { CompanionComplicationView(entry: e) }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_companion"))
        .description(LocalizedStringResource("widget_kind_companion_desc"))
        .supportedFamilies([.accessoryCircular])
    }
}

// MARK: - Controls (the watch's Control Center and the Action button)

struct WatchQiblaControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "WatchQiblaControl") {
            ControlWidgetButton(action: OpenWatchScreenIntent(.qibla)) {
                Label("widget_kind_qibla", systemImage: "location.north.line.fill")
            }
        }
        .displayName(LocalizedStringResource("widget_kind_qibla"))
    }
}

struct WatchTasbihControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "WatchTasbihControl") {
            ControlWidgetButton(action: OpenWatchScreenIntent(.tasbih)) {
                Label("widget_kind_tasbih", systemImage: "circle.dotted.circle")
            }
        }
        .displayName(LocalizedStringResource("widget_kind_tasbih"))
    }
}

@main
struct NearMosqueWatchWidgets: WidgetBundle {
    var body: some Widget {
        WatchNextPrayer()
        WatchCountdown()
        WatchToday()
        WatchQibla()
        WatchTasbih()
        WatchMosque()
        WatchCompanion()
        PrayerSoonWidget()
        WatchQiblaControl()
        WatchTasbihControl()
    }
}
