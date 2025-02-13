/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip04Dm

import android.util.Log
import java.util.Base64

class Nip04Encoder(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
) {
    companion object {
        const val V: Int = 0

        fun decodePayload(payload: String): Nip04Encoder? {
            return try {
                val byteArray = Base64.getDecoder().decode(payload)
                check(byteArray[0].toInt() == V)
                return Nip04Encoder(
                    nonce = byteArray.copyOfRange(1, 25),
                    ciphertext = byteArray.copyOfRange(25, byteArray.size),
                )
            } catch (e: Exception) {
                Log.w("NIP04", "Unable to Parse encrypted payload: $payload")
                null
            }
        }

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

        fun decodeFromNIP04(payload: String): Nip04Encoder? =
            try {
                // cleaning up some bug from some client.
                val parts = payload.removeSuffix("-null").split("?iv=")
                Nip04Encoder(
                    ciphertext = Base64.getDecoder().decode(parts[0]),
                    nonce = Base64.getDecoder().decode(parts[1]),
                )
            } catch (e: Exception) {
                Log.w("NIP04", "Unable to Parse encrypted payload: $payload")
                null
            }
    }

    fun encodeToNIP04(): String {
        val nonce = Base64.getEncoder().encodeToString(nonce)
        val ciphertext = Base64.getEncoder().encodeToString(ciphertext)
        return "$ciphertext?iv=$nonce"
    }
}
