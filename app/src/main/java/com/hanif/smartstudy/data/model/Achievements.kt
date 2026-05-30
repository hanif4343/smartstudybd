package com.hanif.smartstudy.data.model

data class Achievement(
    val id          : String,
    val emoji       : String,
    val title       : String,
    val description : String,
    val xpReward    : Int = 50
)

object Achievements {
    val ALL = listOf(
        Achievement("first_quiz",     "🎯", "প্রথম Quiz",       "প্রথমবার Quiz দিয়েছ!",           25),
        Achievement("streak_3",       "🔥", "৩ দিনের Streak",   "টানা ৩ দিন পড়েছ!",               50),
        Achievement("streak_7",       "⚡", "এক সপ্তাহ Streak", "টানা ৭ দিন পড়েছ!",              100),
        Achievement("streak_30",      "👑", "মাস Streak",        "টানা ৩০ দিন পড়েছ!",             500),
        Achievement("xp_100",         "⭐", "১০০ XP",            "মোট ১০০ XP অর্জন করেছ!",          50),
        Achievement("xp_500",         "🌟", "৫০০ XP",            "মোট ৫০০ XP অর্জন করেছ!",         100),
        Achievement("xp_1000",        "💫", "১০০০ XP",           "মোট ১০০০ XP অর্জন করেছ!",        200),
        Achievement("perfect_score",  "💯", "Perfect Score",     "একটি Quiz-এ সব সঠিক!",           100),
        Achievement("bookmarked_10",  "📌", "১০টি Bookmark",     "১০টি প্রশ্ন সংরক্ষণ করেছ!",       25),
        Achievement("typing_40wpm",   "⌨️", "Typing Pro",        "৪০+ WPM টাইপিং স্পিড!",           75),
        Achievement("search_used",    "🔍", "Searcher",          "Global Search ব্যবহার করেছ!",     10),
        Achievement("ai_used",        "🤖", "AI Explorer",       "Gemini AI ব্যবহার করেছ!",         25),
    )

    fun findById(id: String) = ALL.find { it.id == id }
}
