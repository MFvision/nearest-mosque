import XCTest
@testable import NMCore

/// Mirrors android/core IqamaTest.kt.
final class IqamaTests: XCTestCase {
    private let calc = PrayerCalculator()
    private let makkah = LatLng(21.4225, 39.8262)!
    private let zone = TimeZone(identifier: "Asia/Riyadh")!
    private let start = CivilDate(year: 2026, month: 10, day: 15)
    private var days: [DaySchedule] { (-1...1).map { calc.schedule(makkah, date: start.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) } }
    private var today: DaySchedule { days[1] }

    func testDefaults() {
        let s = IqamaSettings()
        XCTAssertEqual(PrayerEvent.prayers.map { s.wait($0) }, [25, 20, 20, 10, 20])
        XCTAssertEqual(s.wait(.sunrise), 0)
        XCTAssertEqual(s.prayerMinutes, 15)
    }

    func testOldSavesDecodeWithDefaults() throws {
        let s = try JSONDecoder().decode(IqamaSettings.self, from: Data("{}".utf8))
        XCTAssertEqual(s, IqamaSettings())
    }

    // After the Adhan: the wait for the iqama, then the prayer, then the countdown to the next Adhan.
    func testStagesAfterDhuhr() {
        let s = IqamaSettings()
        let dhuhr = today[.dhuhr]!
        let before = calc.moment(days, now: dhuhr.addingTimeInterval(-60), iqama: s)!
        XCTAssertEqual(before.stage, .upcoming)
        XCTAssertEqual(before.event, .dhuhr)
        XCTAssertEqual(before.iqama, dhuhr.addingTimeInterval(20 * 60))

        let wait = calc.moment(days, now: dhuhr.addingTimeInterval(1), iqama: s)!
        XCTAssertEqual(wait.stage, .toIqama)
        XCTAssertEqual(wait.event, .dhuhr)
        XCTAssertEqual(wait.until, dhuhr.addingTimeInterval(20 * 60))
        XCTAssertEqual(wait.next.event, .asr)

        let prayer = calc.moment(days, now: dhuhr.addingTimeInterval(21 * 60), iqama: s)!
        XCTAssertEqual(prayer.stage, .prayer)
        XCTAssertEqual(prayer.until, dhuhr.addingTimeInterval(35 * 60))

        let after = calc.moment(days, now: dhuhr.addingTimeInterval(36 * 60), iqama: s)!
        XCTAssertEqual(after.stage, .upcoming)
        XCTAssertEqual(after.event, .asr)
        XCTAssertEqual(after.since, dhuhr.addingTimeInterval(35 * 60))
    }

    func testMaghribWaitIsTen() {
        let m = today[.maghrib]!
        let w = calc.moment(days, now: m.addingTimeInterval(5 * 60), iqama: IqamaSettings())!
        XCTAssertEqual(w.stage, .toIqama)
        XCTAssertEqual(w.until, m.addingTimeInterval(10 * 60))
    }

    func testOffGoesStraightToTheNextAdhan() {
        let dhuhr = today[.dhuhr]!
        let m = calc.moment(days, now: dhuhr.addingTimeInterval(1), iqama: IqamaSettings(enabled: false))!
        XCTAssertEqual(m.stage, .upcoming)
        XCTAssertEqual(m.event, .asr)
        XCTAssertNil(m.iqama)
        XCTAssertEqual(m.since, dhuhr)
    }

    func testNeverPastTheNextAdhan() {
        var s = IqamaSettings()
        s.setWait(.maghrib, 45)
        s.prayerMinutes = 30
        let isha = today[.isha]!   // 90 min after Maghrib with Umm al-Qura
        let m = calc.moment(days, now: isha.addingTimeInterval(-60), iqama: s)!
        XCTAssertEqual(m.stage, .upcoming)
        XCTAssertEqual(m.event, .isha)
        let w = calc.moment(days, now: today[.maghrib]!.addingTimeInterval(60), iqama: s)!
        XCTAssertLessThanOrEqual(w.until, isha)
    }

    func testStageChangesForATimeline() {
        let dhuhr = today[.dhuhr]!
        let c = calc.stageChanges(days, after: dhuhr.addingTimeInterval(-1), iqama: IqamaSettings())
        XCTAssertEqual(Array(c.prefix(4)), [dhuhr, dhuhr.addingTimeInterval(20 * 60), dhuhr.addingTimeInterval(35 * 60), today[.asr]!])
    }

    func testIqamaAlertFollowsTheAdhanOrBell() {
        var a = AlertSettings()
        a.adhan = true
        let now = today[.fajr]!.addingTimeInterval(-3600)
        let p = AlertPlanner.plan([today], now: now, atPrayer: [], alerts: a, iqama: IqamaSettings()) { _ in false }
        let iq = p.filter { $0.kind == .iqama }
        XCTAssertEqual(iq.map(\.event), [.dhuhr, .asr, .maghrib, .isha])
        XCTAssertEqual(iq[0].at, today[.dhuhr]!.addingTimeInterval(20 * 60))
        XCTAssertTrue(AlertPlanner.plan([today], now: now, atPrayer: [], alerts: a) { _ in false }.allSatisfy { $0.kind != .iqama })
    }
}
