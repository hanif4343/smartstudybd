package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TypingCurriculumStageContentDao {

    @Query("SELECT * FROM typing_curriculum_stage_content")
    suspend fun getAll(): List<TypingCurriculumStageContentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TypingCurriculumStageContentEntity>)

    @Query("DELETE FROM typing_curriculum_stage_content")
    suspend fun clearAll()

    /** TypingSheetPassageDao.replaceAll()-এর একই প্যাটার্ন — clear তারপর insert, এক transaction-এ। */
    @Transaction
    suspend fun replaceAll(items: List<TypingCurriculumStageContentEntity>) {
        clearAll()
        insertAll(items)
    }
}
