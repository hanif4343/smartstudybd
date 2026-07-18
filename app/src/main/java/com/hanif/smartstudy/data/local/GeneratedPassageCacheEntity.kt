package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * cacheKey = language + ":" + দুর্বল-শব্দগুলো sorted+joined করে বানানো একটা signature।
 * একই/কাছাকাছি দুর্বল-শব্দ সেটের জন্য বারবার AI call না করে এখান থেকেই রিইউজ হবে —
 * দেখো TypingAdaptiveContentProvider.kt।
 */
@Entity(tableName = "generated_passage_cache", primaryKeys = ["cacheKey"])
data class GeneratedPassageCacheEntity(
    val cacheKey  : String = "",
    val passage   : String = "",
    val provider  : String = "",
    val createdAt : Long = 0L
)
