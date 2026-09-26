package com.hanif.smartstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuestionProgressDao {

    @Query("SELECT * FROM question_progress WHERE userId = :userId AND mode = :mode AND questionId = :questionId LIMIT 1")
    suspend fun get(userId: String, mode: String, questionId: String): QuestionProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuestionProgressEntity)

    // ── একগুচ্ছ topicId এর জন্য একসাথে attempted+correct — subject/topic list
    // লোড হওয়ার সময় এক কোয়েরিতেই সব টপিকের % হিসাব হয়ে যায় ──
    @Query("""
        SELECT topicId AS topicId,
               COUNT(*) AS attempted,
               SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END) AS correct
        FROM question_progress
        WHERE userId = :userId AND mode = :mode AND topicId IN (:topicIds)
        GROUP BY topicId
    """)
    suspend fun statsForTopics(userId: String, mode: String, topicIds: List<String>): List<TopicProgressStat>

    @Query("""
        SELECT subjectId AS subjectId,
               COUNT(*) AS attempted,
               SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END) AS correct
        FROM question_progress
        WHERE userId = :userId AND mode = :mode AND subjectId IN (:subjectIds)
        GROUP BY subjectId
    """)
    suspend fun statsForSubjects(userId: String, mode: String, subjectIds: List<String>): List<SubjectProgressStat>

    @Query("SELECT questionId FROM question_progress WHERE userId = :userId AND mode = :mode AND isCorrect = 0")
    suspend fun wrongQuestionIds(userId: String, mode: String): List<String>

    @Query("SELECT questionId FROM question_progress WHERE userId = :userId AND mode = :mode")
    suspend fun attemptedQuestionIds(userId: String, mode: String): List<String>

    // ── ব্যাচ Firebase-sync এর জন্য — এখনো sync হয়নি এমন রো (backup, UI progress এর জন্য লাগে না) ──
    @Query("SELECT * FROM question_progress WHERE userId = :userId AND synced = 0 LIMIT :limit")
    suspend fun getUnsynced(userId: String, limit: Int = 50): List<QuestionProgressEntity>

    @Query("UPDATE question_progress SET synced = 1 WHERE userId = :userId AND mode = :mode AND questionId = :questionId")
    suspend fun markSynced(userId: String, mode: String, questionId: String)

    @Query("DELETE FROM question_progress WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
