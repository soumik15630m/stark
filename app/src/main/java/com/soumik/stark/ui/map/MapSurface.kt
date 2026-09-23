package com.soumik.stark.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.soumik.stark.BuildConfig
import org.osmdroid.util.GeoPoint

/**
 * Single map entry point. Uses the Google Maps SDK when the build carries a key
 * (BuildConfig.HAS_GOOGLE_MAPS, from a gitignored secrets.properties — design §6.6), else the
 * keyless OpenStreetMap renderer. All screens draw through this so the provider swaps in one place.
 */
@Composable
fun MapSurface(
    modifier: Modifier = Modifier,
    tracks: List<Track> = emptyList(),
    speedTracks: List<SpeedTrack> = emptyList(),
    stops: List<StopPin> = emptyList(),
    replayPoint: GeoPoint? = null,
    fitToTracks: Boolean = true,
) {
    if (BuildConfig.HAS_GOOGLE_MAPS) {
        GoogleMapSurface(modifier, tracks, speedTracks, stops, replayPoint, fitToTracks)
    } else {
        OsmMap(
            modifier = modifier,
            tracks = tracks,
            speedTracks = speedTracks,
            stops = stops,
            fitToTracks = fitToTracks,
            configure = { map ->
                if (replayPoint != null) {
                    map.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }
                    map.overlays.add(org.osmdroid.views.overlay.Marker(map).apply {
                        position = replayPoint
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                        icon = androidx.core.content.ContextCompat.getDrawable(map.context, com.soumik.stark.R.drawable.ic_marker_dot)
                    })
                }
            },
        )
    }
}

@Composable
private fun GoogleMapSurface(
    modifier: Modifier,
    tracks: List<Track>,
    speedTracks: List<SpeedTrack>,
    stops: List<StopPin>,
    replayPoint: GeoPoint?,
    fitToTracks: Boolean,
) {
    val cam = rememberCameraPositionState()
    val all = ArrayList<LatLng>()
    tracks.forEach { t -> t.points.forEach { all.add(LatLng(it.latitude, it.longitude)) } }
    speedTracks.forEach { st -> st.points.forEach { all.add(LatLng(it.latitude, it.longitude)) } }

    LaunchedEffect(all.size) {
        if (fitToTracks && all.isNotEmpty()) {
            try {
                if (all.size == 1) {
                    cam.position = CameraPosition.fromLatLngZoom(all.first(), 15f)
                } else {
                    val b = LatLngBounds.builder().apply { all.forEach { include(it) } }.build()
                    cam.move(CameraUpdateFactory.newLatLngBounds(b, 80))
                }
            } catch (_: Exception) {}
        }
    }

    GoogleMap(modifier = modifier, cameraPositionState = cam) {
        tracks.forEach { t ->
            if (t.points.size >= 2) Polyline(points = t.points.map { LatLng(it.latitude, it.longitude) }, color = Color(0xFF00E5A8), width = t.width)
        }
        speedTracks.forEach { st ->
            for (i in 1 until st.points.size) {
                Polyline(
                    points = listOf(LatLng(st.points[i - 1].latitude, st.points[i - 1].longitude), LatLng(st.points[i].latitude, st.points[i].longitude)),
                    color = Color(speedColor(st.speedsKmh.getOrElse(i) { 0.0 })),
                    width = 9f,
                )
            }
        }
        stops.forEach { s ->
            Marker(state = MarkerState(position = LatLng(s.point.latitude, s.point.longitude)), title = s.label)
        }
        replayPoint?.let {
            Marker(state = MarkerState(position = LatLng(it.latitude, it.longitude)))
        }
    }
}
