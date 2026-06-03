package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Challenge Models — Phase 1
// Firebase path: /Challenges/{challengeId}/
// ─────────────────────────────────────────────────────────

enum class ChallengeStatus {
    PENDING,    // invite পাঠানো হয়েছে
    WAITING,    // সবাই accept করেছে, শুরু হয়নি
    ACTIVE,     // পরীক্ষা চলছে
    FINISHED,   // সবাই submit করেছে
    CANCELLED   // কেউ decline করেছে
}

data class ChallengeParticipant(
    val phone       : String = "",
    val name        : String = "",
    val status      : String = "INVITED",  // INVITED | ACCEPTED | DECLINED | SUBMITTED
    val currentQ    : Int    = 0,          // কত নম্বর প্রশ্নে আছে (live tracking)
    val submittedAt : Long   = 0L,
    val score       : Int    = -1,         // -1 = not submitted yet (blind during exam)
    val correctIds  : List<String> = emptyList()
)

data class Challenge(
    val id           : String = "",
    val creatorPhone : String = "",
    val creatorName  : String = "",
    val subject      : String = "",        // "বাংলা ব্যাকরণ"
    val subTopic     : String = "",        // optional
    val questionCount: Int    = 10,
    val timeLimitSec : Int    = 600,       // 10 minutes default
    val status       : String = ChallengeStatus.PENDING.name,
    val createdAt    : Long   = 0L,
    val startedAt    : Long   = 0L,
    val questionIds  : List<String> = emptyList(),  // locked question ids
    val participants : Map<String, ChallengeParticipant> = emptyMap()
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

// Firebase key-safe phone — dots/# not allowed
fun String.firebaseKey() = replace(".", "_").replace("#", "_").replace("[", "_").replace("]", "_").replace("/", "_")
fun String.fromFirebaseKey() = replace("_", ".")  // simple reverse (may not be perfect for all phones)

data class ChallengeInvite(
    val challengeId  : String = "",
    val creatorName  : String = "",
    val creatorPhone : String = "",
    val subject      : String = "",
    val questionCount: Int    = 10,
    val timeLimitSec : Int    = 600,
    val createdAt    : Long   = 0L
)
