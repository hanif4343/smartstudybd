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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * SyncWorker:
 * 1. Pending offline queue sync করে Firebase/GAS-এ
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
        return try {
            val gasUrl = BuildConfig.GAS_URL
            val payload = gson.fromJson(action.payload, Map::class.java)

            val formBody = when (action.type) {
                "quiz_answer" -> FormBody.Builder()
                    .add("action",     "quizAnswer")
                    .add("phone",      payload["phone"]?.toString() ?: "")
                    .add("questionId", payload["questionId"]?.toString() ?: "")
                    .add("isCorrect",  payload["isCorrect"]?.toString() ?: "false")
                    .build()

                "xp_update" -> FormBody.Builder()
                    .add("action",  "updateXP")
                    .add("phone",   payload["phone"]?.toString() ?: "")
                    .add("xpDelta", payload["xpDelta"]?.toString() ?: "0")
                    .build()

                "study_progress" -> FormBody.Builder()
                    .add("action",  "studyProgress")
                    .add("phone",   payload["phone"]?.toString() ?: "")
                    .add("minutes", payload["minutes"]?.toString() ?: "0")
                    .add("topic",   payload["topic"]?.toString() ?: "")
                    .build()

                else -> return false
            }

            val req  = Request.Builder().url(gasUrl).post(formBody).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(TAG, "Sync ${action.type}: $body")
            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "syncAction error: ${e.message}")
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
