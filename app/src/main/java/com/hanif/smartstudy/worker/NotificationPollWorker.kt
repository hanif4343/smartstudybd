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
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
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
        private const val PRUNE_OLDER_THAN_MS = 7L * 24 * 60 * 60 * 1000 // ৭ দিনের পুরনো read নোটিফিকেশন prune

        // ── FIX (Speed Plan Task 5): এটা FCM push-এর *safety-net* মাত্র (Doze
        // mode/battery-optimization/OEM (Xiaomi-Huawei ধরনের) aggressive kill-এ
        // মাঝেমধ্যে FCM মিস হতে পারে বলেই এই backup poller পুরোপুরি বাদ দেওয়া হয়নি)
        // — আসল ডেলিভারি SmartStudyFirebaseService (FCM onMessageReceived) দিয়েই
        // instant হয়। তাই আগের ১৫-মিনিট (দিনে ৯৬ বার/ইউজার) অতিরিক্ত ঘনঘন ছিল —
        // এখন ৬০ মিনিট (দিনে ২৪ বার), bandwidth ৪ গুণ কমে যায়, তবু কয়েক ঘণ্টার
        // বেশি একটা notification miss থাকবে না।
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationPollWorker>(60, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // ⚠️ আগে KEEP ছিল — interval বদলালেও পুরনো ইনস্টলে কখনো নতুন ৬০-মিনিট শিডিউল প্রয়োগ হতো না, UPDATE দিয়ে বিদ্যমান ডিভাইসেও নতুন interval বসবে
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
                    NotificationChannel(CHANNEL_FCM, "Smart Study", NotificationManager.IMPORTANCE_HIGH)
                        .apply {
                            description = "Admin notifications"
                            enableVibration(true)
                            setSound(
                                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                    .build()
                            )
                        }
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
        val phone    = com.hanif.smartstudy.util.PhoneValidator.sanitize(user.phone) ?: return@withContext Result.success()

        val firebaseBase = BuildConfig.FIREBASE_URL.trimEnd('/')
        val lastCheck    = session.getLastNotifCheck()

        try {
            val fbToken = FirebaseTokenProvider.getToken()
            val fbAuth  = if (fbToken.isNotBlank()) "?auth=$fbToken" else ""
            val url  = "$firebaseBase/Notifications/$phone.json$fbAuth"
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
                    val extras = mapOf(
                        "url"        to (notif.get("url")?.asString ?: ""),
                        "type"       to (notif.get("type")?.asString ?: ""),
                        "questionId" to (notif.get("questionId")?.asString ?: ""),
                        "tab"        to (notif.get("tab")?.asString ?: ""),
                        "challengeId" to (notif.get("challengeId")?.asString ?: "")
                    ).filterValues { it.isNotBlank() }
                    showLocalNotification(title, msgBody, extras)
                    markAsRead(firebaseBase, phone, key, fbAuth)
                }
            }

            // ── FIX (Speed Plan Task 5): "Notifications/{phone} node কখনো prune
            // হয় না, সময়ের সাথে বড়ই হতে থাকে" — read=true হয়ে যাওয়া আর
            // PRUNE_OLDER_THAN_MS-এর চেয়ে পুরনো এন্ট্রি এখানেই ডিলিট করে দেওয়া হয়,
            // তাই প্রতিবার poll-এ যে payload ডাউনলোড হয় সেটা bounded থাকে, সময়ের
            // সাথে না বেড়ে ──
            pruneOldReadNotifications(obj, firebaseBase, phone, fbAuth)

            session.setLastNotifCheck(now)
            Log.d(TAG, "Poll done for $phone")
        } catch (e: Exception) {
            Log.e(TAG, "Poll error: ${e.message}")
        }

        Result.success()
    }

    /** read=true + PRUNE_OLDER_THAN_MS-এর চেয়ে পুরনো এন্ট্রি ডিলিট — node bounded রাখতে */
    private fun pruneOldReadNotifications(obj: JsonObject, base: String, phone: String, auth: String) {
        val cutoff = System.currentTimeMillis() - PRUNE_OLDER_THAN_MS
        obj.entrySet().forEach { (key, value) ->
            if (!value.isJsonObject) return@forEach
            val notif = value.asJsonObject
            if (notif.get("read")?.asBoolean != true) return@forEach // অপঠিত এন্ট্রি কখনো prune না
            val keyTime = key.replace("notif_", "").toLongOrNull() ?: 0L
            val notifTime = if (keyTime > 0) keyTime else {
                notif.get("time")?.asString?.let {
                    try { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).parse(it)?.time } catch (e: Exception) { null }
                } ?: 0L
            }
            if (notifTime in 1 until cutoff) {
                try {
                    val url = "$base/Notifications/$phone/$key.json$auth"
                    client.newCall(Request.Builder().url(url).delete().build()).execute().close()
                } catch (e: Exception) {
                    Log.w(TAG, "prune $key failed: ${e.message}")
                }
            }
        }
    }

    private fun showLocalNotification(title: String, body: String, extras: Map<String, String> = emptyMap()) {
        createChannel(applicationContext)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = PendingIntent.getActivity(
            applicationContext, System.currentTimeMillis().toInt(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                extras.forEach { (k, v) -> putExtra(k, v) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_FCM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
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
