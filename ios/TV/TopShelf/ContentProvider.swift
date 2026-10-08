import NMCore
import SwiftUI
import TVServices

/// The Apple TV home screen row for Near Mosque: a banner with the city, both dates and today's six
/// times, drawn here from the city, method and language the app shares (keychain). Before a city is
/// chosen, tvOS shows the static Top Shelf image from the asset catalog.
final class ContentProvider: TVTopShelfContentProvider {
    override func loadTopShelfContent(completionHandler: @escaping (TVTopShelfContent?) -> Void) {
        Task { @MainActor in
            guard let state = SharedStore.read(), let url = Self.render(state, now: Date()) else { return completionHandler(nil) }
            let item = TVTopShelfItem(identifier: "today")
            item.title = state.t("todays_times")
            item.setImageURL(url, for: [.screenScale1x, .screenScale2x])
            item.displayAction = URL(string: "nearmosque://prayer").map { TVTopShelfAction(url: $0) }
            item.playAction = item.displayAction
            completionHandler(TVTopShelfInsetContent(items: [item]))
        }
    }

    /// The banner as a PNG in this extension's caches (one file, replaced each time).
    @MainActor
    static func render(_ s: SharedState, now: Date) -> URL? {
        let renderer = ImageRenderer(content: TopShelfBanner(state: s, now: now)
            .environment(\.layoutDirection, s.isRTL ? .rightToLeft : .leftToRight))
        renderer.scale = 2
        guard let image = renderer.uiImage, let data = image.pngData(),
              let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return nil }
        let url = dir.appendingPathComponent("top-shelf-\(Int(now.timeIntervalSince1970)).png")
        // Older banners go: a new file name makes tvOS load the new picture.
        for old in (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] where old.lastPathComponent.hasPrefix("top-shelf-") {
            try? FileManager.default.removeItem(at: old)
        }
        do { try data.write(to: url) } catch { return nil }
        return url
    }
}

/// 1940 x 692 points (the inset Top Shelf size): the night-to-gold sky, the city and dates on one side,
/// today's times in a row, the next prayer lit in gold.
struct TopShelfBanner: View {
    let state: SharedState
    let now: Date

    var body: some View {
        let days = state.days(now)
        let today = days.count == 3 ? days[1] : nil
        let next = PrayerCalculator().nextPrayer(days, now: now)
        VStack(alignment: .leading, spacing: 34) {
            HStack(alignment: .firstTextBaseline) {
                Text(state.name).font(.system(size: 70, weight: .bold))
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    Text(state.weekday(now) + " · " + state.dayMonth(now)).font(.system(size: 36, weight: .medium))
                    Text(state.hijri(now)).font(.system(size: 36)).foregroundStyle(Color(red: 0.83, green: 0.66, blue: 0.26))
                }
            }
            HStack(spacing: 22) {
                ForEach(PrayerEvent.allCases, id: \.self) { e in
                    let lit = e == next?.event && next?.isTomorrow == false
                    VStack(spacing: 12) {
                        Text(state.t("prayer_\(e.rawValue)")).font(.system(size: 38, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.6)
                        Text(today?[e].map(state.time) ?? "–").font(.system(size: 50, weight: .medium).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.6)
                    }
                    .foregroundStyle(lit ? Color(red: 0.03, green: 0.11, blue: 0.16) : .white)
                    .padding(.vertical, 30)
                    .frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .fill(lit ? Color(red: 0.83, green: 0.66, blue: 0.26) : Color.white.opacity(0.14)))
                }
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 80)
        .padding(.vertical, 60)
        .frame(width: 1940, height: 692)
        .background(LinearGradient(colors: [Color(red: 0.03, green: 0.11, blue: 0.16), Color(red: 0.07, green: 0.19, blue: 0.29),
                                            Color(red: 0.17, green: 0.30, blue: 0.48)], startPoint: .top, endPoint: .bottom))
    }
}
