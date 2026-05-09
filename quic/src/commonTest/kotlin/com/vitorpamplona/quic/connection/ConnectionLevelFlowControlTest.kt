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

import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.frame.StreamFrame
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §4.1: connection-level inbound flow-control enforcement.
 *
 * "All data sent in STREAM frames counts toward this limit. The sum of
 * the largest received offsets on all streams — including streams in
 * the Reset Recvd state — MUST NOT exceed the value advertised by a
 * receiver."
 *
 * Pre-fix the receiver enforced ONLY per-stream `receiveLimit` (each
 * stream individually couldn't push past `initial_max_stream_data_*`).
 * The aggregate connection-level cap (`initial_max_data`) was advertised
 * AND respected on the SEND side, but never checked on the RECEIVE
 * side. A peer that opened many streams and pushed each one up to its
 * per-stream cap could collectively exceed `initial_max_data` without
 * us closing.
 */
class ConnectionLevelFlowControlTest {
    private fun connectClient(
        maxData: Long = 16 * 1024,
        maxStreamData: Long = 8 * 1024,
        maxStreamsUni: Long = 16,
    ): Pair<QuicConnection, InMemoryQuicPipe> =
        newConnectedClient(
            maxData = maxData,
            maxStreamData = maxStreamData,
            maxStreamsUni = maxStreamsUni,
            maxStreamsBidi = 16,
        )

    @Test
    fun connection_recv_sum_below_max_data_accepted() {
        val (client, pipe) = connectClient(maxData = 16 * 1024, maxStreamData = 8 * 1024)
        // Two server-uni streams (id 3, 7) each carrying 4 KiB. Sum = 8 KiB
        // < 16 KiB connection cap. Should sail through.
        val frame1 = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(4096), fin = false)
        val frame2 = StreamFrame(streamId = 7L, offset = 0L, data = ByteArray(4096), fin = false)
        val datagram = pipe.buildServerApplicationDatagram(listOf(frame1, frame2))!!
        feedDatagram(client, datagram, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
        assertEquals(8L * 1024, client.connectionInboundOffsetSum)
    }

    @Test
    fun connection_recv_sum_exceeding_max_data_closes() {
        val (client, pipe) = connectClient(maxData = 16 * 1024, maxStreamData = 16 * 1024)
        // Three server-uni streams each shipping 6 KiB → 18 KiB > 16 KiB cap.
        // Per-stream limit (16 KiB) is fine; the aggregate breaches MAX_DATA.
        val frame1 = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(6 * 1024), fin = false)
        val frame2 = StreamFrame(streamId = 7L, offset = 0L, data = ByteArray(6 * 1024), fin = false)
        val frame3 = StreamFrame(streamId = 11L, offset = 0L, data = ByteArray(6 * 1024), fin = false)
        val datagram = pipe.buildServerApplicationDatagram(listOf(frame1, frame2, frame3))!!
        feedDatagram(client, datagram, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("FLOW_CONTROL_ERROR"), "expected FLOW_CONTROL_ERROR, got: $reason")
        assertTrue(reason.contains("MAX_DATA"))
    }

    @Test
    fun retransmitted_stream_frame_does_not_double_count() {
        val (client, pipe) = connectClient(maxData = 16 * 1024, maxStreamData = 8 * 1024)
        val frame = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(4 * 1024), fin = false)
        // First arrival.
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(frame))!!, nowMillis = 0L)
        val sumAfterFirst = client.connectionInboundOffsetSum
        assertEquals(4L * 1024, sumAfterFirst)
        // Identical retransmit — does NOT advance the per-stream high-water,
        // so connection counter stays put.
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(frame))!!, nowMillis = 0L)
        assertEquals(sumAfterFirst, client.connectionInboundOffsetSum)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
    }

    @Test
    fun reset_stream_final_size_counts_toward_connection_limit() {
        val (client, pipe) = connectClient(maxData = 16 * 1024, maxStreamData = 16 * 1024)
        // Stream 3 ships 4 KiB then resets at finalSize = 20 KiB. The
        // RESET_STREAM commits the peer to those 20 KiB even though we
        // haven't seen them on the wire — they count toward MAX_DATA per
        // §4.5. 20 KiB on a single stream alone breaches the 16 KiB
        // connection cap, so we MUST close.
        val streamData = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(4 * 1024), fin = false)
        val reset = ResetStreamFrame(streamId = 3L, applicationErrorCode = 0L, finalSize = 20L * 1024)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(streamData, reset))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("FLOW_CONTROL_ERROR"), "expected FLOW_CONTROL_ERROR, got: $reason")
    }

    @Test
    fun handshake_fixture_starts_with_zero_inbound_offset_sum() {
        // Sanity check on the bookkeeping: a fresh handshake-only
        // connection has not received any STREAM bytes, so the sum is 0
        // even though [advertisedMaxData] is the configured initial.
        val (client, pipe) = connectClient()
        runBlocking {
            // Make sure pipe-driven handshake completes to CONNECTED before
            // we read the field — otherwise we'd be looking at the value
            // mid-handshake with no peer streams in play.
            pipe.drive(maxRounds = 4)
        }
        assertEquals(0L, client.connectionInboundOffsetSum)
        assertTrue(client.advertisedMaxData > 0L)
    }
}
