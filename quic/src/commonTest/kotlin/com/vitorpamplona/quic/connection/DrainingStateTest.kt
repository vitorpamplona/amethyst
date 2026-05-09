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

import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.StreamFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §10.2.2 draining state.
 *
 * When an endpoint receives a CONNECTION_CLOSE frame from its peer it
 * MUST enter the draining state. While draining:
 *  - the endpoint MUST NOT send further packets;
 *  - the endpoint SHOULD discard inbound packets without processing;
 *  - the state MUST persist for at least 3 * PTO before transitioning
 *    to fully closed (so the peer's last retransmits can converge).
 *
 * Pre-fix the parser called `markClosedExternally` directly on inbound
 * CONNECTION_CLOSE which transitioned straight to CLOSED. There was no
 * grace period, no observable DRAINING phase, and the read loop didn't
 * explicitly drop subsequent late packets (it just exited on CLOSED —
 * functional equivalence, but the spec-mandated state was missing
 * from observability).
 */
class DrainingStateTest {
    @Test
    fun peer_connection_close_transitions_to_draining() {
        val (client, pipe) = newConnectedClient()
        val close =
            ConnectionCloseFrame(
                errorCode = 0L,
                frameType = 0L, // transport-close (non-null frameType)
                reason = "test peer close",
            )
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(close))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.DRAINING, client.status)
        assertNotNull(client.drainingDeadlineMs, "deadline must be set")
        assertTrue(client.drainingDeadlineMs!! > 0L)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("peer CONNECTION_CLOSE"))
    }

    @Test
    fun late_inbound_during_draining_is_dropped() {
        val (client, pipe) = newConnectedClient()
        val close =
            ConnectionCloseFrame(
                errorCode = 0L,
                frameType = 0L,
                reason = "draining drop test",
            )
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(close))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.DRAINING, client.status)

        // Feed a STREAM frame after the peer-close. Per §10.2.2 the
        // packet MUST be discarded; the connection-level
        // bookkeeping (e.g. `connectionInboundOffsetSum`) MUST stay
        // put.
        val before = client.connectionInboundOffsetSum
        val streamFrame = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(64), fin = false)
        val datagram = pipe.buildServerApplicationDatagram(listOf(streamFrame))
        // The pipe build may legitimately succeed (server keys are
        // separate from client state) — test only matters if it does.
        if (datagram != null) {
            feedDatagram(client, datagram, nowMillis = 0L)
            assertEquals(before, client.connectionInboundOffsetSum, "draining must drop without bookkeeping update")
        }
        // Status remains DRAINING — late packet did not flip us to
        // CLOSED prematurely.
        assertEquals(QuicConnection.Status.DRAINING, client.status)
    }

    @Test
    fun isDrainingExpired_false_before_deadline() {
        val (client, _) = newConnectedClient()
        client.enterDraining("test", nowMillis = 0L)
        // Just before the deadline.
        val deadline = client.drainingDeadlineMs!!
        assertFalse(client.isDrainingExpired(deadline - 1L))
    }

    @Test
    fun isDrainingExpired_true_at_deadline() {
        val (client, _) = newConnectedClient()
        client.enterDraining("test", nowMillis = 0L)
        val deadline = client.drainingDeadlineMs!!
        assertTrue(client.isDrainingExpired(deadline))
        assertTrue(client.isDrainingExpired(deadline + 100L))
    }

    @Test
    fun draining_floor_at_least_minimum_period() {
        // Pre-handshake the PTO is small (smoothed_rtt floor only),
        // so the §10.2.2 "3 * PTO" target could be vanishingly tiny.
        // The implementation floors at MIN_DRAINING_PERIOD_MS so qlog
        // observers and tests can see the DRAINING window.
        val (client, _) = newConnectedClient()
        client.enterDraining("test", nowMillis = 0L)
        val deadline = client.drainingDeadlineMs!!
        assertTrue(deadline >= QuicConnection.MIN_DRAINING_PERIOD_MS, "expected ≥ ${QuicConnection.MIN_DRAINING_PERIOD_MS}, got $deadline")
    }

    @Test
    fun second_connection_close_during_draining_is_no_op() {
        val (client, pipe) = newConnectedClient()
        val close =
            ConnectionCloseFrame(
                errorCode = 0L,
                frameType = 0L,
                reason = "first close",
            )
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(close))!!, nowMillis = 0L)
        val firstReason = client.closeReason
        val firstDeadline = client.drainingDeadlineMs

        // Peer retransmits its CONNECTION_CLOSE — we MUST NOT reset
        // the draining deadline (would extend the grace indefinitely
        // under retransmits) or change the recorded reason.
        val secondClose =
            ConnectionCloseFrame(
                errorCode = 0L,
                frameType = 0L,
                reason = "second close",
            )
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(secondClose))!!, nowMillis = 50L)
        assertEquals(firstReason, client.closeReason, "first-call wins on closeReason")
        assertEquals(firstDeadline, client.drainingDeadlineMs, "deadline should not advance on retransmit")
    }

    @Test
    fun fresh_connection_has_no_draining_deadline() {
        val (client, _) = newConnectedClient()
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
        assertNull(client.drainingDeadlineMs)
    }
}
