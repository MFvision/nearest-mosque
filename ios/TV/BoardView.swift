import NMCore
import SwiftUI

/// Values for one instant, from the cached schedules (the countdown derives from instants).
struct TVSnapshot {
    let now: Date
    let today: DaySchedule?
    let next: Upcoming?
    let current: PrayerEvent?
    let sky: SkyPeriod

    @MainActor
    init(_ model: TVModel, now: Date) {
        self.now = now
        let days = model.days(now: now)
        today = days.count == 3 ? days[1] : nil
        next = model.calculator.nextPrayer(days, now: now)
        if let t = today {
            current = PrayerEvent.allCases.last { (t[$0] ?? .distantFuture) <= now } ?? (days[0][.isha] != nil ? .isha : nil)
        } else {
            current = nil
        }
        sky = SkyPeriod.at(now, today: today, current: current)
    }
}

/// The prayer board: the sky of the hour, the city and both dates, the next prayer with a large
/// countdown, and today's six times along the bottom. Made to be left on screen in a home or a mosque.
struct BoardView: View {
    @Environment(TVModel.self) private var model

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            let snap = TVSnapshot(model, now: ctx.date)
            let sky = Sky.of(snap.sky)
            content(snap)
                .padding(.horizontal, 90)
                .padding(.vertical, 50)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background { SkyBackdrop(sky: sky, horizon: 0.86).ignoresSafeArea() }
                .environment(\.sky, sky)
                .animation(.easeInOut(duration: 1.2), value: sky)
        }
        // Focus can rest on the board, so the tab bar slides away and the board fills the screen.
        .focusable()
        .focusEffectDisabled()
    }

    @ViewBuilder
    private func content(_ snap: TVSnapshot) -> some View {
        let l10n = model.l10n
        let loc = model.settings.location
        let zone = loc?.zone ?? .current
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 10) {
                    Label(loc?.name ?? "", systemImage: "mappin.and.ellipse")
                        .font(.system(size: 44, weight: .semibold))
                    Text(Format.gregorian(CivilDate.of(snap.now, in: zone), locale: l10n.locale))
                        .font(.system(size: 30))
                        .foregroundStyle(.white.opacity(0.85))
                    Text(Format.hijri(CivilDate.of(snap.now, in: zone), adjustment: model.settings.prayer.hijriAdjustmentDays, locale: l10n.locale))
                        .font(.system(size: 30))
                        .foregroundStyle(Theme.gold)
                }
                Spacer()
                Text(Format.time(snap.now, zone: zone, locale: l10n.locale))
                    .font(.system(size: 64, weight: .light, design: .rounded).monospacedDigit())
            }

            Spacer(minLength: 20)

            if let next = snap.next {
                VStack(alignment: .leading, spacing: 6) {
                    Text(l10n.t("next_prayer"))
                        .font(.system(size: 32, weight: .medium))
                        .foregroundStyle(.white.opacity(0.8))
                    HStack(alignment: .firstTextBaseline, spacing: 28) {
                        Text(l10n.t(Format.prayerKey(next.event)))
                            .font(.system(size: 110, weight: .bold))
                        Text(Format.time(next.at, zone: zone, locale: l10n.locale))
                            .font(.system(size: 54, weight: .medium).monospacedDigit())
                            .foregroundStyle(Theme.gold)
                    }
                    Text(Format.countdown(next.at.timeIntervalSince(snap.now), locale: l10n.locale))
                        .font(.system(size: 150, weight: .thin, design: .rounded).monospacedDigit())
                        .contentTransition(.numericText(countsDown: true))
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(l10n.t("countdown_a11y", l10n.t(Format.prayerKey(next.event)), Format.time(next.at, zone: zone, locale: l10n.locale),
                                           Format.remainingLong(next.at.timeIntervalSince(snap.now), locale: l10n.locale)))
            } else {
                Text(l10n.t("polar_unavailable_title")).font(.system(size: 54, weight: .semibold))
                Text(l10n.t("polar_unavailable_body")).font(.system(size: 30)).foregroundStyle(.white.opacity(0.85)).frame(maxWidth: 1200, alignment: .leading)
            }

            Spacer(minLength: 20)

            if let today = snap.today, today.status != .unavailable {
                HStack(spacing: 22) {
                    ForEach(PrayerEvent.allCases, id: \.self) { e in
                        PrayerTile(event: e, time: today[e].map { Format.time($0, zone: zone, locale: l10n.locale) } ?? "–",
                                   isCurrent: e == snap.current && snap.today?[e] != nil, isNext: e == snap.next?.event && snap.next?.isTomorrow == false)
                    }
                }
                if today.status.isEstimated {
                    Text(l10n.t("estimated_badge")).font(.system(size: 24)).foregroundStyle(.white.opacity(0.8)).padding(.top, 12)
                }
            }
        }
        .foregroundStyle(.white)
        // White text stays readable where the horizon glow is bright.
        .shadow(color: .black.opacity(0.35), radius: 8, y: 2)
    }
}

/// One of today's six times on glass; the prayer in progress is outlined in gold, the next one lit.
struct PrayerTile: View {
    @Environment(TVModel.self) private var model
    let event: PrayerEvent
    let time: String
    let isCurrent: Bool
    let isNext: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        VStack(spacing: 12) {
            Image(systemName: Theme.icon(event))
                .font(.system(size: 38))
                .foregroundStyle(isNext || isCurrent ? Theme.gold : .white.opacity(0.85))
            Text(model.l10n.t(Format.prayerKey(event)))
                .font(.system(size: 32, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(time)
                .font(.system(size: 40, weight: .medium).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .opacity(event.isPrayer ? 1 : 0.8)
        .padding(.vertical, 26)
        .frame(maxWidth: .infinity)
        .glass(shape, tint: isNext ? Theme.gold.opacity(0.28) : nil)
        .overlay(shape.stroke(Theme.gold, lineWidth: isCurrent ? 4 : 0))
        .accessibilityElement(children: .combine)
    }
}
