import MapKit
import NMCore
import NMData
import SwiftUI

/// The nearest mosques from where the watch is (or the prayer city): built-in data plus Apple Maps when online search is
/// on (only a rounded centre is sent). The arrow on each row points to the mosque as the watch is held.
struct MosquesPage: View {
    @Environment(WatchModel.self) private var model
    @State private var compass = WatchCompass()
    @State private var items: [RankedMosque] = []
    @State private var center: LatLng?
    @State private var loading = false
    @State private var loadedKey: String?

    static let radius = 25_000.0

    var body: some View {
        let l10n = model.l10n
        Group {
            if let s = model.state {
                if !items.isEmpty, let center {
                    List {
                        ForEach(items.prefix(12)) { r in
                            NavigationLink(value: r) {
                                row(s, r, from: center)
                            }
                        }
                    }
                    .navigationDestination(for: RankedMosque.self) { r in MosqueDetail(mosque: r, state: s, accent: model.accent) }
                } else if loading {
                    ProgressView()
                } else if let shared = s.mosques, !shared.isEmpty {
                    // Nothing found from here: the ones the iPhone found where it last looked.
                    List {
                        ForEach(shared, id: \.id) { m in
                            HStack {
                                Image(systemName: "location.north.fill").rotationEffect(.degrees(m.bearing)).foregroundStyle(model.accent)
                                VStack(alignment: .leading) {
                                    Text(m.name).lineLimit(2)
                                    Text(s.distance(m.meters)).font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                        }
                        Text(l10n.t("watch_mosques_from_iphone")).font(.footnote).foregroundStyle(.secondary).listRowBackground(Color.clear)
                    }
                } else {
                    ScrollView {
                        VStack(spacing: 8) {
                            Image(systemName: "building.columns").font(.title2).foregroundStyle(model.accent)
                            Text(l10n.t("no_records_title")).font(.footnote).multilineTextAlignment(.center)
                            Button(l10n.t("retry")) { loadedKey = nil; Task { await load() } }
                        }
                    }
                }
            } else {
                WaitingView()
            }
        }
        .watchSky()
        .navigationTitle(l10n.t("tab_mosques"))
        .task(id: "\(model.state?.latitude ?? 0),\(model.state?.longitude ?? 0),\(model.settings.onlineSearch)") { await load() }
        .onAppear { compass.start() }
        .onDisappear { compass.stop() }
    }

    private func row(_ s: SharedState, _ r: RankedMosque, from c: LatLng) -> some View {
        let bearing = Geo.initialBearing(from: c, to: r.mosque.location)
        let relative = compass.heading.map { bearing - $0 } ?? bearing
        return HStack(spacing: 8) {
            Image(systemName: "location.north.fill").rotationEffect(.degrees(relative)).foregroundStyle(model.accent)
                .animation(.easeOut(duration: 0.25), value: relative)
            VStack(alignment: .leading, spacing: 1) {
                Text(r.mosque.displayName(model.l10n.language) ?? model.l10n.t("mosque_unnamed")).lineLimit(2)
                Text(s.distance(r.distanceMeters)).font(.footnote.monospacedDigit()).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func load() async {
        guard let s = model.state, let fallback = s.location else { return }
        loading = true
        // The watch's own position when it can get one, else the prayer city.
        let here = await WatchLocator.shared.fix() ?? fallback
        let key = "\((here.latitude * 1000).rounded())/\((here.longitude * 1000).rounded())/\(model.settings.onlineSearch)"
        guard key != loadedKey else { loading = false; return }
        let repo = model.mosques
        let lang = model.l10n.language
        var offline: [RankedMosque] = []
        let radius = Self.radius
        if let repo {
            let result = try? await Task.detached { try repo.nearest(here, radiusMeters: radius, lang: lang) }.value
            if case let .some(.found(list, _)) = result { offline = list }
        }
        var merged = offline
        if model.settings.onlineSearch, let live = await AppleMosqueSearch.search(center: here, radiusMeters: 10_000) {
            merged = OnlineMosques.merge(center: here, offline: offline, online: live, radiusMeters: Self.radius)
        }
        items = merged
        center = here
        loadedKey = key
        loading = false
    }
}

/// One mosque: a small map, the distance, and walking directions in Maps.
struct MosqueDetail: View {
    let mosque: RankedMosque
    let state: SharedState
    let accent: Color

    var body: some View {
        let name = mosque.mosque.displayName(state.language) ?? state.t("mosque_unnamed")
        let coord = CLLocationCoordinate2D(latitude: mosque.mosque.location.latitude, longitude: mosque.mosque.location.longitude)
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Map(initialPosition: .region(MKCoordinateRegion(center: coord, latitudinalMeters: 800, longitudinalMeters: 800))) {
                    Marker(name, systemImage: "building.columns.fill", coordinate: coord).tint(accent)
                    UserAnnotation()
                }
                .frame(height: 110)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                Text(name).font(.headline)
                Text(state.distance(mosque.distanceMeters)).font(.footnote.monospacedDigit()).foregroundStyle(.secondary)
                if let a = mosque.mosque.address { Text(a).font(.footnote).foregroundStyle(.secondary) }
                Button {
                    let item = MKMapItem(placemark: MKPlacemark(coordinate: coord))
                    item.name = name
                    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeWalking])
                } label: {
                    Label(state.t("directions"), systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                }
                .tint(accent)
                Text(state.t(mosque.mosque.packId == OnlineMosques.applePackId ? "source_online_apple" : "source_offline_pack"))
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(state.t("category_mosque"))
    }
}
