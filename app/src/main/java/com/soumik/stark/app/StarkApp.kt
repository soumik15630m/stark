package com.soumik.stark.app

import android.app.Application
import com.soumik.stark.tracking.service.Notifications

class StarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
