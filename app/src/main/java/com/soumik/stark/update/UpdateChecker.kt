package com.soumik.stark.update

import android.content.Context
import com.soumik.stark.core.util.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String?,
    val sha256: String?,
)

/**
 * Checks the public GitHub Releases API for a newer build (design §7). Network only when the
 * update toggle + master switch allow it. Verification (checksum + signature) happens at install.
 */
class UpdateChecker(private val context: Context) {

    fun currentVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    fun owner(): String = Prefs.getString(context, Prefs.KEY_UPDATE_OWNER).ifBlank { com.soumik.stark.BuildConfig.UPDATE_OWNER }
    fun repo(): String = Prefs.getString(context, Prefs.KEY_UPDATE_REPO).ifBlank { com.soumik.stark.BuildConfig.UPDATE_REPO }
    fun isConfigured(): Boolean = owner().isNotBlank() && repo().isNotBlank()

    fun check(): ReleaseInfo? {
        if (!Prefs.networkAllowed(context, Prefs.KEY_NET_UPDATE)) return null
        val owner = owner()
        val repo = repo()
        if (owner.isBlank() || repo.isBlank()) return null

        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val notes = o.optString("body")
            val assets = o.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apkUrl = a.optString("browser_download_url"); break }
                }
            }
            val sha = Regex("sha256[:=]\\s*([0-9a-fA-F]{64})").find(notes)?.groupValues?.get(1)
            return ReleaseInfo(tag, tag.removePrefix("v"), notes, apkUrl, sha)
        } finally {
            conn.disconnect()
        }
    }

    /** True when [remote] version string is strictly newer than the installed one (numeric compare). */
    fun isNewer(remote: String): Boolean {
        fun parts(s: String) = s.filter { it.isDigit() || it == '.' }.split(".").mapNotNull { it.toIntOrNull() }
        val a = parts(remote); val b = parts(currentVersion())
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
