package com.soumik.stark.core.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-lock PIN. The PIN is stored only as a PBKDF2 hash + salt; the raw PIN never persists.
 * (In M6 this same PIN derives the SQLCipher key via Argon2id — see design §6.1. This module is
 * the UI-gating layer and the verifier.)
 */
object AppLock {
    private const val FILE = "stark_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_DECOY_HASH = "decoy_hash"
    private const val KEY_DECOY_SALT = "decoy_salt"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val KEY_TIMEOUT_MS = "lock_timeout_ms"
    private const val ITERATIONS = 120_000
    private const val KEY_LEN = 256

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isPinSet(c: Context): Boolean = p(c).getString(KEY_HASH, null) != null

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        p(c).edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash))
            .apply()
    }

    fun setDecoyPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString(KEY_DECOY_SALT, b64(salt))
            .putString(KEY_DECOY_HASH, b64(pbkdf2(pin, salt)))
            .apply()
    }

    enum class Result { REAL, DECOY, WRONG }

    fun verify(c: Context, pin: String): Result {
        val salt = p(c).getString(KEY_SALT, null)?.let { unb64(it) }
        val hash = p(c).getString(KEY_HASH, null)?.let { unb64(it) }
        if (salt != null && hash != null && constEq(pbkdf2(pin, salt), hash)) return Result.REAL
        val dSalt = p(c).getString(KEY_DECOY_SALT, null)?.let { unb64(it) }
        val dHash = p(c).getString(KEY_DECOY_HASH, null)?.let { unb64(it) }
        if (dSalt != null && dHash != null && constEq(pbkdf2(pin, dSalt), dHash)) return Result.DECOY
        return Result.WRONG
    }

    fun setBiometric(c: Context, enabled: Boolean) = p(c).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    fun biometricEnabled(c: Context): Boolean = p(c).getBoolean(KEY_BIOMETRIC, false)

    fun timeoutMs(c: Context): Long = p(c).getLong(KEY_TIMEOUT_MS, 2 * 60_000L)
    fun setTimeoutMs(c: Context, ms: Long) = p(c).edit().putLong(KEY_TIMEOUT_MS, ms).apply()

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constEq(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
