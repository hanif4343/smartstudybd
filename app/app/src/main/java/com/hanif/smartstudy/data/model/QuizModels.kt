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
    // Runtime state
    val answerState : AnswerState = AnswerState.Unanswered,
    val isBookmarked: Boolean     = false,
    val isWeakTopic : Boolean     = false,
    val isStudyDone : Boolean     = false,  // Study mode: "পড়া হয়েছে" টিকমার্ক — লিস্টের নিচে যাবে, হাইড হবে না
    // ── Model Test এর জন্য ──
    val isImportant  : Boolean    = false,  // admin ম্যানুয়াল ফ্ল্যাগ / বা একাধিক Year-এ repeat হওয়ায় auto-detected
    val sourceSheet  : String     = ""      // "Quiz" | "QBank" | "Study" — কোন sheet থেকে এসেছে
) {
    fun isWritten() = questionType.lowercase().trim() == "written"
    fun isStudy()   = questionType.lowercase().trim() == "study"
    fun isMcq()     = !isWritten() && !isStudy()

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
            sourceSheet  = "Study"
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
            technique    = "",
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: "",
            visualUrl    = q.visualUrl ?: "",
            isImportant  = q.important == true,
            sourceSheet  = "Quiz"
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
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: "",
            year         = q.year ?: "",
            examName     = q.examName ?: "",
            visualUrl    = q.visualUrl ?: "",
            isImportant  = q.important == true,
            sourceSheet  = "QBank"
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
    val subTopics    : List<SubTopicEntry> = emptyList()
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
    val modelTestCount  : Int     = 0       // কতগুলো Model Test আছে ওই subject-এ (সাবটাইটেলে দেখানোর জন্য)
) {
    val progressPct: Int get() = if (totalQ > 0) (doneQ * 100) / totalQ else 0
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
