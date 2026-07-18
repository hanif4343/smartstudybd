package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.GeneratedPassageCacheEntity
import com.hanif.smartstudy.data.remote.TypingAiService
import com.hanif.smartstudy.ui.typing.fallbackPassageFor

/**
 * AI Adaptive Session-এর দ্বিতীয় ধাপের জন্য প্যাসেজ জোগাড় করার একমাত্র entry point।
 * ক্রম: (১) সাম্প্রতিক cache আছে কিনা → থাকলে সেটাই, নতুন API call লাগে না
 *       (২) না থাকলে live AI endpoint কল (Cloudflare Worker)
 *       (৩) সেটাও ব্যর্থ হলে (নেট নেই/সব provider down) local static pool থেকে fallback
 *
 * cache ব্যবহারের কারণ: ইউজার নিজেই পয়েন্ট করেছিলেন — একই ভুল-শব্দ বারবার এলে
 * প্রতিবার fresh API call করা অপ্রয়োজনীয় (rate-limit/খরচ দুটোই বাড়ায়)।
 */
object TypingAdaptiveContentProvider {

    // একই দুর্বল-শব্দ সেটের জন্য এই সময়ের মধ্যে আবার API call করা হবে না
    private const val CACHE_VALID_MS = 6 * 3_600_000L // ৬ ঘণ্টা

    sealed class Source { object Cache : Source(); object LiveAi : Source(); object Fallback : Source() }
    data class Result(val passage: String, val source: Source)

    private fun cacheKey(language: String, weakWords: List<String>): String =
        language + ":" + weakWords.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")

    suspend fun getBlendedPassage(
        context   : Context,
        weakWords : List<String>,
        language  : String,
        difficulty: String = "medium"
    ): Result {
        if (weakWords.isEmpty()) {
            return Result(fallbackPassageFor(language), Source.Fallback)
        }

        val dao = AppDatabase.getInstance(context).generatedPassageCacheDao()
        val key = cacheKey(language, weakWords)

        // ১) cache — একই শব্দ-সেটের জন্য সাম্প্রতিক জেনারেশন থাকলে সেটাই ব্যবহার
        val cached = dao.get(key)
        if (cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_VALID_MS) {
            return Result(cached.passage, Source.Cache)
        }

        // ২) live AI call
        val aiResult = TypingAiService.generatePassage(weakWords, language, difficulty)
        if (aiResult != null) {
            dao.upsert(
                GeneratedPassageCacheEntity(
                    cacheKey  = key,
                    passage   = aiResult.passage,
                    provider  = aiResult.provider,
                    createdAt = System.currentTimeMillis()
                )
            )
            return Result(aiResult.passage, Source.LiveAi)
        }

        // ৩) সবশেষে local fallback — পুরনো cache থাকলে (এমনকি expire হয়ে গেলেও) সেটাই
        // fresh fallback-এর চেয়ে ভালো (অন্তত personalized ছিল একসময়)
        if (cached != null) return Result(cached.passage, Source.Cache)
        return Result(fallbackPassageFor(language), Source.Fallback)
    }
}
