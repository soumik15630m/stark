package com.soumik.stark.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.soumik.stark.R
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Format
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = kmText(context)
                ids.forEach { id -> render(context, manager, id, text) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        // Forget each removed widget's transparency setting.
        ids.forEach { Prefs.setInt(context, ALPHA_PREFIX + it, DEFAULT_ALPHA) }
    }

    companion object {
        const val ALPHA_PREFIX = "widget_alpha_"
        const val DEFAULT_ALPHA = 100          // percent opacity
        private const val BASE_RGB = 0x101821  // the widget's dark card colour, sans alpha

        /** Today's bike distance as display text. */
        suspend fun kmText(context: Context): String {
            val today = TrackRepository.get(context).totalsDao.daily(TimeUtils.todayKey())
            return "${Format.km(today?.distanceBikeM ?: 0.0)} km"
        }

        /** Render one widget, applying its saved background transparency. */
        fun render(context: Context, manager: AppWidgetManager, id: Int, kmText: String) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_km, kmText)
            val alphaPct = Prefs.getInt(context, ALPHA_PREFIX + id, DEFAULT_ALPHA).coerceIn(0, 100)
            val argb = (alphaPct * 255 / 100 shl 24) or BASE_RGB
            views.setInt(R.id.widget_root, "setBackgroundColor", argb)
            // Tapping opens the PIN-free ride dashboard straight to the speedometer.
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DASHBOARD, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_speedo, open)
            manager.updateAppWidget(id, views)
        }

        /** Force all widgets to refresh (call after a trip closes / totals change). */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, TodayWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }
}
