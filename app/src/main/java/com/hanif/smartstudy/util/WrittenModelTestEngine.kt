package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.AnswerState
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.quiz.WRITTEN_SUBJECT_BUCKET
import com.hanif.smartstudy.ui.quiz.paperHeadingBase
import com.hanif.smartstudy.ui.quiz.paperNormHeading
import com.hanif.smartstudy.ui.quiz.resolvePaperLayout
import java.text.Normalizer
import kotlin.random.Random

/**
 * লিখিত মডেল টেস্ট (সব বিষয় মিলিয়ে একটাই পেপার)।
 *
 * পেপার = কয়েকটা সেকশন; প্রতিটা সেকশনে ৫টা উপ-প্রশ্ন (ক–ঙ)।
 *  • গ্রুপ-ভিত্তিক ধরন (বাংলা/ইংরেজি ব্যাকরণ: সন্ধি বিচ্ছেদ, বাগধারা, এক কথায় প্রকাশ, Translation …):
 *    সেকশনের ৫টা প্রশ্ন ৫টা আলাদা উৎস-গ্রুপ থেকে (প্রতি গ্রুপ থেকে একটা) — গ্রুপ কম থাকলে প্রতি গ্রুপ থেকে সর্বোচ্চ ২টা।
 *  • একক প্রশ্নের ধরন (গণিত, সাধারণ জ্ঞান, কম্পিউটার): সেকশন = বিষয়ের নামে "সংক্ষিপ্ত প্রশ্নোত্তর", ৫টা প্রশ্ন আলাদা টপিক থেকে।
 *  • কোন ধরনের সেকশন আসবে, কোন গ্রুপ/প্রশ্ন আসবে — সবই প্রতিবার র‍্যান্ডম; ৪ বিষয়ে (বাংলা/ইংরেজি/গণিত/সাধারণ জ্ঞান) সমান ভাগ।
 *  • ধরন চেনা: একই টপিকের গ্রুপগুলোকে নির্দেশনা-হেডিং মিলিয়ে ভাগ করা হয় (টাইপো-ভিন্নতা একসাথে);
 *    "সংক্ষিপ্ত প্রশ্নের উত্তর দাও"-জাতীয় সাধারণ হেডিংয়ের গ্রুপ আলাদা "সংক্ষিপ্ত প্রশ্নোত্তর" ধরনে যায়;
 *    শব্দভাণ্ডার-সহ অনুচ্ছেদ (cloze) গ্রুপ মেশানো যায় না বলে বাদ।
 *  • আগের লিখিত মডেল টেস্টে আসা প্রশ্ন পরে বসে (recent)।
 *
 * প্রতিটা সেকশন একটা "ভার্চুয়াল গ্রুপ" (নতুন groupId + হেডিং + subIndex) — তাই বর্তমান লিখিত পেপার স্ক্রিনই
 * (বিষয়-ট্যাব, ক/খ/গ লেবেল, লেআউট) হুবহু ব্যবহার হয়, নতুন স্ক্রিন লাগে না।
 */
object WrittenModelTestEngine {
    /** activeQBankMt.category-তে বসানো মার্কার + "আগে দেখা প্রশ্ন"-র স্টোর-কী */
    const val CATEGORY_KEY = "__written_model_test__"
    const val PER_SECTION = 5
    val SIZE_OPTIONS = listOf(25, 40, 50)

    private val ORDER = listOf("বাংলা", "ইংরেজি", "গণিত", "সাধারণ জ্ঞান")
    private val GENERIC = listOf("সংক্ষিপ্ত প্রশ্ন", "প্রশ্নের উত্তর", "প্রশ্নগুলোর", "answer the", "write the answer", "বাংলা ব্যাকরণ", "answer following")
    private val STOP_WORDS = setOf("করুন", "কর", "লিখুন", "দাও", "দিন", "লেখ")
    private const val SIM = 0.45
    private const val MIN_TYPE_GROUPS = 3     // এর কম গ্রুপের ধরন বাদ
    private const val MIN_SECTION_Q = 3       // এর কম প্রশ্ন জোগাড় হলে সেকশন বাদ

    private val TAG_RE   = Regex("<[^>]+>")
    private val PUNCT_RE = Regex("[\\-–—.,]+")
    private val WS_RE    = Regex("\\s+")

    data class Section(val title: String, val subject: String, val questions: List<QuestionItem>)
    data class SubjectStat(val subject: String, val sections: Int, val questions: Int)
    data class Plan(
        val requested: Int,
        val sections: List<Section>,
        val questions: List<QuestionItem>,      // ভার্চুয়াল-গ্রুপ বসানো, পেপারের ক্রমে
        val stats: List<SubjectStat>,
        val notes: List<String>,
        val poolSize: Int,
        val eligible: Int
    )

    /** একটা "ধরন": groups != null → গ্রুপ-ভিত্তিক; singles != null → একক প্রশ্ন */
    private class Type(
        val subject: String,
        val title: String,
        val groups: Map<String, List<QuestionItem>>?,
        val singles: List<QuestionItem>?
    ) {
        /** আর কতটা আলাদা উৎস আছে (র‍্যান্ডম বাছাইয়ের ওজন) */
        fun sources(): Int = groups?.size ?: singles!!.map { it.topicId }.distinct().size
    }

    // ── হেডিং-নরমালাইজ ──
    private fun normHeading(h: String): String = paperNormHeading(paperHeadingBase(h))

    private fun keyNorm(h: String): String {
        val t = PUNCT_RE.replace(TAG_RE.replace(normHeading(h), ""), " ")
        return t.split(' ').filter { it.isNotBlank() && it !in STOP_WORDS }.joinToString(" ")
    }

    private fun bigrams(s: String): Map<String, Int> {
        val t = s.replace(" ", "")
        val m = HashMap<String, Int>()
        for (i in 0 until t.length - 1) {
            val k = t.substring(i, i + 2)
            m[k] = (m[k] ?: 0) + 1
        }
        return m
    }

    private fun dice(a: String, b: String): Double {
        val x = bigrams(a); val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        var inter = 0
        for ((k, v) in x) inter += minOf(v, y[k] ?: 0)
        return 2.0 * inter / (x.values.sum() + y.values.sum())
    }

    private fun isGeneric(h: String): Boolean {
        val n = TAG_RE.replace(h, "").lowercase()
        return GENERIC.any { n.contains(it) }
    }

    private fun subjectOf(q: QuestionItem): String? = WRITTEN_SUBJECT_BUCKET[q.subjectId]?.takeIf { it in ORDER }

    private fun normText(s: String): String =
        WS_RE.replace(Normalizer.normalize(s, Normalizer.Form.NFC), " ").trim().lowercase()

    /** পুল থেকে (লিখিত) যোগ্য প্রশ্ন + সব "ধরন" বানায় */
    private fun buildTypes(pool: List<QuestionItem>): Pair<Map<String, Type>, Int> {
        val seen = HashSet<String>()
        val elig = ArrayList<QuestionItem>()
        for (q in pool) {
            if (!q.questionType.equals("written", ignoreCase = true)) continue
            if (q.question.isBlank() || q.answer.isBlank()) continue
            if (!seen.add(normText(q.question))) continue          // একই প্রশ্ন দুবার নয়
            elig.add(q)
        }
        val types = LinkedHashMap<String, Type>()

        // ── গ্রুপ-ভিত্তিক ──
        val byGroup = elig.filter { it.groupId.isNotBlank() }.groupBy { it.groupId }
        val byTopic = HashMap<String, MutableList<Triple<String, List<QuestionItem>, String>>>()
        for ((gid, list) in byGroup) {
            val qs = list.sortedBy { it.subIndex }
            val first = qs.first()
            val h = first.groupHeading
            val isCloze = resolvePaperLayout(h, "", false, false).kind == "cloze" ||
                h.split("\n").getOrNull(1)?.contains('|') == true
            if (isCloze) continue                                   // অনুচ্ছেদ-বাঁধা গ্রুপ মেশানো যায় না
            if (subjectOf(first) == null) continue
            byTopic.getOrPut(first.topicId) { ArrayList() }.add(Triple(gid, qs, h))
        }
        val genericBySubject = HashMap<String, MutableList<Triple<String, List<QuestionItem>, String>>>()
        for ((tid, gs) in byTopic) {
            val gen = gs.filter { isGeneric(it.third) }
            val non = gs.filterNot { isGeneric(it.third) }
            for (g in gen) genericBySubject.getOrPut(subjectOf(g.second.first())!!) { ArrayList() }.add(g)
            if (non.isEmpty()) continue
            // টপিকের প্রধান নির্দেশনা (সবচেয়ে বেশি গ্রুপ যে হেডিংয়ে)
            val dom = non.groupingBy { keyNorm(it.third) }.eachCount().maxByOrNull { it.value }!!.key
            val clusters = LinkedHashMap<String, MutableList<Triple<String, List<QuestionItem>, String>>>()
            for (g in non) {
                val kn = keyNorm(g.third)
                val c = if (kn == dom || dice(kn, dom) >= SIM) dom else kn
                clusters.getOrPut(c) { ArrayList() }.add(g)
            }
            for ((c, items) in clusters) {
                if (items.size < MIN_TYPE_GROUPS) continue
                val title = items.groupingBy { it.third.split("\n").first().trim() }.eachCount().maxByOrNull { it.value }!!.key
                types["G:$tid:$c"] = Type(subjectOf(items.first().second.first())!!, title, items.associate { it.first to it.second }, null)
            }
        }
        // সাধারণ হেডিংয়ের গ্রুপ → বিষয়ভিত্তিক "সংক্ষিপ্ত প্রশ্নোত্তর"
        for ((subject, items) in genericBySubject) {
            if (items.size < MIN_TYPE_GROUPS) continue
            val title = items.groupingBy { it.third.split("\n").first().trim() }.eachCount().maxByOrNull { it.value }!!.key
            types["GEN:$subject"] = Type(subject, title, items.associate { it.first to it.second }, null)
        }

        // ── একক প্রশ্ন (শুধু গণিত ও সাধারণ জ্ঞান; ভাষার একক প্রশ্ন সব রচনা-জাতীয়, তাই বাদ) ──
        val singlesBySubjectId = elig.filter { it.groupId.isBlank() }.groupBy { it.subjectId }
        for ((sid, qs) in singlesBySubjectId) {
            val label = WRITTEN_SUBJECT_BUCKET[sid] ?: continue
            if (label != "গণিত" && label != "সাধারণ জ্ঞান") continue
            if (qs.size < PER_SECTION) continue
            val name = qs.first().subject.ifBlank { sid }
            types["S:$sid"] = Type(label, "সংক্ষিপ্ত প্রশ্নোত্তর — $name", null, qs)
        }
        return types to elig.size
    }

    // ── সেকশনের প্রশ্ন বাছাই ──
    private fun pickFromGroups(t: Type, used: Set<String>, recent: Set<String>, rnd: Random): List<QuestionItem> {
        fun tier(q: QuestionItem) = if (q.id in recent) 1 else 0
        val gs = t.groups!!.entries.map { it.key to it.value }.shuffled(rnd).sortedBy { g -> g.second.minOf { tier(it) } }
        val chosen = ArrayList<QuestionItem>()
        val per = HashMap<String, Int>()
        for (cap in 1..2) {                       // প্রথমে প্রতি গ্রুপ থেকে ১টা; কম পড়লে সর্বোচ্চ ২টা
            for ((gid, qs) in gs) {
                if (chosen.size >= PER_SECTION) break
                if ((per[gid] ?: 0) >= cap) continue
                val cand = qs.filter { c -> c.id !in used && chosen.none { it.id == c.id } }
                if (cand.isEmpty()) continue
                chosen.add(cand.shuffled(rnd).minByOrNull { tier(it) }!!)
                per[gid] = (per[gid] ?: 0) + 1
            }
        }
        return chosen
    }

    private fun pickSingles(t: Type, used: Set<String>, recent: Set<String>, rnd: Random): List<QuestionItem> {
        fun tier(q: QuestionItem) = if (q.id in recent) 1 else 0
        val qs = t.singles!!.shuffled(rnd).sortedBy { tier(it) }
        val chosen = ArrayList<QuestionItem>()
        val per = HashMap<String, Int>()
        for (cap in 1..2) {                       // আলাদা টপিক থেকে; কম পড়লে টপিক-প্রতি ২টা
            for (q in qs) {
                if (chosen.size >= PER_SECTION) break
                if (q.id in used || chosen.any { it.id == q.id }) continue
                if ((per[q.topicId] ?: 0) >= cap) continue
                chosen.add(q)
                per[q.topicId] = (per[q.topicId] ?: 0) + 1
            }
        }
        return chosen
    }

    /** count = মোট প্রশ্ন (২৫/৪০/৫০ → ৫/৮/১০ সেকশন) */
    fun generate(pool: List<QuestionItem>, count: Int, recent: Set<String> = emptySet(), rnd: Random = Random.Default): Plan {
        val (types, eligible) = buildTypes(pool)
        val nSec = maxOf(1, count / PER_SECTION)

        // বিষয়-কোটা: ৪ বিষয়ে সমান, ভাগশেষ র‍্যান্ডম বিষয়ে
        val quota = ORDER.associateWith { nSec / 4 }.toMutableMap()
        ORDER.shuffled(rnd).take(nSec % 4).forEach { quota[it] = quota[it]!! + 1 }

        val used = HashSet<String>()
        val chosenSections = ArrayList<Triple<String, String, List<QuestionItem>>>()   // (বিষয়, type-key, প্রশ্ন)
        val usedTypes = HashSet<String>()
        val notes = ArrayList<String>()

        fun tryType(key: String): List<QuestionItem>? {
            val t = types[key] ?: return null
            val picked = if (t.groups != null) pickFromGroups(t, used, recent, rnd) else pickSingles(t, used, recent, rnd)
            if (picked.size < MIN_SECTION_Q) return null
            picked.forEach { used.add(it.id) }
            usedTypes.add(key)
            return picked
        }

        var deficit = 0
        for (label in ORDER) {
            // ওজনসহ র‍্যান্ডম ক্রম: যে ধরনে উৎস বেশি, আগে আসার সম্ভাবনা বেশি
            val remaining = types.filter { it.value.subject == label }.keys.shuffled(rnd).toMutableList()
            val ordered = ArrayList<String>()
            while (remaining.isNotEmpty()) {
                val w = remaining.map { minOf(types[it]!!.sources(), 8).coerceAtLeast(1) }
                var r = rnd.nextInt(w.sum())
                var idx = 0
                while (r >= w[idx]) { r -= w[idx]; idx++ }
                ordered.add(remaining.removeAt(idx))
            }
            var got = 0
            for (key in ordered) {
                if (got >= quota[label]!!) break
                val picked = tryType(key) ?: continue
                chosenSections.add(Triple(label, key, picked))
                got++
            }
            if (got < quota[label]!!) {
                deficit += quota[label]!! - got
                notes.add("$label: ${quota[label]!! - got}টি সেকশন কম পড়েছে (এই বিষয়ে ধরন কম) — অন্য বিষয় থেকে পূরণ করা হয়েছে।")
            }
        }
        if (deficit > 0) {                          // ঘাটতি অন্য বিষয়ের অব্যবহৃত ধরন থেকে
            for (key in types.keys.filterNot { it in usedTypes }.shuffled(rnd)) {
                if (deficit <= 0) break
                val picked = tryType(key) ?: continue
                chosenSections.add(Triple(types[key]!!.subject, key, picked))
                deficit--
            }
        }

        // বিষয়ের ক্রমে সাজাই (বিষয়ের ভেতরে র‍্যান্ডম ক্রমই থাকে)
        val sorted = chosenSections.sortedBy { ORDER.indexOf(it.first) }
        val stamp = System.currentTimeMillis().toString(36)
        val sections = ArrayList<Section>()
        val questions = ArrayList<QuestionItem>()
        sorted.forEachIndexed { n, (label, key, qs) ->
            val title = types[key]!!.title
            val vid = "WMT-$stamp-${n + 1}"
            val vq = qs.mapIndexed { i, q ->
                q.copy(groupId = vid, groupHeading = title, subIndex = i + 1, answerState = AnswerState.Unanswered)
            }
            sections.add(Section(title, label, vq))
            questions.addAll(vq)
        }
        val stats = ORDER.mapNotNull { label ->
            val ss = sections.filter { it.subject == label }
            if (ss.isEmpty()) null else SubjectStat(label, ss.size, ss.sumOf { it.questions.size })
        }
        if (sections.size < nSec) notes.add("প্রশ্ন-ভাণ্ডারে যথেষ্ট ধরন না থাকায় ${nSec - sections.size}টি সেকশন কম।")
        return Plan(count, sections, questions, stats, notes, pool.size, eligible)
    }
}
