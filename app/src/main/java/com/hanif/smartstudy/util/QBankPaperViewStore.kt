package com.hanif.smartstudy.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * লিখিত পেপার স্ক্রিনের দেখার ধরন: শিট-ভিউ (ছাপা সল্যুশন-শিটের মতো একটানা পেপার — ডিফল্ট) নাকি
 * কার্ড-ভিউ (আগের সাবজেক্ট-ট্যাব + কার্ড)। পছন্দটা স্থায়ী (SharedPreferences)।
 */
object QBankPaperViewStore {
    private const val PREFS = "qbank_paper_view"
    private const val KEY   = "sheet"
    private var loaded = false

    /** true = শিট-ভিউ (ডিফল্ট), false = কার্ড-ভিউ */
    var sheet by mutableStateOf(true)
        private set

    /** শিট-ভিউর জুম (লেখার মাপের গুণক): ০.৭ – ২.০, ডিফল্ট ১.০ */
    var zoom by mutableStateOf(1f)
        private set

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            sheet = p.getBoolean(KEY, true)
            zoom  = p.getFloat("zoom", 1f).coerceIn(0.7f, 2.0f)
        } catch (_: Exception) { /* ডিফল্ট শিট-ভিউ */ }
    }

    fun set(ctx: Context, value: Boolean) {
        sheet = value
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        } catch (_: Exception) { /* মেমোরিতে থাকবে */ }
    }

    fun setZoom(ctx: Context, value: Float) {
        zoom = value.coerceIn(0.7f, 2.0f)
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("zoom", zoom).apply()
        } catch (_: Exception) { /* মেমোরিতে থাকবে */ }
    }
}
