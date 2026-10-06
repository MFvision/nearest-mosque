import AppIntents

/// Siri phrases and Shortcuts (also for the Action button). Phrases are English for now.
struct NearMosqueShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: NextPrayerIntent(), phrases: [
            "When is the next prayer in \(.applicationName)",
            "Next prayer in \(.applicationName)",
            "\(.applicationName) next prayer",
        ], shortTitle: "Next prayer", systemImageName: "clock")
        AppShortcut(intent: OpenQiblaIntent(), phrases: [
            "Show the Qibla in \(.applicationName)",
            "Where is the Qibla in \(.applicationName)",
            "\(.applicationName) Qibla",
        ], shortTitle: "Qibla", systemImageName: "location.north.line.fill")
    }
}
