import AppIntents
import CoreLocation
import NMCore
import NMData
import SwiftUI
import WidgetKit

// Shortcuts for Siri, the Shortcuts app and the Action button. Nearest mosque and Next prayer answer
// without opening the app; Ask and Prayer times open it where asked.

/// A screen asked for by a shortcut; the app opens it on launch or right away if it is running.
@MainActor
enum PendingRoute {
    static var url: URL?

    static func open(_ url: URL) {
        self.url = url
        NotificationCenter.default.post(name: .openRoute, object: nil)
    }

    static func take() -> URL? {
        defer { url = nil }
        return url
    }
}

extension Notification.Name {
    static let openRoute = Notification.Name("NearMosque.openRoute")
}

struct OpenPrayerTimesIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_open_prayer_times"
    static var description = IntentDescription("intent_open_prayer_times_desc")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        PendingRoute.open(URL(string: "nearmosque://prayer")!)
        return .result()
    }
}

/// Siri asks for the question by voice (or the Shortcuts app shows a field), then the app opens on Ask
/// with it, answered on the device from cited sources.
struct AskIslamIntent: AppIntent {
    static var title: LocalizedStringResource = "widget_kind_ask"
    static var description = IntentDescription("intent_ask_desc")
    static var openAppWhenRun = true

    @Parameter(title: "intent_question", requestValueDialog: IntentDialog("intent_ask_prompt"))
    var question: String

    @MainActor
    func perform() async throws -> some IntentResult {
        var c = URLComponents(string: "nearmosque://ask")!
        c.queryItems = [URLQueryItem(name: "q", value: question)]
        PendingRoute.open(c.url!)
        return .result()
    }
}

/// "Which mosque is nearest?": answered without opening the app, from a fresh location fix when Core
/// Location gives one within a few seconds, else from where the app last looked.
struct NearestMosqueIntent: AppIntent {
    static var title: LocalizedStringResource = "widget_kind_mosque"
    static var description = IntentDescription("intent_nearest_desc")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> & ShowsSnippetView {
        guard var s = SharedStore.read() else {
            let text = SharedState.fallbackNoCity
            return .result(value: text, dialog: IntentDialog(stringLiteral: text),
                           view: IntentSnippet(entry: PrayerEntry.make(Date(), look: .green, state: nil), kind: .mosque, show: false))
        }
        if let fresh = await NearbyMosques.lookup(lang: s.language, unnamed: s.t("mosque_unnamed")) {
            s.mosques = fresh.map(\.shared)
        }
        let text: String
        if let m = s.mosques?.first {
            text = s.t("siri_nearest_mosque", m.name, s.distance(m.meters))
        } else {
            text = s.t(s.mosques == nil ? "siri_nearest_open_app" : "siri_nearest_none")
        }
        let entry = PrayerEntry.make(Date(), look: .green, state: s)
        return .result(value: text, dialog: IntentDialog(stringLiteral: text),
                       view: IntentSnippet(entry: entry, kind: .mosque, show: s.mosques?.isEmpty == false))
    }
}

/// Walking directions to the nearest mosque in Apple Maps, without going through the app.
struct NearestMosqueDirectionsIntent: AppIntent {
    static var title: LocalizedStringResource = "intent_directions"
    static var description = IntentDescription("intent_directions_desc")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & OpensIntent {
        guard let s = SharedStore.read() else {
            return .result(opensIntent: OpenURLIntent(URL(string: "nearmosque://mosques")!), dialog: IntentDialog(stringLiteral: SharedState.fallbackNoCity))
        }
        if let found = await NearbyMosques.lookup(lang: s.language, unnamed: s.t("mosque_unnamed")) {
            guard let m = found.first else {
                let text = s.t("siri_nearest_none")
                return .result(opensIntent: OpenURLIntent(URL(string: "nearmosque://mosques")!), dialog: IntentDialog(stringLiteral: text))
            }
            var c = URLComponents(string: "https://maps.apple.com/")!
            c.queryItems = [URLQueryItem(name: "daddr", value: "\(m.location.latitude),\(m.location.longitude)"),
                            URLQueryItem(name: "dirflg", value: "w"), URLQueryItem(name: "q", value: m.shared.name)]
            let text = s.t("siri_nearest_mosque", m.shared.name, s.distance(m.shared.meters))
            return .result(opensIntent: OpenURLIntent(c.url!), dialog: IntentDialog(stringLiteral: text))
        }
        // No fresh fix: open the app on the nearest mosque it knows.
        var c = URLComponents(string: "nearmosque://mosque")!
        c.queryItems = s.mosques?.first.map { [URLQueryItem(name: "id", value: $0.id)] }
        return .result(opensIntent: OpenURLIntent(c.url!), dialog: IntentDialog(stringLiteral: s.t("siri_nearest_open_app")))
    }
}

/// The widget's own look, shown under Siri's answer.
struct IntentSnippet: View {
    let entry: PrayerEntry
    let kind: PrayerWidgetKind
    var show = true

    var body: some View {
        if show {
            let p = entry.palette
            PrayerWidgetView(entry: entry, kind: kind, family: .systemMedium)
                .padding(16)
                .frame(maxWidth: .infinity, minHeight: 150)
                .background(p.background)
                .environment(\.layoutDirection, entry.state?.isRTL == true ? .rightToLeft : .leftToRight)
        }
    }
}

/// The nearest mosques from a fresh location fix and the installed mosque packs, read on the device.
@MainActor
enum NearbyMosques {
    struct Found {
        let shared: SharedMosque
        let location: LatLng
    }

    /// nil when there is no recent fix or no mosque data on this phone (the app was never opened);
    /// empty when the data has no mosque within 25 km.
    static func lookup(lang: String, unnamed: String) async -> [Found]? {
        guard let center = await OneShotLocation().fix(timeout: 6) else { return nil }
        let result: MosqueResult? = try? await Task.detached {
            try MosqueRepository(db: AppDatabase.onDisk()).nearest(center, radiusMeters: 25_000, lang: lang)
        }.value
        switch result {
        case let .found(list, _):
            return list.prefix(3).map { r in
                Found(shared: SharedMosque(id: r.id, name: r.mosque.displayName(lang) ?? unnamed, meters: r.distanceMeters,
                                           bearing: Geo.initialBearing(from: center, to: r.mosque.location)),
                      location: r.mosque.location)
            }
        case .noRecordsInCoverage: return []
        default: return nil
        }
    }
}

/// One location fix (when-in-use permission already given), or the last fix if under 15 minutes old.
@MainActor
final class OneShotLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var cont: CheckedContinuation<CLLocation?, Never>?

    func fix(timeout: Double) async -> LatLng? {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return nil }
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        let fresh: CLLocation? = await withCheckedContinuation { c in
            cont = c
            manager.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(timeout))
                self.finish(nil)
            }
        }
        let recent = manager.location.flatMap { Date().timeIntervalSince($0.timestamp) < 900 ? $0 : nil }
        return (fresh ?? recent).flatMap { LatLng($0.coordinate.latitude, $0.coordinate.longitude) }
    }

    private func finish(_ l: CLLocation?) {
        cont?.resume(returning: l)
        cont = nil
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in self.finish(l) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }
}
