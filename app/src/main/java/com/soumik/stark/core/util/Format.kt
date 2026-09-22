package com.soumik.stark.core.util

import java.util.Locale

object Format {

    fun km(distanceM: Double): String = String.format(Locale.US, "%.1f", distanceM / 1000.0)

    fun km(distanceM: Long): String = km(distanceM.toDouble())

    fun kmh(speedMps: Double): Int = Math.round(speedMps * 3.6).toInt()

    fun kmhExact(speedMps: Double): Double = speedMps * 3.6

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun clock(epochMs: Long, offsetMin: Int): String {
        val local = epochMs + offsetMin * 60_000L
        val totalMin = (local / 60_000L) % (24 * 60)
        val hh = totalMin / 60
        val mm = totalMin % 60
        return String.format(Locale.US, "%02d:%02d", hh, mm)
    }
}
