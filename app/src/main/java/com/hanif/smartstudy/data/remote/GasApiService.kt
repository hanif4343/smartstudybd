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
                    .build()
                val req  = Request.Builder().url(GAS_URL).post(body).build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No response")
                val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                if (map["status"] == "ok") {
                    GasResult.Success(map)
                } else {
                    GasResult.Error(map["message"]?.toString() ?: "Login failed")
                }
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
                    .build()
                val req  = Request.Builder().url(GAS_URL).post(body).build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No response")
                val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                if (map["status"] == "ok") {
                    GasResult.Success(map)
                } else {
                    GasResult.Error(map["message"]?.toString() ?: "Signup failed")
                }
            } catch (e: Exception) { 
                GasResult.Error(e.message ?: "Network error") 
            }
        }
    }

    suspend fun getUserFromFirebase(phone: String): GasResult<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val req  = Request.Builder()
                    .url("${BuildConfig.FIREBASE_URL}Users.json").get().build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext GasResult.Error("No data")
                val all: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : TypeToken<Map<String, Map<String, Any>>>() {}.type)
                val user = all.values.find { entry ->
                    val p = entry["Phone"]?.toString() ?: entry["phone"]?.toString() ?: ""
                    p.trim() == phone.trim()
                }
                
                if (user != null) {
                    GasResult.Success(user)
                } else {
                    GasResult.Error("ব্যবহারকারী পাওয়া যায়নি")
                }
            } catch (e: Exception) { 
                GasResult.Error(e.message ?: "Network error") 
            }
        }
    }

    /** Admin কে FCM notification পাঠাও — technique বা report জমা হলে */
    suspend fun notifyAdmin(
        event      : String,
        userName   : String,
        userPhone  : String,
        extra      : String = "",
        questionId : String = "",
        tab        : String = ""   // "quiz" | "qbank" | "study"
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = "${BuildConfig.GAS_URL}" +
                    "?action=adminNotify" +
                    "&event=${java.net.URLEncoder.encode(event, "UTF-8")}" +
                    "&name=${java.net.URLEncoder.encode(userName, "UTF-8")}" +
                    "&phone=${java.net.URLEncoder.encode(userPhone, "UTF-8")}" +
                    "&extra=${java.net.URLEncoder.encode(extra.take(80), "UTF-8")}" +
                    "&questionId=${java.net.URLEncoder.encode(questionId, "UTF-8")}" +
                    "&tab=${java.net.URLEncoder.encode(tab, "UTF-8")}"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                Log.d("GAS", "notifyAdmin($event): HTTP ${resp.code}")
                resp.close()
            } catch (e: Exception) {
                Log.e("GAS", "notifyAdmin: ${e.message}")
            }
        }
    }

    suspend fun reportQuestion(
        questionId : String,
        question   : String,
        issue      : String,
        userName   : String = "",
        userPhone  : String = "",
        tab        : String = ""   // "quiz" | "qbank" | "study"
    ) {
        withContext(Dispatchers.IO) {
            try {
                val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey    = BuildConfig.SECRET_KEY
                val authParam    = if (secretKey.isNotBlank() && !secretKey.contains("%%"))
                    "?auth=$secretKey" else ""

                val jsonObj = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("question",   question.take(200))
                    addProperty("issue",      issue)
                    addProperty("userName",   userName)
                    addProperty("userPhone",  userPhone)
                    addProperty("timestamp",  System.currentTimeMillis())
                    addProperty("status",     "pending")
                }

                val url  = "$firebaseBase/Reports.json$authParam"
                val body = jsonObj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                Log.d("GAS", "Report saved: HTTP ${resp.code} → $url")
                resp.close()

                // Admin কে notify করো
                if (userName.isNotBlank() && userPhone.isNotBlank()) {
                    notifyAdmin(
                        event      = "report",
                        userName   = userName,
                        userPhone  = userPhone,
                        extra      = issue.take(60),
                        questionId = questionId,
                        tab        = tab
                    )
                }
                Unit
            } catch (e: Exception) {
                Log.e("GAS", "reportQuestion: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // User Technique — Firebase RTDB
    // Node: UserTechniques/{questionId}/{pushKey}
    // ─────────────────────────────────────────────────────────

    /** একটি প্রশ্নের জন্য সব approved public + নিজের সব টেকনিক fetch */
    suspend fun fetchTechniquesForQuestion(
        questionId : String,
        myUserId   : String
    ): GasResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques/${questionId}.json$auth"
                val req       = Request.Builder().url(url).get().build()
                val json      = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Success(emptyList())
                val raw: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type)
                val list = raw.map { (k, v) ->
                    com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to questionId))
                }.filter { t ->
                    t.userId == myUserId || (t.isPublic && t.isApproved())
                }.sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /** নতুন টেকনিক সেভ করো */
    suspend fun saveTechnique(
        questionId : String,
        userId     : String,
        userName   : String,
        text       : String,
        isPublic   : Boolean
    ): GasResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques/$questionId.json$auth"
                val obj = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("userId",     userId)
                    addProperty("userName",   userName)
                    addProperty("text",       text)
                    addProperty("isPublic",   isPublic)
                    addProperty("status",     "pending")
                    addProperty("timestamp",  System.currentTimeMillis())
                }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                val respJson = resp.body?.string() ?: "{}"
                val pushKey = gson.fromJson(respJson, JsonObject::class.java)?.get("name")?.asString ?: ""
                resp.close()
                Log.d("GAS", "Technique saved: $pushKey")

                // শুধু public হলে admin কে notify করো (approval লাগবে)
                if (isPublic) {
                    notifyAdmin(
                        event     = "technique",
                        userName  = userName,
                        userPhone = userId,
                        extra     = text.take(60)
                    )
                }

                GasResult.Success(pushKey)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /** নিজের টেকনিক আপডেট (text বা visibility পরিবর্তন) */
    suspend fun updateTechnique(
        questionId  : String,
        pushKey     : String,
        text        : String,
        isPublic    : Boolean
    ): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques/$questionId/$pushKey.json$auth"
                val obj = JsonObject().apply {
                    addProperty("text",      text)
                    addProperty("isPublic",  isPublic)
                    addProperty("status",    if (isPublic) "pending" else "approved")
                    addProperty("timestamp", System.currentTimeMillis())
                }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url)
                    .patch(body)
                    .build()
                val resp = client.newCall(req).execute()
                resp.close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /** টেকনিক ডিলিট */
    suspend fun deleteTechnique(questionId: String, pushKey: String): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques/$questionId/$pushKey.json$auth"
                val req       = Request.Builder().url(url).delete().build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /** Admin: সব pending public টেকনিক fetch */
    suspend fun fetchPendingTechniques(): GasResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques.json$auth"
                val req       = Request.Builder().url(url).get().build()
                val json      = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Success(emptyList())
                val raw: Map<String, Map<String, Map<String, Any>>> = gson.fromJson(
                    json, object : com.google.gson.reflect.TypeToken<
                            Map<String, Map<String, Map<String, Any>>>>() {}.type)
                val list = raw.flatMap { (qId, entries) ->
                    entries.map { (k, v) ->
                        com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to qId))
                    }
                }.filter { it.isPublic && it.isPending() }
                    .sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /** Admin: একটি technique approve বা reject করো */
    suspend fun updateTechniqueStatus(
        questionId : String,
        pushKey    : String,
        status     : String
    ): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/UserTechniques/$questionId/$pushKey.json$auth"
                val obj = JsonObject().apply { addProperty("status", status) }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /**
     * Admin: যেকোনো sheet এর যেকোনো question এর field direct Firebase PATCH করো।
     *
     * @param sheet  "Quiz" | "QBank" | "Study"
     * @param rowKey Firebase row key (e.g. "-Nxyz123")
     * @param fields পরিবর্তনযোগ্য field map (e.g. mapOf("Question" to "নতুন প্রশ্ন"))
     */
    suspend fun adminUpdateQuestionField(
        sheet    : String,
        rowKey   : String,
        fields   : Map<String, String>
    ): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/$sheet/$rowKey.json$auth"
                val obj = JsonObject()
                fields.forEach { (k, v) -> obj.addProperty(k, v) }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                val resp = client.newCall(req).execute()
                resp.close()
                if (resp.isSuccessful) GasResult.Success(Unit)
                else GasResult.Error("Firebase error: ${resp.code}")
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }
    }

    /**
     * Admin: MCQ options এর position swap করো (A↔B, A↔C etc.)
     * Firebase এ optionA, optionB, optionC, optionD এবং Answer field update হবে।
     */
    suspend fun adminSwapOptions(
        sheet    : String,
        rowKey   : String,
        options  : Map<String, String>,   // {"OptionA":"...", "OptionB":"...", ...}
        newAnswer: String
    ): GasResult<Unit> {
        val fields = options.toMutableMap()
        fields["Answer"] = newAnswer
        return adminUpdateQuestionField(sheet, rowKey, fields)
    }

    // ── Feature 1: Report Queue ───────────────────────────────

    /**
     * Firebase /Reports থেকে সব pending report fetch করো।
     * Admin এর report queue এর জন্য।
     */
    suspend fun fetchPendingReports(): GasResult<List<ReportedQuestion>> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/Reports.json$auth"
                val req       = Request.Builder().url(url).get().build()
                val json      = client.newCall(req).execute().body?.string() ?: "null"
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
                }.filter { it.status == "pending" }
                 .sortedByDescending { it.timestamp }
                GasResult.Success(list)
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Fetch failed")
            }
        }
    }

    /** Report status আপডেট করো — "resolved" | "dismissed" */
    suspend fun updateReportStatus(reportKey: String, status: String): GasResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val base  = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth  = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url   = "$base/Reports/$reportKey.json$auth"
                val obj   = JsonObject().apply {
                    addProperty("status", status)
                    addProperty("resolvedAt", System.currentTimeMillis())
                }
                val body  = obj.toString().toRequestBody("application/json".toMediaType())
                val req   = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                GasResult.Success(Unit)
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Update failed")
            }
        }
    }

    // ── Feature 2: Add New Question ──────────────────────────

    /**
     * Firebase এ নতুন question push করো।
     * @param sheet "Quiz" | "QBank" | "Study"
     * @param fields Firebase field map
     */
    suspend fun adminAddQuestion(sheet: String, fields: Map<String, String>): GasResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
                val url       = "$base/$sheet.json$auth"
                val obj       = JsonObject()
                fields.forEach { (k, v) -> obj.addProperty(k, v) }
                obj.addProperty("createdAt", System.currentTimeMillis())
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                resp.close()
                if (resp.isSuccessful) {
                    // Firebase returns {"name": "-NxyzPushKey"}
                    val pushKey = try {
                        val j = JsonObject()
                        com.google.gson.JsonParser.parseString(respBody).asJsonObject
                            .get("name")?.asString ?: ""
                    } catch (e: Exception) { "" }
                    GasResult.Success(pushKey)
                } else {
                    GasResult.Error("Firebase error: ${resp.code}")
                }
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Add failed")
            }
        }
    }

    // ── Feature 3: Bulk Audience Change ──────────────────────

    /**
     * একটি sheet এর নির্দিষ্ট subject (এবং optional subTopic) এর
     * সব প্রশ্নের AudienceTags একসাথে update করো।
     * প্রথমে সব matching questions fetch করে, তারপর batch PATCH।
     */
    suspend fun adminBulkAudienceUpdate(
        sheet      : String,
        subject    : String,
        subTopic   : String,   // blank = সব subTopic
        newTag     : String
    ): GasResult<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val base      = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey = BuildConfig.SECRET_KEY
                val auth      = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""

                // Fetch sheet
                val url  = "$base/$sheet.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val json = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext GasResult.Error("Sheet empty")

                val raw: Map<String, Map<String, Any>> = gson.fromJson(
                    json, object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
                )

                // Filter matching rows
                val matching = raw.filter { (_, v) ->
                    val subj = v["subject"]?.toString()?.trim() ?: ""
                    val st   = (v["sub_topic"] ?: v["subTopic"])?.toString()?.trim() ?: ""
                    val subjMatch = subj.equals(subject.trim(), ignoreCase = true)
                    val stMatch   = subTopic.isBlank() || st.equals(subTopic.trim(), ignoreCase = true)
                    subjMatch && stMatch
                }

                if (matching.isEmpty()) return@withContext GasResult.Error("কোনো matching প্রশ্ন পাওয়া যায়নি")

                // Batch update
                var updated = 0
                matching.forEach { (key, _) ->
                    val patchUrl  = "$base/$sheet/$key.json$auth"
                    val obj       = JsonObject().apply { addProperty("AudienceTags", newTag) }
                    val body      = obj.toString().toRequestBody("application/json".toMediaType())
                    val patchReq  = Request.Builder().url(patchUrl).patch(body).build()
                    val resp      = client.newCall(patchReq).execute()
                    if (resp.isSuccessful) updated++
                    resp.close()
                }
                GasResult.Success(updated)
            } catch (e: Exception) {
                GasResult.Error(e.message ?: "Bulk update failed")
            }
        }
    }

}   // end GasApiService

// ── Data model for reported questions ────────────────────────
data class ReportedQuestion(
    val reportKey  : String = "",
    val questionId : String = "",
    val question   : String = "",
    val issue      : String = "",
    val userName   : String = "",
    val userPhone  : String = "",
    val tab        : String = "",   // "quiz" | "qbank" | "study"
    val status     : String = "pending",
    val timestamp  : Long   = 0L
) {
    fun sheetName() = when (tab.lowercase()) {
        "qbank" -> "QBank"
        "study" -> "Study"
        else    -> "Quiz"
    }
}

sealed class GasResult<out T> {
    data class Success<T>(val data: T) : GasResult<T>()
    data class Error(val message: String) : GasResult<Nothing>()
}
