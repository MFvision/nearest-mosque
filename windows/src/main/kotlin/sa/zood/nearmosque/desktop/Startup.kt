package sa.zood.nearmosque.desktop

import java.io.File

/**
 * Start with Windows: the app opens in the notification area when the person signs in, so the Adhan and
 * reminders come without opening it by hand. The installed app (MSI) adds itself to the user's Run key
 * (HKCU, no admin rights); the Microsoft Store version uses the package's startup task instead, which the
 * person can switch off in Settings → Apps → Startup.
 */
object Startup {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Near Mosque"

    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** The launcher jpackage made (absent when run from Gradle). */
    private val exe: String? get() = System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    /** Installed from the Microsoft Store (MSIX): Windows runs the package's startup task. */
    val isStorePackage: Boolean get() = exe?.contains("\\WindowsApps\\", ignoreCase = true) == true

    val available: Boolean get() = isWindows && exe != null && !isStorePackage

    fun set(on: Boolean) {
        val path = exe ?: return
        if (!available) return
        val cmd = if (on) listOf("reg", "add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$path\" --minimized", "/f")
        else listOf("reg", "delete", KEY, "/v", NAME, "/f")
        runCatching { ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor() }
    }
}
