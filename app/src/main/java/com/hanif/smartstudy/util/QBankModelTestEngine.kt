package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.QuestionItem
import java.text.Normalizer
import kotlin.random.Random

/**
 * QBank Model Test Engine — QBank-এর একটা পরীক্ষা-ক্যাটাগরির (বিসিএস/প্রাথমিক/নিবন্ধন...) পুরো
 * প্রশ্ন-পুল থেকে ৫০/১০০/১৫০/২০০ প্রশ্নের balanced + randomized + personalized টেস্ট বানায়।
 *
 * নিয়ম (ক্রমানুযায়ী):
 *  ১. বাদ: ডুপ্লিকেট, ত্রুটিপূর্ণ (প্রশ্ন/উত্তর/অপশন নেই), written, multi-part (group) প্রশ্ন।
 *  ২. ৬টা বিষয়-গ্রুপে সমান ভাগ ("গড় হিসাব"): বাংলা সাহিত্য, বাংলা ব্যাকরণ, ইংরেজি সাহিত্য,
 *     ইংরেজি ব্যাকরণ, গণিত, সাধারণ জ্ঞান। ভাগশেষ থাকলে সাধারণ জ্ঞান → গণিত → ... ক্রমে ১টা করে বাড়তি।
 *     (বাংলাদেশ বিষয়াবলি / আন্তর্জাতিক / কম্পিউটার / বিজ্ঞান ইত্যাদি = সাধারণ জ্ঞান গ্রুপ)
 *  ৩. একই Topic থেকে সর্বোচ্চ ২টি (hard rule) — প্রতি রাউন্ডে প্রতি topic থেকে ১টা করে, তাই কভারেজ বিস্তৃত।
 *  ৪. প্রাধান্য: কখনো দেওয়া হয়নি → আগে ভুল হয়েছে → বাকি; সম্প্রতি মডেল টেস্টে এসেছে এমন প্রশ্ন সবার শেষে;
 *     প্রতি স্তরে র‍্যান্ডম।
 *  ৫. Fallback (error না দিয়ে): কোনো গ্রুপে প্রশ্ন কম পড়লে অন্য গ্রুপ থেকে পূরণ → তবু কম পড়লে topic-limit
 *     ধাপে ধাপে ৩, ৪, ... শিথিল। শেষে notes-এ কারণ জানানো হয়।
 */
object QBankModelTestEngine {

    enum class Bucket(val label: String, val emoji: String) {
        BANGLA_LIT("বাংলা সাহিত্য", "📖"),
        BANGLA_GRAMMAR("বাংলা ব্যাকরণ", "✍️"),
        ENGLISH_LIT("ইংরেজি সাহিত্য", "📚"),
        ENGLISH_GRAMMAR("ইংরেজি ব্যাকরণ", "🔤"),
        MATH("গণিত", "🔢"),
        GK("সাধারণ জ্ঞান", "🌍")
    }

    /** ভাগশেষ বিলির ক্রম */
    private val REMAINDER_ORDER = listOf(
        Bucket.GK, Bucket.MATH, Bucket.BANGLA_LIT, Bucket.ENGLISH_LIT, Bucket.BANGLA_GRAMMAR, Bucket.ENGLISH_GRAMMAR
    )

    const val TOPIC_CAP = 2
    val COUNT_OPTIONS = listOf(50, 100, 150, 200)

    data class BucketStat(
        val bucket   : Bucket,
        val quota    : Int,   // সমান ভাগে যা পাওয়ার কথা
        val actual   : Int,   // আসলে যতগুলো এসেছে
        val topics   : Int,   // কয়টা আলাদা topic থেকে
        val available: Int    // এই গ্রুপে যোগ্য প্রশ্ন কতগুলো ছিল
    )

    data class Plan(
        val category : String,
        val requested: Int,
        val questions: List<QuestionItem>,
        val stats    : List<BucketStat>,
        val notes    : List<String>,
        val poolSize : Int,    // ক্যাটাগরির মোট প্রশ্ন
        val eligible : Int     // বাদ দেওয়ার পর যোগ্য
    ) {
        val isComplete get() = questions.size >= requested
    }

    /** চলমান/সদ্য-শেষ QBank Model Test (ViewModel state-এ থাকে) */
    data class Active(
        val category    : String,
        val requested   : Int,
        val title       : String,
        val questionIds : List<String>
    )

    private class Cand(val q: QuestionItem, val bucket: Bucket, val topicKey: String, val tier: Int, val rnd: Double)

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC).lowercase()

    // ── ব্যাকরণের টপিক-চিহ্ন (সাবজেক্ট একসাথে "ভাষা ও সাহিত্য" হলে topic দেখে ব্যাকরণ আলাদা করা হয়) ──
    private val BN_GRAMMAR_TOPICS = listOf(
        "ব্যাকরণ", "গ্রামার", "সন্ধি", "সমাস", "কারক", "বিভক্তি", "প্রত্যয়", "উপসর্গ", "অনুসর্গ", "বাক্য",
        "বানান", "ধ্বনি", "লিঙ্গ", "বচন", "পুরুষ", "ণত্ব", "ষত্ব", "শব্দ", "পদ", "সমার্থক", "বিপরীত",
        "প্রবাদ", "বাগধারা", "বাগ্ধারা", "এক কথায়", "অলংকার", "ছন্দ", "পরিভাষা", "অনুবাদ", "কাল", "ভাব"
    )
    private val EN_GRAMMAR_TOPICS = listOf(
        "grammar", "tense", "voice", "narration", "article", "preposition", "transformation", "sentence",
        "parts of speech", "noun", "verb", "adjective", "adverb", "pronoun", "conjunction", "right form",
        "sentence correction", "fill in", "synonym", "antonym", "idiom", "phrase", "translation", "vocabulary",
        "spelling", "punctuation", "subject-verb", "determiner", "ব্যাকরণ", "গ্রামার"
    )
    private val EN_LIT_TOPICS = listOf("literature", "poet", "poem", "novel", "drama", "shakespeare", "সাহিত্য")

    /** (subject, subTopic) → ৬ গ্রুপের একটা। শিটের নাম বদলালে এই এক ফাংশনই টিউন করতে হবে। */
    fun bucketOf(subjectRaw: String, subTopicRaw: String): Bucket {
        val s = nfc(subjectRaw)
        val t = nfc(subTopicRaw)
        val both = "$s $t"
        fun has(text: String, vararg kws: String) = kws.any { text.contains(nfc(it)) }

        val isMath = has(both, "গণিত", "পাটিগণিত", "বীজগণিত", "জ্যামিতি", "math", "arithmetic", "algebra", "geometry")
        if (isMath && !has(s, "বাংলা", "ইংরেজি", "english")) return Bucket.MATH

        val isBangla  = has(s, "বাংলা", "bangla", "bengali") && !has(s, "বাংলাদেশ")
        val isEnglish = has(s, "ইংরেজি", "english")
        return when {
            isBangla -> {
                if (has(s, "ব্যাকরণ", "গ্রামার", "grammar")) Bucket.BANGLA_GRAMMAR
                else if (has(s, "সাহিত্য", "literature")) {
                    // "বাংলা ভাষা ও সাহিত্য" — topic ব্যাকরণ-জাতীয় হলে ব্যাকরণ, নাহলে সাহিত্য
                    if (BN_GRAMMAR_TOPICS.any { t.contains(nfc(it)) }) Bucket.BANGLA_GRAMMAR else Bucket.BANGLA_LIT
                } else {
                    if (BN_GRAMMAR_TOPICS.any { t.contains(nfc(it)) }) Bucket.BANGLA_GRAMMAR else Bucket.BANGLA_LIT
                }
            }
            isEnglish -> {
                if (has(s, "ব্যাকরণ", "গ্রামার", "grammar")) Bucket.ENGLISH_GRAMMAR
                else if (has(s, "সাহিত্য", "literature")) {
                    if (EN_GRAMMAR_TOPICS.any { t.contains(nfc(it)) } && !EN_LIT_TOPICS.any { t.contains(nfc(it)) }) Bucket.ENGLISH_GRAMMAR
                    else Bucket.ENGLISH_LIT
                } else {
                    if (EN_LIT_TOPICS.any { t.contains(nfc(it)) }) Bucket.ENGLISH_LIT
                    else if (EN_GRAMMAR_TOPICS.any { t.contains(nfc(it)) }) Bucket.ENGLISH_GRAMMAR
                    else Bucket.ENGLISH_LIT
                }
            }
            else -> Bucket.GK
        }
    }

    // ── যোগ্যতা যাচাই ──
    private fun isUsable(q: QuestionItem): Boolean {
        if (q.id.isBlank() || q.question.isBlank() || q.answer.isBlank()) return false
        if (!q.isMcq() || q.isGrouped()) return false
        val opts = listOf(q.optionA, q.optionB, q.optionC, q.optionD).count { it.isNotBlank() }
        return opts >= 2
    }

    private fun dupKey(q: QuestionItem): String =
        nfc(q.question).replace(Regex("[\\s\\p{P}]+"), "") + "|" + nfc(q.answer).replace(Regex("[\\s\\p{P}]+"), "")

    private fun topicKeyOf(q: QuestionItem): String =
        q.topicId.ifBlank { nfc(q.subject) + "|" + nfc(q.subTopic) }

    /** সমান ভাগ + ভাগশেষ */
    fun quotas(count: Int): Map<Bucket, Int> {
        val n = Bucket.values().size
        val base = count / n
        var rem = count % n
        val out = LinkedHashMap<Bucket, Int>()
        Bucket.values().forEach { out[it] = base }
        for (b in REMAINDER_ORDER) { if (rem <= 0) break; out[b] = out.getValue(b) + 1; rem-- }
        return out
    }

    fun generate(
        category : String,
        pool     : List<QuestionItem>,
        count    : Int,
        attempted: Set<String> = emptySet(),
        wrong    : Set<String> = emptySet(),
        recent   : Set<String> = emptySet(),
        seed     : Long? = null
    ): Plan {
        val rnd = if (seed != null) Random(seed) else Random.Default
        val notes = mutableListOf<String>()

        // ── ১. যোগ্য প্রশ্ন (ডুপ্লিকেট/ত্রুটিপূর্ণ বাদ) ──
        val seenIds = HashSet<String>(); val seenDup = HashSet<String>()
        val cands = ArrayList<Cand>()
        var dropped = 0
        for (q in pool) {
            if (!seenIds.add(q.id)) continue
            if (!isUsable(q)) { dropped++; continue }
            if (!seenDup.add(dupKey(q))) { dropped++; continue }
            val tier = when {
                q.id in recent -> 3
                q.id !in attempted -> 0
                q.id in wrong -> 1
                else -> 2
            }
            cands.add(Cand(q, bucketOf(q.subject, q.subTopic), topicKeyOf(q), tier, rnd.nextDouble()))
        }
        if (dropped > 0) notes += "ℹ️ $dropped টি প্রশ্ন বাদ গেছে (ডুপ্লিকেট / written / multi-part / ত্রুটিপূর্ণ)"

        val byBucket = cands.groupBy { it.bucket }
        val selected = LinkedHashSet<String>()
        val topicCount = HashMap<String, Int>()
        val chosen = ArrayList<Cand>()

        // একটা candidate-সেট থেকে need টা তোলে: প্রতি রাউন্ডে প্রতি topic থেকে ১টা (best tier আগে), cap পর্যন্ত
        fun take(from: List<Cand>, need: Int, cap: Int): Int {
            var left = need
            while (left > 0) {
                val groups = from.filter { it.q.id !in selected && (topicCount[it.topicKey] ?: 0) < cap }
                    .groupBy { it.topicKey }
                if (groups.isEmpty()) break
                val heads = groups.values
                    .map { l -> l.sortedWith(compareBy<Cand> { it.tier }.thenBy { it.rnd }).first() }
                    .sortedWith(compareBy<Cand> { it.tier }.thenBy { it.rnd })
                var progressed = false
                for (h in heads) {
                    if (left <= 0) break
                    if ((topicCount[h.topicKey] ?: 0) >= cap || h.q.id in selected) continue
                    selected.add(h.q.id); chosen.add(h)
                    topicCount[h.topicKey] = (topicCount[h.topicKey] ?: 0) + 1
                    left--; progressed = true
                }
                if (!progressed) break
            }
            return need - left
        }

        // ── ২+৩. প্রতি গ্রুপে সমান ভাগ, topic ≤ ২ ──
        val quota = quotas(count)
        quota.forEach { (b, qn) -> take(byBucket[b].orEmpty(), qn, TOPIC_CAP) }

        // ── ৫ক. কোনো গ্রুপে কম পড়লে অন্য গ্রুপ থেকে (এখনো topic ≤ ২) ──
        var short = count - chosen.size
        if (short > 0) {
            val before = chosen.size
            var progressed = true
            while (short > 0 && progressed) {
                progressed = false
                val order = Bucket.values().sortedByDescending { b ->
                    byBucket[b].orEmpty().count { it.q.id !in selected }
                }
                for (b in order) {
                    if (short <= 0) break
                    if (take(byBucket[b].orEmpty(), 1, TOPIC_CAP) > 0) { short--; progressed = true }
                }
            }
            if (chosen.size > before) notes += "⚠️ কিছু বিষয়ে যথেষ্ট প্রশ্ন নেই, তাই ${chosen.size - before} টি প্রশ্ন অন্য বিষয় থেকে পূরণ করা হয়েছে"
        }

        // ── ৫খ. তবু কম পড়লে topic-limit ধাপে ধাপে শিথিল ──
        short = count - chosen.size
        if (short > 0) {
            var cap = TOPIC_CAP
            val before = chosen.size
            while (short > 0 && cap < 50) {
                cap++
                short -= take(cands, short, cap)
            }
            if (chosen.size > before) notes += "⚠️ একই topic থেকে ২টির বেশি না নিলে ${count} প্রশ্ন পূরণ হতো না — তাই ${chosen.size - before} টি প্রশ্নে সীমা শিথিল করা হয়েছে (সর্বোচ্চ ${topicCount.values.maxOrNull() ?: TOPIC_CAP}টি/topic)"
        }
        if (chosen.size < count) {
            notes += "❗ এই ক্যাটাগরিতে যোগ্য প্রশ্ন মোটে ${cands.size} টি — তাই $count এর বদলে ${chosen.size} টি প্রশ্নের টেস্ট হচ্ছে"
        }
        if (cands.isEmpty()) notes += "❗ এই ক্যাটাগরিতে এখনো কোনো যোগ্য MCQ প্রশ্ন পাওয়া যায়নি"

        // ── কোন গ্রুপ কেন কম/বেশি (রিপোর্ট) ──
        val stats = Bucket.values().map { b ->
            val got = chosen.filter { it.bucket == b }
            BucketStat(b, quota.getValue(b), got.size, got.map { it.topicKey }.distinct().size, byBucket[b].orEmpty().size)
        }
        stats.filter { it.actual != it.quota }.forEach {
            val why = if (it.available <= it.actual) "যোগ্য প্রশ্নই মাত্র ${it.available}টি" else "অন্য গ্রুপের ঘাটতি পূরণে"
            notes += "• ${it.bucket.label}: ${it.quota} এর বদলে ${it.actual} ($why)"
        }
        if (recent.isNotEmpty() && chosen.any { it.tier == 3 }) {
            notes += "ℹ️ ${chosen.count { it.tier == 3 }} টি প্রশ্ন সাম্প্রতিক টেস্টে এসেছিল — নতুন প্রশ্ন কম থাকায় আবার এসেছে"
        }

        // ── ক্রম: গ্রুপ-ওয়ারি (বাংলা → ইংরেজি → গণিত → GK), গ্রুপের ভেতরে র‍্যান্ডম ──
        val ordered = Bucket.values().flatMap { b -> chosen.filter { it.bucket == b }.shuffled(rnd) }.map { it.q }
        return Plan(category, count, ordered, stats, notes, pool.size, cands.size)
    }
}
