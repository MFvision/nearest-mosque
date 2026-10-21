package sa.zood.nearmosque.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Start at sign-in: the app opens in the notification area (minimized where there is none) when the person signs
 * in, so the Adhan and reminders come without opening it by hand.
 *
 * Windows: the installed app (MSI) adds itself to the user's Run key (HKCU, no admin rights); the Microsoft Store
 * version uses the package's startup task instead, which the person can switch off in Settings → Apps → Startup.
 *
 * Linux: an entry in ~/.config/autostart (the freedesktop autostart folder GNOME, KDE, Xfce and others read),
 * pointing at the installed launcher (.deb) or at the AppImage file itself.
 */
object Startup {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Near Mosque"
    const val LINUX_FILE = "near-mosque.desktop"

    val isWindows get() = Platform.isWindows

    /** The launcher jpackage made (absent when run from Gradle). */
    private val exe: String? get() = System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    /** Installed from the Microsoft Store (MSIX): Windows runs the package's startup task. */
    val isStorePackage: Boolean get() = isWindows && exe?.contains("\\WindowsApps\\", ignoreCase = true) == true

    /**
     * What Linux should run at sign-in. An AppImage runs from a new temporary mount each time, so the entry
     * points at the AppImage file ($APPIMAGE), not at the launcher inside it.
     */
    fun linuxLauncher(appImage: String?, appPath: String?): String? =
        appImage?.takeIf { it.isNotBlank() && File(it).isFile } ?: appPath?.takeIf { File(it).exists() }

    private val linux: String? get() = linuxLauncher(System.getenv("APPIMAGE"), System.getProperty("jpackage.app-path"))

    val available: Boolean get() = when {
        isWindows -> exe != null && !isStorePackage
        Platform.isLinux -> linux != null
        else -> false
    }

    /**
     * Adds or removes the entry; false when it could not be done (the Run key read back is not what was asked). Runs
     * reg.exe, so it is called off the window's thread.
     */
    fun set(on: Boolean): Boolean {
        if (!available) return true
        if (isWindows) {
            val path = exe ?: return true
            val want = runValue(path)
            fun current() = runKeyValue(Platform.output(listOf("reg", "query", KEY, "/v", NAME)))
            // Read back after each change, so the switch never says on while Windows has nothing (or a broken value).
            if (!on) return current() == null || (reg("delete", KEY, "/v", NAME, "/f") && current() == null)
            return current() == want || (reg("add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", regArg(want), "/f") && current() == want)
        } else if (Platform.isLinux) {
            val launcher = linux ?: return true
            return runCatching { write(Platform.autostartDir, on, launcher, Platform.iconFile) }.isSuccess
        }
        return true
    }

    /** The Run key's value: the launcher, quoted (its folder has spaces), started in the notification area. */
    fun runValue(path: String): String = "\"$path\" --minimized"

    /**
     * [value] as one argument for reg.exe. Java (its default, legacy quoting on Windows) puts an argument with spaces
     * in quotes but leaves the quotes inside it alone, and reg.exe reads its arguments by the C runtime's rules, so
     * each inner quote takes a backslash here (with the backslashes just before it doubled).
     */
    fun regArg(value: String): String = buildString {
        var backslashes = 0
        for (c in value) {
            when (c) {
                '\\' -> backslashes++
                '"' -> { repeat(backslashes) { append('\\') }; append('\\'); backslashes = 0 }
                else -> backslashes = 0
            }
            append(c)
        }
    }

    /** The value of the app's entry in `reg query … /v "Near Mosque"` output, or null if it is not there. */
    fun runKeyValue(regOutput: String?): String? =
        regOutput?.lineSequence()?.firstNotNullOfOrNull { runLine.find(it)?.groupValues?.get(1)?.trimEnd() }

    private val runLine = Regex("^\\s*${Regex.escape(NAME)}\\s+REG_SZ\\s+(.*)$")

    /** Runs reg.exe; true when it says it succeeded within 10 seconds. */
    private fun reg(vararg args: String): Boolean = runCatching {
        val p = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    /** Writes (or, when [on] is false, removes) the autostart entry in [dir]. */
    fun write(dir: File, on: Boolean, launcher: String, icon: File?) {
        val f = File(dir, LINUX_FILE)
        if (!on) { f.delete(); return }
        dir.mkdirs()
        val text = desktopEntry(launcher, icon?.absolutePath)
        if (f.isFile && f.readText() == text) return
        val tmp = File(dir, "$LINUX_FILE.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /**
     * The autostart entry. TryExec makes the desktop skip it quietly once the app is removed; Exec starts it in the
     * notification area (or minimized) with --minimized.
     */
    fun desktopEntry(launcher: String, icon: String?): String = buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Name=Near Mosque")
        appendLine("Comment=Prayer times, Adhan and reminders")
        appendLine("TryExec=" + escapeValue(launcher))
        appendLine("Exec=" + execArg(launcher) + " --minimized")
        if (icon != null) appendLine("Icon=" + escapeValue(icon))
        appendLine("Terminal=false")
        appendLine("X-GNOME-Autostart-enabled=true")
    }

    /** A string value in a .desktop file: backslash, newline, tab and carriage return are escaped. */
    fun escapeValue(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")

    private val reserved = " \t\n\"'\\><~|&;$*?#()`".toSet()

    /**
     * One argument of Exec: quoted when it holds a reserved character (inside quotes ", `, $ and \ take a
     * backslash), % doubled (field codes), then escaped as a string value, as the Desktop Entry spec says.
     */
    fun execArg(arg: String): String {
        val a = arg.replace("%", "%%")
        val quoted = if (a.any { it in reserved }) {
            "\"" + a.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$") + "\""
        } else a
        return escapeValue(quoted)
    }
}
