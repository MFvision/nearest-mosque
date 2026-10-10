import AppIntents
import NMCore
import SwiftUI
import WidgetKit

/// "I prayed" on the widget: noted for the companion on this phone, without opening the app.
struct CompanionMarkIntent: AppIntent {
    static var title: LocalizedStringResource = "widget_kind_companion"
    static var isDiscoverable = false

    @Parameter(title: "Prayer") var event: String
    @Parameter(title: "Date") var date: String

    init() {}
    init(event: String, date: String) {
        self.event = event
        self.date = date
    }

    func perform() async throws -> some IntentResult {
        if let d = CivilDate(iso: date), PrayerEvent(rawValue: event) != nil { CompanionLog.checkIn(event, on: d) }
        return .result()
    }
}

struct CompanionView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry

    var body: some View {
        CompanionFace(entry: entry, family: family) { event, date, label in
            AnyView(Button(intent: CompanionMarkIntent(event: event, date: date)) { label }.buttonStyle(.plain))
        }
        .widgetURL(URL(string: "nearmosque://prayer"))
    }
}

struct CompanionWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Companion", intent: PrayerWidgetIntent.self, provider: PrayerProvider()) { entry in
            CompanionView(entry: entry)
                .containerBackground(for: .widget) { entry.palette.background }
        }
        .configurationDisplayName(LocalizedStringResource("widget_kind_companion"))
        .description(LocalizedStringResource("widget_kind_companion_desc"))
        .supportedFamilies([.systemSmall, .systemMedium] + LockScreen.companion)
    }
}
