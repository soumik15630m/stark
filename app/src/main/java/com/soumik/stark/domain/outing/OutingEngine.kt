package com.soumik.stark.domain.outing

import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.Outing
import com.soumik.stark.data.repo.TrackRepository

data class OutingSummary(
    val outingId: Long,
    val distanceM: Double,
    val legCount: Int,
    val placeCount: Int,
    val startT: Long,
    val endT: Long,
    val topSpeedKmh: Int,
    val newRecord: String?,
)

/**
 * Groups legs into outings (base → … → base) and fires the back-home summary when a leg ends at
 * base (design §4.6). Base = pinned Home when set, else the outing's first start place.
 */
class OutingEngine(private val repo: TrackRepository) {

    /** Attach a just-closed leg to the current outing; returns a summary when the outing closes. */
    suspend fun onLegClosed(leg: Leg, topSpeedKmh: Int, newRecord: String?): OutingSummary? {
        val home = repo.placeDao.home()
        var outing = repo.outingDao.openOuting()
        if (outing == null) {
            val id = repo.outingDao.insert(
                Outing(
                    basePlaceId = home?.id ?: leg.startPlaceId,
                    startT = leg.startT,
                    endT = leg.endT,
                    offsetMin = leg.offsetMin,
                )
            )
            outing = repo.outingDao.byId(id)!!
        }
        repo.legDao.update(leg.copy(outingId = outing.id))

        val legs = repo.legDao.legsForOuting(outing.id) + leg
        val placeIds = legs.flatMap { listOfNotNull(it.startPlaceId, it.endPlaceId) }.toSet()
        val updated = outing.copy(
            distanceM = outing.distanceM + leg.distanceM,
            legCount = outing.legCount + 1,
            placeCount = placeIds.size,
            endT = leg.endT,
        )

        val base = home?.id ?: outing.basePlaceId
        val backAtBase = base != null && leg.endPlaceId == base && updated.legCount >= 1
        if (backAtBase) {
            repo.outingDao.update(updated.copy(closed = true, summarySent = true))
            return OutingSummary(
                outingId = updated.id,
                distanceM = updated.distanceM,
                legCount = updated.legCount,
                placeCount = updated.placeCount,
                startT = updated.startT,
                endT = leg.endT ?: leg.startT,
                topSpeedKmh = topSpeedKmh,
                newRecord = newRecord,
            )
        }
        repo.outingDao.update(updated)
        return null
    }
}
