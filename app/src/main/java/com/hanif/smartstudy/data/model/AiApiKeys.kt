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
 *
 * ── মডেল/ভ্যারিয়েন্ট (নতুন) ──
 * প্রতিটা প্রোভাইডারের জন্য কোন নির্দিষ্ট মডেল ব্যবহার হবে সেটাও এখন Settings থেকে
 * বদলানো যায় (key টেক্সটবক্সের পাশে ড্রপডাউন)। কারণ: প্রোভাইডাররা মাঝেমধ্যে কোনো
 * মডেল deprecate/বন্ধ করে দেয় (যেমন আগে যেটা কাজ করত সেটা হঠাৎ ৪০৪/৪০০ এরর দিতে
 * পারে) — তখন কোড এডিট না করেই ইউজার নিজে Settings থেকে অন্য মডেলে সুইচ করতে
 * পারবেন। ফিল্ড ফাঁকা/পুরনো (আগে সেভ করা) থাকলে ডিফল্ট মডেলই ব্যবহার হয়, তাই
 * পুরনো ইউজারদের কিছু ভাঙবে না।
 */
data class AiApiKeys(
    val groq        : String = "",
    val mistral     : String = "",
    val cerebras    : String = "",
    val gemini      : String = "",
    val groqModel   : String = DEFAULT_GROQ_MODEL,
    val mistralModel: String = DEFAULT_MISTRAL_MODEL,
    val cerebrasModel: String = DEFAULT_CEREBRAS_MODEL,
    val geminiModel : String = DEFAULT_GEMINI_MODEL
) {
    fun hasAnyKey(): Boolean =
        groq.isNotBlank() || mistral.isNotBlank() || cerebras.isNotBlank() || gemini.isNotBlank()

    companion object {
        const val DEFAULT_GROQ_MODEL     = "llama-3.3-70b-versatile"
        const val DEFAULT_MISTRAL_MODEL  = "mistral-small-latest"
        const val DEFAULT_CEREBRAS_MODEL = "llama-3.3-70b"
        const val DEFAULT_GEMINI_MODEL   = "gemini-1.5-flash"

        // ── Settings-এর ড্রপডাউনে দেখানোর জন্য প্রিসেট অপশন ──
        // কোনো প্রোভাইডার নতুন মডেল আনলে বা পুরনোটা বন্ধ করলে শুধু এই লিস্টে
        // যোগ/বাদ দিলেই হবে, UI/সেভ-লজিক আলাদাভাবে বদলাতে হবে না। প্রতিটা
        // লিস্টের প্রথমটাই ডিফল্ট হিসেবে ধরা হয়।
        val GROQ_MODEL_OPTIONS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "gemma2-9b-it",
            "mixtral-8x7b-32768"
        )
        val MISTRAL_MODEL_OPTIONS = listOf(
            "mistral-small-latest",
            "mistral-large-latest",
            "open-mistral-7b",
            "open-mixtral-8x7b"
        )
        val CEREBRAS_MODEL_OPTIONS = listOf(
            "llama-3.3-70b",
            "llama3.1-8b",
            "llama3.1-70b"
        )
        val GEMINI_MODEL_OPTIONS = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro",
            "gemini-2.0-flash"
        )
    }
}

