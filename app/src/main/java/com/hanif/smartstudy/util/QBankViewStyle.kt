package com.hanif.smartstudy.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank তালিকা দেখার স্টাইল: লিস্ট (এক কলাম) নাকি গ্রিড (দুই কলাম)। পছন্দটা স্থায়ী (SharedPreferences)।
 * পদবী/প্রতিষ্ঠান তালিকা এবং প্রশ্নপত্রের তালিকা — দুই জায়গাতেই একই পছন্দ চলে।
 * QBankTypeScope-এর মতোই Compose state, তাই বদলালে UI আপনা-আপনি আপডেট হয়।
 */
object QBankViewStyle {
    private const val PREFS = "qbank_view_style"
    private const val KEY   = "grid"
    private var loaded = false

    /** true = গ্রিড (২ কলাম), false = লিস্ট (ডিফল্ট) */
    var grid by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            grid = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        } catch (_: Exception) { /* ডিফল্ট লিস্ট */ }
    }

    fun set(ctx: Context, value: Boolean) {
        grid = value
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        } catch (_: Exception) { /* মেমোরিতে থাকবে */ }
    }
}
