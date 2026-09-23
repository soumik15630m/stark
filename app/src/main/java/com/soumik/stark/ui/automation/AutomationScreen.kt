package com.soumik.stark.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.domain.PostTripProcessor
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(onBack: () -> Unit, onOpenUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var master by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_NET_MASTER, true)) }
    var map by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_NET_MAP, true)) }
    var geocode by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_NET_GEOCODE, true)) }
    var update by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_NET_UPDATE, true)) }
    var automationOut by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_AUTOMATION_OUT, false)) }
    var owner by remember { mutableStateOf(Prefs.getString(context, Prefs.KEY_UPDATE_OWNER)) }
    var repo by remember { mutableStateOf(Prefs.getString(context, Prefs.KEY_UPDATE_REPO)) }
    var updateStatus by remember { mutableStateOf("") }
    var googleKey by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        googleKey = withContext(Dispatchers.IO) { TrackRepository.get(context).setting(PostTripProcessor.SETTING_GOOGLE_KEY) } ?: ""
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Automation & network") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Network", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Offline-first. Only these three touch the network — everything else stays on the phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Toggle("Master switch (airplane mode off)", master) { master = it; Prefs.setBool(context, Prefs.KEY_NET_MASTER, it) }
                Toggle("Map tiles", map) { map = it; Prefs.setBool(context, Prefs.KEY_NET_MAP, it) }
                Toggle("Reverse geocoding", geocode) { geocode = it; Prefs.setBool(context, Prefs.KEY_NET_GEOCODE, it) }
                Toggle("Update checks", update) { update = it; Prefs.setBool(context, Prefs.KEY_NET_UPDATE, it) }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Automation", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Broadcasts trip start/end, milestones and the daily summary (no raw coordinates) for Tasker/MacroDroid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Toggle("Emit automation broadcasts", automationOut) { automationOut = it; Prefs.setBool(context, Prefs.KEY_AUTOMATION_OUT, it) }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Optional: your Google Maps key", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Not required — maps use OpenStreetMap and geocoding works keyless. If you add your OWN Google Maps Platform key, reverse-geocoding will use Google. The key is stored only in the encrypted database, never in the app or repo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(googleKey, { googleKey = it }, label = { Text("Google Maps Platform API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { TrackRepository.get(context).putSetting(PostTripProcessor.SETTING_GOOGLE_KEY, googleKey.trim()) }
                        keyMsg = if (googleKey.isBlank()) "Cleared — back to keyless geocoding." else "Saved. Geocoding will use your Google key."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save key") }
                if (keyMsg.isNotEmpty()) Text(keyMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(6.dp))
                Text("Before adding a key: restrict it to this app's package + signing SHA-1, enable only the Geocoding API, use a dedicated GCP project, and set a hard budget cap with billing auto-disable. Worst case then stays near-zero.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Self-update (GitHub Releases)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(owner, { owner = it; Prefs.setString(context, Prefs.KEY_UPDATE_OWNER, it) }, label = { Text("GitHub owner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(repo, { repo = it; Prefs.setString(context, Prefs.KEY_UPDATE_REPO, it) }, label = { Text("Repo name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        updateStatus = "Checking…"
                        val info = withContext(Dispatchers.IO) { runCatching { UpdateChecker(context).check() }.getOrNull() }
                        updateStatus = when {
                            info == null -> "No release found (check repo & network toggle)."
                            UpdateChecker(context).isNewer(info.versionName) -> "Update available: ${info.tag}"
                            else -> "You're on the latest (${UpdateChecker(context).currentVersion()})."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
                if (updateStatus.isNotEmpty()) Text(updateStatus, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(6.dp))
                Button(onClick = onOpenUpdate, modifier = Modifier.fillMaxWidth()) { Text("Open updater (download & install)") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked, onChange)
    }
}
