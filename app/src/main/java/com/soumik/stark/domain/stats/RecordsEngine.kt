package com.soumik.stark.domain.stats

import com.soumik.stark.data.entity.Leg
import com.soumik.stark.data.entity.Record
import com.soumik.stark.data.repo.TrackRepository

object RecordTypes {
    const val LONGEST_RIDE_M = "longest_ride_m"
    const val TOP_SPEED_MPS = "top_speed_mps"
    const val MOST_KM_DAY_M = "most_km_day_m"
    const val LONGEST_DURATION_S = "longest_duration_s"
}

/** Updates the records board after each closed leg; returns a label if a new record was set. */
class RecordsEngine(private val repo: TrackRepository) {

    suspend fun onLegClosed(leg: Leg): String? {
        var newRecord: String? = null

        val longest = repo.recordDao.get(RecordTypes.LONGEST_RIDE_M)
        if (longest == null || leg.distanceM > longest.value) {
            repo.recordDao.put(Record(RecordTypes.LONGEST_RIDE_M, leg.distanceM, leg.endT ?: leg.startT, leg.id))
            newRecord = "Longest ride: ${(leg.distanceM / 1000).format1()} km"
        }

        val top = repo.recordDao.get(RecordTypes.TOP_SPEED_MPS)
        if (top == null || leg.maxSpeedMps > top.value) {
            repo.recordDao.put(Record(RecordTypes.TOP_SPEED_MPS, leg.maxSpeedMps, leg.endT ?: leg.startT, leg.id))
            newRecord = "Top speed: ${Math.round(leg.maxSpeedMps * 3.6)} km/h"
        }

        val dur = repo.recordDao.get(RecordTypes.LONGEST_DURATION_S)
        if (dur == null || leg.durationS > dur.value) {
            repo.recordDao.put(Record(RecordTypes.LONGEST_DURATION_S, leg.durationS.toDouble(), leg.endT ?: leg.startT, leg.id))
        }

        val daily = repo.totalsDao.daily(leg.dateKey)
        val mostDay = repo.recordDao.get(RecordTypes.MOST_KM_DAY_M)
        if (daily != null && (mostDay == null || daily.distanceAllM > mostDay.value)) {
            repo.recordDao.put(Record(RecordTypes.MOST_KM_DAY_M, daily.distanceAllM, leg.endT ?: leg.startT, null))
        }
        return newRecord
    }
}

private fun Double.format1() = String.format(java.util.Locale.US, "%.1f", this)
