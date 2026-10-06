import Foundation
import NMCore
import Security

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

    var location: LatLng? { LatLng(latitude, longitude) }
    var zone: TimeZone { TimeZone(identifier: zoneId) ?? .current }
    var locale: Locale { Locale(identifier: language) }
    var isRTL: Bool { language == "ar" || language == "ur" }

    /// Today's, yesterday's and tomorrow's schedules around `now`.
    func days(_ now: Date, calculator: PrayerCalculator = PrayerCalculator()) -> [DaySchedule] {
        guard let loc = location else { return [] }
        let today = CivilDate.of(now, in: zone)
        return (-1...1).map { calculator.schedule(loc, date: today.adding(days: $0), zone: zone, settings: prayer) }
    }

    /// A string from the app's String Catalog in the chosen language.
    func t(_ key: String, _ args: CVarArg...) -> String {
        let bundle = Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        let format = bundle.localizedString(forKey: key, value: nil, table: nil)
        return args.isEmpty ? format : String(format: format, locale: locale, arguments: args)
    }

    func time(_ d: Date) -> String {
        d.formatted(Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: Calendar(identifier: .gregorian), timeZone: zone))
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
