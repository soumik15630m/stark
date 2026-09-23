package com.soumik.stark.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Accent = Color(0xFF00E5A8)
val AccentDim = Color(0xFF00B486)
val TrueBlack = Color(0xFF000000)
val Surface = Color(0xFF10151B)
val SurfaceHi = Color(0xFF1A2129)
val NightRed = Color(0xFFFF4D4D)

private val StarkDark = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = AccentDim,
    background = TrueBlack,
    onBackground = Color(0xFFE6EAF0),
    surface = Surface,
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = Color(0xFF9BA6B2),
)

private val StarkLight = lightColorScheme(
    primary = AccentDim,
    onPrimary = Color.White,
    background = Color(0xFFF7F9FB),
    surface = Color.White,
)

private val StarkSunlight = darkColorScheme(
    primary = Color(0xFF00FFB2),
    onPrimary = Color.Black,
    secondary = Color(0xFF00FFB2),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = Color(0xFFECECEC),
)

@Composable
fun StarkTheme(
    nightRide: Boolean = false,
    dynamicColor: Boolean = true,
    sunlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        sunlight -> StarkSunlight       // max-contrast for direct sun
        nightRide -> StarkDark.copy(primary = NightRed, secondary = NightRed)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> StarkDark
        else -> StarkLight
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
