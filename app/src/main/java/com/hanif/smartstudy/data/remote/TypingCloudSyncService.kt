package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * টাইপিং প্রগ্রেস (বেস্ট WPM + সাম্প্রতিক হিস্ট্রি) ফোন/কম্পিউটার — সব ডিভাইসে
 * এক রাখার জন্য Firebase Realtime Database-এ push/pull করে (Neonlipi-এর
 * "Cloud Sync — Google account দিয়ে লগইন করলে সব ডিভাইসে progress sync" ফিচারের
 * সমতুল্য)। আগে টাইপিং হিস্ট্রি শুধু লোকাল DataStore-এ থাকত (device-only)।
 *
 * ডেটা রাখা হয় Users/{phone}/typingCloudSync-এ — ঠিক FirebaseAuthService-এর মতোই
 * phone-কী ব্যবহার করে (guest ইউজারের জন্য sync হয় না, স্থায়ী পরিচয় নেই বলে)।
 */
object TypingCloudSyncService {
    private const val TAG = "TypingCloudSync"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private suspend fun authQuery(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    data class CloudSnapshot(
        val bestWpm  : Int = 0,
        val history  : List<Map<String, Any>> = emptyList(),
        val updatedAt: Long = 0L
    )

    /** সেশন শেষে (finishSession()/finishExamPhase()) স্থানীয়ভাবে সেভ করার পাশাপাশি
     *  cloud-এও push করে — ব্যর্থ হলেও local persist-কে প্রভাবিত করে না (silent fail)। */
    suspend fun push(phone: String, bestWpm: Int, history: List<Map<String, Any>>) =
        withContext(Dispatchers.IO) {
            if (phone.isBlank()) return@withContext
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/Users/$phone/typingCloudSync.json$auth"
                val payload = mapOf(
                    "bestWpm"   to bestWpm,
                    "history"   to history,
                    "updatedAt" to System.currentTimeMillis()
                )
                val body = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
                client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
            } catch (e: Exception) {
                Log.w(TAG, "push failed: ${e.message}")
            }
        }

    /** অ্যাপ খোলার সময় বা প্রোফাইল পেজে টান দিলে (pull-to-refresh) কল হয় — cloud-এ
     *  কিছু না থাকলে/fetch ব্যর্থ হলে null রিটার্ন করে, caller তখন শুধু লোকাল ডেটাই দেখায়। */
    suspend fun pull(phone: String): CloudSnapshot? = withContext(Dispatchers.IO) {
        if (phone.isBlank()) return@withContext null
        try {
            val auth = authQuery()
            val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/Users/$phone/typingCloudSync.json$auth"
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val bodyStr = resp.body?.string()
            resp.close()
            if (bodyStr.isNullOrBlank() || bodyStr == "null") return@withContext null

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val raw: Map<String, Any> = gson.fromJson(bodyStr, type) ?: return@withContext null
            val best = (raw["bestWpm"] as? Double)?.toInt() ?: 0
            val hist = (raw["history"] as? List<Map<String, Any>>) ?: emptyList()
            val updated = (raw["updatedAt"] as? Double)?.toLong() ?: 0L
            CloudSnapshot(bestWpm = best, history = hist, updatedAt = updated)
        } catch (e: Exception) {
            Log.w(TAG, "pull failed: ${e.message}")
            null
        }
    }
}
