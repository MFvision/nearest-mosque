package sa.zood.nearmosque

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sa.zood.nearmosque.core.RemoteCatalog
import sa.zood.nearmosque.core.RemoteFile
import sa.zood.nearmosque.core.RemotePack
import sa.zood.nearmosque.core.RemotePacks
import sa.zood.nearmosque.data.ContentDownloads
import java.util.zip.Deflater

/** A language's content, served by a fake host: downloaded on request, checked, installed and searchable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ContentDownloadsTest {
    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(9, true)
        d.setInput(b)
        d.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    @Test
    fun downloadsOnRequestAndInstalls() = runBlocking {
        val c = AppContainer(ApplicationProvider.getApplicationContext(), inMemoryDb = true,
            settingsFile = java.io.File.createTempFile("settings", ".preferences_pb").also { it.delete() })
        val chunks = """{"id":"he:sw:1","seq":1,"anchor":"Swala","section":{"type":"hadith","publisher":"hadeethenc","parts":[{"kind":"hadith","lang":"sw"}]},"original":{"docId":"hadeethenc-sw","lang":"sw","text":"Uislamu umejengwa juu ya nguzo tano"},"url":"https://hadeethenc.com/sw/browse/hadith/1"}""" + "\n"
        val docs = """[{"id":"hadeethenc-sw","kind":"library","title":{"en":"HadeethEnc"},"publisher":"HadeethEnc.com","language":"sw","license":{"id":"l","name":"l","url":"https://hadeethenc.com/","attribution":"a"},"citation":"item","textType":"selectable"}]"""
        val files = mapOf("chunks.jsonl" to chunks.toByteArray(), "documents.json" to docs.toByteArray())
        val manifest = """{"id":"sources.hadeethenc-sw","kind":"sources","schemaVersion":1,"version":1,"title":{"en":"HadeethEnc (sw)"},"languages":["sw"],
            "recordCount":1,"source":{"name":"HadeethEnc.com","url":"https://hadeethenc.com/sw"},"license":{"id":"l","name":"l","url":"https://hadeethenc.com/","attribution":"a"},
            "files":[${files.entries.joinToString(",") { (p, b) -> """{"path":"$p","bytes":${b.size},"sha256":"${RemotePacks.sha256(b)}"}""" }}]}""".toByteArray()
        val all = mapOf("manifest.json" to manifest) + files
        val assets = all.mapKeys { "https://host/c1/hadeethenc-sw.${it.key}.z" }.mapValues { deflate(it.value) }
        val pack = RemotePack(
            "sources.hadeethenc-sw", "sw", mapOf("en" to "HadeethEnc (sw)"), 1, 1, RemotePacks.sha256(manifest),
            assets.values.sumOf { it.size.toLong() }, all.values.sumOf { it.size.toLong() },
            all.map { (p, b) -> val z = deflate(b); RemoteFile(p, "hadeethenc-sw.$p.z", z.size.toLong(), RemotePacks.sha256(z), b.size.toLong()) },
        )
        var requests = 0
        val downloads = ContentDownloads(c.packs, c.db, RemoteCatalog(1, "https://host/c1/", listOf(pack)), CoroutineScope(Dispatchers.IO), afterInstall = {}) { url ->
            requests++
            assets.getValue(url)
        }
        // Nothing is fetched until asked.
        assertEquals(1, downloads.missing("sw").size)
        assertEquals(0, requests)
        downloads.download("sw")
        val done = withTimeout(30_000) { downloads.state.first { it["sw"] == ContentDownloads.State.Done || it["sw"] == ContentDownloads.State.Failed } }
        assertEquals(ContentDownloads.State.Done, done["sw"])
        assertEquals(3, requests)
        assertTrue(downloads.missing("sw").isEmpty())
        assertEquals("Uislamu umejengwa juu ya nguzo tano", c.ask.resolve(listOf("he:sw:1")).single().chunk.original.text)
    }
}
