import Foundation

// "Sky follows the weather" (opt-in): the current weather of the chosen city on the time-of-day sky, from
// MET Norway Locationforecast 2.0 compact (CC BY 4.0 / NLOD 2.0, credit "Based on data from MET Norway").
// Like OnlineMosques, this file only builds the request and reads the reply; each app makes the HTTP GET
// itself (User-Agent always, If-Modified-Since when cached, never HEAD) and passes the status, headers and
// body here. Only the city rounded to 0.01° (about 1 km) is sent. Mirrors core/Weather.kt; both read
// shared/fixtures/met-compact-sample.json and met-symbols.json in their tests.

public enum WeatherCondition: String, Codable, CaseIterable, Sendable {
    case clear, partlyCloudy = "partly_cloudy", cloudy, rain, thunderstorm, fog, snow

    /// "clear", "partly_cloudy", ...; also the suffix of the string key `weather_<key>`.
    public var key: String { rawValue }
    /// The localized condition name: weather_clear, weather_partly_cloudy, ...
    public var stringKey: String { "weather_" + rawValue }
}

public enum Precipitation: String, Codable, CaseIterable, Sendable {
    case none, rain, snow
}

/// What a MET symbol code means for the sky. Sleet is drawn as rain.
public struct WeatherSymbol: Hashable, Sendable {
    public let condition: WeatherCondition
    /// 0.5 light, 0.75 normal, 1.0 heavy (also 1.0 for clear sky, partly cloudy, cloudy and fog; 0.5 for "fair").
    public let intensity: Double
    public let showers: Bool
    public let precipitation: Precipitation

    public init(condition: WeatherCondition, intensity: Double, showers: Bool = false, precipitation: Precipitation = .none) {
        self.condition = condition
        self.intensity = intensity
        self.showers = showers
        self.precipitation = precipitation
    }
}

/// One forecast step as kept on disk: instant values plus the base symbol code (no _day/_night suffix).
public struct WeatherSample: Codable, Hashable, Sendable {
    public var time: Date
    public var temperatureC: Double?
    public var windMs: Double?
    public var symbol: String?

    public init(time: Date, temperatureC: Double?, windMs: Double?, symbol: String?) {
        self.time = time
        self.temperatureC = temperatureC
        self.windMs = windMs
        self.symbol = symbol
    }
}

/// What the sky shows right now.
public struct WeatherNow: Hashable, Sendable {
    public let symbol: WeatherSymbol
    public let temperatureC: Double
    public let windMs: Double
    /// Time of the forecast step shown.
    public let at: Date

    public init(symbol: WeatherSymbol, temperatureC: Double, windMs: Double, at: Date) {
        self.symbol = symbol
        self.temperatureC = temperatureC
        self.windMs = windMs
        self.at = at
    }
}

/// The one cached city (kept on disk while the switch is on; the app deletes the file when it is turned off).
public struct WeatherCache: Codable, Hashable, Sendable {
    /// Weather.key of the rounded city, e.g. "21.42,39.83".
    public var key: String
    /// At most WeatherPolicy.maxSamples steps around the time of the last 200.
    public var samples: [WeatherSample]
    /// Raw Last-Modified header, sent back unchanged as If-Modified-Since.
    public var lastModified: String?
    public var expires: Date?
    /// Last 200 or 304.
    public var validatedAt: Date?
    public var lastAttempt: Date
    public var failures: Int
    /// Set after a 403 (usually a rejected User-Agent).
    public var blockedUntil: Date?

    public init(key: String, samples: [WeatherSample] = [], lastModified: String? = nil, expires: Date? = nil,
                validatedAt: Date? = nil, lastAttempt: Date, failures: Int = 0, blockedUntil: Date? = nil) {
        self.key = key
        self.samples = samples
        self.lastModified = lastModified
        self.expires = expires
        self.validatedAt = validatedAt
        self.lastAttempt = lastAttempt
        self.failures = failures
        self.blockedUntil = blockedUntil
    }
}

public enum Weather {
    public static let endpoint = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    /// Opened by the "Weather: based on data from MET Norway" link (sky_weather_attribution).
    public static let attributionURL = "https://www.met.no/en/free-meteorological-data/Licensing-and-crediting"
    /// English credits line for Sources and licences (not localized, like the other credit lines).
    public static let credit = "Weather (optional): MET Norway, Locationforecast 2.0 · CC BY 4.0 and NLOD 2.0"
    /// Same file name on every platform, in the app's caches/support folder.
    public static let cacheFileName = "weather-cache.json"
    public static let lineKey = "weather_line"
    public static let a11yKey = "weather_a11y"
    static let publisherSite = "https://nearmosque.net/"
    static let publisherEmail = "app@vision.bz"

    private static let posix = Locale(identifier: "en_US_POSIX")

    /// Two decimals in the POSIX locale (never Arabic-Indic digits or a decimal comma), -0.00 as 0.00.
    static func coordinate(_ v: Double) -> String {
        let s = String(format: "%.2f", locale: posix, v)
        return s == "-0.00" ? "0.00" : s
    }

    /// To 0.01° (about 1 km, like OnlineMosques.privacyRound), halves away from zero, with the same arithmetic as
    /// core/Weather.kt (sign(v) * floor(|v| * 100 + 0.5) / 100): Swift's .rounded() and Kotlin's Math.round, which
    /// privacyRound uses there, disagree on negative halves, and every platform must ask for the same point.
    static func round2(_ v: Double) -> Double {
        let r = (abs(v) * 100 + 0.5).rounded(.down) / 100
        return v < 0 ? -r : r
    }

    /// "lat,lon" of the city rounded to 0.01° (round2); the cache key.
    public static func key(_ p: LatLng) -> String {
        coordinate(round2(p.latitude)) + "," + coordinate(round2(p.longitude))
    }

    /// The only request: the city rounded to 0.01° (round2), nothing else (no altitude).
    public static func url(_ p: LatLng) -> String {
        "\(endpoint)?lat=\(coordinate(round2(p.latitude)))&lon=\(coordinate(round2(p.longitude)))"
    }

    /// platform: iOS, iPadOS, macOS, visionOS, Android, Windows or Linux. The publisher's contact, never the user's.
    public static func userAgent(platform: String, version: String) -> String {
        "NearMosque/\(version) (\(platform); +\(publisherSite); \(publisherEmail))"
    }

    /// Headers for the GET: User-Agent always; If-Modified-Since only for the same city. No Accept-Encoding
    /// (URLSession adds gzip and inflates by itself).
    public static func requestHeaders(userAgent: String, cache: WeatherCache?, key: String) -> [String: String] {
        var h = ["User-Agent": userAgent]
        if let c = cache, c.key == key, let lm = c.lastModified, !lm.isEmpty { h["If-Modified-Since"] = lm }
        return h
    }

    /// Wraps a formatted value in FSI...PDI so a minus sign and the degree sign keep their order in RTL text.
    public static func isolate(_ s: String) -> String { "\u{2068}" + s + "\u{2069}" }

    // MARK: Parsing

    /// Steps in [now - 90 min, now + 6 h], oldest first. [] for invalid JSON or units other than celsius and m/s.
    /// Times with or without fractional seconds are read alike, as in core/Weather.kt (Instant.parse).
    public static func parse(_ body: Data, now: Date) -> [WeatherSample] {
        guard let root = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
              let props = root["properties"] as? [String: Any],
              let units = (props["meta"] as? [String: Any])?["units"] as? [String: Any],
              units["air_temperature"] as? String == "celsius", units["wind_speed"] as? String == "m/s",
              let series = props["timeseries"] as? [[String: Any]] else { return [] }
        let iso = ISO8601DateFormatter()
        let isoFraction = ISO8601DateFormatter()
        isoFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let from = now.addingTimeInterval(-WeatherPolicy.nearSample), to = now.addingTimeInterval(WeatherPolicy.horizon)
        var out: [WeatherSample] = []
        for step in series {
            guard let ts = step["time"] as? String, let time = iso.date(from: ts) ?? isoFraction.date(from: ts),
                  time >= from, time <= to else { continue }
            let data = step["data"] as? [String: Any] ?? [:]
            let details = (data["instant"] as? [String: Any])?["details"] as? [String: Any] ?? [:]
            func code(_ period: String) -> String? {
                ((data[period] as? [String: Any])?["summary"] as? [String: Any])?["symbol_code"] as? String
            }
            let symbol = (code("next_1_hours") ?? code("next_6_hours") ?? code("next_12_hours")).map(baseCode)
            out.append(WeatherSample(time: time, temperatureC: num(details["air_temperature"]),
                                     windMs: num(details["wind_speed"]), symbol: symbol))
        }
        return out.sorted { $0.time < $1.time }
    }

    /// A JSON number only: a quoted "12.5" is no value, as in core/Weather.kt (met-symbols.json "steps").
    private static func num(_ a: Any?) -> Double? {
        if let d = a as? Double { return d.isFinite ? d : nil }
        if let i = a as? Int { return Double(i) }
        if let n = a as? NSNumber { return n.doubleValue }
        return nil
    }

    /// The symbol code without _day / _night / _polartwilight (the app's own sky period decides day or night).
    public static func baseCode(_ code: String) -> String {
        let c = code.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        for s in ["_day", "_night", "_polartwilight"] where c.hasSuffix(s) { return String(c.dropLast(s.count)) }
        return c
    }

    // MARK: Symbols

    private static let fixed: [String: WeatherSymbol] = [
        "clearsky": WeatherSymbol(condition: .clear, intensity: 1.0),
        "fair": WeatherSymbol(condition: .partlyCloudy, intensity: 0.5),
        "partlycloudy": WeatherSymbol(condition: .partlyCloudy, intensity: 1.0),
        "cloudy": WeatherSymbol(condition: .cloudy, intensity: 1.0),
        "fog": WeatherSymbol(condition: .fog, intensity: 1.0),
    ]

    /// Every code of MET's legend (41, plus "lightsleetshowersandthunder" / "lightsnowshowersandthunder" next to
    /// MET's "lightss..." spelling), each as light / normal / heavy.
    private static let table: [String: WeatherSymbol] = {
        var t = fixed
        let levels: [(String, Double)] = [("light", 0.5), ("", 0.75), ("heavy", 1.0)]
        // (stem, condition, showers, precipitation)
        let families: [(String, WeatherCondition, Bool, Precipitation)] = [
            ("rainshowers", .rain, true, .rain), ("rain", .rain, false, .rain),
            ("sleetshowers", .rain, true, .rain), ("sleet", .rain, false, .rain),
            ("snowshowers", .snow, true, .snow), ("snow", .snow, false, .snow),
            ("rainshowersandthunder", .thunderstorm, true, .rain), ("sleetshowersandthunder", .thunderstorm, true, .rain),
            ("snowshowersandthunder", .thunderstorm, true, .snow), ("rainandthunder", .thunderstorm, false, .rain),
            ("sleetandthunder", .thunderstorm, false, .rain), ("snowandthunder", .thunderstorm, false, .snow),
        ]
        for (stem, condition, showers, precip) in families {
            for (prefix, intensity) in levels {
                t[prefix + stem] = WeatherSymbol(condition: condition, intensity: intensity, showers: showers, precipitation: precip)
            }
        }
        t["lightssleetshowersandthunder"] = t["lightsleetshowersandthunder"]
        t["lightssnowshowersandthunder"] = t["lightsnowshowersandthunder"]
        return t
    }()

    /// The sky's reading of a MET symbol code (suffix allowed). An unknown code is read from its words
    /// (thunder, snow, sleet/rain, fog, partly/fair, cloudy, clear); anything else is nil: the normal sky.
    public static func symbol(_ code: String) -> WeatherSymbol? {
        let c = baseCode(code)
        if let s = table[c] { return s }
        let condition: WeatherCondition
        if c.contains("thunder") { condition = .thunderstorm }
        else if c.contains("snow") { condition = .snow }
        else if c.contains("sleet") || c.contains("rain") { condition = .rain }
        else if c.contains("fog") { condition = .fog }
        else if c.contains("partly") || c.contains("fair") { condition = .partlyCloudy }
        else if c.contains("cloudy") { condition = .cloudy }
        else if c.contains("clear") { condition = .clear }
        else { return nil }
        let wet = [.rain, .snow, .thunderstorm].contains(condition)
        let intensity = c.hasPrefix("light") ? 0.5 : c.hasPrefix("heavy") ? 1.0 : (wet ? 0.75 : 1.0)
        let precip: Precipitation = c.contains("snow") ? .snow : (c.contains("sleet") || c.contains("rain")) ? .rain : .none
        return WeatherSymbol(condition: condition, intensity: intensity, showers: c.contains("showers"), precipitation: precip)
    }

    // MARK: What to show

    /// The weather to draw now, or nil for the normal sky: no cache or another city, nothing confirmed by MET
    /// within WeatherPolicy.maxAge, no step within 90 minutes, or a missing value. Call it every tick.
    public static func now(cache: WeatherCache?, key: String, now: Date) -> WeatherNow? {
        guard let c = cache, c.key == key, let v = c.validatedAt, now.timeIntervalSince(v) <= WeatherPolicy.maxAge else { return nil }
        let past = c.samples.filter { $0.time <= now && now.timeIntervalSince($0.time) <= WeatherPolicy.nearSample }
        let future = c.samples.filter { $0.time > now && $0.time.timeIntervalSince(now) <= WeatherPolicy.futureSample }
        guard let s = past.max(by: { $0.time < $1.time }) ?? future.min(by: { $0.time < $1.time }),
              let t = s.temperatureC, let w = s.windMs, let code = s.symbol, let sym = symbol(code) else { return nil }
        return WeatherNow(symbol: sym, temperatureC: t, windMs: w, at: s.time)
    }

    // MARK: Disk format

    /// JSON with ISO-8601 UTC times (the same shape core/Weather.kt writes).
    public static func encodeCache(_ cache: WeatherCache) -> Data {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys]
        e.dateEncodingStrategy = .custom { date, enc in
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            var c = enc.singleValueContainer()
            try c.encode(f.string(from: date))
        }
        return (try? e.encode(cache)) ?? Data()
    }

    /// nil for a missing, damaged or foreign file (the sky then stays normal until the next fetch).
    public static func decodeCache(_ data: Data) -> WeatherCache? {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            let frac = ISO8601DateFormatter()
            frac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = frac.date(from: s) ?? ISO8601DateFormatter().date(from: s) { return date }
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "not an ISO-8601 time: \(s)")
        }
        return try? d.decode(WeatherCache.self, from: data)
    }
}

// MARK: - Refresh policy

/// When to ask MET again and how to fold a reply into the cache. Honours Expires and If-Modified-Since,
/// backs off on errors, waits a day after a 403, and never asks twice within a minute.
public enum WeatherPolicy {
    public static let minInterval: TimeInterval = 30 * 60
    public static let maxAge: TimeInterval = 3 * 3600
    /// After the first failure; later failures wait retryCap.
    public static let retry: TimeInterval = 30 * 60
    public static let retryCap: TimeInterval = 60 * 60
    public static let after403: TimeInterval = 24 * 3600
    /// Between any two requests (switching cities quickly).
    public static let minGap: TimeInterval = 60
    public static let maxJitter: TimeInterval = 90
    /// Used when Expires is missing, unreadable or already past (clock skew).
    public static let defaultExpiry: TimeInterval = 30 * 60
    /// A step must be at most this far from now to be shown; parse keeps steps from now minus this.
    public static let nearSample: TimeInterval = 90 * 60
    /// When no step has started yet, the first one within this time is shown.
    public static let futureSample: TimeInterval = 60 * 60
    /// parse keeps steps up to now plus this.
    public static let horizon: TimeInterval = 6 * 3600
    public static let maxSamples = 12

    /// 0-90 s, added to scheduled refreshes so devices do not all ask at the same moment.
    public static func randomJitter() -> TimeInterval { .random(in: 0...maxJitter) }

    /// The earliest time the app may send the next request for `key`. lastAnyRequest: the last request for
    /// any city, this one included (cache.lastAttempt counts too). It holds the next request back minGap even for
    /// the same city, for a request that went out but whose reply was never folded in (cancelled).
    public static func nextFetch(cache: WeatherCache?, key: String, lastAnyRequest: Date?, now: Date, jitter: TimeInterval) -> Date {
        let blocked = cache?.blockedUntil ?? .distantPast
        guard let c = cache, c.key == key else {
            let last = [lastAnyRequest, cache?.lastAttempt].compactMap { $0 }.max()
            return [now, last.map { $0.addingTimeInterval(minGap) } ?? now, blocked].max()!
        }
        let wait = c.failures == 0 ? minInterval : c.failures == 1 ? retry : retryCap
        let gap = lastAnyRequest.map { $0.addingTimeInterval(minGap) } ?? .distantPast
        let base = [c.expires ?? c.lastAttempt.addingTimeInterval(minInterval), c.lastAttempt.addingTimeInterval(wait), blocked, gap].max()!
        return base.addingTimeInterval(jitter)
    }

    public static func shouldFetch(cache: WeatherCache?, key: String, lastAnyRequest: Date?, now: Date, jitter: TimeInterval) -> Bool {
        nextFetch(cache: cache, key: key, lastAnyRequest: lastAnyRequest, now: now, jitter: jitter) <= now
    }

    /// Folds a reply into the cache. status nil means a transport error (offline). The body is read only on
    /// 200/203; a 203 means the API version is deprecated (log it in DEBUG builds). Header names are matched
    /// case-insensitively. Another key replaces the previous city entirely.
    public static func apply(status: Int?, headers: [String: String], body: Data?, now: Date, previous: WeatherCache?, key: String) -> WeatherCache {
        let same = previous?.key == key ? previous : nil
        func header(_ name: String) -> String? {
            headers.first { $0.key.caseInsensitiveCompare(name) == .orderedSame }?.value
        }
        func expiry() -> Date {
            if let e = header("Expires").flatMap(httpDate), e > now { return e }
            return now.addingTimeInterval(defaultExpiry)
        }
        switch status {
        case 200, 203:
            let samples = Array(Weather.parse(body ?? Data(), now: now).prefix(maxSamples))
            return WeatherCache(key: key, samples: samples, lastModified: header("Last-Modified"), expires: expiry(),
                                validatedAt: now, lastAttempt: now, failures: 0, blockedUntil: nil)
        case 304 where same != nil:
            var c = same!
            c.expires = expiry()
            if let lm = header("Last-Modified") { c.lastModified = lm }
            c.validatedAt = now
            c.lastAttempt = now
            c.failures = 0
            c.blockedUntil = nil
            // Kept steps no longer reach now (a 304 after a long pause): ask without If-Modified-Since next time.
            if !c.samples.contains(where: { abs($0.time.timeIntervalSince(now)) <= nearSample }) { c.lastModified = nil }
            return c
        case 403:
            var c = same ?? WeatherCache(key: key, lastAttempt: now)
            c.lastAttempt = now
            c.failures += 1
            c.blockedUntil = now.addingTimeInterval(after403)
            return c
        default:
            var c = same ?? WeatherCache(key: key, lastAttempt: now)
            c.lastAttempt = now
            c.failures += 1
            return c
        }
    }

    /// RFC 1123 HTTP-date, e.g. "Sat, 17 Oct 2026 13:47:30 GMT".
    public static func httpDate(_ s: String) -> Date? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "GMT")
        f.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return f.date(from: s.trimmingCharacters(in: .whitespaces))
    }
}

// MARK: - Units

public enum TemperatureUnit: String, Codable, CaseIterable, Sendable {
    case celsius, fahrenheit
    /// weather_temp_c / weather_temp_f
    public var stringKey: String { self == .celsius ? "weather_temp_c" : "weather_temp_f" }
}

public enum WindUnit: String, Codable, CaseIterable, Sendable {
    case kmh, mph
    /// weather_wind_kmh / weather_wind_mph
    public var stringKey: String { self == .kmh ? "weather_wind_kmh" : "weather_wind_mph" }
}

/// Units of the temperature and wind line. Default from the region; the apps may override from system settings.
public struct WeatherUnits: Hashable, Sendable {
    public var temperature: TemperatureUnit
    public var wind: WindUnit

    public init(temperature: TemperatureUnit, wind: WindUnit) {
        self.temperature = temperature
        self.wind = wind
    }

    public static let metric = WeatherUnits(temperature: .celsius, wind: .kmh)
    static let fahrenheitRegions: Set<String> = ["US", "PR", "GU", "VI", "AS", "MP", "UM", "BS", "BZ", "KY", "PW", "FM", "MH", "LR"]
    static let mphRegions: Set<String> = fahrenheitRegions.union(["GB", "IM", "JE", "GG"])

    /// ISO 3166 region code (any case); nil or unknown gives Celsius and km/h.
    public static func forRegion(_ code: String?) -> WeatherUnits {
        let r = (code ?? "").uppercased()
        return WeatherUnits(temperature: fahrenheitRegions.contains(r) ? .fahrenheit : .celsius,
                            wind: mphRegions.contains(r) ? .mph : .kmh)
    }

    public static func displayTemperature(_ celsius: Double, _ unit: TemperatureUnit) -> Int {
        round(unit == .celsius ? celsius : celsius * 9 / 5 + 32)
    }

    public static func displayWind(_ ms: Double, _ unit: WindUnit) -> Int {
        max(0, round(max(0, ms) * (unit == .kmh ? 3.6 : 2.2369363)))
    }

    public func temperature(_ celsius: Double) -> Int { Self.displayTemperature(celsius, temperature) }
    public func wind(_ ms: Double) -> Int { Self.displayWind(ms, wind) }

    /// sign(x) * floor(|x| + 0.5), never -0: the same on every platform (Swift .rounded() and Kotlin
    /// roundToInt() disagree on negative halves).
    public static func round(_ x: Double) -> Int {
        guard x.isFinite, abs(x) < 1e9 else { return 0 }
        let r = Int((abs(x) + 0.5).rounded(.down))
        return x < 0 ? -r : r
    }
}

// MARK: - Look

/// Plain numbers that drive the drawing, from shared/design/tokens.json "weather" (checked by WeatherTests),
/// so iOS, Android and the desktop draw the same sky. Colours (veil, streaks, flakes, fog) stay in each theme.
public struct WeatherLook: Hashable, Sendable {
    /// How far each sky stop (top, mid, glow, low) is mixed toward the condition's veil colour.
    public var veilMix: Double
    /// Multiplies the horizon glow.
    public var glowStrength: Double
    public var starOpacity: Double
    /// Opacity of the existing cloud band; nil keeps today's default.
    public var cloudOpacity: Double?
    /// Opacity of the overcast deck (0: none).
    public var deckOpacity: Double
    public var rainStreaks: Int
    public var snowFlakes: Int
    /// Opacity of the low fog band (0: none).
    public var fogBand: Double
    /// Occasional soft flash; the apps never flash with Reduce Motion on.
    public var lightning: Bool

    public init(veilMix: Double, glowStrength: Double, starOpacity: Double, cloudOpacity: Double?, deckOpacity: Double,
                rainStreaks: Int, snowFlakes: Int, fogBand: Double, lightning: Bool) {
        self.veilMix = veilMix
        self.glowStrength = glowStrength
        self.starOpacity = starOpacity
        self.cloudOpacity = cloudOpacity
        self.deckOpacity = deckOpacity
        self.rainStreaks = rainStreaks
        self.snowFlakes = snowFlakes
        self.fogBand = fogBand
        self.lightning = lightning
    }

    /// Today's sky, unchanged.
    public static let normal = WeatherLook(veilMix: 0, glowStrength: 1, starOpacity: 1, cloudOpacity: nil, deckOpacity: 0,
                                           rainStreaks: 0, snowFlakes: 0, fogBand: 0, lightning: false)

    // tokens.json "weather"
    static let mix: [WeatherCondition: Double] = [.clear: 0, .partlyCloudy: 0.12, .cloudy: 0.45, .rain: 0.55, .thunderstorm: 0.65, .fog: 0.5, .snow: 0.45]
    static let glow: [WeatherCondition: Double] = [.clear: 1, .partlyCloudy: 0.9, .cloudy: 0.45, .rain: 0.35, .thunderstorm: 0.25, .fog: 0.5, .snow: 0.5]
    static let stars: [WeatherCondition: Double] = [.clear: 1, .partlyCloudy: 0.8, .cloudy: 0.15, .rain: 0, .thunderstorm: 0, .fog: 0.3, .snow: 0.1]
    static let clouds: [WeatherCondition: Double] = [.clear: 0.25, .cloudy: 0.9, .rain: 0.9, .thunderstorm: 0.95, .fog: 0.5, .snow: 0.85]
    static let deck: [WeatherCondition: Double] = [.cloudy: 0.9, .rain: 0.9, .thunderstorm: 1, .snow: 0.9]
    static let fogBands: [WeatherCondition: Double] = [.fog: 1]
    static let cloudsClearNight = 0.15
    static let showersMix = 0.8
    static let rainStreaksMax = 60
    static let snowFlakesMax = 55

    /// nil gives `normal`. night: the sky period is night (a clear night keeps a fainter cloud band).
    public static func of(_ symbol: WeatherSymbol?, night: Bool = false) -> WeatherLook {
        guard let s = symbol else { return normal }
        let c = s.condition
        return WeatherLook(
            veilMix: (mix[c] ?? 0) * (s.showers ? showersMix : 1),
            glowStrength: glow[c] ?? 1,
            starOpacity: stars[c] ?? 1,
            cloudOpacity: c == .clear && night ? cloudsClearNight : clouds[c],
            deckOpacity: deck[c] ?? 0,
            rainStreaks: s.precipitation == .rain ? WeatherUnits.round(Double(rainStreaksMax) * s.intensity) : 0,
            snowFlakes: s.precipitation == .snow ? WeatherUnits.round(Double(snowFlakesMax) * s.intensity) : 0,
            fogBand: fogBands[c] ?? 0,
            lightning: c == .thunderstorm)
    }
}
