import AppIntents
import NMCore
import SwiftUI
import WidgetKit

// MARK: - Configuration

/// The look of a widget, chosen when editing it (long-press → Edit Widget).
enum WidgetLook: String, AppEnum {
    case cream, green, night

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Style"
    static var caseDisplayRepresentations: [WidgetLook: DisplayRepresentation] = [
        .cream: "Gold and cream", .green: "Green", .night: "Night sky",
    ]
}

struct PrayerWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Prayer times"
    static var description = IntentDescription("The next prayer with a countdown, and today's times.")

    @Parameter(title: "Style", default: .cream)
    var look: WidgetLook
}

// MARK: - Timeline

struct PrayerEntry: TimelineEntry {
    let date: Date
    let look: WidgetLook
    let state: SharedState?
    let today: DaySchedule?
    let next: Upcoming?

    static func make(_ date: Date, look: WidgetLook, state: SharedState?) -> PrayerEntry {
        guard let state else { return PrayerEntry(date: date, look: look, state: nil, today: nil, next: nil) }
        let days = state.days(date)
        return PrayerEntry(date: date, look: look, state: state, today: days.count == 3 ? days[1] : nil,
                           next: PrayerCalculator().nextPrayer(days, now: date))
    }

    /// Elapsed fraction of the current prayer period.
    var progress: Double {
        guard let n = next, let start = n.periodStart else { return 0 }
        let total = n.at.timeIntervalSince(start)
        return total > 0 ? min(1, max(0, date.timeIntervalSince(start) / total)) : 0
    }
}

struct PrayerProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> PrayerEntry {
        PrayerEntry.make(Date(), look: .cream, state: SharedState.preview)
    }

    func snapshot(for configuration: PrayerWidgetIntent, in context: Context) async -> PrayerEntry {
        PrayerEntry.make(Date(), look: configuration.look, state: SharedStore.read() ?? (context.isPreview ? SharedState.preview : nil))
    }

    /// One entry now and one at each prayer time in the next day (the countdown itself ticks on its own).
    func timeline(for configuration: PrayerWidgetIntent, in context: Context) async -> Timeline<PrayerEntry> {
        let now = Date()
        let state = SharedStore.read()
        var entries = [PrayerEntry.make(now, look: configuration.look, state: state)]
        if let state {
            let horizon = now.addingTimeInterval(26 * 3600)
            let times = state.days(now).flatMap { d in PrayerEvent.allCases.compactMap { d[$0] } }
                + state.days(now.addingTimeInterval(86_400)).flatMap { d in PrayerEvent.allCases.compactMap { d[$0] } }
            for t in Set(times).sorted() where t > now && t < horizon {
                entries.append(PrayerEntry.make(t.addingTimeInterval(1), look: configuration.look, state: state))
            }
        }
        let next = entries.count > 1 ? entries.last!.date : now.addingTimeInterval(3600)
        return Timeline(entries: entries, policy: .after(next))
    }
}

// MARK: - Looks

struct WidgetPalette {
    let background: LinearGradient
    let ink: Color
    let secondary: Color
    let accent: Color
    let highlight: Color

    static func of(_ look: WidgetLook) -> WidgetPalette {
        switch look {
        case .cream:
            return WidgetPalette(background: LinearGradient(colors: [Color(hex: 0xFFF8EC), Color(hex: 0xF3E3C3)], startPoint: .top, endPoint: .bottom),
                                 ink: Color(hex: 0x1F3B57), secondary: Color(hex: 0x5B6470), accent: Color(hex: 0x8A6A1C), highlight: Color(hex: 0xD4A843).opacity(0.22))
        case .green:
            return WidgetPalette(background: LinearGradient(colors: [Color(hex: 0x0F3D2E), Color(hex: 0x1D6B47)], startPoint: .top, endPoint: .bottom),
                                 ink: Color(hex: 0xFFF8EC), secondary: Color(hex: 0xCFE3D6), accent: Color(hex: 0xE7B65A), highlight: Color.white.opacity(0.14))
        case .night:
            return WidgetPalette(background: LinearGradient(colors: [Color(hex: 0x081B29), Color(hex: 0x1A4D6E)], startPoint: .top, endPoint: .bottom),
                                 ink: .white, secondary: Color.white.opacity(0.75), accent: Color(hex: 0xD4A843), highlight: Color.white.opacity(0.12))
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

// MARK: - Views

struct PrayerWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    let entry: PrayerEntry

    var body: some View {
        let p = WidgetPalette.of(entry.look)
        Group {
            if let state = entry.state, let next = entry.next {
                content(state, next, p)
            } else {
                VStack(spacing: 6) {
                    Image("MosqueTab").renderingMode(.template).resizable().scaledToFit().frame(width: 26, height: 26).foregroundStyle(p.accent)
                    Text(entry.state?.t("choose_city_title") ?? "Open Near Mosque to choose your city")
                        .font(.caption.weight(.semibold)).multilineTextAlignment(.center).foregroundStyle(p.ink)
                }
            }
        }
        .environment(\.layoutDirection, entry.state?.isRTL == true ? .rightToLeft : .leftToRight)
        .environment(\.locale, entry.state?.locale ?? .current)
        .containerBackground(for: .widget) {
            ZStack(alignment: .bottomTrailing) {
                p.background
                Image("MosqueTab").renderingMode(.template).resizable().scaledToFit()
                    .frame(width: 90, height: 90).foregroundStyle(p.accent.opacity(0.10)).offset(x: 14, y: 14)
            }
        }
        .widgetURL(URL(string: "nearmosque://prayer"))
    }

    @ViewBuilder private func content(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette) -> some View {
        let name = s.t("prayer_\(next.event.rawValue)")
        switch family {
        case .accessoryInline:
            Text("\(name) \(s.time(next.at))")
        case .accessoryCircular:
            Gauge(value: entry.progress) {
                Text(name)
            } currentValueLabel: {
                VStack(spacing: 0) {
                    Text(name).font(.system(size: 10, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.6)
                    Text(s.time(next.at)).font(.system(size: 12, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                }
            }
            .gaugeStyle(.accessoryCircularCapacity)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Text("\(name)  \(s.time(next.at))").font(.headline).lineLimit(1)
                Text(timerInterval: entry.date...next.at, countsDown: true).font(.body.monospacedDigit()).lineLimit(1)
                Text(s.name).font(.caption).lineLimit(1).opacity(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .systemSmall:
            VStack(alignment: .leading, spacing: 2) {
                Text(s.name).font(.caption2.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                Spacer(minLength: 0)
                Text(name).font(.subheadline.weight(.semibold)).foregroundStyle(p.accent)
                Text(s.time(next.at)).font(.system(size: 30, weight: .light)).monospacedDigit().minimumScaleFactor(0.6).lineLimit(1)
                    .foregroundStyle(p.ink)
                Text(timerInterval: entry.date...next.at, countsDown: true).font(.footnote.monospacedDigit()).foregroundStyle(p.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .systemMedium:
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.name).font(.caption2.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                    Spacer(minLength: 0)
                    Text(name).font(.subheadline.weight(.semibold)).foregroundStyle(p.accent)
                    Text(s.time(next.at)).font(.system(size: 28, weight: .light)).monospacedDigit().minimumScaleFactor(0.6).lineLimit(1)
                        .foregroundStyle(p.ink)
                    Text(timerInterval: entry.date...next.at, countsDown: true).font(.footnote.monospacedDigit()).foregroundStyle(p.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                times(s, next, p, rows: PrayerEvent.prayers, compact: true)
                    .frame(maxWidth: .infinity)
            }
        default:
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(s.name).font(.headline).foregroundStyle(p.ink).lineLimit(1)
                        Text(s.hijri(entry.date)).font(.caption).foregroundStyle(p.accent)
                    }
                    Spacer()
                    Image("MosqueTab").renderingMode(.template).resizable().scaledToFit().frame(width: 24, height: 24).foregroundStyle(p.accent)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(name).font(.title3.weight(.semibold)).foregroundStyle(p.accent)
                    HStack(alignment: .firstTextBaseline) {
                        Text(s.time(next.at)).font(.system(size: 40, weight: .light)).monospacedDigit().foregroundStyle(p.ink)
                        Spacer()
                        Text(timerInterval: entry.date...next.at, countsDown: true).font(.title3.monospacedDigit()).foregroundStyle(p.secondary)
                            .multilineTextAlignment(.trailing)
                    }
                }
                times(s, next, p, rows: PrayerEvent.allCases, compact: false)
            }
        }
    }

    private func times(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette, rows: [PrayerEvent], compact: Bool) -> some View {
        VStack(spacing: compact ? 1 : 3) {
            ForEach(rows, id: \.self) { e in
                if let at = entry.today?[e] {
                    let isNext = !next.isTomorrow && next.event == e
                    HStack {
                        Text(s.t("prayer_\(e.rawValue)")).font((compact ? Font.caption : .subheadline).weight(isNext ? .semibold : .regular))
                        Spacer(minLength: 4)
                        Text(s.time(at)).font((compact ? Font.caption : .subheadline).monospacedDigit().weight(isNext ? .semibold : .regular))
                    }
                    .foregroundStyle(isNext ? p.accent : (e.isPrayer ? p.ink : p.secondary))
                    .padding(.horizontal, 6).padding(.vertical, compact ? 1 : 3)
                    .background(isNext ? p.highlight : .clear, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
            }
        }
    }
}

struct PrayerWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "PrayerTimes", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            PrayerWidgetView(entry: entry)
        }
        .configurationDisplayName("Prayer times")
        .description("The next prayer with a countdown, and today's times.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryCircular, .accessoryRectangular, .accessoryInline])
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
        QiblaControl()
    }
}

extension SharedState {
    /// Widget gallery preview before the app has shared anything.
    static let preview = SharedState(name: "Makkah", latitude: 21.4225, longitude: 39.8262, zoneId: "Asia/Riyadh",
                                     prayer: PrayerSettings(method: .UMM_AL_QURA), language: "en", hijriAdjustmentDays: 0)
}
