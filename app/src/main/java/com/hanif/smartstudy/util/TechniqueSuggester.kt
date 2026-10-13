package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.QuestionEntity
import com.hanif.smartstudy.data.model.AiApiKeys
import org.json.JSONArray
import org.json.JSONObject
import com.hanif.smartstudy.data.model.UserTechnique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** মিলের ধরন — একটা প্রশ্নে একাধিক ধরন একসাথে থাকতে পারে */
object TechniqueMatch {
    const val ALL  = "ALL"   // ✅ প্রশ্ন + সব অপশন + উত্তর, সব মিলছে
    const val Q    = "Q"     // ❓ সম্পূর্ণ প্রশ্ন হুবহু মিলছে
    const val OA   = "OA"    // 🔘 সব অপশন + উত্তর মিলছে
    const val TAG  = "TAG"   // 🏷 টেকনিকের ট্যাগ শুধু প্রশ্ন/উত্তরে আছে (অপশনে নয়)
    const val AI   = "AI"    // ✨ AI বলছে সরাসরি কাজে লাগবে (শুধু Admin বাটন চাপলে)
    const val AIR  = "AIR"   // 🔶 AI বলছে সম্পর্কিত, তবে আংশিক কাজে লাগবে
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

/** ✨ AI অনুসন্ধানের ফলাফল */
data class TechniqueAiOutcome(
    val items     : List<TechniqueSuggestion>,
    val message   : String,
    val ok        : Boolean = true,
    val candidates: Int = 0
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
 *  🏷 TAG  — টেকনিকে দেওয়া ট্যাগ-বাক্য অন্য প্রশ্নের লেখা বা সঠিক উত্তরে আছে
 *            (অপশনে থাকলে ধরা হয় না — ভুল অপশনের শব্দ মিলে ভুল সাজেশন আসত)
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
                    // অপশন (spacedO) ইচ্ছা করেই দেখা হয় না — শুধু প্রশ্ন + সঠিক উত্তর
                    if (inQ || inA) {
                        hit += tagLabels.getOrElse(i) { t }
                        tagScore += (if (inQ) 3 else 0) + (if (inA) 2 else 0)
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

    // ═══════════════════════════════════════════════════════════════════
    //  ✨ AI অনুসন্ধান — শুধু Admin বাটন চাপলে চলে, নিজে থেকে কখনো না।
    //  ১) AI মূল প্রশ্ন+উত্তর+টেকনিক+ট্যাগ পড়ে বোঝে টেকনিকটা কোন ধারণা শেখায়
    //     (টেকনিকের লেখায় বিষয়ের শব্দ না থাকলেও — যেমন ৪৪ ২২ ৩২২ ৩২১ → "মৌলিক সংখ্যা")
    //  ২) সেই ধারণার বাক্য দিয়ে সব প্রশ্ন থেকে ফ্রি-তে প্রার্থী বাছাই (সর্বোচ্চ ৪০)
    //  ৩) প্রার্থীদের AI ২০টা করে বিচার করে: direct (✨) / related (🔶) / no (বাদ)
    //  মোট AI call: ১ + ⌈প্রার্থী/২০⌉ (সর্বোচ্চ ৩)। AI সব সময় কারণসহ উত্তর দেয়, সন্দেহ হলে "no"।
    // ═══════════════════════════════════════════════════════════════════
    private const val AI_CANDIDATES = 40
    private const val AI_BATCH = 20

    private fun short(s: String, n: Int): String {
        val t = clean(s).replace(Regex("\\s+"), " ").trim()
        return if (t.length > n) t.take(n) + "…" else t
    }

    suspend fun aiSuggest(
        context : Context,
        source  : UserTechnique,
        skipIds : Set<String>,       // রুল-ভিত্তিক তালিকায় আগে থেকেই আছে
        linked  : Set<String>,
        rejected: Set<String>,
        keys    : AiApiKeys
    ): TechniqueAiOutcome = withContext(Dispatchers.Default) {
        if (!keys.hasAnyKey())
            return@withContext TechniqueAiOutcome(emptyList(), "❌ আগে Settings-এ অন্তত একটা AI API key দিন", ok = false)
        val dao = AppDatabase.getInstance(context).questionDao()
        val srcE = dao.getById("QUIZ", source.questionId) ?: dao.getById("QBANK", source.questionId)
            ?: return@withContext TechniqueAiOutcome(emptyList(), "❌ মূল প্রশ্নটা এই ফোনে ডাউনলোড করা নেই", ok = false)

        val srcOpts = listOf(srcE.optionA, srcE.optionB, srcE.optionC, srcE.optionD).filter { it.isNotBlank() }.joinToString(" | ")
        val srcBlock = "SOURCE QUESTION: ${short(srcE.question, 300)}\n" +
            "OPTIONS: ${short(srcOpts, 300).ifBlank { "(none)" }}\n" +
            "CORRECT ANSWER: ${short(correctText(srcE), 120)}\n" +
            "TECHNIQUE (student's memory trick): ${short(source.text, 700)}\n" +
            "ADMIN TAGS: ${source.tagList().joinToString(", ").ifBlank { "(none)" }}"

        // ── ধাপ ১: ধারণা বোঝা ──
        val p1 = """
You help a Bangladeshi exam-prep app link one memory technique to OTHER questions it genuinely helps with.
$srcBlock

Work out which concept or fact set the technique teaches. The technique text may not name the topic - use the source question to infer it.
Return ONLY this JSON, nothing else:
{"concept":"<one short Bengali line>","phrases":["2-6 short Bengali phrases (1-3 words) that OTHER questions about the SAME concept would literally contain"],"exclude":["phrases of look-alike but DIFFERENT topics that must not match"]}
Rules: phrases must be specific, e.g. "মৌলিক সংখ্যা" not just "মৌলিক"; exclude e.g. "মৌলিক ধাতু". Never use generic words (কোন, কিন্তু, শব্দ, সংখ্যা alone) as a phrase.
""".trimIndent()
        val raw1 = TechniqueAiClient.ask(keys, p1, 600)
        val j1 = raw1?.let { TechniqueAiClient.extractJson(it) }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@withContext TechniqueAiOutcome(emptyList(), "⚠️ AI সাড়া দেয়নি বা উত্তর বোঝা যায়নি — নেট/Key দেখে আবার চেষ্টা করুন", ok = false)
        fun arr(name: String): List<String> =
            j1.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val concept = j1.optString("concept").trim()
        val phrases = (source.tagList() + arr("phrases")).map { spaced(it).trim() }
            .filter { it.length >= 3 }.distinct().take(8)
        val excl = arr("exclude").map { spaced(it).trim() }.filter { it.length >= 3 }
        if (phrases.isEmpty())
            return@withContext TechniqueAiOutcome(emptyList(), "⚠️ AI কোনো অনুসন্ধান-বাক্য দেয়নি", ok = false)

        // ── ধাপ ২: ফ্রি প্রার্থী বাছাই ──
        val pool = (dao.getAll("QUIZ") + dao.getAll("QBANK")).filter { !it.questionType.equals("study", true) }
        val cands = ArrayList<Pair<Prepared, Double>>()
        for (e in pool) {
            if (e.fbKey == source.questionId || e.fbKey in skipIds || e.fbKey in linked || e.fbKey in rejected) continue
            val p = prepare(e)
            if (excl.any { x -> (" $x") in p.spacedQ || (" $x") in p.spacedA }) continue
            var sc = 0.0
            for (ph in phrases) {
                val n = " $ph"
                if (n in p.spacedQ) sc += 3
                if (n in p.spacedA) sc += 2
            }
            if (sc > 0) cands += p to sc
        }
        if (cands.isEmpty())
            return@withContext TechniqueAiOutcome(emptyList(), "✨ ধারণা: $concept\nনতুন কোনো প্রার্থী প্রশ্ন পাওয়া যায়নি।", candidates = 0)
        val top = cands.sortedByDescending { it.second }.take(AI_CANDIDATES)

        // ── ধাপ ৩: AI বিচারক (২০টা করে) ──
        val out = ArrayList<TechniqueSuggestion>()
        var failed = 0
        for (batch in top.chunked(AI_BATCH)) {
            val list = StringBuilder()
            batch.forEach { (p, _) ->
                val o = listOf(p.e.optionA, p.e.optionB, p.e.optionC, p.e.optionD).filter { it.isNotBlank() }.joinToString(" | ")
                list.append("[${p.e.fbKey}] Q: ${short(p.e.question, 200)} | OPTIONS: ${short(o, 160)} | ANSWER: ${short(p.answerText, 80)}\n")
            }
            val p2 = """
A student made this memory technique for the SOURCE question. For each CANDIDATE question decide how much the technique helps.
$srcBlock

Verdicts:
"direct" = the technique gives or lets you derive the candidate's correct answer, or clearly helps pick it among the options.
"related" = same topic, but the technique only partly helps.
"no" = different topic, or only common words overlap.
If unsure answer "no". Never match on generic words alone. For number questions, check that the technique really yields the candidate's answer.

CANDIDATES:
$list
Return ONLY a JSON array, one item per candidate: [{"id":"<id exactly as in brackets>","v":"direct|related|no","why":"<max 12 words in Bengali>"}]
""".trimIndent()
            val raw = TechniqueAiClient.ask(keys, p2, 1600)
            val json = raw?.let { TechniqueAiClient.extractJson(it) }
            val jarr = json?.let { j ->
                runCatching { if (j.trimStart().startsWith("[")) JSONArray(j) else JSONObject(j).optJSONArray("results") }.getOrNull()
            }
            if (jarr == null) { failed++; continue }
            val byId = batch.associate { it.first.e.fbKey to it.first }
            for (i in 0 until jarr.length()) {
                val o = jarr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim().removePrefix("[").removeSuffix("]")
                val p = byId[id] ?: continue          // AI-র বানানো id ফেলে দেওয়া হয়
                val v = o.optString("v").trim().lowercase()
                val why = o.optString("why").trim()
                val kind = when { v.startsWith("direct") -> TechniqueMatch.AI; v.startsWith("related") -> TechniqueMatch.AIR; else -> continue }
                out.add(TechniqueSuggestion(
                    questionId = p.e.fbKey, sheet = p.e.sheet, question = p.e.question, answer = p.answerText,
                    subject = p.e.subject, subTopic = p.e.subTopic,
                    reason = (if (kind == TechniqueMatch.AI) "✨ AI: " else "🔶 AI (সম্পর্কিত): ") + why.ifBlank { concept },
                    score = if (kind == TechniqueMatch.AI) 150.0 else 50.0, kinds = setOf(kind)
                ))
            }
        }
        val d = out.count { TechniqueMatch.AI in it.kinds }; val r = out.count { TechniqueMatch.AIR in it.kinds }
        TechniqueAiOutcome(
            out.sortedByDescending { it.score },
            "✨ ধারণা: $concept\n${top.size}টা প্রার্থী দেখে $d টা সরাসরি, $r টা সম্পর্কিত পাওয়া গেছে" +
                (if (failed > 0) "\n⚠️ $failed টা ব্যাচে AI সাড়া দেয়নি — আবার চেষ্টা করলে বাকিগুলো আসতে পারে" else ""),
            ok = failed < top.chunked(AI_BATCH).size, candidates = top.size
        )
    }
}
