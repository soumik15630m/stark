package com.soumik.stark.tracking.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.soumik.stark.R
import com.soumik.stark.core.util.Format
import com.soumik.stark.domain.outing.OutingSummary
import com.soumik.stark.ui.MainActivity

object Notifications {
    const val CHANNEL_LIVE = "live-odometer"
    const val CHANNEL_SUMMARY = "ride-summary"
    const val LIVE_ID = 1001
    const val SUMMARY_ID = 1002
    const val ENDOFDAY_ID = 1003

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val live = NotificationChannel(
            CHANNEL_LIVE,
            context.getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.tracking_channel_desc)
            setShowBadge(false)
        }
        val summary = NotificationChannel(
            CHANNEL_SUMMARY,
            context.getString(R.string.summary_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.summary_channel_desc) }
        nm.createNotificationChannel(live)
        nm.createNotificationChannel(summary)
    }

    fun liveNotification(context: Context, state: LiveState) = run {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (state.paused) "Tracking (paused)" else "Tracking ride"
        val text = "Trip ${Format.km(state.tripDistanceM)} km • ${state.speedKmh.toInt()} km/h • ${Format.duration(state.tripDurationS)}"
        NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun backHomeNotification(context: Context, s: OutingSummary): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val outMin = ((s.endT - s.startT) / 60000L).toInt()
        val lines = buildString {
            append("${Format.km(s.distanceM)} km • ${s.legCount} trips • ${s.placeCount} places\n")
            append("Out for ${outMin} min • top ${s.topSpeedKmh} km/h")
            s.newRecord?.let { append("\n🏆 $it") }
        }
        return NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Back home")
            .setContentText("${Format.km(s.distanceM)} km • ${s.legCount} trips")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    fun endOfDayNotification(context: Context, bikeKm: String, allKm: String, trips: Int): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Today's riding")
            .setContentText("$bikeKm km on the bike • $trips trips")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bikeKm km bike • $allKm km all modes • $trips trips"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }
}
