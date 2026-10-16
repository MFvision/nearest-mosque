package sa.zood.nearmosque.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * One copy of the app at a time, so the Adhan never plays twice (for example when it already started at sign-in
 * and the person opens it from the menu). The running copy holds a lock on a file in the app's folder, which the
 * system releases when it ends, even after a crash. A second copy leaves a request file for the first one to show
 * its window, and quits. Anything unexpected (a folder that cannot be written) lets the app start as usual.
 */
class SingleInstance(private val dir: File) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private val request get() = File(dir, "show-window.request")

    /** True when this is the only copy (or the check could not be made); the lock is then held until the app ends. */
    fun acquire(): Boolean {
        val ch = try {
            dir.mkdirs()
            FileChannel.open(File(dir, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        } catch (e: Exception) {
            return true
        }
        val l = try {
            ch.tryLock()
        } catch (e: OverlappingFileLockException) {
            null // held by this same process: already running
        } catch (e: Exception) {
            runCatching { ch.close() }
            return true
        }
        if (l == null) {
            runCatching { ch.close() }
            return false
        }
        channel = ch
        lock = l
        runCatching { request.delete() }
        return true
    }

    /** From a second copy: ask the running one to show its window. */
    fun askToShow() { runCatching { request.writeText("show") } }

    /** In the running copy, polled every second: whether another copy asked for the window (once per request). */
    fun takeShowRequest(): Boolean = runCatching { request.exists() && request.delete() }.getOrDefault(false)

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}
