package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Weekend Battle / Mega Championship Models
// প্রতি সপ্তাহে শুক্র-শনি: সবাই একই প্রশ্নে পরীক্ষা দেয়
// সর্বোচ্চ XP অর্জনকারী চ্যাম্পিয়ন
// ─────────────────────────────────────────────────────────

enum class BattleStatus { UPCOMING, ACTIVE, CLOSED }

data class WeekendBattle(
    val id            : String            = "",
    val title         : String            = "",     // "সাপ্তাহিক চ্যাম্পিয়নশিপ - সপ্তাহ ৩"
    val subject       : String            = "",
    val questionIds   : List<String>      = emptyList(),
    val questionCount : Int               = 20,
    val timeLimitSec  : Int               = 1200,   // 20 মিনিট
    val startsAt      : Long              = 0L,     // শুক্রবার ৮ PM
    val endsAt        : Long              = 0L,     // শনিবার ১১:৫৯ PM
    val status        : String            = BattleStatus.UPCOMING.name,
    val xpReward      : Int               = 500,    // চ্যাম্পিয়নের XP
    val participantCount: Int             = 0       // মোট অংশগ্রহণকারী (denormalized)
)

data class BattleEntry(
    val phone         : String            = "",
    val name          : String            = "",
    val score         : Int               = 0,
    val total         : Int               = 0,
    val accuracy      : Float             = 0f,    // score/total * 100
    val timeTakenSec  : Int               = 0,     // কত সেকেন্ডে শেষ করেছে
    val submittedAt   : Long              = 0L,
    val xpEarned      : Int               = 0      // এই battle এ যত XP পেয়েছে
)

// Leaderboard এ দেখানোর জন্য rank সহ
data class BattleRankEntry(
    val rank          : Int               = 0,
    val entry         : BattleEntry,
    val isMe          : Boolean           = false
)

// ViewModel UI state
data class WeekendBattleUiState(
    val activeBattle      : WeekendBattle?        = null,
    val upcomingBattle    : WeekendBattle?        = null,
    val leaderboard       : List<BattleRankEntry> = emptyList(),
    val myEntry           : BattleEntry?          = null,
    val hasSubmitted      : Boolean               = false,
    // Exam
    val questions         : List<QuestionItem>    = emptyList(),
    val answers           : Map<String, Int>      = emptyMap(),   // MutableMap থেকে Map — crash fix
    val currentQIndex     : Int                   = 0,
    val timerSec          : Int                   = 0,
    val isExamMode        : Boolean               = false,
    val isSubmitting      : Boolean               = false,
    // UI
    val isLoading         : Boolean               = false,
    val error             : String?               = null,
    val toast             : String?               = null
)
