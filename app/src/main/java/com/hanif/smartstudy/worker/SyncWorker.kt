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

        // ── 2. Content refresh (cache stale থাকলে) ──
        val cached = cache.loadContent()
        if (cached == null || cached.isStale()) {
            Log.d(TAG, "Content is stale, refreshing...")
            when (val result = ContentFetchService.fetchAllContent()) {
                is ContentResult.Success -> {
                    cache.saveContent(result.data)
                    Log.d(TAG, "Content refreshed: Study=${result.data.study.size}")
                }
                is ContentResult.Error -> {
                    Log.w(TAG, "Content refresh failed: ${result.message}")
                    allSuccess = false
                }
            }
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
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminEdit error: ${e.message}")
            false
        }
    }

    companion object {
        const val WORK_NAME = "SmartStudySyncWork"

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

        // ── প্রতি 6 ঘণ্টায় periodic content refresh ──
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
