package com.soumik.stark.app

import android.app.Application
import com.soumik.stark.tracking.service.Notifications
import org.osmdroid.config.Configuration

class StarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getDir("osmdroid", MODE_PRIVATE)
            osmdroidTileCache = getDir("osmdroid_tiles", MODE_PRIVATE)
        }
        com.soumik.stark.tracking.watchdog.Watchdog.schedule(this)
        com.soumik.stark.tracking.gating.MotionGate.arm(this)
    }
}
