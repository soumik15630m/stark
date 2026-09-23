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
import android.os.PowerManager
import com.soumik.stark.core.util.Telemetry
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
    @Volatile private var thermalEase = false
    private var batteryPaused = false
    @Volatile private var accelMoving = false
    private var accelDevEma = 0.0
    private var lastBearing = Double.NaN
    private var turnAccum = 0.0
    @Volatile private var turning = false
    private var lastBearingLat = 0.0
    private var lastBearingLng = 0.0

    private val accelListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val m = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
            val dev = Math.abs(m - 9.81)
            accelDevEma = accelDevEma * 0.8 + dev * 0.2
            accelMoving = accelDevEma > 0.6
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermalEase = status >= PowerManager.THERMAL_STATUS_SEVERE
        if (status >= PowerManager.THERMAL_STATUS_MODERATE) Telemetry.onThermalEvent(this, status)
        if (TrackingController.isTracking) requestUpdates()
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) fixChannel.trySend(loc)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    if (batteryPaused) { batteryPaused = false; if (TrackingController.isEnabled) goActive() }
                    else if (TrackingController.isTracking) requestUpdates()
                }
                Intent.ACTION_POWER_DISCONNECTED -> if (TrackingController.isTracking) requestUpdates()
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
        try {
            getSystemService(PowerManager::class.java)?.addThermalStatusListener(thermalListener)
        } catch (_: Exception) {}
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
            ACTION_MODE -> intent.getStringExtra(EXTRA_MODE)?.let { onReportedMode(com.soumik.stark.data.entity.TravelMode.valueOf(it)) }
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
        batteryPaused = false
        Telemetry.onServiceEnabled(this)
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, true)
        val was = TrackingController.state.value.state
        TrackingController.setState(TrackState.ACTIVE)
        if (was != TrackState.ACTIVE) {
            com.soumik.stark.automation.Automation.emit(this, com.soumik.stark.automation.Automation.ACTION_TRIP_START)
        }
        registerAccel()
        requestActivityUpdates()
        removeStopGeofence()
        requestUpdates()
        seedWarmupFix()
        updateNotification(force = true)
    }

    private var modeStreak = 0
    private fun onReportedMode(m: com.soumik.stark.data.entity.TravelMode) {
        if (!TrackingController.isTracking) return
        if (m == segmenter.currentMode()) { modeStreak = 0; return }
        modeStreak++
        if (modeStreak >= 2) {  // hysteresis — avoid splitting on brief flickers
            modeStreak = 0
            pipelineScope.launch {
                val closed = segmenter.finish(System.currentTimeMillis())
                closed?.let { postProcess(it) }
                filter.reset()
                segmenter.setMode(m)
            }
        }
    }

    private fun activityPi() = android.app.PendingIntent.getBroadcast(
        this, 88, Intent(this, com.soumik.stark.tracking.gating.ActivityUpdateReceiver::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
    )

    @Suppress("MissingPermission")
    private fun requestActivityUpdates() {
        try { com.google.android.gms.location.ActivityRecognition.getClient(this).requestActivityUpdates(20_000, activityPi()) } catch (_: Exception) {}
    }

    private fun removeActivityUpdates() {
        try { com.google.android.gms.location.ActivityRecognition.getClient(this).removeActivityUpdates(activityPi()) } catch (_: Exception) {}
    }

    private fun geofencePi() = android.app.PendingIntent.getBroadcast(
        this, 89, Intent(this, com.soumik.stark.tracking.gating.GeofenceReceiver::class.java),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
    )

    @Suppress("MissingPermission")
    private fun addStopGeofence() {
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener
                val gf = com.google.android.gms.location.Geofence.Builder()
                    .setRequestId("stark-stop")
                    .setCircularRegion(loc.latitude, loc.longitude, 120f)
                    .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
                val req = com.google.android.gms.location.GeofencingRequest.Builder().addGeofence(gf).build()
                com.google.android.gms.location.LocationServices.getGeofencingClient(this).addGeofences(req, geofencePi())
            }
        } catch (_: Exception) {}
    }

    private fun removeStopGeofence() {
        try { com.google.android.gms.location.LocationServices.getGeofencingClient(this).removeGeofences(geofencePi()) } catch (_: Exception) {}
    }

    private fun registerAccel() {
        val sm = getSystemService(SensorManager::class.java) ?: return
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterAccel() {
        getSystemService(SensorManager::class.java)?.unregisterListener(accelListener)
        accelMoving = false
    }

    /** ARMED: enabled but idle — no GPS, wait for significant motion. */
    private fun goArmed() {
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, false)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        unregisterAccel()
        removeActivityUpdates()
        addStopGeofence()
        lastBearing = Double.NaN; turnAccum = 0.0; turning = false
        emaSpeedKmh = 0.0
        TrackingController.set(LiveState(state = TrackState.ARMED))
        armSigMotion()
        updateNotification(force = true)
    }

    private fun pause() {
        Prefs.setBool(this, Prefs.KEY_TRACKING_ACTIVE, false)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        unregisterAccel()
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
        val pct = batteryPct()
        val charging = isCharging()
        // Critical battery: pause tracking rather than log degraded data (design §4.3).
        if (!charging && pct in 1..CRITICAL_BATTERY) {
            pauseForBattery()
            return
        }
        val mode = when {
            thermalEase -> SamplingPolicy.Mode.LOW_POWER
            dashboardMode -> SamplingPolicy.Mode.DASHBOARD
            !charging && pct in 1..LOW_BATTERY -> SamplingPolicy.Mode.LOW_POWER
            turning -> SamplingPolicy.Mode.TURNING
            charging -> SamplingPolicy.Mode.CHARGING
            else -> SamplingPolicy.Mode.BACKGROUND
        }
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(SamplingPolicy.request(mode), callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            goArmed()
        }
    }

    private fun pauseForBattery() {
        if (batteryPaused) return
        batteryPaused = true
        Telemetry.onLowBatteryPause(this)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        TrackingController.update { it.copy(state = TrackState.PAUSED, speedKmh = 0.0) }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifications.LIVE_ID, Notifications.liveNotification(this, TrackingController.state.value))
    }

    private fun batteryPct(): Int =
        getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

    private fun consumeFixes() {
        pipelineScope.launch {
            for (loc in fixChannel) {
                if (TrackingController.state.value.state != TrackState.ACTIVE) continue
                val fix = filter.accept(RawFix.from(loc)) ?: continue
                fixCount++
                lastFixWallClock = System.currentTimeMillis()
                Telemetry.onFix(this@TrackingForegroundService)
                detectTurning(fix)
                val snap = segmenter.onFix(fix, accelMoving)
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

    /** Track heading change and switch to dense sampling through turns (curvature-adaptive). */
    private fun detectTurning(fix: com.soumik.stark.tracking.filter.FilteredFix) {
        if (lastBearingLat != 0.0 || lastBearingLng != 0.0) {
            val b = bearing(lastBearingLat, lastBearingLng, fix.lat, fix.lng)
            if (!lastBearing.isNaN()) {
                var d = Math.abs(b - lastBearing) % 360.0
                if (d > 180) d = 360 - d
                turnAccum = turnAccum * 0.6 + d
                val nowTurning = turnAccum > 35.0
                if (nowTurning != turning) {
                    turning = nowTurning
                    launchMain { if (TrackingController.isTracking) requestUpdates() }
                }
            }
            lastBearing = b
        }
        lastBearingLat = fix.lat; lastBearingLng = fix.lng
    }

    private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLon = Math.toRadians(lng2 - lng1)
        val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
            Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
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
                val thumb = try {
                    val outingLegs = repo.legDao.legsForOuting(summary.outingId)
                    com.soumik.stark.ui.common.RouteThumb.render(outingLegs.map { repo.pointsForLeg(it.id) })
                } catch (_: Exception) { null }
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm.notify(Notifications.SUMMARY_ID, Notifications.backHomeNotification(this@TrackingForegroundService, summary, thumb))
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
        unregisterAccel()
        try { getSystemService(PowerManager::class.java)?.removeThermalStatusListener(thermalListener) } catch (_: Exception) {}
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
        const val ACTION_MODE = "com.soumik.stark.MODE"
        const val EXTRA_MODE = "mode"
        private const val EMA_ALPHA = 0.35 // calmer needle; recorded speed stays the raw filtered value
        private const val LOW_BATTERY = 15
        private const val CRITICAL_BATTERY = 5

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

        fun reportMode(context: Context, mode: com.soumik.stark.data.entity.TravelMode) {
            if (!TrackingController.isTracking) return
            val i = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_MODE; putExtra(EXTRA_MODE, mode.name)
            }
            context.startForegroundService(i)
        }

        fun setDashboard(context: Context, on: Boolean) {
            if (!TrackingController.isEnabled) return
            send(context, if (on) ACTION_DASHBOARD_ON else ACTION_DASHBOARD_OFF)
        }
    }
}
