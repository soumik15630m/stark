package com.soumik.stark.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.soumik.stark.core.util.Geo
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.repo.TrackRepository
import java.io.File
import java.time.Instant

/** Exports a trip as GPX and shares it. Points inside any privacy zone are clipped (design §9). */
object TripShare {

    suspend fun shareGpx(context: Context, legId: Long) {
        val repo = TrackRepository.get(context)
        val leg = repo.legDao.byId(legId) ?: return
        val zones = repo.privacyDao.all()
        val points = repo.pointsForLeg(legId).filter { p ->
            zones.none { z ->
                Geo.distanceM(Geo.fromE7(p.latE7), Geo.fromE7(p.lngE7), Geo.fromE7(z.latE7), Geo.fromE7(z.lngE7)) <= z.radiusM
            }
        }
        val gpx = buildGpx(points, "Stark trip ${leg.dateKey}")
        val file = File(context.cacheDir, "stark-trip-$legId.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share trip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun buildGpx(points: List<Point>, name: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Stark\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<trk><name>").append(name).append("</name><trkseg>\n")
        points.forEach { p ->
            val lat = Geo.fromE7(p.latE7); val lng = Geo.fromE7(p.lngE7)
            append("<trkpt lat=\"").append(lat).append("\" lon=\"").append(lng).append("\">")
            append("<time>").append(Instant.ofEpochMilli(p.tUtc).toString()).append("</time>")
            append("</trkpt>\n")
        }
        append("</trkseg></trk></gpx>\n")
    }
}
