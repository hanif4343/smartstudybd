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
}

sealed class GasResult<out T> {
    data class Success<T>(val data: T) : GasResult<T>()
    data class Error(val message: String) : GasResult<Nothing>()
}
