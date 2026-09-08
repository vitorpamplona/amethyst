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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * `marmot.group.message-retention.v1`, component `0x8005` — disappearing
 * messages.
 *
 * ```text
 * struct { uint64 disappearing_message_secs; } MarmotMessageRetentionV1;
 * ```
 *
 * Exactly eight big-endian bytes, with NO length prefix — unlike most Marmot
 * component fields. `0` means disabled, and removing the component is
 * equivalent to `0`. (MIP-01 spelled this as a variable-length field and
 * treated `0` as invalid; both changed.)
 *
 * Every application message pins the retention state of its OWN source epoch.
 * A later update or removal does not shorten, extend, or restore an existing
 * message's expiry, and a retry of the same MLS message reuses the same pinned
 * value.
 *
 * The duration is authenticated but the base timestamp is the sender's own
 * `created_at`, so a sender that back- or forward-dates its message shifts when
 * that message expires. Expiry is therefore advisory — it inherits the trust
 * already placed in the MLS-authenticated sender and is not a deletion
 * guarantee against a hostile one.
 */
data class MessageRetentionV1(
    val disappearingMessageSecs: ULong,
) {
    val isEnabled: Boolean get() = disappearingMessageSecs != 0uL

    /**
     * `created_at + disappearing_message_secs` in exact, checked uint64
     * arithmetic, or null when the result is undefined.
     *
     * Null means the sender omits the transport expiry hint entirely; the
     * component state and the message both stay valid. Never wrap, saturate,
     * or route this through a floating-point JSON number.
     */
    fun expiryTimestamp(createdAt: Long): ULong? {
        if (!isEnabled) return null
        if (createdAt < 0) return null
        val base = createdAt.toULong()
        val sum = base + disappearingMessageSecs
        // ULong wraps silently on overflow, so detect it by comparison.
        if (sum < base) return null
        return sum
    }

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putUint64(disappearingMessageSecs.toLong())
        return writer.toByteArray()
    }

    companion object {
        const val COMPONENT_ID = AppComponentIds.MESSAGE_RETENTION_V1

        val DISABLED = MessageRetentionV1(0uL)

        fun decode(bytes: ByteArray): MessageRetentionV1 {
            require(bytes.size == 8) {
                "message retention component must be exactly 8 bytes, was ${bytes.size}"
            }
            val reader = TlsReader(bytes)
            // readUint64 hands back a Long; reinterpret rather than widen, so
            // a duration above 2^63 stays the value the sender wrote.
            return MessageRetentionV1(reader.readUint64().toULong())
        }
    }
}
