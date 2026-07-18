package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TypingMistakeDao {

    // ── একটা নির্দিষ্ট শব্দের বর্তমান রেকর্ড খোঁজো (upsert-এর আগে count বাড়ানোর জন্য) ──
    @Query("SELECT * FROM typing_mistakes WHERE userId = :userId AND targetWord = :targetWord AND language = :language LIMIT 1")
    suspend fun findByWord(userId: String, targetWord: String, language: String): TypingMistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TypingMistakeEntity)

    // ── টপ-N দুর্বল শব্দ — frequency আর recency দুটোই বিবেচনা করে সাজানো
    // (নতুন ভুল বেশি গুরুত্ব পায়, পুরনো ভুল ধীরে ধীরে ওজন হারায়) —
    // AI sentence-generation-এর ইনপুট হিসেবে এই ফাংশনটাই মূলত ব্যবহৃত হবে ──
    @Query("""
        SELECT * FROM typing_mistakes 
        WHERE userId = :userId AND language = :language
        ORDER BY (mistakeCount * 1000000 - (:now - lastSeenAt) / 3600000) DESC
        LIMIT :limit
    """)
    suspend fun getTopWeakWords(
        userId  : String,
        language: String,
        now     : Long = System.currentTimeMillis(),
        limit   : Int = 10
    ): List<TypingMistakeEntity>

    // ── আজকে review করার মতো শব্দ (spaced repetition due — ধাপ ৩-এ ব্যবহৃত হবে) ──
    @Query("""
        SELECT * FROM typing_mistakes 
        WHERE userId = :userId AND language = :language AND nextReviewAt <= :now
        ORDER BY nextReviewAt
        LIMIT :limit
    """)
    suspend fun getDueForReview(
        userId  : String,
        language: String,
        now     : Long = System.currentTimeMillis(),
        limit   : Int = 10
    ): List<TypingMistakeEntity>

    // ── ইউজারের মোট কতগুলো আলাদা দুর্বল শব্দ ট্র্যাক হচ্ছে ──
    @Query("SELECT COUNT(*) FROM typing_mistakes WHERE userId = :userId AND language = :language")
    suspend fun countForUser(userId: String, language: String): Int

    // ── ডিবাগ/রিসেট প্রয়োজনে ──
    @Query("DELETE FROM typing_mistakes WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
