import Foundation
import NMCore
import Security

/// One of the nearest mosques for the widget: distance and direction from where the app last looked.
struct SharedMosque: Codable, Equatable {
    var id: String
    var name: String
    var meters: Double
    /// Degrees from north.
    var bearing: Double
}

/// What the widgets (and Siri) need from the app: where prayer times are calculated, how, and in which
/// language. Shared through a keychain access group (no App Group needed), never sent anywhere.
struct SharedState: Codable, Equatable {
    var name: String
    var latitude: Double
    var longitude: Double
    var zoneId: String
    var prayer: PrayerSettings
    var language: String
    var hijriAdjustmentDays: Int
    /// Prayers whose reminder is on (PrayerEvent raw values); the widgets show a bell for them.
    var reminders: [String]?
    /// The nearest mosques (up to three) and suggested questions in the reader's language.
    var mosques: [SharedMosque]?
    var questions: [String]?

    var location: LatLng? { LatLng(latitude, longitude) }
    var zone: TimeZone { TimeZone(identifier: zoneId) ?? .current }
    var locale: Locale { Locale(identifier: Languages.tag(language)) }
    var isRTL: Bool { Languages.isRTL(language) }

    func reminderOn(_ e: PrayerEvent) -> Bool { reminders?.contains(e.rawValue) ?? false }

    /// "600 m", "2.4 km" in the reader's locale.
    func distance(_ meters: Double) -> String {
        let style = Measurement<UnitLength>.FormatStyle(width: .abbreviated, locale: locale, usage: .asProvided,
                                                        numberFormatStyle: .number.precision(.fractionLength(0...1)))
        return meters < 1000 ? Measurement(value: (meters / 10).rounded() * 10, unit: UnitLength.meters).formatted(style)
            : Measurement(value: meters / 1000, unit: UnitLength.kilometers).formatted(style)
    }

    /// Two suggested questions for today (they change every day).
    func todaysQuestions(_ now: Date) -> [String] {
        guard let q = questions, !q.isEmpty else { return [] }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let day = cal.ordinality(of: .day, in: .era, for: now) ?? 0
        return [q[(2 * day) % q.count], q[(2 * day + 1) % q.count]].reduce(into: []) { if !$0.contains($1) { $0.append($1) } }
    }

    /// Today's, yesterday's and tomorrow's schedules around `now`.
    func days(_ now: Date, calculator: PrayerCalculator = PrayerCalculator()) -> [DaySchedule] {
        guard let loc = location else { return [] }
        let today = CivilDate.of(now, in: zone)
        return (-1...1).map { calculator.schedule(loc, date: today.adding(days: $0), zone: zone, settings: prayer) }
    }

    /// A string from the app's String Catalog in the chosen language.
    func t(_ key: String, _ args: CVarArg...) -> String {
        let bundle = Bundle.main.path(forResource: Languages.lproj(language), ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        let format = bundle.localizedString(forKey: key, value: nil, table: nil)
        return args.isEmpty ? format : String(format: format, locale: locale, arguments: args)
    }

    func time(_ d: Date) -> String {
        d.formatted(Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: Calendar(identifier: .gregorian), timeZone: zone))
    }

    /// The time and its AM/PM mark apart (tiles show the mark on its own line); no mark in 24-hour locales.
    func timeParts(_ d: Date) -> (time: String, mark: String?) {
        let f = DateFormatter()
        f.locale = locale
        f.timeZone = zone
        f.calendar = Calendar(identifier: .gregorian)
        f.setLocalizedDateFormatFromTemplate("jmm")
        let full = f.string(from: d)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let mark = cal.component(.hour, from: d) < 12 ? f.amSymbol ?? "" : f.pmSymbol ?? ""
        guard !mark.isEmpty, full.contains(mark) else { return (full, nil) }
        return (full.replacingOccurrences(of: mark, with: "").trimmingCharacters(in: .whitespaces), mark)
    }

    func weekday(_ d: Date) -> String {
        d.formatted(Date.FormatStyle(locale: locale, calendar: Calendar(identifier: .gregorian), timeZone: zone).weekday(.wide))
    }

    /// Gregorian date, numeric in the reader's locale.
    func gregorian(_ d: Date) -> String {
        d.formatted(Date.FormatStyle(date: .numeric, time: .omitted, locale: locale, calendar: Calendar(identifier: .gregorian), timeZone: zone))
    }

    /// Day and month, short ("6 Oct").
    func dayMonth(_ d: Date) -> String {
        d.formatted(Date.FormatStyle(locale: locale, calendar: Calendar(identifier: .gregorian), timeZone: zone).day().month(.abbreviated))
    }

    /// Hijri date (Umm al-Qura, with the app's adjustment), numeric.
    func hijriNumeric(_ now: Date) -> String {
        CivilDate.of(now, in: zone).adding(days: hijriAdjustmentDays).noonUTC.formatted(
            Date.FormatStyle(date: .numeric, time: .omitted, locale: locale, calendar: Calendar(identifier: .islamicUmmAlQura), timeZone: TimeZone(identifier: "UTC")!))
    }

    func hijri(_ now: Date) -> String {
        CivilDate.of(now, in: zone).adding(days: hijriAdjustmentDays).noonUTC.formatted(
            Date.FormatStyle(date: .long, time: .omitted, locale: locale, calendar: Calendar(identifier: .islamicUmmAlQura), timeZone: TimeZone(identifier: "UTC")!))
    }
}

/// Reads and writes `SharedState` in the shared keychain group named in Info.plist (SharedKeychainGroup).
enum SharedStore {
    private static let account = "shared-state"
    private static var group: String? { Bundle.main.object(forInfoDictionaryKey: "SharedKeychainGroup") as? String }

    private static func query() -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "sa.zood.nearmosque.shared",
                                kSecAttrAccount as String: account]
        if let group, !group.hasPrefix("$(") { q[kSecAttrAccessGroup as String] = group }
        return q
    }

    static func read() -> SharedState? {
        var q = query()
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return try? JSONDecoder().decode(SharedState.self, from: data)
    }

    @discardableResult
    static func write(_ state: SharedState?) -> Bool {
        let q = query()
        guard let state, let data = try? JSONEncoder().encode(state) else { return SecItemDelete(q as CFDictionary) == errSecSuccess }
        let attrs: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) == errSecSuccess { return true }
        return SecItemAdd(q.merging(attrs) { $1 } as CFDictionary, nil) == errSecSuccess
    }
}
