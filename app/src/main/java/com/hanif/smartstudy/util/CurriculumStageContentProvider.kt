package com.hanif.smartstudy.util

import android.content.Context
import android.util.Log
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TypingCurriculumStageContentEntity
import com.hanif.smartstudy.data.model.TypingSheetStageContent
import com.hanif.smartstudy.data.remote.GasContentService

/**
 * Smart Typing-এর কারিকুলাম-স্টেজের admin-curated প্র্যাকটিস-কনটেন্ট — Google Sheet
 * "CurriculumStages" ট্যাব (headers: id, track, stage, content, updatedAt) থেকে।
 * TypingPassageProvider.kt-এর হুবহু একই ক্যাশিং-প্যাটার্ন (RAM → fresh fetch → Room
 * অফলাইন cache fallback)।
 *
 * এই provider-টা "override" হিসেবে কাজ করে — CurriculumProvider.buildDrillPassageSmart()
 * আগে এখানে চেক করে (নির্দিষ্ট track+stage-এ admin-curated কনটেন্ট আছে কিনা), থাকলে
 * সেখান থেকে এলোমেলোভাবে একটা বেছে নেয়; না থাকলে আগের মতোই সিন্থেটিক
 * জেনারেশনে (buildDrillPassage) ফিরে যায় — তাই যেসব স্টেজে এখনো এডমিন কিছু
 * বসাননি, সেগুলো ভাঙে না।
 */
object CurriculumStageContentProvider {
    private const val TAG = "CurriculumStageContentProvider"

    @Volatile private var ramCache: Map<String, List<String>>? = null

    private fun key(track: String, stage: Int) = "$track:$stage"

    /** track+stage-এ এডমিন যতগুলো ভ্যারিয়েন্ট দিয়েছে সবগুলো — খালি হলে caller
     *  (CurriculumProvider.buildDrillPassageSmart) সিন্থেটিক জেনারেশনে ফিরে যায়। */
    suspend fun getStageContent(context: Context, track: String, stage: Int): List<String> {
        val all = getAllGrouped(context)
        return all[key(track, stage)] ?: emptyList()
    }

    private suspend fun getAllGrouped(context: Context): Map<String, List<String>> {
        ramCache?.let { return it }

        val dao = AppDatabase.getInstance(context).typingCurriculumStageContentDao()

        val fresh: List<TypingSheetStageContent> = try {
            GasContentService.fetchCurriculumStageContent()
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: ${e.message}")
            emptyList()
        }

        val rows: List<TypingSheetStageContent> = if (fresh.isNotEmpty()) {
            try {
                dao.replaceAll(fresh.map {
                    TypingCurriculumStageContentEntity(
                        id = it.id, track = it.track, stage = it.stage,
                        content = it.content, updatedAt = it.updatedAt
                    )
                })
            } catch (e: Exception) {
                Log.w(TAG, "cache write failed: ${e.message}")
            }
            fresh
        } else {
            try {
                dao.getAll().map {
                    TypingSheetStageContent(it.id, it.track, it.stage, it.content, it.updatedAt)
                }
            } catch (e: Exception) {
                Log.w(TAG, "cache read failed: ${e.message}")
                emptyList()
            }
        }

        val grouped = rows.filter { it.content.isNotBlank() && it.stageInt() != null }
            .groupBy({ key(it.track, it.stageInt()!!) }, { it.content })

        if (rows.isNotEmpty()) { ramCache = grouped }
        return grouped
    }

    /** এডমিন নতুন কনটেন্ট সাবমিট করার পর কল করো (দেখো CurriculumStageAdminScreen.kt) —
     *  পরের getStageContent() পুরনো RAM cache না ব্যবহার করে ফ্রেশ fetch করবে, তাই
     *  এডমিন সাথে সাথে নিজের পরিবর্তন যাচাই করতে পারবে (অ্যাপ রিস্টার্ট ছাড়াই)। */
    fun forceRefreshNextTime() {
        ramCache = null
    }
}
