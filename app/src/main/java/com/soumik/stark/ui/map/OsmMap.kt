package com.soumik.stark.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/** A GeoPoint track plus a colour, drawn as one polyline. */
data class Track(val points: List<GeoPoint>, val color: Int = AndroidColor.rgb(0, 229, 168), val width: Float = 8f, val dashed: Boolean = false)

/**
 * Reusable osmdroid map. Keyless OSM tiles (design §3.2). [configure] runs on each recomposition
 * with the live MapView so callers can add overlays/markers on top of the drawn tracks.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    tracks: List<Track> = emptyList(),
    fitToTracks: Boolean = true,
    initialZoom: Double = 15.0,
    configure: (MapView) -> Unit = {},
) {
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.removeAll { it is Polyline }
            val all = ArrayList<GeoPoint>()
            tracks.forEach { t ->
                if (t.points.isEmpty()) return@forEach
                val line = Polyline(map).apply {
                    setPoints(t.points)
                    outlinePaint.color = t.color
                    outlinePaint.strokeWidth = t.width
                    if (t.dashed) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                }
                map.overlays.add(line)
                all.addAll(t.points)
            }
            configure(map)
            if (fitToTracks && all.isNotEmpty()) {
                map.post {
                    try {
                        if (all.size == 1) {
                            map.controller.setZoom(initialZoom)
                            map.controller.setCenter(all.first())
                        } else {
                            val bb = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(all)
                            map.zoomToBoundingBox(bb.increaseByScale(1.3f), false, 80)
                        }
                    } catch (_: Exception) {}
                }
            }
            map.invalidate()
        },
    )
}

@Composable
private fun rememberMapView(): MapView {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            controller.setZoom(15.0)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
    return mapView
}
