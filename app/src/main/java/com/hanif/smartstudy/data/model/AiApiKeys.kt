package com.hanif.smartstudy.data.model

/**
 * ── Written উত্তর AI-অটো-চেক এর জন্য ইউজারের নিজের ৪টা API key ──
 * Settings থেকে একবার সেভ করলে DataStore-এ থেকে যায় (SessionManager দিয়ে),
 * পরের বার আবার বসাতে হয় না।
 *
 * চেষ্টার ক্রম (fallback order): Groq → Mistral → Cerebras → Gemini।
 * Gemini সবার শেষে রাখা হয়েছে, কারণ এটা প্রায়ই ফেইল করে (free-tier rate limit)।
 * একটা key ফাঁকা থাকলে সেই প্রোভাইডার স্কিপ হয়ে পরেরটা চেষ্টা হয়। সব ফেইল করলে
 * বা কোনো key-ই সেভ করা না থাকলে অটো-চেক null রিটার্ন করে — তখন UI সাথে সাথেই
 * আগের ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক করে।
 */
data class AiApiKeys(
    val groq    : String = "",
    val mistral : String = "",
    val cerebras: String = "",
    val gemini  : String = ""
) {
    fun hasAnyKey(): Boolean =
        groq.isNotBlank() || mistral.isNotBlank() || cerebras.isNotBlank() || gemini.isNotBlank()
}
