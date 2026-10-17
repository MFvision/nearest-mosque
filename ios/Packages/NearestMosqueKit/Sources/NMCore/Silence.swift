import Foundation

/// Silencing the phone during each prayer: from the Adhan or the iqama until the end of the prayer, and on Friday
/// from the Adhan for the khutbah and the prayer. Android turns Do Not Disturb on and off itself (with the user's
/// permission); iOS lets no app change Focus, so there the times feed a Shortcut the user runs. Mirrors android/core
/// Silence.kt.
public struct SilenceSettings: Codable, Hashable, Sendable {
    public enum Start: String, Codable, Sendable, CaseIterable { case adhan, iqama }

    public var enabled = false
    /// When the silence begins on ordinary days; it always ends with the prayer (the iqama plus its prayer minutes).
    public var start: Start = .iqama
    /// The prayers silenced (PrayerEvent raw values).
    public var prayers: [String] = PrayerEvent.prayers.map(\.rawValue)
    /// On Friday, minutes from the Dhuhr Adhan for the khutbah and the Friday prayer.
    public var fridayMinutes = 60

    public static let fridayChoices = [45, 60, 75, 90]

    public init(enabled: Bool = false, start: Start = .iqama, prayers: [String] = PrayerEvent.prayers.map(\.rawValue), fridayMinutes: Int = 60) {
        self.enabled = enabled; self.start = start; self.prayers = prayers; self.fridayMinutes = fridayMinutes
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = (try? c.decodeIfPresent(Bool.self, forKey: .enabled)) ?? false
        start = ((try? c.decodeIfPresent(Start.self, forKey: .start)) ?? nil) ?? .iqama
        prayers = ((try? c.decodeIfPresent([String].self, forKey: .prayers)) ?? nil) ?? PrayerEvent.prayers.map(\.rawValue)
        fridayMinutes = ((try? c.decodeIfPresent(Int.self, forKey: .fridayMinutes)) ?? nil) ?? 60
    }

    public func includes(_ e: PrayerEvent) -> Bool { e.isPrayer && prayers.contains(e.rawValue) }

    public mutating func set(_ e: PrayerEvent, _ on: Bool) {
        prayers.removeAll { $0 == e.rawValue }
        if on { prayers.append(e.rawValue) }
    }

    /// Every silence that has not ended by `now` (the one under way first), in order. Each ends by the next
    /// prayer's Adhan at the latest. With the setting off, none.
    public func windows(_ days: [DaySchedule], iqama: IqamaSettings, after now: Date) -> [SilenceWindow] {
        guard enabled else { return [] }
        let all = days.flatMap { d in PrayerEvent.prayers.compactMap { e in d[e].map { (e, $0, d.date) } } }.sorted { $0.1 < $1.1 }
        var out: [SilenceWindow] = []
        for (i, (e, adhan, date)) in all.enumerated() where includes(e) {
            let friday = e == .dhuhr && date.weekday == 6
            let iq = iqama.iqama(e, adhan: adhan)
            var from = friday || start == .adhan ? adhan : iq
            var to = friday ? adhan.addingTimeInterval(Double(fridayMinutes) * 60) : iq.addingTimeInterval(Double(iqama.prayerMinutes) * 60)
            if i + 1 < all.count { to = min(to, all[i + 1].1) }
            from = min(from, to)
            guard to > from, to > now else { continue }
            out.append(SilenceWindow(event: e, date: date, start: from, end: to, friday: friday))
        }
        return out
    }

    /// The silence under way at `now`, if any.
    public func current(_ days: [DaySchedule], iqama: IqamaSettings, now: Date) -> SilenceWindow? {
        windows(days, iqama: iqama, after: now).first { $0.start <= now }
    }
}

public struct SilenceWindow: Equatable, Sendable {
    public let event: PrayerEvent
    /// The day of that prayer's times (as in the notifications for it).
    public let date: CivilDate
    public let start: Date
    public let end: Date
    /// The Friday prayer (Dhuhr on Friday, from its Adhan).
    public let friday: Bool
}

extension CivilDate {
    /// 1 Sunday ... 7 Saturday.
    public var weekday: Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let d = cal.date(from: DateComponents(year: year, month: month, day: day))!
        return cal.component(.weekday, from: d)
    }
}
