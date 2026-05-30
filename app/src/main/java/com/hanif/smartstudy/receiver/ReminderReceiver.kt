package com.hanif.smartstudy.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.util.SessionManager
import java.util.Calendar

// ─────────────────────────────────────────────────────────────
//  ReminderReceiver — Daily study reminder via AlarmManager
// ─────────────────────────────────────────────────────────────

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID    = "smart_study_reminder"
        private const val NOTIF_ID      = 1001
        private const val REQUEST_CODE  = 2001

        // Schedule daily exact alarm
        fun schedule(context: Context, hour: Int, minute: Int) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java)
            val pi     = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Next occurrence of hour:minute
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            // API 31+ needs SCHEDULE_EXACT_ALARM permission — use setExactAndAllowWhileIdle
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (e: SecurityException) {
                // Fallback to inexact
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY, pi
                )
            }
        }

        // Cancel scheduled alarm
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        showReminderNotification(context)
        // Re-schedule for next day
        val session = SessionManager(context)
        if (session.isReminderOn()) {
            schedule(context, session.getReminderHour(), session.getReminderMinute())
        }
    }

    private fun showReminderNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "পড়ার রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "প্রতিদিনের পড়ার রিমাইন্ডার"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "📚 এখন পড়ার সময়! আজকের লক্ষ্য পূরণ করো।",
            "🎯 দেরি না করে এখনই শুরু করো!",
            "💪 প্রতিদিনের অভ্যাসই সাফল্যের চাবিকাঠি!",
            "⭐ আজ একটু পড়লে কালকে এগিয়ে থাকবে।"
        )
        val msg = messages[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % messages.size]

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Smart Study")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}
