package com.soumik.stark.ui.speedo

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soumik.stark.core.util.Format
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val START_ANGLE = 140f  // degrees (bottom-left)
private const val SWEEP = 260f

// Deep, near-black backdrop with a faint lift toward the centre — reads as depth, not flat black.
private val BgCenter = Color(0xFF11161C)
private val BgEdge = Color(0xFF05070A)
private val TrackColor = Color(0xFF19212A)

@Composable
fun SpeedoScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val live by TrackingController.state.collectAsStateWithLifecycle()

    // Keep the screen on, go fully immersive (hide status + nav bars for a clean dial), and ask
    // the service for 1 Hz dashboard sampling. Everything is restored when the screen leaves.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        insets?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (com.soumik.stark.ui.theme.ThemeState.sunlight.value) {
            window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        }
        TrackingForegroundService.setDashboard(context, true)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            TrackingForegroundService.setDashboard(context, false)
        }
    }

    val speed = live.speedKmh
    val maxScale = max(80.0, ((speed / 20.0).toInt() + 2) * 20.0)
    // Digit updates instantly (EMA already smooths it); only the analog fill is lightly eased.
    val animated by animateFloatAsState(speed.toFloat(), tween(250), label = "needle")
    val shownSpeed = speed.toInt()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BgCenter, BgEdge), radius = 1400f))
            .padding(20.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Subtle, low-emphasis close affordance so it doesn't compete with the dial.
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Icon(Icons.Filled.Close, "Close", tint = Color.White.copy(alpha = 0.7f))
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.fillMaxWidth(0.92f).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Gauge(animated, maxScale.toFloat(), active = live.tracking)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$shownSpeed",
                        fontSize = 104.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (live.tracking) MaterialTheme.colorScheme.primary else Color(0xFF3A4753),
                    )
                    Text(
                        "km/h",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            StatPill(live)
            if (!live.tracking) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Tracking is off — start it from the Today tab.",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Gauge(speed: Float, maxScale: Float, active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val accentBright = lerp(accent, Color.White, 0.35f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.065f
        val inset = stroke * 1.4f + 4f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        // Recessed track.
        drawArc(
            color = TrackColor,
            startAngle = START_ANGLE,
            sweepAngle = SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val frac = (speed / maxScale).coerceIn(0f, 1f)
        val sweep = SWEEP * frac
        if (active && frac > 0f) {
            // Soft halo under the fill for a premium glow.
            drawArc(
                color = accent.copy(alpha = 0.18f),
                startAngle = START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
            )
            // The fill itself, with a gentle gradient from accent to a brighter tip.
            drawArc(
                brush = Brush.linearGradient(listOf(accent, accentBright)),
                startAngle = START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        val cx = size.width / 2
        val cy = size.height / 2
        val rArc = arcSize.minDimension / 2

        // Bright dot at the needle tip.
        if (active && frac > 0f) {
            val a = Math.toRadians((START_ANGLE + sweep).toDouble())
            val tx = cx + rArc * cos(a).toFloat()
            val ty = cy + rArc * sin(a).toFloat()
            drawCircle(accent.copy(alpha = 0.25f), radius = stroke * 1.1f, center = Offset(tx, ty))
            drawCircle(accentBright, radius = stroke * 0.42f, center = Offset(tx, ty))
        }

        // Tick marks every 20 units.
        val ticks = (maxScale / 20).toInt()
        for (i in 0..ticks) {
            val a = Math.toRadians((START_ANGLE + SWEEP * (i.toFloat() / ticks)).toDouble())
            val x1 = cx + (rArc - stroke * 0.9f) * cos(a).toFloat()
            val y1 = cy + (rArc - stroke * 0.9f) * sin(a).toFloat()
            val x2 = cx + (rArc - stroke * 1.55f) * cos(a).toFloat()
            val y2 = cy + (rArc - stroke * 1.55f) * sin(a).toFloat()
            drawLine(Color(0xFF33404C), Offset(x1, y1), Offset(x2, y2), strokeWidth = 3f)
        }
    }
}

@Composable
private fun StatPill(live: com.soumik.stark.tracking.service.LiveState) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat("TRIP", "${Format.km(live.tripDistanceM)} km")
        Divider()
        Stat("TIME", Format.duration(live.tripDurationS))
        Divider()
        Stat("MAX", "${live.tripMaxSpeedKmh.toInt()} km/h")
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(
        Modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
    }
}
