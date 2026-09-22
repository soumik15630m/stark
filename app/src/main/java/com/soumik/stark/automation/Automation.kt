package com.soumik.stark.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soumik.stark.core.util.Prefs

/**
 * Automation surface (design §10). Outbound broadcasts carry non-sensitive payloads only —
 * never raw coordinates. Inbound commands are gated by a signature-level permission.
 */
object Automation {
    const val ACTION_TRIP_START = "com.soumik.stark.event.TRIP_START"
    const val ACTION_TRIP_END = "com.soumik.stark.event.TRIP_END"
    const val ACTION_MILESTONE = "com.soumik.stark.event.MILESTONE"
    const val ACTION_DAILY_SUMMARY = "com.soumik.stark.event.DAILY_SUMMARY"

    const val CMD_FORCE_BACKUP = "com.soumik.stark.command.FORCE_BACKUP"
    const val CMD_ADD_PLACE = "com.soumik.stark.command.ADD_PLACE"

    fun emit(context: Context, action: String, extras: Map<String, Any> = emptyMap()) {
        if (!Prefs.getBool(context, Prefs.KEY_AUTOMATION_OUT, false)) return
        val intent = Intent(action).apply {
            setPackage(null)
            extras.forEach { (k, v) ->
                when (v) {
                    is Int -> putExtra(k, v)
                    is Long -> putExtra(k, v)
                    is Double -> putExtra(k, v)
                    is String -> putExtra(k, v)
                    is Boolean -> putExtra(k, v)
                }
            }
        }
        context.sendBroadcast(intent)
    }
}

/** Receives inbound automation commands; protected by a signature-level permission in the manifest. */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Automation.CMD_FORCE_BACKUP -> {
                // Scheduling a backup is deferred to the backup screen / WorkManager; acknowledge only.
            }
            Automation.CMD_ADD_PLACE -> {
                // A manual place marker at the current location would be added here.
            }
        }
    }
}
