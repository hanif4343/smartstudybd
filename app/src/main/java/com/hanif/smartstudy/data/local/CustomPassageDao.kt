package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CustomPassageDao {

    @Query("SELECT * FROM custom_passages ORDER BY createdAt DESC")
    suspend fun getAll(): List<CustomPassageEntity>

    @Insert
    suspend fun insert(entity: CustomPassageEntity): Long

    @Delete
    suspend fun delete(entity: CustomPassageEntity)
}
