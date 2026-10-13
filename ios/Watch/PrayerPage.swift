import NMCore
import SwiftUI

/// The first page: the next prayer and its countdown over the sky of the hour, then today's times with their bells.
struct PrayerPage: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { tl in
            let now = tl.date
            let days = model.days(now: now)
            let today = days.count == 3 ? days[1] : nil
            let next = model.calculator.nextPrayer(days, now: now)
            let current = PrayerEvent.allCases.last { (today?[$0] ?? .distantFuture) <= now }
            if let s = model.state, let next {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        NextPrayerCard(state: s, next: next, now: now, accent: model.accent)
                        VStack(spacing: 4) {
                            ForEach(PrayerEvent.allCases, id: \.self) { e in
                                if let at = today?[e] { row(s, e, at, now: e == current, past: at <= now && e != current) }
                            }
                        }
                        Label(s.name, systemImage: "mappin.and.ellipse").font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                        Text(s.hijri(now)).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .containerBackground(LinearGradient(colors: WatchColors.sky(current, sinceSunrise: today?[.sunrise].map { now.timeIntervalSince($0) }),
                                                    startPoint: .top, endPoint: .bottom), for: .tabView)
            } else {
                WaitingView()
            }
        }
        .navigationTitle(model.l10n.t("prayer_section"))
    }

    private func row(_ s: SharedState, _ e: PrayerEvent, _ at: Date, now: Bool, past: Bool) -> some View {
        HStack(spacing: 6) {
            Image(systemName: e.watchSymbol).frame(width: 18).foregroundStyle(now ? Color.black : model.accent)
            Text(s.t("prayer_\(e.rawValue)")).lineLimit(1).minimumScaleFactor(0.7)
            Spacer(minLength: 4)
            Text(s.time(at)).monospacedDigit().lineLimit(1)
            if e.isPrayer {
                Image(systemName: s.reminderOn(e) ? "bell.fill" : "bell.slash").font(.caption2)
                    .foregroundStyle(now ? Color.black.opacity(0.7) : (s.reminderOn(e) ? model.accent : .secondary))
                    .accessibilityLabel(s.t(s.reminderOn(e) ? "reminder_on_a11y" : "reminder_off_a11y", s.t("prayer_\(e.rawValue)")))
            } else {
                Image(systemName: "bell").font(.caption2).hidden()
            }
        }
        .font(.body.weight(now ? .semibold : .regular))
        .foregroundStyle(now ? Color.black : .white)
        .opacity(past ? 0.5 : 1)
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background(now ? model.accent : Color.white.opacity(0.08), in: Capsule())
    }
}

/// The next prayer: name and icon, the time, a ring filling over the period, and the time left.
struct NextPrayerCard: View {
    let state: SharedState
    let next: Upcoming
    let now: Date
    let accent: Color

    var body: some View {
        let start = min(next.periodStart ?? now, now)
        HStack(spacing: 10) {
            ZStack {
                ProgressView(timerInterval: start...max(start.addingTimeInterval(1), next.at), countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .progressViewStyle(.circular)
                .tint(accent)
                Image(systemName: next.event.watchSymbol).font(.title3).foregroundStyle(accent)
            }
            .frame(width: 52, height: 52)
            VStack(alignment: .leading, spacing: 0) {
                Text(state.t("next_prayer")).font(.caption2).foregroundStyle(.secondary)
                Text(state.t("prayer_\(next.event.rawValue)")).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
                Text(state.time(next.at)).font(.system(.title3, design: .rounded).weight(.semibold)).monospacedDigit()
            }
            Spacer(minLength: 0)
        }
        Text(timerInterval: now...max(now, next.at), countsDown: true)
            .font(.system(.title2, design: .rounded).weight(.semibold).monospacedDigit())
            .foregroundStyle(accent)
            .environment(\.layoutDirection, .leftToRight)
            .frame(maxWidth: .infinity, alignment: .center)
            .accessibilityLabel(state.t("countdown_a11y", state.t("prayer_\(next.event.rawValue)"), state.time(next.at),
                                        Format.remainingLong(next.at.timeIntervalSince(now), locale: state.locale)))
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
            }
        }
    }
}
