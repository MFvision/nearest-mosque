import Foundation
import NMCore
import NMData
import SwiftUI
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

    var location: LatLng { LatLng(latitude, longitude) ?? Qibla.kaaba }
    var zone: TimeZone { TimeZone(identifier: zoneId) ?? .current }
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
    var onboarded = false
    /// Live mosque results (Apple Maps search) added to downloaded data; disclosed in onboarding and Settings.
    var onlineSearch = true

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        location = try c.decodeIfPresent(PrayerLocation.self, forKey: .location)
        followDevice = try c.decodeIfPresent(Bool.self, forKey: .followDevice) ?? false
        prayer = try c.decodeIfPresent(PrayerSettings.self, forKey: .prayer) ?? PrayerSettings()
        methodChosenByUser = try c.decodeIfPresent(Bool.self, forKey: .methodChosenByUser) ?? false
        reminders = try c.decodeIfPresent(Set<PrayerEvent>.self, forKey: .reminders) ?? []
        alerts = (try? c.decodeIfPresent(AlertSettings.self, forKey: .alerts)) ?? AlertSettings()
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? false
        onlineSearch = try c.decodeIfPresent(Bool.self, forKey: .onlineSearch) ?? true
    }
}

/// App-wide state and services. The UI reads computed values only from local storage and
/// deterministic services; nothing here performs network requests.
@MainActor
@Observable
final class AppModel {
    let l10n = Localization()
    let location = LocationService()
    let calculator = PrayerCalculator()
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

    var settings: StoredSettings {
        didSet { save() }
    }

    init() {
        if let data = UserDefaults.standard.data(forKey: "settings"), let s = try? JSONDecoder().decode(StoredSettings.self, from: data) {
            settings = s
        } else {
            settings = StoredSettings()
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "settings") }
        shareWithWidgets()
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

    func shareWithWidgets() {
        let s = settings.location.map {
            SharedState(name: $0.name, latitude: $0.latitude, longitude: $0.longitude, zoneId: $0.zoneId,
                        prayer: settings.prayer, language: l10n.language, hijriAdjustmentDays: settings.prayer.hijriAdjustmentDays,
                        reminders: settings.reminders.map(\.rawValue).sorted(),
                        mosques: sharedMosques(), questions: sharedQuestions())
        }
        guard s != lastShared else { return }
        lastShared = s
        SharedStore.write(s)
        WidgetCenter.shared.reloadAllTimelines()
    }

    var packsRoot: URL? { Bundle.main.url(forResource: "packs", withExtension: nil) }

    /// Opens the database and installs built-in packs off the main thread on first launch.
    func start() async {
        guard !ready else { return }
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
        if settings.followDevice, location.isAuthorized { await useDeviceLocation(silent: true) }
        await rescheduleReminders()
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
    /// `-demoOnboarding YES` shows the tour. Debug builds only.
    private func applyDemoLaunchArguments() {
        let d = UserDefaults.standard
        if let name = d.string(forKey: "demoCity"), let city = cities?.search(name).first {
            choose(city: city)
            settings.onboarded = true
        }
        if d.bool(forKey: "demoOnboarding") { settings.onboarded = false }
    }
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
        guard let fix = await location.currentPosition() else { return location.isDenied ? .denied : .noFix }
        let resolver = cities.map(TimeZoneResolver.init)
        let z = resolver?.resolve(fix.location, deviceZone: .current)
        let near = (z?.distanceMeters ?? .infinity) < 30_000 ? z?.nearestCity : nil
        settings.location = PrayerLocation(
            name: near?.displayName(l10n.language) ?? l10n.t("location_current"),
            latitude: fix.location.latitude, longitude: fix.location.longitude,
            zoneId: (z?.zone ?? .current).identifier, countryCode: z?.nearestCity?.countryCode, source: .device,
            zoneConfirmed: !(z?.needsConfirmation ?? true)
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

    func setReminder(_ e: PrayerEvent, _ on: Bool) async {
        if on, !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
        if on { settings.reminders.insert(e) } else { settings.reminders.remove(e) }
        shareWithWidgets()
        await rescheduleReminders()
    }

    func setAlerts(_ a: AlertSettings) {
        settings.alerts = a
        Task {
            if !(await Reminders.authorized()) { _ = await Reminders.requestAuthorization() }
            await rescheduleReminders()
        }
    }

    func rescheduleReminders() async {
        await Reminders.reschedule(location: settings.location, settings: settings.prayer, enabled: settings.reminders, alerts: settings.alerts, l10n: l10n)
    }

    /// Sky for the prayer period in progress (night when no location is chosen yet).
    func skyPeriod(now: Date) -> SkyPeriod {
        guard settings.location != nil else { return .night }
        return PrayerSnapshot(model: self, now: now).sky
    }

    // MARK: Prayer schedule (cached per location/settings/date; countdown recomputed from instants)

    // Not observed: a cache filled during view evaluation must not trigger re-renders.
    @ObservationIgnored private var cacheKey: String?
    @ObservationIgnored private var cached: [DaySchedule] = []

    /// Looks up the nearest downloaded mosque when the phone (or the prayer city) has moved ~100 m.
    func refreshNearestMosque() {
        guard let repo = mosques, let center = location.position?.location ?? settings.location?.location else { return }
        let key = "\((center.latitude * 1000).rounded())/\((center.longitude * 1000).rounded())"
        guard key != nearestKey else { return }
        nearestKey = key
        let lang = l10n.language
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
