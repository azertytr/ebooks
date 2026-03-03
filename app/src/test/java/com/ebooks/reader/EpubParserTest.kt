package com.ebooks.reader

import com.ebooks.reader.data.parser.EpubParser
import com.ebooks.reader.data.parser.ReaderTheme
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EPUB parsing utilities.
 * Full parser tests require Android instrumented tests (due to Context dependency).
 */
class EpubParserTest {

    @Test
    fun `reader theme light has correct defaults`() {
        val theme = ReaderTheme.LIGHT
        assertEquals("#FFFFFF", theme.backgroundColor)
        assertEquals("#222222", theme.textColor)
        assertEquals(18, theme.fontSize)
        assertTrue(theme.lineHeight > 1.0f)
    }

    @Test
    fun `reader theme dark has dark background`() {
        val theme = ReaderTheme.DARK
        assertNotEquals("#FFFFFF", theme.backgroundColor)
        assertFalse(theme.backgroundColor.equals("#FFFFFF", ignoreCase = true))
    }

    @Test
    fun `reader theme sepia has warm colors`() {
        val theme = ReaderTheme.SEPIA
        assertTrue(theme.backgroundColor.contains("f3", ignoreCase = true) ||
                   theme.backgroundColor.contains("ea", ignoreCase = true))
    }

    @Test
    fun `all themes have valid css color format`() {
        val themes = listOf(ReaderTheme.LIGHT, ReaderTheme.DARK, ReaderTheme.SEPIA, ReaderTheme.NIGHT)
        val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")
        for (theme in themes) {
            assertTrue("${theme.backgroundColor} is not a valid hex color",
                hexPattern.matches(theme.backgroundColor))
            assertTrue("${theme.textColor} is not a valid hex color",
                hexPattern.matches(theme.textColor))
        }
    }

    @Test
    fun `font size stays within bounds`() {
        val minSize = 12
        val maxSize = 32
        val sizes = (minSize..maxSize step 2).toList()
        sizes.forEach { size ->
            val theme = ReaderTheme.LIGHT.copy(fontSize = size)
            assertEquals(size, theme.fontSize)
        }
    }
}
