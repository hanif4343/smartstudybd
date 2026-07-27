package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurriculumProgressDao {

    @Query("SELECT * FROM curriculum_progress WHERE userId = :userId AND track = :track LIMIT 1")
    suspend fun get(userId: String, track: String): CurriculumProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CurriculumProgressEntity)
}
