import Foundation
import NMCore
import Observation
#if canImport(UIKit)
import UIKit
#endif

/// One reply from MET Norway: the status, the two headers the cache needs, and the body (200 or 203 only).
struct WeatherReply: Sendable {
    var status: Int
    var headers: [String: String]
    var body: Data?
}

/// Sends one GET. nil: no reply (offline, timeout, Low Data Mode, TLS failure). Injected so the glue can be tested
/// without the network; the app uses `WeatherService.urlSession`.
typealias WeatherTransport = @Sendable (URLRequest) async -> WeatherReply?

/// "Sky follows the weather" (opt-in, off by default): the chosen city's forecast from MET Norway Locationforecast
/// 2.0 compact, fetched only while the app is open and in use (MET: "Mobile apps must not fetch new data while the app
/// is not in use"), never in the background. The rules are NMCore's WeatherPolicy: Expires and If-Modified-Since,
/// back-off after errors, a day's wait after a 403, at least a minute between requests and a random 0-90 s delay
/// before each scheduled refresh. This class only sends the GET and keeps the last reply in Caches (not backed up;
/// deleted when the switch is turned off). Only the city rounded to 0.01° (about 1 km) is sent, with the app's
/// User-Agent and nothing that identifies the person. Coordinates and URLs are never logged. Failures are silent:
/// the sky simply stays normal.
@MainActor
@Observable
final class WeatherService {
    /// The last reply for one city (nil when the switch is off or nothing was fetched yet).
    private(set) var cache: WeatherCache?
    /// Units of the temperature and wind line: the device's region and settings, read again each time the app opens.
    private(set) var units: WeatherUnits

    @ObservationIgnored private var loop: Task<Void, Never>?
    @ObservationIgnored private var loopKey: String?
    /// The last request for any city (in memory; with cache.lastAttempt it keeps requests a minute apart).
    @ObservationIgnored private(set) var lastAnyRequest: Date?
    /// A 403 blocks requests for a day for every city; kept here too, so turning the switch off and on (which
    /// deletes the cache) does not end the wait early.
    @ObservationIgnored private var blockedUntil: Date?
    private let transport: WeatherTransport
    private let file: URL?
    private let clock: () -> Date
    private let userAgent: String

    /// The defaults are the app's: URLSession, Caches/weather-cache.json, the system clock and the app's User-Agent.
    init(transport: WeatherTransport? = nil, file: URL? = WeatherService.defaultFile,
         clock: @escaping () -> Date = Date.init, userAgent: String? = nil) {
        self.transport = transport ?? WeatherService.urlSession
        self.file = file
        self.clock = clock
        self.userAgent = userAgent ?? WeatherService.appUserAgent
        units = WeatherService.deviceUnits()
        cache = file.flatMap { try? Data(contentsOf: $0) }.flatMap(Weather.decodeCache)
        blockedUntil = cache?.blockedUntil
    }

    /// The weather to draw for this city (PrayerLocation.weatherLocation) now, or nil for the normal sky (another
    /// city, older than about 3 hours, or a step missing).
    func now(for city: LatLng, at date: Date) -> WeatherNow? {
        guard let cache else { return nil }
        return Weather.now(cache: cache, key: Weather.key(city), now: date)
    }

    /// Called when the app becomes active, the switch changes or the city changes. Off: stops and deletes the cache.
    /// On with a city (PrayerLocation.weatherLocation): fetches now if due, then whenever the policy allows while the
    /// app stays active. Without one (no city chosen, or none within 30 km of the device) nothing is sent.
    func resume(location: LatLng?, enabled: Bool) {
        units = Self.deviceUnits()
        guard enabled else { stop(); clear(); return }
        #if DEBUG
        // Screenshots: `-demoWeather <condition>` draws a fixed sky with no network (see AppModel.skyWeather).
        if UserDefaults.standard.string(forKey: "demoWeather") != nil { stop(); return }
        #endif
        guard let location else { stop(); return }
        let key = Weather.key(location)
        // Already running for this city: leave it (a request in flight is not cancelled).
        if loopKey == key, let loop, !loop.isCancelled { return }
        stop()
        let url = Weather.url(location)
        loopKey = key
        loop = Task { [weak self] in await self?.run(key: key, url: url) }
    }

    /// The app is no longer in use (background, inactive, another app in front on the Mac): no requests until it is.
    func pause() { stop() }

    private func stop() {
        loop?.cancel()
        loop = nil
        loopKey = nil
    }

    private func clear() {
        cache = nil
        if let file { try? FileManager.default.removeItem(at: file) }
    }

    /// The earliest next request for `key`: the policy's time, but never within a minute of the last request and
    /// never before a 403's day is over.
    func nextFetch(key: String, jitter: TimeInterval) -> Date {
        let policy = WeatherPolicy.nextFetch(cache: cache, key: key, lastAnyRequest: lastAnyRequest, now: clock(), jitter: jitter)
        let gap = lastAnyRequest.map { $0.addingTimeInterval(WeatherPolicy.minGap) } ?? .distantPast
        return [policy, gap, blockedUntil ?? .distantPast].max()!
    }

    private func run(key: String, url: String) async {
        // Due when the app opens: ask at once. A refresh that falls due while the app stays open waits a random
        // 0-90 s more, so open apps do not all ask the moment MET's Expires passes.
        var jitter: TimeInterval = nextFetch(key: key, jitter: 0) <= clock() ? 0 : WeatherPolicy.randomJitter()
        while !Task.isCancelled {
            let wait = nextFetch(key: key, jitter: jitter).timeIntervalSince(clock())
            if wait > 0 {
                // Woken at least every 15 minutes to look again (the clock may have changed meanwhile).
                try? await Task.sleep(nanoseconds: UInt64(min(wait, 15 * 60) * 1_000_000_000))
                continue
            }
            await fetch(key: key, url: url)
            jitter = WeatherPolicy.randomJitter()
        }
    }

    /// One GET folded into the cache. Never throws and shows nothing: an error only delays the next try.
    func fetch(key: String, url: String) async {
        guard let u = URL(string: url) else { return }
        var request = URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = "GET"
        for (name, value) in Weather.requestHeaders(userAgent: userAgent, cache: cache, key: key) {
            request.setValue(value, forHTTPHeaderField: name)
        }
        // The same for everyone, so the request does not tell MET the phone's language.
        request.setValue("en", forHTTPHeaderField: "Accept-Language")
        lastAnyRequest = clock()
        let reply = await transport(request)
        // Paused or moved to another city meanwhile: drop the reply (the minute's gap above still holds).
        if Task.isCancelled { return }
        #if DEBUG
        if let s = reply?.status { print(s == 203 ? "weather HTTP 203 (MET deprecated this version)" : "weather HTTP \(s)") }
        #endif
        let updated = WeatherPolicy.apply(status: reply?.status, headers: reply?.headers ?? [:], body: reply?.body,
                                          now: clock(), previous: cache, key: key)
        cache = updated
        if let b = updated.blockedUntil, b > (blockedUntil ?? .distantPast) { blockedUntil = b }
        if let file { try? Weather.encodeCache(updated).write(to: file, options: [.atomic]) }
    }
}

extension WeatherService {
    /// Library/Caches/weather-cache.json: not backed up, and the system may purge it (the sky is then normal until the
    /// next fetch).
    nonisolated static var defaultFile: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent(Weather.cacheFileName)
    }

    /// "NearMosque/0.5.0 (iOS; +https://nearmosque.net/; app@vision.bz)": the publisher's contact, never the user's.
    static var appUserAgent: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        return Weather.userAgent(platform: platformName, version: version)
    }

    static var platformName: String {
        #if os(visionOS)
        return "visionOS"
        #elseif targetEnvironment(macCatalyst)
        return "macOS"
        #elseif canImport(UIKit)
        return UIDevice.current.userInterfaceIdiom == .pad ? "iPadOS" : "iOS"
        #else
        return "iOS"
        #endif
    }

    /// No cookies, no URL cache (WeatherPolicy does the conditional requests itself), a 15 s timeout, and nothing in
    /// Low Data Mode (the request fails and the sky stays normal).
    nonisolated static let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpCookieStorage = nil
        c.httpShouldSetCookies = false
        c.urlCredentialStorage = nil
        c.timeoutIntervalForRequest = 15
        c.timeoutIntervalForResource = 30
        c.waitsForConnectivity = false
        #if canImport(UIKit)
        c.allowsConstrainedNetworkAccess = false
        #endif
        return URLSession(configuration: c)
    }()

    nonisolated static let urlSession: WeatherTransport = { request in
        guard let (data, response) = try? await session.data(for: request), let http = response as? HTTPURLResponse else { return nil }
        var headers: [String: String] = [:]
        for name in ["Expires", "Last-Modified"] {
            if let v = http.value(forHTTPHeaderField: name) { headers[name] = v }
        }
        let body = (http.statusCode == 200 || http.statusCode == 203) ? data : nil
        return WeatherReply(status: http.statusCode, headers: headers, body: body)
    }

    /// Celsius or Fahrenheit as iOS itself shows the weather (Settings → General → Language & Region → Temperature,
    /// read through the system's measurement formatter), mph with the US or UK measurement system, otherwise km/h.
    /// Anything that cannot be told follows the region (WeatherUnits.forRegion).
    nonisolated static func deviceUnits(_ locale: Locale = .autoupdatingCurrent) -> WeatherUnits {
        var u = WeatherUnits.forRegion(locale.region?.identifier)
        if let t = systemTemperature(locale) { u.temperature = t }
        switch locale.measurementSystem {
        case .us, .uk: u.wind = .mph
        case .metric: u.wind = .kmh
        default: break
        }
        return u
    }

    /// The unit the system formatter turns 100 °C into for this locale (it follows the Temperature setting), by
    /// comparing its output with both units written out: independent of the language and its digits.
    nonisolated static func systemTemperature(_ locale: Locale) -> TemperatureUnit? {
        let f = MeasurementFormatter()
        f.locale = locale
        f.unitStyle = .short
        f.numberFormatter.maximumFractionDigits = 0
        let preferred = f.string(from: Measurement(value: 100, unit: UnitTemperature.celsius))
        f.unitOptions = .providedUnit
        if preferred == f.string(from: Measurement(value: 212, unit: UnitTemperature.fahrenheit)) { return .fahrenheit }
        if preferred == f.string(from: Measurement(value: 100, unit: UnitTemperature.celsius)) { return .celsius }
        return nil
    }

    /// `-demoWeather rain`: a condition key (clear, partly_cloudy, cloudy, rain, thunderstorm, fog, snow) or any MET
    /// symbol code (e.g. heavyrainshowers).
    nonisolated static func demoSymbol(_ arg: String) -> WeatherSymbol? {
        let codes = ["clear": "clearsky", "partly_cloudy": "partlycloudy", "cloudy": "cloudy", "rain": "rain",
                     "thunderstorm": "rainandthunder", "fog": "fog", "snow": "snow"]
        return Weather.symbol(codes[arg] ?? arg)
    }
}
