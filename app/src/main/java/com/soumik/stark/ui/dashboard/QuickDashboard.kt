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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soumik.stark.core.util.Format
import com.soumik.stark.tracking.service.TrackingForegroundService
import kotlin.math.max

private const val START_ANGLE = 140f
private const val SWEEP = 260f

@Composable
fun QuickDashboard(onUnlockFull: () -> Unit, vm: QuickDashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val lifetime by vm.lifetime.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        TrackingForegroundService.setDashboard(context, true)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            TrackingForegroundService.setDashboard(context, false)
        }
    }

    val speed = live.speedKmh
    val maxScale = max(80.0, ((speed / 20.0).toInt() + 2) * 20.0)
    val animated by animateFloatAsState(speed.toFloat(), tween(250), label = "needle")
    val shownSpeed = speed.toInt()

    Box(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        IconButton(onClick = onUnlockFull, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Lock, "Unlock full app", tint = MaterialTheme.colorScheme.primary)
        }
        Text("STARK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(0.9f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Gauge(animated, maxScale.toFloat(), MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shownSpeed", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = if (live.tracking) MaterialTheme.colorScheme.primary else Color.DarkGray)
                    Text("km/h", fontSize = 20.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("TODAY", "${Format.km(today?.distanceBikeM ?: 0.0)} km")
                Stat("TRIP", "${Format.km(live.tripDistanceM)} km")
                Stat("LIFETIME", "${Format.km(lifetime?.distanceBikeM ?: 0.0)} km")
            }
            Spacer(Modifier.height(24.dp))
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
private fun Gauge(speed: Float, maxScale: Float, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.07f
        val inset = stroke / 2 + 4f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(Color(0xFF1E2731), START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val frac = (speed / maxScale).coerceIn(0f, 1f)
        drawArc(accent, START_ANGLE, SWEEP * frac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}
