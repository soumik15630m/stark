package com.soumik.stark.core.security

import android.os.Build
import java.io.File

/**
 * Lightweight root/tamper heuristic (design §6.3): detect + warn, never block — the user may root
 * their own phone. Not a substitute for Play Integrity; a best-effort local signal.
 */
object Tamper {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/app/Superuser.apk", "/data/local/xbin/su", "/data/local/bin/su",
    )

    fun isCompromised(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        return suPaths.any { File(it).exists() }
    }
}
