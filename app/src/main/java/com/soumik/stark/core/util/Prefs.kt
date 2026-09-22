package com.soumik.stark.core.util

import android.content.Context

/** Lightweight non-sensitive flags (no coordinates). App data lives in the encrypted-ready DB. */
object Prefs {
    private const val FILE = "stark_prefs"
    const val KEY_TRACKING_ACTIVE = "tracking_active"
    const val KEY_ONBOARDED = "onboarded"
    const val KEY_KEEP_AWAKE = "keep_awake"
    const val KEY_AUTO_TRACK = "auto_track"
    const val KEY_NET_MASTER = "net_master"      // master kill switch (true = network allowed)
    const val KEY_NET_MAP = "net_map"
    const val KEY_NET_GEOCODE = "net_geocode"
    const val KEY_NET_UPDATE = "net_update"
    const val KEY_AUTOMATION_OUT = "automation_out"
    const val KEY_UPDATE_OWNER = "update_owner"
    const val KEY_UPDATE_REPO = "update_repo"

    private fun p(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        p(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        p(context).edit().putBoolean(key, value).apply()
    }

    fun getString(context: Context, key: String, default: String = ""): String =
        p(context).getString(key, default) ?: default

    fun setString(context: Context, key: String, value: String) {
        p(context).edit().putString(key, value).apply()
    }

    /** Network is allowed for a feature only if the master switch AND the feature switch are on. */
    fun networkAllowed(context: Context, featureKey: String): Boolean =
        getBool(context, KEY_NET_MASTER, true) && getBool(context, featureKey, true)
}
