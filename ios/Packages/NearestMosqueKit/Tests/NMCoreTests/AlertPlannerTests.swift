import XCTest
@testable import NMCore

/// Mirrors android/core AlertPlannerTest.kt.
final class AlertPlannerTests: XCTestCase {
    private let calc = PrayerCalculator()
    private let makkah = LatLng(21.4225, 39.8262)!
    private let zone = TimeZone(identifier: "Asia/Riyadh")!
    private let start = CivilDate(year: 2026, month: 10, day: 1)   // Thursday
    private var days: [DaySchedule] { (0...2).map { calc.schedule(makkah, date: start.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) } }
    private var now: Date { var c = Calendar(identifier: .gregorian); c.timeZone = zone; return c.date(from: DateComponents(year: 2026, month: 10, day: 1))! }

    func testAtPrayerAndEarlyReminders() {
        let p = AlertPlanner.plan(days, now: now, atPrayer: [.asr], alerts: AlertSettings(minutesBefore: 15)) { _ in false }
        XCTAssertEqual(p.count, 6)
        XCTAssertEqual(p.prefix(2).map(\.kind), [.before, .atPrayer])
        XCTAssertEqual(p[1].at.timeIntervalSince(p[0].at), 900)
    }

    func testFridayOnlyOnFriday() {
        let d = days
        let p = AlertPlanner.plan(d, now: now, atPrayer: [], alerts: AlertSettings(friday: true)) { _ in false }
        XCTAssertEqual(p.count, 1)
        XCTAssertTrue(p[0].date.isFriday)
        XCTAssertEqual(p[0].at, d[1][.dhuhr]!.addingTimeInterval(-45 * 60))
    }

    func testRamadanSuhoorAndIftarOnlyInRamadan() {
        let target = start.adding(days: 2)
        let p = AlertPlanner.plan(days, now: now, atPrayer: [], alerts: AlertSettings(ramadan: true)) { $0 == target }
        XCTAssertEqual(p.map(\.kind), [.suhoor, .iftar])
    }

    func testFajrAlarmEachDayAndNothingInThePast() {
        let d = days
        let p = AlertPlanner.plan(d, now: d[1][.fajr]!.addingTimeInterval(60), atPrayer: [], alerts: AlertSettings(fajrAlarmMinutesBefore: 20)) { _ in false }
        XCTAssertEqual(p.count, 1)
        XCTAssertEqual(p[0].at, d[2][.fajr]!.addingTimeInterval(-20 * 60))
    }
}
