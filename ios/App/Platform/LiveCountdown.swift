import Foundation
import NMCore
#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import ActivityKit
#endif

/// The next-prayer countdown on the Lock Screen and in the Dynamic Island (Live Activity). Started, moved on to
/// the following prayer, or ended whenever the app comes to the screen or its settings change: Apple lets an app
/// start one only while it is open, and this app has no server to push updates. The activity goes stale at the
/// Adhan time, and the views then show "Time for Asr" with the countdown to the iqama, with no update needed; with
/// the iqama off they say "Time for Asr" a quarter of an hour. iOS removes it after 8 hours at most.
@MainActor
enum LiveCountdown {
    static func refresh(_ model: AppModel, now: Date = Date()) async {
        #if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        let running = Activity<PrayerActivityAttributes>.activities
        guard model.settings.liveCountdown, ActivityAuthorizationInfo().areActivitiesEnabled, let loc = model.settings.location else {
            for a in running { await a.end(nil, dismissalPolicy: .immediate) }
            return
        }
        let l10n = model.l10n
        let days = model.days(now: now)
        let iqama = model.settings.iqama
        guard days.count == 3, let moment = model.calculator.moment(days, now: now, iqama: iqama) else {
            for a in running { await a.end(nil, dismissalPolicy: .immediate) }
            return
        }
        // While a prayer waits for its iqama, that prayer (its Adhan has come: stale at once, so the iqama
        // countdown shows); otherwise the next Adhan.
        let next: Upcoming = moment.stage == .toIqama
            ? Upcoming(event: moment.event, at: moment.adhan, periodStart: moment.adhan, isTomorrow: false) : moment.next
        let iqamaAt: Date? = iqama.enabled && iqama.wait(next.event) > 0 ? iqama.iqama(next.event, adhan: next.at) : nil
        let today = days[1]
        let name = l10n.t(Format.prayerKey(next.event))
        let current = PrayerEvent.allCases.last { (today[$0] ?? .distantFuture) <= now } ?? (days[0][.isha] != nil ? .isha : nil)
        let slots = PrayerEvent.prayers.map { e in
            PrayerActivityAttributes.Slot(name: l10n.t(Format.prayerKey(e)), time: today[e].map { Format.time($0, zone: loc.zone, locale: l10n.locale) } ?? "–")
        }
        let state = PrayerActivityAttributes.ContentState(
            prayer: name, symbol: Theme.icon(next.event), at: next.at, start: next.periodStart ?? now,
            timeText: Format.time(next.at, zone: loc.zone, locale: l10n.locale), city: loc.name,
            nowTitle: l10n.t("reminder_title", name), slots: slots,
            nextIndex: next.isTomorrow ? -1 : (PrayerEvent.prayers.firstIndex(of: next.event) ?? -1),
            period: current?.rawValue ?? "",
            iqama: iqamaAt, iqamaLabel: iqamaAt == nil ? nil : l10n.t("iqama_in"),
            iqamaText: iqamaAt.map { l10n.t("iqama_at", Format.time($0, zone: loc.zone, locale: l10n.locale)) })
        let attributes = PrayerActivityAttributes(rtl: l10n.isRTL, style: model.settings.widgetLook, nextLabel: l10n.t("next_prayer"), qiblaLabel: l10n.t("qibla"),
                                                  mosqueLabel: l10n.t("tab_mosques"))
        // Stale at the Adhan when there is an iqama to count down to, else a quarter of an hour after the prayer
        // time: the views then say "Time for …". Lower relevance than the Qibla compass, which takes the Dynamic
        // Island for its two minutes.
        let content = ActivityContent(state: state, staleDate: iqamaAt == nil ? next.at.addingTimeInterval(15 * 60) : next.at, relevanceScore: 10)
        // While the Qibla compass runs (the app may be woken in the background for it), leave the countdown alone.
        if !Activity<QiblaActivityAttributes>.activities.isEmpty, !running.isEmpty { return }
        if let a = running.first, a.attributes == attributes {
            for extra in running.dropFirst() { await extra.end(nil, dismissalPolicy: .immediate) }
            if a.content.state != state { await a.update(content) }
        } else {
            for a in running { await a.end(nil, dismissalPolicy: .immediate) }
            _ = try? Activity.request(attributes: attributes, content: content, pushType: nil)
        }
        #endif
    }
}

#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
extension PrayerActivityAttributes: Equatable {
    static func == (a: Self, b: Self) -> Bool {
        a.rtl == b.rtl && a.style == b.style && a.nextLabel == b.nextLabel && a.qiblaLabel == b.qiblaLabel && a.mosqueLabel == b.mosqueLabel
    }
}
#endif
