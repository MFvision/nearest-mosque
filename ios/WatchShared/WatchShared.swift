import AppIntents
import Foundation
import NMCore
import Security
import SwiftUI

/// What the watch app gives its complications besides SharedState: the tasbih count and how full the companion is.
/// Kept in the watch's shared keychain group (SharedKeychainGroup in Info.plist); it never leaves the watch.
struct WatchExtras: Codable, Equatable {
    var tasbih = 0
    var companionLevel = 0.5

    private static let account = "watch-extras"
    private static var group: String? { Bundle.main.object(forInfoDictionaryKey: "SharedKeychainGroup") as? String }

    private static func query() -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "sa.zood.nearmosque.shared",
                                kSecAttrAccount as String: account]
        // Simulator builds in CI are unsigned, so they have no access group: the item stays the app's own there.
        #if !targetEnvironment(simulator)
        if let group, !group.hasPrefix("$(") { q[kSecAttrAccessGroup as String] = group }
        #endif
        return q
    }

    static func read() -> WatchExtras {
        #if targetEnvironment(simulator)
        // Unsigned simulator builds (CI) have no keychain.
        return UserDefaults.standard.data(forKey: "sim." + account).flatMap { try? JSONDecoder().decode(WatchExtras.self, from: $0) } ?? WatchExtras()
        #endif
        var q = query()
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data,
              let x = try? JSONDecoder().decode(WatchExtras.self, from: data) else { return WatchExtras() }
        return x
    }

    static func write(_ x: WatchExtras) {
        guard let data = try? JSONEncoder().encode(x) else { return }
        #if targetEnvironment(simulator)
        UserDefaults.standard.set(data, forKey: "sim." + account)
        return
        #endif
        let q = query()
        let attrs: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) != errSecSuccess {
            SecItemAdd(q.merging(attrs) { $1 } as CFDictionary, nil)
        }
    }
}

/// The watch app's screens, for complications, controls and links (nearmosque://qibla and so on).
enum WatchScreen: String, AppEnum {
    case prayer, qibla, spirit, tasbih, mosques, ask

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "widget_kind_next"
    static var caseDisplayRepresentations: [WatchScreen: DisplayRepresentation] = [
        .prayer: "widget_kind_next", .qibla: "widget_kind_qibla", .spirit: "spirit_title", .tasbih: "widget_kind_tasbih", .mosques: "widget_kind_mosque",
        .ask: "widget_kind_ask",
    ]

    var url: URL { URL(string: "nearmosque://\(rawValue)")! }

    init?(url: URL) {
        guard url.scheme == "nearmosque", let host = url.host else { return nil }
        self.init(rawValue: host == "mosque" ? "mosques" : host)
    }
}

extension Notification.Name {
    /// Posted in the watch app when a control asks for a screen.
    static let openWatchScreen = Notification.Name("NearMosque.openWatchScreen")
}

/// Opens the watch app on a screen (the watch's Control Center buttons). Runs in the app.
struct OpenWatchScreenIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_show_qibla"
    static var openAppWhenRun = true
    static var isDiscoverable = false

    @Parameter(title: "widget_kind_next")
    var screen: WatchScreen

    init() { screen = .prayer }
    init(_ screen: WatchScreen) { self.screen = screen }

    @MainActor
    func perform() async throws -> some IntentResult {
        UserDefaults.standard.set(screen.rawValue, forKey: "openWatchScreen")
        NotificationCenter.default.post(name: .openWatchScreen, object: screen.rawValue)
        return .result()
    }
}

/// Colours of the style picked on the iPhone (Settings → style), and the dark sky of each prayer period.
enum WatchColors {
    static func accent(_ state: SharedState?) -> Color { StyleAccent.dark(state?.widgetLook ?? "cream") }

    static func hex(_ v: UInt32) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }

    /// Top and bottom of the sky for the period in effect (shared/design/tokens.json "sky").
    static func sky(_ period: PrayerEvent?, sinceSunrise: TimeInterval? = nil) -> [Color] {
        let k: [UInt32]
        switch period {
        case .fajr: k = [0x141E3C, 0x3A3566]
        case .sunrise where (sinceSunrise ?? .infinity) < 3600: k = [0x1C3F7A, 0x7A6A7A]
        case .sunrise, .dhuhr: k = [0x1C3F7A, 0x2F5590]
        case .asr: k = [0x173F5A, 0x23606F]
        case .maghrib: k = [0x1B1F45, 0x4A2F5E]
        case .isha: k = [0x0A1430, 0x16264D]
        case nil: k = [0x081B29, 0x12304A]
        }
        return k.map(hex)
    }
}

extension PrayerEvent {
    /// SF Symbol for the time of day (same as on the iPhone).
    var watchSymbol: String {
        switch self {
        case .fajr: return "sun.horizon"
        case .sunrise: return "sunrise"
        case .dhuhr: return "sun.max"
        case .asr: return "sun.min"
        case .maghrib: return "sunset"
        case .isha: return "moon.stars"
        }
    }
}

/// A small Kaaba: a dark cube with its gold band.
struct WatchKaaba: View {
    var body: some View {
        Canvas { ctx, s in
            let w = s.width, h = s.height
            var front = Path()
            front.addRect(CGRect(x: w * 0.12, y: h * 0.3, width: w * 0.5, height: h * 0.58))
            var side = Path()
            side.move(to: CGPoint(x: w * 0.62, y: h * 0.3))
            side.addLine(to: CGPoint(x: w * 0.88, y: h * 0.18))
            side.addLine(to: CGPoint(x: w * 0.88, y: h * 0.74))
            side.addLine(to: CGPoint(x: w * 0.62, y: h * 0.88))
            side.closeSubpath()
            var top = Path()
            top.move(to: CGPoint(x: w * 0.12, y: h * 0.3))
            top.addLine(to: CGPoint(x: w * 0.38, y: h * 0.18))
            top.addLine(to: CGPoint(x: w * 0.88, y: h * 0.18))
            top.addLine(to: CGPoint(x: w * 0.62, y: h * 0.3))
            top.closeSubpath()
            ctx.fill(front, with: .color(Color(white: 0.07)))
            ctx.fill(side, with: .color(Color(white: 0.15)))
            ctx.fill(top, with: .color(Color(white: 0.23)))
            ctx.fill(Path(CGRect(x: w * 0.12, y: h * 0.4, width: w * 0.5, height: h * 0.07)), with: .color(Color(red: 0.85, green: 0.66, blue: 0.26)))
            var band = Path()
            band.move(to: CGPoint(x: w * 0.62, y: h * 0.4))
            band.addLine(to: CGPoint(x: w * 0.88, y: h * 0.28))
            band.addLine(to: CGPoint(x: w * 0.88, y: h * 0.35))
            band.addLine(to: CGPoint(x: w * 0.62, y: h * 0.47))
            band.closeSubpath()
            ctx.fill(band, with: .color(Color(red: 0.66, green: 0.5, blue: 0.16)))
        }
        .accessibilityHidden(true)
    }
}

/// An angle in -180...180.
func foldDegrees(_ a: Double) -> Double {
    let x = a.truncatingRemainder(dividingBy: 360)
    return x > 180 ? x - 360 : (x < -180 ? x + 360 : x)
}
