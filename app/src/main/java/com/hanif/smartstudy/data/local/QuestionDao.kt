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

    // ── offline এ local key দিয়ে যোগ করা প্রশ্ন sync হওয়ার পর আসল Firebase key তে rename করতে ──
    @Query("DELETE FROM questions WHERE sheet = :sheet AND fbKey = :fbKey")
    suspend fun deleteByKey(sheet: String, fbKey: String)

    // ── Global search ─────────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM questions 
        WHERE sheet = :sheet AND (question LIKE '%' || :query || '%' OR answer LIKE '%' || :query || '%')
        LIMIT 50
    """)
    suspend fun search(sheet: String, query: String): List<QuestionEntity>
}

// ── Helper projection classes ─────────────────────────────────────────────────
data class SubjectCount(val subject: String, val count: Int)
data class SubTopicCount(val subTopic: String, val count: Int)
