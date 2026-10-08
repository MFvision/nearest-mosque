import MapKit
import NMCore

extension LatLng {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}

/// Live mosque results from Apple Maps (online, no API key). Only a rounded centre is used for the
/// search region; results are filtered to places whose name says mosque/masjid in common languages.
enum AppleMosqueSearch {
    private static let queries = ["mosque", "masjid", "مسجد"]
    private static let keywords = ["mosque", "masjid", "masjed", "musalla", "musallah", "jamia", "jami", "islamic", "مسجد", "جامع", "مصلى",
                                   "cami", "mescit", "mosquée", "mosquee", "mezquita", "moschee", "moskee", "مسجد"]

    /// nil when every request failed (offline or service unavailable).
    static func search(center: LatLng, radiusMeters: Double) async -> [Mosque]? {
        let c = OnlineMosques.privacyRound(center)
        let span = (radiusMeters + Double(OnlineMosques.roundingSlackMeters)) * 2
        let region = MKCoordinateRegion(center: c.coordinate, latitudinalMeters: span, longitudinalMeters: span)
        return await withTaskGroup(of: [Mosque]?.self) { group in
            for q in queries {
                group.addTask {
                    let request = MKLocalSearch.Request()
                    request.naturalLanguageQuery = q
                    request.region = region
                    request.resultTypes = .pointOfInterest
                    guard let response = try? await MKLocalSearch(request: request).start() else { return nil }
                    return response.mapItems.compactMap(convert)
                }
            }
            var all: [Mosque] = []
            var succeeded = false
            for await items in group {
                if let items { succeeded = true; all += items }
            }
            return succeeded ? all : nil
        }
    }

    static func convert(_ item: MKMapItem) -> Mosque? {
        guard let name = item.name, matches(name) else { return nil }
        let coord = item.placemark.coordinate
        guard let loc = LatLng(coord.latitude, coord.longitude) else { return nil }
        let id = "apple:\(Int((loc.latitude * 1e5).rounded())),\(Int((loc.longitude * 1e5).rounded()))"
        return Mosque(sourceId: id, packId: OnlineMosques.applePackId, category: .mosque, names: ["default": name], location: loc,
                      address: item.placemark.title, phone: item.phoneNumber, website: item.url?.absoluteString,
                      openingHoursRaw: nil, sourceTimestamp: nil)
    }

    static func matches(_ name: String) -> Bool {
        let n = name.lowercased()
        return keywords.contains { n.contains($0) }
    }
}
