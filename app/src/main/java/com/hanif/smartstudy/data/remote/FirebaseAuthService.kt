package com.hanif.smartstudy.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object FirebaseAuthService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val FIREBASE_URL = "https://smartentrydb-default-rtdb.firebaseio.com/"

    fun hashPassword(pass: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── LOGIN — Firebase RTDB থেকে verify ──
    suspend fun verifyLogin(phone: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val req  = Request.Builder().url("${FIREBASE_URL}Users.json").get().build()
                val body = client.newCall(req).execute().body?.string()

                if (body.isNullOrEmpty() || body == "null")
                    return@withContext AuthResult.Error("ইন্টারনেট সংযোগ চেক করুন")

                val hashedInput = hashPassword(password)
                val normPhone   = phone.trim()
                val normNo0     = normPhone.trimStart('0')

                val users = parseUsers(body)
                val found = users.values.find { u ->
                    val fb = (u["Phone"] ?: u["phone"] ?: "").toString()
                        .trim().replace("'", "")
                    fb == normPhone || fb.trimStart('0') == normNo0
                } ?: return@withContext AuthResult.Error("ফোন নম্বর পাওয়া যায়নি")

                val stored = (found["Password"] ?: found["password"] ?: "").toString().trim()
                if (stored != hashedInput && stored != password)
                    return@withContext AuthResult.Error("পাসওয়ার্ড ভুল হয়েছে")

                val status = (found["Status"] ?: found["status"] ?: "Active").toString()
                if (status.equals("Inactive", true) || status.equals("Banned", true))
                    return@withContext AuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। অ্যাডমিনের সাথে যোগাযোগ করুন।")

                AuthResult.Success(found)

            } catch (e: Exception) {
                AuthResult.Error("সংযোগ সমস্যা: ${e.message}")
            }
        }

    // ── SIGNUP — GAS এ POST করো ──
    suspend fun signup(
        name: String, phone: String, password: String,
        userType: String, classLevel: String, gasUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // Firebase এ phone আছে কিনা check
            if (checkPhoneExists(phone))
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")

            val payload = mapOf(
                "action"     to "signup",
                "name"       to name,
                "phone"      to phone,
                "password"   to hashPassword(password),
                "userType"   to userType,
                "classLevel" to classLevel
            )
            val body = gson.toJson(payload).toRequestBody(JSON)
            val req  = Request.Builder().url(gasUrl).post(body).build()
            val resp = client.newCall(req).execute().body?.string() ?: ""

            // Response parse — status/result দুটোই check
            return@withContext try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resp, type)
                val s = (map["status"] ?: map["result"] ?: "").toString()
                if (s == "ok" || s == "success") {
                    AuthResult.Success(mapOf("Name" to name, "Phone" to phone,
                        "UserType" to userType, "ClassLevel" to classLevel,
                        "Status" to "Active", "Role" to "User"))
                } else {
                    AuthResult.Error((map["message"] ?: map["error"] ?: "Signup ব্যর্থ").toString())
                }
            } catch (e: Exception) {
                if (resp.contains("ok", true) || resp.contains("success", true))
                    AuthResult.Success(mapOf("Name" to name, "Phone" to phone,
                        "UserType" to userType, "ClassLevel" to classLevel,
                        "Status" to "Active", "Role" to "User"))
                else
                    AuthResult.Error("Signup ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    private suspend fun checkPhoneExists(phone: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = client.newCall(
                    Request.Builder().url("${FIREBASE_URL}Users.json").get().build()
                ).execute().body?.string() ?: return@withContext false
                val norm = phone.trim().trimStart('0')
                parseUsers(body).values.any { u ->
                    val fb = (u["Phone"] ?: u["phone"] ?: "").toString()
                        .trim().replace("'", "").trimStart('0')
                    fb == norm
                }
            } catch (e: Exception) { false }
        }

    private fun parseUsers(body: String): Map<String, Map<String, Any>> {
        return try {
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            gson.fromJson(body, type) ?: emptyMap()
        } catch (e: Exception) {
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>?> = gson.fromJson(body, type) ?: emptyList()
                list.filterNotNull().mapIndexed { i, m -> i.toString() to m }.toMap()
            } catch (e2: Exception) { emptyMap() }
        }
    }
}

sealed class AuthResult {
    data class Success(val userData: Map<String, Any>) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
