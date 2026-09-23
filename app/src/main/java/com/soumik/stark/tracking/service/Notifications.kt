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
    const val CHANNEL_SUMMARY = "back-home-summary"
    const val CHANNEL_ENDOFDAY = "end-of-day"
    const val CHANNEL_MILESTONE = "milestones"
    const val CHANNEL_PAUSED = "tracking-paused"
    const val CHANNEL_UPDATE = "update-available"
    const val LIVE_ID = 1001
    const val SUMMARY_ID = 1002
    const val ENDOFDAY_ID = 1003
    const val UPDATE_ID = 1004

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
        // A separate, individually-tunable channel per notification type (design §8.4).
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ENDOFDAY, "End of day", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MILESTONE, "Milestones", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_PAUSED, "Tracking paused", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATE, "Update available", NotificationManager.IMPORTANCE_LOW))
    }

    fun updateNotification(context: Context, tag: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Stark update available")
            .setContentText("Version $tag is ready — tap to review in the app.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun serviceAction(context: Context, action: String, reqCode: Int): PendingIntent {
        val i = Intent(context, TrackingForegroundService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            context, reqCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun liveNotification(context: Context, state: LiveState): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title: String
        val text: String
        when (state.state) {
            TrackState.PAUSED -> {
                title = "Tracking paused"
                text = "Trip ${Format.km(state.tripDistanceM)} km • tap Resume to continue"
            }
            TrackState.ARMED -> {
                title = "Stark is on"
                text = "Waiting for your next ride"
            }
            else -> {
                title = "Tracking ride"
                text = "${Format.km(state.tripDistanceM)} km • ${state.speedKmh.toInt()} km/h • ${Format.duration(state.tripDurationS)}"
            }
        }
        val b = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        when (state.state) {
            TrackState.ACTIVE -> {
                b.addAction(0, "Pause", serviceAction(context, TrackingForegroundService.ACTION_PAUSE, 11))
                b.addAction(0, "Stop", serviceAction(context, TrackingForegroundService.ACTION_STOP_TRIP, 12))
            }
            TrackState.PAUSED -> {
                b.addAction(0, "Resume", serviceAction(context, TrackingForegroundService.ACTION_RESUME, 13))
                b.addAction(0, "Stop", serviceAction(context, TrackingForegroundService.ACTION_STOP_TRIP, 12))
            }
            else -> {
                b.addAction(0, "Turn off", serviceAction(context, TrackingForegroundService.ACTION_DISABLE, 14))
            }
        }
        return b.build()
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
        return NotificationCompat.Builder(context, CHANNEL_ENDOFDAY)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Today's riding")
            .setContentText("$bikeKm km on the bike • $trips trips")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bikeKm km bike • $allKm km all modes • $trips trips"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }
}
