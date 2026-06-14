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
    val lastNudgeAt  : Long   = 0L
)

data class BuddyState(
    val hasBuddy        : Boolean        = false,
    val buddy           : BuddyLink?     = null,
    val myProgress      : BuddyProgress  = BuddyProgress(),
    val buddyProgress   : BuddyProgress  = BuddyProgress(),
    val incomingRequest : BuddyRequest?  = null,
    val outgoingRequest : BuddyRequest?  = null,
    val searchResults   : List<User>     = emptyList(),
    val isLoading       : Boolean        = false,
    val toast           : String?        = null,
    val error           : String?        = null
)
