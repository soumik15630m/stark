package com.soumik.stark.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.soumik.stark.data.backup.BackupManager
import com.soumik.stark.data.backup.StkCodec
import com.soumik.stark.location_import.TakeoutImporter
import com.soumik.stark.ui.common.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (pass.length < 4) { status = "Set a backup passphrase first (4+ chars)"; return@rememberLauncherForActivityResult }
        scope.launch {
            status = "Exporting…"
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val json = BackupManager(context).exportJson()
                    StkCodec.encrypt(json, pass)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                status = "Backup saved (${bytes.size / 1024} KB)."
            } catch (e: Exception) { status = "Export failed: ${e.message}" }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Restoring…"
            try {
                val n = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val json = StkCodec.decrypt(bytes, pass)
                    BackupManager(context).importJson(json)
                }
                status = "Restored $n trips. Reopen the app to refresh."
            } catch (e: Exception) { status = "Restore failed: ${e.message}" }
        }
    }

    val takeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Importing Timeline…"
            try {
                val n = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    TakeoutImporter(context).import(text)
                }
                status = if (n == 0)
                    "No trips found in that file. Pick your Timeline.json (or Semantic Location History .json)."
                else
                    "Imported $n trips from Google Timeline. Reopen the app to refresh."
            } catch (e: Exception) { status = "Import failed: ${e.message}" }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Backup & restore") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Encrypted backup (.stk)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Backup passphrase") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("AES-256-GCM. Separate from your app PIN — a leaked backup can't reveal your PIN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { exporter.launch("stark-backup.stk") }, modifier = Modifier.fillMaxWidth()) { Text("Export .stk") }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Restore from .stk") }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Automatic backups", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("A silent encrypted snapshot is saved daily (last 7 kept) to app storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        status = "Restoring latest auto-backup…"
                        try {
                            val n = withContext(Dispatchers.IO) {
                                val dir = java.io.File(context.getExternalFilesDir(null), "backups")
                                val latest = dir.listFiles { f -> f.name.endsWith(".stk") }?.maxByOrNull { it.lastModified() }
                                    ?: return@withContext -1
                                val pass = com.soumik.stark.core.crypto.DbKeys.deviceBackupPassphrase(context)
                                BackupManager(context).importJson(StkCodec.decrypt(latest.readBytes(), pass))
                            }
                            status = if (n < 0) "No auto-backup found yet." else "Restored $n trips from auto-backup."
                        } catch (e: Exception) { status = "Restore failed: ${e.message}" }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Restore latest auto-backup") }
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text("Import Google Maps Timeline", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Bootstrap history from a Timeline.json (on-device export) or a Semantic Location History .json (Google Takeout).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { takeout.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import Timeline .json") }
            }
            if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
        }
    }
}
