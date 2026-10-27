package com.hanif.smartstudy.util

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * QBank-এ পদবী পিন করে তালিকার উপরে রাখা — প্রতিটা ক্যাটাগরির পিন আলাদা ও স্থায়ী।
 * (একটা ক্যাটাগরির পিন অন্য ক্যাটাগরিতে দেখা যায় না, সাজানো/সার্চ যা-ই হোক পিন-করা কার্ড সবার আগে থাকে)
 * নতুন পিন করা কার্ড সবার উপরে বসে। Compose state-map, তাই বদলালে UI আপনা-আপনি আপডেট হয়।
 */
object QBankPinStore {
    private const val PREFS  = "qbank_pins_v1"
    private const val PREFIX = "pins_"
    private const val SEP    = '\u001F'
    private var loaded = false
    private val pins = mutableStateMapOf<String, List<String>>()   // ক্যাটাগরি → পিন-করা নাম (নতুন আগে)

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            for ((k, v) in p.all) {
                if (k.startsWith(PREFIX) && v is String && v.isNotEmpty())
                    pins[k.removePrefix(PREFIX)] = v.split(SEP).filter { it.isNotEmpty() }
            }
        } catch (_: Exception) { /* পিন ছাড়া চলবে */ }
    }

    fun isPinned(category: String, name: String): Boolean = pins[category]?.contains(name) == true

    fun toggle(ctx: Context, category: String, name: String) {
        val old = pins[category] ?: emptyList()
        val next = if (old.contains(name)) old - name else listOf(name) + old
        if (next.isEmpty()) pins.remove(category) else pins[category] = next
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREFIX + category, next.joinToString(SEP.toString())).apply()
        } catch (_: Exception) { /* মেমোরিতে থাকবে */ }
    }

    /** পিন-করা কার্ডগুলো (পিনের ক্রমে) সবার আগে, বাকিগুলো যেমন ছিল তেমন */
    fun <T> apply(category: String, items: List<T>, name: (T) -> String): List<T> {
        val order = pins[category]
        if (order.isNullOrEmpty()) return items
        val rank = HashMap<String, Int>()
        order.forEachIndexed { i, n -> rank[n] = i }
        val (pinned, rest) = items.partition { rank.containsKey(name(it)) }
        return pinned.sortedBy { rank[name(it)] ?: Int.MAX_VALUE } + rest
    }
}
