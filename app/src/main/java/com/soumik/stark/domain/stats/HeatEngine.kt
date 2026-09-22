package com.soumik.stark.domain.stats

import com.soumik.stark.core.util.Geo
import com.soumik.stark.core.util.GeoCell
import com.soumik.stark.data.entity.HeatTile
import com.soumik.stark.data.entity.Point
import com.soumik.stark.data.repo.TrackRepository

/** Streaming per-point tile increment (design §4B): finalizing a leg bumps its tiles once. */
class HeatEngine(private val repo: TrackRepository) {

    suspend fun addLeg(points: List<Point>) {
        val z = GeoCell.HEAT_ZOOM
        val counts = HashMap<Long, Int>()
        for (p in points) {
            val lat = Geo.fromE7(p.latE7)
            val lng = Geo.fromE7(p.lngE7)
            val x = GeoCell.tileX(lng, z)
            val y = GeoCell.tileY(lat, z)
            val key = x.toLong() * 100_000_000L + y
            counts[key] = (counts[key] ?: 0) + 1
        }
        for ((key, add) in counts) {
            val x = (key / 100_000_000L).toInt()
            val y = (key % 100_000_000L).toInt()
            val cur = repo.heatDao.weight(z, x, y) ?: 0
            repo.heatDao.put(HeatTile(z, x, y, cur + add))
        }
    }
}
