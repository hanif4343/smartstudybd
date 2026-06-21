package com.hanif.smartstudy.data.remote

import android.util.Base64
import android.util.Log
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * ════════════════════════════════════════════════════════════════
 * FcmAdminService — GAS ছাড়াই সরাসরি FCM HTTP v1 API
 * ════════════════════════════════════════════════════════════════
 * Firebase service account (FCM_PROJECT_ID / FCM_CLIENT_EMAIL /
 * FCM_PRIVATE_KEY — GitHub Actions secrets থেকে build-time এ
 * BuildConfig এ আসে) দিয়ে app নিজেই একটা signed JWT বানায়, Google এর
 * থেকে OAuth2 access token নেয়, তারপর FCM messages:send এ POST করে।
 * কোনো Apps Script/middle-man নেই — পুরোটাই app → Google।
 *
 * ⚠️ নিরাপত্তা নোট (একবার পড়ে নিও):
 * এই private key টা compile হয়ে APK এর ভেতরে থাকে, এবং একটা public
 * APK সবসময়ই decompile করা সম্ভব (এক মিনিটের কাজ)। তাই Firebase
 * Console এর ডিফল্ট "Firebase Admin SDK" service account ব্যবহার না
 * করে, Google Cloud Console → IAM → Service Accounts থেকে আলাদা একটা
 * service account বানিয়ে, তার role শুধু "Firebase Cloud Messaging
 * API Admin" রাখো। তাহলে চাবিটা কোনোভাবে বের হয়ে গেলেও সর্বোচ্চ ক্ষতি
 * হলো push notification পাঠানো — ডাটাবেস/ইউজার ডেটার কোনো ঝুঁকি থাকবে না।
 * ডিফল্ট service account দিয়ে করলে পুরো Firebase project এর full
 * access একটা client app এর ভেতরে বসিয়ে দেওয়া হয়, যা অনেক বেশি ঝুঁকির।
 */
object FcmAdminService {

    private const val TAG       = "FcmAdmin"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val SCOPE     = "https://www.googleapis.com/auth/firebase.messaging"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()
    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var cachedExpiryMs: Long = 0L

    val isConfigured: Boolean
        get() = BuildConfig.FCM_PROJECT_ID.isNotBlank() &&
                BuildConfig.FCM_CLIENT_EMAIL.isNotBlank() &&
                BuildConfig.FCM_PRIVATE_KEY.isNotBlank()

    // ── PEM private key পার্স করো — secret এ escaped "\n" বা real newline,
    //    দুটোই ঠিকভাবে handle করে (GitHub Secrets এ যেভাবেই paste করা থাকুক) ──
    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        val der  = Base64.decode(cleaned, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(der)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun b64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    // ── Service-account JWT সাইন করে Google থেকে OAuth2 access token আনো ──
    private suspend fun fetchAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000
            val header  = JSONObject().put("alg", "RS256").put("typ", "JWT")
            val payload = JSONObject()
                .put("iss", BuildConfig.FCM_CLIENT_EMAIL)
                .put("scope", SCOPE)
                .put("aud", TOKEN_URL)
                .put("iat", now)
                .put("exp", now + 3600)

            val signingInput = b64Url(header.toString().toByteArray(Charsets.UTF_8)) + "." +
                b64Url(payload.toString().toByteArray(Charsets.UTF_8))

            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(parsePrivateKey(BuildConfig.FCM_PRIVATE_KEY))
                update(signingInput.toByteArray(Charsets.UTF_8))
            }.sign()

            val jwt = "$signingInput.${b64Url(signature)}"

            val body = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build()
            val resp = client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val respBody = resp.body?.string() ?: ""
            resp.close()
            if (!resp.isSuccessful) {
                Log.e(TAG, "Access token fetch failed: HTTP ${resp.code} — $respBody")
                return@withContext null
            }
            val json      = JSONObject(respBody)
            val token     = json.optString("access_token").ifBlank { null }
            val expiresIn = json.optLong("expires_in", 3600L)
            if (token != null) {
                cachedAccessToken = token
                cachedExpiryMs    = System.currentTimeMillis() + (expiresIn - 60) * 1000
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "fetchAccessToken error: ${e.message}", e)
            null
        }
    }

    private suspend fun accessToken(): String? = tokenMutex.withLock {
        val cached = cachedAccessToken
        if (cached != null && System.currentTimeMillis() < cachedExpiryMs) cached
        else fetchAccessToken()
    }

    /** নির্দিষ্ট একটা FCM token কে push পাঠাও */
    suspend fun sendToToken(
        token: String, title: String, body: String, data: Map<String, String> = emptyMap()
    ): Boolean = send(JSONObject().put("token", token), title, body, data)

    /** একটা topic এ subscribed সবাইকে push পাঠাও (broadcast) */
    suspend fun sendToTopic(
        topic: String, title: String, body: String, data: Map<String, String> = emptyMap()
    ): Boolean = send(JSONObject().put("topic", topic), title, body, data)

    /** একাধিক token কে এক এক করে পাঠাও — কতগুলো সফল হলো রিটার্ন করে */
    suspend fun sendToTokens(
        tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap()
    ): Int = withContext(Dispatchers.IO) {
        tokens.distinct().filter { it.isNotBlank() }.count { sendToToken(it, title, body, data) }
    }

    private suspend fun send(
        target: JSONObject, title: String, body: String, data: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.w(TAG, "FCM_PROJECT_ID/CLIENT_EMAIL/PRIVATE_KEY সেট নেই — push skip হলো")
            return@withContext false
        }
        try {
            val accessToken = accessToken() ?: return@withContext false

            // ডেটা-অনলি payload (top-level "notification" নেই) — তাহলে app
            // background এ থাকলেও onMessageReceived() ঠিকভাবে call হবে এবং
            // আগের মতোই custom sound/channel/deep-link কাজ করবে।
            val dataObj = JSONObject()
            data.forEach { (k, v) -> dataObj.put(k, v) }
            dataObj.put("title", title)
            dataObj.put("body", body)

            val message = target
                .put("data", dataObj)
                .put("android", JSONObject().put("priority", "HIGH"))

            val payload = JSONObject().put("message", message)
            val url = "https://fcm.googleapis.com/v1/projects/${BuildConfig.FCM_PROJECT_ID}/messages:send"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val ok = resp.isSuccessful
            if (!ok) Log.e(TAG, "FCM send failed: HTTP ${resp.code} — ${resp.body?.string()}")
            resp.close()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "send error: ${e.message}", e)
            false
        }
    }

    // ── ফোন নম্বর দিয়ে users/{phone}/fcmToken সরাসরি Firebase থেকে lookup ──
    suspend fun fetchTokenForPhone(phone: String): String? = withContext(Dispatchers.IO) {
        try {
            val key = com.hanif.smartstudy.util.PhoneValidator.sanitize(phone) ?: return@withContext null
            val token = FirebaseTokenProvider.getToken(); val auth = if (token.isNotBlank()) "?auth=$token" else ""
            val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/users/$key.json$auth"
            val body = client.newCall(Request.Builder().url(url).get().build()).execute().body?.string()
            if (body.isNullOrBlank() || body == "null") return@withContext null
            JSONObject(body).optString("fcmToken").ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTokenForPhone: ${e.message}")
            null
        }
    }

    // ── Role == "Admin" এমন সব ইউজারের phone বের করো (Users node, capital) ──
    suspend fun fetchAdminPhones(): List<String> = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseTokenProvider.getToken(); val auth = if (token.isNotBlank()) "?auth=$token" else ""
            val base = BuildConfig.FIREBASE_URL.trimEnd('/')
            val profileBody = client.newCall(Request.Builder().url("$base/Users.json$auth").get().build())
                .execute().body?.string()
            if (profileBody.isNullOrBlank() || profileBody == "null") return@withContext emptyList()
            val profiles = JSONObject(profileBody)
            val adminPhones = mutableListOf<String>()
            profiles.keys().forEach { key ->
                val u = profiles.optJSONObject(key) ?: return@forEach
                val role = u.optString("Role", u.optString("role", "")).lowercase()
                if (role == "admin") {
                    val phone = com.hanif.smartstudy.util.PhoneValidator.sanitize(
                        u.optString("Phone", u.optString("phone", key))
                    )
                    if (!phone.isNullOrBlank()) adminPhones += phone
                }
            }
            adminPhones
        } catch (e: Exception) {
            Log.e(TAG, "fetchAdminPhones: ${e.message}")
            emptyList()
        }
    }

    // ── Admin দের fcmToken বের করো (presence/users node, lowercase) ──
    // Fallback: AdminFCMTokens node (admin app সরাসরি এখানে save করে)
    suspend fun fetchAdminTokens(): List<String> = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseTokenProvider.getToken()
            val auth  = if (token.isNotBlank()) "?auth=$token" else ""
            val base  = BuildConfig.FIREBASE_URL.trimEnd('/')
            val tokens = mutableListOf<String>()

            // Priority 1: users/{adminPhone}/fcmToken
            val adminPhones = fetchAdminPhones()
            if (adminPhones.isNotEmpty()) {
                val presenceBody = client.newCall(
                    Request.Builder().url("$base/users.json$auth").get().build()
                ).execute().body?.string()
                if (!presenceBody.isNullOrBlank() && presenceBody != "null") {
                    val presence = JSONObject(presenceBody)
                    adminPhones.forEach { phone ->
                        presence.optJSONObject(phone)
                            ?.optString("fcmToken")?.ifBlank { null }
                            ?.let { tokens += it }
                    }
                }
            }

            // Fallback: AdminFCMTokens/token (admin Capacitor app save করে)
            if (tokens.isEmpty()) {
                val fallbackBody = client.newCall(
                    Request.Builder().url("$base/AdminFCMTokens/token.json$auth").get().build()
                ).execute().body?.string()
                if (!fallbackBody.isNullOrBlank() && fallbackBody != "null") {
                    val t = fallbackBody.trim().removeSurrounding("\"")
                    if (t.isNotBlank()) {
                        tokens += t
                        Log.d(TAG, "fetchAdminTokens: using AdminFCMTokens fallback")
                    }
                }
            }

            Log.d(TAG, "fetchAdminTokens: ${tokens.size} token(s) found")
            tokens.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "fetchAdminTokens: ${e.message}")
            emptyList()
        }
    }
}
