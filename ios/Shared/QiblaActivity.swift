#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import AppIntents
import Foundation

/// Experimental: a Qibla compass in the Dynamic Island, started from the Qibla widget without opening the app.
/// The app reads the compass in the background for about two minutes (iOS shows the location indicator) and
/// updates the arrow about once a second; then the activity ends by itself.
struct QiblaActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// Degrees to turn, clockwise positive (-180...180); nil while the compass is not ready.
        var turn: Double?
        var facing: Bool
        /// "Facing the Qibla", "Turn right", "Turn left a little"... in the reader's language.
        var hint: String
    }

    var city: String
    var title: String
    /// The Qibla from north, as text ("243°"), and "from north" in the reader's language.
    var bearingText: String
    var fromNorth: String
    var rtl: Bool
    /// The widget style picked in the app (WidgetLook raw value): the island's accent colour follows it.
    var style: String
}

/// The widget's button: starts the compass in the Dynamic Island. Runs in the app (in the background).
struct StartQiblaIslandIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "qibla_island"
    static var description = IntentDescription("qibla_island_note")
    static var isDiscoverable = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await QiblaIslandRunner.start?()
        return .result()
    }
}

/// Set by the app at launch: the widget extension has no compass session of its own.
@MainActor
enum QiblaIslandRunner {
    static var start: (() async -> Void)?
}
#endif
