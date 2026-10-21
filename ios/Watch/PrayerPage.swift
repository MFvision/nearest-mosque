import NMCore
import SwiftUI

/// The first page: the next prayer over the living sky (its time, a large countdown and the period filling up),
/// then today's times with their reminder bells, the place and the Hijri date.
struct PrayerPage: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        // At the start of each minute: prayer times are whole minutes, so the page moves on the moment the countdown ends.
        TimelineView(.everyMinute) { tl in
            let now = tl.date
            let days = model.days(now: now)
            let today = days.count == 3 ? days[1] : nil
            let next = model.calculator.nextPrayer(days, now: now)
            let current = PrayerEvent.allCases.last { (today?[$0] ?? .distantFuture) <= now }
            if let s = model.state, let next {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        NextPrayerHero(state: s, next: next, now: now, accent: model.accent)
                        VStack(spacing: 3) {
                            ForEach(PrayerEvent.allCases, id: \.self) { e in
                                if let at = today?[e] { row(s, e, at, now: e == current, past: at <= now && e != current) }
                            }
                        }
                        .padding(4)
                        .watchCard(radius: 20)
                        VStack(alignment: .leading, spacing: 2) {
                            Label(s.name, systemImage: "mappin.and.ellipse").lineLimit(2)
                            Label(s.hijri(now), systemImage: "moon.stars").lineLimit(2)
                        }
                        .font(.footnote).foregroundStyle(.white.opacity(0.8))
                        SettingsButton()
                    }
                }
            } else {
                WaitingView()
            }
        }
        .watchSky()
        .navigationTitle(model.l10n.t("prayer_section"))
    }

    private func row(_ s: SharedState, _ e: PrayerEvent, _ at: Date, now: Bool, past: Bool) -> some View {
        // No bell while notifications are off for the app, whatever the switches say.
        let bell = s.reminderOn(e) && !model.remindersBlocked
        return HStack(spacing: 6) {
            Image(systemName: e.watchSymbol).frame(width: 18).foregroundStyle(now ? Color.black : model.accent)
            Text(s.t("prayer_\(e.rawValue)")).lineLimit(1).minimumScaleFactor(0.6)
            Spacer(minLength: 2)
            Text(s.time(at)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7).layoutPriority(1)
            if e.isPrayer {
                Image(systemName: bell ? "bell.fill" : "bell.slash").font(.caption2)
                    .foregroundStyle(now ? Color.black.opacity(0.7) : (bell ? model.accent : .secondary))
                    .accessibilityLabel(s.t(bell ? "reminder_on_a11y" : "reminder_off_a11y", s.t("prayer_\(e.rawValue)")))
            } else {
                Image(systemName: "bell").font(.caption2).hidden()
            }
        }
        .font(.callout.weight(now ? .semibold : .regular))
        .foregroundStyle(now ? Color.black : .white)
        .opacity(past ? 0.5 : 1)
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background {
            if now {
                Capsule().fill(LinearGradient(colors: [model.accent, model.accent.opacity(0.8)], startPoint: .top, endPoint: .bottom))
                    .shadow(color: model.accent.opacity(0.5), radius: 6)
            }
        }
    }
}

/// The next prayer, large and centred on the sky: icon and name, time, the countdown and a bar for the period.
struct NextPrayerHero: View {
    let state: SharedState
    let next: Upcoming
    let now: Date
    let accent: Color

    var body: some View {
        let start = min(next.periodStart ?? now, now)
        let name = state.t("prayer_\(next.event.rawValue)")
        VStack(spacing: 2) {
            Text(state.t("next_prayer")).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.75)).textCase(.uppercase)
            HStack(spacing: 6) {
                Image(systemName: next.event.watchSymbol).foregroundStyle(accent).symbolRenderingMode(.hierarchical)
                Text(name).font(.title3.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7)
            }
            Text(state.time(next.at))
                .font(.system(size: 34, weight: .semibold, design: .rounded)).monospacedDigit()
                .lineLimit(1).minimumScaleFactor(0.6)
            Text(timerInterval: now...max(now, next.at), countsDown: true)
                .font(.system(.title3, design: .rounded).weight(.bold).monospacedDigit())
                .foregroundStyle(accent)
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityLabel(state.t("countdown_a11y", name, state.time(next.at),
                                            Format.remainingLong(next.at.timeIntervalSince(now), locale: state.locale)))
            ProgressView(timerInterval: start...max(start.addingTimeInterval(1), next.at), countsDown: false) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .tint(accent)
            .padding(.horizontal, 14)
            .padding(.top, 2)
        }
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.45), radius: 4, y: 1)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
    }
}

/// Before the iPhone has sent anything: a note and a button for the watch's own location.
struct WaitingView: View {
    @Environment(WatchModel.self) private var model
    @State private var busy = false
    @State private var failed: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                Image(systemName: "applewatch.and.arrow.forward").font(.title2).foregroundStyle(model.accent)
                Text(model.l10n.t("watch_waiting")).font(.footnote).multilineTextAlignment(.center)
                Button {
                    busy = true
                    Task {
                        let r = await model.useWatchLocation()
                        busy = false
                        failed = r == .ok ? nil : model.l10n.t(r == .denied ? "location_denied" : "location_fix_failed")
                    }
                } label: {
                    if busy { ProgressView() } else { Label(model.l10n.t("watch_this_watch"), systemImage: "location.fill") }
                }
                .disabled(busy)
                if let failed { Text(failed).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center) }
                SettingsButton()
            }
        }
    }
}

/// Opens Settings (at the end of the prayer page).
struct SettingsButton: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        Button { model.showSettings = true } label: {
            Label(model.l10n.t("settings"), systemImage: "gearshape")
        }
        .padding(.top, 6)
    }
}
