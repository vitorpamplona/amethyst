/**
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

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.utils.LibSodiumInstance
import com.vitorpamplona.quartz.utils.RandomInstance
import java.text.Normalizer

class Nip49 {
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

        val normalizedPassword = Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        val n = Math.pow(2.0, encryptedInfo.logn.toDouble()).toInt()
        val key = SCrypt.scrypt(normalizedPassword, encryptedInfo.salt, n, 8, 1, 32)
        val m = ByteArray(32)

        LibSodiumInstance.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            m,
            key,
            encryptedInfo.encryptedKey,
            byteArrayOf(encryptedInfo.keySecurity),
            encryptedInfo.nonce,
            key,
        )

        check(m.any { it > 0 }) { "Incorrect password" }

        return m.toHexKey()
    }

    fun encrypt(
        secretKeyHex: String,
        password: String,
        logn: Int = 16,
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

        val normalizedPassword = Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        val n = Math.pow(2.0, logn.toDouble()).toInt()
        val key = SCrypt.scrypt(normalizedPassword, salt, n, 8, 1, 32)
        val ciphertext = ByteArray(48)

        // byte[] c, long[] cLen,
        // byte[] m, long mLen,
        // byte[] ad, long adLen,
        // byte[] nSec, byte[] nPub, byte[] k
        LibSodiumInstance.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            ciphertext,
            secretKey,
            byteArrayOf(ksb),
            key,
            nonce,
            key,
        )

        return EncryptedInfo(
            EncryptedInfo.V,
            logn.toByte(),
            salt,
            nonce,
            ksb,
            ciphertext,
        ).encodePayload()
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
        fun encodePayload(): String =
            (
                byteArrayOf(version, logn) + salt + nonce + keySecurity + encryptedKey
            ).toNCryptSec()
    }
}
