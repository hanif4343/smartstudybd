package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Typing Race Models — Firebase path: /TypingRaces/{raceId}/
// ChallengeModels.kt-এর একই প্যাটার্নে, কিন্তু quiz-question-ভিত্তিক না —
// continuous WPM/progress-ভিত্তিক (দেখো SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md)
// ─────────────────────────────────────────────────────────

enum class RaceStatus {
    PENDING,    // invite পাঠানো হয়েছে, বন্ধু এখনো accept করেনি
    WAITING,    // accept হয়েছে, creator এখনো start করেনি
    ACTIVE,     // রেস চলছে
    FINISHED,   // দুজনেই শেষ করেছে (বা সময় শেষ)
    CANCELLED   // decline করা হয়েছে
}

data class RaceParticipant(
    val phone      : String  = "",
    val name       : String  = "",
    val status     : String  = "INVITED",  // INVITED | ACCEPTED | DECLINED | RACING | FINISHED
    val progress   : Int     = 0,          // কতটা ক্যারেক্টার টাইপ হয়েছে — live progress bar-এর জন্য
    val liveWpm    : Int     = 0,          // প্রতি ~২ সেকেন্ডে আপডেট হওয়া WPM (opponent দেখতে পাবে)
    val finishedAt : Long    = 0L,
    val finalWpm   : Int     = 0,
    val accuracy   : Int     = 100
)

data class TypingRace(
    val id           : String = "",
    val creatorPhone : String = "",
    val creatorName  : String = "",
    val passage      : String = "",
    val language     : String = "bn",
    val status       : String = RaceStatus.PENDING.name,
    val createdAt    : Long   = 0L,
    val startedAt    : Long   = 0L,
    val participants : Map<String, RaceParticipant> = emptyMap()
) {
    fun getStatus() = try { RaceStatus.valueOf(status) } catch (_: Exception) { RaceStatus.PENDING }

    fun myParticipant(myPhone: String) = participants[myPhone.firebaseKey()]
    fun otherParticipants(myPhone: String) = participants.filterKeys { it != myPhone.firebaseKey() }

    fun allAccepted() = participants.values.isNotEmpty() &&
        participants.values.all { it.status == "ACCEPTED" || it.status == "RACING" || it.status == "FINISHED" }

    fun allFinished() = participants.values.isNotEmpty() && participants.values.all { it.status == "FINISHED" }

    /** সবচেয়ে আগে শেষ করা participant — জয়ী */
    fun winner(): RaceParticipant? =
        participants.values.filter { it.status == "FINISHED" }.minByOrNull { it.finishedAt }
}

data class RaceInvite(
    val raceId      : String = "",
    val creatorName : String = "",
    val creatorPhone: String = "",
    val language    : String = "bn",
    val createdAt   : Long   = 0L
)
