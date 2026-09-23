package com.soumik.stark.ui.trip_detail

import android.app.Application
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.soumik.stark.core.util.Format
import com.soumik.stark.data.entity.TravelMode
import com.soumik.stark.ui.common.SectionCard
import com.soumik.stark.ui.map.OsmMap
import com.soumik.stark.ui.map.Track
import org.osmdroid.views.overlay.Marker

@Composable
fun TripDetailScreen(legId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val owner = LocalViewModelStoreOwner.current!!
    val vm = remember(legId) {
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    TripDetailViewModel(app, legId) as T
            },
        )["trip_$legId", TripDetailViewModel::class.java]
    }

    val leg by vm.leg.collectAsStateWithLifecycle()
    val geo by vm.geo.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val startName by vm.startPlace.collectAsStateWithLifecycle()
    val endName by vm.endPlace.collectAsStateWithLifecycle()

    var progress by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var speedMult by remember { mutableFloatStateOf(1f) }
    var label by remember(leg?.label) { mutableStateOf(leg?.label ?: "") }

    LaunchedEffect(playing, geo.size, speedMult) {
        while (playing && geo.isNotEmpty()) {
            kotlinx.coroutines.delay((120 / speedMult).toLong().coerceAtLeast(16))
            progress += 1f / geo.size.coerceAtLeast(1)
            if (progress >= 1f) { progress = 1f; playing = false }
        }
    }

    val idx = if (geo.isEmpty()) 0 else (progress * (geo.size - 1)).toInt().coerceIn(0, geo.size - 1)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val curSpeed = points.getOrNull(idx)?.speedMps ?: 0f
            val replayIcon = if (curSpeed < 1f) com.soumik.stark.R.drawable.ic_marker_dot
            else when (leg?.mode) {
                TravelMode.WALK -> com.soumik.stark.R.drawable.ic_dir_walk
                TravelMode.RUN -> com.soumik.stark.R.drawable.ic_dir_run
                else -> com.soumik.stark.R.drawable.ic_dir_bike
            }
            com.soumik.stark.ui.map.MapSurface(
                modifier = Modifier.fillMaxSize(),
                speedTracks = if (geo.size >= 2) listOf(com.soumik.stark.ui.map.SpeedTrack(geo, points.map { it.speedMps * 3.6 })) else emptyList(),
                replayPoint = geo.getOrNull(idx),
                replayIconRes = replayIcon,
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leg?.let { l ->
                Text(
                    "${Format.clock(l.startT, l.offsetMin)} – ${l.endT?.let { Format.clock(it, l.offsetMin) } ?: "…"}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Distance", "${Format.km(l.distanceM)} km")
                    Stat("Duration", Format.duration(l.durationS))
                    Stat("Max", "${Format.kmh(l.maxSpeedMps)} km/h")
                    Stat("Avg", "${if (l.movingDurationS > 0) Math.round(l.distanceM / l.movingDurationS * 3.6) else 0} km/h")
                }
                if (startName != null || endName != null) {
                    Text("${startName ?: "Start"} → ${endName ?: "End"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionCard(Modifier.fillMaxWidth()) {
                    Text("Replay", style = MaterialTheme.typography.titleSmall)
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
                    // Speed legend for the colour-graded route.
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LegendDot(Color(0xFF4CAF50), "<15"); LegendDot(Color(0xFF00E5A8), "30"); LegendDot(Color(0xFFFFD640), "50"); LegendDot(Color(0xFFFF8A00), "70"); LegendDot(Color(0xFFE53935), ">70")
                        Text(" km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    points.getOrNull(idx)?.let {
                        Text("At marker: ${Format.kmh(it.speedMps.toDouble())} km/h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                SectionCard(Modifier.fillMaxWidth()) {
                    Text("Mode", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("Bike", l.mode == TravelMode.VEHICLE) { vm.setMode(TravelMode.VEHICLE) }
                        ModeChip("Walk", l.mode == TravelMode.WALK) { vm.setMode(TravelMode.WALK) }
                        ModeChip("Run", l.mode == TravelMode.RUN) { vm.setMode(TravelMode.RUN) }
                        ModeChip("Cycle", l.mode == TravelMode.BICYCLE) { vm.setMode(TravelMode.BICYCLE) }
                    }
                }

                SectionCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(label, { label = it }, label = { Text("Trip label") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.setLabel(label) }) { Text("Save label") }
                }

                SectionCard(Modifier.fillMaxWidth()) {
                    Text("Edit", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.mergePrevious() }) { Text("Merge w/ previous") }
                        OutlinedButton(onClick = { points.getOrNull(idx)?.let { vm.splitAt(it.id) } }) { Text("Split here") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.share(context) }) { Text("Share as GPX") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.delete(onBack) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete trip") }
                }
            }
            Spacer(Modifier.height(24.dp))
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
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) })
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
