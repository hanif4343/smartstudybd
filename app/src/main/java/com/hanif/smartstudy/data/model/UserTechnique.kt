package com.hanif.smartstudy.data.model

// ── একজন ইউজারের দেওয়া টেকনিক ──
data class UserTechnique(
    val id          : String = "",   // Firebase push key
    val questionId  : String = "",
    val userId      : String = "",   // phone number
    val userName    : String = "",
    val text        : String = "",
    val isPublic    : Boolean = false,
    val status      : String = "pending", // "pending" | "approved" | "rejected"
    val timestamp   : Long   = 0L,
    val type        : String = "technique" // "technique" | "explanation" — ইউজার কোনটা যোগ করেছে
) {
    fun isPending()      = status == "pending"
    fun isApproved()     = status == "approved"
    fun isRejected()     = status == "rejected"
    fun isExplanation()  = type == "explanation"
    fun isTechnique()    = type != "explanation"

    companion object {
        fun fromMap(id: String, map: Map<String, Any>): UserTechnique = UserTechnique(
            id         = id,
            questionId = map["questionId"]?.toString() ?: "",
            userId     = map["userId"]?.toString()     ?: "",
            userName   = map["userName"]?.toString()   ?: "ব্যবহারকারী",
            text       = map["text"]?.toString()       ?: "",
            isPublic   = map["isPublic"]?.toString()?.toBooleanStrictOrNull() ?: false,
            status     = map["status"]?.toString()     ?: "pending",
            timestamp  = map["timestamp"]?.toString()?.toLongOrNull() ?: 0L,
            type       = map["type"]?.toString()       ?: "technique"
        )
    }
}
