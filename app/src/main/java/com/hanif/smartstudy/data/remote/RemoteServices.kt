package com.hanif.smartstudy.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.viewmodel.ActiveUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

// ─────────────────────────────────────────────────────────
// ImgBB Upload Service
// ─────────────────────────────────────────────────────────
object ImgBBService {

    private const val TAG = "ImgBB"
    private val API_KEY get() = BuildConfig.IMGBB_API_KEY

    suspend fun upload(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val body = FormBody.Builder()
                .add("key",   API_KEY)
                .add("image", b64)
                .build()
            val req  = Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(body)
                .build()
            val resp = client.newCall(req).execute()
            val json = resp.body?.string() ?: return@withContext null
            Log.d(TAG, "Response: $json")
            val obj  = JSONObject(json)
            if (obj.optBoolean("success")) {
                obj.getJSONObject("data").getString("url")
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "upload error: ${e.message}")
            null
        }
    }
}

// ─────────────────────────────────────────────────────────
// User Sync Service — GAS endpoint দিয়ে Firebase update
// ─────────────────────────────────────────────────────────
object UserSyncService {

    private val TAG     = "UserSync"
    private val GAS_URL get() = BuildConfig.GAS_URL
    private val FB_URL  get() = BuildConfig.FIREBASE_URL
    private val gson    = Gson()

    // ── Profile picture update ──
    suspend fun updatePicture(phone: String, url: String): Boolean = gasPost(mapOf(
        "action"  to "updateUser",
        "phone"   to phone,
        "field"   to "Picture",
        "value"   to url
    ))

    // ── Name update ──
    suspend fun updateName(phone: String, name: String): Boolean = gasPost(mapOf(
        "action"  to "updateUser",
        "phone"   to phone,
        "field"   to "Name",
        "value"   to name
    ))

    // ── FCM token save ──
    suspend fun saveFcmToken(phone: String, token: String): Boolean = gasPost(mapOf(
        "action"   to "saveFcmToken",
        "phone"    to phone,
        "fcmToken" to token
    ))

    // ── Report app activity (active/inactive) ──
    suspend fun reportActivity(phone: String, isActive: Boolean): Boolean = gasPost(mapOf(
        "action"   to "reportActivity",
        "phone"    to phone,
        "isActive" to isActive.toString(),
        "ts"       to System.currentTimeMillis().toString()
    ))

    // ── Fetch active users (Admin) ──
    suspend fun fetchActiveUsers(): List<ActiveUser> = withContext(Dispatchers.IO) {
        try {
            val url  = "$FB_URL/ActivityLog.json"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext emptyList()
            if (body == "null" || body.isBlank()) return@withContext emptyList()
            val obj  = JSONObject(body)
            val now  = System.currentTimeMillis()
            val list = mutableListOf<ActiveUser>()
            obj.keys().forEach { phone ->
                val entry = obj.getJSONObject(phone)
                val lastSeen = entry.optLong("ts", 0L)
                val isOnline = entry.optBoolean("isActive", false) &&
                        (now - lastSeen) < 5 * 60 * 1000L // 5 min window
                list.add(ActiveUser(
                    phone    = phone,
                    name     = entry.optString("name", phone),
                    lastSeen = lastSeen,
                    isOnline = isOnline,
                    fcmToken = entry.optString("fcmToken", "")
                ))
            }
            list.sortedByDescending { it.lastSeen }
        } catch (e: Exception) {
            Log.e(TAG, "fetchActiveUsers: ${e.message}")
            emptyList()
        }
    }

    // ── Fetch single user from Firebase ──
    // Firebase এ user গুলো numeric key (1, 2, 3...) তে আছে
    // তাই phone number দিয়ে query করতে হবে
    // path: users.json?orderBy="Phone"&equalTo="01729814214"
    suspend fun fetchUser(phone: String): com.hanif.smartstudy.data.model.User? =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.trim()
            try {
                // Method 1: Phone field দিয়ে query (Firebase index লাগবে)
                val encodedPhone = java.net.URLEncoder.encode("\"$cleanPhone\"", "UTF-8")
                val url = "$FB_URL/users.json?orderBy=%22Phone%22&equalTo=%22$cleanPhone%22"
                Log.d(TAG, "fetchUser query: $url")
                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string() ?: return@withContext null
                Log.d(TAG, "fetchUser response: $body")
                if (body == "null" || body.isBlank() || body == "{}") {
                    // Method 2: সব user নিয়ে phone দিয়ে filter
                    return@withContext fetchUserByScan(cleanPhone)
                }
                // response হলো { "2": { ...userData... } } — value টা নাও
                val rootMap = gson.fromJson(body, Map::class.java) as? Map<String, Any>
                    ?: return@withContext null
                val userMap = rootMap.values.firstOrNull() as? Map<String, Any>
                    ?: return@withContext null
                com.hanif.smartstudy.data.model.User.fromFirebaseMap(userMap)
            } catch (e: Exception) {
                Log.e(TAG, "fetchUser error: ${e.message}")
                // fallback: scan করো
                try { fetchUserByScan(cleanPhone) } catch (e2: Exception) { null }
            }
        }

    // Fallback: সব user scan করে phone match করো
    private suspend fun fetchUserByScan(phone: String): com.hanif.smartstudy.data.model.User? =
        withContext(Dispatchers.IO) {
            try {
                val url  = "$FB_URL/users.json?shallow=false"
                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string() ?: return@withContext null
                if (body == "null" || body.isBlank()) return@withContext null
                val rootMap = gson.fromJson(body, Map::class.java) as? Map<String, Any>
                    ?: return@withContext null
                // প্রতিটা entry তে Phone field check করো
                for ((_, value) in rootMap) {
                    val userMap = value as? Map<String, Any> ?: continue
                    val p = userMap["Phone"]?.toString()?.trim()
                        ?: userMap["phone"]?.toString()?.trim() ?: continue
                    if (p == phone) {
                        Log.d(TAG, "fetchUserByScan: found user for $phone")
                        return@withContext com.hanif.smartstudy.data.model.User.fromFirebaseMap(userMap)
                    }
                }
                Log.d(TAG, "fetchUserByScan: no user found for $phone")
                null
            } catch (e: Exception) {
                Log.e(TAG, "fetchUserByScan error: ${e.message}")
                null
            }
        }

    // ── Admin FCM broadcast / targeted notification ──
    suspend fun sendAdminNotification(title: String, body: String, targetPhone: String?): Boolean =
        gasPost(mapOf(
            "action"      to "adminNotify",
            "title"       to title,
            "body"        to body,
            "targetPhone" to (targetPhone ?: "ALL")
        ))

    // ── Leaderboard fetch ──
    suspend fun fetchLeaderboard(): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        try {
            val url  = "$FB_URL/users.json?orderBy=\"XP\"&limitToLast=20"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext emptyList()
            if (body == "null" || body.isBlank()) return@withContext emptyList()
            val obj  = JSONObject(body)
            val list = mutableListOf<LeaderboardEntry>()
            obj.keys().forEach { key ->
                val u = obj.getJSONObject(key)
                list.add(LeaderboardEntry(
                    phone = key,
                    name  = u.optString("Name", u.optString("name", key)),
                    xp    = u.optInt("XP", u.optInt("xp", 0)),
                    pic   = u.optString("Picture", "")
                ))
            }
            list.sortedByDescending { it.xp }
        } catch (e: Exception) {
            Log.e(TAG, "leaderboard: ${e.message}")
            emptyList()
        }
    }

    // ── GAS POST helper ──
    private suspend fun gasPost(params: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val builder = FormBody.Builder()
                params.forEach { (k, v) -> builder.add(k, v) }
                val req  = Request.Builder().url(GAS_URL).post(builder.build()).build()
                val resp = client.newCall(req).execute()
                resp.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "gasPost: ${e.message}")
                false
            }
        }
}

data class LeaderboardEntry(
    val phone : String,
    val name  : String,
    val xp    : Int,
    val pic   : String = ""
)
