package com.soumik.stark.core.util

import android.content.Context

/** Lightweight non-sensitive flags (no coordinates). App data lives in the encrypted-ready DB. */
object Prefs {
    private const val FILE = "stark_prefs"
    const val KEY_TRACKING_ACTIVE = "tracking_active"
    const val KEY_ONBOARDED = "onboarded"
    const val KEY_KEEP_AWAKE = "keep_awake"

    private fun p(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        p(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        p(context).edit().putBoolean(key, value).apply()
    }
}
