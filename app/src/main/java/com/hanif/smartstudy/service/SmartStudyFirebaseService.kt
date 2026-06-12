package com.hanif.smartstudy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
//  FCM Service — token save to Firebase RTDB + show notification
// ─────────────────────────────────────────────────────────────

class SmartStudyFirebaseService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "smart_study_channel"
        const val CHANNEL_NAME = "Smart Study"

        private val http   = OkHttpClient()
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()
        private val fbUrl   get() = BuildConfig.FIREBASE_URL.trimEnd('/')
        private val fbAuth  get() = BuildConfig.FIREBASE_DB_SECRET

        private fun fbPatchAsync(path: String, data: Map<String, Any?>) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL })
                        .toString().toRequestBody(JSON_MT)
                    val req = Request.Builder()
                        .url("$fbUrl/$path.json?auth=$fbAuth")
                        .patch(body).build()
                    http.newCall(req).execute().close()
                } catch (e: Exception) {
                    Log.e("FBRest", "fbPatch $path failed: ${e.message}")
                }
            }
        }

        // Save FCM token to Firebase RTDB under users/{phone}/fcmToken
        fun saveFcmTokenToFirebase(context: Context, token: String) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }

            fbPatchAsync("users/$phone", mapOf(
                "fcmToken" to token,
                "lastSeen" to System.currentTimeMillis()
            ))
            CoroutineScope(Dispatchers.IO).launch {
                session.saveUser(user.copy(fcmToken = token))
            }
            Log.d("FCM", "Token save initiated: $token")
        }

        // Record presence: online/offline in Firebase RTDB
        fun updatePresence(context: Context, isOnline: Boolean) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }

            fbPatchAsync("presence/$phone", mapOf(
                "online"   to isOnline,
                "lastSeen" to System.currentTimeMillis(),
                "name"     to (user.name ?: "")
            ))
            // Note: onDisconnect() is Firebase SDK-only feature.
            // Presence will update next time updatePresence(false) is called (app pause/stop).
        }

        // Record app session time to Firebase (for admin)
        fun recordAppSession(context: Context, sessionMinutes: Int) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
            if (sessionMinutes <= 0) return

            // REST PATCH - lastActive update (increment handled server-side via GAS if needed)
            fbPatchAsync("users/$phone", mapOf(
                "lastActive" to System.currentTimeMillis()
            ))
        }
    }

    // ── New FCM token ─────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        saveFcmTokenToFirebase(applicationContext, token)
    }

    // ── Message received ──────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "Smart Study"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val data  = message.data

        showNotification(title, body, data)
    }

    private fun showNotification(
        title : String,
        body  : String,
        data  : Map<String, String> = emptyMap()
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart Study notifications"
                enableVibration(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Pass all FCM data fields as intent extras for deep link parsing
            data.forEach { (key, value) -> putExtra(key, value) }
            // Ensure type-specific extras are always present for DeepLinkHandler
            // (FCM data map already contains them via forEach above)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
