import CoreLocation
import Foundation
import NMCore
import NMData
import SwiftUI
import WatchConnectivity
import WatchKit
import WidgetKit

/// Where the watch's prayer times come from: the city and method chosen on the iPhone, or the watch's own location.
enum WatchSource: String, Codable { case phone, watch }

/// The watch's own place, when it locates itself.
struct WatchPlace: Codable, Equatable {
    var name: String
    var latitude: Double
    var longitude: Double
    var zoneId: String
    var countryCode: String?
}

/// Settings kept on the watch (UserDefaults). Missing keys decode to defaults so older saves keep working.
struct WatchSettings: Codable, Equatable {
    var source: WatchSource = .phone
    var place: WatchPlace?
    var prayer = PrayerSettings()
    var methodChosenByUser = false
    /// The watch schedules its own reminders (for when the iPhone is left behind).
    var ownReminders = false
    var reminders: [String] = PrayerEvent.prayers.map(\.rawValue)
    var qiblaHaptic = true
    var tasbihHaptic = true
    /// Live results from Apple Maps added to the built-in mosque data (approximate location only).
    var onlineSearch = true

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func get<T: Decodable>(_ type: T.Type, _ key: CodingKeys) -> T? { (try? c.decodeIfPresent(type, forKey: key)) ?? nil }
        source = get(WatchSource.self, .source) ?? .phone
        place = get(WatchPlace.self, .place)
        prayer = get(PrayerSettings.self, .prayer) ?? PrayerSettings()
        methodChosenByUser = get(Bool.self, .methodChosenByUser) ?? false
        ownReminders = get(Bool.self, .ownReminders) ?? false
        reminders = get([String].self, .reminders) ?? PrayerEvent.prayers.map(\.rawValue)
        qiblaHaptic = get(Bool.self, .qiblaHaptic) ?? true
        tasbihHaptic = get(Bool.self, .tasbihHaptic) ?? true
        onlineSearch = get(Bool.self, .onlineSearch) ?? true
    }
}

/// The watch app's state. Prayer times are calculated on the watch, from what the iPhone sent (WatchConnectivity)
/// or from the watch's own location; the complications read the result from the watch's keychain group.
@MainActor
@Observable
final class WatchModel {
    static let shared = WatchModel()

    let l10n = Localization()
    let calculator = PrayerCalculator()
    private(set) var cities: CityIndex?
    private(set) var mosques: MosqueRepository?
    private(set) var askEngine: Retriever?
    private(set) var ready = false
    /// The latest values from the iPhone app (nil until it has sent them).
    private(set) var phone: SharedState?
    var extras = WatchExtras.read()
    var screen: WatchScreen = .prayer
    var showSettings = false
    /// A question to ask when the Ask page opens (debug screenshots).
    var pendingAsk: String?

    var settings: WatchSettings {
        didSet {
            guard settings != oldValue else { return }
            if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "watchSettings") }
            publish()
        }
    }

    init() {
        if let data = UserDefaults.standard.data(forKey: "watchSettings"), let s = try? JSONDecoder().decode(WatchSettings.self, from: data) {
            settings = s
        } else {
            settings = WatchSettings()
        }
        if let data = UserDefaults.standard.data(forKey: "phoneState") { phone = try? JSONDecoder().decode(SharedState.self, from: data) }
    }

    /// What the screens and complications show.
    var state: SharedState? {
        switch settings.source {
        case .phone:
            guard var s = phone else { return nil }
            if settings.ownReminders { s.reminders = settings.reminders }
            return s
        case .watch:
            guard let p = settings.place else { return nil }
            return SharedState(name: p.name, latitude: p.latitude, longitude: p.longitude, zoneId: p.zoneId, prayer: settings.prayer,
                               language: l10n.language, hijriAdjustmentDays: phone?.hijriAdjustmentDays ?? 0,
                               reminders: settings.ownReminders ? settings.reminders : phone?.reminders,
                               mosques: nil, questions: phone?.questions, widgetLook: phone?.widgetLook, companion: phone?.companion)
        }
    }

    var accent: Color { WatchColors.accent(state) }

    // MARK: Start

    /// Reads the city table, the built-in mosques and the common answers off the main thread, and listens to the iPhone.
    func start() async {
        guard !ready else { return }
        WatchLink.shared.start()
        let root = Bundle.main.resourceURL
        let opened: (CityIndex?, MosqueRepository?, Retriever?) = await Task.detached(priority: .userInitiated) {
            let tsv = root.flatMap { try? String(contentsOf: $0.appendingPathComponent("cities/cities.tsv"), encoding: .utf8) }
            var repo: MosqueRepository?
            if let db = try? AppDatabase.inMemory() {
                PackManager(db: db, bundledRoot: root, defaults: UserDefaults(suiteName: "watch-packs") ?? .standard).ensureBuiltins()
                repo = MosqueRepository(db: db)
            }
            // The common answers only (the books stay on the iPhone): no passages to search.
            var engine: Retriever?
            if let url = Bundle.main.url(forResource: "common-questions", withExtension: "json"), let data = try? Data(contentsOf: url),
               let file = try? JSONDecoder().decode(CommonQuestionsFile.self, from: data) {
                let stopData = Bundle.main.url(forResource: "stopwords", withExtension: "json").flatMap { try? Data(contentsOf: $0) } ?? Data()
                engine = Retriever(store: InMemoryChunkStore([]), commonQuestions: file.questions, stopwords: AskRepository.parseStopwords(stopData))
            }
            return (tsv.map(CityIndex.parse), repo, engine)
        }.value
        cities = opened.0
        mosques = opened.1
        askEngine = opened.2
        ready = true
        #if DEBUG
        applyDemo()
        #endif
        if settings.source == .watch { _ = await useWatchLocation() }
        publish()
        if let raw = UserDefaults.standard.string(forKey: "openWatchScreen"), let s = WatchScreen(rawValue: raw) {
            screen = s
            UserDefaults.standard.removeObject(forKey: "openWatchScreen")
        }
    }

    // MARK: From the iPhone

    /// The iPhone sent new values (application context).
    func received(_ state: SharedState?, level: Double?) {
        if let state {
            phone = state
            if let data = try? JSONEncoder().encode(state) { UserDefaults.standard.set(data, forKey: "phoneState") }
            // The watch speaks the language chosen on the iPhone.
            if l10n.language != state.language { l10n.override = state.language }
            if !settings.methodChosenByUser { settings.prayer = state.prayer }
        }
        if let level { extras.companionLevel = level }
        publish()
    }

    // MARK: Location

    enum LocateOutcome { case ok, denied, noFix }

    /// The watch's own location, named after the nearest known city; kept to about 100 m.
    func useWatchLocation() async -> LocateOutcome {
        guard let fix = await WatchLocator.shared.fix() else { return WatchLocator.shared.denied ? .denied : .noFix }
        let z = cities.map(TimeZoneResolver.init)?.resolve(fix, deviceZone: .current)
        let near = (z?.distanceMeters ?? .infinity) < 30_000 ? z?.nearestCity : nil
        settings.place = WatchPlace(name: near?.displayName(l10n.language) ?? l10n.t("location_current"),
                                    latitude: (fix.latitude * 1000).rounded() / 1000, longitude: (fix.longitude * 1000).rounded() / 1000,
                                    zoneId: (z?.zone ?? .current).identifier, countryCode: z?.nearestCity?.countryCode)
        if !settings.methodChosenByUser { settings.prayer.method = PrayerCalculator.suggestedMethod(z?.nearestCity?.countryCode) }
        settings.source = .watch
        return .ok
    }

    func setMethod(_ m: PrayerMethod) {
        settings.prayer.method = m
        settings.methodChosenByUser = true
    }

    // MARK: Schedule

    @ObservationIgnored private var cacheKey: String?
    @ObservationIgnored private var cached: [DaySchedule] = []

    /// Yesterday, today and tomorrow (cached per day and settings).
    func days(now: Date) -> [DaySchedule] {
        guard let s = state, let loc = s.location else { return [] }
        let today = CivilDate.of(now, in: s.zone)
        let key = "\(s.latitude),\(s.longitude),\(s.zoneId),\(today),\(String(describing: s.prayer))"
        if key != cacheKey {
            cached = (-1...1).map { calculator.schedule(loc, date: today.adding(days: $0), zone: s.zone, settings: s.prayer) }
            cacheKey = key
        }
        return cached
    }

    // MARK: Tasbih

    func countTasbih() {
        extras.tasbih += 1
        let round = extras.tasbih % 33 == 0
        if settings.tasbihHaptic { WKInterfaceDevice.current().play(round ? .success : .click) }
        WatchExtras.write(extras)
        // The complication is redrawn at each round (and when the app leaves the screen), not on every count.
        if round { WidgetCenter.shared.reloadTimelines(ofKind: "WatchTasbih") }
    }

    func resetTasbih() {
        extras.tasbih = 0
        WatchExtras.write(extras)
        WidgetCenter.shared.reloadTimelines(ofKind: "WatchTasbih")
    }

    // MARK: Complications and reminders

    @ObservationIgnored private var lastPublished: SharedState?
    @ObservationIgnored private var lastExtras: WatchExtras?

    /// Gives the complications the current values and sets the watch's own reminders.
    func publish() {
        let s = state
        if s != lastPublished || extras != lastExtras {
            lastPublished = s
            lastExtras = extras
            SharedStore.write(s)
            WatchExtras.write(extras)
            WidgetCenter.shared.reloadAllTimelines()
        }
        let events = settings.reminders.compactMap(PrayerEvent.init(rawValue:))
        let own = settings.ownReminders
        Task { await WatchReminders.reschedule(state: s, events: events, enabled: own) }
        scheduleRefresh()
    }

    /// Wakes the app now and then (about every six hours) to keep the reminders and complications going.
    func scheduleRefresh() {
        WKApplication.shared().scheduleBackgroundRefresh(withPreferredDate: Date().addingTimeInterval(6 * 3600), userInfo: "refresh" as NSString) { _ in }
    }

    func backgroundRefresh() async {
        if settings.source == .watch { _ = await useWatchLocation() }
        lastPublished = nil
        publish()
    }

    #if DEBUG
    /// Simulator screenshots in CI: `-demoCity "Makkah"` uses that city as if the iPhone had sent it,
    /// `-demoScreen qibla` opens a screen, `-demoTasbih 21` sets the count. Debug builds only.
    private func applyDemo() {
        let d = UserDefaults.standard
        if let name = d.string(forKey: "demoCity"), let city = cities?.search(name).first {
            let lang = d.string(forKey: "demoLanguage") ?? l10n.language
            if l10n.language != lang { l10n.override = lang }
            var prayer = PrayerSettings(method: PrayerCalculator.suggestedMethod(city.countryCode))
            prayer.hijriAdjustmentDays = 0
            let questions = askEngine.map { _ in demoQuestions(lang) } ?? []
            received(SharedState(name: city.displayName(lang), latitude: city.location.latitude, longitude: city.location.longitude,
                                 zoneId: city.zoneId, prayer: prayer, language: lang, hijriAdjustmentDays: 0,
                                 reminders: ["fajr", "dhuhr", "maghrib"],
                                 mosques: [SharedMosque(id: "demo-1", name: l10n.t("category_mosque"), meters: 450, bearing: 40)],
                                 questions: questions, widgetLook: d.string(forKey: "demoStyle") ?? "cream", companion: "lantern"), level: 0.7)
            settings.source = .phone
        }
        if let s = d.string(forKey: "demoScreen").flatMap(WatchScreen.init(rawValue:)) { screen = s }
        if d.bool(forKey: "demoSettings") { showSettings = true }
        if d.object(forKey: "demoTasbih") != nil { extras.tasbih = d.integer(forKey: "demoTasbih") }
        if let id = d.string(forKey: "demoAskId"), let url = Bundle.main.url(forResource: "common-questions", withExtension: "json"),
           let data = try? Data(contentsOf: url), let file = try? JSONDecoder().decode(CommonQuestionsFile.self, from: data),
           let q = file.questions.first(where: { $0.id == id }) {
            pendingAsk = q.question[l10n.language] ?? q.question["en"]
        }
    }

    private func demoQuestions(_ lang: String) -> [String] {
        guard let url = Bundle.main.url(forResource: "common-questions", withExtension: "json"), let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(CommonQuestionsFile.self, from: data) else { return [] }
        return file.questions.compactMap { $0.question[lang] ?? $0.question["en"] }
    }
    #endif
}

/// Receives the iPhone app's values (WatchConnectivity application context).
final class WatchLink: NSObject, WCSessionDelegate {
    static let shared = WatchLink()

    func start() {
        guard WCSession.isSupported(), WCSession.default.delegate == nil else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    private func apply(_ ctx: [String: Any]) {
        let state = (ctx["state"] as? Data).flatMap { try? JSONDecoder().decode(SharedState.self, from: $0) }
        let level = ctx["level"] as? Double
        Task { @MainActor in WatchModel.shared.received(state, level: level) }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated, !session.receivedApplicationContext.isEmpty { apply(session.receivedApplicationContext) }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        apply(applicationContext)
    }
}

/// One location fix for "this watch's location".
@MainActor
final class WatchLocator: NSObject, CLLocationManagerDelegate {
    static let shared = WatchLocator()
    private let manager = CLLocationManager()
    private var authCont: CheckedContinuation<Void, Never>?
    private var fixCont: CheckedContinuation<CLLocation?, Never>?
    private(set) var denied = false
    /// The last fix this session, for measuring distances to mosques (never saved).
    private(set) var last: LatLng?

    func fix() async -> LatLng? {
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        if manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { c in
                authCont = c
                manager.requestWhenInUseAuthorization()
            }
        }
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { denied = status == .denied || status == .restricted; return nil }
        denied = false
        let l: CLLocation? = await withCheckedContinuation { c in
            fixCont = c
            manager.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(20))
                self.finish(nil)
            }
        }
        last = (l ?? manager.location).flatMap { LatLng($0.coordinate.latitude, $0.coordinate.longitude) }
        return last
    }

    private func finish(_ l: CLLocation?) {
        fixCont?.resume(returning: l)
        fixCont = nil
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let status = m.authorizationStatus
        Task { @MainActor in
            guard status != .notDetermined else { return }
            self.authCont?.resume()
            self.authCont = nil
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in self.finish(l) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }
}
