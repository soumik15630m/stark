package com.soumik.stark.ui.theme

import android.content.Context
import com.soumik.stark.core.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow

/** UI theme prefs: night-riding (red) mode and dynamic color, backed by Prefs. */
object ThemeState {
    val nightRide = MutableStateFlow(false)
    val dynamicColor = MutableStateFlow(true)

    fun init(context: Context) {
        nightRide.value = Prefs.getBool(context, "night_ride", false)
        dynamicColor.value = Prefs.getBool(context, "dynamic_color", true)
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
