package com.soumik.stark.domain.places

import android.content.Context
import android.location.Geocoder
import com.soumik.stark.core.util.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Reverse geocoding (design §3.2, §6.6). Keyless by default: on-device Android Geocoder, then OSM
 * Nominatim when network is allowed. Optional: if the user has supplied their own Google Maps
 * Platform key, use Google Geocoding — the only billable surface, and only when opted in.
 */
class Geocode(
    private val context: Context,
    private val googleKey: String? = null,
) {
    private val androidGeocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.US) else null

    fun label(lat: Double, lng: Double): String? {
        val netOk = Prefs.networkAllowed(context, Prefs.KEY_NET_GEOCODE)
        if (!googleKey.isNullOrBlank() && netOk) googleLabel(lat, lng)?.let { return it }
        androidLabel(lat, lng)?.let { return it }
        if (netOk) nominatimLabel(lat, lng)?.let { return it }
        return null
    }

    @Suppress("DEPRECATION")
    private fun androidLabel(lat: Double, lng: Double): String? = try {
        androidGeocoder?.getFromLocation(lat, lng, 1)?.firstOrNull()?.let {
            it.featureName ?: it.thoroughfare ?: it.subLocality ?: it.locality
        }
    } catch (_: Exception) { null }

    private fun googleLabel(lat: Double, lng: Double): String? = try {
        val url = URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=${URLEncoder.encode(googleKey, "UTF-8")}")
        readJson(url)?.optJSONArray("results")?.optJSONObject(0)?.optString("formatted_address")?.substringBefore(",")
    } catch (_: Exception) { null }

    private fun nominatimLabel(lat: Double, lng: Double): String? = try {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=16")
        val o = readJson(url) ?: return null
        val a = o.optJSONObject("address")
        a?.optString("road")?.ifBlank { null }
            ?: a?.optString("suburb")?.ifBlank { null }
            ?: a?.optString("neighbourhood")?.ifBlank { null }
            ?: o.optString("name").ifBlank { null }
    } catch (_: Exception) { null }

    private fun readJson(url: URL): JSONObject? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", context.packageName)
            connectTimeout = 8000; readTimeout = 8000
        }
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
