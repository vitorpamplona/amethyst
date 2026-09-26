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
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.utils.LibSodiumInstance
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.UnicodeNormalizer
import kotlin.math.pow

class Nip49 {
    companion object {
        /**
         * Shortest password Amethyst accepts when *creating* an ncryptsec. NIP-49 sets no
         * minimum, and decrypting must accept any password other clients allowed, so this
         * is a creation-time policy only.
         *
         * An ncryptsec can be attacked offline with no rate limit, at one scrypt(2^16)
         * per guess. 12 characters keeps even a random-looking password out of reach of
         * large GPU farms, while staying typeable on a phone at login.
         */
        const val MIN_PASSWORD_LENGTH = 12

        /** scrypt cost used when creating: 2^16 rounds, 64 MiB. Safe on low-end phones. */
        const val DEFAULT_LOG_N = 16

        /**
         * Length as scrypt sees it: code points of the NFKC-normalized password, so
         * an emoji (a UTF-16 surrogate pair) counts once and compatibility forms
         * count as what they normalize to.
         */
        fun passwordLength(password: String): Int = UnicodeNormalizer().normalizeNFKC(password).count { !it.isLowSurrogate() }

        fun isLongEnough(password: String): Boolean = passwordLength(password) >= MIN_PASSWORD_LENGTH
    }

    fun decrypt(
        nCryptSec: String,
        password: String,
    ): HexKey = decrypt(EncryptedInfo.decodePayload(nCryptSec), password)

    fun decrypt(
        encryptedInfo: EncryptedInfo?,
        password: String = "",
    ): String {
        check(encryptedInfo != null) { "Couldn't decode key" }
        check(encryptedInfo.version == EncryptedInfo.V) { "invalid version" }
        // 32-byte key + 16-byte tag. A shorter payload can still carry a valid tag and
        // would otherwise come back as a zero-padded "key".
        check(encryptedInfo.encryptedKey.size == 48) { "invalid encrypted key length" }

        val normalizedPassword = UnicodeNormalizer().normalizeNFKC(password).encodeToByteArray()
        val n = 2.0.pow(encryptedInfo.logn.toDouble()).toInt()
        val key = deriveKey(normalizedPassword, encryptedInfo.salt, n, encryptedInfo.logn.toInt())
        val m = ByteArray(32)

        try {
            // The Poly1305 tag is what tells a wrong password apart. Inspecting the output
            // instead (e.g. "any byte > 0") rejects valid keys whose bytes are all >= 0x80.
            val authenticated =
                LibSodiumInstance.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                    m,
                    key,
                    encryptedInfo.encryptedKey,
                    byteArrayOf(encryptedInfo.keySecurity),
                    encryptedInfo.nonce,
                    key,
                )

            check(authenticated) { "Incorrect password" }

            return m.toHexKey()
        } finally {
            // NIP-49: the symmetric key should be zeroed and discarded after use.
            key.fill(0)
            normalizedPassword.fill(0)
            m.fill(0)
        }
    }

    fun encrypt(
        secretKeyHex: String,
        password: String,
        logn: Int = DEFAULT_LOG_N,
        ksb: Byte = EncryptedInfo.CLIENT_DOES_NOT_TRACK,
    ): String = encrypt(secretKeyHex.hexToByteArray(), password, logn, ksb)

    fun encrypt(
        secretKey: ByteArray,
        password: String,
        logn: Int,
        ksb: Byte,
    ): String {
        check(secretKey.size == 32) { "invalid secret key" }
        val salt = RandomInstance.bytes(16)
        val nonce = RandomInstance.bytes(24)

        val normalizedPassword = UnicodeNormalizer().normalizeNFKC(password).encodeToByteArray()
        val n = 2.0.pow(logn.toDouble()).toInt()
        val key = deriveKey(normalizedPassword, salt, n, logn)
        val ciphertext = ByteArray(48)

        try {
            // byte[] c, long[] cLen,
            // byte[] m, long mLen,
            // byte[] ad, long adLen,
            // byte[] nSec, byte[] nPub, byte[] k
            val encrypted =
                LibSodiumInstance.cryptoAeadXChaCha20Poly1305IetfEncrypt(
                    ciphertext,
                    secretKey,
                    byteArrayOf(ksb),
                    key,
                    nonce,
                    key,
                )
            // Never hand back an ncryptsec of an untouched (all-zero) buffer: it would be a
            // backup that no password can ever open.
            check(encrypted) { "Failed to encrypt the key" }
        } finally {
            // NIP-49: the symmetric key should be zeroed and discarded after use.
            key.fill(0)
            normalizedPassword.fill(0)
        }

        return EncryptedInfo(
            EncryptedInfo.V,
            logn.toByte(),
            salt,
            nonce,
            ksb,
            ciphertext,
        ).encodePayload()
    }

    /**
     * scrypt allocates 128 * r * N bytes up front: 1 GiB at LOG_N 20, which NIP-49 allows and
     * other clients may choose. Past the heap that is an OutOfMemoryError, which callers'
     * `catch (e: Exception)` would miss, crashing instead of reporting the key as unusable.
     */
    private fun deriveKey(
        normalizedPassword: ByteArray,
        salt: ByteArray,
        n: Int,
        logn: Int,
    ): ByteArray =
        try {
            SCrypt.scrypt(normalizedPassword, salt, n, 8, 1, 32)
        } catch (e: Error) {
            normalizedPassword.fill(0)
            throw IllegalStateException("Not enough memory for this key's scrypt cost (LOG_N $logn)", e)
        }

    class EncryptedInfo(
        val version: Byte,
        val logn: Byte,
        val salt: ByteArray,
        val nonce: ByteArray,
        val keySecurity: Byte,
        val encryptedKey: ByteArray,
    ) {
        companion object {
            const val V: Byte = 0x02

            const val UNSAFE_HANDLING: Byte = 0x00
            const val SAFE_HANDLING: Byte = 0x01
            const val CLIENT_DOES_NOT_TRACK: Byte = 0x02

            fun decodePayload(nCryptSec: String): EncryptedInfo? {
                val byteArray =
                    try {
                        nCryptSec.bechToBytes()
                    } catch (e: Throwable) {
                        Log.e("NIP19 Parser", "Issue trying to Decode NIP49 $nCryptSec: ${e.message}", e)
                        return null
                    }

                return try {
                    return EncryptedInfo(
                        version = byteArray[0],
                        logn = byteArray[1],
                        salt = byteArray.copyOfRange(2, 2 + 16),
                        nonce = byteArray.copyOfRange(2 + 16, 2 + 16 + 24),
                        keySecurity = byteArray.copyOfRange(2 + 16 + 24, 2 + 16 + 24 + 1).get(0),
                        encryptedKey = byteArray.copyOfRange(2 + 16 + 24 + 1, byteArray.size),
                    )
                } catch (e: Exception) {
                    Log.w("NIP44v2", "Unable to Parse encrypted ncryptsec payload")
                    null
                }
            }
        }

        // ln(n.toDouble()).toInt().toByte(),
        fun encodePayload(): String = (byteArrayOf(version, logn) + salt + nonce + keySecurity + encryptedKey).toNCryptSec()
    }
}
