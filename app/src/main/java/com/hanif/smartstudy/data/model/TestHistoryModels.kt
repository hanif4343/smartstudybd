package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Test History — "যেকোনো সময় টেস্ট দাও" ফিচারের রেজাল্ট হিস্ট্রি
// DataStore-এ JSON list হিসেবে সংরক্ষিত, Profile মেনুতে দেখানো হয়
// ─────────────────────────────────────────────────────────

data class TestHistoryEntry(
    val id            : String                    = "",
    val timestamp     : Long                      = 0L,
    val mode          : String                    = "QUIZ",   // QUIZ | QBANK | STUDY
    val topics        : List<String>              = emptyList(), // "Subject" বা "Subject - SubTopic"
    val total         : Int                       = 0,
    val correct       : Int                       = 0,
    val wrong         : Int                       = 0,
    val skipped       : Int                       = 0,
    val timeTakenSec  : Int                       = 0,
    val xpEarned      : Int                       = 0,
    val subjectBreakdown: Map<String, SubjectScore> = emptyMap(),
    // "" = সাধারণ Quiz/QBank/Study টেস্ট, "model_test" = এডমিন-কিউরেটেড Model Test
    val source        : String                    = "",
    // Model Test-এর written প্রশ্নে অটো-চেক হয় না — কতগুলো উত্তর গ্রেড না করেই "সংরক্ষিত" হয়েছে
    val recorded      : Int                       = 0
) {
    val pct: Int get() = if (total > 0) (correct * 100) / total else 0

    val isModelTest: Boolean get() = source == "model_test"
    // written model test-এ correct/wrong/pct অর্থহীন — শুধু "কতগুলো জমা হয়েছে" দেখানো হবে
    val isUngraded: Boolean get() = isModelTest && recorded > 0

    val modeLabel: String get() = when (mode) {
        "QBANK" -> "প্রশ্নভান্ডার"
        "STUDY" -> "পড়া"
        else    -> "কুইজ"
    }

    val gradeEmoji: String get() = when {
        isUngraded -> "✍️"
        pct >= 80   -> "🏆"
        pct >= 60   -> "👏"
        pct >= 40   -> "💪"
        else        -> "📚"
    }
}

fun QuizResult.toHistoryEntry(mode: String, topics: List<String>, source: String = ""): TestHistoryEntry = TestHistoryEntry(
    id              = "test_${System.currentTimeMillis()}",
    timestamp       = System.currentTimeMillis(),
    mode            = mode,
    topics          = topics,
    total           = total,
    correct         = correct,
    wrong           = wrong,
    skipped         = skipped,
    timeTakenSec    = timeTakenSec,
    xpEarned        = xpEarned,
    subjectBreakdown = subjectBreakdown,
    source          = source,
    recorded        = recorded
)
