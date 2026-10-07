import AppIntents

/// Siri phrases and Shortcuts, also for the Action button (Settings > Action Button > Shortcut).
/// Phrases are English for now.
struct NearMosqueShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: NextPrayerIntent(), phrases: [
            "When is the next prayer in \(.applicationName)",
            "Next prayer in \(.applicationName)",
            "\(.applicationName) next prayer",
        ], shortTitle: "widget_kind_next", systemImageName: "clock")
        AppShortcut(intent: NearestMosqueIntent(), phrases: [
            "Where is the nearest mosque in \(.applicationName)",
            "Nearest mosque in \(.applicationName)",
            "\(.applicationName) nearest mosque",
        ], shortTitle: "widget_kind_mosque", systemImageName: "building.columns")
        AppShortcut(intent: NearestMosqueDirectionsIntent(), phrases: [
            "Take me to the nearest mosque with \(.applicationName)",
            "Directions to the nearest mosque in \(.applicationName)",
        ], shortTitle: "directions", systemImageName: "figure.walk")
        AppShortcut(intent: AskIslamIntent(), phrases: [
            "Ask \(.applicationName)",
            "Ask a question in \(.applicationName)",
            "\(.applicationName) ask about Islam",
        ], shortTitle: "widget_kind_ask", systemImageName: "sparkles")
        AppShortcut(intent: OpenPrayerTimesIntent(), phrases: [
            "Show prayer times in \(.applicationName)",
            "Open \(.applicationName) prayer times",
        ], shortTitle: "todays_times", systemImageName: "calendar")
        AppShortcut(intent: OpenQiblaIntent(), phrases: [
            "Show the Qibla in \(.applicationName)",
            "Where is the Qibla in \(.applicationName)",
            "\(.applicationName) Qibla",
        ], shortTitle: "Qibla", systemImageName: "location.north.line.fill")
    }
}
