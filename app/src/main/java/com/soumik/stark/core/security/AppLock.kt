package com.soumik.stark.core.security

import android.content.Context
import android.util.Base64
import com.soumik.stark.core.crypto.Argon2
import java.security.SecureRandom

/**
 * App-lock PIN. The PIN is stored only as an Argon2id hash + salt (design §6.1); the raw PIN
 * never persists. The same Argon2id derivation wraps a copy of the DB master key (see
 * [com.soumik.stark.core.crypto.DbKeys]).
 */
object AppLock {
    private const val FILE = "stark_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_DECOY_HASH = "decoy_hash"
    private const val KEY_DECOY_SALT = "decoy_salt"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val KEY_TIMEOUT_MS = "lock_timeout_ms"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isPinSet(c: Context): Boolean = p(c).getString(KEY_HASH, null) != null

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = kdf(pin, salt)
        p(c).edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash))
            .apply()
        // Wrap the DB master key under this PIN (PIN → Argon2id → master), per design §6.1.
        com.soumik.stark.core.crypto.DbKeys.addPinEnvelope(c, pin)
    }

    fun setDecoyPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString(KEY_DECOY_SALT, b64(salt))
            .putString(KEY_DECOY_HASH, b64(kdf(pin, salt)))
            .apply()
    }

    enum class Result { REAL, DECOY, WRONG }

    fun verify(c: Context, pin: String): Result {
        val salt = p(c).getString(KEY_SALT, null)?.let { unb64(it) }
        val hash = p(c).getString(KEY_HASH, null)?.let { unb64(it) }
        if (salt != null && hash != null && constEq(kdf(pin, salt), hash)) return Result.REAL
        val dSalt = p(c).getString(KEY_DECOY_SALT, null)?.let { unb64(it) }
        val dHash = p(c).getString(KEY_DECOY_HASH, null)?.let { unb64(it) }
        if (dSalt != null && dHash != null && constEq(kdf(pin, dSalt), dHash)) return Result.DECOY
        return Result.WRONG
    }

    fun setBiometric(c: Context, enabled: Boolean) = p(c).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    fun biometricEnabled(c: Context): Boolean = p(c).getBoolean(KEY_BIOMETRIC, false)

    fun timeoutMs(c: Context): Long = p(c).getLong(KEY_TIMEOUT_MS, 2 * 60_000L)
    fun setTimeoutMs(c: Context, ms: Long) = p(c).edit().putLong(KEY_TIMEOUT_MS, ms).apply()

    private fun kdf(pin: String, salt: ByteArray): ByteArray =
        Argon2.derive(pin.toByteArray(Charsets.UTF_8), salt, 32)

    private fun constEq(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
