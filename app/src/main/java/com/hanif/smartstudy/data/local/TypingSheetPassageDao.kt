package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TypingSheetPassageDao {

    @Query("SELECT * FROM typing_sheet_passages")
    suspend fun getAll(): List<TypingSheetPassageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TypingSheetPassageEntity>)

    @Query("DELETE FROM typing_sheet_passages")
    suspend fun clearAll()

    /** সার্ভার থেকে নতুন fetch হওয়া পুরো তালিকা দিয়ে cache replace করে — পুরনো/মুছে
     *  ফেলা row cache-এ পড়ে না থাকার জন্য আগে clear তারপর insert, একটাই transaction-এ। */
    @Transaction
    suspend fun replaceAll(items: List<TypingSheetPassageEntity>) {
        clearAll()
        insertAll(items)
    }
}
