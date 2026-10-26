package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * QBank প্রশ্নের ধরন (MCQ / লিখিত) — Room-এ প্রশ্ন ডাউনলোড না থাকলেও কার্ডের সংখ্যা ঠিক দেখানোর জন্য।
 *
 * কেন: আগে পদবী/প্রতিষ্ঠান কার্ডের MCQ/লিখিত সংখ্যা শুধু Room-এ থাকা প্রশ্ন থেকে গোনা হতো। প্রশ্ন
 * ডাউনলোড না হলে লিখিত = ০ দেখাতো (সব MCQ ধরা হতো)। এখন GAS publish-এর exam-appearances.json-এ
 * প্রতিটা রো-তে `qtype` ("W"/"M") থাকে; সেটা এখানে আলাদা ছোট ফাইলে জমা হয় (Room schema ছোঁয়া হয়নি,
 * তাই migration লাগে না)।
 *
 * নিরাপত্তা:
 *  • JSON-এ qtype না থাকলে (GAS এখনো আপডেট হয়নি) [replaceAll] কিছুই বদলায় না — আগের আচরণই থাকে।
 *  • ফাইল পড়া/লেখায় যেকোনো সমস্যা হলে exception ছোঁড়ে না, খালি/পুরনো ম্যাপ দিয়ে চলে।
 *  • কী = Exam_Appearances.question_id (QBank প্রশ্নের fbKey-র সমান), মান: true = লিখিত, false = MCQ।
 */
object QBankTypeStore {
    private const val TAG  = "QBankTypeStore"
    private const val FILE = "qbank_question_types_v1.tsv"
    private val lock = Any()

    @Volatile private var cache: Map<String, Boolean>? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    /** সব (questionId → isWritten); ফাইল না থাকলে খালি ম্যাপ */
    fun all(ctx: Context): Map<String, Boolean> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val loaded = HashMap<String, Boolean>()
            try {
                val f = file(ctx)
                if (f.exists()) {
                    f.forEachLine { line ->
                        val tab = line.lastIndexOf('\t')
                        if (tab > 0 && tab < line.length - 1) {
                            when (line[tab + 1]) {
                                'W' -> loaded[line.substring(0, tab)] = true
                                'M' -> loaded[line.substring(0, tab)] = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "load failed: ${e.message}")
            }
            cache = loaded
            return loaded
        }
    }

    /**
     * নতুন sync-এর ফল দিয়ে পুরোটা বদলায়। [pairs] = (questionId, qtype) — qtype "W"/"M" ছাড়া বাকি বাদ।
     * বৈধ এন্ট্রি একটাও না থাকলে (পুরনো JSON) কিছু বদলায় না, false ফেরত দেয়।
     */
    fun replaceAll(ctx: Context, pairs: List<Pair<String, String?>>): Boolean {
        val fresh = HashMap<String, Boolean>()
        for ((id, t) in pairs) {
            if (id.isBlank()) continue
            when (t?.trim()?.uppercase()) {
                "W" -> fresh[id.trim()] = true
                "M" -> fresh[id.trim()] = false
            }
        }
        if (fresh.isEmpty()) return false
        synchronized(lock) {
            cache = fresh
            try {
                val tmp = File(ctx.applicationContext.filesDir, "$FILE.tmp")
                tmp.bufferedWriter().use { w ->
                    for ((k, v) in fresh) { w.write(k); w.write("\t"); w.write(if (v) "W" else "M"); w.write("\n") }
                }
                val f = file(ctx)
                val renamed = tmp.renameTo(f)
                if (!renamed) {
                    f.delete()
                    tmp.renameTo(f)
                }
            } catch (e: Exception) {
                Log.w(TAG, "save failed (memory cache still valid): ${e.message}")
            }
            Unit   // synchronized-এর শেষ এক্সপ্রেশন Unit — try/if কে এক্সপ্রেশন ধরা হবে না
        }
        return true
    }
}
