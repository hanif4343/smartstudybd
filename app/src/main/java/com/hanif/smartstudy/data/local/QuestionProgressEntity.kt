package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * প্রতিটা প্রশ্নে ইউজার সর্বশেষ কী উত্তর দিয়েছে (সঠিক/ভুল) — সরাসরি Room-এ
 * persist হয়, ১০০% local/offline, লিখতে কোনো Firebase কল লাগে না (তাই instant,
 * এবং quota-safe)। subjectId/topicId এখানেই সেভ থাকে বলে — কোনো টপিকের কত%
 * সঠিক হয়েছে সেটা জানতে পুরো টপিকের প্রশ্ন ডাউনলোড করে গোনা লাগে না, শুধু একটা
 * aggregate Room query (দেখো QuestionProgressDao) — তাই lazy subject/topic
 * list-এও তাৎক্ষণিক সঠিক accuracy % দেখানো সম্ভব।
 *
 * userId (phone) দিয়ে আলাদা রাখা হয়েছে — একই ডিভাইসে একাধিক ইউজার লগইন করলে
 * একজনের প্রগ্রেস আরেকজনের সাথে না গুলিয়ে যায়।
 *
 * Firebase-এ backup sync হয় আলাদাভাবে, ব্যাচ করে — দেখো ContentRepository.
 * flushProgressToFirebaseIfDue() ও worker/SyncWorker.kt। UI-এর progress %
 * সবসময় এই লোকাল টেবিল থেকেই আসে, Firebase read লাগে না।
 */
@Entity(
    tableName   = "question_progress",
    primaryKeys = ["userId", "mode", "questionId"]
)
data class QuestionProgressEntity(
    val userId    : String  = "",
    val mode      : String  = "QUIZ",   // StudyMode.name → "QUIZ" | "QBANK" | "STUDY"
    val questionId: String  = "",
    val subjectId : String  = "",
    val topicId   : String  = "",
    val isCorrect : Boolean = false,    // সর্বশেষ attempt-এর ফলাফল
    val attempts  : Int     = 1,
    val updatedAt : Long    = 0L,
    val synced    : Boolean = false     // Firebase backup এ গেছে কিনা (batched sync)
)

data class TopicProgressStat(val topicId: String, val attempted: Int, val correct: Int)
data class SubjectProgressStat(val subjectId: String, val attempted: Int, val correct: Int)
