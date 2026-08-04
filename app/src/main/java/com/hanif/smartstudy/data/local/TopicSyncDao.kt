package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TopicSyncDao {
    @Query("SELECT * FROM topic_sync WHERE topicId = :topicId LIMIT 1")
    suspend fun get(topicId: String): TopicSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TopicSyncEntity)

    @Query("DELETE FROM topic_sync")
    suspend fun clearAll()
}
