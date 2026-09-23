package com.soumik.stark.core.security

import android.os.Build
import java.io.File

/**
 * Lightweight root/tamper heuristic (design §6.3): detect + warn, never block — the user may root
 * their own phone. Not a substitute for Play Integrity; a best-effort local signal.
 */
object Tamper {
    private val suspiciousPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/app/Superuser.apk", "/data/local/xbin/su", "/data/local/bin/su",
        "/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules", "/cache/.disable_magisk",
    )

    /**
     * Full Play Integrity needs Play distribution + a GCP project + a verification server, none of
     * which a sideload-only app has — so we use this local heuristic (root managers, ROM signing
     * tags) as the practical "detect + warn, don't block" signal.
     */
    fun isCompromised(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        return suspiciousPaths.any { File(it).exists() }
    }
}
