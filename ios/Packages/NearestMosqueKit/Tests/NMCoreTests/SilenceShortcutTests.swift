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

    // The notification button: the silence under way, or that prayer's own if it begins within the hour.
    func testShortcutEndForButtonCurrentAndSoon() {
        let ds = days(thursday)
        let dhuhr = ds[1][.dhuhr]!
        let s = SilenceSettings()  // off in the app, from the iqama (Dhuhr: 20 minutes after the Adhan, 15 to pray)
        let iq = IqamaSettings()
        let end = dhuhr.addingTimeInterval(35 * 60)
        func button(_ now: Date, _ e: PrayerEvent? = .dhuhr, _ d: CivilDate? = thursday, maxLead: TimeInterval = 60 * 60) -> Date? {
            s.shortcutEndForButton(ds, iqama: iq, now: now, event: e, date: d, maxLead: maxLead)
        }
        // Under way, whichever notification the button is on.
        XCTAssertEqual(button(dhuhr.addingTimeInterval(25 * 60)), end)
        XCTAssertEqual(button(dhuhr.addingTimeInterval(25 * 60), .fajr), end)
        XCTAssertEqual(button(dhuhr.addingTimeInterval(25 * 60), nil, nil), end)
        // Ending within the margin: over (the Shortcut, run a moment later, would get Asr's).
        XCTAssertEqual(button(end.addingTimeInterval(-91)), end)
        XCTAssertNil(button(end.addingTimeInterval(-30)))
        XCTAssertNil(button(end.addingTimeInterval(-1)))
        // At the Adhan, before a silence from the iqama: that prayer's.
        XCTAssertEqual(button(dhuhr), end)
        // The prayer's own beginning in exactly an hour, and in just over an hour.
        let start = dhuhr.addingTimeInterval(20 * 60)
        XCTAssertEqual(button(start.addingTimeInterval(-60 * 60)), end)
        XCTAssertNil(button(start.addingTimeInterval(-61 * 60)))
        // A shorter lead.
        XCTAssertNil(button(dhuhr, maxLead: 10 * 60))
        XCTAssertEqual(button(dhuhr, maxLead: 20 * 60), end)
        XCTAssertNil(s.shortcutEndForButton([], iqama: iq, now: dhuhr, event: .dhuhr, date: thursday))
    }

    // A button from another prayer, or from that prayer on another day, before a silence: nothing.
    func testShortcutEndForButtonStale() {
        let ds = days(thursday)
        let s = SilenceSettings(enabled: true)
        let iq = IqamaSettings()
        let isha = ds[1][.isha]!, maghrib = ds[1][.maghrib]!
        // Isha's button tapped after its silence (the Shortcut alone would silence until after Fajr).
        let ishaEnd = isha.addingTimeInterval(35 * 60)
        let fajrEnd = ds[2][.fajr]!.addingTimeInterval(40 * 60)
        let late = ishaEnd.addingTimeInterval(60)
        XCTAssertEqual(s.shortcutEnd(ds, iqama: iq, now: late), fajrEnd)
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: late, event: .isha, date: thursday))
        // Maghrib's iqama button tapped 55 minutes after its Adhan: Isha's silence is within the hour, not Maghrib's.
        let now = maghrib.addingTimeInterval(55 * 60)
        XCTAssertLessThan(isha.addingTimeInterval(20 * 60).timeIntervalSince(now), 60 * 60)
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: now, event: .maghrib, date: thursday))
        // From the Adhan, Maghrib's button half an hour before Isha's Adhan: nothing; Isha's own at its Adhan: Isha's.
        let fromAdhan = SilenceSettings(enabled: true, start: .adhan)
        XCTAssertNil(fromAdhan.shortcutEndForButton(ds, iqama: iq, now: isha.addingTimeInterval(-30 * 60), event: .maghrib, date: thursday))
        XCTAssertEqual(fromAdhan.shortcutEndForButton(ds, iqama: iq, now: isha, event: .isha, date: thursday), ishaEnd)
        // Yesterday's Isha button at today's Isha Adhan, before a silence from the iqama: nothing.
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: isha, event: .isha, date: thursday.adding(days: -1)))
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: isha, event: .isha, date: nil))
        XCTAssertEqual(s.shortcutEndForButton(ds, iqama: iq, now: isha, event: .isha, date: thursday), ishaEnd)
        // Maghrib's button tapped during Isha's silence: the silence under way.
        XCTAssertEqual(s.shortcutEndForButton(ds, iqama: iq, now: isha.addingTimeInterval(25 * 60), event: .maghrib, date: thursday), ishaEnd)
    }

    // Friday: from the Adhan, for the minutes chosen; after it, Asr is hours away; another prayer's button: nothing.
    func testShortcutEndForButtonFriday() {
        let ds = days(friday)
        let dhuhr = ds[1][.dhuhr]!
        let s = SilenceSettings(fridayMinutes: 60)
        let iq = IqamaSettings()
        let end = dhuhr.addingTimeInterval(60 * 60)
        XCTAssertEqual(s.shortcutEndForButton(ds, iqama: iq, now: dhuhr.addingTimeInterval(-60), event: .dhuhr, date: friday), end)
        XCTAssertEqual(s.shortcutEndForButton(ds, iqama: iq, now: dhuhr.addingTimeInterval(45 * 60), event: .dhuhr, date: friday), end)
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: dhuhr.addingTimeInterval(-60 * 60), event: .fajr, date: friday))
        XCTAssertNil(s.shortcutEndForButton(ds, iqama: iq, now: end.addingTimeInterval(60), event: .dhuhr, date: friday))
    }

    // Only the prayers chosen for silence count; with none chosen, all of them.
    func testShortcutEndForButtonChosenPrayers() {
        let ds = days(thursday)
        let iq = IqamaSettings()
        let asr = ds[1][.asr]!, maghrib = ds[1][.maghrib]!
        var onlyMaghrib = SilenceSettings(enabled: true)
        for e in PrayerEvent.prayers where e != .maghrib { onlyMaghrib.set(e, false) }
        let duringAsr = asr.addingTimeInterval(25 * 60)
        XCTAssertNil(onlyMaghrib.shortcutEndForButton(ds, iqama: iq, now: duringAsr, event: .asr, date: thursday))
        XCTAssertEqual(onlyMaghrib.shortcutEndForButton(ds, iqama: iq, now: maghrib, event: .maghrib, date: thursday), maghrib.addingTimeInterval(25 * 60))
        XCTAssertEqual(SilenceSettings(enabled: true).shortcutEndForButton(ds, iqama: iq, now: duringAsr, event: .asr, date: thursday),
                       asr.addingTimeInterval(35 * 60))
        var none = SilenceSettings(enabled: true)
        for e in PrayerEvent.prayers { none.set(e, false) }
        XCTAssertEqual(none.shortcutEndForButton(ds, iqama: iq, now: duringAsr, event: .asr, date: thursday), asr.addingTimeInterval(35 * 60))
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
        // A saved name wins over the default (the default in another language included).
        XCTAssertEqual(SilenceSettings.shortcutName("Prayer silence", default: "صمت الصلاة"), "Prayer silence")
        // Typed: saved as it is, the default too (it then stays when the language changes).
        XCTAssertEqual(SilenceSettings.storedShortcutName("Quiet ", current: nil), "Quiet")
        XCTAssertEqual(SilenceSettings.storedShortcutName(" Prayer silence ", current: nil), "Prayer silence")
        XCTAssertEqual(SilenceSettings.storedShortcutName("Quiet", current: "Prayer silence"), "Quiet")
        // An empty field keeps what is saved, and saves nothing when nothing is.
        XCTAssertEqual(SilenceSettings.storedShortcutName("", current: "Quiet"), "Quiet")
        XCTAssertEqual(SilenceSettings.storedShortcutName("  ", current: "Quiet"), "Quiet")
        XCTAssertNil(SilenceSettings.storedShortcutName("", current: nil))
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
