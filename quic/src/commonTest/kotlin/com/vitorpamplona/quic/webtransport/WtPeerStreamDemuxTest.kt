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

import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.http3.Http3FrameType
import com.vitorpamplona.quic.http3.Http3Settings
import com.vitorpamplona.quic.http3.Http3StreamType
import com.vitorpamplona.quic.stream.QuicStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the audit-3 fix: GOAWAY frames on the H3 CONTROL stream are now
 * decoded into [WtPeerStreamDemux.peerGoawayStreamId] instead of being
 * silently dropped.
 *
 * Drives the demux directly with a fake server-initiated unidirectional
 * QuicStream that carries:
 *   varint(CONTROL)         – stream-type prefix
 *   SETTINGS frame          – any well-formed body
 *   GOAWAY frame            – body = single varint stream id
 *
 * The pre-fix code matched `Http3Frame.Goaway -> Unit`, dropping the body.
 * Without this test, the fix could silently regress to the old no-op.
 */
class WtPeerStreamDemuxTest {
    @Test
    fun control_stream_goaway_sets_peer_goaway_stream_id() {
        runBlocking {
            // Server-initiated unidirectional stream id (kind == 3 mod 4).
            val controlStreamId = 3L
            val stream = QuicStream(controlStreamId, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)

            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // Push the bytes the server would have sent on its CONTROL stream:
            //   1. Stream-type prefix = 0x00 (CONTROL).
            //   2. SETTINGS frame with a single QPACK_MAX_TABLE_CAPACITY=0.
            //   3. GOAWAY frame with stream id 12.
            val typePrefix = QuicWriter().also { it.writeVarint(Http3StreamType.CONTROL) }.toByteArray()
            val settingsFrame = Http3Settings(emptyMap()).encodeFrame()
            val goawayBody = QuicWriter().also { it.writeVarint(12L) }.toByteArray()
            val goawayFrame =
                QuicWriter()
                    .apply {
                        writeVarint(Http3FrameType.GOAWAY)
                        writeVarint(goawayBody.size.toLong())
                        writeBytes(goawayBody)
                    }.toByteArray()

            stream.deliverIncoming(typePrefix)
            stream.deliverIncoming(settingsFrame)
            stream.deliverIncoming(goawayFrame)
            // Closing the stream lets the demux's read loop finish — without
            // this the test hangs on the chunk channel.
            stream.closeIncoming()

            // Wait up to a generous bound for the demux coroutine to consume
            // the bytes; the actual work is microseconds but JVM scheduling
            // jitter can stretch that.
            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.peerGoawayStreamId == null) delay(5)
                    true
                }
            assertEquals(true, ok, "demux should observe GOAWAY within timeout")
            assertEquals(12L, demux.peerGoawayStreamId)
            assertNotNull(demux.peerSettings, "SETTINGS should also have been captured")

            demuxScope.cancel()
        }
    }

    @Test
    fun control_stream_without_goaway_leaves_peer_goaway_null() {
        runBlocking {
            // Same setup minus the GOAWAY frame — peerGoawayStreamId stays null,
            // peerSettings still populated. Catches an over-eager fix that
            // accidentally fires on SETTINGS or DATA.
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            val typePrefix = QuicWriter().also { it.writeVarint(Http3StreamType.CONTROL) }.toByteArray()
            val settingsFrame = Http3Settings(emptyMap()).encodeFrame()
            stream.deliverIncoming(typePrefix)
            stream.deliverIncoming(settingsFrame)
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.peerSettings == null) delay(5)
                    true
                }
            assertEquals(true, ok)
            assertNull(demux.peerGoawayStreamId)

            demuxScope.cancel()
        }
    }
}
