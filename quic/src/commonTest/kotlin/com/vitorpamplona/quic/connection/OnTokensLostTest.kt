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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.connection.recovery.RecoveryToken
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Step 6 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
 * `QuicConnection.onTokensLost` dispatches lost-packet tokens to
 * the matching `pending*` field with the supersede check (lost
 * value must equal current advertised). Mirrors neqo's
 * `streams.rs::lost` dispatch + `fc.rs::frame_lost` flag-set logic.
 */
class OnTokensLostTest {
    private fun newConn(): QuicConnection =
        QuicConnection(
            serverName = "example.test",
            config = QuicConnectionConfig(),
            tlsCertificateValidator =
                com.vitorpamplona.quic.tls
                    .PermissiveCertificateValidator(),
        )

    @Test
    fun ackToken_doesNotPopulateAnyPending() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.onTokensLost(listOf(RecoveryToken.Ack))
            } finally {
                conn.lock.unlock()
            }
            assertNull(conn.pendingMaxStreamsUni)
            assertNull(conn.pendingMaxStreamsBidi)
            assertNull(conn.pendingMaxData)
            assertEquals(emptyMap<Long, Long>(), conn.pendingMaxStreamData)
        }

    @Test
    fun lostMaxStreamsUni_matchingAdvertised_setsPending() =
        runBlocking {
            val conn = newConn()
            // Simulate the writer having advertised a higher cap.
            conn.lock.lock()
            try {
                conn.advertisedMaxStreamsUni = 150L
                conn.onTokensLost(listOf(RecoveryToken.MaxStreamsUni(maxStreams = 150L)))
            } finally {
                conn.lock.unlock()
            }
            assertEquals(150L, conn.pendingMaxStreamsUni)
        }

    @Test
    fun lostMaxStreamsUni_supersededByHigherEmit_isDropped() =
        runBlocking {
            val conn = newConn()
            // The writer has since advertised a higher cap (200) than
            // the value carried by the lost token (150). The lost
            // frame is irrelevant — re-emitting 150 would not extend
            // the cap. neqo's fc.rs line 322 supersede check.
            conn.lock.lock()
            try {
                conn.advertisedMaxStreamsUni = 200L
                conn.onTokensLost(listOf(RecoveryToken.MaxStreamsUni(maxStreams = 150L)))
            } finally {
                conn.lock.unlock()
            }
            assertNull(conn.pendingMaxStreamsUni, "stale lost extension must not be re-emitted")
        }

    @Test
    fun lostMaxStreamsBidi_matchingAdvertised_setsPending() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.advertisedMaxStreamsBidi = 200L
                conn.onTokensLost(listOf(RecoveryToken.MaxStreamsBidi(maxStreams = 200L)))
            } finally {
                conn.lock.unlock()
            }
            assertEquals(200L, conn.pendingMaxStreamsBidi)
        }

    @Test
    fun lostMaxData_matchingAdvertised_setsPending() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.advertisedMaxData = 1_000_000L
                conn.onTokensLost(listOf(RecoveryToken.MaxData(maxData = 1_000_000L)))
            } finally {
                conn.lock.unlock()
            }
            assertEquals(1_000_000L, conn.pendingMaxData)
        }

    @Test
    fun lostMaxData_supersededIsDropped() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.advertisedMaxData = 2_000_000L
                conn.onTokensLost(listOf(RecoveryToken.MaxData(maxData = 1_000_000L)))
            } finally {
                conn.lock.unlock()
            }
            assertNull(conn.pendingMaxData)
        }

    @Test
    fun lostMaxStreamData_unknownStream_dropped() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.onTokensLost(
                    listOf(RecoveryToken.MaxStreamData(streamId = 999L, maxData = 1024L)),
                )
            } finally {
                conn.lock.unlock()
            }
            // No stream with id 999 exists ⇒ token is dropped silently.
            assertEquals(emptyMap<Long, Long>(), conn.pendingMaxStreamData)
        }

    @Test
    fun multipleLostTokens_dispatchAll() =
        runBlocking {
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.advertisedMaxStreamsUni = 150L
                conn.advertisedMaxStreamsBidi = 200L
                conn.advertisedMaxData = 5_000_000L
                conn.onTokensLost(
                    listOf(
                        RecoveryToken.Ack,
                        RecoveryToken.MaxStreamsUni(maxStreams = 150L),
                        RecoveryToken.MaxStreamsBidi(maxStreams = 200L),
                        RecoveryToken.MaxData(maxData = 5_000_000L),
                    ),
                )
            } finally {
                conn.lock.unlock()
            }
            assertEquals(150L, conn.pendingMaxStreamsUni)
            assertEquals(200L, conn.pendingMaxStreamsBidi)
            assertEquals(5_000_000L, conn.pendingMaxData)
        }

    @Test
    fun lostTokensFromMultiplePackets_unionInPending() =
        runBlocking {
            // Two SentPackets each lost; their tokens are dispatched
            // sequentially. Each frame type's pending field holds at
            // most one value (the last setter wins; the supersede
            // check filters older losses).
            val conn = newConn()
            conn.lock.lock()
            try {
                conn.advertisedMaxStreamsUni = 200L
                // First lost packet had MaxStreamsUni(150) — stale, dropped.
                conn.onTokensLost(listOf(RecoveryToken.MaxStreamsUni(maxStreams = 150L)))
                assertNull(conn.pendingMaxStreamsUni)
                // Second lost packet had MaxStreamsUni(200) — current, set.
                conn.onTokensLost(listOf(RecoveryToken.MaxStreamsUni(maxStreams = 200L)))
                assertEquals(200L, conn.pendingMaxStreamsUni)
            } finally {
                conn.lock.unlock()
            }
        }
}
