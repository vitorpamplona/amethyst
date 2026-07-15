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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import kotlinx.serialization.Serializable

/**
 * One recipient's entry in a rekey (CORD-06): a [locator] (the recipient's
 * pseudonym, so only they know it's for them) and the [wrapped] new key (the
 * 72-byte payload, base64'd then NIP-44-encrypted under the rotator↔recipient
 * pairwise key).
 */
@Serializable
class RekeyBlob(
    val locator: String,
    val wrapped: String,
)

/**
 * The 72-byte rekey payload: `scope_id[32] ‖ epoch_be8 ‖ new_key[32]`
 * (CORD-06 §2). Fixed-width so a recipient can verify the scope and epoch it
 * decrypts to match what they expected before adopting [newKey].
 */
class RekeyPayload(
    val scopeId: ByteArray,
    val epoch: Long,
    val newKey: ByteArray,
) {
    fun encode(): ByteArray {
        require(scopeId.size == 32) { "scopeId must be 32 bytes" }
        require(newKey.size == 32) { "newKey must be 32 bytes" }
        val out = ByteArray(SIZE)
        scopeId.copyInto(out, 0)
        ConcordKeyDerivation.writeBe64(out, 32, epoch)
        newKey.copyInto(out, 40)
        return out
    }

    companion object {
        const val SIZE = 72

        fun decode(bytes: ByteArray): RekeyPayload? {
            if (bytes.size != SIZE) return null
            var epoch = 0L
            for (i in 0 until 8) epoch = (epoch shl 8) or (bytes[32 + i].toLong() and 0xFF)
            return RekeyPayload(bytes.copyOfRange(0, 32), epoch, bytes.copyOfRange(40, 72))
        }
    }
}
