import XCTest
@testable import NMCore

/// Mirrors android/core SilenceTest.kt.
final class SilenceTests: XCTestCase {
    private let calc = PrayerCalculator()
    private let makkah = LatLng(21.4225, 39.8262)!
    private let zone = TimeZone(identifier: "Asia/Riyadh")!
    // Thursday 15, Friday 16 October 2026.
    private let thursday = CivilDate(year: 2026, month: 10, day: 15)
    private func days(_ d: CivilDate) -> [DaySchedule] {
        (-1...1).map { calc.schedule(makkah, date: d.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) }
    }

    func testOffByDefault() throws {
        XCTAssertFalse(SilenceSettings().enabled)
        XCTAssertEqual(try JSONDecoder().decode(SilenceSettings.self, from: Data("{}".utf8)), SilenceSettings())
        XCTAssertTrue(SilenceSettings().windows(days(thursday), iqama: IqamaSettings(), after: .distantPast).isEmpty)
    }

    func testWeekday() {
        XCTAssertEqual(thursday.weekday, 5)
        XCTAssertEqual(thursday.adding(days: 1).weekday, 6)
    }

    // From the iqama to the end of the prayer.
    func testFromIqama() {
        let ds = days(thursday)
        let dhuhr = ds[1][.dhuhr]!
        let s = SilenceSettings(enabled: true)
        let w = s.windows(ds, iqama: IqamaSettings(), after: dhuhr).first!
        XCTAssertEqual(w.event, .dhuhr)
        XCTAssertEqual(w.start, dhuhr.addingTimeInterval(20 * 60))
        XCTAssertEqual(w.end, dhuhr.addingTimeInterval(35 * 60))
        XCTAssertFalse(w.friday)
        XCTAssertNil(s.current(ds, iqama: IqamaSettings(), now: dhuhr.addingTimeInterval(60)))
        XCTAssertEqual(s.current(ds, iqama: IqamaSettings(), now: dhuhr.addingTimeInterval(25 * 60)), w)
        XCTAssertNil(s.current(ds, iqama: IqamaSettings(), now: dhuhr.addingTimeInterval(36 * 60)))
    }

    func testFromAdhan() {
        let ds = days(thursday)
        let asr = ds[1][.asr]!
        let w = SilenceSettings(enabled: true, start: .adhan).windows(ds, iqama: IqamaSettings(), after: asr).first!
        XCTAssertEqual(w.event, .asr)
        XCTAssertEqual(w.start, asr)
        XCTAssertEqual(w.end, asr.addingTimeInterval(35 * 60))
    }

    // Friday: from the Dhuhr Adhan for the khutbah and the prayer.
    func testFriday() {
        let friday = thursday.adding(days: 1)
        let ds = days(friday)
        let dhuhr = ds[1][.dhuhr]!
        let w = SilenceSettings(enabled: true, fridayMinutes: 75).windows(ds, iqama: IqamaSettings(), after: dhuhr).first!
        XCTAssertTrue(w.friday)
        XCTAssertEqual(w.start, dhuhr)
        XCTAssertEqual(w.end, dhuhr.addingTimeInterval(75 * 60))
    }

    func testOnlyChosenPrayers() {
        var s = SilenceSettings(enabled: true)
        s.set(.fajr, false)
        s.set(.isha, false)
        let ds = days(thursday)
        let events = s.windows(ds, iqama: IqamaSettings(), after: ds[1][.fajr]!.addingTimeInterval(-3600)).map(\.event)
        XCTAssertFalse(events.contains(.fajr))
        XCTAssertFalse(events.contains(.isha))
        XCTAssertEqual(Array(events.prefix(3)), [.dhuhr, .asr, .maghrib])
    }

    // Never past the next Adhan, whatever the waits are set to.
    func testEndsByNextAdhan() {
        let ds = days(thursday)
        let maghrib = ds[1][.maghrib]!, isha = ds[1][.isha]!
        var iq = IqamaSettings()
        iq.setWait(.maghrib, 45)
        iq.prayerMinutes = 60
        let w = SilenceSettings(enabled: true).windows(ds, iqama: iq, after: maghrib).first!
        XCTAssertEqual(w.event, .maghrib)
        XCTAssertLessThanOrEqual(w.end, isha)
    }
}
