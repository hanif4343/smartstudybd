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
import com.hanif.smartstudy.data.local.RoutineCache
import kotlinx.coroutines.runBlocking
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════
//  RoutineItemReminderReceiver
// ───────────────────────────────────────────────────────────────────
//  Daily Routine-এর প্রতিটি আইটেমের জন্য আলাদা সময়ে আলাদা alarm/reminder
//  সেট করার জন্য। concept টা ঠিক alarm_create_v0 এর মতোই — প্রতিটা
//  item-এর নিজস্ব hour/minute থাকে এবং সেই সময়ে একটা notification ফায়ার হয়।
//
//  প্রতিটা routine item-এর id থেকে একটা স্থিতিশীল (stable) ইন্টিজার
//  request code বানানো হয় (id.hashCode()), যাতে item ভিন্ন হলে আলাদা
//  PendingIntent তৈরি হয় এবং একটার alarm আরেকটাকে override না করে।
// ═══════════════════════════════════════════════════════════════════

class RoutineItemReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID  = "routine_item_reminder"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TITLE   = "item_title"
        const val EXTRA_SUBJECT = "item_subject"
        const val EXTRA_HOUR    = "hour"
        const val EXTRA_MINUTE  = "minute"

        /** item.id থেকে স্থিতিশীল request code — একই item সবসময় একই code পায় */
        fun requestCodeFor(itemId: String): Int = "routine_item_$itemId".hashCode()

        /** Android 12+ এ exact alarm permission আছে কিনা */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                return am.canScheduleExactAlarms()
            }
            return true
        }

        /** এই routine item-এর জন্য আজকে/আগামীকাল hour:minute এ alarm সেট করো */
        fun schedule(context: Context, itemId: String, title: String, subject: String, hour: Int, minute: Int) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val reqCode = requestCodeFor(itemId)

            val intent = Intent(context, RoutineItemReminderReceiver::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_HOUR, hour)
                putExtra(EXTRA_MINUTE, minute)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (e: SecurityException) {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            }
        }

        /** এই routine item-এর alarm বাতিল করো */
        fun cancel(context: Context, itemId: String) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val reqCode = requestCodeFor(itemId)
            val pi = PendingIntent.getBroadcast(
                context, reqCode,
                Intent(context, RoutineItemReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel(CHANNEL_ID, "রুটিন আইটেম রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
                        .apply {
                            description = "প্রতিদিনের রুটিনের প্রতিটি আইটেমের জন্য আলাদা সময়ের রিমাইন্ডার"
                            enableVibration(true)
                        }
                        .also { nm.createNotificationChannel(it) }
                }
            }
        }

        /**
         * App চালু হওয়ার সময় / boot-এর পর — আজকের রুটিনে reminder-অন থাকা
         * সব আইটেমের alarm আবার schedule করো (RoutineViewModel.load() বা
         * BootReceiver থেকে ডাকা হয়)।
         */
        fun rescheduleAll(context: Context) {
            try {
                val routine = runBlocking { RoutineCache(context).getTodayRoutine() }
                routine.items.forEach { item ->
                    if (item.hasReminder && !item.done) {
                        schedule(context, item.id, item.title, item.subject, item.reminderHour, item.reminderMinute)
                    } else if (item.hasReminder && item.done) {
                        // আজকের জন্য আগেই সম্পন্ন — আর বিরক্ত করার দরকার নেই
                        cancel(context, item.id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itemId  = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val title   = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "তোমার রুটিন" }
        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()

        // আজকের রুটিনে আইটেমটা এখনো আছে কিনা, আগে থেকেই done কিনা এবং
        // reminder এখনো অন আছে কিনা — একসাথে চেক করে নাও
        try {
            runBlocking {
                val routine = RoutineCache(context).getTodayRoutine()
                val current = routine.items.find { it.id == itemId }

                // মুছে ফেলা বা ইতিমধ্যে সম্পন্ন আইটেমের জন্য notification না দেখানোই ভালো
                if (current != null && !current.done) {
                    showNotification(context, itemId, current.title.ifBlank { title }, current.subject.ifBlank { subject })
                }

                // পরের দিনের জন্য আবার schedule করো (item তখনও routine-এ থাকলে ও reminder অন থাকলে)
                if (current != null && current.hasReminder) {
                    schedule(context, itemId, current.title, current.subject, current.reminderHour, current.reminderMinute)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // fallback — অন্তত notification টা দেখাও, পরের দিনের জন্য reschedule নাও হতে পারে
            showNotification(context, itemId, title, subject)
        }
    }

    private fun showNotification(context: Context, itemId: String, title: String, subject: String) {
        createChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val body = if (subject.isNotBlank()) "$subject — এখনই পড়া শুরু করো! 📖" else "এখনই পড়া শুরু করো! 📖"

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        nm.notify(requestCodeFor(itemId), notif)
    }
}
