package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────────
// AI Chat (ডাউট সলভার) — Home স্ক্রিনের "AI Chat" কার্ড থেকে খোলে।
// এটা বিদ্যমান "Study Buddy" (মানুষ-বন্ধু) ফিচার থেকে সম্পূর্ণ আলাদা —
// এখানে শিক্ষার্থী সরাসরি AI-এর সাথে কথা বলে পড়াশোনার প্রশ্ন/ডাউট জিজ্ঞেস করে।
// Settings-এ সেভ করা একই ৪টা key (Groq/Mistral/Cerebras/Gemini) ব্যবহার হয়।
// ─────────────────────────────────────────────────────────────

data class AiChatMessage(
    val role    : String, // "user" | "assistant"
    val content : String
)

data class AiChatState(
    val messages  : List<AiChatMessage> = emptyList(),
    val isSending : Boolean              = false,
    val error     : String?              = null,
    // ইউজার Settings-এ অন্তত একটা AI key সেভ করেছে কিনা — না থাকলে চ্যাট শুরুর
    // আগেই সেই বার্তা দেখানো হয়, নেটওয়ার্ক কল করার দরকারই পড়ে না
    val hasAnyKey : Boolean               = true
)
