package com.ebooks.reader.data.parser

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser for FB2 (FictionBook 2.0) format — XML-based ebook format popular in Russia.
 * Extracts metadata, cover image, and converts the body to HTML in a single stream pass.
 */
class Fb2Parser(private val context: Context) {

    data class Fb2Book(
        val title: String,
        val author: String,
        val description: String?,
        val coverImageBase64: String?,
        val htmlContent: String
    )

    fun parse(uri: Uri): Fb2Book? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { parseFb2(it) }
    }.getOrNull()

    private fun parseFb2(stream: InputStream): Fb2Book {
        val parser = newPullParser(stream)

        // Context flags
        var inDescription = false
        var inTitleInfo = false
        var inAuthor = false
        var inAnnotation = false
        var inCoverPage = false
        var inBody = false
        var inBinary = false
        var currentTag = ""

        // Metadata accumulators
        var title = "Unknown Title"
        var author = "Unknown Author"
        var firstName = ""
        var lastName = ""
        var annotation = ""
        var coverImageId: String? = null

        // Binary blobs (base64-encoded images embedded in the FB2)
        var currentBinaryId = ""
        val binaries = mutableMapOf<String, StringBuilder>()

        // HTML body output
        val html = StringBuilder("<html><head><meta charset='UTF-8'/></head><body>")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    when (currentTag) {
                        "description" -> inDescription = true
                        "title-info"  -> if (inDescription) inTitleInfo = true
                        "author"      -> if (inTitleInfo) inAuthor = true
                        "annotation"  -> if (inTitleInfo) inAnnotation = true
                        "coverpage"   -> if (inDescription) inCoverPage = true
                        "image" -> when {
                            inCoverPage -> {
                                val href = parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "href")
                                coverImageId = href?.trimStart('#')
                            }
                            inBody -> {
                                val href = (parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "href"))
                                    ?.trimStart('#')
                                if (href != null) html.append("<img data-fb2-src='$href'/>")
                            }
                        }
                        "body"       -> inBody = true
                        "section"    -> if (inBody) html.append("<div class='section'>")
                        "title"      -> if (inBody) html.append("<h2>")
                        "p"          -> if (inBody) html.append("<p>")
                        "emphasis"   -> if (inBody) html.append("<em>")
                        "strong"     -> if (inBody) html.append("<strong>")
                        "empty-line" -> if (inBody) html.append("<br/>")
                        "binary" -> {
                            inBinary = true
                            currentBinaryId = parser.getAttributeValue(null, "id") ?: ""
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name ?: ""
                    when (tag) {
                        "description" -> inDescription = false
                        "title-info"  -> inTitleInfo = false
                        "author" -> {
                            if (inTitleInfo) {
                                inAuthor = false
                                val full = "$firstName $lastName".trim()
                                if (full.isNotBlank()) author = full
                                firstName = ""; lastName = ""
                            }
                        }
                        "annotation" -> inAnnotation = false
                        "coverpage"  -> inCoverPage = false
                        "body"       -> inBody = false
                        "section"    -> if (inBody) html.append("</div>")
                        "title"      -> if (inBody) html.append("</h2>")
                        "p"          -> if (inBody) html.append("</p>")
                        "emphasis"   -> if (inBody) html.append("</em>")
                        "strong"     -> if (inBody) html.append("</strong>")
                        "binary"     -> { inBinary = false; currentBinaryId = "" }
                    }
                    currentTag = ""
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    when {
                        inBinary && currentBinaryId.isNotBlank() ->
                            binaries.getOrPut(currentBinaryId) { StringBuilder() }.append(text)
                        inAnnotation -> annotation += text
                        inAuthor && currentTag == "first-name" -> firstName = text.trim()
                        inAuthor && currentTag == "last-name"  -> lastName = text.trim()
                        inTitleInfo && currentTag == "book-title" && text.isNotBlank() ->
                            title = text.trim()
                        inBody && text.isNotBlank() ->
                            html.append(text.escapeHtml())
                    }
                }
            }
            parser.next()
            eventType = parser.eventType
        }

        html.append("</body></html>")

        return Fb2Book(
            title = title,
            author = author,
            description = annotation.trim().ifBlank { null },
            coverImageBase64 = coverImageId?.let { binaries[it]?.toString()?.trim() },
            htmlContent = html.toString()
        )
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun newPullParser(stream: InputStream): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser().also { it.setInput(stream, "UTF-8") }
}
