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
package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException

/**
 * Placeholder for attestations that don't support
 *
 * @see TimeAttestation
 */
class UnknownAttestation internal constructor(
    val tag: ByteArray,
    val payload: ByteArray,
) : TimeAttestation() {
    override fun tag(): ByteArray = tag

    override fun serializePayload(ctx: StreamSerializationContext) {
        ctx.writeBytes(this.payload)
    }

    override fun toString(): String = "UnknownAttestation " + this.tag().toHexKey() + ' ' + this.payload.toHexKey()

    override fun compareTo(other: TimeAttestation): Int {
        val ota = other as UnknownAttestation

        return Utils.compare(this.payload, ota.payload)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UnknownAttestation) {
            return false
        }

        if (!this.tag().contentEquals(other.tag())) {
            return false
        }

        if (!this.payload.contentEquals(other.payload)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = this.tag().contentHashCode() xor this.payload.contentHashCode()

    companion object {
        val TAG: ByteArray = byteArrayOf()

        @Throws(DeserializationException::class)
        fun deserialize(
            ctxPayload: StreamDeserializationContext,
            tag: ByteArray,
        ): UnknownAttestation {
            val payload = ctxPayload.readVarbytes(MAX_PAYLOAD_SIZE)
            return UnknownAttestation(tag, payload)
        }
    }
}
