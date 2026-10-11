package com.hanif.smartstudy.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * টেকনিকে আগে যে ট্যাগগুলো দিয়েছেন সেগুলো মনে রাখে — পরে টাইপ করতে গেলে নিচে প্রস্তাব হিসেবে দেখায়,
 * যাতে একই ধারণা বারবার আলাদা বানানে ("মৌলিক সংখ্যা" / "মৌলিক সংখ্যা ") না ভাঙে।
 * আর ট্যাগ-বক্স খোলা/বন্ধ থাকার পছন্দও এখানে (SharedPreferences) থাকে।
 */
object TechniqueTagStore {
    private const val PREFS = "technique_ui"
    private const val KEY_RECENT = "recent_tags"
    private const val KEY_OPEN = "tag_box_open"
    private const val MAX = 60
    private val gson = Gson()
    private val type = object : TypeToken<List<String>>() {}.type

    fun clean(raw: String): String =
        raw.trim().trimStart('#').replace(Regex("\\s+"), " ").take(40)

    fun recent(context: Context): List<String> = try {
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, null)
        if (json.isNullOrBlank()) emptyList() else (gson.fromJson<List<String>>(json, type) ?: emptyList())
    } catch (_: Exception) { emptyList() }

    /** সেভ করার সময় ডাকুন — সর্বশেষ ব্যবহার করা ট্যাগ সবার আগে */
    fun remember(context: Context, tags: List<String>) {
        if (tags.isEmpty()) return
        val merged = (tags.map { clean(it) }.filter { it.isNotEmpty() } + recent(context))
            .distinctBy { it.lowercase() }.take(MAX)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECENT, gson.toJson(merged)).apply()
    }

    /** ডিফল্ট: খোলা */
    fun isOpen(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OPEN, true)

    fun setOpen(context: Context, open: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OPEN, open).apply()
    }
}
