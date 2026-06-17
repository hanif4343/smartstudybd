package com.hanif.smartstudy.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.PhoneValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import com.hanif.smartstudy.BuildConfig // BuildConfig ইমপোর্ট নিশ্চিত করার জন্য

object FirebaseAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS   = 256

    // ── নতুন পাসওয়ার্ড hashing — salted PBKDF2 (প্রতিটা ইউজারের আলাদা random salt) ──
    // ফরম্যাট: "pbkdf2:<iterations>:<saltBase64>:<hashBase64>"
    fun hashPasswordSecure(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Bytes(password, salt, PBKDF2_ITERATIONS)
        return "pbkdf2:$PBKDF2_ITERATIONS:" +
            "${Base64.encodeToString(salt, Base64.NO_WRAP)}:" +
            Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun pbkdf2Bytes(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
        val key  = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return key.encoded
    }

    private fun verifyPasswordSecure(password: String, stored: String): Boolean = try {
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != "pbkdf2") false
        else {
            val iterations = parts[1].toIntOrNull() ?: throw IllegalArgumentException("bad iterations")
            val salt        = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected    = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual      = pbkdf2Bytes(password, salt, iterations)
            MessageDigest.isEqual(actual, expected)
        }
    } catch (e: Exception) { false }

    // ── পুরনো (unsalted, single-round SHA-256) hash — শুধু আগের রেকর্ড verify করার জন্য রাখা ──
    // এটা দিয়ে নতুন কোনো পাসওয়ার্ড সেভ করা হয় না, শুধু legacy login চেক ও তারপর upgrade করার জন্য।
    private fun legacyHashPassword(pass: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Login এর সময় পুরনো ফরম্যাটে পাওয়া গেলে, এখনই salted PBKDF2 এ upgrade করো ──
    private suspend fun upgradePasswordHash(phone: String, plainPassword: String, firebaseUrl: String) {
        try {
            val newHash = hashPasswordSecure(plainPassword)
            val auth    = authQuery()
            val baseUrl = firebaseUrl.trimEnd('/')
            val url     = "$baseUrl/Users/$phone.json$auth"   // phone এখানে আগেই sanitize করা (digits-only)
            val body    = gson.toJson(mapOf("Password" to newHash)).toRequestBody(JSON_MEDIA_TYPE)
            client.newCall(Request.Builder().url(url).patch(body).build()).execute().close()
            Log.d("Login", "Password hash upgraded to PBKDF2 for $phone")
        } catch (e: Exception) {
            Log.e("Login", "upgradePasswordHash failed: ${e.message}")
        }
    }

    /** Gson সব number কে Double করে — এই function সঠিক String দেয় */
    private fun anyToString(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is Double -> {
                if (value == Math.floor(value) && !value.isInfinite()) {
                    value.toLong().toString()
                } else value.toString()
            }
            is Long    -> value.toString()
            is Int     -> value.toString()
            is Boolean -> value.toString()
            else       -> value.toString()
        }
    }

    /** Firebase Database Secret দিয়ে auth query param বানাও */
    private fun authQuery(): String {
        val secret = BuildConfig.FIREBASE_DB_SECRET
        return if (secret.isNotBlank()) "?auth=$secret" else ""
    }

    /**
     * Users node থেকে phone দিয়ে user খোঁজে।
     * key = phone number অথবা numeric index — দুটোই handle করে।
     */
    private suspend fun findUserByPhone(phone: String, firebaseUrl: String): Map<String, Any>? =
        withContext(Dispatchers.IO) {
            try {
                val normPhone = phone.trim()
                val auth = authQuery()
                val baseUrl = firebaseUrl.trimEnd('/')
                val allUrl = "$baseUrl/Users.json$auth"
                Log.d("Login", "Scanning users for phone: $normPhone")
                val allReq  = Request.Builder().url(allUrl).get().build()
                val allResp = client.newCall(allReq).execute()
                val allBody = allResp.body?.string() ?: ""

                if (allBody.isBlank() || allBody == "null") return@withContext null

                val type = object : TypeToken<Map<String, Any>>() {}.type
                val rawMap: Map<String, Any> = gson.fromJson(allBody, type)

                for ((key, value) in rawMap) {
                    when {
                        key == normPhone -> {
                            if (value is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                return@withContext value as Map<String, Any>
                            }
                        }
                        value is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val userMap = value as Map<String, Any>
                            val storedPhone = anyToString(userMap["Phone"] ?: userMap["phone"]).trim()
                            if (storedPhone == normPhone) {
                                Log.d("Login", "Found: ${userMap["Name"]}")
                                return@withContext userMap
                            }
                        }
                    }
                }
                Log.d("Login", "User not found: $normPhone")
                null
            } catch (e: Exception) {
                Log.e("Login", "findUserByPhone error: ${e.message}")
                null
            }
        }

    // ── LOGIN ──
    suspend fun verifyLogin(phone: String, password: String, firebaseUrl: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val cleanPhone = PhoneValidator.sanitize(phone)
                    ?: return@withContext AuthResult.Error("ফোন নম্বরটি সঠিক ফরম্যাটে নেই")

                val userMap = findUserByPhone(cleanPhone, firebaseUrl)
                    ?: return@withContext AuthResult.Error("এই ফোন নম্বর দিয়ে কোনো অ্যাকাউন্ট নেই")

                val savedPassword = anyToString(userMap["Password"] ?: userMap["password"] ?: "")
                val inputPassword = password.trim()

                val passwordMatches = when {
                    savedPassword.startsWith("pbkdf2:") ->
                        verifyPasswordSecure(inputPassword, savedPassword)

                    savedPassword == legacyHashPassword(inputPassword) -> {
                        // পুরনো unsalted SHA-256 ফরম্যাট — মিলেছে, এখনই salted PBKDF2 এ upgrade করো
                        upgradePasswordHash(cleanPhone, inputPassword, firebaseUrl)
                        true
                    }

                    savedPassword.isNotBlank() && savedPassword == inputPassword -> {
                        // ⚠️ একদম পুরনো plaintext-stored রেকর্ড (যদি থাকে) — এখনই upgrade করে দেওয়া হলো,
                        // যাতে Firebase এ plain-text password আর না থাকে।
                        Log.w("Login", "Plaintext password record for $cleanPhone — upgrading now")
                        upgradePasswordHash(cleanPhone, inputPassword, firebaseUrl)
                        true
                    }

                    else -> false
                }

                if (passwordMatches) {
                    val status = (userMap["Status"] ?: userMap["status"] ?: "Active").toString().lowercase()
                    if (status == "inactive") {
                        AuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin এর সাথে যোগাযোগ করুন।")
                    } else {
                        AuthResult.Success(userMap)
                    }
                } else {
                    AuthResult.Error("পাসওয়ার্ড ভুল হয়েছে")
                }
            } catch (e: Exception) {
                Log.e("Login", "Error: ${e.message}")
                AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
            }
        }

    // ── GOOGLE SIGN-IN ──
    suspend fun googleSignIn(
        email: String,
        name: String,
        photoUrl: String,
        firebaseUrl: String
    ): GoogleAuthResult = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val baseUrl = firebaseUrl.trimEnd('/')
            val url  = "$baseUrl/Users.json$auth"
            Log.d("GoogleAuth", "Fetching users from Firebase")
            val req  = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (body.isBlank() || body == "null") {
                return@withContext GoogleAuthResult.NewUser(email, name, photoUrl)
            }

            val normEmail    = email.trim().lowercase()
            var matchedUser: Map<String, Any>? = null

            try {
                val type   = object : TypeToken<Map<String, Any>>() {}.type
                val rawMap: Map<String, Any> = gson.fromJson(body, type)
                for ((_, value) in rawMap) {
                    if (value is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val userMap = value as Map<String, Any>
                        val uEmail  = (userMap["Email"] ?: userMap["email"])
                            ?.toString()?.trim()?.lowercase() ?: ""
                        if (uEmail == normEmail) { matchedUser = userMap; break }
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleAuth", "Parse error: ${e.message}")
            }

            if (matchedUser != null) {
                val status = (matchedUser["Status"] ?: matchedUser["status"] ?: "Active")
                    .toString().lowercase()
                if (status == "inactive") {
                    return@withContext GoogleAuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin-এর সাথে যোগাযোগ করুন।")
                }
                GoogleAuthResult.ExistingUser(matchedUser)
            } else {
                GoogleAuthResult.NewUser(email = email, name = name, photoUrl = photoUrl)
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error: ${e.message}")
            GoogleAuthResult.Error("সংযোগ সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP WITH EMAIL (Google) ──
    suspend fun signupWithEmail(
        name: String,
        phone: String,
        email: String,
        password: String,
        picture: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = PhoneValidator.sanitize(phone)
                ?: return@withContext AuthResult.Error("ফোন নম্বরটি সঠিক ফরম্যাটে নেই (শুধু সংখ্যা দিন)")

            val existing = findUserByPhone(cleanPhone, firebaseUrl)
            if (existing != null) {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name"       to name,
                "Phone"      to cleanPhone,
                "Email" to email,
                "Password"   to hashPasswordSecure(password),
                "Picture"    to picture,
                "UserType"   to userType,
                "ClassLevel" to classLevel,
                "Status"     to "Active",
                "Role"       to "User"
            )

            val auth        = authQuery()
            val baseUrl = firebaseUrl.trimEnd('/')
            val url         = "$baseUrl/Users/$cleanPhone.json$auth"
            val requestBody = gson.toJson(userData).toRequestBody(JSON_MEDIA_TYPE)
            val req         = Request.Builder().url(url).put(requestBody).build()
            val resp        = client.newCall(req).execute()

            if (resp.isSuccessful) AuthResult.Success(userData)
            else AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে (HTTP ${resp.code})")
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP (Phone + Password) ──
    suspend fun signup(
        name: String,
        phone: String,
        password: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = PhoneValidator.sanitize(phone)
                ?: return@withContext AuthResult.Error("ফোন নম্বরটি সঠিক ফরম্যাটে নেই (শুধু সংখ্যা দিন)")

            val existing = findUserByPhone(cleanPhone, firebaseUrl)
            if (existing != null) {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name"       to name,
                "Phone"      to cleanPhone,
                "Email"      to "",
                "Password"   to hashPasswordSecure(password),
                "Picture"    to "",
                "UserType"   to userType,
                "ClassLevel" to classLevel,
                "Status"     to "Active",
                "Role"       to "User"
            )

            val auth        = authQuery()
            val baseUrl = firebaseUrl.trimEnd('/')
            val url         = "$baseUrl/Users/$cleanPhone.json$auth"
            val requestBody = gson.toJson(userData).toRequestBody(JSON_MEDIA_TYPE)
            val req         = Request.Builder().url(url).put(requestBody).build()
            val resp        = client.newCall(req).execute()

            if (resp.isSuccessful) AuthResult.Success(userData)
            else AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে (HTTP ${resp.code})")
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }
}

sealed class AuthResult {
    data class Success(val userData: Map<String, Any>) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class GoogleAuthResult {
    data class ExistingUser(val userData: Map<String, Any>) : GoogleAuthResult()
    data class NewUser(val email: String, val name: String, val photoUrl: String) : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}
