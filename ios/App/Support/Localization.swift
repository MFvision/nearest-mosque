import NMCore
import SwiftUI

/// Interface language: the device's ordered preference (which already reflects the per-app choice in
/// iOS Settings) or a persistent in-app override. Strings come from the compiled String Catalog of
/// the chosen language, so switching works offline and immediately.
@Observable
final class Localization {
    static let supported = Languages.all.map(\.code)
    static let nativeNames = Dictionary(uniqueKeysWithValues: Languages.all.map { ($0.code, $0.name) })
    private static let key = "languageOverride"

    /// nil = use device language.
    var override: String? {
        didSet {
            UserDefaults.standard.set(override, forKey: Self.key)
            reload()
        }
    }
    private(set) var language: String = "en"
    private(set) var bundle: Bundle = .main

    init() {
        override = UserDefaults.standard.string(forKey: Self.key)
        reload()
    }

    private func reload() {
        let device = Bundle.main.preferredLocalizations.first.flatMap(Languages.code(identifier:))
        let lang = override.flatMap { Self.supported.contains($0) ? $0 : nil } ?? device ?? Self.bestMatch() ?? "en"
        language = lang
        bundle = Bundle.main.path(forResource: Languages.lproj(lang), ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
    }

    /// First supported language in the user's ordered preferences.
    private static func bestMatch() -> String? {
        Locale.preferredLanguages.lazy.compactMap(Languages.code(identifier:)).first
    }

    var locale: Locale { Locale(identifier: Languages.tag(language)) }
    var isRTL: Bool { Languages.isRTL(language) }
    var layoutDirection: LayoutDirection { isRTL ? .rightToLeft : .leftToRight }

    func t(_ key: String) -> String { bundle.localizedString(forKey: key, value: nil, table: nil) }

    func t(_ key: String, _ args: CVarArg...) -> String {
        String(format: t(key), locale: locale, arguments: args)
    }

    /// Plural-aware lookup (String Catalog variations) with localized digits.
    func plural(_ key: String, _ count: Int) -> String {
        let format = bundle.localizedString(forKey: key, value: nil, table: nil)
        // The interface locale selects the plural category (Arabic has six).
        let s = String(format: format, locale: locale, count)
        return s.replacingOccurrences(of: String(count), with: count.formatted(.number.locale(locale)))
    }
}
