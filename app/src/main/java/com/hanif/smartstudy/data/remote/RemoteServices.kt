package com.hanif.smartstudy.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import com.hanif.smartstudy.viewmodel.ActiveUser
import com.hanif.smartstudy.viewmodel.DebugLogEntry
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
    private val FB_URL  get() = BuildConfig.FIREBASE_URL.trimEnd('/')

    private suspend fun authQuery(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }
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
    // Activity/presence ডাটা আছে "users" (lowercase) node এ, phone-key দিয়ে
    // (fcmToken, lastActive, lastSeen, totalAppMinutes)। নাম পাওয়া যাবে
    // "Users" (capital, profile) node থেকে।
    suspend fun fetchActiveUsers(): List<ActiveUser> = withContext(Dispatchers.IO) {
        try {
            val fbAuth = authQuery()

            // ১) প্রোফাইল node থেকে phone -> name ম্যাপ বানাও
            val nameMap = mutableMapOf<String, String>()
            try {
                val profUrl  = "$FB_URL/Users.json$fbAuth"
                val profReq  = Request.Builder().url(profUrl).get().build()
                val profBody = client.newCall(profReq).execute().body?.string()
                if (!profBody.isNullOrBlank() && profBody != "null") {
                    val profObj = JSONObject(profBody)
                    profObj.keys().forEach { key ->
                        val u = profObj.optJSONObject(key) ?: return@forEach
                        val phone = u.optString("Phone", u.optString("phone", "")).trim()
                        val name  = u.optString("Name", u.optString("name", ""))
                        if (phone.isNotBlank()) {
                            nameMap[phone] = name.ifBlank { phone }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchActiveUsers (names): ${e.message}")
            }

            // ২) presence/activity node ("users", lowercase)
            val url  = "$FB_URL/users.json$fbAuth"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext emptyList()
            if (body == "null" || body.isBlank()) return@withContext emptyList()

            val obj  = JSONObject(body)
            val now  = System.currentTimeMillis()
            val list = mutableListOf<ActiveUser>()
            obj.keys().forEach { phone ->
                val entry = obj.optJSONObject(phone) ?: return@forEach
                val lastSeen   = entry.optLong("lastSeen", 0L)
                val lastActive = entry.optLong("lastActive", lastSeen)
                val isOnline   = (now - lastActive) < 5 * 60 * 1000L // 5 min window
                list.add(ActiveUser(
                    phone    = phone,
                    name     = nameMap[phone] ?: phone,
                    lastSeen = if (lastSeen > 0L) lastSeen else lastActive,
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

    // ── Fetch all users (for challenge friend dropdown) ──
    suspend fun fetchAllUsers(excludePhone: String): List<com.hanif.smartstudy.data.model.User> =
        withContext(Dispatchers.IO) {
            try {
                val fbAuth = authQuery()
                val url  = "$FB_URL/Users.json$fbAuth"
                val req  = Request.Builder().url(url).get().build()
                val body = client.newCall(req).execute().body?.string()
                if (body.isNullOrBlank() || body == "null") return@withContext emptyList()

                val rootMap = gson.fromJson(body, Map::class.java) as? Map<String, Any> ?: return@withContext emptyList()
                rootMap.values.mapNotNull { value ->
                    val userMap = value as? Map<String, Any> ?: return@mapNotNull null
                    val u = com.hanif.smartstudy.data.model.User.fromFirebaseMap(userMap)
                    if (u.phone.isNullOrBlank() || u.phone == excludePhone) null else u
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAllUsers: ${e.message}")
                emptyList()
            }
        }

    // ── Fetch single user from Firebase ──
    // Firebase এ user গুলো numeric key (1, 2, 3...) তে আছে
    // Phone field দিয়ে query অথবা scan করে খুঁজবো
    suspend fun fetchUser(phone: String): com.hanif.smartstudy.data.model.User? =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.trim()
            Log.d(TAG, "fetchUser: phone=$cleanPhone")

            // "users" এবং "Users" দুটো path-ই try করবো
            for (node in listOf("users", "Users")) {
                try {
                    // Method 1: orderBy query
                    val fbAuth = authQuery()
                    val url = "$FB_URL/$node.json?orderBy=%22Phone%22&equalTo=%22$cleanPhone%22&auth=${FirebaseTokenProvider.getToken()}"
                    Log.d(TAG, "fetchUser trying: $url")
                    val req  = Request.Builder().url(url).get().build()
                    val body = client.newCall(req).execute().body?.string() ?: continue
                    Log.d(TAG, "fetchUser $node response: $body")

                    if (body != "null" && body.isNotBlank() && body != "{}") {
                        val rootMap = gson.fromJson(body, Map::class.java) as? Map<String, Any>
                        if (!rootMap.isNullOrEmpty()) {
                            val userMap = rootMap.values.firstOrNull() as? Map<String, Any>
                            if (userMap != null) {
                                Log.d(TAG, "fetchUser: found in $node via query")
                                return@withContext com.hanif.smartstudy.data.model.User.fromFirebaseMap(userMap)
                            }
                        }
                    }

                    // Method 2: সব user scan করো
                    val scanAuth = authQuery()
                    val scanUrl = "$FB_URL/$node.json$scanAuth"
                    val scanReq  = Request.Builder().url(scanUrl).get().build()
                    val scanBody = client.newCall(scanReq).execute().body?.string() ?: continue
                    Log.d(TAG, "fetchUserScan $node: length=${scanBody.length}")

                    if (scanBody == "null" || scanBody.isBlank()) continue
                    val allMap = gson.fromJson(scanBody, Map::class.java) as? Map<String, Any> ?: continue

                    for ((_, value) in allMap) {
                        val userMap = value as? Map<String, Any> ?: continue
                        val p = userMap["Phone"]?.toString()?.trim()
                            ?: userMap["phone"]?.toString()?.trim() ?: continue
                        if (p == cleanPhone) {
                            Log.d(TAG, "fetchUser: found in $node via scan")
                            return@withContext com.hanif.smartstudy.data.model.User.fromFirebaseMap(userMap)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "fetchUser error for $node: ${e.message}")
                }
            }

            Log.w(TAG, "fetchUser: user not found for $cleanPhone")
            null
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
            val fbAuth = authQuery()
            val url  = "$FB_URL/users.json?orderBy=\"XP\"&limitToLast=20&auth=${FirebaseTokenProvider.getToken()}"
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

    // ── Fetch debug logs (Admin) ──
    // path: DebugLogs/{phone}/{date}/{key} -> {ts, level, tag, msg, phone}
    suspend fun fetchDebugLogPhones(): List<String> = withContext(Dispatchers.IO) {
        try {
            val fbAuth = authQuery() // "" or "?auth=..."
            val sep = if (fbAuth.isEmpty()) "?" else "&"
            val url  = "$FB_URL/DebugLogs.json$fbAuth${sep}shallow=true"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string()
            if (body.isNullOrBlank() || body == "null") return@withContext emptyList()
            val obj = JSONObject(body)
            obj.keys().asSequence().toList().sorted()
        } catch (e: Exception) {
            Log.e(TAG, "fetchDebugLogPhones: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchDebugLogs(phone: String, limit: Int = 200): List<DebugLogEntry> = withContext(Dispatchers.IO) {
        try {
            val fbAuth = authQuery()
            val safePhone = phone.replace(Regex("[.#$\\[\\]/]"), "_")
            val url  = "$FB_URL/DebugLogs/$safePhone.json$fbAuth"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string()
            if (body.isNullOrBlank() || body == "null") return@withContext emptyList()

            val rootObj = JSONObject(body) // { "dd-MM-yyyy": { key: {entry}, ... }, ... }
            val list = mutableListOf<DebugLogEntry>()
            rootObj.keys().forEach { dateKey ->
                val dayObj = rootObj.optJSONObject(dateKey) ?: return@forEach
                dayObj.keys().forEach { logKey ->
                    val e = dayObj.optJSONObject(logKey) ?: return@forEach
                    list.add(DebugLogEntry(
                        ts    = e.optLong("ts", 0L),
                        level = e.optString("level", "D"),
                        tag   = e.optString("tag", ""),
                        msg   = e.optString("msg", ""),
                        phone = e.optString("phone", phone)
                    ))
                }
            }
            list.sortedByDescending { it.ts }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "fetchDebugLogs: ${e.message}")
            emptyList()
        }
    }


    private suspend fun gasPost(params: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val builder = FormBody.Builder()
                params.forEach { (k, v) -> builder.add(k, v) }
                builder.add("secret", BuildConfig.SECRET_KEY)
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
