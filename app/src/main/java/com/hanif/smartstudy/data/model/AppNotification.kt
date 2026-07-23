package com.hanif.smartstudy.data.model

/**
 * ── 🔔 Notification inbox — Firebase RTDB "Notifications/{phone}/{key}" node থেকে
 * পড়া হয় (এই একই node NotificationPollWorker.kt আগে থেকেই পোল করে system-tray
 * notification দেখায়); এখানে সেই একই ডেটা অ্যাপের ভেতরে একটা লিস্ট হিসেবেও দেখানো
 * হয়, যাতে সিস্টেম নোটিফিকেশন মিস করলেও 🔔 আইকনে চেপে পরে দেখা যায়। ──
 */
data class AppNotification(
    val key         : String,
    val title       : String,
    val body        : String,
    val time        : Long,
    val read        : Boolean,
    val type        : String = "",
    val url         : String = "",
    val questionId  : String = "",
    val tab         : String = "",
    val challengeId : String = ""
)
