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
package com.vitorpamplona.quic.webtransport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Regression coverage for [CapsuleReader] — the decoder powering peer-initiated
 * WT_CLOSE_SESSION detection on the WT CONNECT bidi.
 *
 * Audit-3 finding: the encoder existed but no decoder consumed the CONNECT
 * stream, so a peer-initiated graceful close was silently ignored. These tests
 * cover the byte-level decode plus split-chunk/empty-body edge cases that the
 * production reader (driven by `QuicStream.incoming.collect`) will hit.
 */
class CapsuleReaderTest {
    @Test
    fun decodes_complete_close_session_capsule_in_one_push() {
        val reader = CapsuleReader()
        val capsule = encodeCloseSessionCapsule(errorCode = 7, reason = "bye")
        reader.push(capsule)

        val first = reader.next()
        assertIs<WtCloseSession>(first)
        assertEquals(7, first.errorCode)
        assertEquals("bye", first.reason)

        // No second capsule queued.
        assertNull(reader.next())
    }

    @Test
    fun handles_split_chunks_across_push_calls() {
        // Chunk the encoded capsule arbitrarily — the QUIC stream callback
        // does not respect framing boundaries, so the reader must reassemble.
        val capsule = encodeCloseSessionCapsule(errorCode = 42, reason = "split-recv")
        val reader = CapsuleReader()
        // Push one byte at a time — pathological but tests the buffer logic.
        for (b in capsule) {
            reader.push(byteArrayOf(b))
        }

        val parsed = reader.next()
        assertIs<WtCloseSession>(parsed)
        assertEquals(42, parsed.errorCode)
        assertEquals("split-recv", parsed.reason)
    }

    @Test
    fun returns_null_when_buffer_holds_only_partial_capsule() {
        val reader = CapsuleReader()
        val full = encodeCloseSessionCapsule(0, "x")
        // Push everything except the last byte. Reader must wait for more data.
        reader.push(full.copyOfRange(0, full.size - 1))
        assertNull(reader.next())

        // After the final byte arrives, the capsule decodes.
        reader.push(byteArrayOf(full.last()))
        val parsed = reader.next()
        assertIs<WtCloseSession>(parsed)
    }

    @Test
    fun decodes_empty_body_close_session_as_zero_error_empty_reason() {
        // Spec lower bound: body length 0, no error code present.
        val empty = encodeCapsule(WtCapsuleType.WT_CLOSE_SESSION, ByteArray(0))
        val reader = CapsuleReader()
        reader.push(empty)
        val parsed = reader.next()
        assertIs<WtCloseSession>(parsed)
        assertEquals(0, parsed.errorCode)
        assertEquals("", parsed.reason)
    }

    @Test
    fun unknown_capsule_type_surfaces_as_raw_pair_without_breaking_stream() {
        // Server may send capsule types we don't recognise (e.g., DRAIN, future
        // extensions). They should not break the reader for subsequent
        // recognised capsules.
        val reader = CapsuleReader()
        val drainBody = byteArrayOf(0x01, 0x02)
        reader.push(encodeCapsule(WtCapsuleType.WT_DRAIN_SESSION, drainBody))
        reader.push(encodeCloseSessionCapsule(9, "after"))

        val first = reader.next()

        // Unknown types come back as a Pair<typeVarint, body>.
        @Suppress("UNCHECKED_CAST")
        val pair = first as Pair<Long, ByteArray>
        assertEquals(WtCapsuleType.WT_DRAIN_SESSION, pair.first)
        assertEquals(2, pair.second.size)

        val second = reader.next()
        assertIs<WtCloseSession>(second)
        assertEquals(9, second.errorCode)
        assertEquals("after", second.reason)
    }
}
