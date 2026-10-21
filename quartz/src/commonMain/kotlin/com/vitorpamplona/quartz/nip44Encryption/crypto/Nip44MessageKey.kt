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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import com.vitorpamplona.crypto.equalsConstantTime
import com.vitorpamplona.crypto.kdf.Hkdf
import com.vitorpamplona.crypto.mac.MacInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/** The NIP-44 v2 per-message keys: HKDF-Expand of the conversation key to 76 bytes, split three ways. */
class MessageKey(
    val chachaKey: ByteArray,
    val chachaNonce: ByteArray,
    val hmacKey: ByteArray,
)

/**
 * NIP-44 v2's HKDF-Expand to 76 bytes, three HMAC rounds on one MAC instance, split into a
 * [MessageKey]. When [ciphertext] and [ciphertextMac] are given it also checks the message's
 * MAC with the same instance, and throws [IllegalStateException] if it doesn't match.
 */
fun Hkdf.fastExpand(
    key: ByteArray,
    nonce: ByteArray,
    ciphertext: ByteArray? = null,
    ciphertextMac: ByteArray? = null,
): MessageKey {
    check(key.size == hashLen)
    check(nonce.size == hashLen)

    val mac = MacInstance(algorithm, key)

    // First round: T(1) = HMAC-SHA256(key, nonce || 0x01)
    mac.update(nonce)
    mac.update(1)
    val round1 = mac.doFinal()

    // Second round: T(2) = HMAC-SHA256(key, T(1) || nonce || 0x02)
    mac.update(round1)
    mac.update(nonce)
    mac.update(2)
    val round2 = mac.doFinal()

    // Third round: T(3) = HMAC-SHA256(key, T(2) || nonce || 0x03)
    mac.update(round2)
    mac.update(nonce)
    mac.update(3)
    val round3 = mac.doFinal()

    val hmacKey = ByteArray(hashLen)

    // System.arraycopy(round2, 12, hmacKey, 0, 20)
    round2.copyInto(hmacKey, 0, 12, 32)
    // System.arraycopy(round3, 0, hmacKey, 20, 12)
    round3.copyInto(hmacKey, 20, 0, 12)

    // checks the mac here to avoid building a new Mac.getInstance(algorithm)
    if (ciphertext != null && ciphertextMac != null) {
        mac.init(hmacKey, algorithm)
        mac.update(nonce)
        mac.update(ciphertext)
        val calculatedMac = mac.doFinal()

        check(calculatedMac.equalsConstantTime(ciphertextMac)) {
            "Invalid Mac: Calculated ${calculatedMac.toHexKey()}, decoded: ${ciphertextMac.toHexKey()}"
        }
    }

    return MessageKey(
        chachaKey = round1,
        chachaNonce = round2.copyOfRange(0, 12),
        hmacKey = hmacKey,
    )
}
