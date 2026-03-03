package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterHref: String,
    val position: Int,
    val selectedText: String? = null,
    val note: String? = null,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
