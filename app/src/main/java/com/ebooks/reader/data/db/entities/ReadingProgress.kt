package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val chapterHref: String = "",
    val scrollPosition: Int = 0,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
