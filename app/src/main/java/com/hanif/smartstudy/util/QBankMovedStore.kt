package com.hanif.smartstudy.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank "আনমুভ টপিক ফিল্টার"-এর ডেটা-সোর্স (শুধু QBank, শুধু Admin-এর জন্য)।
 *
 * QBank-এ "মুভ" মানে প্রশ্ন Quiz-এ কপি করা — QBank-এর প্রশ্ন কখনো সরে না, মুছে না, লুকায়ও না
 * (দেখো MenuViewModel.adminCopyQBankToQuiz)। তাই "এই টপিক থেকে কিছু Quiz-এ পাঠানো হয়েছে কিনা"
 * আলাদা করে এখানে মনে রাখা হয় — QBank টপিকের topicId ধরে (SharedPreferences, ডিভাইস-লোকাল)।
 *
 * • টপিকের যেকোনো প্রশ্ন (একটা হোক বা সব) Quiz-এ পাঠালেই টপিকটা "মুভ হয়েছে" মার্ক হয়।
 * • [version] Compose state — মার্ক বদলালেই যে স্ক্রিন [movedIds] পড়ছে সেটা সাথে সাথে রিকম্পোজ হয়।
 */
object QBankMovedStore {
    private const val PREF = "qbank_moved_topics"
    private var prefs: SharedPreferences? = null

    /** প্রতিবার মার্ক বদলালে বাড়ে — Compose-কে রিকম্পোজ করানোর জন্য */
    var version by mutableStateOf(0)
        private set

    private fun p(ctx: Context): SharedPreferences {
        val cur = prefs
        if (cur != null) return cur
        val created = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = created
        return created
    }

    /** এখন পর্যন্ত "মুভ হয়েছে" এমন সব QBank topicId (Compose-এ পড়লে নিজে থেকেই রিকম্পোজ হয়) */
    fun movedIds(ctx: Context): Set<String> {
        @Suppress("UNUSED_VARIABLE") val v = version   // state পড়া — subscription এর জন্য
        return p(ctx).all.keys.toSet()
    }

    fun isMoved(ctx: Context, topicId: String): Boolean = topicId.isNotBlank() && p(ctx).contains(topicId)

    fun markMoved(ctx: Context, topicIds: Collection<String>) {
        val ids = topicIds.filter { it.isNotBlank() && !it.startsWith("-local") }
        if (ids.isEmpty()) return
        val ed = p(ctx).edit()
        val now = System.currentTimeMillis()
        ids.forEach { ed.putLong(it, now) }
        ed.apply()
        version++
    }

    fun unmark(ctx: Context, topicId: String) {
        if (topicId.isBlank()) return
        p(ctx).edit().remove(topicId).apply()
        version++
    }
}
