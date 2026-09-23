package com.soumik.stark.ui.automation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.soumik.stark.automation.Locale
import com.soumik.stark.ui.theme.StarkTheme

/** Tasker/Locale plugin edit screen — pick the Stark command this task should fire (design §10). */
class LocaleEditActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StarkTheme { Picker(::done) } }
    }

    private fun done(command: String, blurb: String) {
        val bundle = Bundle().apply { putString(Locale.KEY_COMMAND, command) }
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(Locale.EXTRA_BUNDLE, bundle)
            putExtra(Locale.EXTRA_BLURB, blurb)
        })
        finish()
    }
}

@Composable
private fun Picker(onPick: (String, String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Stark action for this task", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { onPick(Locale.CMD_FORCE_BACKUP, "Stark: back up now") }, modifier = Modifier.fillMaxWidth()) { Text("Back up now") }
        Button(onClick = { onPick(Locale.CMD_ADD_PLACE, "Stark: mark current place") }, modifier = Modifier.fillMaxWidth()) { Text("Mark current place") }
    }
}
