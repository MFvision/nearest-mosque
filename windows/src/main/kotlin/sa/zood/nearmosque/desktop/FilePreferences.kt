package sa.zood.nearmosque.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.prefs.AbstractPreferences

/**
 * The settings as one small file (Linux: ~/.config/near-mosque/settings.properties), behind the same Preferences
 * calls the Windows registry takes, so Store reads and writes them the same way on both. A single node: the app
 * keeps no sub-nodes. A missing or unreadable file reads as defaults; each flush replaces the file in one move.
 */
internal class FilePreferences(private val file: File) : AbstractPreferences(null, "") {
    private val values = Properties().apply {
        runCatching { if (file.isFile) file.inputStream().use { load(it) } }
    }

    override fun putSpi(key: String, value: String) { values.setProperty(key, value) }
    override fun getSpi(key: String): String? = values.getProperty(key)
    override fun removeSpi(key: String) { values.remove(key) }
    override fun keysSpi(): Array<String> = values.stringPropertyNames().toTypedArray()
    override fun childrenNamesSpi(): Array<String> = emptyArray()
    override fun childSpi(name: String): AbstractPreferences = throw UnsupportedOperationException("no sub-nodes")
    override fun removeNodeSpi() = throw UnsupportedOperationException("the root stays")
    override fun syncSpi() {}

    override fun flushSpi() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { values.store(it, "Near Mosque settings") }
        runCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .recoverCatching { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            .getOrThrow()
    }
}
