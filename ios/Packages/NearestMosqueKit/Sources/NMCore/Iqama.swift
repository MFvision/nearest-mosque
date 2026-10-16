import Foundation

/// The time between the Adhan and the iqama, and the prayer after it. These are the usual waits (as in
/// mosques in Saudi Arabia), not any mosque's own timetable; the user can change each one. Mirrors
/// android/core Iqama.kt.
public struct IqamaSettings: Codable, Hashable, Sendable {
    /// Show the wait for the iqama after each Adhan; off goes straight from one Adhan to the countdown to the next.
    public var enabled = true
    /// Minutes from the Adhan to the iqama, per prayer (PrayerEvent raw value); a prayer not listed has its default.
    public var minutes: [String: Int] = [:]
    /// Minutes after the iqama shown as the prayer itself, before the countdown to the next prayer.
    public var prayerMinutes = 15
    /// A notification at the iqama.
    public var alert = true

    public static let defaults: [PrayerEvent: Int] = [.fajr: 25, .dhuhr: 20, .asr: 20, .maghrib: 10, .isha: 20]
    public static let choices = [5, 10, 15, 20, 25, 30, 35, 40, 45]
    public static let prayerChoices = [5, 10, 15, 20, 30]

    public init(enabled: Bool = true, minutes: [String: Int] = [:], prayerMinutes: Int = 15, alert: Bool = true) {
        self.enabled = enabled; self.minutes = minutes; self.prayerMinutes = prayerMinutes; self.alert = alert
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = (try? c.decodeIfPresent(Bool.self, forKey: .enabled)) ?? true
        minutes = ((try? c.decodeIfPresent([String: Int].self, forKey: .minutes)) ?? nil) ?? [:]
        prayerMinutes = ((try? c.decodeIfPresent(Int.self, forKey: .prayerMinutes)) ?? nil) ?? 15
        alert = (try? c.decodeIfPresent(Bool.self, forKey: .alert)) ?? true
    }

    /// Minutes from this prayer's Adhan to its iqama (0 for sunrise, which has none).
    public func wait(_ e: PrayerEvent) -> Int {
        guard e.isPrayer else { return 0 }
        return max(0, minutes[e.rawValue] ?? Self.defaults[e] ?? 0)
    }

    public mutating func setWait(_ e: PrayerEvent, _ m: Int) { minutes[e.rawValue] = m }

    /// The iqama of a prayer whose Adhan is at `adhan`.
    public func iqama(_ e: PrayerEvent, adhan: Date) -> Date { adhan.addingTimeInterval(Double(wait(e)) * 60) }
}

/// What the prayer times show at a moment: the countdown to the next Adhan, the wait for the iqama after an
/// Adhan, or the prayer itself after the iqama.
public struct PrayerMoment: Equatable, Sendable {
    public enum Stage: String, Sendable { case upcoming, toIqama, prayer }

    public let stage: Stage
    /// The prayer counted down to (upcoming) or the one being called and prayed (toIqama, prayer).
    public let event: PrayerEvent
    /// That prayer's Adhan.
    public let adhan: Date
    /// Its iqama (nil while upcoming with the iqama off).
    public let iqama: Date?
    /// When this stage ends: the Adhan (upcoming), the iqama (toIqama), the end of the prayer (prayer).
    public let until: Date
    /// When this stage began: the previous prayer's end (upcoming), the Adhan (toIqama), the iqama (prayer).
    public let since: Date?
    /// The next Adhan after now, whatever the stage.
    public let next: Upcoming
}

extension PrayerCalculator {
    /// What to show at `now`: within a prayer's wait for the iqama, or its prayer time, that prayer; otherwise the
    /// countdown to the next Adhan. With the iqama off this is always the countdown.
    public func moment(_ days: [DaySchedule], now: Date, iqama: IqamaSettings) -> PrayerMoment? {
        guard let next = nextPrayer(days, now: now) else { return nil }
        let prayers = days.flatMap { d in PrayerEvent.prayers.compactMap { e in d[e].map { (e, $0) } } }.sorted { $0.1 < $1.1 }
        if iqama.enabled, let (e, adhan) = prayers.last(where: { $0.1 <= now }) {
            let iq = iqama.iqama(e, adhan: adhan)
            let end = iq.addingTimeInterval(Double(iqama.prayerMinutes) * 60)
            // Never past the next Adhan, whatever the waits are set to.
            if now < min(iq, next.at), iq > adhan {
                return PrayerMoment(stage: .toIqama, event: e, adhan: adhan, iqama: iq, until: min(iq, next.at), since: adhan, next: next)
            }
            if now < min(end, next.at), end > iq {
                return PrayerMoment(stage: .prayer, event: e, adhan: adhan, iqama: iq, until: min(end, next.at), since: iq, next: next)
            }
        }
        let since: Date? = {
            guard iqama.enabled, let start = next.periodStart, let prev = prayers.last(where: { $0.1 == start }) else { return next.periodStart }
            return min(next.at, iqama.iqama(prev.0, adhan: start).addingTimeInterval(Double(iqama.prayerMinutes) * 60))
        }()
        return PrayerMoment(stage: .upcoming, event: next.event, adhan: next.at,
                            iqama: iqama.enabled ? iqama.iqama(next.event, adhan: next.at) : nil, until: next.at, since: since, next: next)
    }

    /// Every moment the stage changes (each Adhan, iqama and end of prayer) after `now`, in order: the times a
    /// widget timeline needs entries at.
    public func stageChanges(_ days: [DaySchedule], after now: Date, iqama: IqamaSettings) -> [Date] {
        var out: [Date] = []
        for d in days {
            for e in PrayerEvent.prayers {
                guard let adhan = d[e] else { continue }
                out.append(adhan)
                guard iqama.enabled else { continue }
                let iq = iqama.iqama(e, adhan: adhan)
                out.append(iq)
                out.append(iq.addingTimeInterval(Double(iqama.prayerMinutes) * 60))
            }
        }
        return Array(Set(out.filter { $0 > now })).sorted()
    }
}
