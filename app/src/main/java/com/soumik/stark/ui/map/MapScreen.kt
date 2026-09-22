package com.soumik.stark.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.views.overlay.Polygon

@Composable
fun MapScreen(vm: MapViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val heat by vm.heat.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var showHeat by remember { mutableStateOf(false) }

    val maxWeight = remember(heat) { (heat.maxOfOrNull { it.weight } ?: 1).coerceAtLeast(1) }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            tracks = if (showHeat) emptyList() else tracks,
            configure = { map ->
                map.overlays.removeAll { it is Polygon }
                if (showHeat) {
                    heat.forEach { dot ->
                        val alpha = (60 + 195 * dot.weight / maxWeight).coerceIn(60, 255)
                        val poly = Polygon(map).apply {
                            points = Polygon.pointsAsCircle(dot.point, 40.0)
                            fillPaint.color = AndroidColor.argb(alpha, 255, 90, 40)
                            outlinePaint.color = AndroidColor.argb(0, 0, 0, 0)
                        }
                        map.overlays.add(poly)
                    }
                }
            },
        )

        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(!showHeat, { showHeat = false }, label = { Text("Routes") })
            FilterChip(showHeat, { showHeat = true }, label = { Text("Heatmap") })
        }

        if (loading) {
            Text("Loading map…", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (tracks.isEmpty()) {
            Text("Ride a bit — your routes will appear here.", Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
