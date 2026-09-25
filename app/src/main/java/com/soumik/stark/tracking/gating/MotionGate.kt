package com.soumik.stark.tracking.gating

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService

/**
 * Idle motion gating (design §4.2, §4B). Activity-Recognition transitions wake the app via a
 * PendingIntent broadcast even when the process isn't running and auto-start tracking on
 * movement — the deepest-idle, near-zero-power gate. The significant-motion sensor is a
 * supplementary in-process kick.
 */
object MotionGate {
    const val ACTION_TRANSITION = "com.soumik.stark.ACTIVITY_TRANSITION"
    private var sigMotionListener: TriggerEventListener? = null

    @SuppressLint("MissingPermission")
    fun arm(context: Context) {
        if (!Prefs.getBool(context, Prefs.KEY_AUTO_TRACK, false)) return
        // Only motorised/cycling rides arm auto-start — this app isn't for walking.
        val transitions = listOf(
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE,
        ).map {
            ActivityTransition.Builder()
                .setActivityType(it)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        val request = ActivityTransitionRequest(transitions)
        val pi = PendingIntent.getBroadcast(
            context, 77,
            Intent(context, MotionReceiver::class.java).setAction(ACTION_TRANSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try {
            ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pi)
        } catch (_: SecurityException) {}
        armSignificantMotion(context)
    }

    @SuppressLint("MissingPermission")
    fun disarm(context: Context) {
        val pi = PendingIntent.getBroadcast(
            context, 77,
            Intent(context, MotionReceiver::class.java).setAction(ACTION_TRANSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try {
            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pi)
        } catch (_: Exception) {}
        val sm = context.getSystemService(SensorManager::class.java)
        sigMotionListener?.let { sm?.cancelTriggerSensor(it, sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)) }
        sigMotionListener = null
    }

    private fun armSignificantMotion(context: Context) {
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                // Confirm genuine movement before logging — a mere phone pickup must not start a ride.
                if (!TrackingController.isTracking) TrackingForegroundService.autoStart(context)
                sigMotionListener = null // one-shot; re-armed after the next idle period
            }
        }
        sigMotionListener = listener
        sm.requestTriggerSensor(listener, sensor)
    }
}

/** Receives Activity-Recognition transitions and starts tracking when movement begins. */
class MotionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MotionGate.ACTION_TRANSITION) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val movingEnter = result.transitionEvents.any {
            it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                it.activityType in setOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE)
        }
        if (movingEnter && !TrackingController.isTracking) {
            TrackingForegroundService.autoStart(context)
        }
    }
}
