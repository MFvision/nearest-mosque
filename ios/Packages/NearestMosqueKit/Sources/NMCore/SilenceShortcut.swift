import Foundation

/// iPhone's side of the silence during prayer: no app may turn on Focus there, so the user makes a Shortcut once
/// (the app's "End of prayer" action, then "Set Focus: Do Not Disturb, On, Until" its result) and the app runs it
/// by name from the Adhan or iqama notification.
extension SilenceSettings {
    /// The settings the "End of prayer" action uses: on even when switched off in the app (someone who runs the
    /// shortcut wants it to work), with every prayer when none is chosen.
    public var forShortcut: SilenceSettings {
        var s = self
        s.enabled = true
        if !PrayerEvent.prayers.contains(where: { s.includes($0) }) { s.prayers = PrayerEvent.prayers.map(\.rawValue) }
        return s
    }

    /// When the silence under way at `now` ends, else when the next one ends (nil without prayer times).
    /// The windows never overlap, so the first one not yet over is the current or the next.
    public func shortcutEnd(_ days: [DaySchedule], iqama: IqamaSettings, now: Date) -> Date? {
        forShortcut.windows(days, iqama: iqama, after: now).first?.end
    }

    /// What the notification button "Silence until after the prayer" on the notification for `event` on `date`
    /// silences until: the end of the silence under way at `now`, else of that prayer's own silence if it begins
    /// within `maxLead` (the button on an Adhan before a silence from the iqama). nil otherwise: a button tapped
    /// after its prayer's silence (the notification left in Notification Center) would silence until after the
    /// next prayer (Isha's until after Fajr), or from the tap through it. A silence ending within `endMargin`
    /// counts as over: the Shortcut reads the clock again once the app is on the screen and Shortcuts has opened,
    /// and by then it would get the next one. When not nil, it is what the Shortcut gets from `shortcutEnd`
    /// within `endMargin`.
    public func shortcutEndForButton(_ days: [DaySchedule], iqama: IqamaSettings, now: Date, event: PrayerEvent?, date: CivilDate?,
                                     maxLead: TimeInterval = 60 * 60, endMargin: TimeInterval = 90) -> Date? {
        guard let w = forShortcut.windows(days, iqama: iqama, after: now.addingTimeInterval(endMargin)).first else { return nil }
        if w.start <= now { return w.end }
        guard w.event == event, w.date == date, w.start.timeIntervalSince(now) <= maxLead else { return nil }
        return w.end
    }

    /// Whether this notification carries the "Silence until after the prayer" button: the one at which the
    /// silence begins. That is the Adhan (or the bell at prayer time) when the silence starts at the Adhan and
    /// for the Friday prayer, and the iqama notification when it starts at the iqama; with no iqama
    /// notification for that prayer, the Adhan carries it (the shortcut then silences until the prayer ends).
    public func offersAction(_ kind: AlertKind, event: PrayerEvent, date: CivilDate, iqama: IqamaSettings) -> Bool {
        guard enabled, includes(event) else { return false }
        let atAdhan = kind == .adhan || kind == .atPrayer
        if start == .adhan || (event == .dhuhr && date.isFriday) { return atAdhan }
        let iqamaAlert = iqama.enabled && iqama.alert && iqama.wait(event) > 0
        return kind == .iqama || (atAdhan && !iqamaAlert)
    }

    /// The Shortcut's name: the one saved, else the default in the app's language. The app saves the name in use
    /// once the shortcut is (silence turned on, tried, or run from a notification), so a later change of language
    /// does not run a translated name the user's Shortcut does not have; a saved name always wins over the default.
    public static func shortcutName(_ stored: String?, default fallback: String) -> String {
        let s = stored?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return s.isEmpty ? fallback : s
    }

    /// The name to save from the Settings field: what was typed (the default too, as it is), or with the field
    /// empty the name already saved (nil when none yet). An empty field never erases a name.
    public static func storedShortcutName(_ typed: String, current: String?) -> String? {
        let s = typed.trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? current : s
    }

    /// `shortcuts://run-shortcut?name=…`, the name percent-encoded byte by byte (spaces, Arabic, & and + included).
    public static func runShortcutURL(_ name: String) -> URL? {
        var allowed = CharacterSet()
        allowed.insert(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        guard let encoded = name.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
        return URL(string: "shortcuts://run-shortcut?name=" + encoded)
    }

    /// Opens the Shortcuts app.
    public static let shortcutsAppURL = URL(string: "shortcuts://")!
}
