package com.hanif.smartstudy.data.model

// ── XP Level সিস্টেম ──
data class LevelInfo(
    val level  : Int    = 1,
    val name   : String = "নতুন শিক্ষার্থী",
    val emoji  : String = "🌱",
    val minXP  : Int    = 0
)

data class XpInfo(
    val xp           : Int       = 0,
    val currentLevel : LevelInfo = LevelInfo(),
    val nextLevel    : LevelInfo? = LevelInfo(2, "অনুসন্ধানী", "🔍", 100),
    val progressPct  : Int       = 0   // 0–100
) {
    companion object {
        private val LEVELS = listOf(
            LevelInfo(1,  "নতুন শিক্ষার্থী",  "🌱",  0),
            LevelInfo(2,  "অনুসন্ধানী",        "🔍",  100),
            LevelInfo(3,  "মনোযোগী",            "📖",  250),
            LevelInfo(4,  "পরিশ্রমী",           "💪",  500),
            LevelInfo(5,  "দক্ষ",               "⚡",  1000),
            LevelInfo(6,  "বিশেষজ্ঞ",           "🎯",  2000),
            LevelInfo(7,  "প্রতিভাবান",         "🌟",  4000),
            LevelInfo(8,  "চ্যাম্পিয়ন",        "🏆",  7000),
            LevelInfo(9,  "কিংবদন্তি",          "👑", 12000),
            LevelInfo(10, "মাস্টার",             "🔥", 20000)
        )

        fun fromXp(xp: Int): XpInfo {
            val cur  = LEVELS.lastOrNull { it.minXP <= xp } ?: LEVELS.first()
            val next = LEVELS.firstOrNull { it.minXP > xp }
            val pct  = if (next != null && next.minXP > cur.minXP)
                minOf(100, ((xp - cur.minXP) * 100) / (next.minXP - cur.minXP))
            else 100
            return XpInfo(xp, cur, next, pct)
        }
    }
}

// ── Weekly Streak (7 দিন) ──
data class StreakDay(
    val labelBn: String,   // শনি, রবি…
    val isToday : Boolean  = false,
    val isDone  : Boolean  = false   // streak এ আছে কিনা
)

data class StreakInfo(
    val streakDays : Int          = 0,
    val weekDays   : List<StreakDay> = emptyList()
)

// ── Daily Goal ──
data class GoalProgress(
    val goalMinutes   : Int = 20,    // ইউজার নির্ধারিত লক্ষ্য
    val doneMinutes   : Int = 0,     // আজ পড়েছে
    val progressPct   : Float = 0f   // 0–100
)

// ── Study Time stats ──
data class StudyStats(
    val todayMinutes : Int = 0,
    val weekMinutes  : Int = 0,
    val totalMinutes : Int = 0,
    val correctCount : Int = 0,
    val wrongCount   : Int = 0,
    val accuracyPct  : Int = 0
)

// ── Exam Countdown ──
data class ExamCountdown(
    val examName : String = "পরীক্ষা",
    val days     : Long   = 0,
    val hours    : Long   = 0,
    val minutes  : Long   = 0,
    val seconds  : Long   = 0,
    val isSet    : Boolean = false
)

// ── Motivational Quote ──
data class MotivationalQuote(
    val text   : String,
    val author : String
) {
    companion object {
        val QUOTES = listOf(
            MotivationalQuote("সাফল্য একদিনে আসে না, প্রতিদিনের অভ্যাসই তোমাকে গড়ে তোলে।", "স্মার্ট স্টাডি"),
            MotivationalQuote("কঠিন পথেই শক্তিশালী মানুষ তৈরি হয়।", "অজানা"),
            MotivationalQuote("আজকের পরিশ্রম কালকের সাফল্যের বীজ।", "স্মার্ট স্টাডি"),
            MotivationalQuote("স্বপ্ন দেখো, পরিশ্রম করো, সফল হও।", "অজানা"),
            MotivationalQuote("প্রতিটি প্রশ্নের উত্তর তোমাকে পরীক্ষার এক ধাপ কাছে নিয়ে যায়।", "স্মার্ট স্টাডি"),
            MotivationalQuote("ব্যর্থতা মানে শেষ নয়, এটি শেখার একটি সুযোগ।", "অজানা"),
            MotivationalQuote("মনোযোগ দিয়ে পড়ো, ফলাফল নিজেই আসবে।", "স্মার্ট স্টাডি"),
            MotivationalQuote("আজ যা পড়লে, কাল পরীক্ষায় কাজে লাগবে।", "অজানা"),
            MotivationalQuote("লক্ষ্য স্থির রাখো, পথ নিজেই তৈরি হয়।", "স্মার্ট স্টাডি"),
            MotivationalQuote("ধৈর্য ও পরিশ্রম — সাফল্যের দুই চাবিকাঠি।", "অজানা")
        )

        fun random(): MotivationalQuote = QUOTES.random()
        fun ofDay(): MotivationalQuote = QUOTES[java.util.Calendar.getInstance()
            .get(java.util.Calendar.DAY_OF_YEAR) % QUOTES.size]
    }
}
