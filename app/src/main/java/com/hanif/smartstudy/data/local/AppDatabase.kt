package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, TypingMistakeEntity::class, TypingHandStatsEntity::class, GeneratedPassageCacheEntity::class, StudyTypingProgressEntity::class, CustomPassageEntity::class, TypingSheetPassageEntity::class, TypingKeyStatEntity::class, CurriculumProgressEntity::class, SubjectEntity::class, TopicEntity::class, SubTopicEntity::class, TagEntity::class, PostEntity::class, InstitutionEntity::class, ExamAppearanceEntity::class, TopicSyncEntity::class],
    // v1 → v2: QuestionEntity তে explanationIsPublic column যোগ হলো
    // v2 → v3: TypingMistakeEntity যোগ হলো — word-level mistake tracking
    // v3 → v4: TypingHandStatsEntity যোগ হলো — বাম/ডান হাতের error-rate tracking
    // v4 → v5: GeneratedPassageCacheEntity যোগ হলো — AI-generated passage cache
    // (একই দুর্বল-শব্দ সেটের জন্য বারবার API call এড়াতে)
    // v5 → v6: StudyTypingProgressEntity যোগ হলো — "স্টাডি টাইপিং" ফিচারে কোন STUDY
    // আইটেম ইতিমধ্যে টাইপ করা হয়ে গেছে তার ট্র্যাকিং (একবার হলে আর না আসার জন্য)
    // v6 → v7: CustomPassageEntity যোগ হলো — ইউজারের নিজের যোগ করা টাইপিং প্যাসেজ (লোকাল-অনলি)
    // v7 → v8: TypingSheetPassageEntity যোগ হলো — Google Sheet "Typing" ট্যাব থেকে আসা
    // ডিফল্ট প্যাসেজ পুলের অফলাইন cache (হার্ডকোডেড PASSAGES তালিকা রিমুভ করার পর)
    // v8 → v9: TypingKeyStatEntity যোগ হলো — Neonlipi-স্টাইল Phase ১: প্রতিটা কী-এর
    // cumulative সঠিক/ভুল কাউন্ট — লাইভ হিটম্যাপ ও দুর্বল-কী ড্রিলের ভিত্তি
    // v9 → v10: CurriculumProgressEntity যোগ হলো — Phase ৩ Key-unlock কারিকুলাম:
    // ইউজার কোন স্টেজ পর্যন্ত আনলক করেছে (bn/en আলাদা), দেখো data/model/BijoyCurriculum.kt
    // v10 → v11: Phase 6 (DB migration v2, User App) — QuestionEntity-তে subjectId/topicId/
    // subtopicId/groupId/subIndex কলাম যোগ (Admin App-এর নতুন Sheet schema-র সাথে সামঞ্জস্য),
    // আর ৭টা নতুন reference-টেবিলের local cache: SubjectEntity/TopicEntity/SubTopicEntity/
    // TagEntity/PostEntity/InstitutionEntity/ExamAppearanceEntity (GAS getReferenceData থেকে
    // populate হবে — দেখো data/model/ReferenceModels.kt, data/local/ReferenceDao.kt)।
    // fallbackToDestructiveMigration() থাকায় migration SQL লাগে না।
    // v11 → v12: QuestionEntity-তে reviewed/reviewedAt কলাম যোগ (Admin-only Review System)
    // v12 → v13: TopicSyncEntity যোগ — প্রতিটা Topic-এ getQuestionsPage দিয়ে কতদূর আনা
    // হয়েছে তার ট্র্যাকিং (progressive fill: একই ব্যাচ দুইবার না, ধীরে ধীরে পুরো Topic
    // লোকালি জমা হয়ে অফলাইন-সক্ষম হয়ে যায়)।
    // fallbackToDestructiveMigration() থাকায় migration SQL লাগে না।
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun questionDao(): QuestionDao
    abstract fun typingMistakeDao(): TypingMistakeDao
    abstract fun typingHandStatsDao(): TypingHandStatsDao
    abstract fun generatedPassageCacheDao(): GeneratedPassageCacheDao
    abstract fun studyTypingProgressDao(): StudyTypingProgressDao
    abstract fun customPassageDao(): CustomPassageDao
    abstract fun typingSheetPassageDao(): TypingSheetPassageDao
    abstract fun typingKeyStatDao(): TypingKeyStatDao
    abstract fun curriculumProgressDao(): CurriculumProgressDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun topicSyncDao(): TopicSyncDao

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
