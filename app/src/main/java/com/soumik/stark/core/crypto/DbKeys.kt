package com.soumik.stark.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Master-key hierarchy for the encrypted DB (design §6.1): a random SQLCipher passphrase is
 * generated once, wrapped by an AES key held in the Android Keystore (StrongBox/TEE when
 * available), and stored only in wrapped form. The raw passphrase never persists in the clear.
 */
object DbKeys {
    private const val KS = "AndroidKeyStore"
    private const val MASTER_ALIAS = "stark_master"
    private const val PREF = "stark_keys"
    private const val KEY_WRAPPED = "db_pass"
    private const val KEY_WRAPPED_DECOY = "db_pass_decoy"

    fun passphrase(context: Context, decoy: Boolean = false): ByteArray {
        val prefKey = if (decoy) KEY_WRAPPED_DECOY else KEY_WRAPPED
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stored = prefs.getString(prefKey, null)
        if (stored != null) return unwrap(stored)

        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString(prefKey, wrap(raw)).apply()
        return raw
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        gen.init(
            KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(raw)
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): ByteArray {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val ct = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }
}
