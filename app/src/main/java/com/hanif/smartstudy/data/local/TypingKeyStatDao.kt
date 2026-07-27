package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TypingKeyStatDao {

    @Query("SELECT * FROM typing_key_stats WHERE userId = :userId AND keyChar = :keyChar AND language = :language LIMIT 1")
    suspend fun find(userId: String, keyChar: String, language: String): TypingKeyStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TypingKeyStatEntity)

    /** হিটম্যাপের জন্য — সবচেয়ে বেশি প্র্যাকটিস হওয়া কী আগে (যথেষ্ট ডেটা না থাকা কী
     *  দেখিয়ে বিভ্রান্ত করার দরকার নেই), সীমিত সংখ্যক দেখানো হয় ──*/
    @Query("""
        SELECT * FROM typing_key_stats 
        WHERE userId = :userId AND language = :language
        ORDER BY (correctCount + wrongCount) DESC
        LIMIT :limit
    """)
    suspend fun getMostPracticed(userId: String, language: String, limit: Int = 16): List<TypingKeyStatEntity>

    /** দুর্বল-কী ড্রিলের জন্য — যথেষ্ট নমুনা (minSamples) আছে এমন কী-গুলোর মধ্যে
     *  সবচেয়ে কম accuracy-র কী আগে ──*/
    @Query("""
        SELECT * FROM typing_key_stats 
        WHERE userId = :userId AND language = :language AND (correctCount + wrongCount) >= :minSamples
        ORDER BY (CAST(correctCount AS REAL) / (correctCount + wrongCount)) ASC
        LIMIT :limit
    """)
    suspend fun getWeakest(userId: String, language: String, minSamples: Int = 10, limit: Int = 6): List<TypingKeyStatEntity>

    @Query("DELETE FROM typing_key_stats WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
