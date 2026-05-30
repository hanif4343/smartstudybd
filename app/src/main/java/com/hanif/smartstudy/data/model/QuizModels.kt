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
    val technique   : String  = "",
    val questionType: String  = "mcq",   // "mcq" | "written"
    val audienceTags: String  = "",
    val year        : String  = "",      // QBank only
    val examName    : String  = "",      // QBank only
    val imageUrl    : String  = "",      // embedded image
    // Runtime state
    val answerState : AnswerState = AnswerState.Unanswered,
    val isBookmarked: Boolean     = false,
    val isWeakTopic : Boolean     = false
) {
    fun isWritten() = questionType.lowercase().trim() == "written"
    fun isMcq()     = !isWritten()

    companion object {
        fun fromStudyItem(s: StudyItem) = QuestionItem(
            id           = s.id ?: "",
            subject      = s.subject ?: "",
            subTopic     = s.subTopic ?: "",
            question     = s.question ?: "",
            answer       = s.answer ?: "",
            explanation  = s.answer ?: "",
            audienceTags = s.audienceTags ?: "",
            questionType = "written"
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
            technique    = "",
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: ""
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
            questionType = q.questionType?.lowercase()?.trim() ?: "mcq",
            audienceTags = q.audienceTags ?: "",
            year         = q.year ?: "",
            examName     = q.examName ?: ""
        )
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
    val isWeak    : Boolean = false
) {
    val progressPct: Int get() = if (totalQ > 0) (doneQ * 100) / totalQ else 0
}

// ── Quiz/Result ──
data class QuizResult(
    val total        : Int,
    val correct      : Int,
    val wrong        : Int,
    val skipped      : Int,
    val timeTakenSec : Int,
    val xpEarned     : Int,
    val subjectBreakdown: Map<String, SubjectScore> = emptyMap()
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
