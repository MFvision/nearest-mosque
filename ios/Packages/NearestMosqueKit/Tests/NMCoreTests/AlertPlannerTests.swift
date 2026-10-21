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

    // Every reminder and sound is independent: one on, the others stay off.
    func testAdhanAndRemindersAreIndependent() {
        var a = AlertSettings()
        a.adhan = true               // Dhuhr to Isha; the Fajr Adhan stays off
        a.before10 = true            // 10 minutes on, 5 minutes off
        a.setSounds(.maghrib, PrayerSounds(adhan: false, before10: true, before5: true, near: true))
        let p = AlertPlanner.plan([days[0]], now: now, atPrayer: [], alerts: a) { _ in false }
        let adhans = p.filter { $0.kind == .adhan }.map(\.event)
        XCTAssertEqual(adhans, [.dhuhr, .asr, .isha])
        XCTAssertEqual(p.filter { $0.kind == .before10 }.count, 5)
        XCTAssertTrue(p.allSatisfy { $0.kind != .before5 && $0.kind != .near && $0.kind != .atPrayer })
    }

    func testBellWithoutAdhanAndAdhanWithoutBell() {
        var a = AlertSettings()
        a.fajrAdhan = true
        let p = AlertPlanner.plan([days[0]], now: now, atPrayer: [.maghrib], alerts: a) { _ in false }
        XCTAssertEqual(p.map(\.kind), [.adhan, .atPrayer])
        XCTAssertEqual(p.map(\.event), [.fajr, .maghrib])
    }

    func testFajrAdhanFromEitherMosque() {
        var a = AlertSettings()
        a.fajrAdhan = true
        a.fajrAdhanSource = .madinah
        XCTAssertTrue(a.adhanPlays(.fajr))
        a.fajrAdhan = false
        XCTAssertFalse(a.adhanPlays(.fajr))
    }

    func testRemindersBeforeThePrayer() {
        var a = AlertSettings()
        a.before5 = true
        a.near = true
        a.nearMinutes = 20
        let d = days[0]
        let p = AlertPlanner.plan([d], now: now, atPrayer: [], alerts: a) { _ in false }.filter { $0.event == .asr }
        XCTAssertEqual(p.map(\.kind), [.near, .before5])
        XCTAssertEqual(d[.asr]!.timeIntervalSince(p[0].at), 1200)
        XCTAssertEqual(d[.asr]!.timeIntervalSince(p[1].at), 300)
    }

    func testSalawatAndKahfOnFridayOnlyQuranAndSleepDaily() {
        var a = AlertSettings()
        a.salawat = true
        a.salawatMinutes = 11 * 60
        a.kahf = true
        a.quran = true
        a.bedtimeMinutes = 22 * 60
        let p = AlertPlanner.plan(days, now: now, atPrayer: [], alerts: a) { _ in false }
        XCTAssertEqual(p.filter { $0.kind == .salawat }.map(\.date), [start.adding(days: 1)])
        XCTAssertTrue(p.filter { $0.kind == .salawat || $0.kind == .kahf }.allSatisfy { $0.date.isFriday })
        XCTAssertEqual(p.filter { $0.kind == .quran }.count, 3)
        XCTAssertEqual(p.filter { $0.kind == .sleep }.count, 3)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let s = p.first { $0.kind == .salawat }!
        XCTAssertEqual(cal.component(.hour, from: s.at), 11)
    }

    func testOldEarlyReminderMovesToTheNewOnes() throws {
        let ten = try JSONDecoder().decode(AlertSettings.self, from: Data(#"{"minutesBefore":10}"#.utf8))
        XCTAssertTrue(ten.before10)
        XCTAssertFalse(ten.before5)
        XCTAssertEqual(ten.minutesBefore, 0)
        let twenty = try JSONDecoder().decode(AlertSettings.self, from: Data(#"{"minutesBefore":20}"#.utf8))
        XCTAssertTrue(twenty.near)
        XCTAssertEqual(twenty.nearMinutes, 20)
        let round = try JSONDecoder().decode(AlertSettings.self, from: JSONEncoder().encode(twenty))
        XCTAssertEqual(round, twenty)
    }

    func testSunriseBellGivesANotificationAtSunrise() {
        let d = days[0]
        let p = AlertPlanner.plan([d], now: now, atPrayer: [.sunrise], alerts: AlertSettings()) { _ in false }
        XCTAssertEqual(p.map(\.event), [.sunrise])
        XCTAssertEqual(p[0].at, d[.sunrise])
        XCTAssertEqual(p[0].kind, .atPrayer)
    }

    // iOS keeps 60: the next day in full, then the Adhan as far ahead as it goes (no such limit on Android).
    func testBudgetKeepsTheAdhanFirst() {
        let fortnight = (0..<14).map { calc.schedule(makkah, date: start.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) }
        var a = AlertSettings()
        a.adhan = true
        a.fajrAdhan = true
        a.before10 = true
        let p = AlertPlanner.plan(fortnight, now: now, atPrayer: [], alerts: a, iqama: IqamaSettings()) { _ in false }
        XCTAssertEqual(p.count, 14 * 15)
        let b = AlertPlanner.budget(p, limit: 60, now: now)
        XCTAssertEqual(b.count, 60)
        let edge = now.addingTimeInterval(24 * 3600)
        XCTAssertEqual(b.filter { $0.at <= edge }, p.filter { $0.at <= edge })
        XCTAssertTrue(b.filter { $0.at > edge }.allSatisfy { $0.kind == .adhan })
        XCTAssertEqual(b.last?.date, start.adding(days: 9))
        XCTAssertEqual(b, b.sorted { $0.at < $1.at })
        XCTAssertEqual(AlertPlanner.budget(Array(p.prefix(10)), limit: 60, now: now), Array(p.prefix(10)))
    }

    func testOneSwitchForOnePrayer() {
        var a = AlertSettings()
        a.setPrayer(.maghrib, .before10, true)
        XCTAssertTrue(a.before10)
        XCTAssertEqual(PrayerEvent.prayers.filter { a.isOn($0, .before10) }, [.maghrib])
        var b = AlertSettings()
        b.setPrayer(.asr, .adhan, true)
        XCTAssertEqual(PrayerEvent.prayers.filter { b.isOn($0, .adhan) }, [.asr])
        XCTAssertFalse(b.fajrAdhan)
        var c = AlertSettings()
        c.before5 = true
        c.setPrayer(.isha, .before5, false)
        XCTAssertEqual(PrayerEvent.prayers.filter { c.isOn($0, .before5) }.count, 4)
    }
}
