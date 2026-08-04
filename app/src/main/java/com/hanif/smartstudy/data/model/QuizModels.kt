package com.hanif.smartstudy.data.model

// ── Navigation path ──
data class NavPath(
    val subject  : String? = null,   // Level 1
    val subTopic : String? = null    // Level 2
) {
    fun depth() = when {
        subTopic != null -> 2
        subject  != null -> 1
        else             -> 0
    }
}

// ── App Mode ──
enum class StudyMode { QUIZ, STUDY, QBANK }

// ── QBank-only ফিল্টার: ডিফল্ট পদবী (Designation) — এখনকার Subject→SubTopic
// হায়ারার্কি অপরিবর্তিত। প্রতিষ্ঠান (Institution) বাছাই করলে হায়ারার্কি উল্টে যায়
// (আগে প্রতিষ্ঠান, তার আন্ডারে যত পদবী)। সাল (Year) বাছাই করলে flat প্রশ্ন-লিস্ট
// (subject/subTopic নির্বিশেষে ওই সালের সব প্রশ্ন একসাথে)। ──
//
// POST (Phase 6, db-migration-v2) — নতুন schema-র Posts/Institutions/Exam_Appearances
// reference-টেবিল থেকে আসে (দেখো data/local/ReferenceDao.kt)। DESIGNATION/INSTITUTION
// মোড OLD schema-র raw subject/sub_topic টেক্সট থেকে আসে (১ প্রশ্ন = ১টা মাত্র
// designation+institution জোড়া) — POST মোডে একই প্রশ্ন একাধিক পরীক্ষায় (ভিন্ন
// Institution/Year) আলাদা appearance-row হিসেবে থাকতে পারে, তাই এই মোডে একই প্রশ্ন
// একাধিক জায়গায় দেখা যেতে পারে।
enum class QBankFilterMode { DESIGNATION, INSTITUTION, YEAR, POST }

// ── Question answer state (per question) ──
sealed class AnswerState {
    object Unanswered : AnswerState()
    data class McqSelected(val option: Int, val isCorrect: Boolean) : AnswerState()
    data class WrittenSubmitted(val userText: String, val matchPct: Int, val isCorrect: Boolean) : AnswerState()
    // Model Test-এর written প্রশ্নে অটো-চেক (matchPct) হয় না — ইউজার যেভাবে লিখেছে ঠিক
    // সেভাবেই সংরক্ষিত থাকে, এডমিন পরে দেখে যাচাই করবে
    data class WrittenRecorded(val userText: String) : AnswerState()
    object Skipped : AnswerState()
}

// ── Single question with its answer state ──
data class QuestionItem(
    val id          : String  = "",
    val subject     : String  = "",
    val subTopic    : String  = "",
    val question    : String  = "",
    val optionA     : String  = "",
    val optionB     : String  = "",
    val optionC     : String  = "",
    val optionD     : String  = "",
    val answer      : String  = "",      // correct answer text / correct option text
    val explanation : String  = "",
    // ব্যাখ্যা Public (সবাই দেখবে) নাকি Private (শুধু Admin দেখবে) — ডিফল্ট Public
    val explanationIsPublic: Boolean = true,
    val technique   : String  = "",
    val questionType: String  = "mcq",   // "mcq" | "written"
    val audienceTags: String  = "",
    val year        : String  = "",      // QBank only
    val examName    : String  = "",      // QBank only
    val imageUrl    : String  = "",      // embedded image
    val visualUrl   : String  = "",      // VisualURL — image/video/pdf links from Firebase
    // "Question Paper" কলাম — কমা দিয়ে আলাদা একাধিক ImgBB লিংক (আসল প্রশ্নপত্রের ছবি),
    // শুধু QBank-এই ব্যবহৃত হয়, প্রয়োজন হলেই (টগল করে) দেখানো হয়
    val questionPaperUrls: String = "",
    // Runtime state
    val answerState : AnswerState = AnswerState.Unanswered,
    val isBookmarked: Boolean     = false,
    val isWeakTopic : Boolean     = false,
    val isStudyDone : Boolean     = false,  // Study mode: "পড়া হয়েছে" টিকমার্ক — লিস্টের নিচে যাবে, হাইড হবে না
    // ── Model Test এর জন্য ──
    val isImportant  : Boolean    = false,  // admin ম্যানুয়াল ফ্ল্যাগ / বা একাধিক Year-এ repeat হওয়ায় auto-detected
    val sourceSheet  : String     = "",     // "Quiz" | "QBank" | "Study" — কোন sheet থেকে এসেছে
    // ── Phase 6 নতুন schema fields (Admin App migration v2) — খালি স্ট্রিং/０ মানে
    // এখনো reference-টেবিলে link হয়নি বা standalone (group ছাড়া) প্রশ্ন ──
    val subjectId    : String     = "",
    val topicId      : String     = "",
    val subtopicId   : String     = "",     // QBank only
    val groupId      : String     = "",     // multi-part প্রশ্নের সব sub-question একই groupId শেয়ার করে
    val subIndex     : Int        = 0,      // group-এর ভেতর ক্রম (১,২,৩...) — sourceKey দিয়ে unique না বলে আলাদা
    // ── Review System (Admin-only) — student-দের কাছে সম্পূর্ণ অদৃশ্য, কোনো UI/behavior
    // প্রভাব নেই। শুধু Admin-এর Review Mode-এ দেখানো/আপডেট করা হয়। ──
    val reviewed     : Boolean    = false,
    val reviewedAt   : Long       = 0L
) {
    fun isWritten() = questionType.lowercase().trim() == "written"
    fun isStudy()   = questionType.lowercase().trim() == "study"
    fun isMcq()     = !isWritten() && !isStudy()
    fun isGrouped() = groupId.isNotBlank()

    // "Question Paper" কলামের কমা-সেপারেটেড ImgBB লিংকগুলো লিস্ট আকারে —
    // খালি/স্পেস-শুধু অংশ বাদ দিয়ে
    fun questionPaperImageList(): List<String> =
        questionPaperUrls.split(",").map { it.trim() }.filter { it.isNotBlank() }

    // "sheet|id" ফরম্যাটে ইউনিক কী — Model Test এ Quiz আর QBank দুই সোর্স মিক্স হয়,
    // তাই শুধু id দিয়ে ইউনিক না-ও হতে পারে (দুই sheet-এ একই index/key থাকা সম্ভব)
    fun sourceKey() = "$sourceSheet|$id"

    companion object {
        fun fromStudyItem(s: StudyItem) = QuestionItem(
            id           = s.id ?: "",
            subject      = s.subject ?: "",
            subTopic     = s.subTopic ?: "",
            question     = s.question ?: "",
            // index.html: corRaw = getVal(i,'correct'), ansRaw = getVal(i,'answer')
            // correct field আগে দেখি, না থাকলে answer
            answer       = (s.correct ?: s.answer) ?: "",
            // index: expRaw = getVal(i,'explanation'), ansRaw fallback
            explanation  = (s.explanation ?: s.answer) ?: "",
            explanationIsPublic = (s.explanationVisibility?.lowercase()?.trim() != "private"),
            technique    = s.technique ?: "",
            questionType = s.questionType?.lowercase()?.trim() ?: "study",
            audienceTags = s.audienceTags ?: "",
            visualUrl    = s.visualUrl ?: "",
            sourceSheet  = "Study",
            subjectId    = s.subjectId ?: "",
            topicId      = s.topicId ?: "",
            groupId      = s.groupId ?: "",
            subIndex     = s.subIndex?.toIntOrNull() ?: 0,
            reviewed     = s.reviewed?.lowercase()?.trim() == "true",
            reviewedAt   = s.reviewedAt?.toLongOrNull() ?: 0L
        )
        fun fromQuizItem(q: QuizItem) = QuestionItem(
            id           = q.id ?: "",
            subject      = q.subject ?: "",
            subTopic     = q.subTopic ?: "",
            question     = q.question ?: "",
            optionA      = q.optionA ?: "",
            optionB      = q.optionB ?: "",
            optionC      = q.optionC ?: "",
            optionD      = q.optionD ?: "",
            answer       = q.answer ?: "",
            explanation  = q.explanation ?: "",
            explanationIsPublic = (q.explanationVisibility?.lowercase()?.trim() != "private"),
            technique    = q.technique ?: "",
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: "",
            visualUrl    = q.visualUrl ?: "",
            isImportant  = q.important == true,
            sourceSheet  = "Quiz",
            subjectId    = q.subjectId ?: "",
            topicId      = q.topicId ?: "",
            groupId      = q.groupId ?: "",
            subIndex     = q.subIndex?.toIntOrNull() ?: 0,
            reviewed     = q.reviewed?.lowercase()?.trim() == "true",
            reviewedAt   = q.reviewedAt?.toLongOrNull() ?: 0L
        )
        fun fromQBankItem(q: QBankItem) = QuestionItem(
            id           = q.id ?: "",
            subject      = q.subject ?: "",
            subTopic     = q.subTopic ?: "",
            question     = q.question ?: "",
            optionA      = q.optionA ?: "",
            optionB      = q.optionB ?: "",
            optionC      = q.optionC ?: "",
            optionD      = q.optionD ?: "",
            answer       = q.answer ?: "",
            explanation  = q.explanation ?: "",
            explanationIsPublic = (q.explanationVisibility?.lowercase()?.trim() != "private"),
            technique    = q.technique ?: "",
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: "",
            year         = q.year ?: "",
            examName     = q.examName ?: "",
            visualUrl    = q.visualUrl ?: "",
            questionPaperUrls = q.questionPaperUrls ?: "",
            isImportant  = q.important == true,
            sourceSheet  = "QBank",
            subjectId    = q.subjectId ?: "",
            topicId      = q.topicId ?: "",
            subtopicId   = q.subtopicId ?: "",
            groupId      = q.groupId ?: "",
            subIndex     = q.subIndex?.toIntOrNull() ?: 0,
            reviewed     = q.reviewed?.lowercase()?.trim() == "true",
            reviewedAt   = q.reviewedAt?.toLongOrNull() ?: 0L
        )

        // NOTE: আগে এখানে fromStudyMcqCandidate() নামে একটা ফাংশন ছিল যেটা
        // com.hanif.smartstudy.util.StudyMcqGenerator.Candidate টাইপ ব্যবহার করত —
        // কিন্তু StudyMcqGenerator ক্লাসটা প্রজেক্টে কখনো তৈরি হয়নি এবং ফাংশনটা
        // কোথাও কল ও হতো না (Model Test-এ Study-MCQ আসলে QuizViewModel.startModelTest()
        // এ সরাসরি test.inlineMcq থেকে বসানো হয়, sourceSheet = "StudyMcq")।
        // Unresolved reference build error এর কারণ ছিল এটাই — অব্যবহৃত/অসম্পূর্ণ
        // ফাংশনটা মুছে ফেলা হলো।
    }
}

// ── Subject with progress ──
data class SubjectEntry(
    val name         : String,
    val totalQ       : Int,
    val doneQ        : Int,
    val subTopics    : List<SubTopicEntry> = emptyList(),
    // ── Phase 6 (db-migration-v2) — reference-টেবিলের subject_id, খালি হতে পারে যদি
    // QuizViewModel এখনো Room Subjects ম্যাপ থেকে resolve না করে থাকে (backward-compat)।
    // ⚠️ এখনো UI-তে ব্যবহৃত হচ্ছে না — SubjectListScreen.kt-এর onSubject callback এখনো
    // নাম-ভিত্তিক (QuizViewModel.kt/CoreScreen.kt আপডেট না হওয়া পর্যন্ত এটাই নিরাপদ পথ)।
    val subjectId    : String = ""
) {
    val progressPct: Int get() = if (totalQ > 0) (doneQ * 100) / totalQ else 0
}

data class SubTopicEntry(
    val name      : String,
    val subject   : String,
    val totalQ    : Int,
    val doneQ     : Int,
    val isWeak    : Boolean = false,
    // ── Model Test: এটা আসল subTopic না, বরং subTopic list-এর মধ্যে বসানো একটা
    // virtual/special card যেটা ট্যাপ করলে ওই subject এর Model Test list খোলে।
    // Study ও QBank দুই জায়গাতেই একই সোর্স (Firebase: ModelTests/{subject}) থেকে আসে।
    val isModelTest     : Boolean = false,
    val modelTestCount  : Int     = 0,      // কতগুলো Model Test আছে ওই subject-এ (সাবটাইটেলে দেখানোর জন্য)
    // ── QBank: এই সাবটপিকের প্রশ্নগুলো MCQ নাকি Written — কার্ডে ব্যাজ দেখানোর জন্য ──
    val mcqCount     : Int = 0,
    val writtenCount : Int = 0,
    // ── Phase 6 (db-migration-v2) — দেখো SubjectEntry.subjectId এর নোট, একই কারণ প্রযোজ্য ──
    val subjectId    : String = "",
    val topicId      : String = "",
    // ── Phase 6 POST মোড: এই (Post+Institution) জোড়ার আন্ডারে যত প্রশ্ন Exam_Appearances-এ
    // appear করেছে তাদের সরাসরি fbKey লিস্ট — ট্যাপ করলে repo.getRoomQuestionsByIds() দিয়ে
    // সরাসরি এই ID গুলো টেনে দেখানো হয়, কোনো subject/subTopic টেক্সট ম্যাচিং লাগে না
    // (দেখো QuizViewModel.selectQBankInstitutionUnderPost)
    val linkedQuestionIds : List<String> = emptyList()
) {
    val progressPct: Int get() = if (totalQ > 0) (doneQ * 100) / totalQ else 0

    // "mcq" | "written" | "mixed" — কার্ডে ব্যাজ/আইকন দেখাতে ব্যবহার হয়
    val questionTypeLabel: String get() = when {
        mcqCount > 0 && writtenCount > 0 -> "mixed"
        writtenCount > 0                 -> "written"
        else                              -> "mcq"
    }
}

// ── Model Test — এডমিন-কিউরেটেড, ফিক্সড, সবার জন্য একই ──
// Firebase: ModelTests/{subject}/{testNumber} → {title, type, totalMarks, createdAt, questions:{...}}
// questionIds প্রতিটা এন্ট্রি "sheet|id" ফরম্যাটে থাকে (sheet="Quiz"/"QBank") — দুই সোর্স থেকে প্রশ্ন মিক্স করা যায় বলে।
data class ModelTestMeta(
    val subject     : String       = "",
    val testNumber  : Int          = 0,
    val title       : String       = "",
    val type        : String       = "both",   // "mcq" | "written" | "both" — অডিয়েন্স/এডমিন প্রি-সেট
    val totalMarks  : Int          = 0,        // সবসময় পূর্ণমান — ইউজার বদলাতে পারবে না
    val questionIds : List<String> = emptyList(),
    val createdAt   : Long         = 0L,
    // Study-র ছোট-উত্তরের প্রশ্ন থেকে auto-generate করা MCQ — এগুলোর options/answer কোনো
    // স্থায়ী Quiz/QBank আইটেমে নেই (সিন্থেটিক), তাই ফুল ডেটা এখানেই ইনলাইন সেভ থাকে,
    // sourceKey (যেমন "StudyMcq|abc") দিয়ে questionIds থেকে ম্যাপ হয়
    val inlineMcq   : Map<String, InlineMcqQuestion> = emptyMap()
) {
    fun displayTitle() = title.ifBlank { "মডেল টেস্ট $testNumber" }
    fun hasType(t: String) = type == "both" || type == t
}

// Study থেকে auto-generate করা MCQ-র সিন্থেটিক ডেটা (distractor অন্য প্রশ্নের আসল উত্তর থেকে ধার করা)
data class InlineMcqQuestion(
    val question : String = "",
    val optionA  : String = "",
    val optionB  : String = "",
    val optionC  : String = "",
    val optionD  : String = "",
    val answer   : String = "",
    val subTopic : String = ""
)

// ── Quiz/Result ──
data class QuizResult(
    val total        : Int,
    val correct      : Int,
    val wrong        : Int,
    val skipped      : Int,
    val timeTakenSec : Int,
    val xpEarned     : Int,
    val subjectBreakdown: Map<String, SubjectScore> = emptyMap(),
    // Model Test-এর written প্রশ্নে অটো-চেক হয় না — কতগুলো উত্তর "সংরক্ষিত" হয়েছে
    // (সঠিক/ভুল বিচার না করে) সেটা এখানে থাকে, এডমিন পরে যাচাই করবে
    val recorded     : Int = 0
) {
    val pct: Int get() = if (total > 0) (correct * 100) / total else 0
    val emoji: String get() = when {
        pct >= 80 -> "🏆"
        pct >= 60 -> "👏"
        pct >= 40 -> "💪"
        else      -> "📚"
    }
    val title: String get() = when {
        pct >= 80 -> "অসাধারণ!"
        pct >= 60 -> "ভালো হয়েছে!"
        pct >= 40 -> "চেষ্টা করো!"
        else      -> "আরো পড়তে হবে!"
    }
}

data class SubjectScore(
    val subject: String,
    val correct: Int,
    val total  : Int
) {
    val pct: Int get() = if (total > 0) (correct * 100) / total else 0
}

// ── Mock Test config ──
data class MockTestConfig(
    val selectedTopics : List<String> = emptyList(),   // "Subject||SubTopic"
    val questionLimit  : Int          = 25,
    val mode           : StudyMode    = StudyMode.QUIZ
)

// ── Weak topic ──
data class WeakTopic(
    val subTopic   : String,
    val subject    : String,
    val wrongCount : Int
)
