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
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID       = "smart_study_reminder"
        const val REQ_MORNING      = 2001
        const val REQ_NIGHT        = 2002
        const val REQ_EXAM_EVE     = 2025
        const val REQ_MIDDAY       = 2003
        const val REQ_EVENING      = 2004
        const val EXTRA_TYPE       = "reminder_type"
        const val TYPE_MORNING     = "morning"
        const val TYPE_NIGHT       = "night"
        const val TYPE_EXAM_EVE    = "exam_eve"
        const val TYPE_MIDDAY      = "midday"
        const val TYPE_EVENING     = "evening"

        fun scheduleMorning(context: Context, hour: Int, minute: Int, repeatDaily: Boolean = true) {
            schedule(context, hour, minute, REQ_MORNING, TYPE_MORNING, repeatDaily)
            SessionManager(context).setReminderMorning(true, hour, minute, repeatDaily)
        }

        fun scheduleNight(context: Context, hour: Int, minute: Int, repeatDaily: Boolean = true) {
            schedule(context, hour, minute, REQ_NIGHT, TYPE_NIGHT, repeatDaily)
            SessionManager(context).setReminderNight(true, hour, minute, repeatDaily)
        }

        fun scheduleMidday(context: Context, hour: Int, minute: Int, repeatDaily: Boolean = true) {
            schedule(context, hour, minute, REQ_MIDDAY, TYPE_MIDDAY, repeatDaily)
            SessionManager(context).setReminderMidday(true, hour, minute, repeatDaily)
        }

        fun scheduleEvening(context: Context, hour: Int, minute: Int, repeatDaily: Boolean = true) {
            schedule(context, hour, minute, REQ_EVENING, TYPE_EVENING, repeatDaily)
            SessionManager(context).setReminderEvening(true, hour, minute, repeatDaily)
        }

        fun cancelMorning(context: Context) {
            cancel(context, REQ_MORNING, TYPE_MORNING)
            SessionManager(context).setReminderMorning(false, 7, 0)
        }

        fun cancelNight(context: Context) {
            cancel(context, REQ_NIGHT, TYPE_NIGHT)
            SessionManager(context).setReminderNight(false, 21, 0)
        }

        fun cancelMidday(context: Context) {
            cancel(context, REQ_MIDDAY, TYPE_MIDDAY)
            SessionManager(context).setReminderMidday(false, 14, 0)
        }

        fun cancelEvening(context: Context) {
            cancel(context, REQ_EVENING, TYPE_EVENING)
            SessionManager(context).setReminderEvening(false, 19, 0)
        }

        private fun schedule(context: Context, hour: Int, minute: Int, reqCode: Int, type: String, repeatDaily: Boolean = true) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra("hour", hour)
                putExtra("minute", minute)
                putExtra("repeatDaily", repeatDaily)
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
        val defaultHour = when (type) {
            TYPE_MORNING -> 7
            TYPE_MIDDAY  -> 14
            TYPE_EVENING -> 19
            else         -> 21
        }
        val hour   = intent.getIntExtra("hour", defaultHour)
        val minute = intent.getIntExtra("minute", 0)
        val repeatDaily = intent.getBooleanExtra("repeatDaily", true)

        // exam eve — custom title/body সহ একবারই fire হয়, repeat নয়
        if (type == TYPE_EXAM_EVE) {
            val title = intent.getStringExtra("title") ?: "📅 আগামীকাল পরীক্ষা!"
            val body  = intent.getStringExtra("body")  ?: "শেষবারের মতো রিভিশন করে নাও!"
            showCustomNotification(context, title, body, REQ_EXAM_EVE)
            return
        }

        showNotification(context, type)

        val session = SessionManager(context)
        if (repeatDaily) {
            // Daily মোড — পরের দিনের জন্য আবার schedule করো (যদি এখনো on থাকে)
            when (type) {
                TYPE_MORNING -> if (session.isMorningReminderOn()) schedule(context, hour, minute, REQ_MORNING, TYPE_MORNING, true)
                TYPE_NIGHT   -> if (session.isNightReminderOn())   schedule(context, hour, minute, REQ_NIGHT,   TYPE_NIGHT, true)
                TYPE_MIDDAY  -> if (session.isMiddayReminderOn())  schedule(context, hour, minute, REQ_MIDDAY,  TYPE_MIDDAY, true)
                TYPE_EVENING -> if (session.isEveningReminderOn()) schedule(context, hour, minute, REQ_EVENING, TYPE_EVENING, true)
            }
        } else {
            // Once মোড — একবার fire হয়ে শেষ। টগল বন্ধ করে দাও যাতে UI-ও সঠিক অবস্থা দেখায়।
            when (type) {
                TYPE_MORNING -> session.setReminderMorning(false, hour, minute, false)
                TYPE_NIGHT   -> session.setReminderNight(false, hour, minute, false)
                TYPE_MIDDAY  -> session.setReminderMidday(false, hour, minute, false)
                TYPE_EVENING -> session.setReminderEvening(false, hour, minute, false)
            }
        }
    }

    private fun showNotification(context: Context, type: String) {
        createChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // আজকের প্রোগ্রেস চেক করো (মিডডে/ইভেনিং এর জন্য)
        val progressPct: Int by lazy {
            try {
                val session = SessionManager(context)
                val cache   = com.hanif.smartstudy.data.local.ContentCache(context)
                val goal    = session.getDailyGoal().coerceAtLeast(1)
                val done    = runBlocking { cache.getTodayStudyMinutes() }
                ((done * 100) / goal).coerceIn(0, 100)
            } catch (e: Exception) { 0 }
        }

        // যদি আজকের রুটিন ইতিমধ্যে ১০০% শেষ হয়ে থাকে, midday/evening নোটিফিকেশন স্কিপ করো
        if ((type == TYPE_MIDDAY || type == TYPE_EVENING) && progressPct >= 100) {
            return
        }

        val (title, messages) = when (type) {
            TYPE_MORNING -> Pair(
                "📚 সুপ্রভাত! পড়ার সময় হয়েছে",
                listOf(
                    "আজকের লক্ষ্য পূরণ করতে এখনই শুরু করো! 🎯",
                    "প্রতিদিন একটু একটু পড়লে বড় পরীক্ষায় জয়ী হবে! 💪",
                    "উঠে পড়ো, স্মার্ট স্টাডি অপেক্ষা করছে! ⭐"
                )
            )
            TYPE_MIDDAY -> if (progressPct <= 0) Pair(
                "⚠️ আজকের পড়া এখনো শুরু হয়নি!",
                listOf(
                    "দিনের অর্ধেক সময় পার হয়ে যাচ্ছে, পিছিয়ে পড়ার আগেই শুরু করো! 🏃",
                    "এখনো ০% সম্পন্ন — এখনই কয়েকটা প্রশ্ন সমাধান করে ফেলো! ⏳",
                    "আজকের রুটিন এখনো বাকি, দেরি না করে শুরু করো! 📖"
                )
            ) else Pair(
                "👍 ভালো চলছে! আরেকটু এগিয়ে যাও",
                listOf(
                    "আজকের লক্ষ্যের $progressPct% সম্পন্ন — বাকিটাও শেষ করো! 💪",
                    "দারুণ চলছে! একটু সময় দিয়ে আজকের রুটিন কমপ্লিট করো। 🎯",
                    "$progressPct% শেষ — শেষটুকু আজই সেরে নাও! ⭐"
                )
            )
            TYPE_EVENING -> Pair(
                "🌆 দিনের ৭০% সময় পার হয়ে গেছে",
                listOf(
                    "আজকের রুটিন এখনো ${100 - progressPct}% বাকি — এখনই শেষ করো! ⚠️",
                    "সন্ধ্যা হয়ে গেছে, আজকের পড়া বাকি আছে। দ্রুত শেষ করো! 🔥",
                    "Streak ধরে রাখতে আজকের লক্ষ্য পূরণ করো এখনই! 🏆"
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

        val notifId = when (type) {
            TYPE_MORNING -> REQ_MORNING
            TYPE_MIDDAY  -> REQ_MIDDAY
            TYPE_EVENING -> REQ_EVENING
            else         -> REQ_NIGHT
        }
        nm.notify(notifId, notif)
    }

    private fun showCustomNotification(context: Context, title: String, body: String, notifId: Int) {
        createChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()
        nm.notify(notifId, notif)
    }
}
