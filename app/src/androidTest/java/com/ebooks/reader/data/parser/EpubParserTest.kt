package com.ebooks.reader.data.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Instrumented tests for EpubParser.
 * Verifies parsing behavior with valid and malformed EPUBs.
 */
@RunWith(AndroidJUnit4::class)
class EpubParserTest {

    private lateinit var context: Context
    private lateinit var parser: EpubParser

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        parser = EpubParser(context)
    }

    /**
     * Tests that parser returns null gracefully for missing OPF file.
     * This represents EPUBs with non-standard or missing container.xml.
     */
    @Test
    fun parsingMissingOPFReturnsNull() {
        val epubBytes = createMinimalZip(
            mapOf("META-INF/container.xml" to "<invalid>no rootfile</invalid>".toByteArray())
        )
        val testFile = createTestFile(epubBytes)
        val uri = android.net.Uri.fromFile(testFile)

        val result = parser.parse(uri)

        assert(result == null) { "Parser should return null for missing OPF" }
        testFile.delete()
    }

    /**
     * Tests that parser returns null gracefully when the EPUB is not a valid ZIP.
     */
    @Test
    fun parsingCorruptZipReturnsNull() {
        val corruptBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val testFile = createTestFile(corruptBytes)
        val uri = android.net.Uri.fromFile(testFile)

        val result = parser.parse(uri)

        assert(result == null) { "Parser should return null for corrupt ZIP" }
        testFile.delete()
    }

    /**
     * Tests that parser returns null gracefully for empty EPUBs.
     */
    @Test
    fun parsingEmptyEpubReturnsNull() {
        val epubBytes = createMinimalZip(emptyMap())
        val testFile = createTestFile(epubBytes)
        val uri = android.net.Uri.fromFile(testFile)

        val result = parser.parse(uri)

        assert(result == null) { "Parser should return null for empty EPUB" }
        testFile.delete()
    }

    /**
     * Tests that parser handles EPUBs with non-standard directory structure.
     */
    @Test
    fun parsingNonStandardPathsReturnsNull() {
        val epubBytes = createMinimalZip(
            mapOf(
                "different/path/container.xml" to containerXmlContent().toByteArray(),
                "content.opf" to "<package></package>".toByteArray()
            )
        )
        val testFile = createTestFile(epubBytes)
        val uri = android.net.Uri.fromFile(testFile)

        val result = parser.parse(uri)

        // Parser specifically looks for META-INF/container.xml, so this should fail gracefully
        assert(result == null) { "Parser should return null for non-standard paths" }
        testFile.delete()
    }

    /**
     * Tests that parser handles EPUBs with missing manifest gracefully.
     */
    @Test
    fun parsingMissingManifestReturnsValidBook() {
        val epubBytes = createMinimalEpub(includeManifest = false)
        val testFile = createTestFile(epubBytes)
        val uri = android.net.Uri.fromFile(testFile)

        val result = parser.parse(uri)

        // Should still parse but with empty chapters
        assert(result != null) { "Parser should return a book even with missing manifest" }
        assert(result?.chapters?.isEmpty() == true) { "Book should have no chapters" }
        testFile.delete()
    }

    // Helper functions

    private fun createMinimalZip(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((path, content) in files) {
                val entry = ZipEntry(path)
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun createMinimalEpub(includeManifest: Boolean = true): ByteArray {
        val files = mutableMapOf(
            "META-INF/container.xml" to containerXmlContent().toByteArray(),
            "OEBPS/content.opf" to opfContent(includeManifest).toByteArray()
        )
        return createMinimalZip(files)
    }

    private fun containerXmlContent(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles>
        </container>
    """.trimIndent()

    private fun opfContent(includeManifest: Boolean): String = if (includeManifest) {
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title>
                <dc:creator>Test Author</dc:creator>
            </metadata>
            <manifest>
                <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="ch1.html" media-type="application/xhtml+xml"/>
            </manifest>
            <spine toc="toc">
                <itemref idref="ch1"/>
            </spine>
        </package>
        """.trimIndent()
    } else {
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title>
            </metadata>
        </package>
        """.trimIndent()
    }

    private fun createTestFile(content: ByteArray): File {
        val file = File(context.cacheDir, "test_${System.currentTimeMillis()}.epub")
        file.writeBytes(content)
        return file
    }
}
