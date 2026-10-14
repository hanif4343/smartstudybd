package com.hanif.smartstudy.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank → Quiz "পাঠান / মুভ" করা গন্তব্য Subject ও Topic-এর স্থায়ী মার্ক (ডিভাইস-লোকাল, SharedPreferences)।
 *
 * QBank থেকে যেকোনো Subject › Topic-এ প্রশ্ন পাঠালেই সেই Subject ও Topic সাথে সাথে মার্ক হয়ে যায় এবং
 * "Quiz-এ পাঠান" ডায়ালগে (প্রশ্ন-লিস্ট + QBank টপিক ম্যানেজারের 🔀 মার্জ টু — দুই জায়গাতেই) সবসময়
 * ✅ চিহ্নসহ দেখায় — অ্যাপ বন্ধ করে খুললেও মুছে যায় না। Rename/Delete করলে মার্কও সাথে সাথে মিলিয়ে যায়।
 *
 * [version] Compose state — মার্ক বদলালেই যে UI এটা পড়ছে সেটা সাথে সাথে রিকম্পোজ হয়।
 */
object QuizMovedMarkStore {
    private const val PREF = "quiz_moved_marks"
    private const val SEP = "\u0001"
    private var prefs: SharedPreferences? = null

    var version by mutableStateOf(0)
        private set

    private fun p(ctx: Context): SharedPreferences {
        prefs?.let { return it }
        val created = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = created
        return created
    }

    private fun sKey(subject: String) = "S$SEP${subject.trim()}"
    private fun tKey(subject: String, topic: String) = "T$SEP${subject.trim()}$SEP${topic.trim()}"

    @Suppress("UNUSED_VARIABLE")
    fun isSubjectMarked(ctx: Context, subject: String): Boolean {
        val v = version   // state পড়া — subscription
        return p(ctx).contains(sKey(subject))
    }

    @Suppress("UNUSED_VARIABLE")
    fun isTopicMarked(ctx: Context, subject: String, topic: String): Boolean {
        val v = version
        return p(ctx).contains(tKey(subject, topic))
    }

    /** গন্তব্য Subject ও Topic দুটোই মার্ক করে (সাথে সাথে) */
    fun mark(ctx: Context, subject: String, topic: String) {
        if (subject.isBlank() || topic.isBlank()) return
        val now = System.currentTimeMillis()
        p(ctx).edit().putLong(sKey(subject), now).putLong(tKey(subject, topic), now).apply()
        version++
    }

    fun renameSubject(ctx: Context, old: String, new: String) {
        val prefix = "T$SEP${old.trim()}$SEP"
        val all = p(ctx).all
        val ed = p(ctx).edit()
        var changed = false
        if (all.containsKey(sKey(old))) {
            ed.remove(sKey(old)).putLong(sKey(new), System.currentTimeMillis()); changed = true
        }
        all.keys.filter { it.startsWith(prefix) }.forEach { k ->
            ed.remove(k).putLong("T$SEP${new.trim()}$SEP${k.removePrefix(prefix)}", System.currentTimeMillis())
            changed = true
        }
        if (changed) { ed.apply(); version++ }
    }

    fun renameTopic(ctx: Context, subject: String, old: String, new: String) {
        if (!p(ctx).contains(tKey(subject, old))) return
        p(ctx).edit().remove(tKey(subject, old)).putLong(tKey(subject, new), System.currentTimeMillis()).apply()
        version++
    }

    fun deleteSubject(ctx: Context, subject: String) {
        val prefix = "T$SEP${subject.trim()}$SEP"
        val ed = p(ctx).edit().remove(sKey(subject))
        p(ctx).all.keys.filter { it.startsWith(prefix) }.forEach { ed.remove(it) }
        ed.apply(); version++
    }

    fun deleteTopic(ctx: Context, subject: String, topic: String) {
        p(ctx).edit().remove(tKey(subject, topic)).apply()
        version++
    }
}
