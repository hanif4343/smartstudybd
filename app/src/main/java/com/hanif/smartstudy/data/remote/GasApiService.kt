package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object GasApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson   = Gson()
    private val GAS_URL get() = BuildConfig.GAS_URL

    /** Firebase Auth ID Token দিয়ে auth query param */
    private suspend fun authQuery(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun login(phone: String, password: String): GasResult<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("action", "login")
                    .add("phone", phone)
                    .add("password", hashPassword(password))
                    .add("secret", BuildConfig.SECRET_KEY)
                    .build()
                val req  = Request.Builder().url(GAS_URL).post(body).build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No response")
                val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                if (map["status"] == "ok") GasResult.Success(map)
                else GasResult.Error(map["message"]?.toString() ?: "Login failed")
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Network error")
            }
        }
    }

    suspend fun signup(
        name: String, phone: String, password: String,
        userType: String, classLevel: String
    ): GasResult<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("action", "signup")
                    .add("name", name)
                    .add("phone", phone)
                    .add("password", hashPassword(password))
                    .add("userType", userType)
                    .add("classLevel", classLevel)
                    .add("secret", BuildConfig.SECRET_KEY)
                    .build()
                val req  = Request.Builder().url(GAS_URL).post(body).build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No response")
                val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                if (map["status"] == "ok") GasResult.Success(map)
                else GasResult.Error(map["message"]?.toString() ?: "Signup failed")
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Network error")
            }
        }
    }

    suspend fun getUserFromFirebase(phone: String): GasResult<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val req  = Request.Builder()
                    .url("${BuildConfig.FIREBASE_URL.trimEnd('/')}/Users.json$auth").get().build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No data")
                val all: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : TypeToken<Map<String, Map<String, Any>>>() {}.type)
                val user = all.values.find { entry ->
                    val p = entry["Phone"]?.toString() ?: entry["phone"]?.toString() ?: ""
                    p.trim() == phone.trim()
                }
                if (user != null) GasResult.Success(user)
                else GasResult.Error("ব্যবহারকারী পাওয়া যায়নি")
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Network error")
            }
        }
    }

    /** Admin কে FCM notification পাঠাও */
    suspend fun notifyAdmin(
        event     : String,
        userName  : String,
        userPhone : String,
        extra     : String = "",
        questionId: String = "",
        tab       : String = ""
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = "${BuildConfig.GAS_URL}" +
                    "?action=adminNotify" +
                    "&secret=${BuildConfig.SECRET_KEY}" +
                    "&event=${java.net.URLEncoder.encode(event, "UTF-8")}" +
                    "&name=${java.net.URLEncoder.encode(userName, "UTF-8")}" +
                    "&phone=${java.net.URLEncoder.encode(userPhone, "UTF-8")}" +
                    "&extra=${java.net.URLEncoder.encode(extra.take(80), "UTF-8")}" +
                    "&questionId=${java.net.URLEncoder.encode(questionId, "UTF-8")}" +
                    "&tab=${java.net.URLEncoder.encode(tab, "UTF-8")}"
                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                Log.d("GAS", "notifyAdmin($event): HTTP ${resp.code}")
                resp.close()
            } catch (e: Exception) {
                Log.e("GAS", "notifyAdmin: ${e.message}")
            }
        }
    }

    suspend fun reportQuestion(
        questionId: String,
        question  : String,
        issue     : String,
        userName  : String = "",
        userPhone : String = "",
        tab       : String = ""
    ) {
        withContext(Dispatchers.IO) {
            try {
                val auth        = authQuery()
                val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')

                val jsonObj = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("question",   question.take(200))
                    addProperty("issue",      issue)
                    addProperty("userName",   userName)
                    addProperty("userPhone",  userPhone)
                    addProperty("timestamp",  System.currentTimeMillis())
                    addProperty("status",     "pending")
                }

                val url  = "$firebaseBase/Reports.json$auth"
                val body = jsonObj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                Log.d("GAS", "Report saved: HTTP ${resp.code}")
                resp.close()

                if (userName.isNotBlank() && userPhone.isNotBlank()) {
                    notifyAdmin(
                        event      = "report",
                        userName   = userName,
                        userPhone  = userPhone,
                        extra      = issue.take(60),
                        questionId = questionId,
                        tab        = tab
                    )
                } else Unit
            } catch (e: Exception) {
                Log.e("GAS", "reportQuestion: ${e.message}")
            }
        }
    }

    // ── User Techniques ──

    suspend fun fetchTechniquesForQuestion(
        questionId: String,
        myUserId  : String
    ): GasResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/${questionId}.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val json = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Success(emptyList())
                val raw: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : TypeToken<Map<String, Map<String, Any>>>() {}.type)
                val list = raw.map { (k, v) ->
                    com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to questionId))
                }.filter { t ->
                    t.userId == myUserId || (t.isPublic && t.isApproved())
                }.sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun saveTechnique(
        questionId: String,
        userId    : String,
        userName  : String,
        text      : String,
        isPublic  : Boolean
    ): GasResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId.json$auth"
                val obj  = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("userId",     userId)
                    addProperty("userName",   userName)
                    addProperty("text",       text)
                    addProperty("isPublic",   isPublic)
                    addProperty("status",     "pending")
                    addProperty("timestamp",  System.currentTimeMillis())
                }
                val body     = obj.toString().toRequestBody("application/json".toMediaType())
                val req      = Request.Builder().url(url).post(body).build()
                val resp     = client.newCall(req).execute()
                val respJson = resp.body?.string() ?: "{}"
                val pushKey  = gson.fromJson(respJson, JsonObject::class.java)?.get("name")?.asString ?: ""
                resp.close()

                if (isPublic) {
                    notifyAdmin(event = "technique", userName = userName, userPhone = userId, extra = text.take(60))
                }
                GasResult.Success(pushKey)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun updateTechnique(
        questionId: String,
        pushKey   : String,
        text      : String,
        isPublic  : Boolean
    ): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val obj  = JsonObject().apply {
                    addProperty("text",      text)
                    addProperty("isPublic",  isPublic)
                    addProperty("status",    if (isPublic) "pending" else "approved")
                    addProperty("timestamp", System.currentTimeMillis())
                }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun deleteTechnique(questionId: String, pushKey: String): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val req  = Request.Builder().url(url).delete().build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun fetchPendingTechniques(): GasResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val json = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Success(emptyList())
                val raw: Map<String, Map<String, Map<String, Any>>> = gson.fromJson(
                    json, object : TypeToken<Map<String, Map<String, Map<String, Any>>>>() {}.type)
                val list = raw.flatMap { (qId, entries) ->
                    entries.map { (k, v) ->
                        com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to qId))
                    }
                }.filter { it.isPublic && it.isPending() }.sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun updateTechniqueStatus(
        questionId: String,
        pushKey   : String,
        status    : String
    ): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val obj  = JsonObject().apply { addProperty("status", status) }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ADMIN FUNCTIONS
    // ══════════════════════════════════════════════════════════

    /** Admin: যেকোনো question এর field Firebase এ PATCH করো */
    suspend fun adminUpdateQuestionField(
        sheet  : String,
        rowKey : String,
        fields : Map<String, String>
    ): GasResult<Unit> = withContext(Dispatchers.IO) {
        if (rowKey.isBlank()) {
            android.util.Log.e("AdminEdit", "Blank rowKey for sheet=$sheet — refusing to PATCH sheet root")
            return@withContext GasResult.Error("প্রশ্নের ID পাওয়া যায়নি — আবার লোড করে চেষ্টা করুন")
        }
        try {
            val auth = authQuery()
            val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/$sheet/$rowKey.json$auth"
            android.util.Log.d("AdminEdit", "PATCH → $url | fields=$fields")
            val obj  = JsonObject().apply { fields.forEach { (k, v) -> addProperty(k, v) } }
            val body = obj.toString().toRequestBody("application/json".toMediaType())
            val resp = client.newCall(Request.Builder().url(url).patch(body).build()).execute()
            val respBody = resp.body?.string() ?: ""
            val code = resp.code
            android.util.Log.d("AdminEdit", "Response: $code | $respBody")
            resp.close()
            if (resp.isSuccessful) GasResult.Success(Unit)
            else GasResult.Error("Firebase error: $code — $respBody")
        } catch (e: Exception) {
            android.util.Log.e("AdminEdit", "Exception: ${e.message}", e)
            GasResult.Error(e.message ?: "Network error")
        }
    }

    /** Admin: Options swap করো — Firebase এ option1-4 + correct একসাথে update */
    suspend fun adminSwapOptions(
        sheet    : String,
        rowKey   : String,
        options  : Map<String, String>,
        newAnswer: String
    ): GasResult<Unit> {
        val fields = options.toMutableMap()
        fields["correct"] = newAnswer
        return adminUpdateQuestionField(sheet, rowKey, fields)
    }

    /** Admin: নতুন question Firebase এ push করো */
    suspend fun adminAddQuestion(sheet: String, fields: Map<String, String>): GasResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/$sheet.json$auth"
                val obj  = JsonObject().apply {
                    fields.forEach { (k, v) -> addProperty(k, v) }
                    addProperty("createdAt", System.currentTimeMillis())
                }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val resp = client.newCall(Request.Builder().url(url).post(body).build()).execute()
                val respBody = resp.body?.string() ?: ""
                resp.close()
                if (resp.isSuccessful) {
                    val pushKey = try {
                        com.google.gson.JsonParser.parseString(respBody)
                            .asJsonObject.get("name")?.asString ?: ""
                    } catch (e: Exception) { "" }
                    GasResult.Success(pushKey)
                } else GasResult.Error("Firebase error: ${resp.code}")
            } catch (e: Exception) { GasResult.Error(e.message ?: "Add failed") }
        }

    /** Admin: Firebase /Reports থেকে pending reports fetch করো */
    suspend fun fetchPendingReports(): GasResult<List<ReportedQuestion>> =
        withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/Reports.json$auth"
                val json = client.newCall(Request.Builder().url(url).get().build())
                    .execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Success(emptyList())
                val raw: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
                )
                val list = raw.map { (key, v) ->
                    ReportedQuestion(
                        reportKey  = key,
                        questionId = v["questionId"]?.toString() ?: "",
                        question   = v["question"]?.toString() ?: "",
                        issue      = v["issue"]?.toString() ?: "",
                        userName   = v["userName"]?.toString() ?: "",
                        userPhone  = v["userPhone"]?.toString() ?: "",
                        tab        = v["tab"]?.toString() ?: "",
                        status     = v["status"]?.toString() ?: "pending",
                        timestamp  = (v["timestamp"] as? Double)?.toLong() ?: 0L
                    )
                }.filter { it.status == "pending" }.sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Fetch failed") }
        }

    /**
     * Admin: Report status update করো + Reporter user কে FCM notification পাঠাও।
     * Firebase এ user এর FCM token lookup করে notification পাঠানো হয়।
     */
    suspend fun resolveReportAndNotify(
        reportKey  : String,
        status     : String,      // "resolved" | "dismissed"
        userPhone  : String,      // reporter এর phone
        questionSnippet: String = "",
        userName   : String = "",
        questionId : String = "",
        tab        : String = ""
    ): GasResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth     = authQuery()
            val base     = BuildConfig.FIREBASE_URL.trimEnd('/')

            // ── ১. Report status update ──
            val patchUrl = "$base/Reports/$reportKey.json$auth"
            val patchObj = JsonObject().apply {
                addProperty("status", status)
                addProperty("resolvedAt", System.currentTimeMillis())
            }
            client.newCall(
                Request.Builder().url(patchUrl)
                    .patch(patchObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().close()

            // ── ২. Reporter এর FCM token Firebase থেকে lookup ──
            if (userPhone.isNotBlank()) {
                val phoneEncoded = userPhone.replace("+", "").replace(" ", "")
                val userUrl = "$base/Users/$phoneEncoded.json$auth"
                val userJson = try {
                    client.newCall(Request.Builder().url(userUrl).get().build())
                        .execute().body?.string() ?: "null"
                } catch (e: Exception) { "null" }

                val fcmToken = if (userJson != "null") {
                    try {
                        com.google.gson.JsonParser.parseString(userJson)
                            .asJsonObject?.get("fcmToken")?.asString
                            ?: com.google.gson.JsonParser.parseString(userJson)
                                .asJsonObject?.get("FCMToken")?.asString
                    } catch (e: Exception) { null }
                } else null

                // ── ৩. FCM notification পাঠাও ──
                val displayName = userName.ifBlank { "ব্যবহারকারী" }
                val notifMsg = if (status == "resolved")
                    "প্রিয় $displayName, আপনার রিপোর্ট করা প্রশ্নটি সমাধান করা হয়েছে। ধন্যবাদ আপনার সহযোগিতার জন্য! 🎉"
                else
                    "প্রিয় $displayName, আপনার রিপোর্টটি পর্যালোচনা করা হয়েছে।"

                val snippet = if (questionSnippet.isNotBlank())
                    questionSnippet.take(60) + "..." else ""

                if (!fcmToken.isNullOrBlank()) {
                    try {
                        val gasUrl = "${BuildConfig.GAS_URL}" +
                            "?action=sendNotification" +
                            "&secret=${BuildConfig.SECRET_KEY}" +
                            "&token=${java.net.URLEncoder.encode(fcmToken, "UTF-8")}" +
                            "&title=${java.net.URLEncoder.encode("SmartStudyBD", "UTF-8")}" +
                            "&body=${java.net.URLEncoder.encode(notifMsg, "UTF-8")}" +
                            "&extra=${java.net.URLEncoder.encode(snippet, "UTF-8")}" +
                            "&type=admin_report" +
                            "&url=reports" +
                            "&questionId=${java.net.URLEncoder.encode(questionId, "UTF-8")}" +
                            "&tab=${java.net.URLEncoder.encode(tab, "UTF-8")}" +
                            "&sound=default"
                        client.newCall(Request.Builder().url(gasUrl).get().build())
                            .execute().close()
                        Log.d("GAS", "Report notification sent to $userPhone")
                    } catch (e: Exception) {
                        Log.e("GAS", "FCM send failed: ${e.message}")
                    }
                }

                // ── ৪. Notifications/{phone} এ fallback entry লিখো (poll worker এর জন্য) ──
                try {
                    val notifKey = "notif_${System.currentTimeMillis()}"
                    val notifObj = JsonObject().apply {
                        addProperty("title", "SmartStudyBD")
                        addProperty("body", notifMsg)
                        addProperty("type", "admin_report")
                        addProperty("url", "reports")
                        addProperty("questionId", questionId)
                        addProperty("tab", tab)
                        addProperty("read", false)
                        addProperty("time", System.currentTimeMillis())
                    }
                    val notifUrl = "$base/Notifications/$phoneEncoded/$notifKey.json$auth"
                    client.newCall(
                        Request.Builder().url(notifUrl)
                            .put(notifObj.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute().close()
                } catch (e: Exception) {
                    Log.e("GAS", "Notifications fallback write failed: ${e.message}")
                }
            }

            GasResult.Success(Unit)
        } catch (e: Exception) { GasResult.Error(e.message ?: "Update failed") }
    }

    /** Admin: Bulk audience tag update */
    suspend fun adminBulkAudienceUpdate(
        sheet    : String,
        subject  : String,
        subTopic : String,
        newTag   : String
    ): GasResult<Int> = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val base = BuildConfig.FIREBASE_URL.trimEnd('/')
            val json = client.newCall(
                Request.Builder().url("$base/$sheet.json$auth").get().build()
            ).execute().body?.string() ?: "null"
            if (json == "null") return@withContext GasResult.Error("Sheet empty")

            val raw: Map<String, Map<String, Any>> = gson.fromJson(
                json, object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
            )
            val matching = raw.filter { (_, v) ->
                val s  = v["subject"]?.toString()?.trim() ?: ""
                val st = (v["sub_topic"] ?: v["subTopic"])?.toString()?.trim() ?: ""
                s.equals(subject.trim(), ignoreCase = true) &&
                (subTopic.isBlank() || st.equals(subTopic.trim(), ignoreCase = true))
            }
            if (matching.isEmpty()) return@withContext GasResult.Error("কোনো matching প্রশ্ন নেই")

            var updated = 0
            matching.forEach { (key, _) ->
                val obj  = JsonObject().apply { addProperty("AudienceTags", newTag) }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val resp = client.newCall(
                    Request.Builder().url("$base/$sheet/$key.json$auth").patch(body).build()
                ).execute()
                if (resp.isSuccessful) updated++
                resp.close()
            }
            GasResult.Success(updated)
        } catch (e: Exception) { GasResult.Error(e.message ?: "Bulk update failed") }
    }
}

// ── Data model ───────────────────────────────────────────────
data class ReportedQuestion(
    val reportKey  : String = "",
    val questionId : String = "",
    val question   : String = "",
    val issue      : String = "",
    val userName   : String = "",
    val userPhone  : String = "",
    val tab        : String = "",
    val status     : String = "pending",
    val timestamp  : Long   = 0L
) {
    fun sheetName() = when (tab.lowercase()) {
        "qbank" -> "QBank"; "study" -> "Study"; else -> "Quiz"
    }
}

sealed class GasResult<out T> {
    data class Success<T>(val data: T) : GasResult<T>()
    data class Error(val message: String) : GasResult<Nothing>()
}
