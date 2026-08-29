package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.CurriculumProgressEntity
import com.hanif.smartstudy.data.model.BijoyCurriculum

/**
 * Phase ৩ (#1+#2): Key-unlock প্রগ্রেসিভ কারিকুলামের লজিক — কোন স্টেজে আছে,
 * সেই স্টেজের জন্য প্র্যাকটিস-টেক্সট বানানো, আর unlock-শর্ত পূরণ হলে পরের
 * স্টেজে এগিয়ে দেওয়া।
 *
 * প্রতিটা ক্যারেক্টারের accuracy/keypress ডেটার জন্য নতুন কিছু বানানো হয়নি —
 * Phase ১-এর TypingKeyStatStore/TypingKeyStatEntity-ই পুনরায় ব্যবহার করা হয়েছে
 * (কারণ কারিকুলাম-মোডে অনুশীলন করা প্রতিটা কীপ্রেসও তো সেখানেই জমা হয়)। এটা
 * একটা গুরুত্বপূর্ণ সরলীকরণ — ডেটা ডুপ্লিকেট এড়ায়।
 *
 * Unlock শর্ত (ব্যবহারকারীর সাথে আলোচনা করে ঠিক করা, Neonlipi-এর চেয়ে একটু নরম —
 * মোবাইলে ছোট সেশনে বাস্তবসম্মত): প্রতিটা নতুন ক্যারেক্টারে অন্তত UNLOCK_MIN_CORRECT
 * বার সঠিক কীপ্রেস + UNLOCK_MIN_ACCURACY এর বেশি accuracy, এবং সাম্প্রতিক bestWpm
 * টার্গেট WPM-এর UNLOCK_WPM_FRACTION অংশ ছাড়িয়ে গেছে।
 */
object CurriculumProvider {

    const val UNLOCK_MIN_CORRECT   = 150
    const val UNLOCK_MIN_ACCURACY  = 0.92
    const val UNLOCK_WPM_FRACTION  = 0.8

    // ── পর্ব ২.৩ ফিচার #২ (যুক্তাক্ষর কারিকুলাম): হসন্ত ঠিক BijoyCurriculum-এর স্টেজ ৫৮-এ
    // আনলক হয় (দেখো BijoyCurriculum.kt) — তার আগে conjunct-cluster টাইপ করা সম্ভবই না
    // (হসন্ত ছাড়া কোনো conjunct লেখা যায় না), তাই এই স্টেজ থেকেই drill-টেক্সটে
    // conjunct বোনা শুরু হয়।
    //
    // ⚠️ গুরুত্বপূর্ণ ডিজাইন-সিদ্ধান্ত: প্রতিটা conjunct (যেমন "ক্ত") নিজে একটা আলাদা
    // multi-character String — কিন্তু TypingKeyStatStore-এর accuracy/correctCount
    // ট্র্যাকিং সবসময় single-Char-ভিত্তিক (দেখো addDeltas(): Map<Char, IntArray>)।
    // তাই এই conjunct-গুলোকে newCharsAt()-এর "নতুন কী" হিসেবে যোগ করা হয়নি — করলে
    // checkAndAdvance()-এ keyStats["ক্ত"] সবসময় null থাকত (কখনো ডেটা জমত না) আর
    // ইউজার ওই স্টেজে চিরকালের জন্য আটকে যেত। এর বদলে conjunct-গুলো শুধু
    // buildDrillPassage()-এর টেক্সট-জেনারেশনে "flavor" হিসেবে বোনা হয় — এগুলোর
    // component ক্যারেক্টার (ব্যঞ্জনবর্ণ + হসন্ত) আলাদাভাবেই ইতিমধ্যে ট্র্যাক হচ্ছে,
    // তাই unlock-সিস্টেম ভাঙে না, অথচ ইউজার আসল conjunct-টাইপিং motor-sequence
    // (দ্রুত পরপর কয়েকটা কী চাপা) প্র্যাকটিস করার সুযোগ পায়। ──
    private const val CONJUNCT_STAGE = 58
    private val COMMON_CONJUNCTS = listOf(
        "ক্ত", "ক্ষ", "ঙ্গ", "ন্ত", "ন্দ", "স্থ", "স্ব", "স্ত", "স্প", "স্ক",
        "স্ম", "ষ্ট", "ণ্ড", "ন্ধ", "ম্প", "ম্ব", "ন্ট", "র্ক", "র্ত", "র্ম",
        "র্ব", "ল্প", "দ্ব", "দ্ধ", "ব্দ", "হ্ম", "জ্ঞ", "ত্ব", "ঞ্চ", "ঞ্জ",
        "স্ন", "শ্ব", "ঙ্ক"
    )

    private fun userId(context: Context): String =
        SessionManager(context).getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"

    suspend fun getCurrentStage(context: Context, track: String): Int {
        val dao = AppDatabase.getInstance(context).curriculumProgressDao()
        return dao.get(userId(context), track)?.currentStage ?: 1
    }

    /** প্লেসমেন্ট-টেস্টের ফলাফল অনুযায়ী সরাসরি একটা স্টেজে বসিয়ে দেয় (normal
     *  checkAndAdvance()-এর unlock-শর্ত এখানে প্রযোজ্য না — এটা শুধু ইউজারের
     *  বিদ্যমান দক্ষতা অনুযায়ী শুরুর বিন্দু ঠিক করার জন্য, এক-লাফে এগিয়ে দিতে) */
    suspend fun setStage(context: Context, track: String, stage: Int) {
        val dao = AppDatabase.getInstance(context).curriculumProgressDao()
        val clamped = stage.coerceIn(1, BijoyCurriculum.totalStages(track))
        dao.upsert(CurriculumProgressEntity(userId(context), track, clamped, System.currentTimeMillis()))
    }

    /** বর্তমান স্টেজ পর্যন্ত আনলক হওয়া সব ক্যারেক্টার দিয়ে একটা সিন্থেটিক প্র্যাকটিস
     *  টেক্সট বানায় — শুরুর স্টেজগুলোতে সাধারণ Sheet-পুলের কনটেন্ট ব্যবহার করা যায় না
     *  (তাতে এখনো-আনলক-না-হওয়া অক্ষরও থাকে), তাই ছোট ছোট এলোমেলো সিলেবল/শব্দ-প্যাটার্ন
     *  জেনারেট করা হয় (Neonlipi-সহ প্রায় সব টাইপিং-টিউটোরিয়ালেই একই পদ্ধতি — শুরুতে
     *  বাস্তব শব্দের বদলে key-drill প্যাটার্নই শেখানো হয়)। */
    fun buildDrillPassage(track: String, stage: Int, wordCount: Int = 12): String {
        val allowed = BijoyCurriculum.allowedCharsUpTo(track, stage).toList()
        if (allowed.isEmpty()) return ""

        // ── নতুন-স্টেজের ক্যারেক্টার একটু বেশি ঘন ঘন আসুক (তাদেরই বেশি প্র্যাকটিস দরকার),
        // পুরনোগুলোও মাঝে মাঝে আসুক (revision) ──
        val newChars = BijoyCurriculum.newCharsAt(track, stage)
        val weighted = allowed + newChars + newChars   // নতুন char দ্বিগুণ ওজন

        // ── পর্ব ২.৩ ফিচার #২: হসন্ত আনলক হয়ে গেলে (stage ≥ CONJUNCT_STAGE), শুধু
        // এলোমেলো একক-অক্ষর সিলেবলের বদলে মাঝে মাঝে বাস্তব যুক্তাক্ষর-ক্লাস্টার বুনে
        // দেওয়া হয় — এটাই আসল conjunct-টাইপিং motor-sequence শেখায় ──
        val conjunctPool = if (track == "bn" && stage >= CONJUNCT_STAGE) COMMON_CONJUNCTS else emptyList()

        val words = (1..wordCount).map {
            if (conjunctPool.isNotEmpty() && (0..2).random() == 0) {   // ~১/৩ শব্দে conjunct
                val core = conjunctPool.random()
                val prefix = if ((0..1).random() == 0) weighted.random() else ""
                val suffix = if ((0..1).random() == 0) weighted.random() else ""
                "$prefix$core$suffix"
            } else {
                val len = (2..4).random()
                (1..len).map { weighted.random() }.joinToString("")
            }
        }
        return words.joinToString(" ")
    }

    /** TypingKeyStatStore-এ প্রতিটা ক্যারেক্টার তার নিজের script অনুযায়ী তিনটা bucket-এর
     *  একটাতে জমা হয় (bn/en/sym — দেখো TypingPracticeScreen.kt-এর script-split লজিক)।
     *  কারিকুলামের bn/en ট্র্যাকেও শেষের দিকে চিহ্ন/punctuation স্টেজ আছে, যেগুলো আসলে
     *  "sym" bucket-এ জমা হয় — তাই এখানে তিনটা bucket-ই একসাথে মার্জ করে লুকআপ করা হয়,
     *  নাহলে চিহ্ন-স্টেজের progress কখনো খুঁজে পাওয়া যেত না। */
    private suspend fun mergedKeyStats(context: Context): Map<String, TypingKeyStatEntityLike> {
        val bn  = TypingKeyStatStore.getHeatmap(context, "bn", limit = 200)
        val en  = TypingKeyStatStore.getHeatmap(context, "en", limit = 200)
        val sym = TypingKeyStatStore.getHeatmap(context, "sym", limit = 200)
        return (bn + en + sym).associate { it.keyChar to TypingKeyStatEntityLike(it.correctCount, it.wrongCount) }
    }

    private data class TypingKeyStatEntityLike(val correctCount: Int, val wrongCount: Int)

    /** সেশন শেষে (finishSession()-এ) কল হয় — বর্তমান স্টেজের নতুন ক্যারেক্টারগুলোর
     *  মধ্যে সবগুলো unlock-শর্ত পূরণ করলে পরের স্টেজে এগিয়ে দেয়, নাহলে কিছু করে না।
     *  রিটার্ন করে নতুন স্টেজ যদি advance হয় (celebration UI দেখানোর জন্য), নাহলে null। */
    suspend fun checkAndAdvance(context: Context, track: String, targetWpm: Int, recentWpm: Int): Int? {
        val dao = AppDatabase.getInstance(context).curriculumProgressDao()
        val uid = userId(context)
        val current = dao.get(uid, track)?.currentStage ?: 1
        if (current >= BijoyCurriculum.totalStages(track)) return null   // ইতিমধ্যে শেষ স্টেজে

        val newChars = BijoyCurriculum.newCharsAt(track, current)
        if (newChars.isEmpty()) return null

        val keyStats = mergedKeyStats(context)

        val allEligible = newChars.all { ch ->
            val stat = keyStats[ch] ?: return@all false
            val total = stat.correctCount + stat.wrongCount
            val acc = if (total > 0) stat.correctCount.toDouble() / total else 0.0
            stat.correctCount >= UNLOCK_MIN_CORRECT && acc >= UNLOCK_MIN_ACCURACY
        }
        val wpmOk = recentWpm >= (targetWpm * UNLOCK_WPM_FRACTION)

        if (!allEligible || !wpmOk) return null

        val nextStage = current + 1
        dao.upsert(CurriculumProgressEntity(uid, track, nextStage, System.currentTimeMillis()))
        return nextStage
    }

    /** স্টেজ-প্রগ্রেস UI-এর জন্য — এই স্টেজের প্রতিটা নতুন ক্যারেক্টারের বর্তমান
     *  correctCount/UNLOCK_MIN_CORRECT অনুপাত (progress bar আঁকতে) */
    suspend fun stageProgress(context: Context, track: String, stage: Int): List<Pair<String, Int>> {
        val newChars = BijoyCurriculum.newCharsAt(track, stage)
        if (newChars.isEmpty()) return emptyList()
        val keyStats = mergedKeyStats(context)
        return newChars.map { ch -> ch to (keyStats[ch]?.correctCount ?: 0).coerceAtMost(UNLOCK_MIN_CORRECT) }
    }
}
