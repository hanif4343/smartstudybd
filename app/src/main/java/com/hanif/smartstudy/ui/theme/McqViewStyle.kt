package com.hanif.smartstudy.ui.theme

import androidx.compose.runtime.compositionLocalOf

// ── MCQ ভিউ স্টাইল ─────────────────────────────────────────────
// নতুন ডিজাইন যোগ করতে: (১) এখানে একটা entry যোগ করুন,
// (২) ui/shared/SharedComponents.kt-এর McqOptions-এর `when`-এ একটা renderer বসান।
// Settings-এর সিলেক্টর entries থেকে নিজে নিজেই নতুন অপশন দেখাবে।
// id একবার দিলে আর বদলাবেন না — এটাই ফোনে সেভ থাকে।
enum class McqViewStyle(val id: String, val label: String, val description: String) {
    CARD_MODERN("card", "ডিজাইন ২ — কার্ড/মডার্ন", "প্রতিটা অপশন আলাদা কার্ডে, বড় ও পরিষ্কার"),
    COMPACT_LIST("compact", "ডিজাইন ১ — কমপ্যাক্ট/লিস্ট", "ছোট লাইন-লিস্ট, এক স্ক্রিনে বেশি অপশন");

    companion object {
        val DEFAULT = CARD_MODERN   // বর্তমান লুক — আগের ইউজাররা কোনো পরিবর্তন দেখবে না
        fun fromId(id: String?): McqViewStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

val LocalMcqViewStyle = compositionLocalOf { McqViewStyle.DEFAULT }
