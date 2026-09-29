package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TechniqueCacheDao {

    @Query("SELECT * FROM technique_cache WHERE questionId = :questionId LIMIT 1")
    suspend fun get(questionId: String): TechniqueCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TechniqueCacheEntity)

    /** ব্যবহারকারী ম্যানুয়ালি "রিফ্রেশ" চাপলে — এই একটা প্রশ্নের cache মুছে পরের বার
     *  আবার fetch হতে দেয় (দেখো UserTechniqueSection-এর রিফ্রেশ আইকন)। */
    @Query("DELETE FROM technique_cache WHERE questionId = :questionId")
    suspend fun invalidate(questionId: String)
}
