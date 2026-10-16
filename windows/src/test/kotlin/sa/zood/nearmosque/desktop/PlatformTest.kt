package sa.zood.nearmosque.desktop

import sa.zood.nearmosque.core.PrayerEvent
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Windows and Linux differences that need no display: folders, the sign-in entry, notifications, one copy. */
class PlatformTest {
    private fun temp(): File = Files.createTempDirectory("nm-test").toFile().apply { deleteOnExit() }
    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test fun osNames() {
        assertEquals(Os.WINDOWS, Os.of("Windows 11"))
        assertEquals(Os.LINUX, Os.of("Linux"))
        assertEquals(Os.OTHER, Os.of("Mac OS X"))
    }

    @Test fun windowsFoldersAreUnchanged() {
        assertEquals(File("C:\\Users\\a\\AppData\\Local", "NearMosque"), Platform.dataDir(Os.WINDOWS, env("LOCALAPPDATA" to "C:\\Users\\a\\AppData\\Local"), "C:\\Users\\a"))
        assertNull(Platform.configDir(Os.WINDOWS, env(), "C:\\Users\\a"), "Windows keeps java.util.prefs (the registry)")
    }

    @Test fun linuxFoldersFollowXdg() {
        if (Platform.isWindows) return // "/data" is not an absolute path on Windows
        assertEquals(File("/home/a/.local/share/near-mosque"), Platform.dataDir(Os.LINUX, env(), "/home/a"))
        assertEquals(File("/home/a/.config/near-mosque"), Platform.configDir(Os.LINUX, env(), "/home/a"))
        assertEquals(File("/home/a/.config/autostart"), Platform.autostartDir(env(), "/home/a"))
        val xdg = env("XDG_DATA_HOME" to "/data", "XDG_CONFIG_HOME" to "/conf")
        assertEquals(File("/data/near-mosque"), Platform.dataDir(Os.LINUX, xdg, "/home/a"))
        assertEquals(File("/conf/near-mosque"), Platform.configDir(Os.LINUX, xdg, "/home/a"))
        assertEquals(File("/conf/autostart"), Platform.autostartDir(xdg, "/home/a"))
        // The spec: a relative or empty value is ignored. LOCALAPPDATA means nothing on Linux.
        val bad = env("XDG_DATA_HOME" to "relative/dir", "XDG_CONFIG_HOME" to "", "LOCALAPPDATA" to "/x")
        assertEquals(File("/home/a/.local/share/near-mosque"), Platform.dataDir(Os.LINUX, bad, "/home/a"))
        assertEquals(File("/home/a/.config/autostart"), Platform.autostartDir(bad, "/home/a"))
    }

    @Test fun findsProgramsOnThePath() {
        if (Platform.isWindows) return // no executable bit, and ';' between PATH entries
        val a = temp()
        val b = temp()
        File(a, "notify-send").writeText("not executable")
        val exe = File(b, "notify-send").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals(exe, Platform.findExecutable("notify-send", "${a.path}:${b.path}", ":"))
        assertNull(Platform.findExecutable("xdg-open", "${a.path}:${b.path}", ":"))
        assertNull(Platform.findExecutable("xdg-open", null, ":"))
    }

    @Test fun notificationRoutes() {
        assertEquals(Platform.Route.TRAY, Platform.route(Os.WINDOWS, traySupported = true, notifySendFound = false))
        assertEquals(Platform.Route.TRAY, Platform.route(Os.WINDOWS, traySupported = true, notifySendFound = true), "Windows unchanged")
        assertEquals(Platform.Route.NOTIFY_SEND, Platform.route(Os.LINUX, traySupported = true, notifySendFound = true))
        assertEquals(Platform.Route.NOTIFY_SEND, Platform.route(Os.LINUX, traySupported = false, notifySendFound = true))
        assertEquals(Platform.Route.TRAY, Platform.route(Os.LINUX, traySupported = true, notifySendFound = false))
        assertEquals(Platform.Route.IN_APP, Platform.route(Os.LINUX, traySupported = false, notifySendFound = false))
    }

    @Test fun notifySendCommandKeepsTextsAsTexts() {
        val icon = File("/tmp/icon.png")
        assertEquals(
            listOf("/usr/bin/notify-send", "--app-name=Near Mosque", "--icon=${icon.absolutePath}", "--", "-Dhuhr", "12:00"),
            Platform.notifySendCommand("/usr/bin/notify-send", "-Dhuhr", "12:00", icon),
        )
        assertEquals(listOf("notify-send", "--app-name=Near Mosque", "--", "Fajr", "4:32"), Platform.notifySendCommand("notify-send", "Fajr", "4:32", null))
    }

    @Test fun autostartEntry() {
        assertEquals(
            """
            [Desktop Entry]
            Type=Application
            Name=Near Mosque
            Comment=Prayer times, Adhan and reminders
            TryExec=/opt/near-mosque/bin/Near Mosque
            Exec="/opt/near-mosque/bin/Near Mosque" --minimized
            Icon=/home/a/.local/share/near-mosque/icon.png
            Terminal=false
            X-GNOME-Autostart-enabled=true

            """.trimIndent(),
            Startup.desktopEntry("/opt/near-mosque/bin/Near Mosque", "/home/a/.local/share/near-mosque/icon.png"),
        )
        assertTrue(Startup.desktopEntry("/opt/near-mosque/bin/near-mosque", null).contains("Exec=/opt/near-mosque/bin/near-mosque --minimized\n"))
        assertFalse(Startup.desktopEntry("/x", null).contains("Icon="))
    }

    @Test fun execQuoting() {
        assertEquals("/usr/bin/app", Startup.execArg("/usr/bin/app"))
        assertEquals("\"/home/a/My Apps/app\"", Startup.execArg("/home/a/My Apps/app"))
        // Inside quotes $ and " take a backslash; the whole value then has its backslashes doubled.
        assertEquals("\"/home/a/\\\\\$x/\\\\\"q\\\\\"\"", Startup.execArg("/home/a/\$x/\"q\""))
        assertEquals("/home/a/100%%", Startup.execArg("/home/a/100%"))
    }

    @Test fun linuxLauncherPrefersTheAppImageFile() {
        val dir = temp()
        val appImage = File(dir, "Near_Mosque-x86_64.AppImage").apply { writeText("") }
        val inside = File(dir, "mount/bin/Near Mosque").apply { parentFile.mkdirs(); writeText("") }
        assertEquals(appImage.path, Startup.linuxLauncher(appImage.path, inside.path))
        assertEquals(inside.path, Startup.linuxLauncher(null, inside.path))
        assertEquals(inside.path, Startup.linuxLauncher(File(dir, "gone.AppImage").path, inside.path))
        assertNull(Startup.linuxLauncher(null, null), "run from Gradle: nothing to start")
    }

    @Test fun autostartFileIsWrittenAndRemoved() {
        val dir = File(temp(), "autostart")
        Startup.write(dir, on = true, launcher = "/opt/near-mosque/bin/Near Mosque", icon = null)
        val f = File(dir, Startup.LINUX_FILE)
        assertTrue(f.readText().contains("Exec=\"/opt/near-mosque/bin/Near Mosque\" --minimized"))
        assertFalse(File(dir, Startup.LINUX_FILE + ".tmp").exists())
        Startup.write(dir, on = false, launcher = "/opt/near-mosque/bin/Near Mosque", icon = null)
        assertFalse(f.exists())
    }

    @Test fun settingsFileRoundTrip() {
        val file = File(temp(), "conf/near-mosque/settings.properties")
        val p = FilePreferences(file)
        assertEquals(-1L, p.getLong("cityId", -1), "no file yet: defaults")
        p.putLong("cityId", 108410)
        p.put("language", "ar")
        p.putBoolean("adhan", false)
        p.put("iqamaMinutes", "FAJR=25,DHUHR=20")
        p.flush()
        val again = FilePreferences(file)
        assertEquals(108410L, again.getLong("cityId", -1))
        assertEquals("ar", again.get("language", null))
        assertFalse(again.getBoolean("adhan", true))
        assertEquals("FAJR=25,DHUHR=20", again.get("iqamaMinutes", ""))
        again.remove("cityId")
        again.flush()
        assertEquals(-1L, FilePreferences(file).getLong("cityId", -1))
        // A broken file reads as defaults instead of stopping the app.
        file.writeBytes(byteArrayOf(0, 1, 2, '\\'.code.toByte(), 'u'.code.toByte()))
        assertEquals("en", FilePreferences(file).get("language", "en"))
    }

    @Test fun oneCopyAtATime() {
        val dir = temp()
        val first = SingleInstance(dir)
        assertTrue(first.acquire())
        val second = SingleInstance(dir)
        assertFalse(second.acquire(), "a second copy sees the first")
        assertFalse(first.takeShowRequest())
        second.askToShow()
        assertTrue(first.takeShowRequest(), "the first copy is asked to show its window")
        assertFalse(first.takeShowRequest(), "once per request")
        first.release()
        val third = SingleInstance(dir)
        assertTrue(third.acquire(), "after the first ends, a new copy starts")
        third.release()
    }

    @Test fun pendingDesktopStrings() {
        Strings.language = "en"
        assertEquals("Start when you sign in", t("start_at_login_linux"))
        Strings.language = "ar"
        assertEquals("التشغيل عند تسجيل الدخول", t("start_at_login_linux"))
        Strings.language = "fr"
        assertEquals("Démarrer à la connexion", t("start_at_login_linux"), "other languages read the shared file")
        Strings.language = "en"
        assertEquals("Start with Windows", t("start_at_login"), "the shared file still wins for its own keys")
    }

    @Test fun fiveMinuteReminder() {
        Strings.language = "en"
        val riyadh = Cities.index.search("Riyadh", 1).first()
        val settings = DesktopSettings(cityId = riyadh.id, adhan = false, before5 = true)
        val clock = Clock(riyadh, settings)
        val dhuhr: Instant = clock.today(Instant.parse("2026-10-12T10:00:00Z"))[PrayerEvent.DHUHR]!!
        val sent = mutableListOf<Pair<String, String>>()
        val r = Reminders { title, body -> sent += title to body }
        r.tick(clock, settings, dhuhr.minusSeconds(600).plusSeconds(10))
        assertTrue(sent.isEmpty(), "ten-minute reminder off")
        r.tick(clock, settings, dhuhr.minusSeconds(300).plusSeconds(10))
        assertEquals(listOf("Dhuhr" to "5 minutes until Dhuhr"), sent)
        assertEquals("voice_ar_asr_5", Sound.reminderFile(PrayerEvent.ASR, "ar", 5))
        assertEquals("voice_en_asr_10", Sound.reminderFile(PrayerEvent.ASR, "en"))
        assertTrue(listOf(5, 10).all { m -> PrayerEvent.prayers.all { e -> Sound::class.java.classLoader.getResource("sounds/${Sound.reminderFile(e, "ar", m)}.mp3") != null } })
    }
}
