#if canImport(ActivityKit) && !targetEnvironment(macCatalyst)
import ActivityKit
import Foundation

/// The next-prayer countdown on the Lock Screen and in the Dynamic Island. The app starts it (and moves it to
/// the following prayer) when it opens; the words come ready-made in the reader's language, so the widget
/// extension only draws them.
struct PrayerActivityAttributes: ActivityAttributes {
    struct Slot: Codable, Hashable {
        var name: String
        var time: String
    }

    struct ContentState: Codable, Hashable {
        /// The next prayer: name, SF Symbol, time and when its period began (for the progress bar).
        var prayer: String
        var symbol: String
        var at: Date
        var start: Date
        var timeText: String
        var city: String
        /// "Time for Asr", shown once the time has come.
        var nowTitle: String
        /// Today's five prayers along the bottom of the Lock Screen view, and which one is next (-1: tomorrow's).
        var slots: [Slot]
        var nextIndex: Int
        /// PrayerEvent raw value of the period in effect, for the sky colours ("" before Fajr).
        var period: String
    }

    var rtl: Bool
    /// The widget style picked in the app (WidgetLook raw value): the island's accent colour follows it.
    var style: String
    var nextLabel: String
    var qiblaLabel: String
    var mosqueLabel: String
}
#endif
