package com.hanif.smartstudy.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.gson.JsonObject
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

// Firebase Notifications node polling — HTML app এর checkNotifications() এর সমতুল্য
class NotificationPollWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME    = "notification_poll"
        private const val CHANNEL_FCM  = "smart_study_channel"
        private const val TAG          = "NotifPoll"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_FCM) == null) {
                    NotificationChannel(CHANNEL_FCM, "Smart Study", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Admin notifications" }
                        .also { nm.createNotificationChannel(it) }
                }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session  = SessionManager(applicationContext)
        val user     = session.getCurrentUser() ?: return@withContext Result.success()
        val phone    = user.phone?.trim()?.replace(Regex("[.#\$\\[\\]\\s]"), "_") ?: return@withContext Result.success()

        val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')
        val secretKey    = BuildConfig.SECRET_KEY
        val authParam    = if (secretKey.isNotBlank() && !secretKey.contains("%%")) "?auth=$secretKey" else ""
        val lastCheck    = session.getLastNotifCheck()

        try {
            val url  = "$firebaseBase/Notifications/$phone.json$authParam"
            val req  = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().body?.string() ?: return@withContext Result.success()

            if (body == "null" || body.isBlank()) return@withContext Result.success()

            val obj = com.google.gson.Gson().fromJson(body, JsonObject::class.java)
                ?: return@withContext Result.success()

            val now = System.currentTimeMillis()

            obj.entrySet().forEach { (key, value) ->
                if (!value.isJsonObject) return@forEach
                val notif = value.asJsonObject
                if (notif.get("read")?.asBoolean == true) return@forEach

                val keyTime = key.replace("notif_", "").toLongOrNull() ?: 0L
                val notifTime = if (keyTime > 0) keyTime else {
                    notif.get("time")?.asString?.let {
                        try { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).parse(it)?.time } catch (e: Exception) { null }
                    } ?: 0L
                }

                if (notifTime > lastCheck) {
                    val title = notif.get("title")?.asString ?: "Smart Study"
                    val msgBody = notif.get("body")?.asString ?: ""
                    showLocalNotification(title, msgBody)
                    markAsRead(firebaseBase, phone, key, authParam)
                }
            }

            session.setLastNotifCheck(now)
            Log.d(TAG, "Poll done for $phone")
        } catch (e: Exception) {
            Log.e(TAG, "Poll error: ${e.message}")
        }

        Result.success()
    }

    private fun showLocalNotification(title: String, body: String) {
        createChannel(applicationContext)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_FCM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun markAsRead(base: String, phone: String, key: String, auth: String) {
        try {
            val url  = "$base/Notifications/$phone/$key/read.json$auth"
            val body = "true".toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
        } catch (e: Exception) { Log.e(TAG, "markAsRead: ${e.message}") }
    }
}
