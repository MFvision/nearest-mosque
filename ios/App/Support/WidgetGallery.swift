#if DEBUG
import NMCore
import SwiftUI
import WidgetKit

/// Debug builds only: the home-screen widgets drawn at their real sizes, for CI screenshots
/// (`-demoWidgets YES -demoWidgetKind next|countdown|today|mosque|ask -demoWidgetLook cream|green|night`).
/// Uses the city and settings chosen in the app, like the widgets themselves.
struct WidgetGalleryView: View {
    @Environment(AppModel.self) private var model

    private var kind: PrayerWidgetKind {
        switch UserDefaults.standard.string(forKey: "demoWidgetKind") {
        case "countdown": return .countdown
        case "today": return .today
        case "mosque": return .mosque
        case "ask": return .ask
        default: return .next
        }
    }
    private var look: WidgetLook { WidgetLook(rawValue: UserDefaults.standard.string(forKey: "demoWidgetLook") ?? "") ?? .cream }

    private var sizes: [(WidgetFamily, CGSize)] {
        let small = CGSize(width: 170, height: 170), medium = CGSize(width: 364, height: 170), large = CGSize(width: 364, height: 382)
        switch kind {
        case .next: return [(.systemSmall, small), (.systemMedium, medium), (.systemLarge, large)]
        case .countdown: return [(.systemSmall, small), (.systemMedium, medium)]
        case .today: return [(.systemMedium, medium), (.systemLarge, large)]
        case .mosque, .ask: return [(.systemSmall, small), (.systemMedium, medium)]
        }
    }

    var body: some View {
        let state = model.settings.location.map { loc in
            SharedState(name: loc.name, latitude: loc.latitude, longitude: loc.longitude, zoneId: loc.zoneId, prayer: model.settings.prayer,
                        language: model.l10n.language, hijriAdjustmentDays: model.settings.prayer.hijriAdjustmentDays,
                        reminders: ["fajr", "maghrib", "isha"],
                        mosques: model.nearestMosques.map { r in
                            SharedMosque(id: r.id, name: r.mosque.displayName(model.l10n.language) ?? "", meters: r.distanceMeters,
                                         bearing: Geo.initialBearing(from: loc.location, to: r.mosque.location))
                        },
                        questions: ((try? model.ask?.commonQuestions()) ?? []).compactMap { $0.question[model.l10n.language] ?? $0.question["en"] })
        } ?? SharedState.preview
        let entry = PrayerEntry.make(Date(), look: look, state: state)
        let p = WidgetPalette.of(look)
        ScrollView {
            VStack(spacing: 18) {
                ForEach(Array(sizes.enumerated()), id: \.offset) { _, item in
                    PrayerWidgetView(entry: entry, kind: kind, family: item.0)
                        .padding(16)
                        .frame(width: item.1.width, height: item.1.height)
                        .background(p.background)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .shadow(color: .black.opacity(0.15), radius: 8, y: 3)
                }
            }
            .padding(.vertical, 60)
            .frame(maxWidth: .infinity)
        }
        .background(Color(white: 0.55).ignoresSafeArea())
        .task(id: model.ready) { model.refreshNearestMosque() }
    }
}
#endif
