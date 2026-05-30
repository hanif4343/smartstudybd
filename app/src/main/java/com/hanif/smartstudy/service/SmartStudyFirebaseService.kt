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
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  FCM Service — token save to Firebase RTDB + show notification
// ─────────────────────────────────────────────────────────────

class SmartStudyFirebaseService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "smart_study_channel"
        const val CHANNEL_NAME = "Smart Study"

        // Save FCM token to Firebase RTDB under users/{phone}/fcmToken
        fun saveFcmTokenToFirebase(context: Context, token: String) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }

            try {
                val db  = com.google.firebase.database.FirebaseDatabase.getInstance()
                val ref = db.getReference("users/$phone")
                ref.child("fcmToken").setValue(token)
                ref.child("lastSeen").setValue(System.currentTimeMillis())
                // Update user object with token
                CoroutineScope(Dispatchers.IO).launch {
                    session.saveUser(user.copy(fcmToken = token))
                }
                Log.d("FCM", "Token saved: $token")
            } catch (e: Exception) {
                Log.e("FCM", "Token save failed: ${e.message}")
            }
        }

        // Record presence: online/offline in Firebase RTDB
        fun updatePresence(context: Context, isOnline: Boolean) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }

            try {
                val db  = com.google.firebase.database.FirebaseDatabase.getInstance()
                val ref = db.getReference("presence/$phone")
                ref.child("online").setValue(isOnline)
                ref.child("lastSeen").setValue(System.currentTimeMillis())
                ref.child("name").setValue(user.name ?: "")

                // On disconnect: auto-mark offline
                if (isOnline) {
                    ref.child("online").onDisconnect().setValue(false)
                    ref.child("lastSeen").onDisconnect().setValue(
                        com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                }
            } catch (e: Exception) {
                Log.e("Presence", "updatePresence failed: ${e.message}")
            }
        }

        // Record app session time to Firebase (for admin)
        fun recordAppSession(context: Context, sessionMinutes: Int) {
            val session = SessionManager(context)
            val user    = session.getCurrentUser() ?: return
            val phone   = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
            if (sessionMinutes <= 0) return

            try {
                val db  = com.google.firebase.database.FirebaseDatabase.getInstance()
                val ref = db.getReference("users/$phone")
                // Increment total session time server-side
                ref.child("totalAppMinutes").runTransaction(object :
                    com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        val cur = mutableData.getValue(Int::class.java) ?: 0
                        mutableData.value = cur + sessionMinutes
                        return com.google.firebase.database.Transaction.success(mutableData)
                    }
                    override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
                })
                ref.child("lastActive").setValue(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("Session", "recordAppSession failed: ${e.message}")
            }
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

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Smart Study notifications" }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
