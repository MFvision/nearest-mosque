import Foundation
@testable import NMCore
import XCTest

/// Same cases as core WeatherTest.kt, from shared/fixtures/met-compact-sample.json and met-symbols.json.
final class WeatherTests: XCTestCase {
    private let iso = ISO8601DateFormatter()
    private func t(_ s: String) -> Date { iso.date(from: s)! }
    private var sample: Data { try! Data(contentsOf: Fixtures.root.appendingPathComponent("shared/fixtures/met-compact-sample.json")) }
    private let symbols = Fixtures.json("shared/fixtures/met-symbols.json")
    private let key = "21.42,39.83"

    private func expected(_ o: [String: Any]) -> WeatherSymbol {
        WeatherSymbol(condition: WeatherCondition(rawValue: o["condition"] as! String)!, intensity: o["intensity"] as! Double,
                      showers: o["showers"] as! Bool, precipitation: Precipitation(rawValue: o["precipitation"] as! String)!)
    }

    // MARK: Request

    func testUrlAndKeyUseRoundedCityWithTwoAsciiDecimals() {
        for c in symbols["coordinates"] as! [[String: Any]] {
            let p = LatLng(c["lat"] as! Double, c["lon"] as! Double)!
            XCTAssertEqual(Weather.url(p), "https://api.met.no/weatherapi/locationforecast/2.0/compact?" + (c["query"] as! String))
            XCTAssertEqual(Weather.key(p), c["key"] as! String)
        }
        let u = Weather.url(LatLng(-33.92487, 18.42401)!)
        XCTAssertTrue(u.hasSuffix("?lat=-33.92&lon=18.42"))
        XCTAssertFalse(u.contains("33.924"))
        XCTAssertFalse(u.contains("altitude"))
        XCTAssertTrue(u.unicodeScalars.allSatisfy(\.isASCII))
    }

    func testCoordinateFormatIgnoresTheInterfaceLocale() {
        // Swift cannot swap the process locale in a test (Kotlin's test does, for ar and fr); the formatter is
        // pinned to en_US_POSIX, so the output is always ASCII digits with a dot and exactly two decimals.
        for v in [-33.92487, 18.42401, 21.42, 39.83, -0.004, 179.999, -89.995] {
            let s = Weather.coordinate(OnlineMosques.privacyRound(LatLng(v.clamped90, v)!).longitude)
            XCTAssertNotNil(s.range(of: #"^-?[0-9]+\.[0-9]{2}$"#, options: .regularExpression), s)
        }
        XCTAssertEqual(Weather.coordinate(-33.92), "-33.92")
        XCTAssertEqual(Weather.coordinate(-0.0), "0.00")
        XCTAssertEqual(Weather.coordinate(-0.001), "0.00")
        XCTAssertEqual(Weather.coordinate(21.4199999), "21.42")
    }

    func testUserAgentAndHeaders() {
        let ua = Weather.userAgent(platform: "iOS", version: "1.4")
        XCTAssertEqual(ua, "NearMosque/1.4 (iOS; +https://nearmosque.net/; app@vision.bz)")
        let cache = WeatherCache(key: key, lastModified: "Sat, 17 Oct 2026 13:17:28 GMT", lastAttempt: t("2026-10-17T13:17:40Z"))
        XCTAssertEqual(Weather.requestHeaders(userAgent: ua, cache: cache, key: key),
                       ["User-Agent": ua, "If-Modified-Since": "Sat, 17 Oct 2026 13:17:28 GMT"])
        XCTAssertEqual(Weather.requestHeaders(userAgent: ua, cache: cache, key: "-33.92,18.42"), ["User-Agent": ua])
        XCTAssertEqual(Weather.requestHeaders(userAgent: ua, cache: nil, key: key), ["User-Agent": ua])
        XCTAssertEqual(Weather.isolate("-3°C"), "\u{2068}-3°C\u{2069}")
    }

    // MARK: Symbols

    func testEveryLegendCodeAndVariant() {
        let codes = symbols["codes"] as! [String: [String: Any]]
        XCTAssertEqual(codes.count, 41)
        for (code, o) in codes { XCTAssertEqual(Weather.symbol(code), expected(o), code) }
        for code in symbols["withVariants"] as! [String] {
            for suffix in symbols["suffixes"] as! [String] {
                XCTAssertEqual(Weather.symbol(code + suffix), expected(codes[code]!), code + suffix)
            }
        }
        for (code, o) in symbols["alternates"] as! [String: [String: Any]] { XCTAssertEqual(Weather.symbol(code), expected(o), code) }
        XCTAssertEqual(Weather.symbol("lightssleetshowersandthunder "), expected(codes["lightssleetshowersandthunder"]!))
    }

    func testUnknownCodesFallBackByTheirWords() {
        for (code, o) in symbols["fallback"] as! [String: Any] {
            if let o = o as? [String: Any] {
                XCTAssertEqual(Weather.symbol(code), expected(o), code)
            } else {
                XCTAssertNil(Weather.symbol(code), code)
            }
        }
    }

    // MARK: Parsing

    func testParseKeepsTheNextHoursWithFallbacksAndStrippedSuffixes() {
        let s = Weather.parse(sample, now: t("2026-10-17T13:20:00Z"))
        XCTAssertEqual(s.map { iso.string(from: $0.time) }, (12...19).map { "2026-10-17T\($0):00:00Z" })
        XCTAssertEqual(s[1], WeatherSample(time: t("2026-10-17T13:00:00Z"), temperatureC: 35.4, windMs: 5.7, symbol: "fair"))
        XCTAssertEqual(s.map(\.symbol), ["clearsky", "fair", "partlycloudy", "heavyrainshowersandthunder", "cloudy",
                                         "lightssnowshowersandthunder", "clearsky", "fog"])
        XCTAssertEqual(s[5].temperatureC, -2.5)
        XCTAssertLessThanOrEqual(s.count, WeatherPolicy.maxSamples)
        // Boundaries: now - 90 min and now + 6 h are inclusive.
        XCTAssertEqual(Weather.parse(sample, now: t("2026-10-17T13:30:00Z")).first?.time, t("2026-10-17T12:00:00Z"))
        XCTAssertEqual(Weather.parse(sample, now: t("2026-10-17T13:31:00Z")).first?.time, t("2026-10-17T13:00:00Z"))
        XCTAssertEqual(Weather.parse(sample, now: t("2026-10-17T14:00:00Z")).last?.time, t("2026-10-17T20:00:00Z"))
    }

    func testParseReadsTheSharedStepsLikeCore() throws {
        let steps = symbols["steps"] as! [String: Any]
        let now = t(steps["now"] as! String)
        for c in steps["cases"] as! [[String: Any]] {
            let step = try JSONSerialization.data(withJSONObject: c["step"]!)
            let head = #"{"properties":{"meta":{"units":{"air_temperature":"celsius","wind_speed":"m/s"}},"timeseries":["#
            let body = Data(head.utf8) + step + Data("]}}".utf8)
            let x = c["expected"] as! [String: Any]
            let want = WeatherSample(time: t(x["time"] as! String), temperatureC: x["temperatureC"] as? Double,
                                     windMs: x["windMs"] as? Double, symbol: x["symbol"] as? String)
            XCTAssertEqual(Weather.parse(body, now: now), [want], "\(c)")
        }
    }

    func testParseRejectsOtherUnitsAndBadJson() {
        let text = String(data: sample, encoding: .utf8)!
        let now = t("2026-10-17T13:20:00Z")
        XCTAssertTrue(Weather.parse(Data(text.replacingOccurrences(of: "\"celsius\"", with: "\"fahrenheit\"").utf8), now: now).isEmpty)
        XCTAssertTrue(Weather.parse(Data(text.replacingOccurrences(of: "\"m/s\"", with: "\"knots\"").utf8), now: now).isEmpty)
        XCTAssertTrue(Weather.parse(Data("not json".utf8), now: now).isEmpty)
        XCTAssertTrue(Weather.parse(Data("{\"properties\":{}}".utf8), now: now).isEmpty)
    }

    // MARK: What to show

    private func fresh(at now: Date) -> WeatherCache {
        WeatherPolicy.apply(status: 200, headers: ["Last-Modified": "Sat, 17 Oct 2026 13:17:28 GMT", "Expires": "Sat, 17 Oct 2026 13:47:30 GMT"],
                            body: sample, now: now, previous: nil, key: key)
    }

    func testNowPicksTheCurrentStep() {
        let c = fresh(at: t("2026-10-17T13:17:40Z"))
        let n = Weather.now(cache: c, key: key, now: t("2026-10-17T13:20:00Z"))!
        XCTAssertEqual(n, WeatherNow(symbol: WeatherSymbol(condition: .partlyCloudy, intensity: 0.5), temperatureC: 35.4, windMs: 5.7,
                                     at: t("2026-10-17T13:00:00Z")))
        XCTAssertEqual(Weather.now(cache: c, key: key, now: t("2026-10-17T13:59:59Z"))?.at, t("2026-10-17T13:00:00Z"))
        XCTAssertEqual(Weather.now(cache: c, key: key, now: t("2026-10-17T14:00:00Z"))?.symbol.condition, .partlyCloudy)
        XCTAssertEqual(Weather.now(cache: c, key: key, now: t("2026-10-17T15:10:00Z"))?.symbol.condition, .thunderstorm)
        XCTAssertEqual(Weather.now(cache: c, key: key, now: t("2026-10-17T16:10:00Z"))?.symbol.condition, .cloudy)
    }

    func testNowFallsBackToTheNormalSky() {
        let c = fresh(at: t("2026-10-17T13:17:40Z"))
        XCTAssertNil(Weather.now(cache: nil, key: key, now: t("2026-10-17T13:20:00Z")))
        XCTAssertNil(Weather.now(cache: c, key: "-33.92,18.42", now: t("2026-10-17T13:20:00Z")), "another city")
        XCTAssertNotNil(Weather.now(cache: c, key: key, now: t("2026-10-17T16:17:40Z")), "exactly 3 h old")
        XCTAssertNil(Weather.now(cache: c, key: key, now: t("2026-10-17T16:17:41Z")), "older than 3 h")
        var never = c
        never.validatedAt = nil
        XCTAssertNil(Weather.now(cache: never, key: key, now: t("2026-10-17T13:20:00Z")))

        let one = WeatherCache(key: key, samples: [WeatherSample(time: t("2026-10-17T13:00:00Z"), temperatureC: 30, windMs: 2, symbol: "rain")],
                               validatedAt: t("2026-10-17T13:00:00Z"), lastAttempt: t("2026-10-17T13:00:00Z"))
        XCTAssertNotNil(Weather.now(cache: one, key: key, now: t("2026-10-17T14:30:00Z")), "90 min after the step")
        XCTAssertNil(Weather.now(cache: one, key: key, now: t("2026-10-17T14:30:01Z")), "no step within 90 min")
        XCTAssertNotNil(Weather.now(cache: one, key: key, now: t("2026-10-17T12:00:00Z")), "first step within 60 min")
        XCTAssertNil(Weather.now(cache: one, key: key, now: t("2026-10-17T11:59:59Z")))

        var missing = one
        missing.samples[0].windMs = nil
        XCTAssertNil(Weather.now(cache: missing, key: key, now: t("2026-10-17T13:10:00Z")))
        missing = one
        missing.samples[0].symbol = "sandstorm"
        XCTAssertNil(Weather.now(cache: missing, key: key, now: t("2026-10-17T13:10:00Z")))
    }

    // MARK: Policy

    func testRefreshSequence() {
        let t0 = t("2026-10-17T13:17:40Z")
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: nil, key: key, lastAnyRequest: nil, now: t0, jitter: 30), t0, "first fetch now")
        var c = fresh(at: t0)
        XCTAssertEqual(c.samples.count, 8)
        XCTAssertEqual(c.lastModified, "Sat, 17 Oct 2026 13:17:28 GMT")
        XCTAssertEqual(c.expires, t("2026-10-17T13:47:30Z"))
        XCTAssertEqual(c.validatedAt, t0)
        XCTAssertEqual(c.failures, 0)

        // Before Expires: no request. After: one, with If-Modified-Since.
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: t0, now: t("2026-10-17T13:27:40Z"), jitter: 30),
                       t("2026-10-17T13:48:10Z"))
        XCTAssertFalse(WeatherPolicy.shouldFetch(cache: c, key: key, lastAnyRequest: t0, now: t("2026-10-17T13:47:31Z"), jitter: 0))
        let t1 = t("2026-10-17T13:48:10Z")
        XCTAssertTrue(WeatherPolicy.shouldFetch(cache: c, key: key, lastAnyRequest: t0, now: t1, jitter: 30))
        XCTAssertEqual(Weather.requestHeaders(userAgent: "ua", cache: c, key: key)["If-Modified-Since"], "Sat, 17 Oct 2026 13:17:28 GMT")

        // 304: same steps, new Expires, validated again; header names in any case.
        let before = c.samples
        c = WeatherPolicy.apply(status: 304, headers: ["expires": "Sat, 17 Oct 2026 14:18:30 GMT"], body: nil, now: t1, previous: c, key: key)
        XCTAssertEqual(c.samples, before)
        XCTAssertEqual(c.validatedAt, t1)
        XCTAssertEqual(c.expires, t("2026-10-17T14:18:30Z"))
        XCTAssertEqual(c.lastModified, "Sat, 17 Oct 2026 13:17:28 GMT")

        // 429, then 5xx, then offline: +30 min, then +60 min (cap); the steps stay.
        let t2 = t("2026-10-17T14:19:00Z")
        XCTAssertTrue(WeatherPolicy.shouldFetch(cache: c, key: key, lastAnyRequest: t1, now: t2, jitter: 0))
        c = WeatherPolicy.apply(status: 429, headers: [:], body: nil, now: t2, previous: c, key: key)
        XCTAssertEqual(c.failures, 1)
        XCTAssertEqual(c.samples, before)
        XCTAssertEqual(c.validatedAt, t1)
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: t2, now: t2, jitter: 0), t("2026-10-17T14:49:00Z"))
        c = WeatherPolicy.apply(status: 503, headers: [:], body: nil, now: t("2026-10-17T14:49:00Z"), previous: c, key: key)
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: nil, now: t("2026-10-17T14:49:00Z"), jitter: 0), t("2026-10-17T15:49:00Z"))
        c = WeatherPolicy.apply(status: nil, headers: [:], body: nil, now: t("2026-10-17T15:49:00Z"), previous: c, key: key)
        XCTAssertEqual(c.failures, 3)
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: nil, now: t("2026-10-17T15:49:00Z"), jitter: 0), t("2026-10-17T16:49:00Z"))
        XCTAssertNotNil(Weather.now(cache: c, key: key, now: t("2026-10-17T16:00:00Z")), "still within 3 h of the 304")
        XCTAssertNil(Weather.now(cache: c, key: key, now: t("2026-10-17T16:49:00Z")), "stale: normal sky without a request")

        // A 200 resets the failures.
        c = WeatherPolicy.apply(status: 200, headers: [:], body: sample, now: t("2026-10-17T16:49:00Z"), previous: c, key: key)
        XCTAssertEqual(c.failures, 0)
        XCTAssertNil(c.lastModified)
    }

    func testForbiddenWaitsADayAndNewCityRespectsTheGap() {
        let t0 = t("2026-10-17T13:17:40Z")
        let c = fresh(at: t0)
        let t1 = t("2026-10-17T13:50:00Z")
        let blocked = WeatherPolicy.apply(status: 403, headers: [:], body: nil, now: t1, previous: c, key: key)
        XCTAssertEqual(blocked.blockedUntil, t("2026-10-18T13:50:00Z"))
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: blocked, key: key, lastAnyRequest: t1, now: t1, jitter: 0), t("2026-10-18T13:50:00Z"))
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: blocked, key: "-33.92,18.42", lastAnyRequest: t1, now: t1, jitter: 0),
                       t("2026-10-18T13:50:00Z"), "the block is for the app, not the city")

        // A new city: at once, but never within a minute of the last request.
        let other = "-33.92,18.42"
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: other, lastAnyRequest: t0, now: t("2026-10-17T13:18:00Z"), jitter: 30),
                       t("2026-10-17T13:18:40Z"))
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: other, lastAnyRequest: t0, now: t("2026-10-17T13:30:00Z"), jitter: 30),
                       t("2026-10-17T13:30:00Z"))
        let moved = WeatherPolicy.apply(status: nil, headers: [:], body: nil, now: t("2026-10-17T13:30:00Z"), previous: c, key: other)
        XCTAssertEqual(moved.key, other)
        XCTAssertTrue(moved.samples.isEmpty, "only one city is kept")
        XCTAssertEqual(moved.failures, 1)
        XCTAssertEqual(WeatherPolicy.apply(status: 304, headers: [:], body: nil, now: t1, previous: c, key: other).failures, 1,
                       "a 304 for another city is not data")
    }

    func testARequestWhoseReplyWasNeverFoldedInStillHoldsTheNextOneBackAMinute() {
        let c = fresh(at: t("2026-10-17T13:17:40Z"))
        // Due at 13:47:40; sent at 13:48:00 and cancelled (the cache never saw it): not again before 13:49:00.
        let sent = t("2026-10-17T13:48:00Z")
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: sent, now: t("2026-10-17T13:48:20Z"), jitter: 0),
                       t("2026-10-17T13:49:00Z"))
        XCTAssertFalse(WeatherPolicy.shouldFetch(cache: c, key: key, lastAnyRequest: sent, now: t("2026-10-17T13:48:59Z"), jitter: 0))
        XCTAssertTrue(WeatherPolicy.shouldFetch(cache: c, key: key, lastAnyRequest: sent, now: t("2026-10-17T13:49:00Z"), jitter: 0))
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: c, key: key, lastAnyRequest: sent, now: t("2026-10-17T13:48:20Z"), jitter: 30),
                       t("2026-10-17T13:49:30Z"), "the jitter still comes on top")
    }

    func testExpiresMissingUnreadableOrPastMeansThirtyMinutes() {
        let now = t("2026-10-17T13:17:40Z")
        for h in [[:], ["Expires": "soon"], ["Expires": "Sat, 17 Oct 2026 12:00:00 GMT"], ["Expires": "0"]] as [[String: String]] {
            let c = WeatherPolicy.apply(status: 200, headers: h, body: sample, now: now, previous: nil, key: key)
            XCTAssertEqual(c.expires, t("2026-10-17T13:47:40Z"), "\(h)")
        }
        XCTAssertEqual(WeatherPolicy.httpDate("Sat, 17 Oct 2026 13:47:30 GMT"), t("2026-10-17T13:47:30Z"))
        let longer = WeatherPolicy.apply(status: 200, headers: ["Expires": "Sat, 17 Oct 2026 15:00:00 GMT"], body: sample, now: now, previous: nil, key: key)
        XCTAssertEqual(WeatherPolicy.nextFetch(cache: longer, key: key, lastAnyRequest: now, now: now, jitter: 0), t("2026-10-17T15:00:00Z"),
                       "a long Expires is honoured")
        XCTAssertTrue((0...WeatherPolicy.maxJitter).contains(WeatherPolicy.randomJitter()))
    }

    func testNotModifiedAfterALongPauseAsksAfreshNextTime() {
        let c = fresh(at: t("2026-10-17T13:17:40Z"))
        let late = WeatherPolicy.apply(status: 304, headers: [:], body: nil, now: t("2026-10-18T09:00:00Z"), previous: c, key: key)
        XCTAssertNil(late.lastModified)
        XCTAssertNil(Weather.now(cache: late, key: key, now: t("2026-10-18T09:00:00Z")))
    }

    func testCacheRoundTripsOnDisk() {
        let c = fresh(at: t("2026-10-17T13:17:40Z"))
        let back = Weather.decodeCache(Weather.encodeCache(c))
        XCTAssertEqual(back, c)
        XCTAssertNil(Weather.decodeCache(Data("{}".utf8)))
        XCTAssertNil(Weather.decodeCache(Data()))
        let kotlinShape = #"{"key":"21.42,39.83","samples":[{"time":"2026-10-17T13:00:00Z","temperatureC":35.4,"windMs":5.7,"symbol":"fair"}],"lastAttempt":"2026-10-17T13:17:40.123Z","failures":0,"validatedAt":"2026-10-17T13:17:40Z"}"#
        XCTAssertEqual(Weather.decodeCache(Data(kotlinShape.utf8))?.samples.first?.symbol, "fair")
    }

    // MARK: Units and rounding

    func testRoundingUnitsAndRegions() {
        for r in symbols["rounding"] as! [[String: Any]] {
            XCTAssertEqual(WeatherUnits.round(r["x"] as! Double), r["expected"] as! Int, "\(r)")
        }
        for r in symbols["temperature"] as! [[String: Any]] {
            XCTAssertEqual(WeatherUnits.displayTemperature(r["celsius"] as! Double, TemperatureUnit(rawValue: r["unit"] as! String)!),
                           r["expected"] as! Int, "\(r)")
        }
        for r in symbols["wind"] as! [[String: Any]] {
            XCTAssertEqual(WeatherUnits.displayWind(r["ms"] as! Double, WindUnit(rawValue: r["unit"] as! String)!), r["expected"] as! Int, "\(r)")
        }
        for (region, units) in symbols["regions"] as! [String: [String]] {
            let u = WeatherUnits.forRegion(region == "_none" ? nil : region)
            XCTAssertEqual([u.temperature.rawValue, u.wind.rawValue], units, region)
        }
        XCTAssertEqual(WeatherUnits.forRegion("US").temperature(35.4), 96)
        XCTAssertEqual(WeatherUnits.forRegion("GB").wind(5.7), 13)
        XCTAssertEqual(TemperatureUnit.fahrenheit.stringKey, "weather_temp_f")
        XCTAssertEqual(WindUnit.kmh.stringKey, "weather_wind_kmh")
    }

    // MARK: Look

    func testLookMatchesTokens() {
        let w = Fixtures.json("shared/design/tokens.json")["weather"] as! [String: Any]
        func number(_ a: Any?) -> Double { (a as? Double) ?? (a as? Int).map(Double.init) ?? (a as! NSNumber).doubleValue }
        func table(_ name: String) -> [String: Double] { (w[name] as! [String: Any]).mapValues(number) }
        let byKey: (Dictionary<WeatherCondition, Double>) -> [String: Double] = { d in
            Dictionary(uniqueKeysWithValues: d.map { (k, v) in (Self.tokenName(k), v) })
        }
        XCTAssertEqual(byKey(WeatherLook.mix), table("mix"))
        XCTAssertEqual(byKey(WeatherLook.glow), table("glow"))
        XCTAssertEqual(byKey(WeatherLook.stars), table("stars"))
        XCTAssertEqual(byKey(WeatherLook.clouds), table("clouds"))
        XCTAssertEqual(byKey(WeatherLook.deck), table("deck"))
        XCTAssertEqual(byKey(WeatherLook.fogBands), table("fogBand"))
        XCTAssertEqual(WeatherLook.cloudsClearNight, number(w["cloudsClearNight"]))
        XCTAssertEqual(WeatherLook.showersMix, number(w["showersMix"]))
        XCTAssertEqual(Double(WeatherLook.rainStreaksMax), number(w["rainStreaks"]))
        XCTAssertEqual(Double(WeatherLook.snowFlakesMax), number(w["snowFlakes"]))
        for c in WeatherCondition.allCases where c != .clear {
            XCTAssertNotNil((w["veilDark"] as! [String: Any])[Self.tokenName(c)], "\(c)")
            XCTAssertNotNil((w["veilLight"] as! [String: Any])[Self.tokenName(c)], "\(c)")
        }
    }

    private static func tokenName(_ c: WeatherCondition) -> String { c == .partlyCloudy ? "partlyCloudy" : c.rawValue }

    func testLookPerCondition() {
        XCTAssertEqual(WeatherLook.of(nil), .normal)
        XCTAssertEqual(WeatherLook.of(nil), WeatherLook(veilMix: 0, glowStrength: 1, starOpacity: 1, cloudOpacity: nil, deckOpacity: 0,
                                                        rainStreaks: 0, snowFlakes: 0, fogBand: 0, lightning: false))
        XCTAssertEqual(WeatherLook.of(Weather.symbol("clearsky")).cloudOpacity, 0.25)
        XCTAssertEqual(WeatherLook.of(Weather.symbol("clearsky_night"), night: true).cloudOpacity, 0.15)
        XCTAssertNil(WeatherLook.of(Weather.symbol("partlycloudy")).cloudOpacity)
        let heavy = WeatherLook.of(Weather.symbol("heavyrain"))
        XCTAssertEqual(heavy.rainStreaks, 60)
        XCTAssertEqual(heavy.deckOpacity, 0.9)
        XCTAssertEqual(heavy.veilMix, 0.55, accuracy: 1e-9)
        let showers = WeatherLook.of(Weather.symbol("rainshowers"))
        XCTAssertEqual(showers.rainStreaks, 45)
        XCTAssertEqual(showers.veilMix, 0.44, accuracy: 1e-9)
        let thunder = WeatherLook.of(Weather.symbol("lightssnowshowersandthunder"))
        XCTAssertTrue(thunder.lightning)
        XCTAssertEqual(thunder.snowFlakes, 28)
        XCTAssertEqual(thunder.rainStreaks, 0)
        XCTAssertEqual(thunder.deckOpacity, 1)
        XCTAssertEqual(WeatherLook.of(Weather.symbol("snow")).snowFlakes, 41)
        XCTAssertEqual(WeatherLook.of(Weather.symbol("fog")).fogBand, 1)
        XCTAssertEqual(WeatherLook.of(Weather.symbol("fog")).deckOpacity, 0)
        XCTAssertFalse(WeatherLook.of(Weather.symbol("heavyrain")).lightning)
        XCTAssertEqual(WeatherCondition.partlyCloudy.stringKey, "weather_partly_cloudy")
    }
}

private extension Double {
    var clamped90: Double { Swift.min(90, Swift.max(-90, self)) }
}
