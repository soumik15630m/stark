package com.soumik.stark.tracking.gating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService

/** Detected-activity updates → mode-change auto-split while a trip is active (design §4.11). */
class ActivityUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val a = ActivityRecognitionResult.extractResult(intent)?.mostProbableActivity ?: return
        if (a.confidence < 50) return
        val mode = when (a.type) {
            DetectedActivity.IN_VEHICLE -> TravelMode.VEHICLE
            DetectedActivity.ON_BICYCLE -> TravelMode.BICYCLE
            DetectedActivity.WALKING -> TravelMode.WALK
            DetectedActivity.RUNNING -> TravelMode.RUN
            else -> null
        } ?: return
        if (TrackingController.isTracking) TrackingForegroundService.reportMode(context, mode)
    }
}

/** Geofence-exit from the current stop → cheap wake to resume tracking (design §4.2). */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            if (!TrackingController.isTracking) TrackingForegroundService.start(context)
        }
    }
}
