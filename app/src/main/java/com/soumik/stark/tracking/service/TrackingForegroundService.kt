package com.soumik.stark.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.tracking.filter.LocationFilter
import com.soumik.stark.tracking.location.SamplingPolicy
import com.soumik.stark.tracking.segmentation.Segmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class TrackingForegroundService : LifecycleService() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repo: TrackRepository
    private val filter = LocationFilter()
    private lateinit var segmenter: Segmenter

    private val pipelineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val pipelineScope = CoroutineScope(SupervisorJob() + pipelineDispatcher)
    private val fixChannel = Channel<Location>(Channel.UNLIMITED)

    @Volatile private var dashboardMode = false
    @Volatile private var lastFixWallClock = 0L
    private var fixCount = 0
    private var emaSpeedKmh = 0.0
    private var lastNotifyAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) fixChannel.trySend(loc)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> requestUpdates()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        repo = TrackRepository.get(this)
        segmenter = Segmenter(repo)
        Notifications.ensureChannels(this)
        registerReceiver(
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )
        consumeFixes()
        watchdogTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_DASHBOARD_ON -> {
                dashboardMode = true
                requestUpdates()
            }
            ACTION_DASHBOARD_OFF -> {
                dashboardMode = false
                requestUpdates()
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        com.soumik.stark.core.util.Prefs.setBool(
            this, com.soumik.stark.core.util.Prefs.KEY_TRACKING_ACTIVE, true
        )
        TrackingController.set(LiveState(tracking = true))
        val notif = Notifications.liveNotification(this, TrackingController.state.value)
        ServiceCompat.startForeground(
            this, Notifications.LIVE_ID, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        requestUpdates()
    }

    private fun requestUpdates() {
        val mode = when {
            dashboardMode -> SamplingPolicy.Mode.DASHBOARD
            isCharging() -> SamplingPolicy.Mode.CHARGING
            else -> SamplingPolicy.Mode.BACKGROUND
        }
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(
                SamplingPolicy.request(mode), callback, Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun consumeFixes() {
        pipelineScope.launch {
            for (loc in fixChannel) {
                val fix = filter.accept(com.soumik.stark.tracking.filter.RawFix.from(loc)) ?: continue
                fixCount++
                lastFixWallClock = System.currentTimeMillis()
                val snap = segmenter.onFix(fix)
                emaSpeedKmh = if (emaSpeedKmh == 0.0) Format.kmhExact(snap.speedMps)
                else emaSpeedKmh * (1 - EMA_ALPHA) + Format.kmhExact(snap.speedMps) * EMA_ALPHA
                TrackingController.update {
                    it.copy(
                        tracking = true,
                        speedKmh = emaSpeedKmh,
                        tripDistanceM = snap.tripDistanceM,
                        tripDurationS = snap.tripDurationS,
                        tripMaxSpeedKmh = Format.kmhExact(snap.maxSpeedMps),
                        gpsFixCount = fixCount,
                        paused = snap.paused,
                    )
                }
                if (snap.legClosed) filter.reset()
                maybeUpdateNotification()
            }
        }
    }

    /** Zero the live speed when fixes stop arriving so a stale reading never lingers on the dial. */
    private fun watchdogTicker() {
        lifecycleScope.launch {
            while (isActive) {
                delay(2000)
                val age = System.currentTimeMillis() - lastFixWallClock
                if (lastFixWallClock != 0L && age > 4000) {
                    emaSpeedKmh = 0.0
                    TrackingController.update { it.copy(speedKmh = 0.0, lastFixAgeMs = age) }
                    maybeUpdateNotification()
                }
            }
        }
    }

    private fun maybeUpdateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 5000) return
        lastNotifyAt = now
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifications.LIVE_ID, Notifications.liveNotification(this, TrackingController.state.value))
    }

    private fun stopTracking() {
        com.soumik.stark.core.util.Prefs.setBool(
            this, com.soumik.stark.core.util.Prefs.KEY_TRACKING_ACTIVE, false
        )
        pipelineScope.launch {
            segmenter.finish(System.currentTimeMillis())
        }
        try {
            fused.removeLocationUpdates(callback)
        } catch (_: Exception) {}
        TrackingController.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isCharging(): Boolean {
        val bm = getSystemService(BatteryManager::class.java)
        return bm?.isCharging == true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep tracking after the app is swiped away; the OS restarts a START_STICKY service.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            fused.removeLocationUpdates(callback)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {}
        fixChannel.close()
        pipelineDispatcher.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.soumik.stark.STOP"
        const val ACTION_DASHBOARD_ON = "com.soumik.stark.DASHBOARD_ON"
        const val ACTION_DASHBOARD_OFF = "com.soumik.stark.DASHBOARD_OFF"
        private const val EMA_ALPHA = 0.4

        fun start(context: Context) {
            val i = Intent(context, TrackingForegroundService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, TrackingForegroundService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }

        fun setDashboard(context: Context, on: Boolean) {
            if (!TrackingController.isTracking) return
            val i = Intent(context, TrackingForegroundService::class.java).apply {
                action = if (on) ACTION_DASHBOARD_ON else ACTION_DASHBOARD_OFF
            }
            context.startService(i)
        }
    }
}
