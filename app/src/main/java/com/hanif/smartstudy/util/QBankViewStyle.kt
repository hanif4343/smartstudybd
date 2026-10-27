package com.hanif.smartstudy.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * QBank তালিকা দেখার স্টাইল: লিস্ট (এক কলাম) নাকি গ্রিড (দুই কলাম) — প্রতিটা ক্যাটাগরির
 * (বিসিএস / প্রাথমিক / নিবন্ধন / ব্যাংক / ১৬-২০ গ্রেড) জন্য আলাদা ও স্থায়ী (SharedPreferences)।
 * একটা ক্যাটাগরির পছন্দ অন্যটাকে কখনো প্রভাবিত করে না। ডিফল্ট = লিস্ট।
 * Compose state-map, তাই বদলালে UI আপনা-আপনি আপডেট হয়।
 */
object QBankViewStyle {
    private const val PREFS  = "qbank_view_style_v2"
    private const val PREFIX = "grid_"
    private var loaded = false
    private val map = mutableStateMapOf<String, Boolean>()

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            for ((k, v) in p.all) {
                if (k.startsWith(PREFIX) && v is Boolean) map[k.removePrefix(PREFIX)] = v
            }
        } catch (_: Exception) { /* ডিফল্ট লিস্ট */ }
    }

    /** এই ক্যাটাগরিতে গ্রিড চালু কিনা (ডিফল্ট false = লিস্ট) */
    fun isGrid(category: String): Boolean = map[category] ?: false

    fun set(ctx: Context, category: String, grid: Boolean) {
        map[category] = grid
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFIX + category, grid).apply()
        } catch (_: Exception) { /* মেমোরিতে থাকবে */ }
    }
}
