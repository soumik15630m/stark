package com.soumik.stark.core.time

import java.util.TimeZone

object TimeUtils {

    /** Minutes east of UTC for the given instant, per the device's current zone. */
    fun offsetMinutes(epochMs: Long = System.currentTimeMillis()): Int =
        TimeZone.getDefault().getOffset(epochMs) / 60_000

    /** Local calendar day as an integer yyyymmdd, stable for grouping and DailyTotal keys. */
    fun localDateKey(epochMs: Long, offsetMin: Int = offsetMinutes(epochMs)): Int {
        val localMs = epochMs + offsetMin * 60_000L
        val days = localMs / 86_400_000L
        // Convert epoch-day to yyyymmdd via java time on the fixed offset.
        val date = java.time.LocalDate.ofEpochDay(days)
        return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
    }

    fun todayKey(): Int = localDateKey(System.currentTimeMillis())
}
