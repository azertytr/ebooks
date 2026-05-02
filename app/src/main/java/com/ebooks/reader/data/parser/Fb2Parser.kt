package com.ebooks.reader.data.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser for FB2 (FictionBook 2.0) format — XML-based ebook format popular in Russia.
 * Extracts metadata and converts FB2 content to HTML for display.
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
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val parser = newPullParser(stream)
            val metadata = parseFb2Metadata(parser)
            val coverImageBase64 = extractCoverImage(parser)
            val htmlContent = parseFb2Body(parser)

            Fb2Book(
                title = metadata["title"] ?: "Unknown Title",
                author = metadata["author"] ?: "Unknown Author",
                description = metadata["annotation"],
                coverImageBase64 = coverImageBase64,
                htmlContent = htmlContent
            )
        }
    }.getOrNull()

    private fun parseFb2Metadata(parser: XmlPullParser): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        var eventType = parser.eventType
        var inDescription = false
        var inTitleInfo = false
        var inAuthor = false
        var firstName = ""
        var lastName = ""
        var inAnnotation = false
        var annotationText = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "title-info" -> inTitleInfo = true
                        "description" -> inDescription = true
                        "book-title" -> {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                metadata["title"] = parser.text ?: ""
                            }
                        }
                        "author" -> inAuthor = true
                        "first-name" -> {
                            if (inAuthor) {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    firstName = parser.text ?: ""
                                }
                            }
                        }
                        "last-name" -> {
                            if (inAuthor) {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    lastName = parser.text ?: ""
                                }
                            }
                        }
                        "annotation" -> inAnnotation = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title-info" -> inTitleInfo = false
                        "description" -> inDescription = false
                        "author" -> {
                            inAuthor = false
                            if (firstName.isNotBlank() || lastName.isNotBlank()) {
                                metadata["author"] = "$firstName $lastName".trim()
                            }
                            firstName = ""
                            lastName = ""
                        }
                        "annotation" -> {
                            inAnnotation = false
                            if (annotationText.isNotBlank()) {
                                metadata["annotation"] = annotationText.trim()
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inAnnotation && parser.text?.isNotBlank() == true) {
                        annotationText += parser.text + "\n"
                    }
                }
            }
            parser.next()
            eventType = parser.eventType
        }

        return metadata
    }

    private fun extractCoverImage(parser: XmlPullParser): String? {
        return runCatching {
            // Reset parser to beginning
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "coverpage") {
                            var foundImage = false
                            while (!foundImage && eventType != XmlPullParser.END_DOCUMENT) {
                                parser.next()
                                eventType = parser.eventType
                                if (eventType == XmlPullParser.START_TAG && parser.name == "image") {
                                    val href = parser.getAttributeValue(null, "l:href")
                                        ?: parser.getAttributeValue(null, "href")
                                    return@runCatching href
                                }
                                if (eventType == XmlPullParser.END_TAG && parser.name == "coverpage") {
                                    foundImage = true
                                }
                            }
                        }
                    }
                }
                parser.next()
                eventType = parser.eventType
            }
            null
        }.getOrNull()
    }

    private fun parseFb2Body(parser: XmlPullParser): String {
        val html = StringBuilder("<html><head><meta charset='UTF-8'/></head><body>")
        var eventType = parser.eventType
        var foundBody = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "body" -> foundBody = true
                        "section" -> if (foundBody) html.append("<div class='section'>")
                        "title" -> if (foundBody) html.append("<h2>")
                        "p" -> if (foundBody) html.append("<p>")
                        "emphasis" -> if (foundBody) html.append("<em>")
                        "strong" -> if (foundBody) html.append("<strong>")
                        "empty-line" -> if (foundBody) html.append("<br/>")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "body" -> if (foundBody) return html.append("</body></html>").toString()
                        "section" -> if (foundBody) html.append("</div>")
                        "title" -> if (foundBody) html.append("</h2>")
                        "p" -> if (foundBody) html.append("</p>")
                        "emphasis" -> if (foundBody) html.append("</em>")
                        "strong" -> if (foundBody) html.append("</strong>")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (foundBody && parser.text?.isNotBlank() == true) {
                        html.append(parser.text?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;"))
                    }
                }
            }
            parser.next()
            eventType = parser.eventType
        }

        return html.append("</body></html>").toString()
    }

    private fun newPullParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")
        return parser
    }
}
