package com.soumik.stark.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Optional Google Maps rendering (design §6.6), used only when the build carries a user-supplied
 * key (BuildConfig.HAS_GOOGLE_MAPS). Default builds use [MapScreen] (OSM). Same data source.
 */
@Composable
fun GoogleMapScreen(vm: MapViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val heat by vm.heat.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var showHeat by remember { mutableStateOf(false) }
    val cam = rememberCameraPositionState()
    val maxWeight = remember(heat) { (heat.maxOfOrNull { it.weight } ?: 1).coerceAtLeast(1) }

    // Fit the camera to the routes once they load, so it never sits on the world map.
    LaunchedEffect(tracks.size) {
        val all = tracks.flatMap { it.points }.map { LatLng(it.latitude, it.longitude) }
        if (all.isEmpty()) return@LaunchedEffect
        try {
            if (all.size == 1) cam.position = CameraPosition.fromLatLngZoom(all.first(), 15f)
            else {
                val b = LatLngBounds.builder().apply { all.forEach { include(it) } }.build()
                cam.move(CameraUpdateFactory.newLatLngBounds(b, 100))
            }
        } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cam) {
            if (!showHeat) {
                tracks.forEach { t ->
                    if (t.points.size >= 2) Polyline(points = t.points.map { LatLng(it.latitude, it.longitude) }, color = Color(0xFF00E5A8), width = 10f)
                }
            } else {
                heat.forEach { dot ->
                    val alpha = (60 + 195 * dot.weight / maxWeight).coerceIn(60, 255)
                    Circle(center = LatLng(dot.point.latitude, dot.point.longitude), radius = 40.0,
                        fillColor = Color(AndroidColor.argb(alpha, 255, 90, 40)), strokeColor = Color.Transparent, strokeWidth = 0f)
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showHeat, { showHeat = false }, label = { Text("Routes") })
            FilterChip(showHeat, { showHeat = true }, label = { Text("Heatmap") })
        }
        if (loading) Text("Loading map…", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (tracks.isEmpty()) Text("Ride a bit — your routes will appear here.", Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
