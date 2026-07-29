package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TypingKeyPairStatDao {

    @Query("SELECT * FROM typing_key_pair_stats WHERE userId = :userId AND fromChar = :fromChar AND toChar = :toChar AND language = :language LIMIT 1")
    suspend fun find(userId: String, fromChar: String, toChar: String, language: String): TypingKeyPairStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TypingKeyPairStatEntity)

    /** এই কী (toChar)-এ পৌঁছানোর সব bigram-এর মধ্যে সবচেয়ে বেশি গড়-দেরি হওয়া জুটি —
     *  Key Analysis কার্ডের "ধীর জুটি"। কম নমুনা (minCount) থাকা জুটি বাদ, নাহলে
     *  ১-২ বারের কাকতালীয় দেরিতে ভুল সিদ্ধান্ত হতে পারে ──*/
    @Query("""
        SELECT * FROM typing_key_pair_stats
        WHERE userId = :userId AND toChar = :toChar AND language = :language AND count >= :minCount
        ORDER BY (CAST(totalMs AS REAL) / count) DESC
        LIMIT 1
    """)
    suspend fun getSlowestPairFor(userId: String, toChar: String, language: String, minCount: Int = 3): TypingKeyPairStatEntity?

    @Query("DELETE FROM typing_key_pair_stats WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
