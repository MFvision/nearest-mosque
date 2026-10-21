package sa.zood.nearmosque.desktop

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/** The PC's system, for the few things done differently on Windows and Linux. */
enum class Os {
    WINDOWS, LINUX, OTHER;

    companion object {
        fun of(osName: String): Os = when {
            osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
            osName.startsWith("Linux", ignoreCase = true) -> LINUX
            else -> OTHER
        }
    }
}

/**
 * What differs between Windows and Linux: the app's folders, finding a program, opening links and files, and how
 * a notification reaches the person. Every Linux path degrades quietly (no crash) when a piece is missing.
 */
object Platform {
    val os: Os = Os.of(System.getProperty("os.name").orEmpty())
    val isWindows get() = os == Os.WINDOWS
    val isLinux get() = os == Os.LINUX

    private val home: String get() = System.getProperty("user.home").orEmpty()

    /** An XDG base directory: the variable when it holds an absolute path (the spec says to ignore others), else [fallback]. */
    fun xdg(value: String?, fallback: String): File =
        value?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File) ?: File(fallback)

    /**
     * Downloaded books and the app's small files. Windows: %LOCALAPPDATA%\NearMosque (as before). Linux:
     * $XDG_DATA_HOME/near-mosque, by default ~/.local/share/near-mosque.
     */
    fun dataDir(os: Os, env: (String) -> String?, home: String): File = when (os) {
        Os.LINUX -> File(xdg(env("XDG_DATA_HOME"), "$home/.local/share"), "near-mosque")
        else -> File(env("LOCALAPPDATA")?.let(::File) ?: File(home, ".local/share"), "NearMosque")
    }

    /** Linux: $XDG_CONFIG_HOME/near-mosque (~/.config/near-mosque) for the settings. Elsewhere none: java.util.prefs. */
    fun configDir(os: Os, env: (String) -> String?, home: String): File? = when (os) {
        Os.LINUX -> File(xdg(env("XDG_CONFIG_HOME"), "$home/.config"), "near-mosque")
        else -> null
    }

    /** Linux: $XDG_CONFIG_HOME/autostart, where the desktop starts programs at sign-in. */
    fun autostartDir(env: (String) -> String?, home: String): File = File(xdg(env("XDG_CONFIG_HOME"), "$home/.config"), "autostart")

    val dataDir: File get() = dataDir(os, System::getenv, home)
    val configDir: File? get() = configDir(os, System::getenv, home)
    val autostartDir: File get() = autostartDir(System::getenv, home)

    /** The program [name] on [path] (the PATH variable), if there is an executable file by that name. */
    fun findExecutable(name: String, path: String?, separator: String = File.pathSeparator): File? =
        path.orEmpty().split(separator).asSequence().filter { it.isNotBlank() }
            .map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }

    private fun which(name: String): File? = runCatching { findExecutable(name, System.getenv("PATH")) }.getOrNull()

    private val xdgOpen: File? by lazy { which("xdg-open") }
    private val notifySend: File? by lazy { which("notify-send") }

    /** Whether the desktop has a notification area for the app's icon (GNOME, for one, has none by default). */
    val hasTray: Boolean by lazy { runCatching { androidx.compose.ui.window.isTraySupported }.getOrDefault(false) }

    /** The app's icon as a file, for Linux notifications and the sign-in entry (null if it cannot be written). */
    val iconFile: File? by lazy {
        runCatching {
            val f = File(Store.dataDir, "icon.png")
            if (!f.isFile) Platform::class.java.classLoader.getResourceAsStream("icon.png")!!.use { input -> f.outputStream().use { input.copyTo(it) } }
            f
        }.getOrNull()
    }

    /** Opens a web page, an email or a call in the PC's own apps; false if nothing could take it. */
    fun open(url: String): Boolean = when (os) {
        // Linux: xdg-open hands the link to the desktop's own choice; AWT's Desktop is often missing there.
        Os.LINUX -> xdgOpen(url) || awt { if (url.startsWith("mailto:")) it.mail(URI(url)) else it.browse(URI(url)) }
        else -> runCatching {
            val d = Desktop.getDesktop()
            if (url.startsWith("mailto:")) d.mail(URI(url)) else d.browse(URI(url))
            true
        }.getOrDefault(false)
    }

    /** Opens a downloaded file (a book's PDF) in the PC's reader. */
    fun openFile(file: File): Boolean = when (os) {
        Os.LINUX -> xdgOpen(file.absolutePath) || awt { it.open(file) }
        else -> runCatching { Desktop.getDesktop().open(file); true }.getOrDefault(false)
    }

    private fun xdgOpen(target: String): Boolean {
        val exe = xdgOpen ?: return false
        return runCatching { start(listOf(exe.path, target)); true }.getOrDefault(false)
    }

    /** AWT's Desktop, only where it says it can (on Linux it needs GTK). */
    private inline fun awt(action: (Desktop) -> Unit): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return false
        action(Desktop.getDesktop())
        true
    }.getOrDefault(false)

    private fun start(cmd: List<String>): Process =
        ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()

    /**
     * Whether the desktop lets apps animate: Windows "Animation effects" (MinAnimate under
     * HKCU\Control Panel\Desktop\WindowMetrics), GNOME's enable-animations, KDE's AnimationDurationFactor. Read once,
     * in the background; nothing moves until it is known, and nothing moves when any of them says no.
     */
    val animations: Boolean get() = animationState.value

    private val animationState: androidx.compose.runtime.MutableState<Boolean> by lazy {
        androidx.compose.runtime.mutableStateOf(false).also { state ->
            Thread { state.value = runCatching { readAnimations() }.getOrDefault(true) }.apply { isDaemon = true; name = "animation-setting" }.start()
        }
    }

    private fun readAnimations(): Boolean = when (os) {
        Os.WINDOWS -> windowsAnimations(output(listOf("reg", "query", "HKCU\\Control Panel\\Desktop\\WindowMetrics", "/v", "MinAnimate"))) ?: true
        Os.LINUX -> {
            val gnome = which("gsettings")?.let { gnomeAnimations(output(listOf(it.path, "get", "org.gnome.desktop.interface", "enable-animations"))) }
            val kde = runCatching { File(xdg(System.getenv("XDG_CONFIG_HOME"), "$home/.config"), "kdeglobals").takeIf { it.isFile }?.readText() }.getOrNull()
            gnome != false && kdeAnimations(kde) != false
        }
        Os.OTHER -> true
    }

    /** `reg query … /v MinAnimate`: "    MinAnimate    REG_SZ    0" means off; null if the value is not there. */
    fun windowsAnimations(regOutput: String?): Boolean? {
        val line = regOutput?.lineSequence()?.firstOrNull { it.trim().startsWith("MinAnimate", ignoreCase = true) } ?: return null
        return when (line.trim().split(Regex("\\s+")).lastOrNull()) {
            "0" -> false
            "1" -> true
            else -> null
        }
    }

    /** `gsettings get org.gnome.desktop.interface enable-animations`: "true" or "false". */
    fun gnomeAnimations(output: String?): Boolean? = when (output?.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /** ~/.config/kdeglobals: AnimationDurationFactor=0 in [KDE] turns animations off. */
    fun kdeAnimations(kdeglobals: String?): Boolean? {
        var group = ""
        for (raw in kdeglobals?.lineSequence().orEmpty()) {
            val line = raw.trim()
            if (line.startsWith("[")) group = line
            else if (group == "[KDE]" && line.startsWith("AnimationDurationFactor=")) {
                return line.substringAfter('=').trim().toDoubleOrNull()?.let { it > 0.0 }
            }
        }
        return null
    }

    /** A short command's output, or null if it cannot run, fails or takes more than 3 seconds. */
    internal fun output(cmd: List<String>): String? = runCatching {
        val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) text else null
    }.getOrNull()

    /** How a notification is shown on this PC. */
    enum class Route { TRAY, NOTIFY_SEND, IN_APP }

    /** Linux: the desktop's own notifications (notify-send) when present; else the tray icon's; else inside the window. */
    fun route(os: Os, traySupported: Boolean, notifySendFound: Boolean): Route = when {
        os == Os.LINUX && notifySendFound -> Route.NOTIFY_SEND
        traySupported -> Route.TRAY
        else -> Route.IN_APP
    }

    val route: Route get() = route(os, hasTray, notifySend != null)

    /**
     * The notify-send command for one notification (the texts after "--", so none is read as an option). [urgent]
     * (the Fajr alarm) asks for a critical notification, which most desktops keep on screen until it is dismissed.
     */
    fun notifySendCommand(exe: String, title: String, body: String, icon: File?, urgent: Boolean = false): List<String> =
        listOfNotNull(exe, "--app-name=Near Mosque", icon?.let { "--icon=${it.absolutePath}" }, if (urgent) "--urgency=critical" else null, "--", title, body)

    /**
     * Shows a notification through notify-send; if it cannot run or reports a failure (no notification service),
     * [fallback] shows it another way. Never blocks the caller.
     */
    fun notifySend(title: String, body: String, urgent: Boolean = false, fallback: () -> Unit) {
        val exe = notifySend ?: return fallback()
        Thread {
            val ok = runCatching {
                val p = start(notifySendCommand(exe.path, title, body, iconFile, urgent))
                p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)
            if (!ok) fallback()
        }.apply { isDaemon = true; name = "notify-send" }.start()
    }
}
