package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BooksTest {
    private val sha = "a".repeat(64)
    private fun catalog(base: String = "https://example.org/books/", file: String = "tawhid-v01.pdf", hash: String = sha) = """
        {"schemaVersion":1,"baseUrl":"$base","source":"OpenITI","books":[
         {"id":"tawhid","group":"aqidah","title":"كتاب التوحيد","author":"x","edition":"y","volumes":[{"file":"$file","volume":1,"pages":10,"bytes":5,"sha256":"$hash"}]},
         {"id":"bukhari","group":"hadith","title":"صحيح البخاري","author":"x","edition":"y","volumes":[{"file":"bukhari-v01.pdf","volume":1,"pages":10,"bytes":5,"sha256":"$sha"}]}]}
    """.trimIndent()

    @Test fun parsesAndGroupsInOrder() {
        val c = BookCatalog.parse(catalog())
        assertEquals(listOf("aqidah", "hadith"), c.byGroup.map { it.first })
        assertEquals("https://example.org/books/tawhid-v01.pdf", c.url(c.books[0].volumes[0]))
    }

    @Test fun refusesUnsafeCatalogs() {
        assertThrows(PackError::class.java) { BookCatalog.parse(catalog(base = "http://example.org/")) }
        assertThrows(PackError::class.java) { BookCatalog.parse(catalog(file = "../x.pdf")) }
        assertThrows(PackError::class.java) { BookCatalog.parse(catalog(file = "x.exe")) }
        assertThrows(PackError::class.java) { BookCatalog.parse(catalog(hash = "nothex")) }
    }
}
