package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.QuestionEntity
import com.hanif.smartstudy.data.model.UserTechnique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** একটা সম্পর্কিত-প্রশ্নের প্রস্তাব — Admin নিজে যাচাই করে যোগ/বাদ দেবে */
data class TechniqueSuggestion(
    val questionId: String,
    val sheet     : String,      // "QUIZ" | "QBANK"
    val question  : String,
    val answer    : String,
    val subject   : String,
    val subTopic  : String,
    val reason    : String,      // কেন প্রস্তাব হলো (Admin-কে দেখানো হয়)
    val score     : Double
)

data class TechniqueSuggestResult(
    val items      : List<TechniqueSuggestion>,
    val totalFound : Int,
    val poolSize   : Int,
    val sourceFound: Boolean
)

/**
 * টেকনিকের সাথে সম্পর্কিত প্রশ্ন খোঁজে — কখনো নিজে লিংক করে না, শুধু প্রস্তাব দেয়।
 *
 * সংকেত (জোরালো → দুর্বল):
 *  ১) প্রায় একই প্রশ্ন  — একই উত্তর + প্রশ্নের লেখার মিল ≥ ৬০%
 *  ২) শব্দ মিলেছে        — প্রশ্ন/উত্তরের বিরল শব্দ টেকনিকের লেখাতেই আছে
 *  ৩) একই উত্তর         — শুধু একই উত্তর (সবচেয়ে দুর্বল, জেনেরিক উত্তর বাদ)
 *
 * শুধু ফোনে ডাউনলোড হওয়া প্রশ্ন (Room) থেকে খোঁজা হয়, আর শুধু একই subject-এর মধ্যে।
 */
object TechniqueSuggester {

    private val STOP = setOf(
        "কোন", "কোনটি", "কোনটা", "কোনগুলো", "কত", "কতটি", "কয়টি", "কয়", "কি", "কী", "কে", "কার", "কাকে", "কখন", "কেন", "কিভাবে",
        "এর", "ও", "এবং", "বা", "অথবা", "হলো", "হল", "হয়", "হয়েছিল", "হয়েছে", "হবে", "আছে", "ছিল", "নয়", "না", "নেই",
        "শব্দ", "শব্দটি", "ভাষা", "ভাষার", "নিচের", "নিম্নের", "উত্তর", "সঠিক", "ভুল", "মধ্যে", "থেকে", "জন্য", "দ্বারা", "একটি", "একটা",
        "এই", "সেই", "যে", "যা", "তার", "করে", "করা", "বলে", "বলা", "বলতে", "কোনো", "কোনও", "সব", "সকল", "প্রথম", "শেষ", "সালে", "সাল",
        "what", "which", "is", "are", "the", "an", "of", "in", "to", "and", "or", "for", "with", "by", "on", "at", "from", "as", "be",
        "was", "were", "it", "this", "that", "who", "how", "many", "much", "does", "do", "did", "following", "correct", "word"
    )
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}\\p{M}]+")
    private val TAGS = Regex("<[^>]+>")
    private val URLS = Regex("https?://\\S+")
    private val NUMERIC = Regex("^[\\d০-৯%.,টিজনটাখানা]+$")

    private fun clean(s: String) = s.replace(URLS, " ").replace(TAGS, " ")
    private fun tokens(s: String): Set<String> =
        clean(s).lowercase().split(TOKEN_SPLIT).filter { it.length >= 2 && it !in STOP }.toSet()
    private fun norm(s: String) = clean(s).lowercase().replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), "")

    /** entity.answer সরাসরি টেক্সট হলে সেটাই; অপশন-অক্ষর/নম্বর হলে সংশ্লিষ্ট অপশনের টেক্সট */
    private fun correctText(e: QuestionEntity): String {
        val a = e.answer.trim()
        return when (a.uppercase()) {
            "A", "১", "1", "ক" -> e.optionA
            "B", "২", "2", "খ" -> e.optionB
            "C", "৩", "3", "গ" -> e.optionC
            "D", "৪", "4", "ঘ" -> e.optionD
            else -> a
        }.ifBlank { a }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    suspend fun suggest(
        context : Context,
        source  : UserTechnique,
        linked  : Set<String>,       // ইতিমধ্যে সংযুক্ত টার্গেট প্রশ্ন
        rejected: Set<String>,       // আগে "বাদ" দেওয়া টার্গেট প্রশ্ন
        limit   : Int = 30
    ): TechniqueSuggestResult = withContext(Dispatchers.Default) {
        val dao = AppDatabase.getInstance(context).questionDao()
        val src = dao.getById("QUIZ", source.questionId) ?: dao.getById("QBANK", source.questionId)
            ?: return@withContext TechniqueSuggestResult(emptyList(), 0, 0, false)

        val all = dao.getAll("QUIZ") + dao.getAll("QBANK")
        val pool = all.filter { e ->
            !e.questionType.equals("written", true) && !e.questionType.equals("study", true) &&
                e.answer.isNotBlank() &&
                (if (src.subjectId.isNotBlank() && e.subjectId.isNotBlank()) e.subjectId == src.subjectId
                 else e.subject == src.subject)
        }

        val techT   = tokens(source.text).filter { it.length >= 3 }.toSet()
        val srcQT   = tokens(src.question)
        val srcAN   = norm(correctText(src))

        // শব্দ কতগুলো প্রশ্নে আছে — অনেক প্রশ্নে থাকা শব্দ "জেনেরিক", মিল ধরা হবে না
        val df = HashMap<String, Int>()
        val ansDf = HashMap<String, Int>()
        val cache = pool.map { e ->
            val aTxt = correctText(e)
            val qT = tokens(e.question); val aT = tokens(aTxt)
            (qT + aT).forEach { df[it] = (df[it] ?: 0) + 1 }
            val aN = norm(aTxt); ansDf[aN] = (ansDf[aN] ?: 0) + 1
            Triple(e, Triple(qT, aT, aN), aTxt)
        }
        val dfMax = maxOf(12.0, pool.size * 0.004)
        fun specific(t: String) = (df[t] ?: 0) <= dfMax

        val out = ArrayList<TechniqueSuggestion>()
        for ((e, parts, aTxt) in cache) {
            val (qT, aT, aN) = parts
            if (e.fbKey == source.questionId) continue
            if (e.fbKey in linked || e.fbKey in rejected) continue

            var score = 0.0; var reason = ""
            val sim = jaccard(qT, srcQT)
            if (aN.isNotEmpty() && aN == srcAN && sim >= 0.6) {
                score = 100 * sim; reason = "প্রায় একই প্রশ্ন (${(sim * 100).toInt()}% মিল)"
            } else {
                val hitA = aT.filter { it in techT && specific(it) }
                val hitQ = qT.filter { it in techT && specific(it) }
                if (hitA.isNotEmpty()) {
                    score = 60.0 + 10 * hitA.size; reason = "উত্তরের শব্দ টেকনিকে আছে: " + hitA.take(3).joinToString(", ")
                } else if (hitQ.isNotEmpty()) {
                    score = 40.0 + 5 * hitQ.size; reason = "প্রশ্নের শব্দ টেকনিকে আছে: " + hitQ.take(3).joinToString(", ")
                } else if (aN.isNotEmpty() && aN == srcAN && aN.length >= 4 &&
                    !NUMERIC.matches(aN) && (ansDf[aN] ?: 0) <= 12) {
                    score = 10.0 + sim * 10; reason = "একই উত্তর (প্রশ্ন আলাদা হতে পারে — ভালো করে দেখুন)"
                }
            }
            if (score > 0) out.add(
                TechniqueSuggestion(
                    questionId = e.fbKey, sheet = e.sheet, question = e.question, answer = aTxt,
                    subject = e.subject, subTopic = e.subTopic, reason = reason, score = score
                )
            )
        }
        // একই প্রশ্ন Quiz ও QBank দুই জায়গায় থাকলে — দুটোই আলাদা প্রস্তাব (দুটোতেই যোগ করা দরকার)
        val sorted = out.sortedByDescending { it.score }
        TechniqueSuggestResult(sorted.take(limit), sorted.size, pool.size, true)
    }
}
