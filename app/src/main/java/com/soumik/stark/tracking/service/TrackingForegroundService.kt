package com.soumik.stark.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
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
import com.google.android.gms.location.Priority
import com.soumik.stark.core.util.Format
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.domain.PostTripProcessor
import com.soumik.stark.tracking.filter.LocationFilter
import com.soumik.stark.tracking.filter.RawFix
import com.soumik.stark.tracking.location.SamplingPolicy
import com.soumik.stark.tracking.segmentation.Segmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Always-on, motion-gated tracker. Once enabled it stays a foreground service with a persistent
 * notification (survives app close, restarts on boot). GPS runs only when moving: ARMED (idle, no
 * GPS, significant-motion wake) → ACTIVE (capturing) → back to ARMED on a confirmed stop. Pause
 * and Stop are notification actions.
 */
class TrackingForegroundService : LifecycleService() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repo: TrackRepository
    private val filter = LocationFilter()
    private lateinit var segmenter: Segmenter
    private val postProcessor by lazy { PostTripProcessor(this) }

    private val pipelineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val pipelineScope = CoroutineScope(SupervisorJob() + pipelineDispatcher)
    private val fixChannel = Channel<Location>(Channel.UNLIMITED)

    @Volatile private var dashboardMode = false
    @Volatile private var lastFixWallClock = 0L
    private var fixCount = 0
    private var emaSpeedKmh = 0.0
    private var lastNotifyAt = 0L
    private var sigMotion: TriggerEventListener? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) fixChannel.trySend(loc)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED ->
                    if (TrackingController.isTracking) requestUpdates()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        repo = TrackRepository.get(this)
        segmenter = Segmenter(repo)
        Notifications.ensureChannels(this)
        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        consumeFixes()
        watchdogTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Ensure we're a foreground service ASAP whenever started.
        promoteForeground()
        when (intent?.action) {
            ACTION_DISABLE -> { disable(startId); return START_NOT_STICKY }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> goActive()
            ACTION_STOP_TRIP -> stopTrip()
            ACTION_ENABLE_ARMED -> { Prefs.setBool(this, Prefs.KEY_TRACKING_ENABLED, true); goArmed() }
            ACTION_DASHBOARD_ON -> { dashboardMode = true; if (TrackingController.isTracking) requestUpdates() }
            ACTION_DASHBOARD_OFF -> { dashboardMode = false; if (TrackingController.isTracking) requestUpdates() }
            else -> { Prefs.setBool(this, Prefs.KEY_TRACKING_ENABLED, true); goActive() }
        }
        updateNotification(force = true)
        return START_STICKY
    }

    private fun promoteForeground() {
        val notif = Notifications.liveNotification(this, TrackingController.state.value)
        ServiceCompat.startForeground(this, Notifications.LIVE_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    /** ACTIVE: GPS on, capturing. */
    private fun goActive() {
        cancelSigMotion()
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, true)
        val was = TrackingController.state.value.state
        TrackingController.setState(TrackState.ACTIVE)
        if (was != TrackState.ACTIVE) {
            com.soumik.stark.automation.Automation.emit(this, com.soumik.stark.automation.Automation.ACTION_TRIP_START)
        }
        requestUpdates()
        seedWarmupFix()
        updateNotification(force = true)
    }

    /** ARMED: enabled but idle — no GPS, wait for significant motion. */
    private fun goArmed() {
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, false)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        emaSpeedKmh = 0.0
        TrackingController.set(LiveState(state = TrackState.ARMED))
        armSigMotion()
        updateNotification(force = true)
    }

    private fun pause() {
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, false)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        pipelineScope.launch { segmenter.pauseBreak(System.currentTimeMillis()) }
        emaSpeedKmh = 0.0
        TrackingController.update { it.copy(state = TrackState.PAUSED, speedKmh = 0.0) }
        updateNotification(force = true)
    }

    /** End the current trip but stay enabled/armed for the next ride. */
    private fun stopTrip() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        pipelineScope.launch {
            val closed = segmenter.finish(System.currentTimeMillis())
            closed?.let { postProcess(it) }
            filter.reset()
        }
        goArmed()
    }

    /** Turn tracking off entirely. Uses stopSelf(startId) so a racing re-enable is not torn down. */
    private fun disable(startId: Int) {
        Prefs.setBool(this, Prefs.KEY_TRACKING_ENABLED, false)
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, false)
        cancelSigMotion()
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        pipelineScope.launch {
            val closed = segmenter.finish(System.currentTimeMillis())
            closed?.let { postProcess(it) }
        }
        TrackingController.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun armSigMotion() {
        cancelSigMotion()
        val sm = getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                sigMotion = null
                if (TrackingController.state.value.state == TrackState.ARMED) goActive()
            }
        }
        sigMotion = listener
        sm.requestTriggerSensor(listener, sensor)
    }

    private fun cancelSigMotion() {
        val sm = getSystemService(SensorManager::class.java) ?: return
        sigMotion?.let { sm.cancelTriggerSensor(it, sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)) }
        sigMotion = null
    }

    @Suppress("MissingPermission")
    private fun seedWarmupFix() {
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (loc != null) fixChannel.trySend(loc) }
        } catch (_: SecurityException) {}
    }

    private fun requestUpdates() {
        val mode = when {
            dashboardMode -> SamplingPolicy.Mode.DASHBOARD
            isCharging() -> SamplingPolicy.Mode.CHARGING
            else -> SamplingPolicy.Mode.BACKGROUND
        }
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(SamplingPolicy.request(mode), callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            goArmed()
        }
    }

    private fun consumeFixes() {
        pipelineScope.launch {
            for (loc in fixChannel) {
                if (TrackingController.state.value.state != TrackState.ACTIVE) continue
                val fix = filter.accept(RawFix.from(loc)) ?: continue
                fixCount++
                lastFixWallClock = System.currentTimeMillis()
                val snap = segmenter.onFix(fix)
                emaSpeedKmh = if (emaSpeedKmh == 0.0) Format.kmhExact(snap.speedMps)
                else emaSpeedKmh * (1 - EMA_ALPHA) + Format.kmhExact(snap.speedMps) * EMA_ALPHA
                TrackingController.update {
                    it.copy(
                        state = TrackState.ACTIVE,
                        speedKmh = emaSpeedKmh,
                        tripDistanceM = snap.tripDistanceM,
                        tripDurationS = snap.tripDurationS,
                        tripMaxSpeedKmh = Format.kmhExact(snap.maxSpeedMps),
                        gpsFixCount = fixCount,
                    )
                }
                if (snap.legClosed) {
                    filter.reset()
                    snap.closedLegId?.let { postProcess(it) }
                    // Confirmed stop → drop GPS and wait for motion again.
                    launchMain { goArmed() }
                }
                maybeUpdateNotification()
            }
        }
    }

    private fun launchMain(block: () -> Unit) {
        lifecycleScope.launch { block() }
    }

    private fun watchdogTicker() {
        lifecycleScope.launch {
            while (isActive) {
                delay(2000)
                val age = System.currentTimeMillis() - lastFixWallClock
                if (TrackingController.isTracking && lastFixWallClock != 0L && age > 4000) {
                    emaSpeedKmh = 0.0
                    TrackingController.update { it.copy(speedKmh = 0.0, lastFixAgeMs = age) }
                    maybeUpdateNotification()
                }
            }
        }
    }

    private fun postProcess(legId: Long) {
        pipelineScope.launch {
            val leg = try { repo.legDao.byId(legId) } catch (_: Exception) { null }
            leg?.let {
                com.soumik.stark.automation.Automation.emit(
                    this@TrackingForegroundService,
                    com.soumik.stark.automation.Automation.ACTION_TRIP_END,
                    mapOf("distanceM" to it.distanceM, "durationS" to it.durationS, "mode" to it.mode.name),
                )
            }
            val summary = try { postProcessor.process(legId) } catch (_: Exception) { null }
            if (summary != null) {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.notify(Notifications.SUMMARY_ID, Notifications.backHomeNotification(this@TrackingForegroundService, summary))
            }
        }
    }

    private fun maybeUpdateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 5000) return
        updateNotification(force = false)
    }

    private fun updateNotification(force: Boolean) {
        lastNotifyAt = System.currentTimeMillis()
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifications.LIVE_ID, Notifications.liveNotification(this, TrackingController.state.value))
    }

    private fun isCharging(): Boolean = getSystemService(BatteryManager::class.java)?.isCharging == true

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running after the app is swiped away.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cancelSigMotion()
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        fixChannel.close()
        pipelineDispatcher.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISABLE = "com.soumik.stark.DISABLE"
        const val ACTION_PAUSE = "com.soumik.stark.PAUSE"
        const val ACTION_RESUME = "com.soumik.stark.RESUME"
        const val ACTION_STOP_TRIP = "com.soumik.stark.STOP_TRIP"
        const val ACTION_ENABLE_ARMED = "com.soumik.stark.ENABLE_ARMED"
        const val ACTION_DASHBOARD_ON = "com.soumik.stark.DASHBOARD_ON"
        const val ACTION_DASHBOARD_OFF = "com.soumik.stark.DASHBOARD_OFF"
        private const val EMA_ALPHA = 0.35 // calmer needle; recorded speed stays the raw filtered value

        private fun send(context: Context, action: String?) {
            val i = Intent(context, TrackingForegroundService::class.java)
            if (action != null) i.action = action
            context.startForegroundService(i)
        }

        /** Enable + start capturing now (user tapped Start). */
        fun start(context: Context) = send(context, null)
        /** Enable in armed mode (boot); waits for motion. */
        fun enableArmed(context: Context) = send(context, ACTION_ENABLE_ARMED)
        /** Turn tracking off entirely. */
        fun disable(context: Context) = send(context, ACTION_DISABLE)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stopTrip(context: Context) = send(context, ACTION_STOP_TRIP)

        // Back-compat with existing callers.
        fun stop(context: Context) = disable(context)

        fun setDashboard(context: Context, on: Boolean) {
            if (!TrackingController.isEnabled) return
            send(context, if (on) ACTION_DASHBOARD_ON else ACTION_DASHBOARD_OFF)
        }
    }
}
