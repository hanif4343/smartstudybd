package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, TypingMistakeEntity::class, TypingHandStatsEntity::class, GeneratedPassageCacheEntity::class, StudyTypingProgressEntity::class, CustomPassageEntity::class, TypingSheetPassageEntity::class, TypingKeyStatEntity::class, CurriculumProgressEntity::class, TypingKeyPairStatEntity::class, SubjectEntity::class, TopicEntity::class, SubTopicEntity::class, TagEntity::class, PostEntity::class, InstitutionEntity::class, ExamAppearanceEntity::class, TopicSyncEntity::class, TypingCurriculumStageContentEntity::class],
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
    // v10 → v11: TypingKeyStatEntity-তে totalLatencyMs/latencySumSqMs/latencySamples কলাম
    // যোগ হলো, আর TypingKeyPairStatEntity নতুন টেবিল হিসেবে যোগ হলো — Key Analysis
    // ফিচার (দ্বিধা/স্থিরতা/ধীর জুটি), দেখো ui/typing/TypingPracticeScreen.kt-এর KeyAnalysisSection
    // fallbackToDestructiveMigration() থাকায় migration SQL লাগে না।
    // v11 → v12: SubjectEntity/TopicEntity/SubTopicEntity/TagEntity/PostEntity/
    // InstitutionEntity/ExamAppearanceEntity + TopicSyncEntity যোগ হলো — Phase ৬
    // reference-ডেটা (ContentRepository.kt-এর referenceDao()/topicSyncDao())।
    // এই টেবিলগুলোর DAO/Entity ফাইল আগে থেকেই ছিল, শুধু এখানে রেজিস্টার করা হয়নি
    // ছিল — সেটাই compileDebugKotlin-এর "Unresolved reference" এর কারণ ছিল।
    // v12 → v13: SubjectEntity-তে tagId কলাম যোগ হলো — Subjects রেফারেন্স-শিটের নতুন
    // "tag_id" কলাম (Tags শিট lookup করে audience-ফিল্টার, দেখো AudienceFilter.
    // subjectVisibleForUser() ও QuizViewModel.rebuildSubjectsLazy())।
    // fallbackToDestructiveMigration() থাকায় migration SQL লাগে না — শুধু cache
    // রিফ্রেশ হবে, পরের syncReferenceData()-এই আবার পপুলেট হয়ে যাবে।
    // v13 → v14: TopicEntity-তে rowCountQuiz/rowCountQbank/rowCountStudy কলাম যোগ হলো
    // — FIX ("Article: 74 প্রশ্ন" দেখাতো কিন্তু Quiz শুরু করলে ভিতরে ২৩টা): আগে একটাই
    // mode-নিরপেক্ষ rowCount কলাম ছিল যেটাতে আসলে সবসময় Study sheet-এর কাউন্ট বসতো
    // (GAS rebuildIndex-এর প্রসেসিং-ক্রমের কারণে) — Quiz/QBank মোডে ব্রাউজ করলেও ভুল
    // সংখ্যা দেখাতো। এখন per-sheet কলাম আলাদা, দেখো ReferenceModels.kt/QuizViewModel.
    // navigateToSubjectLazy()। fallbackToDestructiveMigration() থাকায় migration SQL
    // লাগে না।
    // v15: TopicSyncEntity-তে lastHash কলাম যোগ (Speed Plan Task 3 — CDN hash-based
    // skip-fetch, দেখো TopicSyncEntity.kt-এর কমেন্ট)।
    // v16: QuestionEntity-তে groupHeading/formatStyle/important কলাম যোগ হলো — FIX
    // ("Sheet er group heading nai"): এই তিনটা আগে কখনো Room-এ persist হতো না, দেখো
    // QuestionEntity.kt-এর কমেন্ট। fallbackToDestructiveMigration() থাকায় migration
    // SQL লাগে না — পরের syncTopic/getRoomQuestionsByIds কলেই আবার সঠিকভাবে পপুলেট হবে।
    // v16 → v17: TypingCurriculumStageContentEntity যোগ হলো (Typing-ব্রাঞ্চ থেকে
    // মূল-ব্রাঞ্চে merge করার সময়) — Google Sheet-এর "CurriculumStages" ট্যাব থেকে
    // admin-curated কারিকুলাম-স্টেজ প্র্যাকটিস-কনটেন্টের অফলাইন cache (দেখো
    // CurriculumStageContentProvider.kt)।
    version = 17,
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
    abstract fun typingKeyPairStatDao(): TypingKeyPairStatDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun topicSyncDao(): TopicSyncDao
    abstract fun typingCurriculumStageContentDao(): TypingCurriculumStageContentDao

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
