package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudyTypingProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markUsed(entity: StudyTypingProgressEntity)

    // ── একটা sub_topic-এ যে আইটেমগুলো ইতিমধ্যে টাইপ করা হয়ে গেছে তাদের id ──
    @Query("""
        SELECT contentId FROM study_typing_progress
        WHERE userId = :userId AND subject = :subject AND subTopic = :subTopic
    """)
    suspend fun getUsedIds(userId: String, subject: String, subTopic: String): List<String>

    // ── কতগুলো আইটেম শেষ হয়েছে ("কারক: ৮/১০ শেষ"-এর মতো প্রগ্রেস দেখানোর জন্য) ──
    @Query("""
        SELECT COUNT(*) FROM study_typing_progress
        WHERE userId = :userId AND subject = :subject AND subTopic = :subTopic
    """)
    suspend fun countUsed(userId: String, subject: String, subTopic: String): Int

    // ── শুধু এই sub_topic-এর used id গুলো মুছবে (পুরো ট্র্যাকিং টেবিল না) ──
    @Query("""
        DELETE FROM study_typing_progress
        WHERE userId = :userId AND subject = :subject AND subTopic = :subTopic
    """)
    suspend fun resetSubTopic(userId: String, subject: String, subTopic: String)

    @Query("DELETE FROM study_typing_progress WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
