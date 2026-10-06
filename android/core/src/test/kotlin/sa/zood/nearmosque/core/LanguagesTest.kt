package sa.zood.nearmosque.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    @Test
    fun platformLocalesMapToAppCodes() {
        assertEquals("id", Languages.code("in", "ID"))
        assertEquals("id", Languages.code("id"))
        assertEquals("ar", Languages.code("ar", "SA"))
        assertEquals("en", Languages.code("en", "GB"))
        // Variants: Dari is Persian of Afghanistan, Tagalog is fil on the platforms, Chinese is Simplified.
        assertEquals("prs", Languages.code("fa", "AF"))
        assertEquals("fa", Languages.code("fa", "IR"))
        assertEquals("fa", Languages.code("fa"))
        assertEquals("tl", Languages.code("fil", "PH"))
        assertEquals("tl", Languages.code("tl"))
        assertEquals("zh", Languages.code("zh", "CN"))
        assertEquals("zh", Languages.code("zh", "CN", "Hans"))
        assertEquals("ckb", Languages.code("ckb", "IQ"))
        // A locale the app does not offer keeps its own code (the UI then falls back to English strings).
        assertEquals("xx", Languages.code("xx"))
    }

    @Test
    fun rightToLeftAndTags() {
        assertTrue(Languages.isRtl("ar"))
        assertTrue(Languages.isRtl("ur"))
        assertFalse(Languages.isRtl("tr"))
        for (c in listOf("fa", "prs", "ckb", "ug")) assertTrue(c, Languages.isRtl(c))
        assertEquals("en", Languages.tag("en"))
        assertEquals("fa-AF", Languages.tag("prs"))
        assertEquals("fil", Languages.tag("tl"))
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
    }
}
