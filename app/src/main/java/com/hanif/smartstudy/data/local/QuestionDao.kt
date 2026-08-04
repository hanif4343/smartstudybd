package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuestionDao {

    // ── Insert / Upsert ──────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: QuestionEntity)

    // ── Subject list (distinct) ───────────────────────────────────────────────
    @Query("SELECT DISTINCT subject FROM questions WHERE sheet = :sheet AND subject != '' ORDER BY subject")
    suspend fun getSubjects(sheet: String): List<String>

    // ── SubTopic list for a subject ───────────────────────────────────────────
    @Query("""
        SELECT DISTINCT subTopic FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic != '' 
        ORDER BY subTopic
    """)
    suspend fun getSubTopics(sheet: String, subject: String): List<String>

    // ── Question count per subject ────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND subject = :subject")
    suspend fun countBySubject(sheet: String, subject: String): Int

    // ── Question count per subTopic ───────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic")
    suspend fun countBySubTopic(sheet: String, subject: String, subTopic: String): Int

    // ── PAGINATED questions for a subTopic — এটাই সবচেয়ে গুরুত্বপূর্ণ query ──
    // LIMIT + OFFSET দিয়ে শুধু একটা page আনে — সব ১০,০০০ প্রশ্ন মেমরিতে আনতে হয় না
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedQuestions(
        sheet   : String,
        subject : String,
        subTopic: String,
        limit   : Int,
        offset  : Int
    ): List<QuestionEntity>

    // ── ALL questions for a subTopic (quiz mode-এ shuffle এর জন্য) ──────────
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
        ORDER BY fbKey
    """)
    suspend fun getAllForSubTopic(sheet: String, subject: String, subTopic: String): List<QuestionEntity>

    // ── Audience-filtered paginated query ────────────────────────────────────
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedQuestionsFiltered(
        sheet   : String,
        subject : String,
        subTopic: String,
        tag     : String,
        limit   : Int,
        offset  : Int
    ): List<QuestionEntity>

    // ── Count filtered questions (total pages জানার জন্য) ────────────────────
    @Query("""
        SELECT COUNT(*) FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic = :subTopic
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
    """)
    suspend fun countFiltered(sheet: String, subject: String, subTopic: String, tag: String): Int

    // ── SubjectEntry data (subject + count) একসাথে ───────────────────────────
    @Query("""
        SELECT subject, COUNT(*) as count FROM questions 
        WHERE sheet = :sheet AND subject != ''
        GROUP BY subject
        ORDER BY subject
    """)
    suspend fun getSubjectCounts(sheet: String): List<SubjectCount>

    // ── SubTopicEntry data ────────────────────────────────────────────────────
    @Query("""
        SELECT subTopic, COUNT(*) as count FROM questions 
        WHERE sheet = :sheet AND subject = :subject AND subTopic != ''
        GROUP BY subTopic
        ORDER BY subTopic
    """)
    suspend fun getSubTopicCounts(sheet: String, subject: String): List<SubTopicCount>

    // ═════════════════════════════════════════════════════════════════════════
    // QBank-only ফিল্টার: "প্রতিষ্ঠান-ভিত্তিক" ও "সাল-ভিত্তিক" — এই দুটোতে
    // সাধারণ subject/subTopic হায়ারার্কি উল্টে বা পাশ কাটিয়ে দেখতে হয়, তাই
    // subject-নিরপেক্ষভাবে subTopic (Institution) বা year ধরে group করা লাগে।
    // ═════════════════════════════════════════════════════════════════════════

    // ── প্রতিষ্ঠান তালিকা (subject-নিরপেক্ষ, সব ডিজিগনেশন মিলিয়ে unique subTopic) —
    // SubjectCount ক্লাসই রিইউজ করা হলো (subTopic কে "subject" কলাম নামে alias করে) ──
    @Query("""
        SELECT subTopic as subject, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND subTopic != ''
        GROUP BY subTopic
        ORDER BY subTopic
    """)
    suspend fun getInstitutionCounts(sheet: String): List<SubjectCount>

    // ── একটা নির্দিষ্ট প্রতিষ্ঠানের আন্ডারে যত পদবী (Designation) আছে —
    // SubTopicCount ক্লাস রিইউজ করা হলো (subject কে "subTopic" কলাম নামে alias করে) ──
    @Query("""
        SELECT subject as subTopic, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND subTopic = :institution AND subject != ''
        GROUP BY subject
        ORDER BY subject
    """)
    suspend fun getDesignationsUnderInstitution(sheet: String, institution: String): List<SubTopicCount>

    // ── সালের তালিকা (subject/subTopic-নিরপেক্ষ, সব মিলিয়ে unique year) ──
    @Query("""
        SELECT year as subject, COUNT(*) as count FROM questions
        WHERE sheet = :sheet AND year != ''
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearCounts(sheet: String): List<SubjectCount>

    // ── সাল-ভিত্তিক flat প্রশ্ন-লিস্ট (subject/subTopic নির্বিশেষে) — পেজিনেটেড ──
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND year = :year
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedByYear(sheet: String, year: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND year = :year
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedByYearFiltered(sheet: String, year: String, tag: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND year = :year")
    suspend fun countByYear(sheet: String, year: String): Int

    @Query("""
        SELECT COUNT(*) FROM questions WHERE sheet = :sheet AND year = :year
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
    """)
    suspend fun countByYearFiltered(sheet: String, year: String, tag: String): Int

    // ── DB তে কতটা data আছে ──────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM questions WHERE sheet = :sheet")
    suspend fun countAll(sheet: String): Int

    // ── সর্বশেষ sync time (delta sync এর জন্য) ──────────────────────────────
    @Query("SELECT MAX(syncedAt) FROM questions WHERE sheet = :sheet")
    suspend fun getLastSyncTime(sheet: String): Long?

    // ── পুরো sheet মুছো (full refresh) ──────────────────────────────────────
    @Query("DELETE FROM questions WHERE sheet = :sheet")
    suspend fun deleteSheet(sheet: String)

    // ── সব data মুছো ──────────────────────────────────────────────────────────
    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    // ── একটা প্রশ্ন ID দিয়ে খোঁজো (admin edit/report এর জন্য) ──────────────
    @Query("SELECT * FROM questions WHERE sheet = :sheet AND fbKey = :fbKey LIMIT 1")
    suspend fun getById(sheet: String, fbKey: String): QuestionEntity?

    // ── Phase 6: multi-part প্রশ্ন — একই groupId-এর সব sub-question, sub_index অনুযায়ী
    // সাজানো। খালি groupId পাঠালে কিছুই ফেরত আসবে না (standalone প্রশ্নের জন্য এই কল লাগেই না) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND groupId = :groupId AND groupId != ''
        ORDER BY subIndex
    """)
    suspend fun getGroupMates(sheet: String, groupId: String): List<QuestionEntity>

    // ── Phase 6: "পদ অনুযায়ী ব্রাউজ" — Exam_Appearances থেকে একগুচ্ছ questionId পেলে
    // সরাসরি সেই নির্দিষ্ট প্রশ্নগুলো টেনে আনার জন্য (audience-filtered, একটা নির্দিষ্ট
    // Post+Institution-এর আন্ডারে যত প্রশ্ন appear করেছে সবগুলো একসাথে) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND fbKey IN (:ids)
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
    """)
    suspend fun getByFbKeysFiltered(sheet: String, ids: List<String>, tag: String): List<QuestionEntity>

    // ── Review System (Admin-only): লোকাল Room cache-এ reviewed status আপডেট —
    // GAS-এ লেখার পর Room-ও সাথে সাথে sync রাখার জন্য (fresh fetch ছাড়াই cache নির্ভুল থাকে) ──
    @Query("UPDATE questions SET reviewed = :reviewed, reviewedAt = :reviewedAt WHERE sheet = :sheet AND fbKey = :fbKey")
    suspend fun updateReviewed(sheet: String, fbKey: String, reviewed: Boolean, reviewedAt: Long)

    // ── Progressive topic-fill: এই topicId-এর জন্য এখন পর্যন্ত Room-এ যতটুকু cache
    // হয়েছে সব — audience-filtered। GAS getQuestionsPage থেকে ব্যাচ-ব্যাচ করে এখানে জমা
    // হয় (দেখো ContentRepository.cacheNextTopicBatch) ──
    @Query("""
        SELECT * FROM questions
        WHERE sheet = :sheet AND topicId = :topicId
          AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%')
        ORDER BY fbKey
    """)
    suspend fun getByTopicId(sheet: String, topicId: String, tag: String): List<QuestionEntity>

    // ── Global search — Room cache (offline/persistent) থেকে সব ফিল্ড মিলিয়ে খোঁজে।
    // ⚠️ Phase 6 ফিক্স: আগে এখানে audience-tag ফিল্টার ছিলই না — যেকোনো ইউজারের সার্চে
    // অন্য audience group-এর (ভিন্ন চাকরি/ক্লাসের) প্রশ্নও চলে আসতো, আর LIMIT 50-এর
    // স্লট সেগুলো দখল করে আসল প্রাসঙ্গিক ফলাফল বাদ দিয়ে ফেলতে পারতো। এখন
    // getPagedQuestionsFiltered/getByFbKeysFiltered-এর মতোই একই audience-tag শর্ত ──
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND (audienceTags = '' OR audienceTags LIKE '%' || :tag || '%') AND (
            question LIKE '%' || :query || '%' OR 
            answer   LIKE '%' || :query || '%' OR
            subject  LIKE '%' || :query || '%' OR
            subTopic LIKE '%' || :query || '%' OR
            optionA  LIKE '%' || :query || '%' OR
            optionB  LIKE '%' || :query || '%'
        )
        LIMIT 50
    """)
    suspend fun search(sheet: String, query: String, tag: String): List<QuestionEntity>
}

// ── Helper projection classes ─────────────────────────────────────────────────
data class SubjectCount(val subject: String, val count: Int)
data class SubTopicCount(val subTopic: String, val count: Int)
