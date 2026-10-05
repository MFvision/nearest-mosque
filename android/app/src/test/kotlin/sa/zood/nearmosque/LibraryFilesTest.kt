package sa.zood.nearmosque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.zood.nearmosque.data.LibraryFiles

class LibraryFilesTest {
    @Test
    fun onlyIslamHouseHttpsFilesAreFetched() {
        assertTrue(LibraryFiles.isAllowed("https://d1.islamhouse.com/data/en/ih_books/single/en_What_is_Islam.pdf"))
        assertTrue(LibraryFiles.isAllowed("https://islamhouse.com/en/books/123/"))
        assertFalse(LibraryFiles.isAllowed("http://d1.islamhouse.com/a.pdf"))
        assertFalse(LibraryFiles.isAllowed("https://islamhouse.com.evil.example/a.pdf"))
        assertFalse(LibraryFiles.isAllowed("https://example.com/islamhouse.com/a.pdf"))
        assertFalse(LibraryFiles.isAllowed("not a url"))
    }

    @Test
    fun localNamesAreStableAndSafe() {
        assertEquals("ih_en_2846462.pdf", LibraryFiles.fileName("ih:en:2846462", "https://d1.islamhouse.com/x/Book%20One.PDF"))
        assertEquals("ih_ar_1.bin", LibraryFiles.fileName("ih:ar:1", "https://d1.islamhouse.com/x/noext"))
    }
}
