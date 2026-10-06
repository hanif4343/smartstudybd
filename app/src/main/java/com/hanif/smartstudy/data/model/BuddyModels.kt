package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────
// Study Buddy Models
// Firebase paths:
//   /Buddies/{myPhoneKey}            -> BuddyLink (current buddy info, mirrored both সাইডে)
//   /BuddyRequests/{toPhoneKey}/{id} -> BuddyRequest (pending invite)
// ─────────────────────────────────────────────────────────

data class BuddyRequest(
    val id           : String = "",
    val fromPhone    : String = "",
    val fromName     : String = "",
    val toPhone      : String = "",
    val createdAt    : Long   = 0L,
    val status       : String = "PENDING" // PENDING | ACCEPTED | DECLINED
)

data class BuddyLink(
    val buddyPhone   : String = "",
    val buddyName    : String = "",
    val since        : Long   = 0L,
    val active       : Boolean = true
)

// আজকের progress — প্রতিদিন রাত ১২টায় রিসেট হিসাব করার জন্য date string ব্যবহার করা হয়
data class BuddyProgress(
    val phone        : String = "",
    val name         : String = "",
    val date         : String = "",   // yyyy-MM-dd
    val doneMinutes  : Int    = 0,
    val goalMinutes  : Int    = 20,
    val progressPct  : Int    = 0,
    val lastNudgeAt  : Long   = 0L,
    // ── Phase B2: share permission অনুযায়ী — -1 মানে "বন্ধু দেখতে পারবে না" (উৎসেই লুকানো) ──
    val streak       : Int    = -1,
    val routineDone  : Int    = -1,
    val routineTotal : Int    = -1,
    val quizAcc      : Int    = -1   // Quiz সঠিক-উত্তরের % (সব মিলিয়ে)
)

/** বন্ধু কী কী দেখতে পাবে — ইউজার নিজে ঠিক করে (ভুল প্রশ্ন, নোট, ব্যক্তিগত তথ্য কখনোই শেয়ার হয় না) */
data class BuddyShareSettings(
    val studyTime : Boolean = true,
    val progress  : Boolean = true,
    val streak    : Boolean = true,
    val routine   : Boolean = true,
    val quizScore : Boolean = true
)

// ── Study Nav Phase 6 (Study Together): বন্ধু এখন কোন Topic পড়ছে — /BuddyStudying/{key} ──
data class BuddyStudying(
    val phone   : String = "",
    val name    : String = "",
    val subject : String = "",
    val topic   : String = "",
    val at      : Long   = 0L
) {
    /** ২০ মিনিটের মধ্যে আপডেট হলেই "এখন পড়ছে" ধরা হয় (অ্যাপ হঠাৎ বন্ধ হলে পুরনো ডেটা ভুল না দেখায়) */
    fun isFresh(now: Long = System.currentTimeMillis()) =
        topic.isNotBlank() && at > 0L && now - at <= 20 * 60 * 1000L
}

// ── Study Buddy Phase B1: Knock (Study Action Knock) ──
// Firebase: /BuddyKnocks/{toKey}/{id}  — প্রাপক নিজের inbox-এ status/reply আপডেট করে, প্রেরক সেটা দেখে
object KnockKind {
    const val KNOCK = "KNOCK"   // 👋 সাধারণ knock
    const val FOCUS = "FOCUS"   // ⏱ একসাথে Focus করবি?
    const val ASK   = "ASK"     // ❓ নির্দিষ্ট প্রশ্নের কৌশল জানতে চাই
}

data class BuddyKnock(
    val id        : String = "",
    val fromPhone : String = "",
    val fromName  : String = "",
    val kind      : String = KnockKind.KNOCK,
    val message   : String = "",
    val at        : Long   = 0L,
    val status    : String = "PENDING",   // PENDING | ACCEPTED | LATER | REPLIED
    val reply     : String = "",
    val replyAt   : Long   = 0L
)

// ── Study Buddy Phase B3: Shared Goal (Team / Challenge mode) ──
// Firebase: /BuddyGoals/{pairKey}/goal  +  /BuddyGoals/{pairKey}/progress/{phoneKey}
object GoalMetric { const val QUESTIONS = "QUESTIONS"; const val MINUTES = "MINUTES" }
object GoalMode   { const val TEAM = "TEAM"; const val RACE = "RACE" }

data class BuddyGoal(
    val id         : String = "",
    val title      : String = "",
    val metric     : String = GoalMetric.QUESTIONS,
    val target     : Int    = 100,
    val mode       : String = GoalMode.TEAM,   // TEAM: দুজনের যোগফল ≥ target · RACE: প্রত্যেকে target ছুঁবে, কে আগে
    val days       : Int    = 7,
    val startMs    : Long   = 0L,
    val createdBy  : String = "",
    val createdByName: String = ""
) {
    fun endMs() = startMs + days * 24L * 3600_000L
    fun daysLeft(now: Long = System.currentTimeMillis()): Int =
        (((endMs() - now) + 24L * 3600_000L - 1) / (24L * 3600_000L)).toInt().coerceAtLeast(0)
    fun expired(now: Long = System.currentTimeMillis()) = now >= endMs()
    val unit get() = if (metric == GoalMetric.MINUTES) "মিনিট" else "প্রশ্ন"
}

data class BuddyGoalState(
    val goal          : BuddyGoal? = null,
    val myValue       : Int  = 0,
    val buddyValue    : Int  = 0,
    val myReachedAt   : Long = 0L,
    val buddyReachedAt: Long = 0L
)

// ── Study Buddy Phase B4: Study Together (shared Focus session) ──
// Firebase: /BuddyFocus/{pairKey} -> { startMs, minutes, startedBy, members/{phoneKey}: {name, joinedAt, leftAt} }
data class FocusMember(val phoneKey: String = "", val name: String = "", val joinedAt: Long = 0L, val leftAt: Long = 0L) {
    val inSession get() = joinedAt > 0L && leftAt == 0L
}

data class BuddyFocusSession(
    val startMs   : Long = 0L,
    val minutes   : Int  = 25,
    val startedBy : String = "",
    val members   : List<FocusMember> = emptyList()
) {
    fun endMs() = startMs + minutes * 60_000L
    fun active(now: Long = System.currentTimeMillis()) = startMs > 0L && now < endMs()
}

data class BuddyState(
    val focus           : BuddyFocusSession? = null,
    val goalState       : BuddyGoalState = BuddyGoalState(),
    val share           : BuddyShareSettings = BuddyShareSettings(),
    val knocks          : List<BuddyKnock> = emptyList(),   // আমার inbox — এখনো উত্তর দেইনি
    val sentKnocks      : List<BuddyKnock> = emptyList(),   // আমার পাঠানো — বন্ধু যেগুলোর উত্তর দিয়েছে
    val buddyStreak     : Int            = 0,
    val streakRisk      : String?        = null,            // "Rahim আজ এখনো study করেনি" ধরনের বার্তা

    val hasBuddy        : Boolean        = false,
    val buddy           : BuddyLink?     = null,
    val myProgress      : BuddyProgress  = BuddyProgress(),
    val buddyProgress   : BuddyProgress  = BuddyProgress(),
    val incomingRequest : BuddyRequest?  = null,
    val outgoingRequest : BuddyRequest?  = null,
    val buddyStudying   : BuddyStudying? = null,
    val searchResults   : List<User>     = emptyList(),
    val isLoading       : Boolean        = false,
    val toast           : String?        = null,
    val error           : String?        = null
)
