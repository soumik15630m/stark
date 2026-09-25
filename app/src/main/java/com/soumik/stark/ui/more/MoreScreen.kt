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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soumik.stark.BuildConfig
import com.soumik.stark.core.security.AppLock
import com.soumik.stark.data.repo.TrackRepository
import com.soumik.stark.ui.common.NavRow
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.common.SectionLabel
import com.soumik.stark.ui.common.SettingToggleRow
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
    val night by ThemeState.nightRide.collectAsStateWithLifecycle()
    val dynamic by ThemeState.dynamicColor.collectAsStateWithLifecycle()
    val reduce by ThemeState.reduceMotion.collectAsStateWithLifecycle()
    val sunlight by ThemeState.sunlight.collectAsStateWithLifecycle()
    var biometric by remember { mutableStateOf(AppLock.biometricEnabled(context)) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        SectionLabel("Tools")
        SectionCard(Modifier.fillMaxWidth()) {
            NavRow(Icons.Filled.Search, "Search rides", onClick = onOpenSearch)
            NavRow(Icons.Filled.LocalGasStation, "Fuel & mileage", "Tank, range and cost per km", onClick = onOpenFuel)
            NavRow(Icons.Filled.Place, "Places", onClick = onOpenPlaces)
            NavRow(Icons.Filled.Backup, "Backup & restore", "Encrypted backup and Timeline import", onClick = onOpenBackup)
            NavRow(Icons.Filled.Bolt, "Automation & network", onClick = onOpenAutomation)
        }

        SectionLabel("Tracking")
        SectionCard(Modifier.fillMaxWidth()) {
            var auto by remember { mutableStateOf(com.soumik.stark.core.util.Prefs.getBool(context, com.soumik.stark.core.util.Prefs.KEY_AUTO_TRACK, false)) }
            SettingToggleRow(
                Icons.Filled.Sensors, "Start rides automatically",
                "Begins tracking when a ride starts, sipping battery while idle.", auto,
            ) {
                auto = it
                com.soumik.stark.core.util.Prefs.setBool(context, com.soumik.stark.core.util.Prefs.KEY_AUTO_TRACK, it)
                if (it) com.soumik.stark.tracking.gating.MotionGate.arm(context)
                else com.soumik.stark.tracking.gating.MotionGate.disarm(context)
            }
            NavRow(Icons.Filled.BatteryChargingFull, "Battery & background", "Keep tracking running reliably", onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            })
            NavRow(Icons.Filled.Settings, "App info & permissions", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
            })
        }

        SectionLabel("Appearance")
        SectionCard(Modifier.fillMaxWidth()) {
            SettingToggleRow(Icons.Filled.DarkMode, "Night-riding mode", "Deep red palette for night", night) { ThemeState.setNight(context, it) }
            SettingToggleRow(Icons.Filled.Palette, "Dynamic colour", "Match your wallpaper", dynamic) { ThemeState.setDynamic(context, it) }
            SettingToggleRow(Icons.Filled.Animation, "Reduce motion", checked = reduce) { ThemeState.setReduceMotion(context, it) }
            SettingToggleRow(Icons.Filled.WbSunny, "Sunlight mode", "High contrast for bright light", sunlight) { ThemeState.setSunlight(context, it) }
        }

        SectionLabel("Security")
        SectionCard(Modifier.fillMaxWidth()) {
            SettingToggleRow(Icons.Filled.Fingerprint, "Unlock with fingerprint", checked = biometric) { AppLock.setBiometric(context, it); biometric = it }
            Text(
                "Your rides are encrypted on your device and locked behind your PIN and fingerprint.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            var decoyPin by remember { mutableStateOf("") }
            var decoyMsg by remember { mutableStateOf("") }
            OutlinedTextField(
                decoyPin, { if (it.length <= 6 && it.all(Char::isDigit)) decoyPin = it },
                label = { Text("Duress PIN") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Entering this PIN opens a separate, empty space instead of your real data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = {
                if (decoyPin.length >= 4) { AppLock.setDecoyPin(context, decoyPin); decoyPin = ""; decoyMsg = "Duress PIN set." }
                else decoyMsg = "Use 4–6 digits."
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Set duress PIN") }
            if (decoyMsg.isNotEmpty()) Text(decoyMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

            var confirmWipe by remember { mutableStateOf(false) }
            NavRow(Icons.Filled.DeleteForever, "Erase all data", tint = MaterialTheme.colorScheme.error) { confirmWipe = true }
            if (confirmWipe) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmWipe = false },
                    title = { Text("Erase everything?") },
                    text = { Text("This permanently deletes all your rides, places and settings. It can't be undone.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmWipe = false; wipeAll(context) }) { Text("Erase", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
                )
            }
        }

        SectionLabel("Privacy")
        SectionCard(Modifier.fillMaxWidth()) {
            NavRow(Icons.Filled.TwoWheeler, "Find my bike", "Navigate to where you last parked", onClick = { findMyBike(context) })
            NavRow(Icons.Filled.Share, "Share my location", onClick = { shareCurrentLocation(context) })
            NavRow(Icons.Filled.Shield, "Add a privacy zone here", "Hidden from shared exports", onClick = { addPrivacyZoneHere(context) })
        }

        SectionLabel("About")
        SectionCard(Modifier.fillMaxWidth()) {
            Text("Stark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Private, offline ride tracking and odometer for your motorbike.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
