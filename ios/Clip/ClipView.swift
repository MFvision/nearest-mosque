import MapKit
import NMCore
import StoreKit
import SwiftUI

/// One screen: the nearest mosque with a large Directions button, the next prayer, and the other mosques around.
/// Once mosques are shown, the App Store banner offers the full app.
struct ClipView: View {
    @Environment(Localization.self) private var l10n
    @Environment(\.openURL) private var openURL
    @State private var model = ClipModel()
    @State private var offerApp = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { ctx in
            let sky = Sky.of(SkyPeriod.at(ctx.date, today: model.today, current: model.current(at: ctx.date)))
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(l10n.t("tab_mosques"))
                        .font(.largeTitle.bold())
                        .padding(.top, 24)
                    if let next = model.next { nextPrayer(next, now: ctx.date) }
                    content
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 120)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
            .foregroundStyle(Theme.ink)
            .background { SkyBackdrop(sky: sky, horizon: 0.82, skyline: true).ignoresSafeArea() }
            .environment(\.sky, sky)
            .animation(Theme.spring, value: model.phase)
        }
        .task { model.start() }
        .onChange(of: model.phase) { _, phase in
            if phase == .ready || phase == .empty { offerApp = true }
        }
        .appStoreOverlay(isPresented: $offerApp) { SKOverlay.AppClipConfiguration(position: .bottom) }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .locating, .searching:
            HStack(spacing: 12) {
                ProgressView()
                Text(l10n.t(model.phase == .locating ? "searching_from_you" : "online_searching"))
            }
            .glassCard()
        case .ready:
            if let first = model.items.first { nearest(first) }
            ForEach(model.items.dropFirst()) { r in row(r) }
        case .empty:
            message("building.columns", l10n.t("no_records_title"), action: l10n.t("retry")) { model.start() }
        case .failed:
            message("location.slash", l10n.t(model.here == nil ? "location_fix_failed" : "no_records_title"),
                    action: l10n.t("retry")) { model.start() }
        case .denied:
            VStack(alignment: .leading, spacing: 10) {
                Label(l10n.t("mosques_need_location_title"), systemImage: "location.fill").font(.headline)
                Text(l10n.t("location_denied")).foregroundStyle(Theme.ink.opacity(0.8))
                Button(l10n.t("open_settings")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .buttonStyle(FallbackGlassButtonStyle(prominent: true))
            }
            .glassCard()
        }
    }

    private func nextPrayer(_ next: Upcoming, now: Date) -> some View {
        HStack {
            Image(systemName: Theme.icon(next.event)).foregroundStyle(Theme.accent)
            Text(l10n.t("next_prayer"))
            Spacer()
            Text(l10n.t(Format.prayerKey(next.event))).bold()
            Text(Format.time(next.at, zone: model.zone, locale: l10n.locale)).monospacedDigit()
        }
        .font(.callout)
        .glassCard(padding: 14)
        .accessibilityElement(children: .combine)
    }

    private func nearest(_ r: RankedMosque) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(l10n.t("nearest_known_mosque")).font(.subheadline).foregroundStyle(Theme.accent)
            Text(name(r)).font(.title2.bold()).lineLimit(3)
            Text(Format.distance(r.distanceMeters, l10n: l10n)).font(.headline.monospacedDigit())
            if let a = r.mosque.address { Text(a).font(.footnote).foregroundStyle(Theme.ink.opacity(0.75)) }
            Button { directions(r) } label: {
                Label(l10n.t("directions"), systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(FallbackGlassButtonStyle(prominent: true))
            .padding(.top, 4)
        }
        .glassCard()
    }

    private func row(_ r: RankedMosque) -> some View {
        Button { directions(r) } label: {
            HStack(spacing: 12) {
                Image(systemName: "building.columns.fill").foregroundStyle(Theme.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(name(r)).lineLimit(2).multilineTextAlignment(.leading)
                    Text(Format.distance(r.distanceMeters, l10n: l10n)).font(.footnote.monospacedDigit())
                        .foregroundStyle(Theme.ink.opacity(0.75))
                }
                Spacer()
                Image(systemName: "arrow.triangle.turn.up.right.circle").foregroundStyle(Theme.accent)
            }
        }
        .buttonStyle(.plain)
        .glassCard(padding: 14)
        .accessibilityLabel(l10n.t("mosque_detail_a11y", name(r), Format.distance(r.distanceMeters, l10n: l10n)))
        .accessibilityHint(l10n.t("directions"))
    }

    private func message(_ icon: String, _ text: String, action: String, _ run: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(text, systemImage: icon).font(.headline)
            Button(action, action: run).buttonStyle(FallbackGlassButtonStyle(prominent: false))
        }
        .glassCard()
    }

    private func name(_ r: RankedMosque) -> String {
        r.mosque.displayName(l10n.language) ?? l10n.t("mosque_unnamed")
    }

    /// Walking directions in Apple Maps.
    private func directions(_ r: RankedMosque) {
        let coord = r.mosque.location.coordinate
        let item = MKMapItem(placemark: MKPlacemark(coordinate: coord))
        item.name = name(r)
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeWalking])
    }
}
