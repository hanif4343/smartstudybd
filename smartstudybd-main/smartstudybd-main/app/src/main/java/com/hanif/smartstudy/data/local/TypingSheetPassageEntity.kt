package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Google Sheet "Typing" ট্যাব (headers: id, language, content, updatedAt) থেকে
 * fetch করা passage-গুলোর অফলাইন cache — নেট না থাকলেও আগেরবার fetch করা পুল
 * থেকে প্র্যাকটিস চালানো যায়। দেখো TypingPassageProvider.kt।
 */
@Entity(tableName = "typing_sheet_passages")
data class TypingSheetPassageEntity(
    @PrimaryKey val id: String = "",
    val language  : String = "",
    val content   : String = "",
    val updatedAt : Long   = 0L
)
