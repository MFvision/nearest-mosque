import Foundation

/// What the user asked to be told, beyond the per-prayer bells. Mirrors android/core Alerts.kt.
public struct AlertSettings: Codable, Hashable, Sendable {
    /// Minutes before each enabled prayer for an early reminder; 0 = off.
    public var minutesBefore = 0
    /// Friday: a reminder `fridayLeadMinutes` before Dhuhr (Jumu'ah).
    public var friday = false
    /// In Ramadan: suhoor `suhoorLeadMinutes` before Fajr, and iftar at Maghrib.
    public var ramadan = false
    /// An alarm (rings until stopped) this many minutes before Fajr; nil = off.
    public var fajrAlarmMinutesBefore: Int?
    /// Friday morning: a reminder to read Surat Al-Kahf, with its first verse.
    public var kahf = false
    /// A verse before sleep (Ayat al-Kursi, or the last verses of Al-Baqarah) at this many minutes after
    /// midnight; nil = off.
    public var bedtimeMinutes: Int?

    public static let fridayLeadMinutes = 45
    public static let suhoorLeadMinutes = 45
    public static let beforeChoices = [0, 5, 10, 15, 20, 30]
    public static let fajrAlarmChoices = [0, 10, 20, 30, 45]

    public static let kahfHour = 9
    public static let defaultBedtimeMinutes = 22 * 60 + 30

    public init(minutesBefore: Int = 0, friday: Bool = false, ramadan: Bool = false, fajrAlarmMinutesBefore: Int? = nil,
                kahf: Bool = false, bedtimeMinutes: Int? = nil) {
        self.minutesBefore = minutesBefore; self.friday = friday; self.ramadan = ramadan; self.fajrAlarmMinutesBefore = fajrAlarmMinutesBefore
        self.kahf = kahf; self.bedtimeMinutes = bedtimeMinutes
    }

    /// Settings saved before a field existed decode with that field's default.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        minutesBefore = try c.decodeIfPresent(Int.self, forKey: .minutesBefore) ?? 0
        friday = try c.decodeIfPresent(Bool.self, forKey: .friday) ?? false
        ramadan = try c.decodeIfPresent(Bool.self, forKey: .ramadan) ?? false
        fajrAlarmMinutesBefore = try c.decodeIfPresent(Int.self, forKey: .fajrAlarmMinutesBefore)
        kahf = try c.decodeIfPresent(Bool.self, forKey: .kahf) ?? false
        bedtimeMinutes = try c.decodeIfPresent(Int.self, forKey: .bedtimeMinutes)
    }
}

public enum AlertKind: Int, Sendable { case atPrayer, before, friday, suhoor, iftar, fajrAlarm }

public struct PlannedAlert: Equatable, Sendable {
    public let kind: AlertKind
    public let event: PrayerEvent
    public let at: Date
    public let date: CivilDate
}

/// Turns schedules into the alerts to schedule, in time order, after `now` (the platforms cap how many
/// they keep). `isRamadan` says whether a civil date falls in Ramadan (Hijri, with the user's adjustment).
public enum AlertPlanner {
    public static func plan(_ days: [DaySchedule], now: Date, atPrayer: Set<PrayerEvent>, alerts: AlertSettings,
                            isRamadan: (CivilDate) -> Bool) -> [PlannedAlert] {
        var out: [PlannedAlert] = []
        for d in days {
            for e in PrayerEvent.prayers {
                guard let at = d[e], atPrayer.contains(e) else { continue }
                out.append(PlannedAlert(kind: .atPrayer, event: e, at: at, date: d.date))
                if alerts.minutesBefore > 0 {
                    out.append(PlannedAlert(kind: .before, event: e, at: at.addingTimeInterval(-Double(alerts.minutesBefore) * 60), date: d.date))
                }
            }
            if alerts.friday, d.date.isFriday, let dhuhr = d[.dhuhr] {
                out.append(PlannedAlert(kind: .friday, event: .dhuhr, at: dhuhr.addingTimeInterval(-Double(AlertSettings.fridayLeadMinutes) * 60), date: d.date))
            }
            if alerts.ramadan, isRamadan(d.date) {
                if let f = d[.fajr] { out.append(PlannedAlert(kind: .suhoor, event: .fajr, at: f.addingTimeInterval(-Double(AlertSettings.suhoorLeadMinutes) * 60), date: d.date)) }
                if let m = d[.maghrib] { out.append(PlannedAlert(kind: .iftar, event: .maghrib, at: m, date: d.date)) }
            }
            if let m = alerts.fajrAlarmMinutesBefore, let f = d[.fajr] {
                out.append(PlannedAlert(kind: .fajrAlarm, event: .fajr, at: f.addingTimeInterval(-Double(m) * 60), date: d.date))
            }
        }
        return out.filter { $0.at > now }.sorted { ($0.at, $0.kind.rawValue) < ($1.at, $1.kind.rawValue) }
    }
}

extension CivilDate {
    /// Friday in the proleptic Gregorian calendar.
    public var isFriday: Bool {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c.component(.weekday, from: noonUTC) == 6
    }
}
