package com.soumik.stark.ui.qstile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService

/** Quick Settings tile: start / stop tracking (design §8.2). */
class TrackTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (TrackingController.isTracking) {
            TrackingForegroundService.stop(this)
        } else {
            TrackingForegroundService.start(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val tracking = TrackingController.isTracking
        tile.state = if (tracking) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (tracking) "Tracking on" else "Track ride"
        tile.updateTile()
    }
}
