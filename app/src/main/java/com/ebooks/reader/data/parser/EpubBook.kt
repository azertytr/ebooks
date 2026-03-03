package com.ebooks.reader.data.parser

data class EpubBook(
    val title: String,
    val author: String,
    val description: String?,
    val publisher: String?,
    val language: String?,
    val coverBytes: ByteArray?,
    val chapters: List<EpubChapter>,
    val cssStyles: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpubBook) return false
        return title == other.title && author == other.author
    }

    override fun hashCode(): Int = 31 * title.hashCode() + author.hashCode()
}

data class EpubChapter(
    val index: Int,
    val title: String,
    val href: String,
    val subChapters: List<EpubChapter> = emptyList()
)

data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String = ""
)

data class SpineItem(
    val idref: String,
    val linear: Boolean = true
)

data class TocItem(
    val id: String,
    val title: String,
    val href: String,
    val order: Int,
    val children: List<TocItem> = emptyList()
)
