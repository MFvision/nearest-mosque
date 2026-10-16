package sa.zood.nearmosque.desktop

import java.io.File

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

    fun set(on: Boolean) {
        if (!available) return
        if (isWindows) {
            val path = exe ?: return
            val cmd = if (on) listOf("reg", "add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$path\" --minimized", "/f")
            else listOf("reg", "delete", KEY, "/v", NAME, "/f")
            runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor() }
        } else if (Platform.isLinux) {
            val launcher = linux ?: return
            runCatching { write(Platform.autostartDir, on, launcher, Platform.iconFile) }
        }
    }

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
