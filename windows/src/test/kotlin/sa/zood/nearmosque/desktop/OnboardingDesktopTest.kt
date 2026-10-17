package sa.zood.nearmosque.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import sa.zood.nearmosque.core.AdhanChoice
import sa.zood.nearmosque.core.AdhanSource
import sa.zood.nearmosque.core.OnboardingFeature
import sa.zood.nearmosque.core.OnboardingFlow
import sa.zood.nearmosque.core.OnboardingStep
import sa.zood.nearmosque.core.PrayerEvent
import sa.zood.nearmosque.core.WeatherCondition
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The first-run tour on the PC: who sees it, what its answers save, and what it shows. */
class OnboardingDesktopTest {
    private fun settingsFile(): File =
        File(Files.createTempDirectory("nm-onboarding").toFile().apply { deleteOnExit() }, "settings.properties")

    @Test fun onlyNewPeopleSeeTheTour() {
        assertFalse(Store.load(FilePreferences(settingsFile())).onboarded, "no settings at all: the first run, with the tour")
        // Settings saved before the tour existed belong to someone who already uses the app (the weather offer seen or not).
        val older = settingsFile().apply { writeText("cityId=108410\nlanguage=ar\nweatherOffered=true\n") }
        assertTrue(Store.load(FilePreferences(older)).onboarded)
        val unanswered = settingsFile().apply { writeText("language=en\n") }
        assertTrue(Store.load(FilePreferences(unanswered)).onboarded)
        // "Show welcome tour" saves an explicit false, which stays false; finishing saves true.
        val replay = settingsFile()
        Store.save(DesktopSettings(cityId = 108410, language = "en", onboarded = false), FilePreferences(replay))
        assertFalse(Store.load(FilePreferences(replay)).onboarded)
        Store.save(DesktopSettings(cityId = 108410, language = "en", onboarded = true), FilePreferences(replay))
        assertTrue(Store.load(FilePreferences(replay)).onboarded)
        // A damaged value reads as "settings exist": no tour forced on someone who has them.
        val damaged = settingsFile().apply { writeText("onboarded=perhaps\nlanguage=en\n") }
        assertTrue(Store.load(FilePreferences(damaged)).onboarded)
    }

    @Test fun adhanChoicesMapToTheSwitches() {
        val base = DesktopSettings(
            silent = setOf(PrayerEvent.ASR), adhanSource = AdhanSource.MADINAH, fajrAdhanSource = AdhanSource.MADINAH, shortAdhan = true,
        )
        val makkah = base.withAdhanChoice(AdhanChoice.MAKKAH)
        assertTrue(makkah.adhan)
        assertEquals(AdhanSource.MAKKAH, makkah.adhanSource)
        assertEquals(AdhanSource.MAKKAH, makkah.fajrAdhanSource)
        assertFalse(makkah.shortAdhan)
        assertEquals("fajr_makkah", Sound.adhanFile(PrayerEvent.FAJR, makkah))
        val madinah = base.copy(adhan = false).withAdhanChoice(AdhanChoice.MADINAH)
        assertTrue(madinah.adhan)
        assertEquals(AdhanSource.MADINAH, madinah.adhanSource)
        assertEquals("adhan_madinah", Sound.adhanFile(PrayerEvent.DHUHR, madinah))
        // The short Adhan keeps the recordings chosen before.
        val short = makkah.copy(adhanSource = AdhanSource.MADINAH).withAdhanChoice(AdhanChoice.SHORT)
        assertTrue(short.adhan && short.shortAdhan)
        assertEquals(AdhanSource.MADINAH, short.adhanSource)
        assertEquals("adhan_madinah_short", Sound.adhanFile(PrayerEvent.ISHA, short))
        // Banner only: the Adhan off, everything else as it was.
        val banner = base.withAdhanChoice(AdhanChoice.BANNER_ONLY)
        assertEquals(base.copy(adhan = false), banner)
        assertTrue(PrayerEvent.prayers.none { banner.alerts().adhanPlays(it) })
        // One prayer's own switch is never touched, and every choice reads back as itself.
        AdhanChoice.entries.forEach { c ->
            val s = base.withAdhanChoice(c)
            assertEquals(setOf(PrayerEvent.ASR), s.silent, "$c")
            assertEquals(c, s.alerts().adhanChoice, "$c")
        }
        assertEquals(AdhanChoice.MAKKAH, DesktopSettings().alerts().adhanChoice, "a new PC starts on Makkah")
    }

    @Test fun continueOnTheAdhanStepKeepsTheFajrRecording() {
        // Seen again from Settings: the Adhan from Makkah, and the Fajr Adhan from Madinah chosen in Settings.
        val s = DesktopSettings(adhanSource = AdhanSource.MAKKAH, fajrAdhanSource = AdhanSource.MADINAH)
        val shown = s.alerts().adhanChoice
        assertEquals(AdhanChoice.MAKKAH, shown)
        assertEquals(s, s.keepingShownAdhan(shown), "Continue with the choice shown changes nothing")
        assertEquals("fajr_madinah", Sound.adhanFile(PrayerEvent.FAJR, s.keepingShownAdhan(shown)))
        // A choice that differs from the settings is saved through the usual mapping.
        assertEquals(s.withAdhanChoice(AdhanChoice.MADINAH), s.keepingShownAdhan(AdhanChoice.MADINAH))
    }

    @Test fun theDesktopFlow() {
        val flow = OnboardingFlow(hasLook = false)
        assertEquals(12, flow.steps.size)
        assertFalse(OnboardingStep.LOOK in flow.steps, "one dark look on the desktop: no style step")
        assertEquals(OnboardingStep.WEATHER, flow.back(OnboardingStep.DAY))
        assertEquals(OnboardingStep.BUILDING, flow.next(OnboardingStep.WEATHER))
        // The rest of the app on a PC: the bearing from north (no compass), the classical books (no IslamHouse library).
        assertEquals(
            listOf(
                OnboardingFeature.IQAMA, OnboardingFeature.BEFORE_PRAYER, OnboardingFeature.FAJR_ALARM, OnboardingFeature.QIBLA_NORTH,
                OnboardingFeature.MOSQUES, OnboardingFeature.CLASSICS, OnboardingFeature.HIJRI, OnboardingFeature.START_AT_SIGN_IN,
            ),
            OnboardingFeature.more(desktopFeatures(startup = true)),
        )
        assertFalse(OnboardingFeature.START_AT_SIGN_IN in OnboardingFeature.more(desktopFeatures(startup = false)))
    }

    @Test fun everyWeatherIconIsThere() {
        WeatherCondition.entries.forEach { c ->
            listOf(false, true).forEach { night ->
                val path = weatherIconPath(c, night)
                assertNotNull(javaClass.classLoader.getResource(path), path)
            }
        }
        assertEquals("icons/ic_wx_clear_night.xml", weatherIconPath(WeatherCondition.CLEAR, night = true))
        assertEquals("icons/ic_wx_rain.xml", weatherIconPath(WeatherCondition.RAIN, night = true))
        listOf("ic_pin", "ic_globe", "ic_onb_method", "ic_onb_asr", "ic_onb_wave", "ic_onb_friday", "ic_onb_startup", "ic_alarm", "ic_timer", "ic_p_sunrise")
            .forEach { assertNotNull(javaClass.classLoader.getResource("icons/$it.xml"), it) }
    }

    @Test fun askPreviewQuotesItsSource() {
        Strings.language = "en"
        val p = assertNotNull(AskPreviews.load("en"))
        assertTrue(p.question.contains("Friday"), p.question)
        assertEquals(t("answer_common_unreviewed"), p.label, "the Friday answer awaits review")
        assertNotNull(p.source)
        assertTrue(p.source.endsWith("62:9"), p.source)
    }

    /** Every step with the animations on (no still frame): the springs, the sun's glide and the preview's crossfade run without a fault. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun everyStepRunsAnimated() {
        // English in two panes, Arabic in one column.
        listOf(Triple("en", 980, 680), Triple("ar", 720, 680)).forEach { (language, w, h) ->
            Strings.language = language
            val city = Cities.index.search("Riyadh", 1).first()
            val settings = DesktopSettings(cityId = city.id, language = language, skyWeather = true)
            OnboardingFlow(hasLook = false).steps.forEach { step ->
                val scene = ImageComposeScene(w, h, Density(1f)) {
                    OnboardingDesktop(
                        settings, city, Instant.parse("2026-10-12T10:00:00Z"), onSettings = {}, onCity = {}, animate = true,
                        startStep = step, tray = true, startup = true,
                    )
                }
                (0..24).forEach { i -> scene.render(i * 80_000_000L) }
                scene.close()
            }
        }
    }
}
