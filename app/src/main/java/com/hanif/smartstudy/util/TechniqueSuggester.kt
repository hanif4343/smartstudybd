package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.QuestionEntity
import com.hanif.smartstudy.data.model.UserTechnique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** মিলের ধরন — একটা প্রশ্নে একাধিক ধরন একসাথে থাকতে পারে */
object TechniqueMatch {
    const val ALL  = "ALL"   // ✅ প্রশ্ন + সব অপশন + উত্তর, সব মিলছে
    const val Q    = "Q"     // ❓ সম্পূর্ণ প্রশ্ন হুবহু মিলছে
    const val OA   = "OA"    // 🔘 সব অপশন + উত্তর মিলছে
    const val TAG  = "TAG"   // 🏷 টেকনিকের ট্যাগ প্রশ্ন/অপশন/উত্তরে আছে
}

/** একটা সম্পর্কিত-প্রশ্নের প্রস্তাব — Admin নিজে যাচাই করে যোগ/বাদ দেবে */
data class TechniqueSuggestion(
    val questionId: String,
    val sheet     : String,      // "QUIZ" | "QBANK"
    val question  : String,
    val answer    : String,
    val subject   : String,
    val subTopic  : String,
    val reason    : String,      // কেন প্রস্তাব হলো (Admin-কে দেখানো হয়)
    val score     : Double,
    val kinds     : Set<String> = emptySet()   // TechniqueMatch.*
)

data class TechniqueSuggestResult(
    val items      : List<TechniqueSuggestion>,
    val totalFound : Int,
    val poolSize   : Int,
    val sourceFound: Boolean,
    val hasTags    : Boolean = false
)

/**
 * টেকনিকের সাথে সম্পর্কিত প্রশ্ন খোঁজে — কখনো নিজে লিংক করে না, শুধু প্রস্তাব দেয়।
 *
 * এখন মাত্র ৪ ধরনের স্পষ্ট মিল (আন্দাজ/শব্দ-গোনা/AI নেই — ব্যবহার করে দেখে পরে বদলানো হবে):
 *  ✅ ALL  — প্রশ্ন + সব অপশন + উত্তর, সব মিলছে
 *  ❓ Q    — সম্পূর্ণ প্রশ্ন হুবহু মিলছে (অপশন/উত্তর আলাদা হতে পারে)
 *  🔘 OA   — সব অপশন + উত্তর মিলছে (প্রশ্ন আলাদা হতে পারে)
 *  🏷 TAG  — টেকনিকে দেওয়া ট্যাগ-বাক্য অন্য প্রশ্নের লেখা/অপশন/উত্তরে আছে
 *
 * মিল দেখার সময় নম্বর-চিহ্ন ("৯১."), যতিচিহ্ন, HTML, বড়/ছোট হাতের অক্ষর, বাংলা/ইংরেজি অঙ্ক
 * আর অপশনের ক্রম উপেক্ষা করা হয়। সব subject-এর (Quiz + QBank) ডাউনলোড হওয়া প্রশ্নে খোঁজা হয়।
 */
object TechniqueSuggester {

    private const val TAG_LIMIT = 40          // শুধু-ট্যাগ-মিল সর্বোচ্চ কতটা দেখাবে (বাকি আরও আছে বলা হবে)
    private val URLS = Regex("https?://\\S+")
    private val TAGS = Regex("<[^>]+>")
    private val LEAD_NO = Regex("^\\s*[\\d০-৯]+\\s*[.)।:\\-]\\s*")
    private val NON_TEXT = Regex("[^\\p{L}\\p{N}\\p{M}]+")

    private fun clean(s: String) = s.replace(URLS, " ").replace(TAGS, " ")

    private fun digits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '০'..'৯') ('0' + (c - '০')) else c)
        return sb.toString()
    }

    /** তুলনার জন্য: স্পেস সরিয়ে একদম সংকুচিত রূপ */
    private fun norm(s: String): String =
        digits(clean(s).lowercase()).replace(NON_TEXT, "")

    private fun normQuestion(s: String): String =
        norm(clean(s).replace(LEAD_NO, ""))

    /** ট্যাগ খোঁজার জন্য: শব্দ-সীমানা রেখে (স্পেস দিয়ে আলাদা) রূপ */
    private fun spaced(s: String): String =
        " " + digits(clean(s).lowercase()).replace(NON_TEXT, " ").trim().replace(Regex("\\s+"), " ")

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

    private class Prepared(
        val e: QuestionEntity,
        val answerText: String,
        val q: String,
        val a: String,
        val opts: List<String>,       // সাজানো, ফাঁকা বাদ
        val spacedQ: String,
        val spacedA: String,
        val spacedO: String
    )

    private fun prepare(e: QuestionEntity): Prepared {
        val at = correctText(e)
        val opts = listOf(e.optionA, e.optionB, e.optionC, e.optionD).map { norm(it) }.filter { it.isNotEmpty() }.sorted()
        return Prepared(
            e = e, answerText = at, q = normQuestion(e.question), a = norm(at), opts = opts,
            spacedQ = spaced(e.question), spacedA = spaced(at),
            spacedO = spaced(listOf(e.optionA, e.optionB, e.optionC, e.optionD).joinToString(" "))
        )
    }

    suspend fun suggest(
        context : Context,
        source  : UserTechnique,
        linked  : Set<String>,       // ইতিমধ্যে সংযুক্ত টার্গেট প্রশ্ন
        rejected: Set<String>,       // আগে "বাদ" দেওয়া টার্গেট প্রশ্ন
        limit   : Int = 200
    ): TechniqueSuggestResult = withContext(Dispatchers.Default) {
        val dao = AppDatabase.getInstance(context).questionDao()
        val srcE = dao.getById("QUIZ", source.questionId) ?: dao.getById("QBANK", source.questionId)
            ?: return@withContext TechniqueSuggestResult(emptyList(), 0, 0, false)

        val src = prepare(srcE)
        val tags = source.tagList()
            .map { spaced(it).trim() }          // স্পেস-সংকুচিত রূপ
            .filter { it.length >= 2 }
            .distinct()
        val tagLabels = source.tagList().filter { spaced(it).trim().length >= 2 }

        val all = dao.getAll("QUIZ") + dao.getAll("QBANK")
        val pool = all.filter { !it.questionType.equals("study", true) }

        val out = ArrayList<TechniqueSuggestion>()
        for (e in pool) {
            if (e.fbKey == source.questionId) continue
            if (e.fbKey in linked || e.fbKey in rejected) continue
            val p = prepare(e)

            val qSame  = src.q.length >= 4 && p.q == src.q
            val oaSame = src.opts.size >= 2 && p.opts == src.opts && src.a.isNotEmpty() && p.a == src.a
            val kinds = LinkedHashSet<String>()
            val reasons = ArrayList<String>()
            var score = 0.0

            if (qSame && oaSame) {
                kinds += TechniqueMatch.ALL; kinds += TechniqueMatch.Q; kinds += TechniqueMatch.OA
                reasons += "✅ সব মিল — প্রশ্ন + অপশন + উত্তর"
                score += 400
            } else {
                if (qSame)  { kinds += TechniqueMatch.Q;  reasons += "❓ প্রশ্ন হুবহু মিল (অপশন/উত্তর আলাদা)"; score += 300 }
                if (oaSame) { kinds += TechniqueMatch.OA; reasons += "🔘 সব অপশন + উত্তর মিল (প্রশ্ন আলাদা)"; score += 200 }
            }

            // 🏷 ট্যাগ — শব্দের শুরু মিলতে হবে (বিভক্তি-যুক্ত রূপও ধরা পড়ে: "মৌলিক সংখ্যা" → "মৌলিক সংখ্যার")
            if (tags.isNotEmpty()) {
                val hit = ArrayList<String>()
                var tagScore = 0.0
                for ((i, t) in tags.withIndex()) {
                    val needle = " $t"
                    val inQ = p.spacedQ.contains(needle)
                    val inA = p.spacedA.contains(needle)
                    val inO = p.spacedO.contains(needle)
                    if (inQ || inA || inO) {
                        hit += tagLabels.getOrElse(i) { t }
                        tagScore += (if (inQ) 3 else 0) + (if (inA) 2 else 0) + (if (inO) 1 else 0)
                    }
                }
                if (hit.isNotEmpty()) {
                    kinds += TechniqueMatch.TAG
                    reasons += "🏷 ট্যাগ: " + hit.joinToString(", ")
                    score += 100 + tagScore
                }
            }

            if (kinds.isNotEmpty()) out.add(
                TechniqueSuggestion(
                    questionId = e.fbKey, sheet = e.sheet, question = e.question, answer = p.answerText,
                    subject = e.subject, subTopic = e.subTopic,
                    reason = reasons.joinToString(" · "), score = score, kinds = kinds
                )
            )
        }

        val sorted = out.sortedByDescending { it.score }
        // শুধু-ট্যাগ-মিল অনেক হলে সীমা — বাকিগুলো "আরও আছে" হিসেবে গোনা হয়
        val strong = sorted.filter { it.kinds.any { k -> k != TechniqueMatch.TAG } }
        val tagOnly = sorted.filter { s -> s.kinds.all { it == TechniqueMatch.TAG } }.take(TAG_LIMIT)
        val shown = (strong + tagOnly).sortedByDescending { it.score }.take(limit)
        TechniqueSuggestResult(shown, sorted.size, pool.size, true, hasTags = tags.isNotEmpty())
    }
}
