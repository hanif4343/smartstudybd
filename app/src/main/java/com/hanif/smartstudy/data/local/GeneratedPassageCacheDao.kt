package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeneratedPassageCacheDao {

    @Query("SELECT * FROM generated_passage_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): GeneratedPassageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeneratedPassageCacheEntity)

    // পুরনো cache এন্ট্রি মাঝেমধ্যে পরিষ্কার করার জন্য (ঐচ্ছিক, ভবিষ্যতে ব্যবহার হতে পারে)
    @Query("DELETE FROM generated_passage_cache WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
