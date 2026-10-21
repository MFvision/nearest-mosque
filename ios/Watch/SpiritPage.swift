import NMCore
import SwiftUI

/// Spiritual health: the companion (the lantern or the heart) bright when you keep your prayers and tired when you
/// miss them, today's five prayers with an "I prayed" button, the last seven days and the days in a row with all
/// five. Only what you mark counts; the marks stay on your watch and iPhone.
struct SpiritPage: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let l10n = model.l10n
        TimelineView(.everyMinute) { tl in
            let now = tl.date
            if let s = model.state {
                let days = model.days(now: now)
                let today = days.count == 3 ? days[1] : nil
                let current = PrayerEvent.allCases.last { (today?[$0] ?? .distantFuture) <= now }
                let todayDate = CivilDate.of(now, in: s.zone)
                let level = model.extras.companionLevel
                let _ = model.marksVersion
                ScrollView {
                    VStack(spacing: 10) {
                        HStack(spacing: 8) {
                            CompanionStage(kind: companionKind(s), level: level)
                                .frame(width: 58, height: 72)
                            Text(moodLine(s, level)).font(.footnote.weight(.semibold))
                                .foregroundStyle(.white).shadow(color: .black.opacity(0.4), radius: 3)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if let m = CompanionLog.markable(today: today, current: current, now: now), m.0.isPrayer {
                            let done = CompanionLog.checkedIn(m.0.rawValue, on: m.1)
                            Button {
                                model.markPrayer(m.0, on: m.1)
                            } label: {
                                Label(s.t(done ? "companion_marked" : "companion_mark", s.t("prayer_\(m.0.rawValue)")),
                                      systemImage: done ? "checkmark.circle.fill" : "hands.and.sparkles")
                                    .font(.footnote.weight(.semibold))
                            }
                            .disabled(done)
                            .tint(model.accent)
                        }
                        todayRow(s, todayDate)
                        weekGrid(s, todayDate)
                        Text(l10n.t("spirit_note")).font(.caption2).foregroundStyle(.white.opacity(0.7)).multilineTextAlignment(.center)
                    }
                }
            } else {
                WaitingView()
            }
        }
        .watchSky()
        .navigationTitle(l10n.t("spirit_title"))
        .onAppear { model.refreshCompanion() }
    }

    private func companionKind(_ s: SharedState) -> CompanionKind {
        let k = s.companion.flatMap(CompanionKind.init(rawValue:)) ?? .lantern
        return k == .none ? .lantern : k
    }

    private func moodLine(_ s: SharedState, _ level: Double) -> String {
        switch CompanionLog.mood(level) {
        case .happy: return s.t("companion_happy")
        case .quiet: return s.t("companion_quiet")
        case .sad: return s.t("companion_sad")
        }
    }

    /// Today's five prayers: lit when marked.
    private func todayRow(_ s: SharedState, _ day: CivilDate) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(model.l10n.t("spirit_today")).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
            HStack(spacing: 0) {
                ForEach(PrayerEvent.prayers, id: \.self) { e in
                    let on = CompanionLog.checkedIn(e.rawValue, on: day)
                    VStack(spacing: 3) {
                        ZStack {
                            Circle().fill(on ? AnyShapeStyle(model.accent) : AnyShapeStyle(Color.white.opacity(0.12)))
                            Image(systemName: e.watchSymbol).font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(on ? Color.black : .white.opacity(0.6))
                        }
                        .frame(width: 24, height: 24)
                        .shadow(color: on ? model.accent.opacity(0.7) : .clear, radius: 4)
                        Text(s.t("prayer_\(e.rawValue)")).font(.system(size: 9)).lineLimit(1).minimumScaleFactor(0.6)
                            .foregroundStyle(.white.opacity(0.8))
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(s.t(on ? "companion_marked" : "prayer_\(e.rawValue)", s.t("prayer_\(e.rawValue)")))
                }
            }
        }
        .watchCard()
    }

    /// The last seven days, oldest first: five dots a day, and the days in a row with all five.
    private func weekGrid(_ s: SharedState, _ today: CivilDate) -> some View {
        let days = (0..<7).map { today.adding(days: $0 - 6) }
        let marked = days.flatMap { d in PrayerEvent.prayers.filter { CompanionLog.checkedIn($0.rawValue, on: d) } }.count
        let num = { (n: Int) in n.formatted(.number.locale(s.locale)) }
        return VStack(alignment: .leading, spacing: 6) {
            Text(model.l10n.t("spirit_week")).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
            HStack(alignment: .bottom, spacing: 0) {
                ForEach(days, id: \.self) { d in
                    VStack(spacing: 3) {
                        ForEach(PrayerEvent.prayers.reversed(), id: \.self) { e in
                            Circle().fill(CompanionLog.checkedIn(e.rawValue, on: d) ? AnyShapeStyle(model.accent) : AnyShapeStyle(Color.white.opacity(0.14)))
                                .frame(width: 7, height: 7)
                        }
                        Text(weekday(d, s)).font(.system(size: 9, weight: d == today ? .bold : .regular))
                            .foregroundStyle(d == today ? model.accent : .white.opacity(0.7))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .environment(\.layoutDirection, s.isRTL ? .rightToLeft : .leftToRight)
            Text(model.l10n.t("spirit_week_count", num(marked), num(35))).font(.footnote.weight(.semibold))
            Text(model.l10n.t("spirit_streak", num(streak(today)))).font(.caption2).foregroundStyle(.white.opacity(0.8))
        }
        .watchCard()
        .accessibilityElement(children: .combine)
    }

    private func weekday(_ d: CivilDate, _ s: SharedState) -> String {
        d.noonUTC.formatted(Date.FormatStyle(locale: s.locale, calendar: Calendar(identifier: .gregorian), timeZone: TimeZone(identifier: "UTC")!).weekday(.narrow))
    }

    /// Days in a row with all five prayers marked, up to today (today counts once it is complete).
    private func streak(_ today: CivilDate) -> Int {
        func full(_ d: CivilDate) -> Bool { PrayerEvent.prayers.allSatisfy { CompanionLog.checkedIn($0.rawValue, on: d) } }
        var d = full(today) ? today : today.adding(days: -1)
        var n = 0
        while full(d) && n < 60 {
            n += 1
            d = d.adding(days: -1)
        }
        return n
    }
}

/// The companion alive: the flame flickers or the liquid moves, it sways a little and blinks now and then.
struct CompanionStage: View {
    let kind: CompanionKind
    let level: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.isLuminanceReduced) private var dimmed

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion || dimmed)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let blink = t.truncatingRemainder(dividingBy: 4.2) < 0.15
            CompanionFigure(kind: kind, pose: CompanionPose(level: level, time: t, swing: sin(t * 0.9) * 0.08,
                                                           pulse: kind == .heart ? max(0, sin(t * 2.4)) * 0.4 : 0, blink: blink))
                .shadow(color: Color(hex: 0xFFD27A).opacity(0.25 + level * 0.5), radius: 10 + level * 8)
        }
        .accessibilityHidden(true)
    }
}
