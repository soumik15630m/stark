package com.soumik.stark.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * `.stk` container: authenticated AES-256-GCM over gzipped JSON (design §6.1). Custom magic
 * header, per-backup random salt + nonce, key from the backup passphrase (PBKDF2; Argon2id is the
 * planned KDF upgrade). Other apps opening a `.stk` see only random bytes.
 */
object StkCodec {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'K'.code.toByte(), 1)
    private const val ITER = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encrypt(json: String, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(gzip(json.toByteArray(Charsets.UTF_8)))
        return ByteArrayOutputStream().apply {
            write(MAGIC); write(salt); write(nonce); write(ct)
        }.toByteArray()
    }

    fun decrypt(data: ByteArray, passphrase: String): String {
        require(data.size > 32 && data.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a Stark backup" }
        val salt = data.copyOfRange(4, 20)
        val nonce = data.copyOfRange(20, 32)
        val ct = data.copyOfRange(32, data.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        return gunzip(cipher.doFinal(ct)).toString(Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITER, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun gzip(b: ByteArray): ByteArray =
        ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(b) } }.toByteArray()

    private fun gunzip(b: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
