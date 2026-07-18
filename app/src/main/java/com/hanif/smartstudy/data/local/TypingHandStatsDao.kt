package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TypingHandStatsDao {

    @Query("SELECT * FROM typing_hand_stats WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): TypingHandStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TypingHandStatsEntity)

    /** একটা সেশনের ডেল্টা (session-local counts) বিদ্যমান রেকর্ডের সাথে যোগ করে সেভ করে */
    suspend fun addSessionDelta(
        userId: String,
        leftCorrect: Long, leftWrong: Long,
        rightCorrect: Long, rightWrong: Long
    ) {
        val existing = get(userId)
        upsert(
            TypingHandStatsEntity(
                userId           = userId,
                leftCorrectChars = (existing?.leftCorrectChars ?: 0L) + leftCorrect,
                leftWrongChars   = (existing?.leftWrongChars ?: 0L) + leftWrong,
                rightCorrectChars= (existing?.rightCorrectChars ?: 0L) + rightCorrect,
                rightWrongChars  = (existing?.rightWrongChars ?: 0L) + rightWrong,
                updatedAt        = System.currentTimeMillis()
            )
        )
    }
}
