import CoreImage.CIFilterBuiltins
import MapKit
import NMCore
import NMData
import SwiftUI

/// Mosques near the TV's city: the built-in data, plus Apple Maps when online search is on.
@MainActor
@Observable
final class TVMosquesModel {
    enum Online: Equatable { case off, searching, done, unavailable }
    private(set) var result: MosqueResult?
    private(set) var items: [RankedMosque] = []
    private(set) var online: Online = .off
    private(set) var loading = false
    private var key: String?
    static let radius = 25_000.0

    func load(_ model: TVModel) async {
        guard let repo = model.mosques, let loc = model.settings.location else { return }
        let center = loc.location
        let key = "\(center.latitude),\(center.longitude),\(model.settings.onlineSearch),\(model.l10n.language)"
        guard key != self.key else { return }
        self.key = key
        loading = true
        let lang = model.l10n.language
        let r = try? await Task.detached { try repo.nearest(center, radiusMeters: Self.radius, lang: lang) }.value
        guard key == self.key else { return }
        result = r
        var offline: [RankedMosque] = []
        if case let .found(list, _) = r { offline = list }
        items = offline
        loading = false
        guard model.settings.onlineSearch else { online = .off; return }
        online = .searching
        let live = await AppleMosqueSearch.search(center: center, radiusMeters: 10_000)
        guard key == self.key else { return }
        if let live {
            items = OnlineMosques.merge(center: center, offline: offline, online: live, radiusMeters: Self.radius)
            online = .done
        } else {
            online = .unavailable
        }
    }
}

/// The nearest mosques as a list beside a map. Selecting one shows its distance and details, with a
/// code to scan for directions on a phone (a television cannot give directions itself).
struct TVMosquesView: View {
    @Environment(TVModel.self) private var model
    @State private var vm = TVMosquesModel()
    @State private var selected: String?
    @State private var camera: MapCameraPosition = .automatic

    var body: some View {
        let l10n = model.l10n
        HStack(alignment: .top, spacing: 50) {
            VStack(alignment: .leading, spacing: 16) {
                Text(l10n.t("tab_mosques")).font(.system(size: 56, weight: .bold))
                if let loc = model.settings.location {
                    Text(l10n.t("searching_from_city", loc.name)).font(.system(size: 26)).foregroundStyle(.secondary)
                }
                list
            }
            .frame(width: 720)
            .focusSection()
            VStack(alignment: .leading, spacing: 24) {
                map
                if let r = current { TVMosqueCard(ranked: r) }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 80)
        .padding(.vertical, 40)
        .background { SkyBackdrop(sky: Sky.of(.night), horizon: 0.9).ignoresSafeArea() }
        .task(id: "\(model.settings.location?.latitude ?? 0)\(model.settings.onlineSearch)\(model.l10n.language)") {
            await vm.load(model)
            if selected == nil || !vm.items.contains(where: { $0.id == selected }) { selected = vm.items.first?.id }
            fly()
        }
        .onChange(of: selected) { _, _ in fly() }
    }

    private var current: RankedMosque? { vm.items.first { $0.id == selected } ?? vm.items.first }

    @ViewBuilder
    private var list: some View {
        let l10n = model.l10n
        if vm.loading {
            ProgressView().frame(maxWidth: .infinity, minHeight: 200)
        } else if vm.items.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                switch vm.result {
                case let .areaNotDownloaded(installed)?:
                    Text(l10n.t("region_not_downloaded_title")).font(.system(size: 34, weight: .semibold))
                    Text(l10n.t("region_not_downloaded_body", installed.isEmpty ? l10n.t("region_none_installed") : installed.joined(separator: ", ")))
                case let .noRecordsInCoverage(area, radius)?:
                    Text(l10n.t("no_records_title")).font(.system(size: 34, weight: .semibold))
                    Text(l10n.t("no_records_body", area, Format.distance(radius, l10n: l10n)))
                default:
                    Text(l10n.t("no_records_title")).font(.system(size: 34, weight: .semibold))
                }
                if vm.online == .searching { Label(l10n.t("online_searching"), systemImage: "antenna.radiowaves.left.and.right") }
                if vm.online == .unavailable { Text(l10n.t("online_offline_note")) }
            }
            .font(.system(size: 26))
            .foregroundStyle(.secondary)
        } else {
            ScrollView {
                LazyVStack(spacing: 14) {
                    ForEach(vm.items.prefix(30)) { r in
                        Button {
                            selected = r.id
                        } label: {
                            HStack(spacing: 20) {
                                Image(systemName: r.id == selected ? "building.columns.fill" : "building.columns")
                                    .foregroundStyle(Theme.gold)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(r.mosque.displayName(l10n.language) ?? l10n.t("mosque_unnamed")).lineLimit(1)
                                    Text(l10n.t("straight_line", Format.distance(r.distanceMeters, l10n: l10n)))
                                        .font(.system(size: 22)).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                            }
                            .font(.system(size: 30, weight: .medium))
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.card)
                        .accessibilityLabel(l10n.t("mosque_detail_a11y", r.mosque.displayName(l10n.language) ?? l10n.t("mosque_unnamed"),
                                                   Format.distance(r.distanceMeters, l10n: l10n)))
                    }
                }
                .padding(20)
            }
            if vm.online == .searching {
                Label(l10n.t("online_searching"), systemImage: "antenna.radiowaves.left.and.right").font(.system(size: 22)).foregroundStyle(.secondary)
            }
            Text(sourceNote).font(.system(size: 20)).foregroundStyle(.secondary)
        }
    }

    private var sourceNote: String {
        var parts: [String] = []
        if vm.items.contains(where: { $0.mosque.packId != OnlineMosques.applePackId }) { parts.append(model.l10n.t("data_attribution_osm")) }
        if vm.items.contains(where: { $0.mosque.packId == OnlineMosques.applePackId }) { parts.append(model.l10n.t("source_online_apple")) }
        return parts.joined(separator: " · ")
    }

    private var map: some View {
        Map(position: $camera, interactionModes: []) {
            if let loc = model.settings.location {
                Annotation(loc.name, coordinate: loc.location.coordinate) {
                    Image(systemName: "mappin.circle.fill").font(.system(size: 36)).foregroundStyle(.white, Theme.navy)
                }
            }
            ForEach(vm.items.prefix(30)) { r in
                Marker(r.mosque.displayName(model.l10n.language) ?? model.l10n.t("mosque_unnamed"), systemImage: "building.columns.fill",
                       coordinate: r.mosque.location.coordinate)
                    .tint(r.id == current?.id ? Theme.gold : Theme.navy)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .frame(height: 540)
        .accessibilityLabel(model.l10n.t("map_a11y"))
    }

    private func fly() {
        guard let r = current else {
            if let loc = model.settings.location {
                camera = .camera(MapCamera(centerCoordinate: loc.location.coordinate, distance: 12_000))
            }
            return
        }
        withAnimation(.easeInOut(duration: 0.8)) {
            camera = .camera(MapCamera(centerCoordinate: r.mosque.location.coordinate, distance: 2_500))
        }
    }
}

/// The selected mosque: name, distance, address and phone, with a code that opens directions on a phone.
struct TVMosqueCard: View {
    @Environment(TVModel.self) private var model
    let ranked: RankedMosque

    var body: some View {
        let l10n = model.l10n
        let m = ranked.mosque
        let name = m.displayName(l10n.language) ?? l10n.t("mosque_unnamed")
        HStack(alignment: .top, spacing: 36) {
            VStack(alignment: .leading, spacing: 12) {
                Text(name).font(.system(size: 40, weight: .bold)).lineLimit(2)
                Label(l10n.t("straight_line", Format.distance(ranked.distanceMeters, l10n: l10n)), systemImage: "ruler")
                if let a = m.address, !a.isEmpty { Label(a, systemImage: "mappin").lineLimit(2) }
                if let p = m.phone, !p.isEmpty { Label(p, systemImage: "phone") }
                Text(l10n.t(m.packId == OnlineMosques.applePackId ? "source_online_apple" : "source_offline_pack"))
                    .font(.system(size: 22)).foregroundStyle(.secondary)
            }
            .font(.system(size: 28))
            Spacer(minLength: 0)
            if let qr = QRCode.image(Self.directionsURL(m, name: name)) {
                VStack(spacing: 10) {
                    Image(uiImage: qr)
                        .interpolation(.none)
                        .resizable()
                        .frame(width: 190, height: 190)
                        .padding(14)
                        .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    Text(l10n.t("tv_scan_directions"))
                        .font(.system(size: 22))
                        .multilineTextAlignment(.center)
                        .frame(width: 230)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(l10n.t("tv_scan_directions"))
            }
        }
        .padding(30)
        .glass(RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    /// Apple Maps directions as a web link, so the phone that scans it can open it.
    static func directionsURL(_ m: Mosque, name: String) -> String {
        var c = URLComponents(string: "https://maps.apple.com/")!
        c.queryItems = [URLQueryItem(name: "daddr", value: "\(m.location.latitude),\(m.location.longitude)"), URLQueryItem(name: "q", value: name)]
        return c.url?.absoluteString ?? ""
    }
}

enum QRCode {
    static func image(_ text: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let out = f.outputImage, let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
