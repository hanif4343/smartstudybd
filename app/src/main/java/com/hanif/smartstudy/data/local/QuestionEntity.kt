package com.hanif.smartstudy.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Room Entity — Quiz, QBank, Study তিনটাই এক টেবিলে।
 * `sheet` column দিয়ে আলাদা করা হয়: "QUIZ" | "QBANK" | "STUDY"
 *
 * Primary key: sheet + fbKey (Firebase-এর array index বা push key)
 *
 * Index on (sheet, subject, subTopic) → SubTopic query instant হয়।
 * Index on (sheet, subject)           → Subject list instant হয়।
 */
@Entity(
    tableName = "questions",
    primaryKeys = ["sheet", "fbKey"],
    indices = [
        Index(value = ["sheet", "subject", "subTopic"]),
        Index(value = ["sheet", "subject"]),
        Index(value = ["sheet"]),
        // ── Phase 6: নতুন schema (subject_id/topic_id) — future getQuestionsPage-স্টাইল
        // ক্যোয়ারির জন্য, আর groupId দিয়ে multi-part প্রশ্নের sub-question একসাথে বের করতে ──
        Index(value = ["sheet", "subjectId", "topicId"]),
        Index(value = ["sheet", "groupId"]),
        // ── Review System: টপিক-ভিত্তিক reviewed/unreviewed গোনা+ফিল্টার করার জন্য ──
        Index(value = ["sheet", "topicId", "reviewed"])
    ]
)
data class QuestionEntity(
    val sheet       : String = "",   // "QUIZ" | "QBANK" | "STUDY"
    val fbKey       : String = "",   // Firebase array index বা push key — unique per sheet
    val subject     : String = "",
    val subTopic    : String = "",
    val question    : String = "",
    val optionA     : String = "",
    val optionB     : String = "",
    val optionC     : String = "",
    val optionD     : String = "",
    val answer      : String = "",
    val explanation : String = "",
    // ব্যাখ্যা Public নাকি Private (শুধু Admin) — ডিফল্ট Public
    val explanationIsPublic: Boolean = true,
    val technique   : String = "",
    val questionType: String = "mcq",
    val audienceTags: String = "",
    val year        : String = "",   // QBank only
    val examName    : String = "",   // QBank only
    val imageUrl    : String = "",
    val visualUrl   : String = "",
    // "Question Paper" কলাম — কমা দিয়ে আলাদা করা একাধিক ImgBB লিংক (QBank only)
    val questionPaperUrls: String = "",
    // ── Phase 5/6 নতুন schema fields — Admin App migration-এর পর Sheet-এ এই কলামগুলো
    // বসেছে (subject_id/topic_id/subtopic_id/group_id/sub_index)। খালি থাকলে (পুরনো row
    // যেগুলো এখনো reference-টেবিলে link হয়নি) "" / 0 ডিফল্ট। ──
    val subjectId   : String = "",
    val topicId     : String = "",
    val subtopicId  : String = "",   // QBank only
    // multi-part প্রশ্ন (একই instruction-এর কয়েকটা sub-question, যেমন "কারক নির্ণয় কর")
    // একই groupId শেয়ার করে — খালি groupId মানে standalone প্রশ্ন
    val groupId     : String = "",
    val subIndex    : Int    = 0,
    // ── FIX ("Sheet er group heading nai" — QBank exam-paper স্ক্রিনে multi-part
    // প্রশ্নের হেডিং (যেমন "১. এক কথায় প্রকাশ করুন") মাঝেমধ্যে দেখা যেত না):
    // এই তিনটা কলাম আগে Room schema-তে কখনো যোগ হয়নি (দেখো EntityExtensions.kt-এর
    // আগের ⚠️ কমেন্ট) — অথচ groupId/subIndex ছিল। ফলে Room round-trip হওয়া
    // (এখন পদবী/প্রতিষ্ঠান/সাল — সবগুলো মোডই getRoomQuestionsByIds() দিয়ে যায়) যেকোনো
    // প্রশ্নের groupHeading/formatStyle/important চুপচাপ "" /false হয়ে যেত, যদিও
    // Sheet-এ Admin ঠিকই টাইপ করেছিল। এখন এই তিনটা কলাম যোগ করে toEntity()/
    // toQuestionItem()/toQuizItem()/toQBankItem()/toStudyItem() সবগুলোতে ওয়্যার করা
    // হলো (দেখো EntityExtensions.kt) — নতুন কলাম বলে Room schema migration লাগবে
    // (fallbackToDestructiveMigration ইতিমধ্যে অন থাকলে সমস্যা নেই, নাহলে DB version bump করতে হবে)। ──
    val groupHeading: String = "",
    val formatStyle : String = "",
    val important   : Boolean = false,
    // ── Review System (Admin-only) — Admin App-এ প্রতিটা প্রশ্ন 'রিভিউ করা হয়েছে' মার্ক
    // করার জন্য (Only-Admin ফিচার, student-দের UI/behavior-এ প্রভাব নেই)। ──
    val reviewed    : Boolean = false,
    val reviewedAt  : Long    = 0L,
    val syncedAt    : Long   = 0L    // Firebase থেকে কখন এসেছে — delta sync এর জন্য
)
