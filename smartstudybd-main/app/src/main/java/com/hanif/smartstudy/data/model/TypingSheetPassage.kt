package com.hanif.smartstudy.data.model

/**
 * Google Sheet-এর "Typing" ট্যাব থেকে (Firebase হয়ে) আসা raw প্যাসেজ।
 * শীটের কলাম এখন মাত্র ৪টা: id, language, content, updatedAt (আগে title/level
 * নামের দুটো কলামও ছিল, সেগুলো বাদ দেওয়া হয়েছে — দেখো code_updated_gs.txt)।
 * language: "bn" | "en" — TypingPracticeScreen/TypingRaceScreen ভাষা অনুযায়ী
 * এখান থেকেই passage pool বানায়। দেখো util/TypingPassageProvider.kt।
 */
data class TypingSheetPassage(
    val id        : String = "",
    val language  : String = "",
    val content   : String = "",
    val updatedAt : Long   = 0L
)
