package com.soumik.stark.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.soumik.stark.R
import com.soumik.stark.core.security.AppLock
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Format
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
                // Hide exact km until unlocked (design §6.3): show a masked value when a PIN is set.
                val text = if (AppLock.isPinSet(context)) "•• km" else {
                    val today = TrackRepository.get(context).totalsDao.daily(TimeUtils.todayKey())
                    "${Format.km(today?.distanceBikeM ?: 0.0)} km"
                }
                ids.forEach { id -> render(context, manager, id, text) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, kmText: String) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_km, kmText)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.widget_speedo, open)
        manager.updateAppWidget(id, views)
    }
}
