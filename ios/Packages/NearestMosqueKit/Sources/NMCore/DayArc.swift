import Foundation

/// The half of the day in progress, for the sky card's arc: daylight runs from sunrise to Maghrib (the
/// sun moves along the arc), night from Maghrib to the next sunrise (the moon). `fraction` is elapsed
/// time, not the sun's altitude. The prayers that fall inside the half sit on the arc as marks.
/// Mirrors android/core DayArc.kt.
public struct DayArc: Equatable, Sendable {
    public struct Mark: Equatable, Sendable {
        public let event: PrayerEvent
        public let at: Date
        public let fraction: Double
    }

    public let isDay: Bool
    public let start: Date
    public let end: Date
    public let fraction: Double
    public let marks: [Mark]

    /// The event at the left end of the arc (sunrise by day, Maghrib by night) and at the right end.
    public var startEvent: PrayerEvent { isDay ? .sunrise : .maghrib }
    public var endEvent: PrayerEvent { isDay ? .maghrib : .sunrise }

    /// `days` is yesterday, today and tomorrow. Nil when a sunrise or Maghrib is missing (polar days).
    public static func at(_ now: Date, days: [DaySchedule]) -> DayArc? {
        guard days.count == 3 else { return nil }
        let (yesterday, today, tomorrow) = (days[0], days[1], days[2])
        guard let sunrise = today[.sunrise], let maghrib = today[.maghrib] else { return nil }
        let isDay: Bool, start: Date, end: Date
        if now < sunrise {
            guard let m = yesterday[.maghrib] else { return nil }
            (isDay, start, end) = (false, m, sunrise)
        } else if now < maghrib {
            (isDay, start, end) = (true, sunrise, maghrib)
        } else {
            guard let s = tomorrow[.sunrise] else { return nil }
            (isDay, start, end) = (false, maghrib, s)
        }
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return nil }
        let frac: (Date) -> Double = { min(1, max(0, $0.timeIntervalSince(start) / total)) }
        let inner: [PrayerEvent] = isDay ? [.dhuhr, .asr] : [.isha, .fajr]
        var marks: [Mark] = []
        for day in days {
            for e in inner {
                if let at = day[e], at > start, at < end { marks.append(Mark(event: e, at: at, fraction: frac(at))) }
            }
        }
        marks.sort { $0.at < $1.at }
        return DayArc(isDay: isDay, start: start, end: end, fraction: frac(now), marks: marks)
    }
}
