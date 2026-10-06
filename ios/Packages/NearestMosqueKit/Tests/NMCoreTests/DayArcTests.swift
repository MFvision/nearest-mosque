import XCTest
@testable import NMCore

/// Mirrors android/core DayArcTest.kt.
final class DayArcTests: XCTestCase {
    private let calc = PrayerCalculator()
    private let makkah = LatLng(21.4225, 39.8262)!
    private let zone = TimeZone(identifier: "Asia/Riyadh")!
    private let date = CivilDate(year: 2026, month: 3, day: 21)
    private var days: [DaySchedule] {
        (-1...1).map { calc.schedule(makkah, date: date.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) }
    }

    func testDaylightHasDhuhrAndAsrBetweenSunriseAndMaghrib() throws {
        let d = days
        let sunrise = try XCTUnwrap(d[1][.sunrise]), maghrib = try XCTUnwrap(d[1][.maghrib])
        let noonish = sunrise.addingTimeInterval(maghrib.timeIntervalSince(sunrise) / 2)
        let arc = try XCTUnwrap(DayArc.at(noonish, days: d))
        XCTAssertTrue(arc.isDay)
        XCTAssertEqual(arc.startEvent, .sunrise)
        XCTAssertEqual(arc.fraction, 0.5, accuracy: 1e-6)
        XCTAssertEqual(arc.marks.map(\.event), [.dhuhr, .asr])
        XCTAssertEqual(arc.marks[0].fraction, 0.5, accuracy: 0.03)
    }

    func testEveningRunsFromMaghribToTomorrowsSunrise() throws {
        let d = days
        let arc = try XCTUnwrap(DayArc.at(try XCTUnwrap(d[1][.maghrib]).addingTimeInterval(60), days: d))
        XCTAssertFalse(arc.isDay)
        XCTAssertEqual(arc.end, d[2][.sunrise])
        XCTAssertEqual(arc.marks.map(\.event), [.isha, .fajr])
        XCTAssertEqual(arc.marks[1].at, d[2][.fajr])
    }

    func testBeforeSunriseRunsFromYesterdaysMaghrib() throws {
        let d = days
        let arc = try XCTUnwrap(DayArc.at(try XCTUnwrap(d[1][.sunrise]).addingTimeInterval(-60), days: d))
        XCTAssertFalse(arc.isDay)
        XCTAssertEqual(arc.start, d[0][.maghrib])
        XCTAssertEqual(arc.marks.last?.at, d[1][.fajr])
        XCTAssertGreaterThan(arc.fraction, 0.95)
    }

    func testPolarDayHasNoArc() {
        let tromso = LatLng(69.6496, 18.956)!
        let oslo = TimeZone(identifier: "Europe/Oslo")!
        let d = CivilDate(year: 2026, month: 6, day: 21)
        let polar = (-1...1).map { calc.schedule(tromso, date: d.adding(days: $0), zone: oslo, settings: PrayerSettings()) }
        let noon = polar[1].times.values.first ?? Date(timeIntervalSince1970: 1_782_036_000)
        XCTAssertNil(DayArc.at(noon, days: polar))
    }
}
