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

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID       = "smart_study_reminder"
        const val REQ_MORNING      = 2001
        const val REQ_NIGHT        = 2002
        const val EXTRA_TYPE       = "reminder_type"
        const val TYPE_MORNING     = "morning"
        const val TYPE_NIGHT       = "night"

        fun scheduleMorning(context: Context, hour: Int, minute: Int) {
            schedule(context, hour, minute, REQ_MORNING, TYPE_MORNING)
            SessionManager(context).setReminderMorning(true, hour, minute)
        }

        fun scheduleNight(context: Context, hour: Int, minute: Int) {
            schedule(context, hour, minute, REQ_NIGHT, TYPE_NIGHT)
            SessionManager(context).setReminderNight(true, hour, minute)
        }

        fun cancelMorning(context: Context) {
            cancel(context, REQ_MORNING, TYPE_MORNING)
            SessionManager(context).setReminderMorning(false, 7, 0)
        }

        fun cancelNight(context: Context) {
            cancel(context, REQ_NIGHT, TYPE_NIGHT)
            SessionManager(context).setReminderNight(false, 21, 0)
        }

        private fun schedule(context: Context, hour: Int, minute: Int, reqCode: Int, type: String) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra("hour", hour)
                putExtra("minute", minute)
            }
            val pi = PendingIntent.getBroadcast(
                context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (e: SecurityException) {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            }
        }

        private fun cancel(context: Context, reqCode: Int, type: String) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, reqCode,
                Intent(context, ReminderReceiver::class.java).apply { putExtra(EXTRA_TYPE, type) },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel(CHANNEL_ID, "পড়ার রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "সকাল ও রাতের পড়ার রিমাইন্ডার"; enableVibration(true) }
                        .also { nm.createNotificationChannel(it) }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val type   = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_MORNING
        val hour   = intent.getIntExtra("hour", if (type == TYPE_MORNING) 7 else 21)
        val minute = intent.getIntExtra("minute", 0)

        showNotification(context, type)

        // পরের দিনের জন্য আবার schedule করো
        val session = SessionManager(context)
        when (type) {
            TYPE_MORNING -> if (session.isMorningReminderOn()) schedule(context, hour, minute, REQ_MORNING, TYPE_MORNING)
            TYPE_NIGHT   -> if (session.isNightReminderOn())   schedule(context, hour, minute, REQ_NIGHT,   TYPE_NIGHT)
        }
    }

    private fun showNotification(context: Context, type: String) {
        createChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val (title, messages) = when (type) {
            TYPE_MORNING -> Pair(
                "📚 সুপ্রভাত! পড়ার সময় হয়েছে",
                listOf(
                    "আজকের লক্ষ্য পূরণ করতে এখনই শুরু করো! 🎯",
                    "প্রতিদিন একটু একটু পড়লে বড় পরীক্ষায় জয়ী হবে! 💪",
                    "উঠে পড়ো, স্মার্ট স্টাডি অপেক্ষা করছে! ⭐"
                )
            )
            else -> Pair(
                "🌙 রাতের রিমাইন্ডার",
                listOf(
                    "ঘুমানোর আগে আজকের লক্ষ্য পূরণ করেছো? 🔥",
                    "Streak ধরে রাখো, আজকের পড়া শেষ করো! 📖",
                    "একটু পড়েই ঘুমাও — সফলতা নিশ্চিত! 🏆"
                )
            )
        }
        val msg = messages[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % messages.size]

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        nm.notify(if (type == TYPE_MORNING) REQ_MORNING else REQ_NIGHT, notif)
    }
}
