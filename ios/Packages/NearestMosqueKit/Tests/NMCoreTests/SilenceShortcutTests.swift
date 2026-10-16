import XCTest
@testable import NMCore

final class SilenceShortcutTests: XCTestCase {
    private let calc = PrayerCalculator()
    private let makkah = LatLng(21.4225, 39.8262)!
    private let zone = TimeZone(identifier: "Asia/Riyadh")!
    // Thursday 15, Friday 16 October 2026.
    private let thursday = CivilDate(year: 2026, month: 10, day: 15)
    private var friday: CivilDate { thursday.adding(days: 1) }
    private func days(_ d: CivilDate) -> [DaySchedule] {
        (-1...1).map { calc.schedule(makkah, date: d.adding(days: $0), zone: zone, settings: PrayerSettings(method: .UMM_AL_QURA)) }
    }

    // The shortcut works with the setting off in the app, and with no prayer chosen.
    func testForShortcut() {
        var s = SilenceSettings(enabled: false, start: .adhan, fridayMinutes: 90)
        for e in PrayerEvent.prayers { s.set(e, false) }
        let f = s.forShortcut
        XCTAssertTrue(f.enabled)
        XCTAssertEqual(f.start, .adhan)
        XCTAssertEqual(f.fridayMinutes, 90)
        XCTAssertTrue(PrayerEvent.prayers.allSatisfy(f.includes))
        var some = SilenceSettings()
        some.set(.fajr, false)
        XCTAssertFalse(some.forShortcut.includes(.fajr))
    }

    // During the silence: its end. Before it (at the Adhan, silence from the iqama): the end of that prayer.
    func testShortcutEnd() {
        let ds = days(thursday)
        let dhuhr = ds[1][.dhuhr]!
        let s = SilenceSettings()  // off in the app, from the iqama
        let end = dhuhr.addingTimeInterval(35 * 60)
        XCTAssertEqual(s.shortcutEnd(ds, iqama: IqamaSettings(), now: dhuhr), end)
        XCTAssertEqual(s.shortcutEnd(ds, iqama: IqamaSettings(), now: dhuhr.addingTimeInterval(25 * 60)), end)
        // After it: the next prayer's.
        let asr = ds[1][.asr]!
        XCTAssertEqual(s.shortcutEnd(ds, iqama: IqamaSettings(), now: end.addingTimeInterval(60)), asr.addingTimeInterval(35 * 60))
        XCTAssertNil(s.shortcutEnd([], iqama: IqamaSettings(), now: dhuhr))
    }

    func testShortcutEndFriday() {
        let ds = days(friday)
        let dhuhr = ds[1][.dhuhr]!
        XCTAssertEqual(SilenceSettings(fridayMinutes: 60).shortcutEnd(ds, iqama: IqamaSettings(), now: dhuhr.addingTimeInterval(-60)),
                       dhuhr.addingTimeInterval(60 * 60))
    }

    func testOffersAction() {
        let iq = IqamaSettings()
        let fromIqama = SilenceSettings(enabled: true)
        XCTAssertTrue(fromIqama.offersAction(.iqama, event: .asr, date: thursday, iqama: iq))
        XCTAssertFalse(fromIqama.offersAction(.adhan, event: .asr, date: thursday, iqama: iq))
        XCTAssertFalse(fromIqama.offersAction(.before10, event: .asr, date: thursday, iqama: iq))
        // No iqama notification: the Adhan (or the bell) carries it.
        XCTAssertTrue(fromIqama.offersAction(.adhan, event: .asr, date: thursday, iqama: IqamaSettings(alert: false)))
        XCTAssertTrue(fromIqama.offersAction(.atPrayer, event: .asr, date: thursday, iqama: IqamaSettings(enabled: false)))
        // Friday prayer: from the Adhan.
        XCTAssertTrue(fromIqama.offersAction(.adhan, event: .dhuhr, date: friday, iqama: iq))
        XCTAssertFalse(fromIqama.offersAction(.iqama, event: .dhuhr, date: friday, iqama: iq))
        XCTAssertTrue(fromIqama.offersAction(.iqama, event: .dhuhr, date: thursday, iqama: iq))

        let fromAdhan = SilenceSettings(enabled: true, start: .adhan)
        XCTAssertTrue(fromAdhan.offersAction(.adhan, event: .maghrib, date: thursday, iqama: iq))
        XCTAssertTrue(fromAdhan.offersAction(.atPrayer, event: .maghrib, date: thursday, iqama: iq))
        XCTAssertFalse(fromAdhan.offersAction(.iqama, event: .maghrib, date: thursday, iqama: iq))
        XCTAssertFalse(fromAdhan.offersAction(.atPrayer, event: .sunrise, date: thursday, iqama: iq))
        XCTAssertFalse(fromAdhan.offersAction(.fajrAlarm, event: .fajr, date: thursday, iqama: iq))

        // Off, or a prayer left out: none.
        XCTAssertFalse(SilenceSettings(enabled: false).offersAction(.iqama, event: .asr, date: thursday, iqama: iq))
        var noFajr = SilenceSettings(enabled: true)
        noFajr.set(.fajr, false)
        XCTAssertFalse(noFajr.offersAction(.iqama, event: .fajr, date: thursday, iqama: iq))
    }

    func testShortcutName() {
        XCTAssertEqual(SilenceSettings.shortcutName(nil, default: "Prayer silence"), "Prayer silence")
        XCTAssertEqual(SilenceSettings.shortcutName("  ", default: "Prayer silence"), "Prayer silence")
        XCTAssertEqual(SilenceSettings.shortcutName(" Quiet ", default: "Prayer silence"), "Quiet")
        XCTAssertNil(SilenceSettings.storedShortcutName(" Prayer silence ", default: "Prayer silence"))
        XCTAssertNil(SilenceSettings.storedShortcutName("", default: "Prayer silence"))
        XCTAssertEqual(SilenceSettings.storedShortcutName("Quiet ", default: "Prayer silence"), "Quiet")
    }

    func testRunShortcutURL() {
        XCTAssertEqual(SilenceSettings.runShortcutURL("Prayer silence")?.absoluteString, "shortcuts://run-shortcut?name=Prayer%20silence")
        XCTAssertEqual(SilenceSettings.runShortcutURL("A&B+C=D/E?")?.absoluteString, "shortcuts://run-shortcut?name=A%26B%2BC%3DD%2FE%3F")
        XCTAssertEqual(SilenceSettings.runShortcutURL("صمت الصلاة")?.absoluteString,
                       "shortcuts://run-shortcut?name=%D8%B5%D9%85%D8%AA%20%D8%A7%D9%84%D8%B5%D9%84%D8%A7%D8%A9")
        let url = SilenceSettings.runShortcutURL("صمت الصلاة")!
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value, "صمت الصلاة")
        XCTAssertEqual(SilenceSettings.shortcutsAppURL.scheme, "shortcuts")
    }
}
