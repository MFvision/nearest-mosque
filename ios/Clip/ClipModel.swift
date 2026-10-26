import CoreLocation
import NMCore

/// One location fix, then the mosques around it from Apple Maps (only a rounded centre is sent; distances are measured
/// here from the exact point) and the next prayer, calculated on the phone with the method usual in the phone's region.
@MainActor @Observable
final class ClipModel: NSObject, CLLocationManagerDelegate {
    enum Phase: Equatable { case locating, searching, ready, empty, denied, failed }

    static let searchRadius = 10_000.0
    static let listRadius = 25_000.0

    private(set) var phase: Phase = .locating
    private(set) var here: LatLng?
    private(set) var items: [RankedMosque] = []
    private(set) var next: Upcoming?
    private(set) var today: DaySchedule?
    let zone = TimeZone.current

    private let manager = CLLocationManager()
    private var wantsFix = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func start() {
        phase = .locating
        wantsFix = true
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .denied, .restricted: phase = .denied
        default: manager.requestLocation()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let status = m.authorizationStatus
        Task { @MainActor in
            guard self.wantsFix else { return }
            switch status {
            case .authorizedWhenInUse, .authorizedAlways: self.manager.requestLocation()
            case .denied, .restricted: self.phase = .denied
            default: break
            }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let c = locations.last?.coordinate else { return }
        Task { @MainActor in await self.found(c) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            guard self.wantsFix else { return }
            self.wantsFix = false
            self.phase = self.manager.authorizationStatus == .denied ? .denied : .failed
        }
    }

    private func found(_ c: CLLocationCoordinate2D) async {
        guard wantsFix, let p = LatLng(c.latitude, c.longitude) else { return }
        wantsFix = false
        here = p
        phase = .searching
        let settings = PrayerSettings(method: PrayerCalculator.suggestedMethod(Locale.current.region?.identifier))
        let calc = PrayerCalculator()
        today = calc.schedule(p, date: CivilDate.of(.now, in: zone), zone: zone, settings: settings)
        next = calc.nextPrayer(p, now: .now, zone: zone, settings: settings)
        guard let live = await AppleMosqueSearch.search(center: p, radiusMeters: Self.searchRadius) else {
            phase = .failed
            return
        }
        items = MosqueRanking.rank(center: p, candidates: live, radiusMeters: Self.listRadius, limit: 15)
        phase = items.isEmpty ? .empty : .ready
    }

    /// The prayer whose time it is now (nil before Fajr), for the sky behind the list.
    func current(at now: Date) -> PrayerEvent? {
        guard let today else { return nil }
        return PrayerEvent.allCases.last { e in today[e].map { $0 <= now } ?? false }
    }
}
