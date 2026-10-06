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
        // A locale the app does not offer keeps its own code (the UI then falls back to English strings).
        assertEquals("xx", Languages.code("xx"))
    }

    @Test
    fun rightToLeftAndTags() {
        assertTrue(Languages.isRtl("ar"))
        assertTrue(Languages.isRtl("ur"))
        assertFalse(Languages.isRtl("tr"))
        assertEquals("en", Languages.tag("en"))
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
    }
}
