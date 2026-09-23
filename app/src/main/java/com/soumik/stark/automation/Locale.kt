package com.soumik.stark.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.soumik.stark.tracking.watchdog.AutoBackupWorker

/** Locale/Tasker plugin protocol constants (design §10). */
object Locale {
    const val ACTION_EDIT = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val KEY_COMMAND = "stark_command"
    const val CMD_FORCE_BACKUP = "force_backup"
    const val CMD_ADD_PLACE = "add_place"
}

/** Receives Tasker/Locale FIRE and performs the configured Stark command. */
class LocaleFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Locale.ACTION_FIRE) return
        val bundle: Bundle = intent.getBundleExtra(Locale.EXTRA_BUNDLE) ?: return
        when (bundle.getString(Locale.KEY_COMMAND)) {
            Locale.CMD_FORCE_BACKUP ->
                WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
            Locale.CMD_ADD_PLACE ->
                context.sendBroadcast(Intent(Automation.CMD_ADD_PLACE).setPackage(context.packageName))
        }
    }
}
