package com.hanif.smartstudy.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * RemoteLogger — পুরো অ্যাপের সব Log.d/e/w/i কল আর crash এই এখানে গিয়ে
 * Firebase RTDB তে "DebugLogs/{phone}/{date}/{key}" path এ জমা হয়।
 * PC/logcat ছাড়াই Admin Panel থেকে এই log গুলো দেখা যাবে।
 */
object RemoteLogger {

    private const val TAG = "RemoteLogger"
    private const val MAX_BUFFER   = 300   // মেমোরি তে কতগুলো রাখবে
    private const val FLUSH_AT     = 25    // এতগুলো জমা হলে auto-flush
    private const val MAX_KEEP_FB  = 500   // Firebase এ ইউজার প্রতি কতগুলো রাখবে

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var phone   : String = "guest"
    @Volatile private var initialized = false
    private val seq = AtomicInteger(0)
    // sdf ইচ্ছাকৃতভাবে instance field না — SimpleDateFormat thread-safe না, আর
    // flushBlocking() একাধিক থ্রেড (crash handler + normal flush) থেকে চলতে পারে।
    // তাই ব্যবহারের জায়গাতেই (flushBlocking) নতুন করে বানানো হয়।

    private val buffer = Collections.synchronizedList(mutableListOf<JSONObject>())

    private val FB_URL get() = BuildConfig.FIREBASE_URL.trimEnd('/')

    /** App start এ একবার call করো (SmartStudyApp.onCreate) */
    fun init(context: Context, phone: String?) {
        this.phone = phone?.takeIf { it.isNotBlank() } ?: "guest"
        if (initialized) return
        initialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("CRASH", "FATAL crash on thread ${thread.name}: " +
                        Log.getStackTraceString(throwable))
                flushBlocking()
            } catch (_: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        i("App", "App started — version=${BuildConfig.VERSION_NAME}, sdk=${Build.VERSION.SDK_INT}, model=${Build.MODEL}")
    }

    /** Login এর পর phone update করো */
    fun setUserPhone(phone: String) {
        this.phone = phone.ifBlank { "guest" }
    }

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)
    fun e(tag: String, msg: String, t: Throwable) =
        log("E", tag, "$msg\n${Log.getStackTraceString(t)}")

    private fun log(level: String, tag: String, msg: String) {
        when (level) {
            "E" -> Log.e(tag, msg)
            "W" -> Log.w(tag, msg)
            "I" -> Log.i(tag, msg)
            else -> Log.d(tag, msg)
        }
        try {
            val entry = JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("level", level)
                put("tag", tag)
                put("msg", msg.take(2000)) // খুব বড় msg কেটে দাও
                put("phone", phone)
            }
            buffer.add(entry)
            if (buffer.size > MAX_BUFFER) buffer.removeAt(0)
            if (level == "E" || buffer.size >= FLUSH_AT) flush()
        } catch (_: Exception) {
        }
    }

    /** ব্যাকগ্রাউন্ডে Firebase এ পাঠাও */
    fun flush() {
        if (buffer.isEmpty()) return
        scope.launch { flushBlocking() }
    }

    /** crash হ্যান্ডলারে synchronously পাঠানোর জন্য — main thread হলেও যেন
     *  NetworkOnMainThreadException না হয় তাই আলাদা থ্রেডে চালাই এবং join করি। */
    private fun flushBlocking() {
        if (buffer.isEmpty()) return
        val toSend: List<JSONObject>
        synchronized(buffer) {
            toSend = ArrayList(buffer)
            buffer.clear()
        }
        val worker = Thread {
            try {
                val secret = runCatching {
                    kotlinx.coroutines.runBlocking { FirebaseTokenProvider.getToken() }
                }.getOrDefault("")
                val safePhone = phone.replace(Regex("[.#$\\[\\]/]"), "_")
                val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                val patch = JSONObject()
                toSend.forEach { entry ->
                    val key = "${entry.optLong("ts")}_${seq.incrementAndGet()}"
                    patch.put(key, entry)
                }
                val authQ = if (secret.isNotBlank()) "?auth=$secret" else ""
                val url = "$FB_URL/DebugLogs/$safePhone/$today.json$authQ"
                val body = patch.toString().toRequestBody("application/json".toMediaType())
                val req  = Request.Builder().url(url).patch(body).build()
                client.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "flush failed: ${e.message}")
            }
        }
        worker.start()
        try { worker.join(8000) } catch (_: InterruptedException) {}
    }
}
