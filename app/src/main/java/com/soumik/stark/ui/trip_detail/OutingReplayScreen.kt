package com.soumik.stark.ui.trip_detail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.soumik.stark.core.util.Format
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.map.MapSurface
import com.soumik.stark.ui.map.SpeedTrack

@Composable
fun OutingReplayScreen(outingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val owner = LocalViewModelStoreOwner.current!!
    val vm = remember(outingId) {
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    OutingReplayViewModel(app, outingId) as T
            },
        )["outing_$outingId", OutingReplayViewModel::class.java]
    }

    val s by vm.state.collectAsStateWithLifecycle()

    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var speedMult by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(playing, s.geoAll.size, speedMult) {
        while (playing && s.geoAll.isNotEmpty()) {
            kotlinx.coroutines.delay((120 / speedMult).toLong().coerceAtLeast(16))
            progress += 1f / s.geoAll.size.coerceAtLeast(1)
            if (progress >= 1f) { progress = 1f; playing = false }
        }
    }

    val idx = if (s.geoAll.isEmpty()) 0 else (progress * (s.geoAll.size - 1)).toInt().coerceIn(0, s.geoAll.size - 1)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val curSpeed = s.speedsAll.getOrNull(idx) ?: 0f
            val replayIcon = if (curSpeed < 1f) com.soumik.stark.R.drawable.ic_marker_dot
            else com.soumik.stark.R.drawable.ic_dir_bike
            MapSurface(
                modifier = Modifier.fillMaxSize(),
                speedTracks = s.perLegTracks,
                replayPoint = s.geoAll.getOrNull(idx),
                replayIconRes = replayIcon,
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Distance", "${Format.km(s.distanceM)} km")
                Stat("Duration", Format.duration(s.durationS))
                Stat("Max", "${Format.kmh(s.maxSpeedMps)} km/h")
                Stat("Trips", "${s.tripCount}")
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Text("Replay whole outing", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (progress >= 1f) progress = 0f; playing = !playing }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
                    }
                    Slider(progress, { progress = it; playing = false }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 1f, 2f, 4f).forEach { m ->
                        FilterChip(speedMult == m, { speedMult = m }, label = { Text(if (m == 0.5f) "0.5x" else "${m.toInt()}x") })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LegendDot(Color(0xFF4CAF50), "<15"); LegendDot(Color(0xFF00E5A8), "30"); LegendDot(Color(0xFFFFD640), "50"); LegendDot(Color(0xFFFF8A00), "70"); LegendDot(Color(0xFFE53935), ">70")
                    Text(" km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.speedsAll.getOrNull(idx)?.let {
                    Text("At marker: ${Format.kmh(it.toDouble())} km/h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
