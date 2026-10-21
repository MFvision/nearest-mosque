import Foundation
import NMCore
import NMData
import SwiftUI
import UserNotifications
import WidgetKit

/// The location prayer times are calculated for: separate from the device position and map centre.
struct PrayerLocation: Codable, Equatable {
    enum Source: String, Codable { case city, device }
    var name: String
    var latitude: Double
    var longitude: Double
    var zoneId: String
    var countryCode: String?
    var source: Source
    var zoneConfirmed = true
    /// With source .device: the nearest bundled city within 30 km (the one named), nil when none is that close.
    var cityLatitude: Double?
    var cityLongitude: Double?

    var location: LatLng { LatLng(latitude, longitude) ?? Qibla.kaaba }
    var zone: TimeZone { TimeZone(identifier: zoneId) ?? .current }

    /// Whose weather the sky shows ("Sky follows the weather" sends only a city's coordinates): the chosen city, or
    /// with "Use my location" the city near the device; nil when there is none (the sky stays normal).
    var weatherLocation: LatLng? {
        if source == .city { return location }
        guard let la = cityLatitude, let lo = cityLongitude else { return nil }
        return LatLng(la, lo)
    }
}

/// Persistent settings (UserDefaults). Missing keys decode to defaults so older saves keep working.
struct StoredSettings: Codable, Equatable {
    var location: PrayerLocation?
    var followDevice = false
    var prayer = PrayerSettings()
    var methodChosenByUser = false
    var reminders: Set<PrayerEvent> = []
    /// Early, Friday and Ramadan reminders and the Fajr alarm.
    var alerts = AlertSettings()
    /// The wait for the iqama after each Adhan, and the prayer after it.
    var iqama = IqamaSettings()
    /// Silent during prayer (iPhone: through the user's Shortcut, run from the Adhan or iqama notification).
    var silence = SilenceSettings()
    /// The name of that Shortcut as the user made it; nil = the default name in the app's language, until the
    /// shortcut is first used (silence turned on, tried, or run): the name in use is then saved as it is.
    var silenceShortcut: String?
    var onboarded = false
    /// Live mosque results (Apple Maps search) added to downloaded data; disclosed in onboarding and Settings.
    var onlineSearch = true
    /// Style of the widgets set to "Same as app" (WidgetLook raw value).
    var widgetLook = "cream"
    /// The next-prayer countdown on the Lock Screen and in the Dynamic Island.
    var liveCountdown = true
    /// The companion on the Prayer screen and its widget (CompanionKind raw value).
    var companion = "lantern"
    /// Sky follows the weather: opt-in, off by default. The prayer city's weather from MET Norway on the sky, with
    /// the temperature and the wind (WeatherService). Not shared with the widgets or the watch.
    var skyWeather = false

    init() {}

    /// Each field is read on its own: one that cannot be read takes its default and the rest are kept (one bad
    /// field used to reset everything, the prayer bells included).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func get<T: Decodable>(_ type: T.Type, _ key: CodingKeys) -> T? { (try? c.decodeIfPresent(type, forKey: key)) ?? nil }
        location = get(PrayerLocation.self, .location)
        followDevice = get(Bool.self, .followDevice) ?? false
        prayer = get(PrayerSettings.self, .prayer) ?? PrayerSettings()
        methodChosenByUser = get(Bool.self, .methodChosenByUser) ?? false
        // Bells as names, so a value this version does not know is skipped rather than failing the set.
        reminders = Set((get([String].self, .reminders) ?? []).compactMap(PrayerEvent.init(rawValue:)))
        alerts = get(AlertSettings.self, .alerts) ?? AlertSettings()
        iqama = get(IqamaSettings.self, .iqama) ?? IqamaSettings()
        silence = get(SilenceSettings.self, .silence) ?? SilenceSettings()
        silenceShortcut = get(String.self, .silenceShortcut)
        onboarded = get(Bool.self, .onboarded) ?? false
        onlineSearch = get(Bool.self, .onlineSearch) ?? true
        widgetLook = get(String.self, .widgetLook) ?? "cream"
        liveCountdown = get(Bool.self, .liveCountdown) ?? true
        companion = get(String.self, .companion) ?? "lantern"
        skyWeather = get(Bool.self, .skyWeather) ?? false
    }

    /// The saved settings, read without the app's model (App Intents run with the app in the background).
    static func saved() -> StoredSettings? {
        UserDefaults.standard.data(forKey: "settings").flatMap { try? JSONDecoder().decode(StoredSettings.self, from: $0) }
    }
}

/// App-wide state and services. The UI reads computed values only from local storage and deterministic services.
/// Two things go online, both disclosed in onboarding and Settings: the live mosque search (Apple Maps, when online
/// search is on: refreshNearestMosque) and, only when "Sky follows the weather" is turned on, the prayer city's
/// weather from MET Norway (WeatherService, while the app is in use).
@MainActor
@Observable
final class AppModel {
    /// The one model of the app (the environment's fallback, see AppEnvironment.swift).
    static let shared = AppModel()

    let l10n = Localization()
    let location = LocationService()
    let calculator = PrayerCalculator()
    /// "Sky follows the weather" (resumed and paused with the scene in NearMosqueApp).
    let weather = WeatherService()
    private(set) var db: AppDatabase?
    private(set) var packs: PackManager?
    private(set) var mosques: MosqueRepository?
    private(set) var ask: AskRepository?
    /// Meaning-based library search (vectors built in the background after the libraries install).
    private(set) var semantic: SemanticIndexStore?
    /// Content for languages without bundled content, downloaded when the reader asks.
    let downloads = ContentDownloads()
    private(set) var cities: CityIndex?
    private(set) var ready = false
    private(set) var storageError: String?
    /// Nearest downloaded mosque to the phone (or to the prayer city), for the prayer screen.
    private(set) var nearestMosque: RankedMosque?
    /// Up to three nearest mosques and where they were measured from (shared with the widgets).
    private(set) var nearestMosques: [RankedMosque] = []
    private var nearestCenter: LatLng?
    /// The mosque data covers the last place looked at (so an empty list means none within reach).
    private var nearestCovered = false
    /// Suggested questions in the reader's language, read once per language (not on every settings save).
    private var questionsCache: (lang: String, list: [String])?
    /// Set by a widget tap: the mosques tab opens this mosque's page.
    var requestMosqueId: String?
    private var nearestKey: String?
    /// Set by a widget tap, Siri or a control: the prayer screen opens the Qibla view.
    var requestQibla = false
    private var lastShared: SharedState?
    /// What keeps the alerts that are on from arriving (nil: nothing): the prayer screen and Prayer & Audio say so.
    private(set) var alertBlock: AlertBlock?

    var settings: StoredSettings {
        didSet { save() }
    }

    init() {
        let data = UserDefaults.standard.data(forKey: "settings")
        if let data, let s = try? JSONDecoder().decode(StoredSettings.self, from: data) {
            settings = s
        } else {
            // Unreadable saved settings are kept aside, never silently replaced, so they can still be recovered.
            if let data { UserDefaults.standard.set(data, forKey: "settings.unreadable") }
            settings = StoredSettings()
        }
        ThemeStore.shared.style = settings.widgetLook
    }

    private func save() {
        if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "settings") }
        // One style for everything: the app's accent follows the style that colours the widgets and the island.
        if ThemeStore.shared.style != settings.widgetLook { ThemeStore.shared.style = settings.widgetLook }
        shareWithWidgets()
        Task { await LiveCountdown.refresh(self) }
    }

    /// Gives the widgets and Siri the city, method and language (shared keychain), and refreshes the widgets.
    /// nil when not known: no place looked at yet, or the area's mosques are not on the phone.
    private func sharedMosques() -> [SharedMosque]? {
        guard let c = nearestCenter, nearestCovered else { return nil }
        return nearestMosques.map { r in
            SharedMosque(id: r.id, name: r.mosque.displayName(l10n.language) ?? l10n.t("mosque_unnamed"), meters: r.distanceMeters, bearing: Geo.initialBearing(from: c, to: r.mosque.location))
        }
    }

    private func sharedQuestions() -> [String] {
        let lang = l10n.language
        if let c = questionsCache, c.lang == lang { return c.list }
        let list = ((try? ask?.commonQuestions()) ?? []).compactMap { $0.question[lang] ?? $0.question["en"] }
        if !list.isEmpty { questionsCache = (lang, list) }
        return list
    }

    /// See SharedState.linkToken.
    static let linkToken: String = {
        if let t = UserDefaults.standard.string(forKey: "linkToken") { return t }
        let t = UUID().uuidString
        UserDefaults.standard.set(t, forKey: "linkToken")
        return t
    }()

    /// What the widgets, Siri and the watch get (the onboarding's widget previews draw it too); nil before a place is chosen.
    func widgetState() -> SharedState? {
        // A bell on every prayer something comes at: its bell, or its Adhan.
        let alerts = settings.alerts
        let bells = Set(settings.reminders.map(\.rawValue) + PrayerEvent.prayers.filter { alerts.adhanPlays($0) }.map(\.rawValue)).sorted()
        return settings.location.map {
            SharedState(name: $0.name, latitude: $0.latitude, longitude: $0.longitude, zoneId: $0.zoneId,
                        prayer: settings.prayer, language: l10n.language, hijriAdjustmentDays: settings.prayer.hijriAdjustmentDays,
                        reminders: bells,
                        mosques: sharedMosques(), questions: sharedQuestions(), widgetLook: settings.widgetLook, companion: settings.companion,
                        linkToken: Self.linkToken, iqama: settings.iqama, onlineSearch: settings.onlineSearch)
        }
    }

    func shareWithWidgets() {
        let s = widgetState()
        syncWatch(s)
        guard s != lastShared else { return }
        lastShared = s
        SharedStore.write(s)
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// The same values for the Apple Watch app, with how full the companion is (only sent when something changed).
    private func syncWatch(_ s: SharedState?) {
        #if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
        WatchBridge.shared.send(s, companionLevel: CompanionLog.level(now: Date(), zone: s?.zone ?? .current), marks: CompanionLog.marks)
        #endif
    }

    var packsRoot: URL? { Bundle.main.url(forResource: "packs", withExtension: nil) }

    /// Opens the database and installs built-in packs off the main thread on first launch.
    func start() async {
        guard !ready else { return }
        // Silence turned on in builds 141-142 kept no name for the default one: keep it now, before a change of
        // language could make the notification button run a translated name the user's Shortcut does not have.
        if settings.silence.enabled, settings.silenceShortcut == nil { settings.silenceShortcut = silenceShortcutName }
        let root = packsRoot
        let stopData = Bundle.main.url(forResource: "stopwords", withExtension: "json").flatMap { try? Data(contentsOf: $0) } ?? Data()
        let lexiconData = Bundle.main.url(forResource: "lexicon", withExtension: "json").flatMap { try? Data(contentsOf: $0) } ?? Data()
        do {
            let opened: (AppDatabase, PackManager, CityIndex?) = try await Task.detached(priority: .userInitiated) {
                let db = try AppDatabase.onDisk()
                let packs = PackManager(db: db, bundledRoot: root)
                // Quran, mosques and cities first; the libraries follow in the background (ensureLibraries).
                packs.ensureBuiltins { !ChunkScope.isLibrary($0.id) }
                let tsv = root.flatMap { try? String(contentsOf: $0.appendingPathComponent("cities/cities.tsv"), encoding: .utf8) }
                return (db, packs, tsv.map(CityIndex.parse))
            }.value
            db = opened.0
            packs = opened.1
            cities = opened.2
            mosques = MosqueRepository(db: opened.0)
            if let model = Bundle.main.url(forResource: "model", withExtension: "bin"),
               let support = try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true) {
                semantic = SemanticIndexStore(dir: support.appendingPathComponent("semantic"), db: opened.0, modelURL: model)
            }
            ask = AskRepository(db: opened.0, stopwords: AskRepository.parseStopwords(stopData), lexicon: Lexicon(json: lexiconData), semantic: semantic)
        } catch {
            storageError = error.localizedDescription
        }
        ready = true
        ensureLibraries(for: l10n.language)
        #if DEBUG
        applyDemoLaunchArguments()
        #endif
        if settings.followDevice, location.isAuthorized {
            lastFollowFix = Date()
            await useDeviceLocation(silent: true)
        }
        await rescheduleReminders()
        // Today's questions are ready now; the watch gets them with the rest.
        shareWithWidgets()
    }

    /// Saved library items (chunk ids), kept on this device only.
    var bookmarks: Set<String> = Set(UserDefaults.standard.stringArray(forKey: "libraryBookmarks") ?? []) {
        didSet { UserDefaults.standard.set(Array(bookmarks).sorted(), forKey: "libraryBookmarks") }
    }

    func setBookmark(_ id: String, _ on: Bool) {
        if on { bookmarks.insert(id) } else { bookmarks.remove(id) }
    }

    private var librariesTask: Task<Void, Never>?
    private(set) var installingLibraries = false

    /// Installs the bundled library packs for an interface language (ChunkScope.libraryLanguages) in the
    /// background. The large Arabic fatwa pack takes a while to index; Ask works meanwhile.
    func ensureLibraries(for lang: String) {
        guard let packs else { return }
        let previous = librariesTask
        installingLibraries = true
        librariesTask = Task {
            await previous?.value
            let wanted = ChunkScope.libraryLanguages(lang)
            let semantic = semantic
            await Task.detached(priority: .utility) {
                packs.ensureBuiltins { ChunkScope.isLibrary($0.id) && wanted.contains(ChunkScope.libraryLanguage($0.id)) }
                // Meaning-based search: vectors for the installed libraries, built once per install.
                try? semantic?.ensure()
            }.value
            installingLibraries = false
        }
    }

    #if DEBUG
    /// Simulator screenshots in CI: `-demoCity "Cape Town"` picks a city and skips onboarding;
    /// `-demoOnboarding YES` shows the welcome flow (OnboardingView reads its own step options), and
    /// `-demoOnboardingWatch installed|available|none` stands in for the paired Apple Watch. Debug builds only.
    private func applyDemoLaunchArguments() {
        let d = UserDefaults.standard
        if let name = d.string(forKey: "demoCity"), let city = cities?.search(name).first {
            choose(city: city)
            settings.onboarded = true
        }
        if d.bool(forKey: "demoOnboarding") { settings.onboarded = false }
        #if canImport(WatchConnectivity) && !targetEnvironment(macCatalyst) && !os(visionOS)
        switch d.string(forKey: "demoOnboardingWatch") {
        case "installed": WatchPairing.shared.state = .paired(appInstalled: true)
        case "available": WatchPairing.shared.state = .paired(appInstalled: false)
        case "none": WatchPairing.shared.state = .unpaired
        default: break
        }
        #endif
    }

    /// Simulator screenshots: `-demoWeather rain` (clear, partly_cloudy, cloudy, rain, thunderstorm, fog, snow, or a
    /// MET symbol code) draws that weather at 18 °C and 4 m/s, with no network and whatever the switch says.
    private static let demoWeather: WeatherSymbol? = UserDefaults.standard.string(forKey: "demoWeather").flatMap(WeatherService.demoSymbol)
    #endif

    // MARK: Prayer location

    func choose(city: City) {
        let name = city.displayName(l10n.language)
        settings.location = PrayerLocation(name: name, latitude: city.location.latitude, longitude: city.location.longitude,
                                           zoneId: city.zoneId, countryCode: city.countryCode, source: .city)
        settings.followDevice = false
        if !settings.methodChosenByUser { settings.prayer.method = PrayerCalculator.suggestedMethod(city.countryCode) }
        Task { await rescheduleReminders() }
    }

    enum LocateOutcome { case ok, denied, noFix }

    /// Uses a fresh fix (recent fix as fallback); never substitutes a default city.
    @discardableResult
    func useDeviceLocation(silent: Bool = false) async -> LocateOutcome {
        let before = settings.location
        guard let fix = await location.currentPosition() else { return location.isDenied ? .denied : .noFix }
        // A city picked while the fix was coming is kept (and followDevice stays off).
        guard settings.location == before else { return .ok }
        let resolver = cities.map(TimeZoneResolver.init)
        let z = resolver?.resolve(fix.location, deviceZone: .current)
        // On its own (at launch, back on the screen): a move of under about 2 km in the same time zone changes no
        // prayer time, so nothing is saved or rebuilt for it.
        if silent, settings.followDevice, let b = before, b.source == .device, b.zoneId == (z?.zone ?? .current).identifier,
           Geo.distanceMeters(b.location, fix.location) < 2_000 { return .ok }
        let near = (z?.distanceMeters ?? .infinity) < 30_000 ? z?.nearestCity : nil
        settings.location = PrayerLocation(
            name: near?.displayName(l10n.language) ?? l10n.t("location_current"),
            // Kept to about 100 m: plenty for prayer times and the Qibla, and the saved settings never hold the
            // exact position (they are in backups).
            latitude: (fix.location.latitude * 1000).rounded() / 1000, longitude: (fix.location.longitude * 1000).rounded() / 1000,
            zoneId: (z?.zone ?? .current).identifier, countryCode: z?.nearestCity?.countryCode, source: .device,
            zoneConfirmed: !(z?.needsConfirmation ?? true),
            cityLatitude: near?.location.latitude, cityLongitude: near?.location.longitude
        )
        settings.followDevice = true
        if !settings.methodChosenByUser { settings.prayer.method = PrayerCalculator.suggestedMethod(z?.nearestCity?.countryCode) }
        await rescheduleReminders()
        return .ok
    }

    func setMethod(_ m: PrayerMethod) {
        settings.prayer.method = m
        settings.methodChosenByUser = true
        Task { await rescheduleReminders() }
    }

    /// When Asr begins: the shadow equal to the object (Shafi'i, Maliki, Hanbali) or twice it (Hanafi).
    func setMadhab(_ m: AsrMadhab) {
        settings.prayer.madhab = m
        Task { await rescheduleReminders() }
    }

    /// Near the poles (the polar card's button): times from the nearest latitude that has them, with their alerts.
    func setPolarRule(_ r: PolarRule) {
        settings.prayer.polarRule = r
        Task { await rescheduleReminders() }
    }

    func setReminder(_ e: PrayerEvent, _ on: Bool) async {
        if on, !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
        if on { settings.reminders.insert(e) } else { settings.reminders.remove(e) }
        shareWithWidgets()
        await rescheduleReminders()
    }

    /// The bell on a prayer's row. On: the prayer's bell and its own Adhan switch (the Adhan plays where it is on for
    /// all prayers); off: neither, so nothing comes at that prayer's time. One save, one rebuild.
    func setPrayerCall(_ e: PrayerEvent, _ on: Bool) async {
        if on, !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
        let old = settings.alerts
        var s = settings
        if on { s.reminders.insert(e) } else { s.reminders.remove(e) }
        if e.isPrayer {
            var sounds = s.alerts.sounds(e)
            sounds.adhan = on
            s.alerts.setSounds(e, sounds)
        }
        settings = s
        shareWithWidgets()
        await askForAlarms(s.alerts, after: old)
        await rescheduleReminders()
    }

    /// `askPermission`: false in the onboarding, which asks for notifications (and alarms) once, on its privacy step.
    func setAlerts(_ a: AlertSettings, askPermission: Bool = true) {
        let old = settings.alerts
        settings.alerts = a
        Task {
            if askPermission, !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
            if askPermission { await askForAlarms(a, after: old) }
            await rescheduleReminders()
        }
    }

    /// An Adhan (or the Fajr alarm) just turned on: alarms are asked for where the device has them, so it rings even
    /// on silent. The system asks only once; without them it stays a notification.
    private func askForAlarms(_ a: AlertSettings, after old: AlertSettings) async {
        let turnedOn = PrayerEvent.prayers.contains { a.adhanPlays($0) && !old.adhanPlays($0) }
            || (old.fajrAlarmMinutesBefore == nil && a.fajrAlarmMinutesBefore != nil)
        guard turnedOn, PrayerAlarms.permission == .notDetermined else { return }
        await PrayerAlarms.requestAuthorization()
    }

    /// Something is on that comes as a notification: a bell, an Adhan or a reminder (the iqama alert and the silence
    /// button come only with these).
    var wantsNotifications: Bool {
        let a = settings.alerts
        return !settings.reminders.isEmpty || PrayerEvent.prayers.contains { a.adhanPlays($0) } || a.before10 || a.before5 || a.near
            || a.friday || a.ramadan || a.kahf || a.salawat || a.quran || a.bedtimeMinutes != nil || a.fajrAlarmMinutesBefore != nil
    }

    /// An Adhan or the Fajr alarm is on: they ring as alarms where the device has them.
    var wantsAlarms: Bool {
        let a = settings.alerts
        return PrayerEvent.prayers.contains { a.adhanPlays($0) } || a.fajrAlarmMinutesBefore != nil
    }

    /// Notifications and alarms allowed, as the last rebuild found them: a change since then rebuilds the reminders.
    @ObservationIgnored private var rebuiltAccess: [Bool]?

    /// Reads again whether notifications (and, for the Adhan, alarms) are allowed for what is on. When that changed
    /// since the last rebuild (turned on or off in Settings meanwhile), the reminders are rebuilt with it and this
    /// returns true once they are, so whoever looks next (Check my alerts) sees them.
    @discardableResult
    func refreshAlertAccess() async -> Bool {
        let access = await readAlertAccess()
        // Before the first rebuild, start() does it.
        guard ready, let rebuilt = rebuiltAccess, rebuilt != access else { return false }
        await rescheduleReminders()
        return true
    }

    /// The banner for what is on, read again; returns whether notifications and alarms are allowed.
    private func readAlertAccess() async -> [Bool] {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        let alarms = PrayerAlarms.permission
        var block: AlertBlock?
        if wantsNotifications, status == .notDetermined {
            block = .notificationsUndecided
        } else if wantsNotifications, status == .denied {
            block = .notificationsDenied
        } else if wantsAlarms, alarms == .notDetermined {
            block = .alarmsUndecided
        } else if wantsAlarms, alarms == .denied {
            block = .alarmsDenied
        }
        if block != alertBlock { alertBlock = block }
        return [status == .authorized || status == .provisional, alarms == .authorized]
    }

    /// The banner's Allow: the system asks (this once), then the reminders are rebuilt with the answer.
    func askAlertAccess() async {
        switch alertBlock {
        case .notificationsUndecided?: _ = await Reminders.requestAuthorization()
        case .alarmsUndecided?: await PrayerAlarms.requestAuthorization()
        default: return
        }
        await rescheduleReminders()
    }

    /// The onboarding's "Notify me at prayer times": a bell at each of the five prayers, unless some are chosen already
    /// (nothing is ever turned off here).
    func enablePrayerBells() async {
        if settings.reminders.isEmpty { settings.reminders = Set(PrayerEvent.prayers) }
        shareWithWidgets()
        await rescheduleReminders()
    }

    func setIqama(_ q: IqamaSettings) {
        settings.iqama = q
        shareWithWidgets()
        Task {
            await LiveCountdown.refresh(self)
            await rescheduleReminders()
        }
    }

    /// `askPermission` as in setAlerts.
    func setSilence(_ s: SilenceSettings, askPermission: Bool = true) {
        // On: the Shortcut's name in use is kept from now on (one save for both).
        var copy = settings
        copy.silence = s
        if s.enabled { copy.silenceShortcut = silenceShortcutName }
        settings = copy
        Task {
            // The button that runs the Shortcut is on the notifications.
            if askPermission, s.enabled, !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
            await rescheduleReminders()
        }
    }

    /// Saves the Shortcut's name as typed. An empty field changes nothing (the saved name stays, or the default
    /// until one is saved), so a field not filled in yet can never erase a name.
    func setSilenceShortcut(_ typed: String) {
        let name = SilenceSettings.storedShortcutName(typed, current: settings.silenceShortcut)
        if name != settings.silenceShortcut { settings.silenceShortcut = name }
    }

    /// The Shortcut the notifications run.
    var silenceShortcutName: String {
        SilenceSettings.shortcutName(settings.silenceShortcut, default: l10n.t("silence_shortcut_name"))
    }

    /// The Shortcut's name about to be run ("Try the shortcut", the notification button), saved as it is: a
    /// default name then stays when the app's language changes, and a name the user typed is never replaced.
    func keepSilenceShortcutName() -> String {
        let name = silenceShortcutName
        if settings.silenceShortcut != name { settings.silenceShortcut = name }
        return name
    }

    // MARK: Rebuilding the reminders

    /// One rebuild at a time: each waits for the one before it, and calls made meanwhile share one rebuild after it,
    /// with the settings as they are then (two rebuilds interleaved used to keep alerts from old settings).
    @ObservationIgnored private var rescheduleRun: (ticket: Int, task: Task<Void, Never>)?
    @ObservationIgnored private var rescheduleTickets = 0
    @ObservationIgnored private var rescheduledTicket = 0
    /// When the reminders were last rebuilt: coming back to the screen tops them up when that is over an hour ago.
    @ObservationIgnored private var lastReschedule: Date?
    /// With "Use my location": when the app last asked for a fix on its own.
    @ObservationIgnored private var lastFollowFix: Date?

    func rescheduleReminders() async {
        rescheduleTickets += 1
        let ticket = rescheduleTickets
        let previous = rescheduleRun?.task
        let task = Task { @MainActor [self] in
            await previous?.value
            // A newer call is waiting: it reads newer settings, so this one leaves the rebuild to it.
            guard ticket == rescheduleTickets, !Task.isCancelled else { return }
            await performReschedule()
            rescheduledTicket = ticket
        }
        rescheduleRun = (ticket: ticket, task: task)
        // Passed over for a newer call: done once that one has run (the background refresh completes after a rebuild).
        var waiting = (ticket: ticket, task: task)
        while true {
            await waiting.task.value
            guard rescheduledTicket < ticket, let latest = rescheduleRun, latest.ticket > waiting.ticket else { break }
            waiting = latest
        }
    }

    /// The background refresh ran out of time: a rebuild not yet committed leaves the reminders as they were.
    func cancelRescheduling() {
        rescheduleRun?.task.cancel()
    }

    /// Coming back to the screen: the Lock Screen countdown moves on; with "Use my location" a new fix (the place and
    /// the reminders change only when the phone has moved far enough to matter); the reminders are topped up when the
    /// last rebuild is over an hour old, or notifications or alarms were allowed (or turned off) meanwhile.
    func foregroundRefresh() async {
        await LiveCountdown.refresh(self)
        // Before then, start() does the rest.
        guard ready else { return }
        await refreshFollowedLocation()
        // A change of what is allowed has rebuilt them already.
        if !(await refreshAlertAccess()), Date().timeIntervalSince(lastReschedule ?? .distantPast) > 3600 { await rescheduleReminders() }
    }

    /// Midnight, or the clock or the time zone changed (delivered when the app resumes, and to a Mac app left open for
    /// days): the place again with "Use my location", and the reminders rebuilt.
    func timeChanged() async {
        guard ready else { return }
        let asked = Date()
        await refreshFollowedLocation()
        // Unless the new place has just rebuilt them.
        if (lastReschedule ?? .distantPast) < asked { await rescheduleReminders() }
        await LiveCountdown.refresh(self)
    }

    /// With "Use my location": a new fix at most every half hour, or at once when the phone's time zone is no longer
    /// the place's (after a flight).
    func refreshFollowedLocation() async {
        guard ready, settings.followDevice, location.isAuthorized else { return }
        NSTimeZone.resetSystemTimeZone()
        let zoneMoved = settings.location.map { !TimeZoneResolver.sameRules($0.zone, .current) } ?? true
        if !zoneMoved, Date().timeIntervalSince(lastFollowFix ?? .distantPast) < 30 * 60 { return }
        lastFollowFix = Date()
        await useDeviceLocation(silent: true)
    }

    private func performReschedule() async {
        ReminderRefresh.submit()
        // Read first: a change while the rebuild runs is caught at the next look.
        let access = await readAlertAccess()
        let lang = l10n.language
        let ask = ask
        let mosqueLine = nearestMosque.map { l10n.t("nearest_mosque_chip", Format.distance($0.distanceMeters, l10n: l10n)) }
        await Reminders.reschedule(location: settings.location, settings: settings.prayer, enabled: settings.reminders, alerts: settings.alerts,
                                   iqama: settings.iqama, silence: settings.silence, l10n: l10n, mosqueLine: mosqueLine) { id in
            // The Qur'an text on the phone; English readers also get the translation in the pack.
            guard let c = (try? ask?.resolve([id]))?.first?.chunk else { return nil }
            let en = lang == "en" ? c.allTranslations.first { $0.lang == "en" }?.text : nil
            return [c.original.text, en].compactMap { $0 }.joined(separator: "\n")
        }
        if !Task.isCancelled {
            lastReschedule = Date()
            rebuiltAccess = access
        }
    }

    /// Sky for the prayer period in progress (night when no location is chosen yet).
    func skyPeriod(now: Date) -> SkyPeriod {
        guard settings.location != nil else { return .night }
        return PrayerSnapshot(model: self, now: now).sky
    }

    /// The sky before a place is chosen (the onboarding's first steps): the period now at the largest bundled city in
    /// the device's time zone, for the colours only (never saved or shown); night when there is none.
    func previewPeriod(now: Date) -> SkyPeriod {
        let zoneId = TimeZone.current.identifier
        if previewCity?.zone != zoneId, let cities { previewCity = (zoneId, cities.largest(inZone: zoneId)) }
        guard let city = previewCity?.city, let zone = TimeZone(identifier: city.zoneId) else { return .night }
        let prayer = PrayerSettings(method: PrayerCalculator.suggestedMethod(city.countryCode))
        let today = CivilDate.of(now, in: zone)
        let days = (-1...1).map { calculator.schedule(city.location, date: today.adding(days: $0), zone: zone, settings: prayer) }
        let current = PrayerEvent.allCases.last { (days[1][$0] ?? .distantFuture) <= now } ?? (days[0][.isha] != nil ? .isha : nil)
        return SkyPeriod.at(now, today: days[1], current: current)
    }

    /// The prayer city's weather to draw on the sky now; nil (the normal sky) when the switch is off, no city is
    /// chosen (or, with "Use my location", none is within 30 km), or there is no recent data (offline, older than
    /// about 3 hours, another city).
    func skyWeather(now: Date) -> WeatherNow? {
        #if DEBUG
        if let demo = Self.demoWeather { return WeatherNow(symbol: demo, temperatureC: 18, windMs: 4, at: now) }
        #endif
        guard settings.skyWeather, let place = settings.location?.weatherLocation else { return nil }
        return weather.now(for: place, at: now)
    }

    // MARK: Prayer schedule (cached per location/settings/date; countdown recomputed from instants)

    // Not observed: a cache filled during view evaluation must not trigger re-renders.
    @ObservationIgnored private var previewCity: (zone: String, city: City?)?
    @ObservationIgnored private var cacheKey: String?
    @ObservationIgnored private var cached: [DaySchedule] = []

    /// Looks up the nearest downloaded mosque when the phone (or the prayer city) has moved ~100 m.
    func refreshNearestMosque() {
        guard let repo = mosques, let center = location.position?.location ?? settings.location?.location else { return }
        let key = "\((center.latitude * 1000).rounded())/\((center.longitude * 1000).rounded())"
        guard key != nearestKey else { return }
        nearestKey = key
        let lang = l10n.language
        let useOnline = settings.onlineSearch
        Task {
            let r = try? await Task.detached { try repo.nearest(center, radiusMeters: 25_000, lang: lang) }.value
            switch r {
            case let .found(list, _):
                nearestMosque = list.first; nearestMosques = Array(list.prefix(3)); nearestCovered = true
            case .noRecordsInCoverage:
                nearestMosque = nil; nearestMosques = []; nearestCovered = true
            default:
                nearestMosque = nil; nearestMosques = []; nearestCovered = false
            }
            // Nothing on the phone for this area: the live Apple Maps results (when online search is on), as on
            // the Nearest Mosque tab, so the Qibla ring can still show the nearest mosque.
            if nearestMosque == nil, useOnline, let live = await AppleMosqueSearch.search(center: center, radiusMeters: 10_000), !live.isEmpty {
                let merged = OnlineMosques.merge(center: center, offline: [], online: live, radiusMeters: 25_000)
                if !merged.isEmpty { nearestMosque = merged.first; nearestMosques = Array(merged.prefix(3)); nearestCovered = true }
            }
            nearestCenter = center
            shareWithWidgets()
        }
    }

    func days(now: Date) -> [DaySchedule] {
        guard let loc = settings.location else { return [] }
        let today = CivilDate.of(now, in: loc.zone)
        let key = "\(loc.latitude),\(loc.longitude),\(loc.zoneId),\(today),\(String(describing: settings.prayer))"
        if key != cacheKey {
            cached = (-1...1).map { calculator.schedule(loc.location, date: today.adding(days: $0), zone: loc.zone, settings: settings.prayer) }
            cacheKey = key
        }
        return cached
    }
}
