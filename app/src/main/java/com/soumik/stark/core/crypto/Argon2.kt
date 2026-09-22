package com.soumik.stark.core.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * Argon2id KDF (design §6.1). Memory cost is 64 MiB (device-safe; the spec's ~256 MB OOMs budget
 * phones) with 3 iterations — still a heavy, GPU-resistant derivation (~0.5–1 s).
 */
object Argon2 {
    private val argon2 by lazy { Argon2Kt() }
    private const val ITERATIONS = 3
    private const val MEMORY_KIB = 65_536 // 64 MiB
    private const val PARALLELISM = 2

    fun derive(password: ByteArray, salt: ByteArray, lengthBytes: Int = 32): ByteArray {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = ITERATIONS,
            mCostInKibibyte = MEMORY_KIB,
            parallelism = PARALLELISM,
            hashLengthInBytes = lengthBytes,
        )
        return result.rawHashAsByteArray()
    }
}
