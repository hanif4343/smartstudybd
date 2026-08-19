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

    // ── Admin Subject/Topic ডিলিট ইনস্ট্যান্ট দেখানোর জন্য — নাম দিয়ে subjectId
    // রিজলভ করতে হয় (deleteByIds/deleteReferenceId-এর id-ভিত্তিক API নাম নেয় না) ──
    @Query("SELECT * FROM subjects WHERE sheet = :sheet AND name = :name LIMIT 1")
    suspend fun getSubjectByName(sheet: String, name: String): SubjectEntity?

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query("DELETE FROM subjects WHERE subjectId = :subjectId")
    suspend fun deleteSubjectById(subjectId: String)

    // ── Topics ────────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopics(items: List<TopicEntity>)

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY name")
    suspend fun getTopicsForSubject(subjectId: String): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE subjectId = :subjectId AND name = :name LIMIT 1")
    suspend fun getTopicByName(subjectId: String, name: String): TopicEntity?

    @Query("SELECT * FROM topics ORDER BY name")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE topicId = :topicId LIMIT 1")
    suspend fun getTopicById(topicId: String): TopicEntity?

    @Query("DELETE FROM topics")
    suspend fun deleteAllTopics()

    @Query("DELETE FROM topics WHERE topicId = :topicId")
    suspend fun deleteTopicById(topicId: String)

    @Query("DELETE FROM topics WHERE subjectId = :subjectId")
    suspend fun deleteTopicsBySubjectId(subjectId: String)

    // ── SubTopics ─────────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubTopics(items: List<SubTopicEntity>)

    @Query("SELECT * FROM subtopics WHERE topicId = :topicId ORDER BY name")
    suspend fun getSubTopicsForTopic(topicId: String): List<SubTopicEntity>

    @Query("SELECT * FROM subtopics ORDER BY name")
    suspend fun getAllSubTopics(): List<SubTopicEntity>

    @Query("DELETE FROM subtopics")
    suspend fun deleteAllSubTopics()

    @Query("DELETE FROM subtopics WHERE topicId = :topicId")
    suspend fun deleteSubTopicsByTopicId(topicId: String)

    @Query("DELETE FROM subtopics WHERE topicId IN (SELECT topicId FROM topics WHERE subjectId = :subjectId)")
    suspend fun deleteSubTopicsBySubjectId(subjectId: String)

    // ── FIX ("সাবজেক্ট/টপিক ডিলিট হচ্ছে না" বাগ, মূল কারণ): Subject/Topic-এর আন্ডারের সব
    // প্রশ্ন ডিলিট হলেও SubjectListScreen/SubTopicListScreen যেই "subjects"/"topics"
    // reference-টেবিল থেকে পড়ে, সেটা আগে কখনো ছোঁয়াই হতো না — তাই খালি Subject/Topic
    // এন্ট্রিটা তালিকায় থেকেই যেত। এই দুটো cascade delete দিয়ে reference-টেবিল থেকেও
    // (তার আন্ডারের topics/subtopics-সহ) সাথে সাথে সরিয়ে দেওয়া হয়, @Transaction দিয়ে
    // atomic (মাঝপথে fail হলে আংশিক ডিলিট থেকে যাবে না) ──
    @Transaction
    suspend fun deleteSubjectCascade(subjectId: String) {
        deleteSubTopicsBySubjectId(subjectId)
        deleteTopicsBySubjectId(subjectId)
        deleteSubjectById(subjectId)
    }

    @Transaction
    suspend fun deleteTopicCascade(topicId: String) {
        deleteSubTopicsByTopicId(topicId)
        deleteTopicById(topicId)
    }

    // ── Admin "Move Topic" (ফাইল ম্যানেজারের মতো, অন্য Subject-এ) ──
    @Query("UPDATE topics SET subjectId = :newSubjectId WHERE topicId = :topicId")
    suspend fun reparentTopic(topicId: String, newSubjectId: String)

    // ── FIX ("সাবজেক্টে টপিক-সংখ্যা / টপিকে প্রশ্ন-সংখ্যা বেমিল দেখাচ্ছে", "move করার পর
    // কাউন্ট রিয়েল-টাইম আপডেট হয় না", "Article: 74 প্রশ্ন দেখাতো Quiz-এ ঢুকলে ভিতরে ২৩টা"):
    // rowCount কলাম শুধু syncReferenceData() (GAS থেকে, পর্যায়ক্রমে) দিয়ে বসতো — কোনো
    // move/delete-এর সময় local ভাবে এটা কখনো ছোঁয়াই হতো না। এছাড়াও একই topic_id
    // Quiz/QBank/Study — তিনটা sheet-এই আলাদা প্রশ্ন-সংখ্যা থাকতে পারে, তাই একটা মাত্র
    // generic rowCount কলামে সব sheet-এর জন্য একই সংখ্যা বসানো ভুল ছিল। এখন প্রতিটা
    // sheet-এর জন্য আলাদা আপডেট-মেথড — ContentRepository.refreshTopicRowCount() move-এর
    // সাথে সাথেই dao.countByTopicId() দিয়ে Room থেকে লাইভ কাউন্ট গুনে সঠিক sheet-এর
    // কলামে বসিয়ে দেয় (+ legacy rowCount-ও fallback হিসেবে সিঙ্কে রাখে) — বাকি সব ফিল্ড
    // (name, subjectId, order ইত্যাদি) অক্ষত থাকে। ──
    @Query("UPDATE topics SET rowCount = :count WHERE topicId = :topicId")
    suspend fun updateTopicRowCount(topicId: String, count: Int)

    @Query("UPDATE topics SET rowCountQuiz = :count WHERE topicId = :topicId")
    suspend fun updateTopicRowCountQuiz(topicId: String, count: Int)

    @Query("UPDATE topics SET rowCountQbank = :count WHERE topicId = :topicId")
    suspend fun updateTopicRowCountQbank(topicId: String, count: Int)

    @Query("UPDATE topics SET rowCountStudy = :count WHERE topicId = :topicId")
    suspend fun updateTopicRowCountStudy(topicId: String, count: Int)

    @Query("UPDATE subtopics SET topicId = :targetTopicId WHERE topicId = :sourceTopicId")
    suspend fun reassignSubTopics(sourceTopicId: String, targetTopicId: String)

    /** destination Subject-এ same নামের Topic আগে থেকে থাকলে merge — sourceTopicId-এর
     *  subtopics/questions সব targetTopicId-তে চলে যায়, সোর্স Topic-এর reference-রো
     *  ডিলিট হয়ে যায় (এখানে শুধু reference-টেবিল অংশ; questions টেবিল আলাদা করে
     *  QuestionDao.moveQuestionsByTopicId দিয়ে আপডেট হয় — ContentRepository দেখো)। */
    @Transaction
    suspend fun mergeTopicCascade(sourceTopicId: String, targetTopicId: String) {
        reassignSubTopics(sourceTopicId, targetTopicId)
        deleteTopicById(sourceTopicId)
    }

    // ── "নতুন Topic যোগ করে Move" — অস্থায়ী লোকাল topicId (adminAddQuestion-এর
    // localId প্যাটার্নের মতোই) ব্যাকগ্রাউন্ডে GAS-এর addReferenceItem দেওয়া আসল
    // topicId দিয়ে replace করে (Room-এর PK topicId বলে "update" না, delete+insert)। ──
    @Transaction
    suspend fun replaceTopicId(oldTopicId: String, newTopicId: String) {
        val old = getTopicById(oldTopicId) ?: return
        deleteTopicById(oldTopicId)
        upsertTopics(listOf(old.copy(topicId = newTopicId)))
        reassignSubTopics(oldTopicId, newTopicId)
    }

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
