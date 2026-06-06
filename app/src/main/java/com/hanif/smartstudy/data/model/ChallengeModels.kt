package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Challenge Models
// Firebase path: /Challenges/{challengeId}/
// ─────────────────────────────────────────────────────────

enum class ChallengeStatus {
    PENDING,    // invite পাঠানো হয়েছে
    WAITING,    // সবাই accept করেছে, শুরু হয়নি
    ACTIVE,     // পরীক্ষা চলছে
    FINISHED,   // সবাই submit করেছে
    CANCELLED,  // কেউ decline করেছে
    GHOST_ACTIVE // Ghost Mode: creator submit করেছে, বন্ধু এখনো দেয়নি
}

data class ChallengeParticipant(
    val phone           : String       = "",
    val name            : String       = "",
    val status          : String       = "INVITED",  // INVITED | ACCEPTED | DECLINED | SUBMITTED
    val currentQ        : Int          = 0,
    val submittedAt     : Long         = 0L,
    val score           : Int          = -1,
    val correctIds      : List<String> = emptyList(),
    // Lifeline usage — প্রতি lifeline একবারই ব্যবহার করা যাবে
    val usedFiftyFifty  : Boolean      = false,
    val usedTimeFreeze  : Boolean      = false
)

// Lifeline types
enum class Lifeline { FIFTY_FIFTY, TIME_FREEZE }

data class Challenge(
    val id           : String = "",
    val creatorPhone : String = "",
    val creatorName  : String = "",
    val subject      : String = "",
    val subTopic     : String = "",
    val questionCount: Int    = 10,
    val timeLimitSec : Int    = 600,
    val status       : String = ChallengeStatus.PENDING.name,
    val createdAt    : Long   = 0L,
    val startedAt    : Long   = 0L,
    val questionIds  : List<String> = emptyList(),
    val participants : Map<String, ChallengeParticipant> = emptyMap(),
    // Ghost Mode
    val isGhostMode  : Boolean = false,
    val ghostLockedAt: Long    = 0L,
    // Coin/XP Wager — চ্যালেঞ্জে বাজি ধরা XP পরিমাণ
    val wagerXp      : Int     = 0   // 0 = no wager; জিতলে পাবে, হারলে হারাবে
) {
    fun getStatus() = try { ChallengeStatus.valueOf(status) } catch (_: Exception) { ChallengeStatus.PENDING }

    fun myParticipant(myPhone: String) =
        participants[myPhone.firebaseKey()]

    fun otherParticipants(myPhone: String) =
        participants.filterKeys { it != myPhone.firebaseKey() }

    fun allAccepted() =
        participants.values.all { it.status == "ACCEPTED" || it.status == "SUBMITTED" }

    fun allSubmitted() =
        participants.values.all { it.status == "SUBMITTED" }

    fun activeCount() =
        participants.values.count { it.status == "ACCEPTED" }

    fun submittedCount() =
        participants.values.count { it.status == "SUBMITTED" }
}

// Firebase key-safe phone
fun String.firebaseKey() = replace(".", "_").replace("#", "_").replace("[", "_").replace("]", "_").replace("/", "_")
fun String.fromFirebaseKey() = replace("_", ".")

data class ChallengeInvite(
    val challengeId  : String = "",
    val creatorName  : String = "",
    val creatorPhone : String = "",
    val subject      : String = "",
    val questionCount: Int    = 10,
    val timeLimitSec : Int    = 600,
    val createdAt    : Long   = 0L,
    val isGhostMode  : Boolean = false,
    val wagerXp      : Int    = 0    // Ghost invite হলে দেখাবে wager amount
)

// ─────────────────────────────────────────────────────────
// Match History — Firebase path: /MatchHistory/{myPhone}/{opponentPhone}/{matchId}
// ─────────────────────────────────────────────────────────

data class MatchRecord(
    val challengeId   : String  = "",
    val subject       : String  = "",
    val myScore       : Int     = 0,
    val opponentScore : Int     = 0,
    val opponentName  : String  = "",
    val opponentPhone : String  = "",
    val iWon          : Boolean = false,
    val total         : Int     = 0,
    val playedAt      : Long    = 0L,
    val isGhostMode   : Boolean = false,
    val wagerXp       : Int     = 0,   // বাজি ধরা XP পরিমাণ
    val xpChange      : Int     = 0    // +wagerXp (জিতলে) বা -wagerXp (হারলে)
)
