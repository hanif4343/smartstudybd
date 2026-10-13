package com.hanif.smartstudy.util

import android.content.Context

/**
 * 📍 "লাস্ট টেকনিক" — সর্বশেষ কোন প্রশ্নে টেকনিক/ব্যাখ্যা যোগ (বা এডিট) করা হয়েছিল তার তালিকা,
 * নতুনটা আগে। শুধু এই ফোনেই থাকে। প্রশ্নের তালিকায় (যেকোনো প্রশ্নপত্র/টপিক) এই তালিকার
 * সবচেয়ে নতুন যে id মিলবে, সেটাই ওই প্রশ্নপত্রের "লাস্ট টেকনিক" — তাই আলাদা করে
 * প্রশ্নপত্র-ভিত্তিক key রাখার দরকার পড়ে না।
 */
object LastTechniqueTracker {
    private const val PREFS = "last_technique_prefs"
    private const val KEY = "recent_question_ids"
    private const val MAX = 500

    fun recent(ctx: Context): List<String> = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun record(ctx: Context, questionId: String) {
        if (questionId.isBlank()) return
        try {
            val list = ArrayList<String>(MAX)
            list.add(questionId)
            recent(ctx).forEach { if (it != questionId && list.size < MAX) list.add(it) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, list.joinToString("\n")).apply()
        } catch (_: Exception) { }
    }
}
