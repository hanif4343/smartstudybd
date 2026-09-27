package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedQuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markDeleted(entity: DeletedQuestionEntity)

    /** এই sheet-এর সব tombstoned questionId — syncToRoom() এখান থেকেই বাদ দেওয়ার লিস্ট বানায় */
    @Query("SELECT questionId FROM deleted_questions WHERE sheet = :sheet")
    suspend fun idsForSheet(sheet: String): List<String>

    /** Firebase-এ delete কনফার্ম হয়ে গেলে tombstone আর দরকার নেই — housekeeping */
    @Query("DELETE FROM deleted_questions WHERE sheet = :sheet AND questionId = :questionId")
    suspend fun clear(sheet: String, questionId: String)
}
