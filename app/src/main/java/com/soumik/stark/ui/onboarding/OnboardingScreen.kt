package com.soumik.stark.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.soumik.stark.core.security.AppLock
import com.soumik.stark.core.util.Geo
import com.soumik.stark.core.util.GeoCell
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.data.entity.Place
import com.soumik.stark.data.entity.PlaceCategory
import com.soumik.stark.data.repo.TrackRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.soumik.stark.ui.common.SectionCard

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var biometric by remember { mutableStateOf(false) }
    var homeSet by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Stark", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Set-up • step ${step + 1} of 5", color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (step) {
            0 -> SectionCard(Modifier.fillMaxWidth()) {
                Text("Your private odometer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A trustworthy speedometer and lifetime odometer, plus a detailed offline ride timeline. Everything stays on this phone. Let's set it up.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            1 -> SectionCard(Modifier.fillMaxWidth()) {
                Text("Set an app PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Locks the app. Stored only as a hash — the PIN never leaves the device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN (4–6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pin2, { if (it.length <= 6 && it.all(Char::isDigit)) pin2 = it }, label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(biometric, { biometric = it })
                    Text("  Also unlock with biometrics", color = MaterialTheme.colorScheme.onSurface)
                }
                if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
            }
            2 -> SectionCard(Modifier.fillMaxWidth()) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Location captures your rides. Notifications show the live odometer. Activity recognition helps detect when you start moving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACTIVITY_RECOGNITION
                    permLauncher.launch(perms.toTypedArray())
                }, modifier = Modifier.fillMaxWidth()) { Text("Grant location & notifications") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }, modifier = Modifier.fillMaxWidth()) { Text("Allow background location (\"all the time\")") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }, modifier = Modifier.fillMaxWidth()) { Text("Battery optimization → don't optimize") }
            }
            3 -> SectionCard(Modifier.fillMaxWidth()) {
                Text("Pin Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Your base for the 'back home' ride summary. You can change it later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { pinHome(context, scope) { homeSet = true; msg = "Home pinned" } }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (homeSet) "Home pinned ✓" else "Use current location as Home")
                }
                if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            4 -> SectionCard(Modifier.fillMaxWidth()) {
                Text("You're set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Take your first ride — your odometer starts here. Open the Today tab and tap Start tracking, or just ride; you can start it anytime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Spacer(Modifier.height(1.dp))
            Button(onClick = {
                when (step) {
                    1 -> {
                        if (pin.length < 4 || pin != pin2) { msg = "PINs must match and be 4–6 digits"; return@Button }
                        msg = "Securing…"
                        val p = pin
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                AppLock.setPin(context, p)
                            }
                            AppLock.setBiometric(context, biometric)
                            msg = ""
                            step++
                        }
                    }
                    4 -> {
                        Prefs.setBool(context, Prefs.KEY_ONBOARDED, true)
                        onDone()
                    }
                    else -> step++
                }
            }) { Text(if (step == 4) "Finish" else "Next") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun pinHome(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasPerm) return
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation.addOnSuccessListener { loc ->
        if (loc == null) return@addOnSuccessListener
        scope.launch {
            val repo = TrackRepository.get(context)
            val existing = repo.placeDao.home()
            val place = Place(
                latE7 = Geo.toE7(loc.latitude), lngE7 = Geo.toE7(loc.longitude),
                geocell = GeoCell.placeCell(loc.latitude, loc.longitude),
                name = "Home", category = PlaceCategory.HOME, isBaseHome = true,
                firstSeen = System.currentTimeMillis(), lastSeen = System.currentTimeMillis(), visitCount = 1,
            )
            if (existing != null) repo.placeDao.update(place.copy(id = existing.id)) else repo.placeDao.insert(place)
            onDone()
        }
    }
}
