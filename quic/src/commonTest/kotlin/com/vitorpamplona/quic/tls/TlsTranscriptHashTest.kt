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
package com.vitorpamplona.quic.tls

import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Regression coverage for [TlsTranscriptHash] after the switch from the
 * O(n²) "concatenate-and-rehash on every snapshot" implementation to an
 * incremental SHA-256 driven by [TlsRunningSha256].
 *
 * The contract that production TLS code depends on:
 *
 *   1. snapshot() bytes equal the SHA-256 of all appended bytes in order.
 *   2. Multiple snapshots taken at the same point yield identical bytes.
 *   3. snapshot() must NOT consume the running hash — further append() calls
 *      keep extending the same digest. This is the non-obvious bit: a naive
 *      `digest.digest()` finalizes and resets, so without the clone trick a
 *      handshake-mid snapshot would silently corrupt later snapshots.
 *   4. Output is always 32 bytes.
 */
class TlsTranscriptHashTest {
    @Test
    fun snapshot_matches_sha256_of_concatenated_bytes() {
        val transcript = TlsTranscriptHash()
        val msg1 = byteArrayOf(1, 2, 3, 4)
        val msg2 = byteArrayOf(5, 6, 7, 8, 9)
        transcript.append(msg1)
        transcript.append(msg2)

        val expected = sha256(msg1 + msg2)
        assertContentEquals(expected, transcript.snapshot())
    }

    @Test
    fun empty_transcript_hashes_empty_input() {
        val transcript = TlsTranscriptHash()
        assertContentEquals(sha256(ByteArray(0)), transcript.snapshot())
    }

    @Test
    fun output_is_always_32_bytes() {
        val transcript = TlsTranscriptHash()
        assertEquals(32, transcript.snapshot().size)
        transcript.append(byteArrayOf(0x42))
        assertEquals(32, transcript.snapshot().size)
    }

    @Test
    fun snapshot_does_not_consume_running_state() {
        // The bug we're guarding against: `MessageDigest.digest()` finalizes
        // and resets. If the implementation accidentally uses that instead of
        // cloning, the second snapshot would hash only the post-snapshot
        // bytes, not the full transcript. TLS 1.3 takes ≥3 snapshots per
        // handshake, so this would corrupt every later key.
        val transcript = TlsTranscriptHash()
        val ch = byteArrayOf(0x01, 0x00, 0x00, 0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val sh = byteArrayOf(0x02, 0x00, 0x00, 0x04, 0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val ee = byteArrayOf(0x08, 0x00, 0x00, 0x02, 0x00, 0x00)

        transcript.append(ch)
        transcript.append(sh)
        val handshakeSnapshot = transcript.snapshot()

        transcript.append(ee)
        val applicationSnapshot = transcript.snapshot()

        // Both must be SHA-256 of their respective prefixes.
        assertContentEquals(sha256(ch + sh), handshakeSnapshot)
        assertContentEquals(sha256(ch + sh + ee), applicationSnapshot)
    }

    @Test
    fun two_snapshots_at_same_position_match() {
        val transcript = TlsTranscriptHash()
        transcript.append(byteArrayOf(0x10, 0x20, 0x30))
        val a = transcript.snapshot()
        val b = transcript.snapshot()
        assertContentEquals(a, b)
    }

    @Test
    fun snapshots_at_different_positions_differ() {
        // Sanity: bumping the transcript should produce a new hash. Catches
        // an implementation that accidentally caches and returns a stale
        // snapshot.
        val transcript = TlsTranscriptHash()
        transcript.append(byteArrayOf(0x00))
        val before = transcript.snapshot()
        transcript.append(byteArrayOf(0x01))
        val after = transcript.snapshot()
        assertFalse(before.contentEquals(after))
    }
}
