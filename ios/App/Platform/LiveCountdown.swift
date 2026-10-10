import Foundation
import NMCore
#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
#endif

/// The next-prayer countdown on the Lock Screen and in the Dynamic Island (Live Activity). Started, moved on to
/// the following prayer, or ended whenever the app comes to the screen or its settings change: Apple lets an app
/// start one only while it is open, and this app has no server to push updates. It shows "Time for Asr" once
/// the time has come and ends with the next refresh; iOS removes it after 8 hours at most.
@MainActor
enum LiveCountdown {
    static func refresh(_ model: AppModel, now: Date = Date()) async {
        #if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
        let running = Activity<PrayerActivityAttributes>.activities
        guard model.settings.liveCountdown, ActivityAuthorizationInfo().areActivitiesEnabled, let loc = model.settings.location else {
            for a in running { await a.end(nil, dismissalPolicy: .immediate) }
            return
        }
        let l10n = model.l10n
        let days = model.days(now: now)
        guard days.count == 3, let next = model.calculator.nextPrayer(days, now: now) else {
            for a in running { await a.end(nil, dismissalPolicy: .immediate) }
            return
        }
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
            period: current?.rawValue ?? "")
        let attributes = PrayerActivityAttributes(rtl: l10n.isRTL, nextLabel: l10n.t("next_prayer"), qiblaLabel: l10n.t("qibla"),
                                                  mosqueLabel: l10n.t("tab_mosques"))
        // Stale a quarter of an hour after the prayer time: the views then say "Time for …".
        let content = ActivityContent(state: state, staleDate: next.at.addingTimeInterval(15 * 60))
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

#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
extension PrayerActivityAttributes: Equatable {
    static func == (a: Self, b: Self) -> Bool {
        a.rtl == b.rtl && a.nextLabel == b.nextLabel && a.qiblaLabel == b.qiblaLabel && a.mosqueLabel == b.mosqueLabel
    }
}
#endif
