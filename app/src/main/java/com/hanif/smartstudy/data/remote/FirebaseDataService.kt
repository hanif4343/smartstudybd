package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object FirebaseDataService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson   = Gson()

    /** Firebase Auth ID Token দিয়ে auth query param */
    private suspend fun authQuery(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    // ── নিরাপদ Firebase node parsing ──────────────────────────
    // Firebase Realtime Database REST API: কোনো node-এর child key গুলো sparse/সব numeric
    // (যেমন "0","1","2"...) হলে Firebase সেটাকে {"0":..,"1":..} object এর বদলে raw JSON
    // array [..,..] হিসেবে ফেরত দেয়। Gson এর built-in Map<String,T> deserializer array
    // পেলে সেটাকে "[key,value] জোড়ার array" ভেবে read করতে যায়, আর row object পেলেই
    // "Expected BEGIN_ARRAY but was BEGIN_OBJECT" ছুঁড়ে crash করে। নিচের হেল্পারগুলো
    // object ও array — দুই ফরম্যাটই নিরাপদে handle করে।
    private val rowMapType = object : TypeToken<Map<String, Any>>() {}.type

    private fun firebaseEntries(element: com.google.gson.JsonElement?): List<Pair<String, com.google.gson.JsonElement>> {
        if (element == null || element.isJsonNull) return emptyList()
        return when {
            element.isJsonObject -> element.asJsonObject.entrySet().map { it.key to it.value }
            element.isJsonArray  -> element.asJsonArray.mapIndexedNotNull { idx, v ->
                if (v == null || v.isJsonNull) null else idx.toString() to v
            }
            else -> emptyList()
        }
    }

    private fun firebaseEntries(json: String): List<Pair<String, com.google.gson.JsonElement>> =
        firebaseEntries(try { com.google.gson.JsonParser.parseString(json) } catch (e: Exception) { null })

    /** sheet/node root কে Map<rowKey, rowFields> এ parse করে — node object বা array, দুই আকারেই */
    private fun parseRowMap(json: String): Map<String, Map<String, Any>> =
        firebaseEntries(json)
            .filter { it.second.isJsonObject }
            .associate { (k, v) -> k to (gson.fromJson<Map<String, Any>>(v, rowMapType) ?: emptyMap()) }

    /** UserTechniques এর মতো ২-লেভেল nested node (questionId -> pushKey -> fields) parse করে */
    private fun parseNestedRowMap(json: String): Map<String, Map<String, Map<String, Any>>> =
        firebaseEntries(json).associate { (k, v) ->
            k to firebaseEntries(v)
                .filter { it.second.isJsonObject }
                .associate { (k2, v2) -> k2 to (gson.fromJson<Map<String, Any>>(v2, rowMapType) ?: emptyMap()) }
        }

    suspend fun getUserFromFirebase(phone: String): ApiResult<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val req  = Request.Builder()
                    .url("${BuildConfig.FIREBASE_URL.trimEnd('/')}/Users.json$auth").get().build()
                val json = client.newCall(req).execute().body?.string()
                    ?: return@withContext ApiResult.Error("No data")
                val all: Map<String, Map<String, Any>> = parseRowMap(json)
                val user = all.values.find { entry ->
                    val p = entry["Phone"]?.toString() ?: entry["phone"]?.toString() ?: ""
                    p.trim() == phone.trim()
                }
                if (user != null) ApiResult.Success(user)
                else ApiResult.Error("ব্যবহারকারী পাওয়া যায়নি")
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }
    }

    /** Admin কে push notification পাঠাও — সরাসরি Firebase lookup + FCM v1 send, কোনো GAS নেই */
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
                val (title, body) = when (event) {
                    "report"    -> "🚩 নতুন রিপোর্ট" to
                        "$userName একটি প্রশ্নে সমস্যা রিপোর্ট করেছে।" + (if (extra.isNotBlank()) " ($extra)" else "")
                    "technique" -> "💡 নতুন টেকনিক" to
                        "$userName একটি নতুন টেকনিক জমা দিয়েছে, অনুমোদনের অপেক্ষায়।" + (if (extra.isNotBlank()) " \"$extra\"" else "")
                    else        -> "🔔 Smart Study" to "$userName থেকে নতুন একটিভিটি: $event"
                }
                val data = mapOf(
                    "type"       to "admin_$event",
                    "url"        to "admin",
                    "questionId" to questionId,
                    "tab"        to tab
                ).filterValues { it.isNotBlank() }

                val adminPhones = FcmAdminService.fetchAdminPhones()
                if (adminPhones.isEmpty()) {
                    Log.w("FirebaseData", "notifyAdmin($event): কোনো admin user পাওয়া যায়নি")
                    return@withContext
                }

                // Notifications fallback — push miss হলেও 15-মিনিট poll worker এ দেখা যাবে
                val auth = authQuery()
                val base = BuildConfig.FIREBASE_URL.trimEnd('/')
                adminPhones.forEach { phone ->
                    try {
                        val notifKey = "notif_${System.currentTimeMillis()}"
                        val notifObj = JsonObject().apply {
                            addProperty("title", title)
                            addProperty("body", body)
                            addProperty("type", "admin_$event")
                            addProperty("url", "admin")
                            addProperty("questionId", questionId)
                            addProperty("tab", tab)
                            addProperty("read", false)
                            addProperty("time", System.currentTimeMillis())
                        }
                        val url = "$base/Notifications/$phone/$notifKey.json$auth"
                        client.newCall(
                            Request.Builder().url(url)
                                .put(notifObj.toString().toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().close()
                    } catch (e: Exception) {
                        Log.e("FirebaseData", "notifyAdmin fallback write failed: ${e.message}")
                    }
                }

                // আসল push — সরাসরি FCM HTTP v1
                val tokens = FcmAdminService.fetchAdminTokens()
                val sent   = FcmAdminService.sendToTokens(tokens, title, body, data)
                Log.d("FirebaseData", "notifyAdmin($event): pushed to $sent/${tokens.size} admin device(s)")
            } catch (e: Exception) {
                Log.e("FirebaseData", "notifyAdmin: ${e.message}")
            }
        }
    }

    suspend fun reportQuestion(
        questionId: String,
        question  : String,
        issue     : String,
        userName  : String = "",
        userPhone : String = "",
        tab       : String = "",
        qsheet    : String = ""
    ) {
        withContext(Dispatchers.IO) {
            try {
                val auth        = authQuery()
                val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')

                // tab থেকে Firebase sheet name বের করো (qsheet না থাকলে)
                val resolvedSheet = qsheet.ifBlank {
                    when (tab.lowercase()) {
                        "qbank" -> "QBank"
                        "study" -> "Study"
                        else    -> "Quiz"
                    }
                }

                val jsonObj = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("question",   question.take(200))
                    addProperty("issue",      issue)
                    addProperty("userName",   userName)
                    addProperty("userPhone",  userPhone)
                    addProperty("tab",        tab)
                    addProperty("qsheet",     resolvedSheet)
                    addProperty("timestamp",  System.currentTimeMillis())
                    addProperty("status",     "pending")
                }

                val url  = "$firebaseBase/Reports.json$auth"
                val body = jsonObj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                Log.d("FirebaseData", "Report saved: HTTP ${resp.code}")
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
                Log.e("FirebaseData", "reportQuestion: ${e.message}")
            }
        }
    }

    // ── User Techniques ──

    suspend fun fetchTechniquesForQuestion(
        questionId: String,
        myUserId  : String
    ): ApiResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/${questionId}.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val json = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext ApiResult.Success(emptyList())
                val raw: Map<String, Map<String, Any>> = parseRowMap(json)
                val list = raw.map { (k, v) ->
                    com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to questionId))
                }.filter { t ->
                    t.userId == myUserId || (t.isPublic && t.isApproved())
                }.sortedByDescending { it.timestamp }
                ApiResult.Success(list)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun saveTechnique(
        questionId: String,
        userId    : String,
        userName  : String,
        text      : String,
        isPublic  : Boolean,
        type      : String = "technique"
    ): ApiResult<String> {
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
                    addProperty("type",       type)
                }
                val body     = obj.toString().toRequestBody("application/json".toMediaType())
                val req      = Request.Builder().url(url).post(body).build()
                val resp     = client.newCall(req).execute()
                val respJson = resp.body?.string() ?: "{}"
                val pushKey  = gson.fromJson(respJson, JsonObject::class.java)?.get("name")?.asString ?: ""
                resp.close()

                if (isPublic) {
                    notifyAdmin(event = if (type == "explanation") "explanation" else "technique", userName = userName, userPhone = userId, extra = text.take(60))
                }
                ApiResult.Success(pushKey)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun updateTechnique(
        questionId: String,
        pushKey   : String,
        text      : String,
        isPublic  : Boolean,
        type      : String = "technique"
    ): ApiResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val obj  = JsonObject().apply {
                    addProperty("text",      text)
                    addProperty("isPublic",  isPublic)
                    addProperty("status",    if (isPublic) "pending" else "approved")
                    addProperty("timestamp", System.currentTimeMillis())
                    addProperty("type",      type)
                }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                ApiResult.Success(Unit)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun deleteTechnique(questionId: String, pushKey: String): ApiResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val req  = Request.Builder().url(url).delete().build()
                client.newCall(req).execute().close()
                ApiResult.Success(Unit)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun fetchPendingTechniques(): ApiResult<List<com.hanif.smartstudy.data.model.UserTechnique>> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques.json$auth"
                val req  = Request.Builder().url(url).get().build()
                val json = client.newCall(req).execute().body?.string() ?: "null"
                if (json == "null") return@withContext ApiResult.Success(emptyList())
                val raw: Map<String, Map<String, Map<String, Any>>> = parseNestedRowMap(json)
                val list = raw.flatMap { (qId, entries) ->
                    entries.map { (k, v) ->
                        com.hanif.smartstudy.data.model.UserTechnique.fromMap(k, v + mapOf("questionId" to qId))
                    }
                }.filter { it.isPublic && it.isPending() }.sortedByDescending { it.timestamp }
                ApiResult.Success(list)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    suspend fun updateTechniqueStatus(
        questionId: String,
        pushKey   : String,
        status    : String
    ): ApiResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/UserTechniques/$questionId/$pushKey.json$auth"
                val obj  = JsonObject().apply { addProperty("status", status) }
                val body = obj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
                ApiResult.Success(Unit)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Network error") }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ADMIN FUNCTIONS
    // ══════════════════════════════════════════════════════════

    // ── Firebase path-safe tag encoding ───────────────────────────────────────
    // Audience tags যেমন "Masters 1", "Honours 2", "Class 10" — এগুলোতে space আছে।
    // Firebase RTDB REST path segment এ space allowed নয়, তাই URL-encode করতে হয়।
    // "Job" বা অন্য simple ASCII tag গুলো encode করলেও অপরিবর্তিত থাকে।
    private fun String.toFirebasePathSegment(): String =
        java.net.URLEncoder.encode(this.trim().ifBlank { "Job" }, "UTF-8").replace("+", "%20")

    /**
     * Admin: একটা নির্দিষ্ট mode + audience tag এর সব subject এর serial একসাথে সেট করো।
     * Path: SubjectOrder/{mode}/{encodedTag} — PUT দিয়ে এই node সম্পূর্ণ replace হয়।
     * এই node এখন একটি নির্দিষ্ট tag এর জন্যই dedicated — তাই PUT নিরাপদ (cross-tag collision নেই)।
     */
    suspend fun adminSetSubjectOrderBulk(mode: String, tag: String, order: Map<String, Int>): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val auth    = authQuery()
                val safeTag = tag.toFirebasePathSegment()
                val url     = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/SubjectOrder/$mode/$safeTag.json$auth"
                val obj     = JsonObject().apply { order.forEach { (k, v) -> addProperty(k, v) } }
                val body    = obj.toString().toRequestBody("application/json".toMediaType())
                val resp    = client.newCall(Request.Builder().url(url).put(body).build()).execute()
                val respBody = resp.body?.string() ?: ""
                val code    = resp.code
                resp.close()
                if (resp.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error("Firebase error: $code — $respBody")
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }

    /**
     * Admin: একটা নির্দিষ্ট mode + audience tag + subject এর ভিতরের সব subTopic এর serial সেট করো।
     * Path: SubTopicOrder/{mode}/{encodedTag} — PATCH দিয়ে শুধু এই subject এর entry বদলায়।
     * subject নাম path এ না বসিয়ে PATCH body তে key হিসেবে পাঠানো হয় বলে বাংলা/স্পেশাল
     * ক্যারেক্টার নিয়ে URL-encoding এর ঝামেলা নেই।
     */
    suspend fun adminSetSubTopicOrderBulk(mode: String, tag: String, subject: String, order: Map<String, Int>): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val auth       = authQuery()
                val safeTag    = tag.toFirebasePathSegment()
                val url        = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/SubTopicOrder/$mode/$safeTag.json$auth"
                val subjectObj = JsonObject().apply { order.forEach { (k, v) -> addProperty(k, v) } }
                val obj        = JsonObject().apply { add(subject, subjectObj) }
                val body       = obj.toString().toRequestBody("application/json".toMediaType())
                val resp       = client.newCall(Request.Builder().url(url).patch(body).build()).execute()
                val respBody   = resp.body?.string() ?: ""
                val code       = resp.code
                resp.close()
                if (resp.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error("Firebase error: $code — $respBody")
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }

    /** Admin: যেকোনো question এর field Firebase এ PATCH করো */
    suspend fun adminUpdateQuestionField(
        sheet  : String,
        rowKey : String,
        fields : Map<String, String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/$sheet/$rowKey.json$auth"
            android.util.Log.d("AdminEdit", "PATCH → $url | fields=$fields")
            com.hanif.smartstudy.util.RemoteLogger.d("AdminEdit", "PATCH → $sheet/$rowKey | fields=$fields | url_preview=${url.take(80)}")
            val obj  = JsonObject().apply { fields.forEach { (k, v) -> addProperty(k, v) } }
            val body = obj.toString().toRequestBody("application/json".toMediaType())
            val resp = client.newCall(Request.Builder().url(url).patch(body).build()).execute()
            val respBody = resp.body?.string() ?: ""
            val code = resp.code
            android.util.Log.d("AdminEdit", "Response: $code | $respBody")
            com.hanif.smartstudy.util.RemoteLogger.d("AdminEdit", "Response: $code | body=${respBody.take(200)}")
            resp.close()
            if (resp.isSuccessful) ApiResult.Success(Unit)
            else {
                com.hanif.smartstudy.util.RemoteLogger.e("AdminEdit", "FAILED: $code — $respBody")
                ApiResult.Error("Firebase error: $code — $respBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminEdit", "Exception: ${e.message}", e)
            com.hanif.smartstudy.util.RemoteLogger.e("AdminEdit", "Exception: ${e.message ?: "unknown"}")
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    /** Admin: Options swap করো — Firebase এ option1-4 + correct একসাথে update */
    suspend fun adminSwapOptions(
        sheet    : String,
        rowKey   : String,
        options  : Map<String, String>,
        newAnswer: String
    ): ApiResult<Unit> {
        val fields = options.toMutableMap()
        fields["correct"] = newAnswer
        return adminUpdateQuestionField(sheet, rowKey, fields)
    }

    /** Admin: নতুন question Firebase এ push করো */
    suspend fun adminAddQuestion(sheet: String, fields: Map<String, String>): ApiResult<String> =
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
                    ApiResult.Success(pushKey)
                } else ApiResult.Error("Firebase error: ${resp.code}")
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Add failed") }
        }

    /** Admin: Firebase /Reports থেকে pending reports fetch করো */
    suspend fun fetchPendingReports(): ApiResult<List<ReportedQuestion>> =
        withContext(Dispatchers.IO) {
            try {
                val auth = authQuery()
                val url  = "${BuildConfig.FIREBASE_URL.trimEnd('/')}/Reports.json$auth"
                val json = client.newCall(Request.Builder().url(url).get().build())
                    .execute().body?.string() ?: "null"
                if (json == "null") return@withContext ApiResult.Success(emptyList())
                val raw: Map<String, Map<String, Any>> = parseRowMap(json)
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
                ApiResult.Success(list)
            } catch (e: Exception) { ApiResult.Error(e.message ?: "Fetch failed") }
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
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
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

            // ── ২. Reporter এর FCM token সরাসরি Firebase থেকে lookup ──
            // (Reports নোডে যে কেউ লিখতে পারে, তাই userPhone কে অবশ্যই sanitize করে
            //  নিতে হবে — না হলে ক্ষতিকর phone value দিয়ে path-injection সম্ভব)
            val phoneEncoded = com.hanif.smartstudy.util.PhoneValidator.sanitize(userPhone)
            if (phoneEncoded != null) {
                val fcmToken = FcmAdminService.fetchTokenForPhone(phoneEncoded)

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
                        val ok = FcmAdminService.sendToToken(
                            token = fcmToken,
                            title = "✅ রিপোর্ট সমাধান হয়েছে!",
                            body  = notifMsg,
                            data  = mapOf(
                                "type"       to "report_resolved",
                                "url"        to "reports",
                                "questionId" to questionId,
                                "tab"        to tab
                            ).filterValues { it.isNotBlank() }
                        )
                        Log.d("FirebaseData", "Report notification sent to $userPhone: $ok")
                    } catch (e: Exception) {
                        Log.e("FirebaseData", "FCM send failed: ${e.message}")
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
                    Log.e("FirebaseData", "Notifications fallback write failed: ${e.message}")
                }
            }

            ApiResult.Success(Unit)
        } catch (e: Exception) { ApiResult.Error(e.message ?: "Update failed") }
    }

    /** Admin: Bulk audience tag update */
    suspend fun adminBulkAudienceUpdate(
        sheet    : String,
        subject  : String,
        subTopic : String,
        newTag   : String
    ): ApiResult<Int> = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val base = BuildConfig.FIREBASE_URL.trimEnd('/')
            val json = client.newCall(
                Request.Builder().url("$base/$sheet.json$auth").get().build()
            ).execute().body?.string() ?: "null"
            if (json == "null") return@withContext ApiResult.Error("Sheet empty")

            val raw: Map<String, Map<String, Any>> = parseRowMap(json)
            val matching = raw.filter { (_, v) ->
                val s  = v["subject"]?.toString()?.trim() ?: ""
                val st = (v["sub_topic"] ?: v["subTopic"])?.toString()?.trim() ?: ""
                s.equals(subject.trim(), ignoreCase = true) &&
                (subTopic.isBlank() || st.equals(subTopic.trim(), ignoreCase = true))
            }
            if (matching.isEmpty()) return@withContext ApiResult.Error("কোনো matching প্রশ্ন নেই")

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
            ApiResult.Success(updated)
        } catch (e: Exception) { ApiResult.Error(e.message ?: "Bulk update failed") }
    }

    /**
     * Admin: একটি Subject অথবা SubTopic এর নাম rename করো।
     * - renameSubTopic = false হলে: subject নাম বদলাবে (subTopic ফাঁকা রাখলে সব sub_topic সহ পুরো subject rename হবে)
     * - renameSubTopic = true হলে: শুধু sub_topic নাম বদলাবে (subject অপরিবর্তিত থাকবে, subject দিয়ে scope করা হয়)
     * sheets প্যারামিটারে একটি বা একাধিক sheet ("Quiz","QBank","Study") দেওয়া যাবে।
     * প্রতিটি sheet এ matching সব row খুঁজে বের করে শুধু subject/sub_topic ফিল্ড PATCH করা হয় —
     * বাকি সব ফিল্ড (question, options, answer, audience ইত্যাদি) অপরিবর্তিত থাকে।
     */
    suspend fun adminRenameSubjectOrTopic(
        sheets         : List<String>,
        oldSubject     : String,
        oldSubTopic    : String,   // ফাঁকা হলে পুরো subject rename (renameSubTopic=false এর সময়)
        newName        : String,
        renameSubTopic : Boolean
    ): ApiResult<Int> = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val base = BuildConfig.FIREBASE_URL.trimEnd('/')
            var totalUpdated = 0
            var anySheetHadData = false

            for (sheet in sheets) {
                val json = client.newCall(
                    Request.Builder().url("$base/$sheet.json$auth").get().build()
                ).execute().body?.string() ?: "null"
                if (json == "null") continue

                val raw: Map<String, Map<String, Any>> = parseRowMap(json)
                if (raw.isEmpty()) continue
                anySheetHadData = true

                val matching = raw.filter { (_, v) ->
                    val s  = v["subject"]?.toString()?.trim() ?: ""
                    val st = (v["sub_topic"] ?: v["subTopic"])?.toString()?.trim() ?: ""
                    if (renameSubTopic) {
                        // SubTopic rename — subject এর মধ্যেই scope, exact sub_topic match লাগবে
                        s.equals(oldSubject.trim(), ignoreCase = true) &&
                        st.equals(oldSubTopic.trim(), ignoreCase = true)
                    } else {
                        // Subject rename — পুরো subject এর সব প্রশ্ন (sub_topic যাই হোক)
                        s.equals(oldSubject.trim(), ignoreCase = true)
                    }
                }
                if (matching.isEmpty()) continue

                matching.forEach { (key, _) ->
                    val obj = JsonObject().apply {
                        if (renameSubTopic) addProperty("sub_topic", newName.trim())
                        else addProperty("subject", newName.trim())
                    }
                    val body = obj.toString().toRequestBody("application/json".toMediaType())
                    val resp = client.newCall(
                        Request.Builder().url("$base/$sheet/$key.json$auth").patch(body).build()
                    ).execute()
                    if (resp.isSuccessful) totalUpdated++
                    resp.close()
                }
            }

            if (!anySheetHadData) return@withContext ApiResult.Error("কোনো sheet এ ডেটা নেই")
            if (totalUpdated == 0) return@withContext ApiResult.Error("কোনো matching প্রশ্ন পাওয়া যায়নি")
            ApiResult.Success(totalUpdated)
        } catch (e: Exception) { ApiResult.Error(e.message ?: "Rename failed") }
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

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}
