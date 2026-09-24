package com.soumik.stark.ui.dashboard

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.tracking.service.TrackingForegroundService
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val START_ANGLE = 140f
private const val SWEEP = 260f

private val BgCenter = Color(0xFF11161C)
private val BgEdge = Color(0xFF05070A)
private val TrackColor = Color(0xFF19212A)

@Composable
fun QuickDashboard(onUnlockFull: () -> Unit, vm: QuickDashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val lifetime by vm.lifetime.collectAsStateWithLifecycle()

    // Keep on + immersive: this is the bike-mount view, so hide the status/nav bars too.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        insets?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        TrackingForegroundService.setDashboard(context, true)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
            TrackingForegroundService.setDashboard(context, false)
        }
    }

    val speed = live.speedKmh
    val maxScale = max(80.0, ((speed / 20.0).toInt() + 2) * 20.0)
    val animated by animateFloatAsState(speed.toFloat(), tween(250), label = "needle")
    val shownSpeed = speed.toInt()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(BgCenter, BgEdge), radius = 1400f))
            .padding(20.dp),
    ) {
        IconButton(
            onClick = onUnlockFull,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Icon(Icons.Filled.Lock, "Unlock full app", tint = Color.White.copy(alpha = 0.7f))
        }
        Text(
            "STARK",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(0.92f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Gauge(animated, maxScale.toFloat(), MaterialTheme.colorScheme.primary, active = live.tracking)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shownSpeed", fontSize = 104.sp, fontWeight = FontWeight.Bold, color = if (live.tracking) MaterialTheme.colorScheme.primary else Color(0xFF3A4753))
                    Text("km/h", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat("TODAY", "${Format.km(today?.distanceBikeM ?: 0.0)} km")
                Divider()
                Stat("TRIP", "${Format.km(live.tripDistanceM)} km")
                Divider()
                Stat("LIFETIME", "${Format.km(lifetime?.distanceBikeM ?: 0.0)} km")
            }
            Spacer(Modifier.height(28.dp))
            val active = live.state == com.soumik.stark.tracking.service.TrackState.ACTIVE
            val paused = live.state == com.soumik.stark.tracking.service.TrackState.PAUSED
            Button(
                onClick = {
                    when {
                        active -> TrackingForegroundService.pause(context)
                        paused -> TrackingForegroundService.resume(context)
                        else -> TrackingForegroundService.start(context)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
            ) {
                Icon(if (active) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                Text(if (active) "  Pause" else if (paused) "  Resume" else "  Start", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Gauge(speed: Float, maxScale: Float, accent: Color, active: Boolean) {
    val accentBright = lerp(accent, Color.White, 0.35f)
    Canvas(Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.065f
        val inset = stroke * 1.4f + 4f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(TrackColor, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val frac = (speed / maxScale).coerceIn(0f, 1f)
        val sweep = SWEEP * frac
        if (active && frac > 0f) {
            drawArc(accent.copy(alpha = 0.18f), START_ANGLE, sweep, false, topLeft, arcSize, style = Stroke(stroke * 2.1f, cap = StrokeCap.Round))
            drawArc(Brush.linearGradient(listOf(accent, accentBright)), START_ANGLE, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val cx = size.width / 2
            val cy = size.height / 2
            val rArc = arcSize.minDimension / 2
            val a = Math.toRadians((START_ANGLE + sweep).toDouble())
            val tx = cx + rArc * cos(a).toFloat()
            val ty = cy + rArc * sin(a).toFloat()
            drawCircle(accent.copy(alpha = 0.25f), radius = stroke * 1.1f, center = Offset(tx, ty))
            drawCircle(accentBright, radius = stroke * 0.42f, center = Offset(tx, ty))
        }
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
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
    }
}
