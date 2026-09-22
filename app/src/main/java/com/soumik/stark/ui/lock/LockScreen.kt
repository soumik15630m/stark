package com.soumik.stark.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.soumik.stark.core.security.AppLock
import com.soumik.stark.core.security.SessionLock

@Composable
fun LockScreen(onUnlocked: (decoy: Boolean) -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val biometricAvailable = remember {
        AppLock.biometricEnabled(context) &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // PIN length is 4–6. Verify on each keypress once at least 4 digits are in; only flag a wrong
    // PIN (and reset) at the 6-digit ceiling, so 4- and 5-digit PINs still unlock.
    fun onDigit(d: String) {
        if (pin.length >= 6) return
        error = false
        val next = pin + d
        pin = next
        if (next.length >= 4) {
            when (AppLock.verify(context, next)) {
                AppLock.Result.REAL -> onUnlocked(false)
                AppLock.Result.DECOY -> onUnlocked(true)
                AppLock.Result.WRONG -> if (next.length == 6) { error = true; pin = "" }
            }
        }
    }

    fun launchBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked(false)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Stark")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Stark", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (error) "Wrong PIN" else "Enter PIN",
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < pin.length) MaterialTheme.colorScheme.primary else Color(0xFF2A343E))
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { d -> Key(d) { onDigit(d) } }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), Alignment.Center) {
                    if (biometricAvailable) {
                        Icon(
                            Icons.Filled.Fingerprint, "Biometric",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp).clickable { launchBiometric() },
                        )
                    }
                }
                Key("0") { onDigit("0") }
                Box(Modifier.size(64.dp), Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace, "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Key(digit: String, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape)
            .border(1.dp, Color(0xFF2A343E), CircleShape)
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Text(digit, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
