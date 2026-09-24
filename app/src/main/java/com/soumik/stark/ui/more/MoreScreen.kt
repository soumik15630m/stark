package com.soumik.stark.ui.more

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.soumik.stark.core.security.AppLock
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.common.LabeledRow
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.theme.ThemeState

@Composable
fun MoreScreen(
    onOpenFuel: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenPlaces: () -> Unit = {},
    onOpenAutomation: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    var pointCount by remember { mutableStateOf(0) }
    val night by ThemeState.nightRide.collectAsStateWithLifecycle()
    val dynamic by ThemeState.dynamicColor.collectAsStateWithLifecycle()
    var biometric by remember { mutableStateOf(AppLock.biometricEnabled(context)) }

    LaunchedEffect(Unit) {
        pointCount = TrackRepository.get(context).pointCount()
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LabeledRow("GPS points stored", pointCount.toString())
            LabeledRow("Sampling", "hybrid ~20 m / 3 s (1 s on dashboard)")
            LabeledRow("Distance", "Haversine sum, incremental")
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("More", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) { Text("Search trips") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onOpenFuel, modifier = Modifier.fillMaxWidth()) { Text("Fuel & mileage") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onOpenPlaces, modifier = Modifier.fillMaxWidth()) { Text("Places") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup, restore & import") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onOpenAutomation, modifier = Modifier.fillMaxWidth()) { Text("Automation & network") }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Night-riding (red) mode", night) { ThemeState.setNight(context, it) }
            ToggleRow("Material You dynamic color", dynamic) { ThemeState.setDynamic(context, it) }
            val reduce by ThemeState.reduceMotion.collectAsStateWithLifecycle()
            ToggleRow("Reduce motion", reduce) { ThemeState.setReduceMotion(context, it) }
            val sunlight by ThemeState.sunlight.collectAsStateWithLifecycle()
            ToggleRow("High-contrast sunlight mode", sunlight) { ThemeState.setSunlight(context, it) }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ToggleRow("Unlock with biometrics", biometric) { AppLock.setBiometric(context, it); biometric = it }
            Text(
                "Data is stored in an encrypted (SQLCipher) database with the key in the hardware Keystore. App is PIN-locked with FLAG_SECURE and auto-locks after 2 minutes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            var decoyPin by remember { mutableStateOf("") }
            var decoyMsg by remember { mutableStateOf("") }
            OutlinedTextField(
                decoyPin, { if (it.length <= 6 && it.all(Char::isDigit)) decoyPin = it },
                label = { Text("Decoy (duress) PIN") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                if (decoyPin.length >= 4) { AppLock.setDecoyPin(context, decoyPin); decoyPin = ""; decoyMsg = "Decoy PIN set — it opens a fake, synthetic dataset." }
                else decoyMsg = "Use 4–6 digits."
            }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Set decoy PIN") }
            if (decoyMsg.isNotEmpty()) Text(decoyMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            var confirmWipe by remember { mutableStateOf(false) }
            Button(
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Wipe all data (crypto-erase)") }
            if (confirmWipe) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmWipe = false },
                    title = { Text("Erase everything?") },
                    text = { Text("This destroys the encryption key and deletes all trips, places and settings. It cannot be undone.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmWipe = false; wipeAll(context) }) { Text("Erase", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
                )
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Auto-tracking", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            var auto by remember { mutableStateOf(com.soumik.stark.core.util.Prefs.getBool(context, com.soumik.stark.core.util.Prefs.KEY_AUTO_TRACK, false)) }
            ToggleRow("Start tracking automatically when I move", auto) {
                auto = it
                com.soumik.stark.core.util.Prefs.setBool(context, com.soumik.stark.core.util.Prefs.KEY_AUTO_TRACK, it)
                if (it) com.soumik.stark.tracking.gating.MotionGate.arm(context)
                else com.soumik.stark.tracking.gating.MotionGate.disarm(context)
            }
            Text(
                "Uses Activity Recognition + the significant-motion sensor to detect a ride start and spin up tracking on its own — near-zero battery while idle.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Keep tracking alive", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android aggressively kills background apps. Exempt Stark from battery optimization and, on Xiaomi/Realme/Oppo/Samsung/OnePlus, enable Autostart and lock it in recents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Battery optimization settings") }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("App info & permissions") }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("Privacy & sharing", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { findMyBike(context) }, modifier = Modifier.fillMaxWidth()) { Text("Find my bike (last parking)") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { shareCurrentLocation(context) }, modifier = Modifier.fillMaxWidth()) { Text("Share my current location") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { addPrivacyZoneHere(context) }, modifier = Modifier.fillMaxWidth()) { Text("Add privacy zone here") }
            Text("Privacy zones are clipped out of shared GPX exports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LabeledRow("App", "Stark")
            LabeledRow("Build", "v1.0.1 — full v1 + Google Maps (M1–M6)")
            Text(
                "Encrypted tracking with 2D Kalman fusion, timeline, maps, stats, fuel, backup, and self-update. Data at rest is SQLCipher-encrypted with a Keystore-held key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@SuppressLint("MissingPermission")
private fun shareCurrentLocation(context: android.content.Context) {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        .lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener
            val uri = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I'm here: $uri")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(send, "Share location").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

@SuppressLint("MissingPermission")
private fun addPrivacyZoneHere(context: android.content.Context) {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        .lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                TrackRepository.get(context).privacyDao.insert(
                    com.soumik.stark.data.entity.PrivacyZone(
                        latE7 = com.soumik.stark.core.util.Geo.toE7(loc.latitude),
                        lngE7 = com.soumik.stark.core.util.Geo.toE7(loc.longitude),
                        radiusM = 150, label = "Zone",
                    )
                )
            }
        }
}

private fun findMyBike(context: android.content.Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val repo = TrackRepository.get(context)
        val lastLeg = repo.legDao.allClosed().firstOrNull()
        val pt = lastLeg?.let { repo.pointsForLeg(it.id).lastOrNull() } ?: return@launch
        val lat = com.soumik.stark.core.util.Geo.fromE7(pt.latE7)
        val lng = com.soumik.stark.core.util.Geo.fromE7(pt.lngE7)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
            .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Your+bike)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun wipeAll(context: android.content.Context) {
    com.soumik.stark.tracking.service.TrackingForegroundService.disable(context)
    com.soumik.stark.core.crypto.DbKeys.wipe(context)
    com.soumik.stark.data.repo.TrackRepository.reset(context)
    context.getSharedPreferences("stark_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("stark_lock", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("stark_telemetry", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    (context as? android.app.Activity)?.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked, onChange)
    }
}
