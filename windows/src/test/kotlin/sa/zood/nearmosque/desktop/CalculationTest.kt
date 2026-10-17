package sa.zood.nearmosque.desktop

import sa.zood.nearmosque.core.AsrMadhab
import sa.zood.nearmosque.core.HighLatRule
import sa.zood.nearmosque.core.Method
import sa.zood.nearmosque.core.PolarRule
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.PrayerMoment
import sa.zood.nearmosque.core.ScheduleStatus
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The high-latitude and polar rules and the minute adjustments: the times they give, and how they are kept. */
class CalculationTest {
    private val tromso get() = Cities.byId(3133895)!!
    private val riyadh get() = Cities.index.search("Riyadh", 1).first()
    private val polarNight = LocalDate.of(2026, 12, 21)
    private val day = LocalDate.of(2026, 10, 12)

    /** A settings file in a fresh folder: the tests never read or write the person's own settings. */
    private fun settingsFile(): File =
        File(Files.createTempDirectory("nm-calc").toFile().apply { deleteOnExit() }, "settings.properties")

    @Test fun polarNightIsUnavailableUntilTheNearestLatitudeIsChosen() {
        Strings.language = "en"
        val clock = Clock(tromso, DesktopSettings(cityId = tromso.id))
        val noon = polarNight.atTime(12, 0).atZone(clock.zone).toInstant()
        val none = clock.day(polarNight)
        assertEquals(ScheduleStatus.Unavailable, none.status)
        assertTrue(none.times.isEmpty(), "no times are invented")
        assertNull(clock.moment(noon), "no countdown, so no moment card")
        assertEquals(t("app_name"), trayStatus(clock, noon))

        val nearest = Clock(tromso, DesktopSettings(cityId = tromso.id, polarRule = PolarRule.NEAREST_LATITUDE))
        val est = nearest.day(polarNight)
        assertEquals(ScheduleStatus.Estimated(48.5), est.status)
        assertEquals(6, est.times.size)
        assertNotNull(nearest.moment(noon), "the estimated times drive the countdown")
        // The reminders follow the estimated times, as on the phone.
        val sent = mutableListOf<String>()
        val quiet = DesktopSettings(cityId = tromso.id, polarRule = PolarRule.NEAREST_LATITUDE, adhan = false)
        Reminders { title, _ -> sent += title }.tick(nearest, quiet, est[PrayerEvent.DHUHR]!!.plusSeconds(10))
        assertEquals(listOf(t("prayer_dhuhr")), sent)
    }

    @Test fun highLatitudeRuleReachesTheCalculation() {
        // Oslo in June: twilight lasts all night, so the rule decides Fajr and Isha.
        val oslo = Cities.index.search("Oslo", 1).first()
        val june = LocalDate.of(2026, 6, 10)
        val fajr = HighLatRule.entries.associateWith { r -> Clock(oslo, DesktopSettings(highLatitudeRule = r)).day(june)[PrayerEvent.FAJR] }
        assertTrue(fajr.values.all { it != null })
        assertTrue(fajr.values.toSet().size > 1, "the rules give different Fajr times: $fajr")
        assertEquals(
            Clock(oslo, DesktopSettings()).day(june)[PrayerEvent.DHUHR],
            Clock(oslo, DesktopSettings(highLatitudeRule = HighLatRule.SEVENTH_OF_THE_NIGHT)).day(june)[PrayerEvent.DHUHR],
            "Dhuhr does not depend on the rule",
        )
    }

    @Test fun minuteAdjustmentsMoveOnlyTheirOwnTime() {
        val base = Clock(riyadh, DesktopSettings(cityId = riyadh.id)).day(day)
        val settings = DesktopSettings(cityId = riyadh.id, offsets = mapOf(PrayerEvent.DHUHR to 5, PrayerEvent.SUNRISE to -2))
        val clock = Clock(riyadh, settings)
        val moved = clock.day(day)
        for (e in PrayerEvent.entries) {
            val want = when (e) { PrayerEvent.DHUHR -> 300L; PrayerEvent.SUNRISE -> -120L; else -> 0L }
            assertEquals(want, Duration.between(base[e], moved[e]).seconds, "$e")
        }
        // The iqama follows the adjusted Adhan.
        val dhuhr = moved[PrayerEvent.DHUHR]!!
        val m = clock.moment(dhuhr.plusSeconds(60))!!
        assertEquals(PrayerMoment.Stage.TO_IQAMA, m.stage)
        assertEquals(dhuhr, m.adhan)
        // The notification comes at the adjusted time, not at the calculated one.
        Strings.language = "en"
        val quiet = settings.copy(adhan = false)
        val sent = mutableListOf<String>()
        val r = Reminders { title, _ -> sent += title }
        r.tick(clock, quiet, base[PrayerEvent.DHUHR]!!.plusSeconds(10))
        assertTrue(sent.isEmpty(), "nothing at the unadjusted Dhuhr")
        r.tick(clock, quiet, dhuhr.plusSeconds(10))
        assertEquals(listOf("Dhuhr"), sent)
    }

    @Test fun ramadanIshaCanBeTurnedOff() {
        // 1 Ramadan 1448 is around 8 February 2027; Riyadh uses Umm al-Qura, Isha 90 (in Ramadan 120) minutes after Maghrib.
        val ramadan = LocalDate.of(2027, 2, 15)
        val on = Clock(riyadh, DesktopSettings(cityId = riyadh.id)).day(ramadan)
        val off = Clock(riyadh, DesktopSettings(cityId = riyadh.id, ramadanIsha = false)).day(ramadan)
        assertTrue(on.ramadanIshaApplied)
        assertFalse(off.ramadanIshaApplied)
        assertEquals(1800L, Duration.between(off[PrayerEvent.ISHA], on[PrayerEvent.ISHA]).seconds)
    }

    @Test fun storeRoundTrip() {
        val file = settingsFile()
        val s = DesktopSettings(
            cityId = tromso.id, language = "ar", method = Method.MUSLIM_WORLD_LEAGUE, madhab = AsrMadhab.HANAFI,
            highLatitudeRule = HighLatRule.SEVENTH_OF_THE_NIGHT, polarRule = PolarRule.NEAREST_LATITUDE,
            offsets = mapOf(PrayerEvent.FAJR to 2, PrayerEvent.SUNRISE to -1, PrayerEvent.ISHA to 5), ramadanIsha = false,
        )
        Store.save(s, FilePreferences(file))
        val p = FilePreferences(file)
        assertEquals("SEVENTH_OF_THE_NIGHT", p.get("highLatRule", null))
        assertEquals("NEAREST_LATITUDE", p.get("polarRule", null))
        assertEquals("FAJR=2,SUNRISE=-1,ISHA=5", p.get("offsets", null))
        assertFalse(p.getBoolean("ramadanIsha", true))
        assertEquals(s, Store.load(p))
        // Back to the defaults: still readable, and nothing moves.
        Store.save(DesktopSettings(language = "en"), FilePreferences(file))
        val again = FilePreferences(file)
        assertEquals("", again.get("offsets", null))
        assertEquals(DesktopSettings(language = "en"), Store.load(again))
    }

    @Test fun anOlderSaveLoadsWithTodaysBehaviour() {
        val file = settingsFile()
        // Written by a build before these settings: none of the new keys.
        file.writeText(
            """
            cityId=108410
            language=en
            madhab=SHAFI
            hijriAdjustment=1
            adhan=true
            silent=ASR
            iqamaMinutes=FAJR=25,DHUHR=20
            """.trimIndent(),
        )
        val s = Store.load(FilePreferences(file))
        assertEquals(108410L, s.cityId)
        assertEquals(1, s.hijriAdjustment)
        assertEquals(setOf(PrayerEvent.ASR), s.silent)
        assertEquals(HighLatRule.AUTO, s.highLatitudeRule)
        assertEquals(PolarRule.UNAVAILABLE, s.polarRule)
        assertEquals(emptyMap(), s.offsets)
        assertTrue(s.ramadanIsha)
        // Saving keeps every older key where an older build looks for it.
        Store.save(s, FilePreferences(file))
        val p = FilePreferences(file)
        assertEquals("FAJR=25,DHUHR=20", p.get("iqamaMinutes", null))
        assertEquals(1, p.getInt("hijriAdjustment", 0))
        assertEquals("ASR", p.get("silent", null))
    }

    @Test fun badValuesFallBackToDefaults() {
        val file = settingsFile()
        file.writeText("language=en\nhighLatRule=FOO\npolarRule=\noffsets=FAJR=abc,XXX=3,ISHA=99,DHUHR=-45,ASR=0,MAGHRIB,=\nramadanIsha=maybe\n")
        val s = Store.load(FilePreferences(file))
        assertEquals(HighLatRule.AUTO, s.highLatitudeRule)
        assertEquals(PolarRule.UNAVAILABLE, s.polarRule)
        assertEquals(mapOf(PrayerEvent.ISHA to 30, PrayerEvent.DHUHR to -30), s.offsets)
        assertTrue(s.ramadanIsha)
        // A store that cannot be written keeps the choice for this run without stopping the app.
        val blocked = File(settingsFile().parentFile, "blocked").apply { writeText("a file, not a folder") }
        Store.save(s, FilePreferences(File(blocked, "settings.properties")))
    }

    @Test fun adjustmentLabels() {
        Strings.language = "en"
        assertEquals("0 min", offset(0))
        assertEquals("⁦+5⁩ min", offset(5))
        assertEquals("⁦−30⁩ min", offset(-30))
        Strings.language = "ar"
        assertEquals("⁦−2⁩ د", offset(-2), "the sign stays left of the number in Arabic")
        Strings.language = "en"
    }
}
