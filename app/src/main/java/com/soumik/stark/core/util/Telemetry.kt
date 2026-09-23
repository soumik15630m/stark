package com.soumik.stark.core.util

import android.content.Context
import com.soumik.stark.core.time.TimeUtils

/**
 * On-device telemetry (design §4A) — never leaves the phone. Cheap counters in prefs so the
 * Diagnostics screen can show the user why to trust the odometer.
 */
object Telemetry {
    private const val FILE = "stark_telemetry"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun onServiceEnabled(c: Context) = p(c).edit().putLong("enabled_since", System.currentTimeMillis()).apply()

    fun onFix(c: Context) {
        val today = TimeUtils.todayKey()
        val pr = p(c)
        val day = pr.getInt("fix_day", 0)
        val count = if (day == today) pr.getInt("fix_count", 0) + 1 else 1
        pr.edit()
            .putInt("fix_day", today)
            .putInt("fix_count", count)
            .putLong("last_fix_at", System.currentTimeMillis())
            .apply()
    }

    fun onThermalEvent(c: Context, status: Int) =
        p(c).edit()
            .putInt("thermal_events", p(c).getInt("thermal_events", 0) + 1)
            .putInt("last_thermal", status)
            .apply()

    fun onLowBatteryPause(c: Context) =
        p(c).edit().putInt("low_batt_pauses", p(c).getInt("low_batt_pauses", 0) + 1).apply()

    data class Snapshot(
        val enabledSince: Long,
        val lastFixAt: Long,
        val fixesToday: Int,
        val thermalEvents: Int,
        val lastThermal: Int,
        val lowBatteryPauses: Int,
    )

    fun snapshot(c: Context): Snapshot {
        val pr = p(c)
        val today = TimeUtils.todayKey()
        return Snapshot(
            enabledSince = pr.getLong("enabled_since", 0),
            lastFixAt = pr.getLong("last_fix_at", 0),
            fixesToday = if (pr.getInt("fix_day", 0) == today) pr.getInt("fix_count", 0) else 0,
            thermalEvents = pr.getInt("thermal_events", 0),
            lastThermal = pr.getInt("last_thermal", 0),
            lowBatteryPauses = pr.getInt("low_batt_pauses", 0),
        )
    }
}
