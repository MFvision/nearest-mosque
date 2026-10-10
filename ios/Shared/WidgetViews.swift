import AppIntents
import NMCore
import SwiftUI
import WidgetKit

// Shared by the widget extension and, in debug builds, the app's widget gallery (CI screenshots).

/// The look of a widget, chosen when editing it (long-press → Edit Widget). "Same as app" follows the style
/// picked in the app's Settings; "Time of day" follows the prayer period like the app's sky.
enum WidgetLook: String, AppEnum {
    case auto, cream, green, night, teal, sky

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "widget_style_title"
    static var caseDisplayRepresentations: [WidgetLook: DisplayRepresentation] = [
        .auto: "style_auto", .cream: "style_cream", .green: "style_green", .night: "style_night", .teal: "style_teal", .sky: "style_sky",
    ]

    /// The styles offered in the app's Settings (everything but "Same as app").
    static let choices: [WidgetLook] = [.cream, .green, .night, .teal, .sky]

    /// "Same as app" resolved to the style chosen in the app (Gold and cream until one is chosen).
    func resolved(_ state: SharedState?) -> WidgetLook {
        guard self == .auto else { return self }
        let chosen = state?.widgetLook.flatMap(WidgetLook.init(rawValue:)) ?? .cream
        return chosen == .auto ? .cream : chosen
    }
}

/// Which widget: the next prayer, the countdown bar, the ring, today's five prayers, the nearest mosques, Ask,
/// the Qibla, or the three quick actions.
enum PrayerWidgetKind {
    case next, countdown, ring, today, mosque, ask, qibla, actions, tasbih

    /// Where a tap on the widget (outside its own links) goes.
    func url(_ state: SharedState?) -> URL? {
        switch self {
        case .mosque: return URL(string: state?.mosques?.first.map { "nearmosque://mosque?id=\(Self.encode($0.id))" } ?? "nearmosque://mosques")
        case .ask: return URL(string: "nearmosque://ask")
        case .qibla: return URL(string: "nearmosque://qibla")
        default: return URL(string: "nearmosque://prayer")
        }
    }

    static func encode(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "" }
}

struct PrayerEntry: TimelineEntry {
    let date: Date
    let look: WidgetLook
    let state: SharedState?
    let today: DaySchedule?
    let next: Upcoming?

    static func make(_ date: Date, look requested: WidgetLook, state: SharedState?) -> PrayerEntry {
        let look = requested.resolved(state)
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

    /// The period in effect now (the latest of today's times already reached, sunrise included).
    var current: PrayerEvent? {
        PrayerEvent.allCases.last { e in today?[e].map { $0 <= date } ?? false }
    }

    /// Colours for this entry ("Time of day" depends on the period in effect).
    var palette: WidgetPalette { WidgetPalette.of(look, period: current, after: today?[.sunrise].map { date.timeIntervalSince($0) }) }

    func isPast(_ e: PrayerEvent) -> Bool {
        guard let at = today?[e] else { return false }
        return at <= date && e != current
    }
}

extension PrayerEvent {
    /// SF Symbol for the time of day (same as in the app).
    var symbol: String {
        switch self {
        case .fajr: return "sun.horizon"
        case .sunrise: return "sunrise"
        case .dhuhr: return "sun.max"
        case .asr: return "sun.min"
        case .maghrib: return "sunset"
        case .isha: return "moon.stars"
        }
    }
}

struct WidgetPalette {
    let background: LinearGradient
    let ink: Color
    let secondary: Color
    let accent: Color
    /// Inner cards and chips.
    let card: Color
    /// The highlighted row or tile, and the text on it.
    let pill: Color
    let onPill: Color

    private static func c(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255).opacity(opacity)
    }

    static func of(_ look: WidgetLook, period: PrayerEvent? = nil, after sunrise: TimeInterval? = nil) -> WidgetPalette {
        switch look {
        case .auto, .cream:
            return WidgetPalette(background: LinearGradient(colors: [c(0xFFF8EC), c(0xF3E3C3)], startPoint: .top, endPoint: .bottom),
                                 ink: c(0x1F3B57), secondary: c(0x5B6470), accent: c(0x8A6A1C), card: Color.white.opacity(0.78),
                                 pill: c(0xC99A3A), onPill: .white)
        case .green:
            return WidgetPalette(background: LinearGradient(colors: [c(0x0F3D2E), c(0x1D6B47)], startPoint: .top, endPoint: .bottom),
                                 ink: c(0xFFF8EC), secondary: c(0xCFE3D6), accent: c(0xE7B65A), card: Color.black.opacity(0.22),
                                 pill: c(0xD9E6C8), onPill: c(0x0F3D2E))
        case .night:
            return WidgetPalette(background: LinearGradient(colors: [c(0x081B29), c(0x1A4D6E)], startPoint: .top, endPoint: .bottom),
                                 ink: .white, secondary: Color.white.opacity(0.75), accent: c(0xD4A843), card: Color.white.opacity(0.10),
                                 pill: c(0xD4A843), onPill: c(0x081B29))
        case .teal:
            return WidgetPalette(background: LinearGradient(colors: [c(0x5E9C95), c(0x3E7D78)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                 ink: .white, secondary: Color.white.opacity(0.82), accent: c(0xF2D27A), card: Color.white.opacity(0.16),
                                 pill: c(0xF2D27A), onPill: c(0x123F3B))
        case .sky:
            // The app's dark skies (shared/design/tokens.json "sky"): top, middle and low colour of each period.
            let k: [UInt32]
            switch period {
            case .fajr: k = [0x141E3C, 0x3A3566, 0x6B4A6E]
            case .sunrise where (sunrise ?? .infinity) < 3600: k = [0x1C3F7A, 0x345A99, 0x7A6A7A]
            case .sunrise, .dhuhr: k = [0x1C3F7A, 0x2F5590, 0x3E6AA8]
            case .asr: k = [0x173F5A, 0x23606F, 0x3C7B74]
            case .maghrib: k = [0x1B1F45, 0x4A2F5E, 0x8A4A5A]
            case .isha: k = [0x0A1430, 0x16264D, 0x22356A]
            case nil: k = [0x081B29, 0x12304A, 0x1A3F60]
            }
            return WidgetPalette(background: LinearGradient(colors: k.map { c($0) }, startPoint: .top, endPoint: .bottom),
                                 ink: .white, secondary: Color.white.opacity(0.78), accent: c(0xF2D27A), card: Color.white.opacity(0.12),
                                 pill: c(0xF2D27A), onPill: c(0x0B1530))
        }
    }
}

/// All three widgets, every size. `family` comes from the widget environment (or the debug gallery).
struct PrayerWidgetView: View {
    let entry: PrayerEntry
    let kind: PrayerWidgetKind
    let family: WidgetFamily

    var body: some View {
        let p = entry.palette
        Group {
            if kind == .actions {
                actionsView(entry.state, p)
            } else if let s = entry.state, kind == .qibla {
                qiblaView(s, p)
            } else if let s = entry.state, kind == .mosque {
                mosqueView(s, p)
            } else if let s = entry.state, kind == .ask {
                askView(s, p)
            } else if let s = entry.state, let next = entry.next {
                switch kind {
                case .next: nextView(s, next, p)
                case .countdown: countdownView(s, next, p)
                case .ring: ringView(s, next, p)
                case .today, .mosque, .ask, .qibla, .actions, .tasbih: todayView(s, next, p)
                }
            } else {
                VStack(spacing: 6) {
                    Image(decorative: "MosqueTab").renderingMode(.template).resizable().scaledToFit().frame(width: 26, height: 26).foregroundStyle(p.accent)
                    Text(entry.state?.t("choose_city_title") ?? "Open Near Mosque to choose your city")
                        .font(.caption.weight(.semibold)).multilineTextAlignment(.center).foregroundStyle(p.ink)
                }
            }
        }
        .environment(\.layoutDirection, entry.state?.isRTL == true ? .rightToLeft : .leftToRight)
        .environment(\.locale, entry.state?.locale ?? .current)
    }

    // MARK: Pieces

    private func name(_ s: SharedState, _ e: PrayerEvent) -> String { s.t("prayer_\(e.rawValue)") }

    private func bell(_ s: SharedState, _ e: PrayerEvent, _ p: WidgetPalette) -> some View {
        Image(systemName: s.reminderOn(e) ? "bell.fill" : "bell.slash")
            .foregroundStyle(s.reminderOn(e) ? p.accent : p.secondary.opacity(0.7))
            .accessibilityLabel(s.t(s.reminderOn(e) ? "reminder_on_a11y" : "reminder_off_a11y", name(s, e)))
    }

    private func countdown(_ next: Upcoming) -> some View {
        Text(timerInterval: entry.date...max(entry.date, next.at), countsDown: true).monospacedDigit()
    }

    private func bar(_ next: Upcoming, _ p: WidgetPalette) -> some View {
        let start = min(next.periodStart ?? entry.date, entry.date)
        return ProgressView(timerInterval: start...max(start.addingTimeInterval(1), next.at), countsDown: false) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .progressViewStyle(.linear)
        .tint(p.accent)
    }

    private func dates(_ s: SharedState, _ p: WidgetPalette, align: HorizontalAlignment = .leading) -> some View {
        VStack(alignment: align, spacing: 1) {
            Text(s.weekday(entry.date)).font(.subheadline.weight(.bold)).foregroundStyle(p.accent)
            Text(s.gregorian(entry.date)).font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(p.ink)
            Text(s.hijriNumeric(entry.date)).font(.caption.weight(.semibold).monospacedDigit()).foregroundStyle(p.ink)
        }
        .lineLimit(1).minimumScaleFactor(0.7)
    }

    private func place(_ s: SharedState, _ p: WidgetPalette) -> some View {
        VStack(spacing: 2) {
            Image(systemName: "mappin.circle.fill").font(.title3).foregroundStyle(p.accent)
            Text(s.name).font(.caption.weight(.bold)).foregroundStyle(p.ink).multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.7)
        }
    }

    /// Today's times as rows: past ones faded, the current period highlighted. With `fill`, the rows share the
    /// height they are given evenly and their text shrinks to fit, so six rows never spill out of a medium widget.
    /// The time always shows in full; the name gives way first.
    private func rows(_ s: SharedState, _ p: WidgetPalette, events: [PrayerEvent], font: Font, icons: Bool, bells: Bool, fill: Bool = false) -> some View {
        VStack(spacing: fill ? 0 : 2) {
            ForEach(events, id: \.self) { e in
                if let at = entry.today?[e] {
                    let now = entry.current == e
                    HStack(spacing: 6) {
                        if icons { Image(systemName: e.symbol).frame(width: 16) }
                        Text(name(s, e)).lineLimit(1).minimumScaleFactor(0.6)
                        Spacer(minLength: 4)
                        Text(s.time(at)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7).layoutPriority(1)
                        if bells && e.isPrayer { bell(s, e, p).font(.caption) } else if bells { Image(systemName: "bell").hidden().font(.caption) }
                    }
                    .font(font.weight(now ? .semibold : .regular))
                    .foregroundStyle(now ? p.onPill : p.ink)
                    .opacity(entry.isPast(e) ? 0.45 : 1)
                    .padding(.horizontal, 8).padding(.vertical, fill ? 1 : 3)
                    .frame(maxHeight: fill ? .infinity : nil)
                    .background(now ? p.pill : .clear, in: Capsule())
                }
            }
        }
    }

    // MARK: Next prayer

    @ViewBuilder private func nextView(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette) -> some View {
        switch family {
        #if !targetEnvironment(macCatalyst)
        case .accessoryInline:
            Label("\(name(s, next.event)) \(s.time(next.at))", systemImage: next.event.symbol)
        case .accessoryCircular:
            Gauge(value: entry.progress) {
                Image(systemName: next.event.symbol)
            } currentValueLabel: {
                VStack(spacing: 0) {
                    Image(systemName: next.event.symbol).font(.system(size: 10, weight: .semibold))
                    Text(s.time(next.at)).font(.system(size: 12, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                }
            }
            .gaugeStyle(.accessoryCircularCapacity)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Label("\(name(s, next.event))  \(s.time(next.at))", systemImage: next.event.symbol).font(.headline).lineLimit(1)
                countdown(next).font(.body)
                Text(s.name).font(.caption).lineLimit(1).opacity(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        #endif
        case .systemSmall:
            nextCard(s, next, p, timeSize: 30, showDay: true)
        case .systemMedium:
            HStack(spacing: 10) {
                nextCard(s, next, p, timeSize: 28, showDay: true)
                    .padding(10)
                    .background(p.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                rows(s, p, events: PrayerEvent.allCases, font: .caption, icons: false, bells: false, fill: true)
                    .frame(maxWidth: .infinity)
            }
        default:
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    place(s, p)
                    Spacer()
                    dates(s, p, align: .trailing)
                }
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: next.event.symbol).foregroundStyle(p.accent)
                        Text(name(s, next.event)).font(.title3.weight(.semibold)).foregroundStyle(p.ink)
                        Spacer()
                        bell(s, next.event, p)
                    }
                    HStack(alignment: .firstTextBaseline) {
                        Text(s.time(next.at)).font(.system(size: 34, weight: .light)).monospacedDigit().foregroundStyle(p.ink)
                        Spacer()
                        countdown(next).font(.title3).foregroundStyle(p.secondary).multilineTextAlignment(.trailing)
                    }
                    bar(next, p)
                }
                .padding(12)
                .background(p.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                rows(s, p, events: PrayerEvent.allCases, font: .subheadline, icons: true, bells: true)
            }
        }
    }

    /// The next prayer: icon and name, time, reminder bell, time left.
    private func nextCard(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette, timeSize: CGFloat, showDay: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(name(s, next.event)).font(.headline).foregroundStyle(p.ink).lineLimit(1)
                Spacer(minLength: 2)
                Image(systemName: next.event.symbol).foregroundStyle(p.accent)
            }
            Text(s.time(next.at)).font(.system(size: timeSize, weight: .light)).monospacedDigit().minimumScaleFactor(0.6).lineLimit(1)
                .foregroundStyle(p.ink)
            HStack(spacing: 4) {
                bell(s, next.event, p).font(.caption)
                if showDay {
                    Text(s.weekdayDayMonth(entry.date)).font(.caption2).foregroundStyle(p.secondary).lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
            }
            Spacer(minLength: 0)
            Text(s.t("widget_remaining")).font(.caption2).foregroundStyle(p.secondary)
            countdown(next).font(.title3.weight(.medium)).foregroundStyle(p.ink).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Countdown bar

    @ViewBuilder private func countdownView(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette) -> some View {
        switch family {
        #if !targetEnvironment(macCatalyst)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Label(name(s, next.event), systemImage: next.event.symbol).font(.headline).lineLimit(1)
                    Spacer()
                    Text(s.time(next.at)).font(.headline.monospacedDigit())
                }
                bar(next, p)
                countdown(next).font(.caption)
            }
        #endif
        case .systemSmall:
            VStack(alignment: .leading, spacing: 6) {
                Text(s.t("next_prayer")).font(.caption.weight(.semibold)).foregroundStyle(p.secondary)
                Label(name(s, next.event), systemImage: next.event.symbol).font(.headline).foregroundStyle(p.ink).lineLimit(1)
                Text(s.time(next.at)).font(.system(size: 26, weight: .semibold)).monospacedDigit().foregroundStyle(p.ink)
                    .minimumScaleFactor(0.6).lineLimit(1)
                Spacer(minLength: 0)
                bar(next, p)
                HStack(spacing: 4) {
                    Image(systemName: "timer")
                    countdown(next)
                }
                .font(.caption.weight(.semibold)).foregroundStyle(p.secondary)
            }
        default:
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(s.t("next_prayer")).font(.headline).foregroundStyle(p.ink)
                    Spacer()
                    Text(s.weekdayDayMonth(entry.date)).font(.caption.weight(.semibold)).foregroundStyle(p.secondary)
                }
                VStack(spacing: 10) {
                    HStack {
                        Label {
                            Text(name(s, next.event))
                        } icon: {
                            Image(systemName: next.event.symbol).foregroundStyle(p.accent)
                        }
                        .font(.title2.weight(.bold))
                        Spacer()
                        Text(s.time(next.at)).font(.title2.weight(.bold)).monospacedDigit()
                    }
                    .foregroundStyle(p.ink)
                    bar(next, p)
                    HStack(spacing: 4) {
                        bell(s, next.event, p)
                        Spacer()
                        Image(systemName: "timer")
                        countdown(next).multilineTextAlignment(.trailing).frame(maxWidth: 80, alignment: .trailing)
                    }
                    .font(.subheadline.weight(.semibold)).foregroundStyle(p.secondary)
                }
                .padding(12)
                .background(p.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }

    // MARK: Ring

    /// Time left to the next prayer on a ring that fills clockwise from the top, with a dot at its end.
    private func ring(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette, line: CGFloat, nameFont: Font, timeSize: CGFloat, label: Bool) -> some View {
        let track = entry.look == .cream ? Color.white : p.ink.opacity(0.18)
        let arc = entry.look == .cream ? p.pill : p.accent
        return ZStack {
            // Clockwise from the top in every language (right-to-left would mirror the arc).
            ZStack {
                Circle().stroke(track, lineWidth: line)
                Circle().trim(from: 0, to: max(0.005, entry.progress))
                    .stroke(arc, style: StrokeStyle(lineWidth: line, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            .environment(\.layoutDirection, .leftToRight)
            GeometryReader { g in
                let r = min(g.size.width, g.size.height) / 2
                let a = entry.progress * 2 * .pi
                Circle().fill(arc)
                    .overlay(Circle().stroke(Color.white, lineWidth: line * 0.7))
                    .frame(width: line * 2.6, height: line * 2.6)
                    .position(x: g.size.width / 2 + r * sin(a), y: g.size.height / 2 - r * cos(a))
            }
            .environment(\.layoutDirection, .leftToRight)
            VStack(spacing: 2) {
                if label { Text(s.t("widget_until")).font(.caption).foregroundStyle(p.secondary) }
                Text(name(s, next.event)).font(nameFont).foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.6)
                countdown(next).font(.system(size: timeSize, weight: .medium)).foregroundStyle(p.ink)
                    .multilineTextAlignment(.center).lineLimit(1).minimumScaleFactor(0.5)
            }
            .padding(.horizontal, line * 2.5)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .combine)
    }

    /// Today's six times in two columns (Fajr to Dhuhr, then Asr to Isha).
    private func grid(_ s: SharedState, _ p: WidgetPalette, font: Font) -> some View {
        HStack(alignment: .top, spacing: 8) {
            rows(s, p, events: [.fajr, .sunrise, .dhuhr], font: font, icons: false, bells: false)
            rows(s, p, events: [.asr, .maghrib, .isha], font: font, icons: false, bells: false)
        }
    }

    @ViewBuilder private func ringView(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette) -> some View {
        switch family {
        case .systemSmall:
            ring(s, next, p, line: 6, nameFont: .subheadline.weight(.semibold), timeSize: 19, label: false)
                .padding(4)
        case .systemMedium:
            HStack(spacing: 12) {
                ring(s, next, p, line: 6, nameFont: .subheadline.weight(.semibold), timeSize: 19, label: true)
                rows(s, p, events: PrayerEvent.allCases, font: .caption, icons: true, bells: false, fill: true)
                    .frame(maxWidth: .infinity)
            }
        case .systemExtraLarge:
            // iPad: a big ring beside today's times.
            HStack(spacing: 28) {
                ring(s, next, p, line: 9, nameFont: .title2.weight(.semibold), timeSize: 38, label: true)
                VStack(alignment: .leading, spacing: 10) {
                    dates(s, p)
                    rows(s, p, events: PrayerEvent.allCases, font: .title3, icons: true, bells: true)
                }
                .frame(maxWidth: .infinity)
            }
        default:
            VStack(spacing: 12) {
                ring(s, next, p, line: 8, nameFont: .title2.weight(.semibold), timeSize: 36, label: true)
                    .padding(.horizontal, 30)
                    .frame(maxHeight: .infinity)
                grid(s, p, font: .subheadline)
            }
        }
    }

    // MARK: Today's prayers

    @ViewBuilder private func todayView(_ s: SharedState, _ next: Upcoming, _ p: WidgetPalette) -> some View {
        let summary = HStack(alignment: .center, spacing: 8) {
            dates(s, p)
            Spacer(minLength: 4)
            VStack(alignment: .leading, spacing: 4) {
                chipRow(name(s, next.event), Text(s.time(next.at)).monospacedDigit(), p)
                chipRow(s.t("widget_remaining"), countdown(next), p)
            }
            Spacer(minLength: 4)
            place(s, p).frame(maxWidth: 70)
        }
        if family == .systemExtraLarge {
            // iPad: the summary as a column beside the six tiles.
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top) { dates(s, p); Spacer(minLength: 4); place(s, p).frame(maxWidth: 90) }
                    VStack(alignment: .leading, spacing: 6) {
                        Label(name(s, next.event), systemImage: next.event.symbol).font(.title3.weight(.semibold)).foregroundStyle(p.ink)
                        Text(s.time(next.at)).font(.system(size: 40, weight: .light)).monospacedDigit().foregroundStyle(p.ink)
                        HStack(spacing: 6) {
                            Image(systemName: "timer")
                            countdown(next)
                        }
                        .font(.title3.weight(.medium)).foregroundStyle(p.secondary)
                        bar(next, p)
                    }
                    .padding(12)
                    .background(p.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    Spacer(minLength: 0)
                }
                .frame(width: 260)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(PrayerEvent.allCases, id: \.self) { e in tile(s, e, next, p, big: true) }
                }
            }
        } else if family == .systemLarge {
            VStack(spacing: 10) {
                summary
                let events = PrayerEvent.allCases
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    ForEach(events, id: \.self) { e in tile(s, e, next, p, big: true) }
                }
                Spacer(minLength: 0)
            }
        } else {
            VStack(spacing: 8) {
                summary
                HStack(spacing: 6) {
                    ForEach(PrayerEvent.prayers, id: \.self) { e in tile(s, e, next, p, big: false) }
                }
            }
        }
    }

    private func chipRow(_ label: String, _ value: some View, _ p: WidgetPalette) -> some View {
        HStack(spacing: 6) {
            Text(label).font(.subheadline.weight(.bold)).foregroundStyle(p.accent).lineLimit(1).minimumScaleFactor(0.7)
            value.font(.subheadline.weight(.bold)).foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.7)
                .frame(minWidth: 64)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(p.card, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
    }

    /// One prayer: name, time (and AM/PM below), the next one highlighted.
    private func tile(_ s: SharedState, _ e: PrayerEvent, _ next: Upcoming, _ p: WidgetPalette, big: Bool) -> some View {
        let isNext = !next.isTomorrow && next.event == e
        let at = entry.today?[e]
        let parts = at.map { s.timeParts($0) }
        return VStack(spacing: 1) {
            if big {
                HStack(spacing: 4) {
                    Image(systemName: e.symbol)
                    if e.isPrayer { bell(s, e, p).font(.caption2) }
                }
                .font(.caption)
            }
            Text(name(s, e)).font((big ? Font.subheadline : .caption).weight(.bold)).lineLimit(1).minimumScaleFactor(0.6)
            Text(parts?.time ?? "—").font((big ? Font.title3 : .callout).weight(.bold).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.6)
            if let mark = parts?.mark { Text(mark).font(.caption2.weight(.semibold)) }
        }
        .foregroundStyle(isNext ? p.onPill : p.ink)
        .opacity(entry.isPast(e) && !isNext ? 0.55 : 1)
        .frame(maxWidth: .infinity)
        .padding(.vertical, big ? 10 : 6)
        .background(isNext ? p.pill : p.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

extension PrayerWidgetView {
    // MARK: Nearest mosque

    private func arrow(_ bearing: Double, _ color: Color) -> some View {
        Image(systemName: "location.north.fill").rotationEffect(.degrees(bearing)).foregroundStyle(color)
            .accessibilityHidden(true)
    }

    @ViewBuilder func mosqueView(_ s: SharedState, _ p: WidgetPalette) -> some View {
        let list = s.mosques ?? []
        let title = HStack(spacing: 6) {
            Image(decorative: "MosqueTab").renderingMode(.template).resizable().scaledToFit().frame(width: 18, height: 18).foregroundStyle(p.accent)
            Text(s.t("widget_kind_mosque")).font(.caption.weight(.bold)).foregroundStyle(p.secondary).lineLimit(1)
        }
        if list.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                title
                Spacer(minLength: 0)
                // nil: not known yet, or this area's mosques are not on the phone; empty: none within reach.
                Text(s.t(s.mosques == nil ? "siri_nearest_open_app" : "no_records_title")).font(.subheadline.weight(.semibold)).foregroundStyle(p.ink)
                Text(s.name).font(.caption).foregroundStyle(p.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else if family == .systemSmall {
            let m = list[0]
            VStack(alignment: .leading, spacing: 4) {
                title
                Spacer(minLength: 0)
                Text(m.name).font(.headline).foregroundStyle(p.ink).lineLimit(3).minimumScaleFactor(0.8)
                HStack(spacing: 6) {
                    arrow(m.bearing, p.accent)
                    Text(s.distance(m.meters)).font(.title3.weight(.semibold).monospacedDigit()).foregroundStyle(p.ink)
                }
                Label(s.t("directions"), systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    .font(.caption.weight(.semibold)).foregroundStyle(p.accent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    title
                    Spacer()
                    Label(s.name, systemImage: "mappin.circle.fill").font(.caption.weight(.semibold)).foregroundStyle(p.secondary).lineLimit(1)
                }
                ForEach(Array(list.prefix(3).enumerated()), id: \.offset) { i, m in
                    Link(destination: URL(string: "nearmosque://mosque?id=\(PrayerWidgetKind.encode(m.id))")!) {
                        HStack(spacing: 8) {
                            Image(decorative: "MosqueTab").renderingMode(.template).resizable().scaledToFit().frame(width: 16, height: 16)
                                .foregroundStyle(i == 0 ? p.onPill : p.accent)
                            Text(m.name).font(.subheadline.weight(i == 0 ? .semibold : .regular)).lineLimit(1)
                            Spacer(minLength: 6)
                            arrow(m.bearing, i == 0 ? p.onPill : p.accent)
                            Text(s.distance(m.meters)).font(.subheadline.monospacedDigit()).lineLimit(1)
                        }
                        .foregroundStyle(i == 0 ? p.onPill : p.ink)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(i == 0 ? p.pill : p.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: Ask

    @ViewBuilder func askView(_ s: SharedState, _ p: WidgetPalette) -> some View {
        let bar = HStack(spacing: 8) {
            Image(systemName: "sparkles").foregroundStyle(p.accent)
            Text(s.t("ask_placeholder")).font(.subheadline).foregroundStyle(p.secondary).lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .background(p.card, in: Capsule())
        if family == .systemSmall {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "sparkles").font(.system(size: 30, weight: .semibold)).foregroundStyle(p.accent)
                Spacer(minLength: 0)
                Text(s.t("widget_kind_ask")).font(.headline).foregroundStyle(p.ink).lineLimit(2).minimumScaleFactor(0.8)
                Text(s.t("tab_ask")).font(.caption.weight(.semibold)).foregroundStyle(p.onPill)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(p.pill, in: Capsule())
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Link(destination: URL(string: "nearmosque://ask")!) { bar }
                Text(s.t("widget_ask_suggested")).font(.caption.weight(.semibold)).foregroundStyle(p.secondary)
                ForEach(s.todaysQuestions(entry.date), id: \.self) { q in
                    Link(destination: URL(string: "nearmosque://ask?q=\(PrayerWidgetKind.encode(q))")!) {
                        HStack(spacing: 6) {
                            Image(systemName: "questionmark.bubble").foregroundStyle(p.accent)
                            Text(q).font(.subheadline.weight(.medium)).foregroundStyle(p.ink).lineLimit(1)
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.forward").font(.caption).foregroundStyle(p.secondary)
                        }
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(p.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: Qibla

    /// North-up: widgets cannot read the compass, so the arrow shows the Qibla from north, and a tap opens
    /// the live compass in the app.
    @ViewBuilder func qiblaView(_ s: SharedState, _ p: WidgetPalette) -> some View {
        if let here = s.location {
            let b = Qibla.bearing(from: here)
            let deg = Int(b.rounded()) % 360
            let num = { (n: Int) in n.formatted(.number.locale(s.locale)) }
            let north = s.t("compass_north")
            switch family {
            #if !targetEnvironment(macCatalyst)
            case .accessoryCircular:
                ZStack {
                    AccessoryWidgetBackground()
                    Text(north).font(.system(size: 9, weight: .bold)).offset(y: -22)
                    Image(systemName: "location.north.fill").font(.system(size: 18, weight: .bold)).rotationEffect(.degrees(b))
                }
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(s.t("qibla_bearing", num(deg)))
            #endif
            case .systemSmall:
                VStack(spacing: 4) {
                    QiblaDial(bearing: b, north: north, palette: p, look: entry.look)
                    Text("\(num(deg))° · \(s.name)").font(.caption.weight(.semibold)).foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.7)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(s.t("qibla_bearing", num(deg)))
            default:
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Label(s.t("widget_kind_qibla"), systemImage: "location.north.line.fill").font(.caption.weight(.semibold)).foregroundStyle(p.secondary)
                        Text("\(num(deg))°").font(.system(size: 34, weight: .semibold)).monospacedDigit().foregroundStyle(p.ink)
                        Text(deg <= 180 ? s.t("widget_qibla_right", num(deg)) : s.t("widget_qibla_left", num(360 - deg)))
                            .font(.caption.weight(.semibold)).foregroundStyle(p.ink).lineLimit(2).minimumScaleFactor(0.75)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(s.t("qibla_distance", s.distance(Qibla.distanceMeters(from: here)))).font(.caption2).foregroundStyle(p.secondary).lineLimit(1)
                        Spacer(minLength: 0)
                        Label(s.t("widget_qibla_live"), systemImage: "hand.tap").font(.caption2.weight(.semibold)).foregroundStyle(p.accent).lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    QiblaHalfDial(bearing: b, north: north, palette: p, look: entry.look)
                        .frame(maxWidth: 150)
                        .accessibilityHidden(true)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    // MARK: Quick actions

    @ViewBuilder func actionsView(_ s: SharedState?, _ p: WidgetPalette) -> some View {
        let items: [(String, String, Bool, String)] = [
            (s?.t("tab_ask_short") ?? "Ask", "sparkles", false, "nearmosque://ask"),
            (s?.t("tab_mosques") ?? "Nearest Mosque", "MosqueTab", true, "nearmosque://mosques"),
            (s?.t("tab_prayer") ?? "Prayer & Qibla", "location.north.circle", false, "nearmosque://qibla"),
        ]
        HStack(spacing: 8) {
            ForEach(items, id: \.3) { item in
                Link(destination: URL(string: item.3)!) {
                    VStack(spacing: 8) {
                        ZStack {
                            Circle().fill(p.pill)
                            if item.2 {
                                Image(decorative: item.1).renderingMode(.template).resizable().scaledToFit().frame(width: 26, height: 26)
                            } else {
                                Image(systemName: item.1).font(.system(size: 24, weight: .semibold))
                            }
                        }
                        .foregroundStyle(p.onPill)
                        .frame(width: 54, height: 54)
                        Text(item.0).font(.caption.weight(.semibold)).foregroundStyle(p.ink).multilineTextAlignment(.center)
                            .lineLimit(2).minimumScaleFactor(0.75)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(p.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
        }
    }
}

/// A point at `radius` from `center`, `degrees` clockwise from straight up.
private func dialPoint(_ center: CGPoint, _ radius: CGFloat, _ degrees: Double) -> CGPoint {
    let a = degrees * .pi / 180
    return CGPoint(x: center.x + radius * CGFloat(sin(a)), y: center.y - radius * CGFloat(cos(a)))
}

private func tick(_ center: CGPoint, _ inner: CGFloat, _ outer: CGFloat, _ degrees: Double) -> Path {
    var p = Path()
    p.move(to: dialPoint(center, inner, degrees))
    p.addLine(to: dialPoint(center, outer, degrees))
    return p
}

/// A north-up dial: ticks, N at the top, and an arrow from the centre to the Kaaba on the rim (the direction
/// of the Qibla from north; a widget cannot read the compass).
struct QiblaDial: View {
    let bearing: Double
    let north: String
    let palette: WidgetPalette
    let look: WidgetLook

    var body: some View {
        GeometryReader { g in
            let d: CGFloat = min(g.size.width, g.size.height)
            let r: CGFloat = d / 2
            let c = CGPoint(x: g.size.width / 2, y: g.size.height / 2)
            let kaabaSize: CGFloat = max(16, d * 0.17)
            let rim: CGPoint = dialPoint(c, r - kaabaSize * 0.62, bearing)
            let tip: CGPoint = dialPoint(c, r - kaabaSize * 1.25, bearing)
            let head: CGFloat = max(6, d * 0.06)
            ZStack {
                Canvas { ctx, _ in
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: d, height: d)), with: .color(palette.card))
                    for i in 0..<36 {
                        let long = i % 9 == 0
                        let inner: CGFloat = r - (long ? 8 : 4)
                        ctx.stroke(tick(c, inner, r - 1.5, Double(i) * 10), with: .color(palette.secondary.opacity(long ? 0.8 : 0.35)), lineWidth: long ? 1.5 : 1)
                    }
                    // Glow under the Kaaba.
                    ctx.fill(Path(ellipseIn: CGRect(x: rim.x - kaabaSize * 0.8, y: rim.y - kaabaSize * 0.8, width: kaabaSize * 1.6, height: kaabaSize * 1.6)),
                             with: .color(palette.pill.opacity(0.28)))
                    var shaft = Path()
                    shaft.move(to: c)
                    shaft.addLine(to: tip)
                    ctx.stroke(shaft, with: .color(palette.pill), style: StrokeStyle(lineWidth: max(2.5, d * 0.025), lineCap: .round))
                    var arrow = Path()
                    arrow.move(to: dialPoint(tip, head * 1.2, bearing))
                    arrow.addLine(to: dialPoint(tip, head, bearing + 130))
                    arrow.addLine(to: dialPoint(tip, head, bearing - 130))
                    arrow.closeSubpath()
                    ctx.fill(arrow, with: .color(palette.pill))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 3, y: c.y - 3, width: 6, height: 6)), with: .color(palette.ink.opacity(0.7)))
                }
                Text(north).font(.system(size: max(9, d * 0.08), weight: .bold)).foregroundStyle(palette.accent).position(x: c.x, y: c.y - r + max(14, d * 0.13))
                WidgetKaaba().frame(width: kaabaSize, height: kaabaSize).position(rim)
            }
        }
        .environment(\.layoutDirection, .leftToRight)
        .aspectRatio(1, contentMode: .fit)
    }
}

/// Half a dial facing the Qibla: the Kaaba at the top, a gold arrow to it, and N marked where north lies
/// (held at the edge when north is behind you).
struct QiblaHalfDial: View {
    let bearing: Double
    let north: String
    let palette: WidgetPalette
    let look: WidgetLook

    var body: some View {
        GeometryReader { g in
            // Room above the top for the Kaaba and its glow.
            let r: CGFloat = min(g.size.width / 2 - 8, g.size.height - 30)
            let c = CGPoint(x: g.size.width / 2, y: g.size.height - 6)
            // North, relative to the Qibla at the top: -bearing, folded into -180...180 and held within the half.
            let northAt: CGPoint = dialPoint(c, r, max(-90, min(90, Self.fold(-bearing))))
            let top = CGPoint(x: c.x, y: c.y - r)
            ZStack {
                Canvas { ctx, _ in
                    var arc = Path()
                    arc.addArc(center: c, radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
                    ctx.stroke(arc, with: .color(palette.card), lineWidth: 14)
                    for i in 0...12 {
                        let major = i % 6 == 0
                        ctx.stroke(tick(c, r - 4, r + 4, Double(i) * 15 - 90), with: .color(palette.secondary.opacity(major ? 0.9 : 0.45)), lineWidth: major ? 1.6 : 1)
                    }
                    var arrow = Path()
                    arrow.move(to: c)
                    arrow.addLine(to: CGPoint(x: c.x, y: c.y - r + 22))
                    ctx.stroke(arrow, with: .color(palette.pill), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)), with: .color(palette.pill))
                    ctx.fill(Path(ellipseIn: CGRect(x: top.x - 20, y: top.y - 20, width: 40, height: 40)), with: .color(palette.pill.opacity(0.3)))
                }
                WidgetKaaba().frame(width: 26, height: 26).position(top)
                ZStack {
                    Circle().fill(palette.accent)
                    Text(north).font(.system(size: 10, weight: .heavy)).foregroundStyle(palette.onPill)
                }
                .frame(width: 18, height: 18)
                .position(northAt)
            }
        }
        .environment(\.layoutDirection, .leftToRight)
        .aspectRatio(1.6, contentMode: .fit)
    }

    /// An angle in -180...180.
    static func fold(_ a: Double) -> Double {
        let x = a.truncatingRemainder(dividingBy: 360)
        return x > 180 ? x - 360 : (x < -180 ? x + 360 : x)
    }
}

/// A small Kaaba: a dark cube with its gold band.
struct WidgetKaaba: View {
    var body: some View {
        Canvas { ctx, s in
            let w = s.width, h = s.height
            var front = Path()
            front.addRect(CGRect(x: w * 0.12, y: h * 0.3, width: w * 0.5, height: h * 0.58))
            var side = Path()
            side.move(to: CGPoint(x: w * 0.62, y: h * 0.3))
            side.addLine(to: CGPoint(x: w * 0.88, y: h * 0.18))
            side.addLine(to: CGPoint(x: w * 0.88, y: h * 0.74))
            side.addLine(to: CGPoint(x: w * 0.62, y: h * 0.88))
            side.closeSubpath()
            var top = Path()
            top.move(to: CGPoint(x: w * 0.12, y: h * 0.3))
            top.addLine(to: CGPoint(x: w * 0.38, y: h * 0.18))
            top.addLine(to: CGPoint(x: w * 0.88, y: h * 0.18))
            top.addLine(to: CGPoint(x: w * 0.62, y: h * 0.3))
            top.closeSubpath()
            ctx.fill(front, with: .color(Color(white: 0.07)))
            ctx.fill(side, with: .color(Color(white: 0.15)))
            ctx.fill(top, with: .color(Color(white: 0.23)))
            ctx.fill(Path(CGRect(x: w * 0.12, y: h * 0.4, width: w * 0.5, height: h * 0.07)), with: .color(Color(red: 0.85, green: 0.66, blue: 0.26)))
            var band = Path()
            band.move(to: CGPoint(x: w * 0.62, y: h * 0.4))
            band.addLine(to: CGPoint(x: w * 0.88, y: h * 0.28))
            band.addLine(to: CGPoint(x: w * 0.88, y: h * 0.35))
            band.addLine(to: CGPoint(x: w * 0.62, y: h * 0.47))
            band.closeSubpath()
            ctx.fill(band, with: .color(Color(red: 0.66, green: 0.5, blue: 0.16)))
        }
        .accessibilityHidden(true)
    }
}

extension SharedState {
    /// Widget gallery preview before the app has shared anything.
    static let preview = SharedState(name: "Madinah", latitude: 24.4672, longitude: 39.6111, zoneId: "Asia/Riyadh",
                                     prayer: PrayerSettings(method: .UMM_AL_QURA), language: "en", hijriAdjustmentDays: 0,
                                     reminders: ["fajr", "maghrib"],
                                     mosques: [SharedMosque(id: "preview-1", name: "Masjid an-Nabawi", meters: 450, bearing: 40)],
                                     questions: ["What is Islam?", "How do I pray?", "What are the five pillars?"])
}
