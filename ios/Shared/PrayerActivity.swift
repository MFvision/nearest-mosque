#if canImport(ActivityKit) && !targetEnvironment(macCatalyst) && !os(visionOS)
import ActivityKit
import Foundation

/// The next-prayer countdown on the Lock Screen and in the Dynamic Island. The app starts it, and moves it on
/// whenever it runs; the words come ready-made in the reader's language, so the widget extension only draws them.
struct PrayerActivityAttributes: ActivityAttributes {
    struct Slot: Codable, Hashable {
        var name: String
        var time: String
    }

    /// An Adhan or an iqama after the one counted down to.
    struct Event: Codable, Hashable {
        enum Kind: String, Codable, Hashable { case prayer, iqama }

        var kind: Kind
        /// The prayer's name and SF Symbol, its time ("6:12 PM"; for an iqama "Iqama 6:22 PM"), and when its
        /// countdown began (for the progress bar).
        var prayer: String
        var symbol: String
        var at: Date
        var start: Date
        var timeText: String
        /// Its slot in its own day's row along the bottom (`slots`, or `followingSlots` for the next day's; -1: neither),
        /// and the period in effect until it, for the sky colours.
        var index: Int
        var period: String
    }

    struct ContentState: Codable, Hashable {
        /// What is counted down to (the next Adhan, or an iqama: see `kind`): name, SF Symbol, time and when its
        /// countdown began (for the progress bar).
        var prayer: String
        var symbol: String
        var at: Date
        var start: Date
        var timeText: String
        var city: String
        /// "Time for Asr", shown once the time has come.
        var nowTitle: String
        /// Today's five prayers along the bottom of the Lock Screen view (tomorrow's after Isha), and which one is
        /// next (-1: none).
        var slots: [Slot]
        var nextIndex: Int
        /// PrayerEvent raw value of the period in effect, for the sky colours ("" before Fajr).
        var period: String
        /// The prayer's iqama, "Iqama in" and "Iqama 3:20 PM". Nil with the iqama off. From an older app (no `following`):
        /// once the Adhan time has come (the activity is then stale) the views count down to the iqama.
        var iqama: Date? = nil
        var iqamaLabel: String? = nil
        var iqamaText: String? = nil
        /// `at` is the Adhan, or the iqama of `prayer` (its Adhan has come: "Time for Asr", "Iqama in"). Nil from an
        /// older app: the Adhan.
        var kind: Event.Kind? = nil
        /// The Adhans and iqamas after `at`, in order. The activity goes stale at `at`, and the views then count
        /// down to the first Adhan among them, so the card moves on with no update. Nil from an older app.
        var following: [Event]? = nil
        /// The row along the bottom once `at` has passed, when the first Adhan in `following` is the next day's
        /// (tomorrow's prayers, after Isha). Nil otherwise, or from an older app: `slots`.
        var followingSlots: [Slot]? = nil

        /// From the Adhan to the iqama, for an older app's countdown once the Adhan time has come.
        var iqamaRange: ClosedRange<Date>? { iqama.map { at...max(at, $0) } }

        /// What the Lock Screen, the Dynamic Island and the watch show.
        struct Shown {
            /// The name in the island; `title` is the Lock Screen's ("Time for Asr" while it waits for its iqama).
            var prayer: String
            var title: String
            var symbol: String
            /// "3:00 PM", "Iqama 3:20 PM", or "Asr · Iqama 3:20 PM" while counting down past that iqama.
            var time: String
            /// "Iqama in", above a countdown to the iqama.
            var label: String?
            /// The countdown and the progress bar (nil: none).
            var range: ClosedRange<Date>?
            var index: Int
            var period: String
            /// The row along the bottom, `index` lit.
            var slots: [Slot]
        }

        /// Before `at`, the countdown to it. Once it has passed (stale), the next Adhan in `following`, its countdown
        /// already running from `at`; an iqama on the way shows only as its time, since a countdown to it would sit
        /// at 0:00 once it passed.
        func shown(stale: Bool) -> Shown {
            if stale, let following, let next = following.first(where: { $0.kind == .prayer }) {
                let iqama = following.prefix { $0.kind == .iqama }.last
                return Shown(prayer: next.prayer, title: next.prayer, symbol: next.symbol,
                             time: iqama.map { $0.prayer + " · " + $0.timeText } ?? next.timeText, label: nil,
                             range: next.start...max(next.start, next.at), index: next.index, period: next.period,
                             slots: followingSlots ?? slots)
            }
            if kind == .iqama {
                return Shown(prayer: prayer, title: nowTitle, symbol: symbol, time: iqamaText ?? timeText, label: stale ? nil : iqamaLabel,
                             range: stale ? nil : start...max(start, at), index: nextIndex, period: period, slots: slots)
            }
            if stale {
                // From an older app (or no Adhan after this one): "Time for Asr", with the countdown to its iqama.
                return Shown(prayer: prayer, title: nowTitle, symbol: symbol, time: iqamaText ?? timeText,
                             label: iqamaRange == nil ? nil : iqamaLabel, range: iqamaRange, index: nextIndex, period: period,
                             slots: slots)
            }
            return Shown(prayer: prayer, title: prayer, symbol: symbol, time: timeText, label: nil,
                         range: start...max(start, at), index: nextIndex, period: period, slots: slots)
        }
    }

    var rtl: Bool
    /// The widget style picked in the app (WidgetLook raw value): the island's accent colour follows it.
    var style: String
    var nextLabel: String
    var qiblaLabel: String
    var mosqueLabel: String
    /// When the app started it: iOS ends an activity 8 hours after that. Nil from an older app.
    var started: Date? = nil
}
#endif
