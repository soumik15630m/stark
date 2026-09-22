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
            Button(onClick = { shareCurrentLocation(context) }, modifier = Modifier.fillMaxWidth()) { Text("Share my current location") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { addPrivacyZoneHere(context) }, modifier = Modifier.fillMaxWidth()) { Text("Add privacy zone here") }
            Text("Privacy zones are clipped out of shared GPX exports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LabeledRow("App", "Stark")
            LabeledRow("Build", "v0.1.0 — M1 field test")
            Text(
                "This build is the trustworthy-odometer core: live tracking, incremental distance, timeline and speedometer. Encryption, maps, stats, backup and OTA are the next milestones.",
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

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked, onChange)
    }
}
