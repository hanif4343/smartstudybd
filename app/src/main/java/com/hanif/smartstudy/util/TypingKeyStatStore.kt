package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingKeyStatEntity

/**
 * প্রতিটা কী-এর সঠিক/ভুল কীপ্রেস গণনা persist ও query করার জায়গা — TypingPracticeScreen
 * সেশন চলাকালীন RAM-এ (Map<Char, IntArray>) জমায়, সেশন শেষে (finishSession()/
 * finishExamPhase()) একবারে [addDeltas] কল করে batch-persist করে (ঠিক
 * TypingHandStatsDao.addSessionDelta()-এর মতোই প্যাটার্ন)।
 *
 * এটাই লাইভ হিটম্যাপ (KeyHeatmapCard) ও দুর্বল-কী ড্রিলের (startKeyDrillSession())
 * ডেটা-সোর্স — Neonlipi-এর "প্রতিটা কী-এর accuracy ট্র্যাক" ফিচারের সমতুল্য।
 */
object TypingKeyStatStore {

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
                    wrongCount   = (existing?.wrongCount ?: 0) + delta[1]
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
}
