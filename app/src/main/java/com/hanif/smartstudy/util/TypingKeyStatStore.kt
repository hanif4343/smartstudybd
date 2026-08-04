package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingKeyPairStatEntity
import com.hanif.smartstudy.data.local.TypingKeyStatEntity
import kotlin.math.sqrt

/**
 * প্রতিটা কী-এর সঠিক/ভুল কীপ্রেস গণনা persist ও query করার জায়গা — TypingPracticeScreen
 * সেশন চলাকালীন RAM-এ (Map<Char, IntArray>) জমায়, সেশন শেষে (finishSession()/
 * finishExamPhase()) একবারে [addDeltas] কল করে batch-persist করে (ঠিক
 * TypingHandStatsDao.addSessionDelta()-এর মতোই প্যাটার্ন)।
 *
 * এটাই লাইভ হিটম্যাপ (KeyHeatmapCard) ও দুর্বল-কী ড্রিলের (startKeyDrillSession())
 * ডেটা-সোর্স — Neonlipi-এর "প্রতিটা কী-এর accuracy ট্র্যাক" ফিচারের সমতুল্য।
 *
 * [addLatencyDeltas]/[addPairDeltas]/[getKeyAnalysis] — Key Analysis কার্ড ফিচার
 * (দ্বিধা/স্থিরতা/ধীর জুটি), একই ব্যাচ-প্যাটার্নে সেশন শেষে persist হয়।
 */
object TypingKeyStatStore {

    /** একটা কী-এর latency নমুনাগুলোর যোগফল — সেশন চলাকালীন RAM-এ জমে, শেষে flush হয় */
    data class LatencyAgg(var sumMs: Long = 0L, var sumSqMs: Double = 0.0, var count: Int = 0)

    /** একটা bigram (আগের কী → এই কী)-এর latency যোগফল */
    data class PairAgg(var sumMs: Long = 0L, var count: Int = 0)

    /** Key Analysis কার্ডে দেখানোর জন্য একটা কী-এর সব তথ্য একসাথে */
    data class KeyAnalysis(
        val keyChar: String,
        val correctCount: Int,
        val wrongCount: Int,
        val avgLatencyMs: Long?,      // দ্বিধা — যথেষ্ট নমুনা না থাকলে null
        val stdDevLatencyMs: Long?,   // স্থিরতা (±ms)
        val slowestPairLabel: String?,// "ির" এর মতো — যথেষ্ট নমুনা না থাকলে null
        val slowestPairMs: Long?
    ) {
        val practiceCount: Int get() = correctCount + wrongCount
        val accuracyPct: Int get() = if (practiceCount == 0) 0 else (correctCount * 100) / practiceCount
        /** গড় ইন্টার-কী ল্যাটেন্সি থেকে আনুমানিক WPM (১ শব্দ ≈ ৫ ক্যারেক্টার ধরে) */
        val speedWpm: Int get() = avgLatencyMs?.takeIf { it > 0 }?.let { (60000.0 / (it * 5)).toInt() } ?: 0
    }

    private fun currentUserId(context: Context): String =
        SessionManager(context).getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"

    /** deltas: ক্যারেক্টার → [correctDelta, wrongDelta] */
    suspend fun addDeltas(context: Context, language: String, deltas: Map<Char, IntArray>) {
        if (deltas.isEmpty()) return
        val dao = AppDatabase.getInstance(context).typingKeyStatDao()
        val userId = currentUserId(context)
        for ((ch, delta) in deltas) {
            val key = ch.toString()
            val existing = dao.find(userId, key, language)
            dao.upsert(
                TypingKeyStatEntity(
                    userId       = userId,
                    keyChar      = key,
                    language     = language,
                    correctCount = (existing?.correctCount ?: 0) + delta[0],
                    wrongCount   = (existing?.wrongCount ?: 0) + delta[1],
                    totalLatencyMs = existing?.totalLatencyMs ?: 0L,
                    latencySumSqMs = existing?.latencySumSqMs ?: 0.0,
                    latencySamples = existing?.latencySamples ?: 0
                )
            )
        }
    }

    /** deltas: ক্যারেক্টার → LatencyAgg (এই সেশনে জমা হওয়া দেরির নমুনা) — cumulative
     *  sum/sumSq/count-এর সাথে যোগ হয়ে যায়, যাতে সময়ের সাথে গড়/stddev আরও নির্ভুল হয় */
    suspend fun addLatencyDeltas(context: Context, language: String, deltas: Map<Char, LatencyAgg>) {
        if (deltas.isEmpty()) return
        val dao = AppDatabase.getInstance(context).typingKeyStatDao()
        val userId = currentUserId(context)
        for ((ch, agg) in deltas) {
            val key = ch.toString()
            val existing = dao.find(userId, key, language)
            dao.upsert(
                TypingKeyStatEntity(
                    userId         = userId,
                    keyChar        = key,
                    language       = language,
                    correctCount   = existing?.correctCount ?: 0,
                    wrongCount     = existing?.wrongCount ?: 0,
                    totalLatencyMs = (existing?.totalLatencyMs ?: 0L) + agg.sumMs,
                    latencySumSqMs = (existing?.latencySumSqMs ?: 0.0) + agg.sumSqMs,
                    latencySamples = (existing?.latencySamples ?: 0) + agg.count
                )
            )
        }
    }

    /** deltas: (আগের কী, এই কী) → PairAgg */
    suspend fun addPairDeltas(context: Context, language: String, deltas: Map<Pair<Char, Char>, PairAgg>) {
        if (deltas.isEmpty()) return
        val dao = AppDatabase.getInstance(context).typingKeyPairStatDao()
        val userId = currentUserId(context)
        for ((pair, agg) in deltas) {
            val (from, to) = pair
            val fromKey = from.toString(); val toKey = to.toString()
            val existing = dao.find(userId, fromKey, toKey, language)
            dao.upsert(
                TypingKeyPairStatEntity(
                    userId   = userId,
                    fromChar = fromKey,
                    toChar   = toKey,
                    language = language,
                    totalMs  = (existing?.totalMs ?: 0L) + agg.sumMs,
                    count    = (existing?.count ?: 0) + agg.count
                )
            )
        }
    }

    /** হিটম্যাপের জন্য — সবচেয়ে বেশি প্র্যাকটিস হওয়া কী আগে */
    suspend fun getHeatmap(context: Context, language: String, limit: Int = 16): List<TypingKeyStatEntity> =
        AppDatabase.getInstance(context).typingKeyStatDao()
            .getMostPracticed(currentUserId(context), language, limit)

    /** দুর্বল-কী ড্রিলের জন্য — যথেষ্ট নমুনা আছে এমন সবচেয়ে কম accuracy-র কী */
    suspend fun getWeakest(
        context: Context, language: String, minSamples: Int = 10, limit: Int = 6
    ): List<TypingKeyStatEntity> =
        AppDatabase.getInstance(context).typingKeyStatDao()
            .getWeakest(currentUserId(context), language, minSamples, limit)

    /** Key Analysis কার্ডের জন্য — সবচেয়ে বেশি প্র্যাকটিস হওয়া কী-গুলো, প্রতিটার সাথে
     *  দ্বিধা/স্থিরতা/ধীর জুটি জুড়ে দেওয়া হয় ──*/
    suspend fun getKeyAnalysis(context: Context, language: String, limit: Int = 16): List<KeyAnalysis> {
        val db = AppDatabase.getInstance(context)
        val userId = currentUserId(context)
        val rows = db.typingKeyStatDao().getMostPracticed(userId, language, limit)
        return rows.map { row ->
            val n = row.latencySamples
            val avg = if (n > 0) row.totalLatencyMs / n else null
            val stdDev = if (n > 1) {
                val mean = row.totalLatencyMs.toDouble() / n
                val variance = (row.latencySumSqMs / n) - (mean * mean)
                if (variance > 0) sqrt(variance).toLong() else 0L
            } else null
            val pair = db.typingKeyPairStatDao().getSlowestPairFor(userId, row.keyChar, language)
            KeyAnalysis(
                keyChar          = row.keyChar,
                correctCount     = row.correctCount,
                wrongCount       = row.wrongCount,
                avgLatencyMs     = avg,
                stdDevLatencyMs  = stdDev,
                slowestPairLabel = pair?.let { it.fromChar + it.toChar },
                slowestPairMs    = pair?.let { if (it.count > 0) it.totalMs / it.count else null }
            )
        }
    }

    /** পর্ব ২.৩ ফিচার #১: সবচেয়ে ধীর N-টা bigram (জুটি) — "🎯 ধীর জুটি প্র্যাকটিস"
     *  বাটনের ডেটা-সোর্স। প্রতিটা এন্ট্রি (fromChar, toChar, avgMs)। */
    suspend fun getSlowestPairsGlobal(
        context: Context, language: String, minCount: Int = 3, limit: Int = 6
    ): List<Triple<String, String, Long>> {
        val db = AppDatabase.getInstance(context)
        val userId = currentUserId(context)
        return db.typingKeyPairStatDao().getSlowestPairs(userId, language, minCount, limit)
            .map { Triple(it.fromChar, it.toChar, if (it.count > 0) it.totalMs / it.count else 0L) }
    }
}
