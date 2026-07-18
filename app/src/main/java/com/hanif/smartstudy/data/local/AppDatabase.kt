package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, TypingMistakeEntity::class, TypingHandStatsEntity::class, GeneratedPassageCacheEntity::class],
    // v1 → v2: QuestionEntity তে explanationIsPublic column যোগ হলো
    // v2 → v3: TypingMistakeEntity যোগ হলো — word-level mistake tracking
    // v3 → v4: TypingHandStatsEntity যোগ হলো — বাম/ডান হাতের error-rate tracking
    // v4 → v5: GeneratedPassageCacheEntity যোগ হলো — AI-generated passage cache
    // (একই দুর্বল-শব্দ সেটের জন্য বারবার API call এড়াতে)
    // fallbackToDestructiveMigration() থাকায় migration SQL লাগে না।
    version  = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun questionDao(): QuestionDao
    abstract fun typingMistakeDao(): TypingMistakeDao
    abstract fun typingHandStatsDao(): TypingHandStatsDao
    abstract fun generatedPassageCacheDao(): GeneratedPassageCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartstudy.db"
                )
                .fallbackToDestructiveMigration()   // version bump হলে DB পুনরায় তৈরি হবে
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
