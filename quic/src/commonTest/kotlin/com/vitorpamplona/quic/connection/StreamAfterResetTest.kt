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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9000 §3.2 receive-side stream state machine. Once the peer has
 * sent RESET_STREAM for a stream, the receive side enters the "Reset
 * Recvd" terminal state. Subsequent STREAM frames on the same id are
 * peer protocol violations and MUST close the connection with
 * STREAM_STATE_ERROR.
 *
 * Pre-fix the parser silently absorbed STREAM frames after RESET — a
 * peer that violated the spec would leave us with a phantom mid-reset
 * stream that the peer believed was already dead, with diverging
 * reset/byte bookkeeping.
 */
class StreamAfterResetTest {
    @Test
    fun stream_frame_after_reset_closes_with_stream_state_error() {
        val (client, pipe) = newConnectedClient(maxStreamData = 64 * 1024)
        // Stream 3 (server-uni) — peer pushes a few bytes then resets at
        // finalSize = highest seen (legal). Receive side is now in
        // Reset Recvd terminal state.
        val streamData = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(8), fin = false)
        val reset = ResetStreamFrame(streamId = 3L, applicationErrorCode = 0L, finalSize = 8L)
        val firstDgram = pipe.buildServerApplicationDatagram(listOf(streamData, reset))!!
        feedDatagram(client, firstDgram, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)

        // Now peer (illegally) sends another STREAM frame on the same id.
        val second = StreamFrame(streamId = 3L, offset = 8L, data = ByteArray(4), fin = false)
        val secondDgram = pipe.buildServerApplicationDatagram(listOf(second))!!
        feedDatagram(client, secondDgram, nowMillis = 0L)

        assertEquals(QuicConnection.Status.CLOSED, client.status)
        val reason = client.closeReason
        assertNotNull(reason)
        assertTrue(reason.contains("STREAM_STATE_ERROR"), "expected STREAM_STATE_ERROR, got: $reason")
        assertTrue(reason.contains("3"), "reason should reference the offending stream id: $reason")
    }

    @Test
    fun stream_frame_with_fin_after_reset_also_rejected() {
        // FIN is the same path — STREAM with fin=true is still a
        // STREAM frame, so it falls under the same Reset Recvd guard.
        val (client, pipe) = newConnectedClient(maxStreamData = 64 * 1024)
        val streamData = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(8), fin = false)
        val reset = ResetStreamFrame(streamId = 3L, applicationErrorCode = 0L, finalSize = 8L)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(streamData, reset))!!, nowMillis = 0L)

        val finFrame = StreamFrame(streamId = 3L, offset = 8L, data = ByteArray(0), fin = true)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(finFrame))!!, nowMillis = 0L)

        assertEquals(QuicConnection.Status.CLOSED, client.status)
        assertTrue(client.closeReason!!.contains("STREAM_STATE_ERROR"))
    }

    @Test
    fun reset_after_clean_fin_still_accepted() {
        // FIN-then-RESET is allowed — RESET on a stream that already
        // FIN'd is a no-op on receive side (state was already terminal
        // via Data Recvd; RESET is silently OK as long as finalSize
        // matches). We don't exercise STREAM-after-this; the reverse
        // ordering is what the test above covers. This case just
        // ensures the §4.5 finalSize-matches-FIN check is preserved.
        val (client, pipe) = newConnectedClient(maxStreamData = 64 * 1024)
        val finFrame = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(8), fin = true)
        val reset = ResetStreamFrame(streamId = 3L, applicationErrorCode = 0L, finalSize = 8L)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(finFrame, reset))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
    }

    @Test
    fun two_streams_independent_states() {
        // Reset on stream 3 must NOT prevent legitimate STREAM frames on
        // stream 7. The flag is per-stream, not connection-wide.
        val (client, pipe) = newConnectedClient(maxStreamData = 64 * 1024)
        val s3data = StreamFrame(streamId = 3L, offset = 0L, data = ByteArray(8), fin = false)
        val s3reset = ResetStreamFrame(streamId = 3L, applicationErrorCode = 0L, finalSize = 8L)
        val s7data = StreamFrame(streamId = 7L, offset = 0L, data = ByteArray(8), fin = false)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(s3data, s3reset, s7data))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
        // Subsequent STREAM on still-open stream 7 — fine.
        val s7more = StreamFrame(streamId = 7L, offset = 8L, data = ByteArray(8), fin = false)
        feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(s7more))!!, nowMillis = 0L)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)
    }
}
