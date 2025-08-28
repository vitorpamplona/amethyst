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
package com.vitorpamplona.quartz.nip04Dm.crypto

import android.util.Log
import java.util.Base64

class EncryptedInfo(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
) {
    fun encodeToNIP04() = encode(ciphertext, nonce)

    companion object {
        const val V: Int = 0

        fun isNIP04(encoded: String): Boolean {
            // cleaning up some bug from some client.
            val cleanedUp = encoded.removeSuffix("-null")

            val l = cleanedUp.length
            if (l < 28) return false
            return cleanedUp[l - 28] == '?' &&
                cleanedUp[l - 27] == 'i' &&
                cleanedUp[l - 26] == 'v' &&
                cleanedUp[l - 25] == '='
        }

        fun encode(
            ciphertext: ByteArray,
            nonce: ByteArray,
        ): String {
            val nonceB64 = Base64.getEncoder().encodeToString(nonce)
            val ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext)
            return "$ciphertextB64?iv=$nonceB64"
        }

        fun decode(payload: String): EncryptedInfo? =
            try {
                // cleaning up some bug from some client.
                val parts = payload.removeSuffix("-null").split("?iv=")
                EncryptedInfo(
                    ciphertext = Base64.getDecoder().decode(parts[0]),
                    nonce = Base64.getDecoder().decode(parts[1]),
                )
            } catch (e: Exception) {
                Log.w("NIP04", "Unable to Parse encrypted payload: $payload")
                null
            }
    }
}
