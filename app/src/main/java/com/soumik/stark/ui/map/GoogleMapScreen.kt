package com.soumik.stark.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline

/**
 * Optional Google Maps rendering (design §6.6), used only when the build carries a user-supplied
 * key (BuildConfig.HAS_GOOGLE_MAPS). Default builds use [MapScreen] (OSM). Same data source.
 */
@Composable
fun GoogleMapScreen(vm: MapViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize()) {
            tracks.forEach { t ->
                Polyline(
                    points = t.points.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFF00E5A8),
                    width = 10f,
                )
            }
        }
    }
}
