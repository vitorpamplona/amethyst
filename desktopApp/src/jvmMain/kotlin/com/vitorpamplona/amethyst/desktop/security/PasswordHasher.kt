/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.desktop.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 password hashing for the Desktop privacy-lock PIN / password.
 *
 * Same primitive family as [com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage]
 * uses for its master-password key derivation. Stored form is
 * `saltBase64$hashBase64` — never plaintext.
 *
 * Iteration count chosen to be light-enough for an interactive unlock (~50ms
 * on modern hardware) while providing meaningful throttling on brute-force
 * attempts on the on-disk hash.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    fun verify(
        password: CharArray,
        stored: String,
    ): Boolean {
        val parts = stored.split("$")
        if (parts.size != 2) return false
        val decoder = Base64.getDecoder()
        val salt =
            runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return false
        val expected =
            runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return false
        val computed = pbkdf2(password, salt)
        return constantTimeEquals(computed, expected)
    }

    private fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
