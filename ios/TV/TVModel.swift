import CoreLocation
import Foundation
import NMCore
import NMData
import SwiftUI
import TVServices

/// Where prayer times are calculated on this Apple TV: a chosen city or the TV's own location.
struct TVLocation: Codable, Equatable {
    var name: String
    var latitude: Double
    var longitude: Double
    var zoneId: String
    var countryCode: String?

    var location: LatLng { LatLng(latitude, longitude) ?? Qibla.kaaba }
    var zone: TimeZone { TimeZone(identifier: zoneId) ?? .current }
}

/// Settings saved on this TV (UserDefaults). Missing keys decode to defaults so older saves keep working.
struct TVSettings: Codable, Equatable {
    var location: TVLocation?
    var prayer = PrayerSettings()
    var methodChosenByUser = false
    /// The screen saver waits while the prayer board is open.
    var keepAwake = true
    /// A full-screen "time for Dhuhr" while the app is open.
    var prayerAlert = true
    /// Live results from Apple Maps added to the built-in mosque data (approximate location only).
    var onlineSearch = true

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        location = try c.decodeIfPresent(TVLocation.self, forKey: .location)
        prayer = (try? c.decodeIfPresent(PrayerSettings.self, forKey: .prayer)) ?? PrayerSettings()
        methodChosenByUser = try c.decodeIfPresent(Bool.self, forKey: .methodChosenByUser) ?? false
        keepAwake = try c.decodeIfPresent(Bool.self, forKey: .keepAwake) ?? true
        prayerAlert = try c.decodeIfPresent(Bool.self, forKey: .prayerAlert) ?? true
        onlineSearch = try c.decodeIfPresent(Bool.self, forKey: .onlineSearch) ?? true
    }
}

/// A prayer whose time has come, shown full screen while the app is open.
struct PrayerAlert: Equatable {
    let event: PrayerEvent
    let at: Date
}

/// The Apple TV app's state. Prayer times are calculated here; mosques come from the built-in data
/// (read into memory: tvOS keeps no lasting local storage) plus Apple Maps when online search is on.
@MainActor
@Observable
final class TVModel {
    static let shared = TVModel()

    let l10n = Localization()
    let calculator = PrayerCalculator()
    private(set) var cities: CityIndex?
    private(set) var mosques: MosqueRepository?
    private(set) var ready = false
    var alert: PrayerAlert?

    var settings: TVSettings {
        didSet {
            guard settings != oldValue else { return }
            if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "tvSettings") }
            shareWithTopShelf()
            scheduleAlert()
        }
    }

    init() {
        if let data = UserDefaults.standard.data(forKey: "tvSettings"), let s = try? JSONDecoder().decode(TVSettings.self, from: data) {
            settings = s
        } else {
            settings = TVSettings()
        }
    }

    /// Reads the city table and the built-in mosque packs off the main thread.
    func start() async {
        guard !ready else { return }
        let root = Bundle.main.resourceURL
        let opened: (CityIndex?, MosqueRepository?) = await Task.detached(priority: .userInitiated) {
            let tsv = root.flatMap { try? String(contentsOf: $0.appendingPathComponent("cities/cities.tsv"), encoding: .utf8) }
            var repo: MosqueRepository?
            if let db = try? AppDatabase.inMemory() {
                // The packs' "removed" list belongs to the phone app; nothing is removable here.
                PackManager(db: db, bundledRoot: root, defaults: UserDefaults(suiteName: "tv-packs") ?? .standard).ensureBuiltins()
                repo = MosqueRepository(db: db)
            }
            return (tsv.map(CityIndex.parse), repo)
        }.value
        cities = opened.0
        mosques = opened.1
        ready = true
        #if DEBUG
        if let name = UserDefaults.standard.string(forKey: "demoCity"), let city = cities?.search(name).first { choose(city: city) }
        #endif
        shareWithTopShelf()
        scheduleAlert()
    }

    // MARK: Location

    func choose(city: City) {
        settings.location = TVLocation(name: city.displayName(l10n.language), latitude: city.location.latitude, longitude: city.location.longitude,
                                       zoneId: city.zoneId, countryCode: city.countryCode)
        if !settings.methodChosenByUser { settings.prayer.method = PrayerCalculator.suggestedMethod(city.countryCode) }
    }

    enum LocateOutcome { case ok, denied, noFix }

    /// The TV's location (from its network), named after the nearest known city; never a default city.
    func useTVLocation() async -> LocateOutcome {
        let finder = TVLocationFinder()
        guard let fix = await finder.fix() else { return finder.denied ? .denied : .noFix }
        let z = cities.map(TimeZoneResolver.init)?.resolve(fix, deviceZone: .current)
        let near = (z?.distanceMeters ?? .infinity) < 30_000 ? z?.nearestCity : nil
        settings.location = TVLocation(name: near?.displayName(l10n.language) ?? l10n.t("location_current"),
                                       latitude: fix.latitude, longitude: fix.longitude,
                                       zoneId: (z?.zone ?? .current).identifier, countryCode: z?.nearestCity?.countryCode)
        if !settings.methodChosenByUser { settings.prayer.method = PrayerCalculator.suggestedMethod(z?.nearestCity?.countryCode) }
        return .ok
    }

    func setMethod(_ m: PrayerMethod) {
        settings.prayer.method = m
        settings.methodChosenByUser = true
    }

    // MARK: Schedule

    @ObservationIgnored private var cacheKey: String?
    @ObservationIgnored private var cached: [DaySchedule] = []

    /// Yesterday, today and tomorrow where the TV's city is (cached per day and settings).
    func days(now: Date) -> [DaySchedule] {
        guard let loc = settings.location else { return [] }
        let today = CivilDate.of(now, in: loc.zone)
        let key = "\(loc.latitude),\(loc.longitude),\(loc.zoneId),\(today),\(String(describing: settings.prayer))"
        if key != cacheKey {
            cached = (-1...1).map { calculator.schedule(loc.location, date: today.adding(days: $0), zone: loc.zone, settings: settings.prayer) }
            cacheKey = key
        }
        return cached
    }

    // MARK: Prayer alert

    @ObservationIgnored private var alertTask: Task<Void, Never>?
    /// How long the alert stays up unless closed.
    static let alertSeconds: TimeInterval = 5 * 60

    /// Waits for the next prayer and raises the alert at its time (again after each prayer). Called on
    /// launch, on every settings change and when the app comes back to the screen.
    func scheduleAlert() {
        alertTask?.cancel()
        guard settings.prayerAlert, settings.location != nil else { alert = nil; return }
        alertTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, let next = self.calculator.nextPrayer(self.days(now: Date()), now: Date()) else { return }
                try? await Task.sleep(for: .seconds(max(1, next.at.timeIntervalSinceNow)))
                guard !Task.isCancelled else { return }
                // Skipped when the TV slept through it.
                if Date().timeIntervalSince(next.at) < Self.alertSeconds {
                    self.alert = PrayerAlert(event: next.event, at: next.at)
                    // The home row's banner lights the next prayer.
                    TVTopShelfContentProvider.topShelfContentDidChange()
                    let shown = self.alert
                    Task { @MainActor [weak self] in
                        try? await Task.sleep(for: .seconds(Self.alertSeconds))
                        if self?.alert == shown { self?.alert = nil }
                    }
                }
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }

    // MARK: Top Shelf

    /// Gives the Top Shelf extension the city, method and language (shared keychain), never sent anywhere.
    func shareWithTopShelf() {
        let s = sharedState
        guard s != lastShared else { return }
        lastShared = s
        SharedStore.write(s)
        TVTopShelfContentProvider.topShelfContentDidChange()
    }

    @ObservationIgnored private var lastShared: SharedState?

    var sharedState: SharedState? {
        settings.location.map {
            SharedState(name: $0.name, latitude: $0.latitude, longitude: $0.longitude, zoneId: $0.zoneId, prayer: settings.prayer,
                        language: l10n.language, hijriAdjustmentDays: settings.prayer.hijriAdjustmentDays)
        }
    }
}

/// One location fix for "use this TV's location" (Apple TV locates itself from its network).
@MainActor
final class TVLocationFinder: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var authCont: CheckedContinuation<Void, Never>?
    private var fixCont: CheckedContinuation<CLLocation?, Never>?
    private(set) var denied = false

    func fix() async -> LatLng? {
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        if manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { c in
                authCont = c
                manager.requestWhenInUseAuthorization()
            }
        }
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { denied = status == .denied || status == .restricted; return nil }
        let l: CLLocation? = await withCheckedContinuation { c in
            fixCont = c
            manager.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(15))
                self.finish(nil)
            }
        }
        return (l ?? manager.location).flatMap { LatLng($0.coordinate.latitude, $0.coordinate.longitude) }
    }

    private func finish(_ l: CLLocation?) {
        fixCont?.resume(returning: l)
        fixCont = nil
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let status = m.authorizationStatus
        Task { @MainActor in
            guard status != .notDetermined else { return }
            self.authCont?.resume()
            self.authCont = nil
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in self.finish(l) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }
}
