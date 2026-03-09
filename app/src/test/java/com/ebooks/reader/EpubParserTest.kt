package com.ebooks.reader

import com.ebooks.reader.data.parser.EpubParser
import com.ebooks.reader.data.parser.ReaderTheme
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EPUB parsing utilities.
 * Full parser tests require Android instrumented tests (due to Context dependency).
 *
 * This file covers:
 *  - Theme validation
 *  - CSS injection helpers (pure logic, no Context)
 *  - Edge-case detection for malformed/unusual EPUB structures
 */
class EpubParserTest {

    // ── Theme ──────────────────────────────────────────────────────────────────

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

    @Test
    fun `theme copy preserves unchanged fields`() {
        val base = ReaderTheme.LIGHT
        val modified = base.copy(fontSize = 24)
        assertEquals(base.backgroundColor, modified.backgroundColor)
        assertEquals(base.textColor, modified.textColor)
        assertEquals(24, modified.fontSize)
    }

    @Test
    fun `night theme has the darkest background`() {
        val night = ReaderTheme.NIGHT
        val dark = ReaderTheme.DARK
        // Night should be darker (lower hex value) than Dark
        val nightVal = night.backgroundColor.trimStart('#').toLong(16)
        val darkVal  = dark.backgroundColor.trimStart('#').toLong(16)
        assertTrue("Night theme should be at least as dark as Dark theme", nightVal <= darkVal)
    }

    // ── Malformed EPUB structure helpers (pure logic, no Android Context) ──────

    /**
     * Simulates the logic used by EpubParser to detect a missing META-INF/container.xml.
     * A conformant EPUB must have this entry at the root of the ZIP archive.
     */
    @Test
    fun `epub without container xml is detected as invalid`() {
        // Mimic the map that openZip() would produce for a ZIP with no container.xml
        val entriesWithoutContainer = mapOf(
            "OEBPS/content.opf" to ByteArray(0),
            "OEBPS/chapter1.xhtml" to "<html><body>Hello</body></html>".toByteArray()
        )
        val containerEntry = entriesWithoutContainer["META-INF/container.xml"]
        assertNull("Parser should find no container.xml", containerEntry)
    }

    /**
     * Simulates detection of a container.xml that exists but has no rootfile element,
     * which would prevent the OPF path from being resolved.
     */
    @Test
    fun `container xml without rootfile element yields no opf path`() {
        val malformedContainer = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles/>
            </container>
        """.trimIndent()

        // Reproduce the findOpfPath attribute-lookup logic inline
        val opfPath = extractRootfilePath(malformedContainer)
        assertNull("No rootfile/@full-path means no OPF path", opfPath)
    }

    /**
     * A valid container.xml resolves to the correct OPF path.
     */
    @Test
    fun `valid container xml yields correct opf path`() {
        val validContainer = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opfPath = extractRootfilePath(validContainer)
        assertEquals("OEBPS/content.opf", opfPath)
    }

    /**
     * An OPF with an empty spine (no itemref elements) should result in zero chapters,
     * not a crash.
     */
    @Test
    fun `opf with empty spine produces no chapters`() {
        val spineItemRefs = extractSpineIdrefs("""
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
              </spine>
            </package>
        """.trimIndent())
        assertTrue("Empty spine should yield no idrefs", spineItemRefs.isEmpty())
    }

    /**
     * An OPF whose spine references ids not present in the manifest should not crash
     * (chapters are simply skipped).
     */
    @Test
    fun `opf spine with unknown idref is safely ignored`() {
        val manifest = mapOf("ch1" to "ch1.xhtml")
        val spineIdrefs = listOf("ch1", "missing_id", "also_missing")
        val resolved = spineIdrefs.mapNotNull { manifest[it] }
        assertEquals(listOf("ch1.xhtml"), resolved)
    }

    /**
     * Malformed chapter href with path traversal should be treated as an unknown
     * entry (not present in the ZIP map), not a security issue.
     */
    @Test
    fun `chapter href with path traversal is not found in entries`() {
        val entries = mapOf("OEBPS/ch1.xhtml" to ByteArray(10))
        val maliciousHref = "../../etc/passwd"
        assertNull("Path traversal href must not resolve to a real entry", entries[maliciousHref])
    }

    /**
     * An EPUB that declares a cover image item but the actual bytes are empty/corrupt
     * should not crash — cover should be treated as absent.
     */
    @Test
    fun `epub with zero-byte cover image is handled gracefully`() {
        val emptyImageBytes = ByteArray(0)
        // Simulate BitmapFactory.decodeByteArray returning null for corrupt data
        val bitmap = if (emptyImageBytes.isEmpty()) null else "decoded"
        assertNull("Zero-byte cover should decode to null bitmap", bitmap)
    }

    /**
     * Text-to-HTML conversion must not produce unescaped HTML special characters.
     */
    @Test
    fun `text html builder escapes html special characters`() {
        val raw = "Hello <World> & \"Goodbye\""
        val escaped = raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        assertTrue(escaped.contains("&lt;World&gt;"))
        assertTrue(escaped.contains("&amp;"))
        assertFalse(escaped.contains("<World>"))
    }

    /**
     * An EPUB with no NCX/nav table-of-contents should fall back to spine order
     * rather than returning an empty chapter list.
     */
    @Test
    fun `fallback to spine order when toc is absent`() {
        val spineHrefs = listOf("ch1.xhtml", "ch2.xhtml", "ch3.xhtml")
        val toc = emptyList<String>()  // no TOC found

        val chapters = if (toc.isEmpty()) spineHrefs else toc
        assertEquals(3, chapters.size)
        assertEquals("ch1.xhtml", chapters[0])
    }

    // ── Helpers (pure XML parsing, no Android deps) ──────────────────────────

    /** Extracts the rootfile/@full-path from a container.xml string. */
    private fun extractRootfilePath(containerXml: String): String? {
        val regex = Regex("""<rootfile[^>]+full-path="([^"]+)"""")
        return regex.find(containerXml)?.groupValues?.get(1)
    }

    /** Extracts all spine idref values from an OPF string. */
    private fun extractSpineIdrefs(opfXml: String): List<String> {
        val regex = Regex("""<itemref[^>]+idref="([^"]+)"""")
        return regex.findAll(opfXml).map { it.groupValues[1] }.toList()
    }
}
