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
        // ── ⚠️ ২০২৬ সালের সেপ্টেম্বরে Anthropic/Google/Groq/Mistral/Cerebras-এর নিজস্ব
        // ডকুমেন্টেশন যাচাই করে বসানো ডিফল্ট। আগের ডিফল্টগুলো (llama-3.3-70b-versatile,
        // llama-3.3-70b, gemini-1.5-flash) সম্পূর্ণ বন্ধ/এন্টারপ্রাইজ-অনলি হয়ে গিয়েছিল —
        // এই কারণেই আগে সব মডেল ব্যর্থ হচ্ছিল। ভবিষ্যতে আবার বন্ধ হয়ে গেলে Settings-এর
        // "🔍 টেস্ট" বাটন দিয়ে যাচাই করে অন্য preset-এ সুইচ করুন। ──
        const val DEFAULT_GROQ_MODEL     = "openai/gpt-oss-20b"
        const val DEFAULT_MISTRAL_MODEL  = "mistral-small-latest"
        const val DEFAULT_CEREBRAS_MODEL = "llama3.1-8b"
        const val DEFAULT_GEMINI_MODEL   = "gemini-2.5-flash-lite"

        // ── Settings-এর ড্রপডাউনে দেখানোর জন্য প্রিসেট অপশন ──
        // কোনো প্রোভাইডার নতুন মডেল আনলে বা পুরনোটা বন্ধ করলে শুধু এই লিস্টে
        // যোগ/বাদ দিলেই হবে, UI/সেভ-লজিক আলাদাভাবে বদলাতে হবে না। প্রতিটা
        // লিস্টের প্রথমটাই ডিফল্ট হিসেবে ধরা হয়।
        val GROQ_MODEL_OPTIONS = listOf(
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
            "groq/compound-mini",
            "qwen/qwen3.8-27b"
        )
        val MISTRAL_MODEL_OPTIONS = listOf(
            "mistral-small-latest",
            "mistral-large-latest",
            "mistral-medium-latest",
            "open-mistral-nemo"
        )
        val CEREBRAS_MODEL_OPTIONS = listOf(
            "llama3.1-8b",
            "gpt-oss-120b",
            "qwen-3-32b"
        )
        val GEMINI_MODEL_OPTIONS = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash"
        )
    }
}

