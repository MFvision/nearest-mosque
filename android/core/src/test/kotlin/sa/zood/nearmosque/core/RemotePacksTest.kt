package sa.zood.nearmosque.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.zip.Deflater

class RemotePacksTest {
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

    private val chunks = """{"id":"he:sw:1","seq":1,"anchor":"x","section":{},"original":{"docId":"d","lang":"sw","text":"t"}}""".toByteArray()
    private val manifest = """{"id":"sources.hadeethenc-sw","kind":"sources","schemaVersion":1,"version":1,"title":{"en":"t"},
        "languages":["sw"],"recordCount":1,"source":{"name":"s","url":"https://s"},"license":{"id":"l","name":"l","url":"https://l","attribution":"a"},
        "files":[{"path":"chunks.jsonl","bytes":${chunks.size},"sha256":"${RemotePacks.sha256(chunks)}"}]}""".toByteArray()

    private fun catalog(manifestSha: String = RemotePacks.sha256(manifest)): Pair<RemoteCatalog, Map<String, ByteArray>> {
        val zm = deflate(manifest)
        val zc = deflate(chunks)
        val assets = mapOf("https://host/c1/p.manifest.json.z" to zm, "https://host/c1/p.chunks.jsonl.z" to zc)
        val pack = RemotePack(
            "sources.hadeethenc-sw", "sw", mapOf("en" to "t"), 1, 1, manifestSha, (zm.size + zc.size).toLong(), (manifest.size + chunks.size).toLong(),
            listOf(
                RemoteFile("manifest.json", "p.manifest.json.z", zm.size.toLong(), RemotePacks.sha256(zm), manifest.size.toLong()),
                RemoteFile("chunks.jsonl", "p.chunks.jsonl.z", zc.size.toLong(), RemotePacks.sha256(zc), chunks.size.toLong()),
            ),
        )
        return RemoteCatalog(1, "https://host/c1/", listOf(pack)) to assets
    }

    @Test
    fun fetchesInflatesAndChecks() {
        val (c, assets) = catalog()
        var done = 0
        val p = RemotePacks.fetch(c, c.forLanguage("sw").single(), { assets.getValue(it) }) { n, _ -> done = n }
        assertEquals("sources.hadeethenc-sw", p.manifest.id)
        assertArrayEquals(chunks, p.files["chunks.jsonl"])
        assertEquals(2, done)
        PackVerifier.verify(p.manifest) { p.files[it]?.inputStream() }
    }

    @Test
    fun rejectsAnyChange() {
        val (c, assets) = catalog()
        val pack = c.packs.single()
        // A changed file on the host.
        val bad = assets.mapValues { (k, v) -> if (k.endsWith("chunks.jsonl.z")) v.copyOf().also { it[0] = (it[0] + 1).toByte() } else v }
        assertThrows(PackError::class.java) { RemotePacks.fetch(c, pack, get = { bad.getValue(it) }) }
        // A manifest that is not the one this app was built with.
        val (c2, assets2) = catalog(manifestSha = "0".repeat(64))
        assertThrows(PackError.Checksum::class.java) { RemotePacks.fetch(c2, c2.packs.single(), get = { assets2.getValue(it) }) }
    }

    @Test
    fun catalogRules() {
        assertThrows(PackError.Format::class.java) { RemoteCatalog.parse("""{"schemaVersion":1,"baseUrl":"http://x/","packs":[]}""") }
        assertThrows(PackError.Format::class.java) {
            RemoteCatalog.parse("""{"schemaVersion":1,"baseUrl":"https://x/","packs":[{"id":"a","language":"sw","title":{},"version":1,"manifestSha256":"0",
                "bytes":1,"installedBytes":1,"files":[{"path":"m","asset":"../x","bytes":1,"sha256":"0","size":1}]}]}""")
        }
        assertEquals(0, RemoteCatalog.parse("""{"schemaVersion":1,"baseUrl":"https://x/"}""").packs.size)
        assertThrows(PackError.Format::class.java) { RemotePacks.inflate(deflate(chunks), chunks.size - 1L) }
    }
}
