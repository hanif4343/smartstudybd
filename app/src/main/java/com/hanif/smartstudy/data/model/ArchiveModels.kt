package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

/* ─────────────────────────────────────────────────────────────────────────
   Archive সেকশন — সম্পূর্ণ নতুন, existing QuizModels.kt/ReferenceModels.kt-এর
   কোনো ক্লাস স্পর্শ করা হয়নি। GAS-এর নতুন ৪টা action (getArchiveQuestionsPage,
   getArchiveQuestionsSorted, archiveMarkDuplicate, archiveMoveToActive) —এর
   রেসপন্স/রিকোয়েস্ট শেপ এখানে।

   ডিজাইন রিমাইন্ডার (backend প্ল্যানের সাথে হুবহু মিলিয়ে):
   - Archive-এর কোনো row কখনো delete হয় না — review_status ("" / "duplicate" /
     "moved") দিয়েই সব ট্র্যাক হয়।
   - Move to Active করলে সবসময় নতুন id জেনারেট হয় (পুরনো Archive id কখনো
     Active শিটে বসে না)।
   ───────────────────────────────────────────────────────────────────────── */

/** কোন Archive শিট নিয়ে কাজ হচ্ছে — GAS-এর ARCHIVE_SHEET_MAP_ এর key-এর সাথে হুবহু মেলানো */
enum class ArchiveSheet(val gasKey: String, val label: String) {
    QUIZ_ARCHIVE("archive_quiz", "Quiz Archive"),
    QBANK_ARCHIVE("archive_qbank", "QBank Archive")
}

/** "Topics Archive" শিটের একটা রো — getSheetRows?tab=Topics Archive থেকে আসে */
data class ArchiveTopicRef(
    @SerializedName("topic_id")   val topicId   : String? = null,
    @SerializedName("subject_id") val subjectId : String? = null,
    @SerializedName("topic_name") val name      : String? = null,
    @SerializedName("row_count_quiz")  val rowCountQuiz  : String? = null,
    @SerializedName("row_count_qbank") val rowCountQbank : String? = null
) {
    /** এই টপিকে (নির্দিষ্ট আর্কাইভ শিটের জন্য) মোট রো-সংখ্যা — শুধু আনুমানিক
     * "কতগুলো আছে" দেখানোর জন্য, রিভিউ হওয়া/না-হওয়া এখানে আলাদা করা নেই। */
    fun rowCountFor(sheet: ArchiveSheet): Int =
        (if (sheet == ArchiveSheet.QUIZ_ARCHIVE) rowCountQuiz else rowCountQbank)
            ?.toDoubleOrNull()?.toInt() ?: 0
}

/** একটা Archive প্রশ্নের রো — getArchiveQuestionsPage/getArchiveQuestionsSorted থেকে আসে।
 * Sheet-এ আরও অনেক কলাম থাকতে পারে (technique, previousexam, visualurl ইত্যাদি) —
 * এখানে শুধু UI-তে যা লাগবে সেটাই ম্যাপ করা, বাকি সব Gson নিজে থেকেই ইগনোর করবে। */
data class ArchiveQuestion(
    val id             : String = "",
    val question       : String = "",
    val option1        : String = "",
    val option2        : String = "",
    val option3        : String = "",
    val option4        : String = "",
    val correct        : String = "",
    val explanation    : String = "",
    val subject        : String = "",
    @SerializedName("subject_id") val subjectId : String = "",
    val topic          : String = "",
    @SerializedName("topic_id")   val topicId   : String = "",
    @SerializedName("review_status") val reviewStatus : String = "",
    @SerializedName("moved_to_id")   val movedToId   : String = "",
    // ── getArchiveQuestionsSorted-এর সিরিয়াল (A-Z সর্ট করার পর ১,২,৩...) —
    // getArchiveQuestionsPage-এ এটা 0/অনুপস্থিত থাকবে ──
    @SerializedName("_srl") val srl : Int = 0
) {
    /** ডুপ্লিকেট-স্ক্যানের সুবিধার জন্য — কাছাকাছি/হুবহু প্রশ্ন এক নজরে বোঝার একটা সরল হেল্পার */
    fun normalizedQuestion(): String = question.trim().lowercase().replace(Regex("\\s+"), " ")
}

/** getArchiveQuestionsPage রেসপন্স */
data class ArchivePageResult(
    val rows       : List<ArchiveQuestion> = emptyList(),
    val hasMore    : Boolean = false,
    val nextCursor : Int = 0,
    val total      : Int = 0,
    val error      : String? = null
)

/** archiveMoveToActive রেসপন্স */
data class ArchiveMoveResult(
    val success   : Boolean = false,
    val moved     : Int = 0,
    val newIds    : List<String> = emptyList(),
    val subjectId : String? = null,
    val topicId   : String? = null,
    val error     : String? = null
)
