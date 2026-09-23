package com.soumik.stark.ui.theme

import android.content.Context
import com.soumik.stark.core.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

/** UI theme prefs: night-riding (red) mode and dynamic color, backed by Prefs. */
object ThemeState {
    val nightRide = MutableStateFlow(false)
    val dynamicColor = MutableStateFlow(true)
    val reduceMotion = MutableStateFlow(false)

    fun init(context: Context) {
        nightRide.value = Prefs.getBool(context, "night_ride", false)
        dynamicColor.value = Prefs.getBool(context, "dynamic_color", true)
        // Manual toggle OR the system "remove animations" accessibility setting.
        val systemScale = try {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
        reduceMotion.value = Prefs.getBool(context, "reduce_motion", false) || systemScale == 0f
    }

    fun setReduceMotion(context: Context, on: Boolean) {
        reduceMotion.value = on
        Prefs.setBool(context, "reduce_motion", on)
    }

    fun setNight(context: Context, on: Boolean) {
        nightRide.value = on
        Prefs.setBool(context, "night_ride", on)
    }

    fun setDynamic(context: Context, on: Boolean) {
        dynamicColor.value = on
        Prefs.setBool(context, "dynamic_color", on)
    }
}
