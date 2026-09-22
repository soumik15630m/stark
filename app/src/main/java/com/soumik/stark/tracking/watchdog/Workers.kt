package com.soumik.stark.tracking.watchdog

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soumik.stark.core.time.TimeUtils
import com.soumik.stark.core.util.Format
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.tracking.service.Notifications
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Health-check: if tracking should be on but the service died, restart it (design §4.8). */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Prefs.getBool(applicationContext, Prefs.KEY_TRACKING_ACTIVE) && !TrackingController.isTracking) {
            try { TrackingForegroundService.start(applicationContext) } catch (_: Exception) {}
        }
        return Result.success()
    }
}

/** Fires the end-of-day summary notification (design §8.4). */
class EndOfDayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = TrackRepository.get(applicationContext)
        val today = repo.totalsDao.daily(TimeUtils.todayKey()) ?: return Result.success()
        if (today.tripCount == 0) return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.notify(
            Notifications.ENDOFDAY_ID,
            Notifications.endOfDayNotification(applicationContext, Format.km(today.distanceBikeM), Format.km(today.distanceAllM), today.tripCount)
        )
        return Result.success()
    }
}

object Watchdog {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "stark-watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
        wm.enqueueUniquePeriodicWork(
            "stark-endofday",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<EndOfDayWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(msUntil(21, 30), TimeUnit.MILLISECONDS)
                .build()
        )
    }

    private fun msUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
