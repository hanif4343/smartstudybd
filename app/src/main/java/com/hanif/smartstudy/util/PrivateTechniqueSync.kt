package com.hanif.smartstudy.util

import android.content.Context
import com.hanif.smartstudy.data.local.LocalTechniqueStore
import com.hanif.smartstudy.data.remote.ApiResult
import com.hanif.smartstudy.data.remote.FirebaseDataService

/**
 * প্রাইভেট টেকনিক Firebase-এ ব্যাকআপ — "আনইনস্টল করলে হারিয়ে যায়" সমস্যার ফিক্স।
 *
 * ফোনে (LocalTechniqueStore) টেকনিক-টা তখনই তোলা হয়, তখনই দেখা যায় (net লাগে না, আগের
 * আচরণ অপরিবর্তিত)। এই ফাংশনটা তারপর ব্যাকগ্রাউন্ডে জমে থাকা সব un-synced টেকনিক
 * **একটা মাত্র batch request**-এ Firebase-এ পাঠায় (status="approved", admin-approval
 * ছাড়াই) — একে একে আলাদা রিকোয়েস্ট পাঠিয়ে quota খরচ করা হয় না (দেখো
 * FirebaseDataService.saveTechniquesBatch)। id client-এ আগে থেকেই বানানো থাকে
 * (LocalTechniqueStore.add), sync-এর পরও id বদলায় না — শুধু synced flag true হয়।
 *
 * উদ্দেশ্য:
 *   - app আনইনস্টল/রিইনস্টল করলে, বা অন্য ফোনে লগইন করলে, নিজের ফোন-নম্বর দিয়ে টেকনিকগুলো
 *     আবার ফেরত পাওয়া যায় (দেখো FirebaseDataService.fetchTechniquesForQuestion)
 *
 * এই ফাংশনটা দুই জায়গা থেকে কল হয় (দুটোই idempotent, নিরাপদে বারবার চালানো যায়):
 *   ১) app চালু/login হওয়ার পর, নেট থাকলে সাথে সাথে (দেখো ui/main/MainScreen.kt)
 *   ২) SyncWorker-এর প্রতিটা রানে (অফলাইনে যোগ করা টেকনিক পরে নেট এলে চলে যায়) — এই
 *      দুই ট্রিগারের মধ্যে যতগুলো টেকনিক জমা হয়, সবগুলো একসাথে একটা ব্যাচেই যায়, তাই
 *      সারাদিন ধরে একটার পর একটা প্রশ্নে টেকনিক যোগ করলেও Firebase-এ বারবার আলাদা
 *      রিকোয়েস্ট যায় না — SyncWorker-এর ExistingWorkPolicy.REPLACE স্বয়ংক্রিয়ভাবেই
 *      ঘন ঘন ট্রিগার একসাথে মিলিয়ে দেয়।
 */
object PrivateTechniqueSync {

    suspend fun syncNow(context: Context, userId: String, userName: String): Boolean {
        if (userId.isBlank()) return true
        val pending = LocalTechniqueStore.getUnsynced(context, userId)
        if (pending.isEmpty()) return true

        return when (val r = FirebaseDataService.saveTechniquesBatch(pending)) {
            is ApiResult.Success -> {
                LocalTechniqueStore.markAllSynced(context, pending.map { it.id }.toSet())
                true
            }
            is ApiResult.Error -> false
        }
    }
}
