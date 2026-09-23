package com.soumik.stark.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.soumik.stark.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** A GeoPoint track plus a colour, drawn as one polyline. */
data class Track(val points: List<GeoPoint>, val color: Int = AndroidColor.rgb(0, 229, 168), val width: Float = 8f, val dashed: Boolean = false)

/** A track with a per-point speed (km/h) so it can be drawn colour-graded by speed. */
data class SpeedTrack(val points: List<GeoPoint>, val speedsKmh: List<Double>)

/** A stop/pause pin. [big] = a real stop (≥5 min); else a brief pause. */
data class StopPin(val point: GeoPoint, val label: String, val big: Boolean)

/** Green (slow) → red (fast) ramp for speed colour-coding. */
fun speedColor(kmh: Double): Int = when {
    kmh < 15 -> AndroidColor.rgb(76, 175, 80)    // green — crawling
    kmh < 30 -> AndroidColor.rgb(0, 229, 168)    // teal
    kmh < 50 -> AndroidColor.rgb(255, 214, 64)   // amber
    kmh < 70 -> AndroidColor.rgb(255, 138, 0)    // orange
    else -> AndroidColor.rgb(229, 57, 53)        // red — fast
}

@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    tracks: List<Track> = emptyList(),
    speedTracks: List<SpeedTrack> = emptyList(),
    stops: List<StopPin> = emptyList(),
    fitToTracks: Boolean = true,
    initialZoom: Double = 15.0,
    configure: (MapView) -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = rememberMapView()

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.removeAll { it is Polyline || it is Marker }
            val all = ArrayList<GeoPoint>()

            tracks.forEach { t ->
                if (t.points.isEmpty()) return@forEach
                map.overlays.add(Polyline(map).apply {
                    setPoints(t.points)
                    outlinePaint.color = t.color
                    outlinePaint.strokeWidth = t.width
                    if (t.dashed) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                })
                all.addAll(t.points)
            }

            // Speed-graded: one short polyline per hop, coloured by that hop's speed.
            speedTracks.forEach { st ->
                for (i in 1 until st.points.size) {
                    val seg = Polyline(map).apply {
                        setPoints(listOf(st.points[i - 1], st.points[i]))
                        outlinePaint.color = speedColor(st.speedsKmh.getOrElse(i) { 0.0 })
                        outlinePaint.strokeWidth = 9f
                    }
                    map.overlays.add(seg)
                }
                all.addAll(st.points)
            }

            stops.forEach { s ->
                map.overlays.add(Marker(map).apply {
                    position = s.point
                    title = s.label
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(context, if (s.big) R.drawable.ic_marker_stop else R.drawable.ic_marker_pause)
                })
            }

            configure(map)
            if (fitToTracks && all.isNotEmpty()) {
                map.post {
                    try {
                        if (all.size == 1) {
                            map.controller.setZoom(initialZoom)
                            map.controller.setCenter(all.first())
                        } else {
                            map.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPointsSafe(all).increaseByScale(1.3f), false, 80)
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
    val context = LocalContext.current
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
