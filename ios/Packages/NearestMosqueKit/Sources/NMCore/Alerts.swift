import Foundation

/// Which recording plays for an Adhan.
public enum AdhanSource: String, Codable, CaseIterable, Sendable {
    case makkah, madinah

    /// Both mosques have a Fajr recording (shared/audio/CREDITS.md); kept so a future source without one can say so.
    public var hasFajr: Bool { true }
}

/// The language of the spoken reminders ("Ten minutes remain until the call to Maghrib prayer").
public enum VoiceLanguage: String, Codable, CaseIterable, Sendable {
    case arabic, english

    /// Arabic for an Arabic interface, English otherwise.
    public static func forInterface(_ language: String) -> VoiceLanguage { language == "ar" ? .arabic : .english }
}

/// Who recites the verses played by the Friday, Qur'an and bedtime reminders.
public enum Reciter: String, Codable, CaseIterable, Sendable {
    /// Mishary Rashid Alafasy.
    case alafasy
    /// Mahmoud Khalil Al-Husary (Friday) and Abdul Basit Abdul Samad (evening verse).
    case classic
}

/// For one prayer: which of the sounds and reminders apply to it. Each is also switched on or off for all
/// prayers in AlertSettings; a sound plays for a prayer only when both are on.
public struct PrayerSounds: Codable, Hashable, Sendable {
    public var adhan = true
    public var before10 = true
    public var before5 = true
    public var near = true

    public init(adhan: Bool = true, before10: Bool = true, before5: Bool = true, near: Bool = true) {
        self.adhan = adhan; self.before10 = before10; self.before5 = before5; self.near = near
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        adhan = try c.decodeIfPresent(Bool.self, forKey: .adhan) ?? true
        before10 = try c.decodeIfPresent(Bool.self, forKey: .before10) ?? true
        before5 = try c.decodeIfPresent(Bool.self, forKey: .before5) ?? true
        near = try c.decodeIfPresent(Bool.self, forKey: .near) ?? true
    }
}

/// What the user asked to be told, beyond the per-prayer bells. Every reminder and sound has its own switch;
/// none turns another on or off. Mirrors android/core Alerts.kt.
public struct AlertSettings: Codable, Hashable, Sendable {
    /// Before the per-prayer 10 and 5 minute reminders existed: one early reminder this many minutes before
    /// each belled prayer. Older saves are moved to the new reminders when read; kept for them only.
    public var minutesBefore = 0
    /// Friday: a reminder `fridayLeadMinutes` before Dhuhr (Jumu'ah).
    public var friday = false
    /// In Ramadan: suhoor `suhoorLeadMinutes` before Fajr, and iftar at Maghrib.
    public var ramadan = false
    /// An alarm (rings until stopped) this many minutes before Fajr; nil = off.
    public var fajrAlarmMinutesBefore: Int?
    /// Friday morning: a reminder to read Surat Al-Kahf, with its first verse.
    public var kahf = false
    /// Before sleep: a reminder with Ar-Ra'd 13:28 recited, at this many minutes after midnight; nil = off.
    public var bedtimeMinutes: Int?

    /// The Adhan at Dhuhr, Asr, Maghrib and Isha, and which recording.
    public var adhan = false
    public var adhanSource = AdhanSource.makkah
    /// The Fajr Adhan (with "prayer is better than sleep"), and which recording.
    public var fajrAdhan = false
    public var fajrAdhanSource = AdhanSource.makkah
    /// Spoken reminders 10 and 5 minutes before the prayer.
    public var before10 = false
    public var before5 = false
    /// "The call to … prayer is approaching", `nearMinutes` before it.
    public var near = false
    public var nearMinutes = 15
    /// The spoken reminders' language; nil follows the interface.
    public var voice: VoiceLanguage?
    /// Per prayer (PrayerEvent raw value); a prayer not listed has everything on.
    public var perPrayer: [String: PrayerSounds] = [:]
    /// Friday only: Salli Ala Rasulillah, Al-Ahzab 33:56 recited, at this many minutes after midnight.
    public var salawat = false
    public var salawatMinutes = 11 * 60
    /// Every day: a reminder to read the Qur'an, at this many minutes after midnight.
    public var quran = false
    public var quranMinutes = 20 * 60 + 30
    public var reciter = Reciter.alafasy

    public static let fridayLeadMinutes = 45
    public static let suhoorLeadMinutes = 45
    public static let beforeChoices = [0, 5, 10, 15, 20, 30]
    public static let nearChoices = [15, 20, 30]
    public static let fajrAlarmChoices = [0, 10, 20, 30, 45]

    public static let kahfHour = 9
    public static let defaultBedtimeMinutes = 22 * 60 + 30

    public init(minutesBefore: Int = 0, friday: Bool = false, ramadan: Bool = false, fajrAlarmMinutesBefore: Int? = nil,
                kahf: Bool = false, bedtimeMinutes: Int? = nil) {
        self.minutesBefore = minutesBefore; self.friday = friday; self.ramadan = ramadan; self.fajrAlarmMinutesBefore = fajrAlarmMinutesBefore
        self.kahf = kahf; self.bedtimeMinutes = bedtimeMinutes
    }

    public func sounds(_ e: PrayerEvent) -> PrayerSounds { perPrayer[e.rawValue] ?? PrayerSounds() }

    public mutating func setSounds(_ e: PrayerEvent, _ s: PrayerSounds) { perPrayer[e.rawValue] = s }

    /// The Adhan plays at this prayer (its switch, the prayer's own switch, and a recording for it).
    public func adhanPlays(_ e: PrayerEvent) -> Bool {
        guard e.isPrayer, sounds(e).adhan else { return false }
        return e == .fajr ? fajrAdhan && fajrAdhanSource.hasFajr : adhan
    }

    /// Settings saved before a field existed decode with that field's default; an old single early reminder
    /// becomes the matching new one (10, 5, or "approaching" at 15, 20 or 30 minutes).
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func get<T: Decodable>(_ k: CodingKeys, _ d: T) -> T { ((try? c.decodeIfPresent(T.self, forKey: k)) ?? nil) ?? d }
        minutesBefore = get(.minutesBefore, 0)
        friday = get(.friday, false)
        ramadan = get(.ramadan, false)
        fajrAlarmMinutesBefore = (try? c.decodeIfPresent(Int.self, forKey: .fajrAlarmMinutesBefore)) ?? nil
        kahf = get(.kahf, false)
        bedtimeMinutes = (try? c.decodeIfPresent(Int.self, forKey: .bedtimeMinutes)) ?? nil
        adhan = get(.adhan, false)
        adhanSource = get(.adhanSource, AdhanSource.makkah)
        fajrAdhan = get(.fajrAdhan, false)
        fajrAdhanSource = get(.fajrAdhanSource, AdhanSource.makkah)
        before10 = get(.before10, false)
        before5 = get(.before5, false)
        near = get(.near, false)
        nearMinutes = get(.nearMinutes, 15)
        voice = (try? c.decodeIfPresent(VoiceLanguage.self, forKey: .voice)) ?? nil
        perPrayer = get(.perPrayer, [String: PrayerSounds]())
        salawat = get(.salawat, false)
        salawatMinutes = get(.salawatMinutes, 11 * 60)
        quran = get(.quran, false)
        quranMinutes = get(.quranMinutes, 20 * 60 + 30)
        reciter = get(.reciter, Reciter.alafasy)
        migrateEarlyReminder()
    }

    /// Moves an old single early reminder to the new reminders (once).
    public mutating func migrateEarlyReminder() {
        switch minutesBefore {
        case 0: return
        case 10: before10 = true
        case 5: before5 = true
        default: near = true; nearMinutes = Self.nearChoices.contains(minutesBefore) ? minutesBefore : 15
        }
        minutesBefore = 0
    }
}

public enum AlertKind: Int, Sendable {
    case atPrayer, before, friday, suhoor, iftar, fajrAlarm
    /// At the prayer time with the Adhan (instead of `atPrayer`).
    case adhan
    case before10, before5, near
    /// Friday Salli Ala Rasulillah, the daily Qur'an reminder, the bedtime verse, Friday Al-Kahf.
    case salawat, quran, sleep, kahf
}

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
        func add(_ k: AlertKind, _ e: PrayerEvent, _ at: Date, _ d: DaySchedule) { out.append(PlannedAlert(kind: k, event: e, at: at, date: d.date)) }
        for d in days {
            for e in PrayerEvent.prayers {
                guard let at = d[e] else { continue }
                let s = alerts.sounds(e)
                if alerts.adhanPlays(e) {
                    add(.adhan, e, at, d)
                } else if atPrayer.contains(e) {
                    add(.atPrayer, e, at, d)
                }
                if atPrayer.contains(e), alerts.minutesBefore > 0 { add(.before, e, at.addingTimeInterval(-Double(alerts.minutesBefore) * 60), d) }
                if alerts.before10, s.before10 { add(.before10, e, at.addingTimeInterval(-600), d) }
                if alerts.before5, s.before5 { add(.before5, e, at.addingTimeInterval(-300), d) }
                if alerts.near, s.near { add(.near, e, at.addingTimeInterval(-Double(alerts.nearMinutes) * 60), d) }
            }
            // Sunrise (the end of Fajr time): a plain notification when its bell is on.
            if atPrayer.contains(.sunrise), let s = d[.sunrise] { add(.atPrayer, .sunrise, s, d) }
            if alerts.friday, d.date.isFriday, let dhuhr = d[.dhuhr] {
                add(.friday, .dhuhr, dhuhr.addingTimeInterval(-Double(AlertSettings.fridayLeadMinutes) * 60), d)
            }
            if alerts.ramadan, isRamadan(d.date) {
                if let f = d[.fajr] { add(.suhoor, .fajr, f.addingTimeInterval(-Double(AlertSettings.suhoorLeadMinutes) * 60), d) }
                if let m = d[.maghrib] { add(.iftar, .maghrib, m, d) }
            }
            if let m = alerts.fajrAlarmMinutesBefore, let f = d[.fajr] {
                add(.fajrAlarm, .fajr, f.addingTimeInterval(-Double(m) * 60), d)
            }
            if alerts.salawat, d.date.isFriday { add(.salawat, .dhuhr, d.date.at(minutes: alerts.salawatMinutes, in: d.zone), d) }
            if alerts.kahf, d.date.isFriday { add(.kahf, .dhuhr, d.date.at(minutes: AlertSettings.kahfHour * 60, in: d.zone), d) }
            if alerts.quran { add(.quran, .isha, d.date.at(minutes: alerts.quranMinutes, in: d.zone), d) }
            if let m = alerts.bedtimeMinutes { add(.sleep, .isha, d.date.at(minutes: m, in: d.zone), d) }
        }
        return out.filter { $0.at > now }.sorted { ($0.at, $0.kind.rawValue) < ($1.at, $1.kind.rawValue) }
    }
}

extension CivilDate {
    /// This date at `minutes` after midnight in `zone`.
    public func at(minutes: Int, in zone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let start = cal.date(from: components) ?? noonUTC
        return cal.date(byAdding: .minute, value: minutes, to: start) ?? start
    }

    /// Friday in the proleptic Gregorian calendar.
    public var isFriday: Bool {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c.component(.weekday, from: noonUTC) == 6
    }
}
