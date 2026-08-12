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
 * fixed-width [RekeyPayload], base64'd then NIP-44-encrypted under the
 * rotator↔recipient pairwise key).
 */
@Serializable
class RekeyBlob(
    val locator: String,
    val wrapped: String,
)

/**
 * A rekey blob's plaintext (CORD-06 §1), fixed-width per form — the width
 * declaring the form:
 *
 *  - **72 bytes** — `scope_id[32] ‖ epoch_be8 ‖ new_key[32]`: a Channel
 *    rotation's blob, or a legacy pre-split *base* rotation (honored when reading
 *    old epochs, never minted anew — CORD-06 §3).
 *  - **104 bytes** — `… ‖ new_control_pk[32]`: a base rotation's member blob,
 *    also carrying the next epoch's Control Plane address (CORD-02 §2).
 *  - **136 bytes** — `… ‖ new_control_root[32]`: a base rotation's staff blob,
 *    additionally delivering the write secret (CORD-04 §3 staff).
 *
 * Any other width is malformed and the blob is dropped ([decode] returns null).
 * The scope and epoch live *inside* the ciphertext so a recipient can verify them
 * against the event's tags before adopting anything, making a blob unspliceable;
 * a staff recipient additionally requires that [newControlRoot] derive to exactly
 * [newControlPk] (CORD-02 §5) before adopting the pair.
 */
class RekeyPayload(
    val scopeId: ByteArray,
    val epoch: Long,
    val newKey: ByteArray,
    /** The next epoch's `control_pk` on a base rotation; null on a channel or legacy blob. */
    val newControlPk: ByteArray? = null,
    /** The next epoch's `control_root` on a staff base blob; null otherwise. */
    val newControlRoot: ByteArray? = null,
) {
    init {
        require(newControlRoot == null || newControlPk != null) { "a control_root is only ever delivered beside its control_pk" }
    }

    fun encode(): ByteArray {
        require(scopeId.size == 32) { "scopeId must be 32 bytes" }
        require(newKey.size == 32) { "newKey must be 32 bytes" }
        require(newControlPk == null || newControlPk.size == 32) { "newControlPk must be 32 bytes" }
        require(newControlRoot == null || newControlRoot.size == 32) { "newControlRoot must be 32 bytes" }
        val size =
            when {
                newControlRoot != null -> SIZE_BASE_STAFF
                newControlPk != null -> SIZE_BASE_MEMBER
                else -> SIZE_CHANNEL
            }
        val out = ByteArray(size)
        scopeId.copyInto(out, 0)
        ConcordKeyDerivation.writeBe64(out, 32, epoch)
        newKey.copyInto(out, 40)
        newControlPk?.copyInto(out, 72)
        newControlRoot?.copyInto(out, 104)
        return out
    }

    companion object {
        /** A Channel rotation's blob — also the legacy pre-split base form (CORD-06 §3). */
        const val SIZE_CHANNEL = 72

        /** A base rotation's member blob: `… ‖ new_control_pk[32]`. */
        const val SIZE_BASE_MEMBER = 104

        /** A base rotation's staff blob: `… ‖ new_control_root[32]`. */
        const val SIZE_BASE_STAFF = 136

        fun decode(bytes: ByteArray): RekeyPayload? {
            if (bytes.size != SIZE_CHANNEL && bytes.size != SIZE_BASE_MEMBER && bytes.size != SIZE_BASE_STAFF) return null
            var epoch = 0L
            for (i in 0 until 8) epoch = (epoch shl 8) or (bytes[32 + i].toLong() and 0xFF)
            return RekeyPayload(
                scopeId = bytes.copyOfRange(0, 32),
                epoch = epoch,
                newKey = bytes.copyOfRange(40, 72),
                newControlPk = if (bytes.size >= SIZE_BASE_MEMBER) bytes.copyOfRange(72, 104) else null,
                newControlRoot = if (bytes.size >= SIZE_BASE_STAFF) bytes.copyOfRange(104, 136) else null,
            )
        }
    }
}
