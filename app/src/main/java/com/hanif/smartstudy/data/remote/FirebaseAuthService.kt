package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object FirebaseAuthService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun hashPassword(pass: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pass.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── LOGIN ──
    // GAS doGet?action=verifyLogin ব্যবহার করে
    // GAS নিজেই Firebase থেকে user খুঁজে password verify করে
    // plain text এবং hash দুটোই GAS এ handle হয়
    suspend fun verifyLogin(phone: String, password: String, gasUrl: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                // Method 1: GAS verifyLogin (recommended)
                // GAS doGet এ verifyLogin action আছে যেটা sheet থেকে verify করে
                val url = "$gasUrl?action=verifyLogin" +
                    "&phone=${encode(phone)}" +
                    "&password=${encode(password)}"

                Log.d("Login", "GAS URL: $url")

                val req  = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                Log.d("Login", "GAS response: $body")

                return@withContext parseGasLoginResponse(body, phone, password)

            } catch (e: Exception) {
                Log.e("Login", "Error: ${e.message}")
                AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
            }
        }

    private fun parseGasLoginResponse(body: String, phone: String, password: String): AuthResult {
        if (body.isBlank()) return AuthResult.Error("GAS থেকে কোনো response পাওয়া যায়নি")

        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(body, type)

            Log.d("Login", "Parsed map: $map")

            val result = map["result"]?.toString() ?: ""
            val status = map["status"]?.toString() ?: ""

            when {
                // GAS doGet verifyLogin: result="success" + user object
                result == "success" -> {
                    val userMap = (map["user"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value ?: "" }
                        ?: map  // user আলাদা field এ না থাকলে root map ই user
                    @Suppress("UNCHECKED_CAST")
                    AuthResult.Success(userMap as Map<String, Any>)
                }

                // GAS doPost login: status="ok" + user fields directly in root
                status == "ok" -> {
                    AuthResult.Success(map)
                }

                // Error cases
                result == "error" || status == "error" -> {
                    val msg = (map["error"] ?: map["message"] ?: "লগইন ব্যর্থ").toString()
                    AuthResult.Error(translateError(msg))
                }

                else -> AuthResult.Error("অপ্রত্যাশিত response: $body")
            }
        } catch (e: Exception) {
            Log.e("Login", "Parse error: ${e.message}, body: $body")
            AuthResult.Error("Response parse করা যায়নি")
        }
    }

    private fun translateError(msg: String): String = when {
        msg.contains("wrong password", true)  -> "পাসওয়ার্ড ভুল হয়েছে"
        msg.contains("not found", true)       -> "এই ফোন নম্বর দিয়ে কোনো অ্যাকাউন্ট নেই"
        msg.contains("missing", true)         -> "ফোন নম্বর ও পাসওয়ার্ড দিন"
        msg.contains("inactive", true)        -> "অ্যাকাউন্ট নিষ্ক্রিয়। Admin এর সাথে যোগাযোগ করুন।"
        else -> msg
    }

    // ── SIGNUP ──
    // GAS doPost signup — FormBody দিয়ে
    suspend fun signup(
        name: String, phone: String, password: String,
        userType: String, classLevel: String, gasUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // FormBody — GAS e.parameter দিয়ে পড়বে
            val formBody = FormBody.Builder()
                .add("action",     "signup")
                .add("name",       name)
                .add("phone",      phone)
                .add("password",   hashPassword(password))  // hash করে save
                .add("userType",   userType)
                .add("classLevel", classLevel)
                .add("status",     "Active")
                .add("role",       "User")
                .build()

            val req  = Request.Builder().url(gasUrl).post(formBody).build()
            val resp = client.newCall(req).execute().body?.string() ?: ""

            Log.d("Signup", "GAS response: $resp")

            return@withContext try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resp, type)
                val s = (map["status"] ?: map["result"] ?: "").toString()

                if (s == "ok" || s == "success") {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone,
                        "UserType" to userType, "ClassLevel" to classLevel,
                        "Status" to "Active", "Role" to "User"
                    ))
                } else {
                    val msg = (map["message"] ?: map["error"] ?: "Signup ব্যর্থ").toString()
                    // Duplicate phone
                    if (msg.contains("already", true) || msg.contains("duplicate", true) ||
                        msg.contains("exists", true))
                        AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
                    else
                        AuthResult.Error(msg)
                }
            } catch (e: Exception) {
                if (resp.contains("ok", true) || resp.contains("success", true))
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone,
                        "UserType" to userType, "ClassLevel" to classLevel,
                        "Status" to "Active", "Role" to "User"
                    ))
                else
                    AuthResult.Error("Signup ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    private fun encode(s: String) =
        java.net.URLEncoder.encode(s, "UTF-8")
}

sealed class AuthResult {
    data class Success(val userData: Map<String, Any>) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
