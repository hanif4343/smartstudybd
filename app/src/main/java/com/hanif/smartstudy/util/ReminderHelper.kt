package com.hanif.smartstudy.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hanif.smartstudy.R
import java.util.Calendar

const val REMINDER_CHANNEL_ID = "study_reminder"
const val REMINDER_REQ_CODE   = 1001

object ReminderHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "পড়ার রিমাইন্ডার",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "প্রতিদিনের পড়ার রিমাইন্ডার" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    // ── Exact daily alarm set ──
    fun setDailyReminder(context: Context, hour: Int, minute: Int) {
        val am   = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal  = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            // যদি আজকের সময় পার হয়ে গেছে, কালকে set করো
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, StudyReminderReceiver::class.java).apply {
            putExtra("hour", hour); putExtra("minute", minute)
        }
        val pi = PendingIntent.getBroadcast(
            context, REMINDER_REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Exact alarm — Android 12+ এ SCHEDULE_EXACT_ALARM permission লাগে
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
        // Save to prefs
        context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true)
            .putInt("hour", hour)
            .putInt("minute", minute)
            .apply()
    }

    fun cancelReminder(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REMINDER_REQ_CODE,
            Intent(context, StudyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", false).apply()
    }

    fun getReminderState(context: Context): Triple<Boolean, Int, Int> {
        val p = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        return Triple(p.getBoolean("enabled", false), p.getInt("hour", 20), p.getInt("minute", 0))
    }
}

// ── BroadcastReceiver ──
class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour   = intent.getIntExtra("hour", 20)
        val minute = intent.getIntExtra("minute", 0)

        // Show notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📚 পড়ার সময় হয়েছে!")
            .setContentText("আজকের লক্ষ্য পূরণ করতে ভুলো না। স্মার্ট স্টাডি খোলো!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(REMINDER_REQ_CODE, notif)

        // Set next day alarm
        ReminderHelper.setDailyReminder(context, hour, minute)
    }
}
