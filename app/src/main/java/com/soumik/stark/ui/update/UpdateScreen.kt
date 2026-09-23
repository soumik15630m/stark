package com.soumik.stark.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.update.ReleaseInfo
import com.soumik.stark.update.UpdateChecker
import com.soumik.stark.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface UiState {
    data object Checking : UiState
    data object UpToDate : UiState
    data class Available(val info: ReleaseInfo) : UiState
    data class Downloading(val pct: Int) : UiState
    data class Ready(val info: ReleaseInfo) : UiState
    data class Failed(val msg: String) : UiState
    data object NotConfigured : UiState
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checker = remember { UpdateChecker(context) }
    var state by remember { mutableStateOf<UiState>(UiState.Checking) }

    suspend fun check() {
        val owner = Prefs.getString(context, Prefs.KEY_UPDATE_OWNER)
        val repo = Prefs.getString(context, Prefs.KEY_UPDATE_REPO)
        if (owner.isBlank() || repo.isBlank()) { state = UiState.NotConfigured; return }
        state = UiState.Checking
        val info = withContext(Dispatchers.IO) { runCatching { checker.check() }.getOrNull() }
        state = when {
            info == null -> UiState.Failed("No release found (check repo name and the update network toggle).")
            checker.isNewer(info.versionName) -> UiState.Available(info)
            else -> UiState.UpToDate
        }
    }

    LaunchedEffect(Unit) { check() }

    fun download(info: ReleaseInfo) {
        val url = info.apkUrl
        if (url == null) { state = UiState.Failed("Release has no APK attached."); return }
        scope.launch {
            state = UiState.Downloading(0)
            val result = withContext(Dispatchers.IO) {
                UpdateInstaller(context).downloadAndVerify(url, info.sha256) { pct ->
                    state = UiState.Downloading(pct)
                }
            }
            when (result) {
                is UpdateInstaller.Result.Ready -> {
                    state = UiState.Ready(info)
                    UpdateInstaller(context).launchInstaller(result.file)
                }
                is UpdateInstaller.Result.Failed -> state = UiState.Failed(result.reason)
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("App update") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Installed: v${checker.currentVersion()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val s = state) {
                is UiState.Checking -> Text("Checking for updates…")
                is UiState.UpToDate -> Text("You're on the latest version. ✓", color = MaterialTheme.colorScheme.primary)
                is UiState.NotConfigured -> SectionCard(Modifier.fillMaxWidth()) {
                    Text("Set the GitHub repo first", fontWeight = FontWeight.SemiBold)
                    Text("More → Automation & network → GitHub owner + repo, then come back.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UiState.Available -> SectionCard(Modifier.fillMaxWidth()) {
                    Text("Update available: ${s.info.tag}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(s.info.notes.take(1200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { download(s.info) }, modifier = Modifier.fillMaxWidth()) { Text("Download & install") }
                    Text("Verified by checksum + signing certificate before install.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                is UiState.Downloading -> {
                    Text("Downloading… ${s.pct}%")
                    LinearProgressIndicator(progress = { s.pct / 100f }, modifier = Modifier.fillMaxWidth())
                }
                is UiState.Ready -> Text("Verified. Opening installer for ${s.info.tag}…", color = MaterialTheme.colorScheme.primary)
                is UiState.Failed -> {
                    Text(s.msg, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { check() } }) { Text("Retry") }
                }
            }
        }
    }
}
