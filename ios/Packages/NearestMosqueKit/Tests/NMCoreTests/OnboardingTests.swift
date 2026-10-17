import XCTest
@testable import NMCore

/// Mirrors android/core OnboardingTest.kt.
final class OnboardingTests: XCTestCase {
    private let withLook = OnboardingFlow(hasLook: true)
    private let noLook = OnboardingFlow(hasLook: false)

    func testStepsInOrder() {
        XCTAssertEqual(withLook.steps.count, 13)
        XCTAssertEqual(noLook.steps.count, 12)
        XCTAssertFalse(noLook.steps.contains(.look))
        XCTAssertEqual(withLook.next(.adhan), .friday)
        XCTAssertEqual(noLook.next(.weather), .building)
        XCTAssertEqual(withLook.next(.day), .extras)
        XCTAssertNil(withLook.next(.done))
        XCTAssertEqual(OnboardingStep(rawValue: "where"), .place)
        XCTAssertEqual(OnboardingStep.friday.key, "friday")
    }

    func testBackTargets() {
        let expected: [(OnboardingStep, OnboardingStep)] = [
            (.place, .promise), (.asr, .place), (.adhan, .asr), (.friday, .adhan), (.weather, .friday), (.look, .weather),
            (.day, .look), (.extras, .day), (.more, .extras), (.privacy, .more),
        ]
        for (from, to) in expected { XCTAssertEqual(withLook.back(from), to, "back from \(from)") }
        XCTAssertEqual(noLook.back(.day), .weather)
        for s in [OnboardingStep.promise, .building, .done] { XCTAssertNil(withLook.back(s)) }
    }

    func testSkipTargets() {
        for s in [OnboardingStep.asr, .adhan, .friday, .weather, .look] { XCTAssertEqual(withLook.skip(s), .building) }
        XCTAssertEqual(withLook.skip(.extras), .privacy)
        XCTAssertEqual(withLook.skip(.more), .privacy)
        for s in [OnboardingStep.promise, .place, .building, .day, .privacy, .done] { XCTAssertNil(withLook.skip(s)) }
    }

    func testHeaderAndQuestions() {
        for s in [OnboardingStep.friday, .extras, .more, .privacy] { XCTAssertTrue(OnboardingFlow.showsHeader(s)) }
        XCTAssertFalse(OnboardingFlow.showsHeader(.promise))
        XCTAssertFalse(OnboardingFlow.showsHeader(.done))
        XCTAssertTrue(OnboardingFlow.isQuestion(.friday))
        XCTAssertTrue(OnboardingFlow.isQuestion(.look))
        XCTAssertFalse(OnboardingFlow.isQuestion(.extras))
        XCTAssertFalse(OnboardingFlow.isQuestion(.building))
    }

    func testMoreKeepsOrderAndShowsOnlyTheRest() {
        let all = Set(OnboardingFeature.allCases)
        let more = OnboardingFeature.more(available: all, shownEarlier: [.quiet])
        XCTAssertFalse(more.contains(.quiet))
        // The compass wins over the north bearing, the library over the classical books.
        XCTAssertFalse(more.contains(.qiblaNorth))
        XCTAssertFalse(more.contains(.classics))
        let order = OnboardingFeature.allCases
        XCTAssertEqual(more, more.sorted { order.firstIndex(of: $0)! < order.firstIndex(of: $1)! })
        XCTAssertEqual(OnboardingFeature.more(available: [.qiblaNorth, .classics, .hijri, .iqama]), [.iqama, .qiblaNorth, .classics, .hijri])
        XCTAssertTrue(OnboardingFeature.more(available: []).isEmpty)
    }

    private let riyadh = LatLng(24.7136, 46.6753)!
    private let riyadhZone = TimeZone(identifier: "Asia/Riyadh")!
    private let umm = PrayerSettings(method: .UMM_AL_QURA)
    private let calc = PrayerCalculator()

    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = riyadhZone
        return cal.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    func testFridayFromSaturdayIsTheComingFriday() throws {
        let f = try XCTUnwrap(FridayPreview.next(location: riyadh, zone: riyadhZone, now: at(2026, 10, 17, 9, 0), settings: umm, calculator: calc))
        let friday = CivilDate(year: 2026, month: 10, day: 23)
        XCTAssertEqual(CivilDate.of(f.dhuhr, in: riyadhZone), friday)
        XCTAssertEqual(f.dhuhr, calc.schedule(riyadh, date: friday, zone: riyadhZone, settings: umm)[.dhuhr])
        XCTAssertEqual(f.dhuhr.timeIntervalSince(f.reminder), Double(AlertSettings.fridayLeadMinutes) * 60, accuracy: 0.001)
    }

    func testFridayIsTodayUntilTheReminderPasses() throws {
        let morning = try XCTUnwrap(FridayPreview.next(location: riyadh, zone: riyadhZone, now: at(2026, 10, 23, 8, 0), settings: umm, calculator: calc))
        XCTAssertEqual(CivilDate.of(morning.dhuhr, in: riyadhZone), CivilDate(year: 2026, month: 10, day: 23))
        let noon = try XCTUnwrap(FridayPreview.next(location: riyadh, zone: riyadhZone, now: at(2026, 10, 23, 12, 0), settings: umm, calculator: calc))
        XCTAssertEqual(CivilDate.of(noon.dhuhr, in: riyadhZone), CivilDate(year: 2026, month: 10, day: 30))
    }

    func testAdhanChoiceRoundTrips() {
        var start = AlertSettings()
        start.before10 = true
        start.adhanSource = .madinah
        start.fajrAdhanSource = .madinah
        start.setSounds(.maghrib, PrayerSounds(adhan: false))
        for c in AdhanChoice.allCases {
            let a = start.with(c)
            XCTAssertEqual(a.adhanChoice, c)
            XCTAssertEqual(a.perPrayer, start.perPrayer)
            XCTAssertTrue(a.before10)
        }
        let makkah = start.with(.makkah)
        XCTAssertTrue(makkah.adhan && makkah.fajrAdhan && !makkah.shortAdhan)
        XCTAssertEqual(makkah.fajrAdhanSource, .makkah)
        // Short keeps the recordings; banner only keeps everything but the two Adhan switches.
        XCTAssertEqual(start.with(.short).adhanSource, .madinah)
        let banner = makkah.with(.short).with(.bannerOnly)
        XCTAssertFalse(banner.adhan || banner.fajrAdhan)
        XCTAssertTrue(banner.shortAdhan)
        XCTAssertEqual(AdhanChoice.bannerOnly.rawValue, "banner_only")
    }

    func testOnboardingChoiceDefaultsToMakkahOnlyForANewUser() {
        XCTAssertEqual(AlertSettings().onboardingChoice, .makkah)
        XCTAssertEqual(AlertSettings(friday: true).onboardingChoice, .bannerOnly)
        XCTAssertEqual(AlertSettings().with(.madinah).onboardingChoice, .madinah)
    }

    func testAsrTimesHanafiIsLater() throws {
        let date = CivilDate(year: 2026, month: 10, day: 17)
        let t = calc.asrTimes(riyadh, date: date, zone: riyadhZone, settings: umm)
        let shafi = try XCTUnwrap(t[.SHAFI]), hanafi = try XCTUnwrap(t[.HANAFI])
        XCTAssertGreaterThan(hanafi, shafi)
        XCTAssertEqual(shafi, calc.schedule(riyadh, date: date, zone: riyadhZone, settings: umm)[.asr])
    }

    func testWeatherPreviewSamples() {
        XCTAssertEqual(WeatherPreview.order.count, 7)
        let now = at(2026, 10, 17, 12, 0)
        for c in WeatherPreview.order {
            XCTAssertEqual(WeatherPreview.now(c, at: now).symbol.condition, c)
            XCTAssertEqual(Weather.symbol(WeatherPreview.code(c))?.condition, c)
        }
        let storm = WeatherPreview.now(.thunderstorm, at: now)
        XCTAssertEqual(storm.temperatureC, 19, accuracy: 1e-9)
        XCTAssertEqual(storm.windMs, 34 / 3.6, accuracy: 1e-9)
        XCTAssertEqual(WeatherPreview.now(.snow, at: now).temperatureC, -2, accuracy: 1e-9)
    }

    func testDayArcHalves() throws {
        let date = CivilDate(year: 2026, month: 10, day: 17)
        let days = (-1...1).map { calc.schedule(riyadh, date: date.adding(days: $0), zone: riyadhZone, settings: umm) }
        let noon = at(2026, 10, 17, 12, 30)
        let night = try XCTUnwrap(DayArc.at(noon, days: days, half: .night))
        XCTAssertFalse(night.isDay)
        XCTAssertEqual(night.start, days[1][.maghrib])
        XCTAssertEqual(night.fraction, 0, accuracy: 1e-9)
        XCTAssertEqual(DayArc.at(noon, days: days), DayArc.at(noon, days: days, half: .day))
        let evening = try XCTUnwrap(days[1][.maghrib]).addingTimeInterval(3600)
        let nextDay = try XCTUnwrap(DayArc.at(evening, days: days, half: .day))
        XCTAssertTrue(nextDay.isDay)
        XCTAssertEqual(nextDay.start, days[2][.sunrise])
        XCTAssertEqual(nextDay.fraction, 0, accuracy: 1e-9)
        let early = try XCTUnwrap(days[1][.fajr])
        XCTAssertEqual(DayArc.at(early, days: days, half: .night)?.start, days[0][.maghrib])
    }

    func testLargestCityInZone() {
        func city(_ id: Int, _ name: String, _ zone: String, _ population: Int) -> City {
            City(id: id, name: name, asciiName: name, arabicNames: [], countryCode: "SA", location: LatLng(Double(id), Double(id))!,
                 zoneId: zone, population: population)
        }
        let index = CityIndex([city(1, "Small", "Asia/Riyadh", 10), city(2, "Big", "Asia/Riyadh", 1000), city(3, "Other", "Asia/Dubai", 5000)])
        XCTAssertEqual(index.largest(inZone: "Asia/Riyadh")?.name, "Big")
        XCTAssertNil(index.largest(inZone: "Europe/Oslo"))
        XCTAssertNotNil(index.largest(inZone: "Asia/Dubai"))
    }
}
