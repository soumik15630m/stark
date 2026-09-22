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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soumik.stark.core.util.Format
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val START_ANGLE = 140f  // degrees (bottom-left)
private const val SWEEP = 260f

@Composable
fun SpeedoScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val live by TrackingController.state.collectAsStateWithLifecycle()

    // Keep the screen on while mounted, and ask the service for 1 Hz dashboard sampling.
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
    val animated by animateFloatAsState(speed.toFloat(), tween(400), label = "needle")

    Box(
        Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, "Close", tint = Color.White)
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Gauge(animated, maxScale.toFloat())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${speed.toInt()}",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (live.tracking) MaterialTheme.colorScheme.primary else Color.DarkGray,
                    )
                    Text("km/h", fontSize = 20.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("TRIP", "${Format.km(live.tripDistanceM)} km")
                Stat("TIME", Format.duration(live.tripDurationS))
                Stat("MAX", "${live.tripMaxSpeedKmh.toInt()} km/h")
            }
            if (!live.tracking) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Tracking is off — start it from the Today tab.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Gauge(speed: Float, maxScale: Float) {
    val accent = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.07f
        val inset = stroke / 2 + 4f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Color(0xFF1E2731),
            startAngle = START_ANGLE,
            sweepAngle = SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val frac = (speed / maxScale).coerceIn(0f, 1f)
        drawArc(
            brush = Brush.sweepGradient(listOf(accent, accent)),
            startAngle = START_ANGLE,
            sweepAngle = SWEEP * frac,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Tick marks every 20 units.
        val cx = size.width / 2
        val cy = size.height / 2
        val rOuter = arcSize.minDimension / 2
        val ticks = (maxScale / 20).toInt()
        for (i in 0..ticks) {
            val a = Math.toRadians((START_ANGLE + SWEEP * (i.toFloat() / ticks)).toDouble())
            val x1 = cx + (rOuter - stroke) * cos(a).toFloat()
            val y1 = cy + (rOuter - stroke) * sin(a).toFloat()
            val x2 = cx + (rOuter - stroke * 1.7f) * cos(a).toFloat()
            val y2 = cy + (rOuter - stroke * 1.7f) * sin(a).toFloat()
            drawLine(Color(0xFF3A4753), Offset(x1, y1), Offset(x2, y2), strokeWidth = 3f)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}
