package com.hanif.smartstudy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.data.local.RoutineCache
import kotlinx.coroutines.runBlocking

// ─────────────────────────────────────────────────────────
//  RoutineWidgetProvider — হোম স্ক্রিনে আজকের রুটিন ও প্রোগ্রেস দেখায়
//  ফোনের মেইন স্ক্রিনেই বাকি থাকা পড়া দেখাবে, যাতে অ্যাপ না
//  খুললেও ইউজার মনে করতে বাধ্য হয়
// ─────────────────────────────────────────────────────────

class RoutineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids     = manager.getAppWidgetIds(ComponentName(context, RoutineWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_routine)

            try {
                val routine = runBlocking { RoutineCache(context).getTodayRoutine() }

                views.setTextViewText(R.id.widget_progress_text, "${routine.doneCount}/${routine.totalCount}")
                views.setProgressBar(R.id.widget_progress_bar, 100, routine.progressPct, false)

                val pendingItems = routine.items.filterNot { it.done }
                val pendingText = when {
                    routine.items.isEmpty() -> "আজকের রুটিন এখনো সেট করা হয়নি।\nঅ্যাপ খুলে যুক্ত করো!"
                    pendingItems.isEmpty()  -> "🎉 আজকের সব রুটিন সম্পন্ন!"
                    else -> pendingItems.take(3).joinToString("\n") { "• ${it.title}" } +
                            if (pendingItems.size > 3) "\n+${pendingItems.size - 3} আরও" else ""
                }
                views.setTextViewText(R.id.widget_pending_text, pendingText)
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_pending_text, "📚 আজকের পড়া বাকি আছে!")
            }

            // ট্যাপ করলে অ্যাপ খুলে যাবে
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_pending_text, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_progress_text, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
