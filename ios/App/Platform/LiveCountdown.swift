import Foundation
import NMCore
#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import ActivityKit
#endif

/// The next-prayer countdown on the Lock Screen and in the Dynamic Island (Live Activity). This app has no server to
/// push updates, so the card moves on by itself: it goes stale at the Adhan or iqama it counts down to, and the views
/// then count down to the next Adhan. The app brings it up to date whenever it runs (on screen, settings changes,
/// background refresh, the Adhan's Stop, notification buttons). Apple lets an app start one only while it is on
/// screen or from a button on the card or the alarm (a Live Activity intent, such as the Adhan's Stop); elsewhere in
/// the background iOS refuses and it is only updated. iOS ends it 8 hours after it starts (it leaves the Dynamic
/// Island, and the last card can stay on the Lock Screen up to 4 hours more, no longer updated), so a new one replaces
/// one that would end too soon whenever iOS lets one start.
@MainActor
enum LiveCountdown {
    static func refresh(_ model: AppModel, now: Date = Date()) async {
        #if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
        // One at a time (two side by side could each start a card); one asked for meanwhile runs once more after it,
        // with the time then.
        if let task = busy {
            again = true
            await task.value
            return
        }
        let task = Task {
            var when = now
            repeat {
                Self.again = false
                await Self.update(model, now: when)
                when = Date()
            } while Self.again
            Self.busy = nil
        }
        busy = task
        await task.value
        #endif
    }

    #if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
    private static var busy: Task<Void, Never>?
    private static var again = false
    /// While the app runs, the next refresh, just after the Adhan or iqama counted down to.
    private static var wake: Task<Void, Never>?

    private static func update(_ model: AppModel, now: Date) async {
        let all = Activity<PrayerActivityAttributes>.activities
        // Ended (at iOS's 8-hour limit; an update does nothing then) or dismissed ones don't count: replaced below.
        let running = all.filter { $0.activityState == .active || $0.activityState == .stale }
        // A compass left over from an app closed during its two minutes ends here; one running now keeps the island.
        if !QiblaIsland.shared.isRunning {
            for q in Activity<QiblaActivityAttributes>.activities { await q.end(nil, dismissalPolicy: .immediate) }
        }
        guard model.settings.liveCountdown, ActivityAuthorizationInfo().areActivitiesEnabled, let shown = Self.content(model, now: now) else {
            wake?.cancel()
            for a in all { await a.end(nil, dismissalPolicy: .immediate) }
            return
        }
        let (attributes, state) = shown
        // Stale at the Adhan or iqama counted down to: the views then count down to the next Adhan. Lower relevance
        // than the Qibla compass, which takes the Dynamic Island for its two minutes.
        let activity = ActivityContent(state: state, staleDate: state.at, relevanceScore: 10)
        wake?.cancel()
        wake = Task {
            try? await Task.sleep(for: .seconds(max(1, state.at.timeIntervalSinceNow + 1)))
            if !Task.isCancelled { await Self.refresh(model) }
        }
        // While the Qibla compass runs (the app may be woken in the background for it), leave the countdown alone.
        if QiblaIsland.shared.isRunning, !running.isEmpty { return }
        let current = running.first { $0.attributes == attributes }
        // A new card replaces one from an older app, one iOS would end within 6 hours (so the updates made later have
        // the most time), or one that would end before the Adhan it counts down to once stale when a new one would not.
        // iOS refuses one in the background except from a Live Activity intent (the Adhan's Stop).
        let until = state.following?.first { $0.kind == .prayer }?.at ?? state.at
        let renew = current?.attributes.started.map { started in
            let ends = started.addingTimeInterval(8 * 3600)
            return ends < now.addingTimeInterval(6 * 3600) || (ends < until && until <= now.addingTimeInterval(8 * 3600))
        } ?? true
        var renewed = attributes
        renewed.started = now
        if renew, (try? Activity.request(attributes: renewed, content: activity, pushType: nil)) != nil {
            // The new card is up before the old ones go, so a refused request never leaves none.
            for a in all { await a.end(nil, dismissalPolicy: .immediate) }
        } else if let a = current ?? running.first {
            // Not due for a new one, or refused: the running card moves on; a later start replaces it.
            for x in all where x.id != a.id { await x.end(nil, dismissalPolicy: .immediate) }
            if a.content.state != state { await a.update(activity) }
        }
    }

    /// What the Lock Screen and the Dynamic Island show now (the onboarding draws the same on its preview); nil without
    /// a place or a next prayer.
    static func content(_ model: AppModel, now: Date) -> (PrayerActivityAttributes, PrayerActivityAttributes.ContentState)? {
        guard let loc = model.settings.location else { return nil }
        let l10n = model.l10n
        let days = model.days(now: now)
        guard days.count == 3 else { return nil }
        // The Adhans and iqamas from now on: the first is counted down to (while a prayer waits for its iqama, that
        // iqama), the two after it follow once it has passed.
        let points = Self.points(days, after: now, iqama: model.settings.iqama)
        guard let next = points.first else { return nil }
        // Today's prayers along the bottom; after Isha, tomorrow's. Once `next` has passed, the card counts down to the
        // Adhan after it, along tomorrow's row when that Adhan is tomorrow's.
        let today = next.day == days[2].date ? days[2] : days[1]
        let nextAdhan = points.dropFirst().prefix(2).first { !$0.iqama }
        let tomorrow: DaySchedule? = today.date != days[2].date && nextAdhan?.day == days[2].date ? days[2] : nil
        let name = l10n.t(Format.prayerKey(next.event))
        let current = PrayerEvent.allCases.last { (days[1][$0] ?? .distantFuture) <= now } ?? (days[0][.isha] != nil ? .isha : nil)
        func time(_ d: Date) -> String { Format.time(d, zone: loc.zone, locale: l10n.locale) }
        // Each one's slot in its own day's row.
        func index(_ p: Point) -> Int {
            p.day == today.date || p.day == tomorrow?.date ? PrayerEvent.prayers.firstIndex(of: p.event) ?? -1 : -1
        }
        func row(_ d: DaySchedule) -> [PrayerActivityAttributes.Slot] {
            PrayerEvent.prayers.map { e in PrayerActivityAttributes.Slot(name: l10n.t(Format.prayerKey(e)), time: d[e].map(time) ?? "–") }
        }
        let slots = row(today)
        let following: [PrayerActivityAttributes.Event] = points.dropFirst().prefix(2).enumerated().map { i, p in
            PrayerActivityAttributes.Event(kind: p.iqama ? .iqama : .prayer, prayer: l10n.t(Format.prayerKey(p.event)), symbol: Theme.icon(p.event),
                                           at: p.at, start: p.start ?? next.at, timeText: p.iqama ? l10n.t("iqama_at", time(p.at)) : time(p.at),
                                           index: index(p), period: points[i].event.rawValue)
        }
        // This prayer's iqama: the one counted down to, or the one after its Adhan.
        let iqamaAt: Date? = next.iqama ? next.at : following.first.flatMap { $0.kind == .iqama ? $0.at : nil }
        let state = PrayerActivityAttributes.ContentState(
            prayer: name, symbol: Theme.icon(next.event), at: next.at, start: next.start ?? now,
            timeText: time(next.at), city: loc.name,
            nowTitle: l10n.t("reminder_title", name), slots: slots, nextIndex: index(next),
            period: current?.rawValue ?? "",
            iqama: iqamaAt, iqamaLabel: iqamaAt == nil ? nil : l10n.t("iqama_in"),
            iqamaText: iqamaAt.map { l10n.t("iqama_at", time($0)) },
            kind: next.iqama ? .iqama : .prayer, following: following, followingSlots: tomorrow.map(row))
        let attributes = PrayerActivityAttributes(rtl: l10n.isRTL, style: model.settings.widgetLook, nextLabel: l10n.t("next_prayer"), qiblaLabel: l10n.t("qibla"),
                                                  mosqueLabel: l10n.t("tab_mosques"))
        return (attributes, state)
    }

    /// An Adhan, or a prayer's iqama; `start` is when its countdown begins (the Adhan before it; for an iqama, its
    /// own Adhan), `day` the date of the prayer times it belongs to.
    private struct Point {
        let event: PrayerEvent
        let iqama: Bool
        let at: Date
        let start: Date?
        let day: CivilDate
    }

    /// The Adhans in `days` after `now` and, with the iqama on, each prayer's iqama, in order.
    private static func points(_ days: [DaySchedule], after now: Date, iqama: IqamaSettings) -> [Point] {
        let adhans = days.flatMap { d in PrayerEvent.prayers.compactMap { e in d[e].map { (e, $0, d.date) } } }.sorted { $0.1 < $1.1 }
        var out: [Point] = []
        for (i, (e, adhan, day)) in adhans.enumerated() {
            out.append(Point(event: e, iqama: false, at: adhan, start: i > 0 ? adhans[i - 1].1 : nil, day: day))
            let iq = iqama.iqama(e, adhan: adhan)
            // As in the app: never past the next Adhan, whatever the waits are set to.
            if iqama.enabled, iq > adhan, i + 1 == adhans.count || iq < adhans[i + 1].1 {
                out.append(Point(event: e, iqama: true, at: iq, start: adhan, day: day))
            }
        }
        return out.filter { $0.at > now }
    }
    #endif
}

#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
extension PrayerActivityAttributes: Equatable {
    static func == (a: Self, b: Self) -> Bool {
        a.rtl == b.rtl && a.style == b.style && a.nextLabel == b.nextLabel && a.qiblaLabel == b.qiblaLabel && a.mosqueLabel == b.mosqueLabel
    }
}
#endif
