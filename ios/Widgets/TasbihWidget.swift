import AppIntents
import SwiftUI
import WidgetKit

/// The tasbih count, kept by the widget extension on this phone (taps run here, without opening the app).
enum TasbihStore {
    private static let key = "tasbihCount"
    static var count: Int {
        get { UserDefaults.standard.integer(forKey: key) }
        set { UserDefaults.standard.set(max(0, newValue), forKey: key) }
    }
}

struct TasbihTapIntent: AppIntent {
    static var title: LocalizedStringResource = "widget_kind_tasbih"
    static var isDiscoverable = false

    func perform() async throws -> some IntentResult {
        TasbihStore.count += 1
        return .result()
    }
}

struct TasbihResetIntent: AppIntent {
    static var title: LocalizedStringResource = "tasbih_reset"
    static var isDiscoverable = false

    func perform() async throws -> some IntentResult {
        TasbihStore.count = 0
        return .result()
    }
}

/// The widget: the shared face (Shared/TasbihFace.swift) with its circle and reset wired to the intents.
struct TasbihView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry

    var body: some View {
        let count = TasbihStore.count
        TasbihFace(count: count, palette: entry.palette, family: family, locale: entry.state?.locale ?? .current,
                   a11y: entry.state?.t("tasbih_count_a11y", count) ?? "\(count)", resetLabel: entry.state?.t("tasbih_reset") ?? "Reset",
                   wrapTap: { AnyView(Button(intent: TasbihTapIntent()) { $0 }.buttonStyle(.plain)) },
                   wrapReset: { AnyView(Button(intent: TasbihResetIntent()) { $0 }.buttonStyle(.plain)) })
    }
}

struct TasbihWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Tasbih", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            TasbihView(entry: entry)
                .containerBackground(for: .widget) { entry.palette.background }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_tasbih"))
        .description(LocalizedStringResource("widget_kind_tasbih_desc"))
        .supportedFamilies([.systemSmall, .systemMedium] + LockScreen.tasbih)
    }
}
