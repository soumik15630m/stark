package com.soumik.stark.tracking.watchdog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.tracking.service.TrackingForegroundService

/** Re-arm tracking after a reboot if it was active when the device went down (design §4.8). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Prefs.getBool(context, Prefs.KEY_TRACKING_ACTIVE)) {
                TrackingForegroundService.start(context)
            }
            com.soumik.stark.tracking.gating.MotionGate.arm(context)
        }
    }
}
