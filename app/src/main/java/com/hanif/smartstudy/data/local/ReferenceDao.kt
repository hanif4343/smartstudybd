package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Subjects/Topics/SubTopics/Tags/Posts/Institutions/Exam_Appearances — ৭টা ছোট reference-টেবিলের
 * জন্য একটাই Dao (Admin App-এর ReferenceManagerTab.jsx যেমন ৬টা আলাদা পেজের বদলে একটাই
 * reusable UI ব্যবহার করে, এখানেও সেই একই স্পিরিট — ছোট টেবিলগুলো একসাথে বাল্ক-ফেচ/রিফ্রেশ হয়)।
 */
@Dao
interface ReferenceDao {

    // ── Subjects ──────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(items: List<SubjectEntity>)

    @Query("SELECT * FROM subjects ORDER BY name")
    suspend fun getAllSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE sheet = :sheet ORDER BY name")
    suspend fun getSubjectsBySheet(sheet: String): List<SubjectEntity>

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    // ── Topics ────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopics(items: List<TopicEntity>)

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY name")
    suspend fun getTopicsForSubject(subjectId: String): List<TopicEntity>

    @Query("SELECT * FROM topics ORDER BY name")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("DELETE FROM topics")
    suspend fun deleteAllTopics()

    // ── SubTopics ─────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTopics(items: List<SubTopicEntity>)

    @Query("SELECT * FROM subtopics WHERE topicId = :topicId ORDER BY name")
    suspend fun getSubTopicsForTopic(topicId: String): List<SubTopicEntity>

    @Query("SELECT * FROM subtopics ORDER BY name")
    suspend fun getAllSubTopics(): List<SubTopicEntity>

    @Query("DELETE FROM subtopics")
    suspend fun deleteAllSubTopics()

    // ── Tags ──────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(items: List<TagEntity>)

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAllTags(): List<TagEntity>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    // ── Posts (পদ) ────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(items: List<PostEntity>)

    @Query("SELECT * FROM posts ORDER BY name")
    suspend fun getAllPosts(): List<PostEntity>

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()

    // ── Institutions (প্রতিষ্ঠান) ─────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstitutions(items: List<InstitutionEntity>)

    @Query("SELECT * FROM institutions ORDER BY name")
    suspend fun getAllInstitutions(): List<InstitutionEntity>

    @Query("DELETE FROM institutions")
    suspend fun deleteAllInstitutions()

    // ── Exam Appearances ─────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamAppearances(items: List<ExamAppearanceEntity>)

    @Query("SELECT * FROM exam_appearances WHERE questionId = :questionId")
    suspend fun getAppearancesForQuestion(questionId: String): List<ExamAppearanceEntity>

    @Query("SELECT * FROM exam_appearances WHERE postId = :postId")
    suspend fun getAppearancesForPost(postId: String): List<ExamAppearanceEntity>

    @Query("SELECT * FROM exam_appearances WHERE institutionId = :institutionId")
    suspend fun getAppearancesForInstitution(institutionId: String): List<ExamAppearanceEntity>

    @Query("DELETE FROM exam_appearances")
    suspend fun deleteAllExamAppearances()

    // ── পুরো reference dataset রিফ্রেশ (GAS getReferenceData কল করার পর) —
    // ছোট টেবিল বলে delete-then-insert নিরাপদ, @Transaction দিয়ে atomic রাখা হলো
    // (মাঝপথে crash হলে অর্ধেক পুরনো/অর্ধেক নতুন ডেটা থেকে যাবে না)। ──
    @Transaction
    suspend fun replaceAll(
        subjects     : List<SubjectEntity>,
        topics       : List<TopicEntity>,
        subtopics    : List<SubTopicEntity>,
        tags         : List<TagEntity>,
        posts        : List<PostEntity>,
        institutions : List<InstitutionEntity>
    ) {
        deleteAllSubjects();     upsertSubjects(subjects)
        deleteAllTopics();       upsertTopics(topics)
        deleteAllSubTopics();    upsertSubTopics(subtopics)
        deleteAllTags();         upsertTags(tags)
        deleteAllPosts();        upsertPosts(posts)
        deleteAllInstitutions(); upsertInstitutions(institutions)
    }
}
