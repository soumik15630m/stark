package com.soumik.stark.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Point

/**
 * Offline mini-map (design §8.4): rasterises route shapes onto a bitmap with no map tiles/network,
 * for the rich back-home / end-of-day notifications.
 */
object RouteThumb {

    fun render(tracks: List<List<Point>>, width: Int = 1000, height: Int = 500): Bitmap? {
        val all = tracks.flatten()
        if (all.size < 2) return null
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        all.forEach {
            val la = Geo.fromE7(it.latE7); val lo = Geo.fromE7(it.lngE7)
            if (la < minLat) minLat = la; if (la > maxLat) maxLat = la
            if (lo < minLng) minLng = lo; if (lo > maxLng) maxLng = lo
        }
        val pad = 40f
        val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
        val spanLng = (maxLng - minLng).coerceAtLeast(1e-6)
        val sx = (width - 2 * pad) / spanLng
        val sy = (height - 2 * pad) / spanLat
        val s = minOf(sx, sy)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(16, 21, 27))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 168); strokeWidth = 6f; style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        fun px(lng: Double) = (pad + (lng - minLng) * s).toFloat()
        fun py(lat: Double) = (height - pad - (lat - minLat) * s).toFloat()
        tracks.forEach { t ->
            if (t.size < 2) return@forEach
            val path = Path()
            path.moveTo(px(Geo.fromE7(t[0].lngE7)), py(Geo.fromE7(t[0].latE7)))
            for (i in 1 until t.size) path.lineTo(px(Geo.fromE7(t[i].lngE7)), py(Geo.fromE7(t[i].latE7)))
            c.drawPath(path, paint)
        }
        // start (green) + end (red) dots.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.color = Color.rgb(76, 175, 80)
        c.drawCircle(px(Geo.fromE7(all.first().lngE7)), py(Geo.fromE7(all.first().latE7)), 10f, dot)
        dot.color = Color.rgb(229, 57, 53)
        c.drawCircle(px(Geo.fromE7(all.last().lngE7)), py(Geo.fromE7(all.last().latE7)), 10f, dot)
        return bmp
    }
}
