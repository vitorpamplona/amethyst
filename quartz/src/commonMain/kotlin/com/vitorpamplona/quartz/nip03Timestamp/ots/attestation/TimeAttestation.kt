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
package com.vitorpamplona.quartz.nip03Timestamp.ots.attestation

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException

/**
 * Class representing Timestamp signature verification
 */
abstract class TimeAttestation : Comparable<TimeAttestation> {
    open fun tag(): ByteArray = byteArrayOf()

    /**
     * Serialize a a general Time Attestation to the specific subclass Attestation.
     *
     * @param ctx The output stream serialization context.
     */
    fun serialize(ctx: StreamSerializationContext) {
        ctx.writeBytes(this.tag())
        val ctxPayload = StreamSerializationContext()
        serializePayload(ctxPayload)
        ctx.writeVarbytes(ctxPayload.output)
    }

    open fun serializePayload(ctx: StreamSerializationContext) {
        // TODO: Is this intentional?
    }

    override fun compareTo(other: TimeAttestation): Int {
        val deltaTag = Utils.compare(this.tag(), other.tag())

        return if (deltaTag == 0) {
            this.compareTo(other)
        } else {
            deltaTag
        }
    }

    companion object {
        const val TAG_SIZE: Int = 8

        const val MAX_PAYLOAD_SIZE: Int = 8192

        /**
         * Deserialize a general Time Attestation to the specific subclass Attestation.
         *
         * @param ctx The stream deserialization context.
         * @return The specific subclass Attestation.
         */
        @Throws(DeserializationException::class)
        fun deserialize(ctx: StreamDeserializationContext): TimeAttestation {
            // console.log('attestation deserialize');

            val tag = ctx.readBytes(TAG_SIZE)

            // console.log('tag: ', com.vitorpamplona.quartz.ots.Utils.bytesToHex(tag));
            val serializedAttestation = ctx.readVarbytes(MAX_PAYLOAD_SIZE)

            // console.log('serializedAttestation: ', com.vitorpamplona.quartz.ots.Utils.bytesToHex(serializedAttestation));
            val ctxPayload = StreamDeserializationContext(serializedAttestation)

            // eslint no-use-before-define: ["error", { "classes": false }]
            if (tag.contentEquals(PendingAttestation.TAG)) {
                return PendingAttestation.deserialize(ctxPayload)
            } else if (tag.contentEquals(BitcoinBlockHeaderAttestation.TAG)) {
                return BitcoinBlockHeaderAttestation.deserialize(ctxPayload)
            }

            return UnknownAttestation(tag, serializedAttestation)
        }
    }
}
