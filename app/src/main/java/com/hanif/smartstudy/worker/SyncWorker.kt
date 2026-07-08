package com.hanif.smartstudy.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.PendingQueue
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.ContentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SyncWorker:
 * 1. Pending offline queue সরাসরি Firebase এ sync করে (কোনো GAS নেই)
 * 2. Content (Study/Quiz/QBank) refresh করে cache-এ
 */
class SyncWorker(
    context: Context,
    params : WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG   = "SyncWorker"
    private val queue = PendingQueue(context)
    private val cache = ContentCache(context)
    private val gson  = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "SyncWorker started")
        var allSuccess = true

        // ── 1. Pending queue sync ──
        val pending = queue.getAll()
        Log.d(TAG, "Pending actions: ${pending.size}")

        for (action in pending) {
            val ok = syncAction(action)
            if (ok) {
                queue.remove(action.id)
                Log.d(TAG, "Synced: ${action.type} id=${action.id}")
            } else {
                queue.incrementRetry(action.id)
                allSuccess = false
                Log.w(TAG, "Failed to sync: ${action.type} retry=${action.retryCount+1}")
            }
        }

        // 5+ বার fail হলে drop করো
        queue.dropFailed()

        // ── 2. Content refresh (শুধু সার্ভারে আসলেই নতুন কিছু থাকলে) ──
        // আগে শুধু TTL (1 ঘণ্টা) দেখেই পুরো Quiz+QBank+Study রিফেচ হতো — কনটেন্ট বদলাক
        // বা না বদলাক। এখন প্রথমে ছোট "/meta/updatedAt" চেক করা হয়; সেটা লাস্ট সেভ করা
        // remoteUpdatedAt এর চেয়ে নতুন হলে তবেই পুরো ডেটা টানা হয়। meta node না থাকলে
        // (পুরনো/আনসাপোর্টেড ডেটাবেস) TTL fallback ব্যবহার হয়, যাতে ডেটা কখনো একদম আটকে না থাকে।
        val cached = cache.loadContent()
        val remoteUpdatedAt = ContentFetchService.fetchMetaUpdatedAt()
        val needsRefresh = when {
            cached == null -> true
            remoteUpdatedAt > 0L -> remoteUpdatedAt > cached.remoteUpdatedAt
            else -> cached.isStale(FALLBACK_TTL_MILLIS)
        }

        if (needsRefresh) {
            Log.d(TAG, "Content refresh needed (remote=$remoteUpdatedAt, cachedRemote=${cached?.remoteUpdatedAt})")
            when (val result = ContentFetchService.fetchAllContent()) {
                is ContentResult.Success -> {
                    val toSave = result.data.copy(
                        remoteUpdatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                    )
                    cache.saveContent(toSave)
                    Log.d(TAG, "Content refreshed: Study=${toSave.study.size}")
                }
                is ContentResult.Error -> {
                    Log.w(TAG, "Content refresh failed: ${result.message}")
                    allSuccess = false
                }
            }
        } else {
            Log.d(TAG, "Content unchanged on server, skipping full refetch")
        }

        if (allSuccess) Result.success() else Result.retry()
    }

    private suspend fun syncAction(action: com.hanif.smartstudy.data.local.PendingAction): Boolean {
        val payload = try {
            gson.fromJson(action.payload, Map::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "syncAction payload parse error: ${e.message}")
            return false
        }
        return when (action.type) {
            "quiz_answer"        -> syncQuizAnswer(payload)
            "xp_update"          -> syncXpUpdate(payload)
            "study_progress"     -> syncStudyProgress(payload)
            "admin_edit_question" -> syncAdminEdit(payload)
            else -> false
        }
    }

    // ── Quiz answer log — সরাসরি Firebase এ (GAS এর quizAnswer action এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncQuizAnswer(payload: Map<*, *>): Boolean {
        return try {
            val phone      = payload["phone"]?.toString() ?: return false
            val questionId = payload["questionId"]?.toString() ?: ""
            val isCorrect  = payload["isCorrect"]?.toString() ?: "false"
            val safePhone  = phone.replace("+", "").trim()
            if (safePhone.isBlank()) return false

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val url    = "$base/QuizAnswers/$safePhone.json?auth=$secret"

            val obj = com.google.gson.JsonObject().apply {
                addProperty("questionId", questionId)
                addProperty("isCorrect", isCorrect)
                addProperty("timestamp", System.currentTimeMillis())
            }
            val resp = client.newCall(
                Request.Builder().url(url)
                    .post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncQuizAnswer $safePhone/$questionId → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncQuizAnswer error: ${e.message}")
            false
        }
    }

    // ── XP update — সরাসরি Firebase Users/{phone}/XP পড়ে+লিখে (GAS এর updateXP এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncXpUpdate(payload: Map<*, *>): Boolean {
        return try {
            val phone = payload["phone"]?.toString() ?: return false
            val delta = payload["xpDelta"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val safePhone = phone.replace("+", "").trim()
            if (safePhone.isBlank() || delta == 0) return true

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val getUrl = "$base/Users/$safePhone.json?auth=$secret"

            val curJson = client.newCall(Request.Builder().url(getUrl).get().build())
                .execute().body?.string()
            val currentXp = if (!curJson.isNullOrBlank() && curJson != "null") {
                try { org.json.JSONObject(curJson).optInt("XP", 0) } catch (e: Exception) { 0 }
            } else 0
            val newXp = maxOf(0, currentXp + delta)

            val patchObj = com.google.gson.JsonObject().apply { addProperty("XP", newXp) }
            val resp = client.newCall(
                Request.Builder().url(getUrl)
                    .patch(patchObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncXpUpdate $safePhone: $currentXp + $delta = $newXp → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncXpUpdate error: ${e.message}")
            false
        }
    }

    // ── Study progress log — সরাসরি Firebase এ (GAS এর studyProgress action এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncStudyProgress(payload: Map<*, *>): Boolean {
        return try {
            val phone   = payload["phone"]?.toString() ?: return false
            val minutes = payload["minutes"]?.toString() ?: "0"
            val topic   = payload["topic"]?.toString() ?: ""
            val safePhone = phone.replace("+", "").trim()
            if (safePhone.isBlank()) return false

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val url    = "$base/StudyLog/$safePhone.json?auth=$secret"

            val obj = com.google.gson.JsonObject().apply {
                addProperty("minutes", minutes)
                addProperty("topic", topic)
                addProperty("timestamp", System.currentTimeMillis())
            }
            val resp = client.newCall(
                Request.Builder().url(url)
                    .post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncStudyProgress $safePhone: ${minutes}min/$topic → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncStudyProgress error: ${e.message}")
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminEdit(payload: Map<*, *>): Boolean {
        return try {
            val sheet      = payload["sheet"]?.toString() ?: return false
            val questionId = payload["questionId"]?.toString() ?: return false
            val fields     = payload["fields"] as? Map<String, String> ?: return false
            if (questionId.isBlank() || fields.isEmpty()) return false

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val url    = "$base/$sheet/$questionId.json?auth=$secret"

            val jsonObj = com.google.gson.JsonObject().apply {
                fields.forEach { (k, v) -> addProperty(k, v) }
            }
            val body = jsonObj.toString()
                .toRequestBody("application/json".toMediaType())
            val resp = client.newCall(
                okhttp3.Request.Builder().url(url).patch(body).build()
            ).execute()
            val code = resp.code
            val respBody = resp.body?.string() ?: ""
            resp.close()
            Log.d(TAG, "syncAdminEdit $sheet/$questionId → $code $respBody")
            val ok = resp.isSuccessful
            // অফলাইনে করা admin edit sync হলে meta touch করো, নইলে অন্য ডিভাইস বুঝবে না নতুন কনটেন্ট আছে
            if (ok) touchMeta(secret, base)
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminEdit error: ${e.message}")
            false
        }
    }

    // অফলাইনে করা admin edit sync হওয়ার পর "/meta/updatedAt" আপডেট করে দেয়, যাতে অন্য
    // ডিভাইসের lightweight check এই edit-টা ধরতে পারে (touchMetaUpdatedAt এর ছোট সংস্করণ)।
    private suspend fun touchMeta(secret: String, base: String) {
        try {
            val url  = "$base/meta/updatedAt.json?auth=$secret"
            val body = System.currentTimeMillis().toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "touchMeta failed: ${e.message}")
        }
    }

    companion object {
        const val WORK_NAME = "SmartStudySyncWork"

        // meta node না থাকলে (পুরনো ডেটাবেস) safety-net TTL — 12 ঘণ্টা।
        // আগে 1 ঘণ্টা ছিল, যেটা meta-check না থাকা অবস্থায় প্রতি ঘণ্টায় পুরো
        // Quiz+QBank+Study রিফেচ করাতো, কনটেন্ট বদলাক বা না বদলাক।
        private const val FALLBACK_TTL_MILLIS = 12 * 60 * 60 * 1000L

        // ── Internet আসলে একবার run ──
        fun scheduleOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        // ── প্রতি ৬ ঘণ্টায় periodic content refresh ──
        // আগে ১ ঘণ্টা ছিল, TTL-ও ১ ঘণ্টা — ফলে অ্যাপ চালু/ব্যাকগ্রাউন্ডে থাকলেই প্রতি ঘণ্টায়
        // পুরো Quiz+QBank+Study রিফেচ হতো, কনটেন্ট বদলাক বা না বদলাক। এখন meta/updatedAt
        // চেক থাকায় বেশিরভাগ রান-এ আসলে কিছুই ডাউনলোড হবে না (শুধু ছোট meta node চেক হবে),
        // তাই ৬ ঘণ্টায় নামিয়ে আনলেও bandwidth নষ্ট হবে না, বরং কমবে।
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            // FIX: আগে KEEP পলিসি ছিল — মানে যাদের ফোনে আগেই ১ ঘণ্টার periodic work
            // enqueue হয়ে গেছে, তাদের জন্য নতুন ৬-ঘণ্টার শিডিউল কখনো কার্যকর হতো না
            // (app update করলেও আগের schedule-ই থেকে যেত)। UPDATE পলিসি দিলে বিদ্যমান
            // request-এর constraints/interval নতুন করে বসে যায়।
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }
    }
}
