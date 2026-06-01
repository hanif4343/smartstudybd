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

    suspend fun login(phone: String, password: String): GasResult<Map<String, Any>> =
        withContext(Dispatchers.IO) {
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
                if (map["status"] == "ok") GasResult.Success(map)
                else GasResult.Error(map["message"]?.toString() ?: "Login failed")
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }

    suspend fun signup(
        name: String, phone: String, password: String,
        userType: String, classLevel: String
    ): GasResult<Map<String, Any>> = withContext(Dispatchers.IO) {
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
            if (map["status"] == "ok") GasResult.Success(map)
            else GasResult.Error(map["message"]?.toString() ?: "Signup failed")
        } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
    }

    suspend fun getUserFromFirebase(phone: String): GasResult<Map<String, Any>> =
        withContext(Dispatchers.IO) {
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
                if (user != null) GasResult.Success(user)
                else GasResult.Error("ব্যবহারকারী পাওয়া যায়নি")
            } catch (e: Exception) { GasResult.Error(e.message ?: "Network error") }
        }

    suspend fun reportQuestion(questionId: String, question: String, issue: String) =
        withContext(Dispatchers.IO) {
            try {
                // Firebase RTDB তে Reports node এ সরাসরি লেখা
                val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')
                val secretKey    = BuildConfig.SECRET_KEY
                val authParam    = if (secretKey.isNotBlank() && !secretKey.contains("%%"))
                    "?auth=$secretKey" else ""

                val jsonObj = JsonObject().apply {
                    addProperty("questionId", questionId)
                    addProperty("question",   question.take(200))
                    addProperty("issue",      issue)
                    addProperty("timestamp",  System.currentTimeMillis())
                    addProperty("status",     "pending")
                }

                // POST to Firebase RTDB — auto push key তৈরি হবে
                val url  = "${'$'}firebaseBase/Reports.json${'$'}authParam"
                val body = jsonObj.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).post(body).build()
                val resp = client.newCall(req).execute()
                Log.d("GAS", "Report saved: HTTP ${'$'}{resp.code} → ${'$'}url")
                resp.close()
            } catch (e: Exception) {
                Log.e("GAS", "reportQuestion: ${'$'}{e.message}")
            }
        }
}

sealed class GasResult<out T> {
    data class Success<T>(val data: T) : GasResult<T>()
    data class Error(val message: String) : GasResult<Nothing>()
}
