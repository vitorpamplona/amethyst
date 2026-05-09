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
import com.vitorpamplona.quic.http3.Http3ErrorCode
import com.vitorpamplona.quic.http3.Http3FrameType
import com.vitorpamplona.quic.http3.Http3Settings
import com.vitorpamplona.quic.http3.Http3SettingsId
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

/**
 * RFC 9114 §6.2.1 + RFC 9204 §4.2 critical-stream closure.
 *
 * "If either control stream is closed at any point, this MUST be
 * treated as a connection error of type H3_CLOSED_CRITICAL_STREAM."
 *
 * RFC 9204 §4.2 extends this to the QPACK encoder + decoder streams.
 *
 * Pre-fix the demux recorded a `peerH3ProtocolError` flag on
 * frame-validation throws but did NOT auto-close the connection on
 * peer-side stream FIN at all — the application was expected to poll
 * the flag and call close(). For WiFi↔cellular handoff and other
 * lossy paths where the relay can drop the control stream
 * mid-session, the audio user saw stalled output with no error event.
 *
 * The fix: the demux now drives `connection.close(errorCode, reason)`
 * itself when any critical stream closes (clean FIN OR protocol
 * violation). The closure intent is latched on
 * `criticalStreamClosureCode` / `criticalStreamClosureReason` so tests
 * can verify the path runs without wiring a real driver.
 */
class CriticalStreamClosureTest {
    private fun controlStreamPrefix(): ByteArray = QuicWriter().also { it.writeVarint(Http3StreamType.CONTROL) }.toByteArray()

    private fun qpackEncoderPrefix(): ByteArray = QuicWriter().also { it.writeVarint(Http3StreamType.QPACK_ENCODER) }.toByteArray()

    private fun validSettingsFrame(): ByteArray =
        Http3Settings(
            mapOf(
                Http3SettingsId.ENABLE_CONNECT_PROTOCOL to 1L,
                Http3SettingsId.ENABLE_WEBTRANSPORT to 1L,
            ),
        ).encodeFrame()

    @Test
    fun control_stream_FIN_triggers_h3_closed_critical_stream() {
        runBlocking {
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // Push a valid SETTINGS so the demux is happy with the
            // stream contents, then FIN it cleanly.
            stream.deliverIncoming(controlStreamPrefix())
            stream.deliverIncoming(validSettingsFrame())
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.criticalStreamClosureCode == null) delay(5)
                    true
                }
            assertEquals(true, ok, "demux should latch H3_CLOSED_CRITICAL_STREAM within timeout")
            assertEquals(Http3ErrorCode.CLOSED_CRITICAL_STREAM, demux.criticalStreamClosureCode)
            val reason = demux.criticalStreamClosureReason
            assertNotNull(reason)
            assertEquals(true, reason.contains("CONTROL"), "reason should mention CONTROL stream: $reason")

            demuxScope.cancel()
        }
    }

    @Test
    fun qpack_encoder_FIN_triggers_h3_closed_critical_stream() {
        runBlocking {
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // QPACK encoder stream — we drain its bytes (we don't run a
            // dynamic table) but its FIN is still a critical-stream
            // closure per RFC 9204 §4.2.
            stream.deliverIncoming(qpackEncoderPrefix())
            stream.deliverIncoming(byteArrayOf(0x00, 0x00, 0x00)) // junk QPACK we ignore
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.criticalStreamClosureCode == null) delay(5)
                    true
                }
            assertEquals(true, ok, "demux should latch H3_CLOSED_CRITICAL_STREAM for QPACK encoder")
            assertEquals(Http3ErrorCode.CLOSED_CRITICAL_STREAM, demux.criticalStreamClosureCode)
            val reason = demux.criticalStreamClosureReason
            assertNotNull(reason)
            assertEquals(true, reason.contains("QPACK encoder"), "reason should mention QPACK encoder: $reason")

            demuxScope.cancel()
        }
    }

    @Test
    fun control_stream_settings_violation_triggers_close_with_specific_code() {
        // Control stream's first frame is REQUIRED to be SETTINGS per
        // RFC 9114 §7.2.4.1. A peer that opens with a different frame
        // is H3_MISSING_SETTINGS — the demux's frame reader throws,
        // and our auto-close should propagate the specific code (not
        // just the generic H3_GENERAL_PROTOCOL_ERROR).
        runBlocking {
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // First (and only) frame on the control stream is GOAWAY
            // — illegal: SETTINGS must be first. Reader should throw
            // H3_MISSING_SETTINGS.
            val goawayBody = QuicWriter().also { it.writeVarint(0L) }.toByteArray()
            val goaway =
                QuicWriter()
                    .apply {
                        writeVarint(Http3FrameType.GOAWAY)
                        writeVarint(goawayBody.size.toLong())
                        writeBytes(goawayBody)
                    }.toByteArray()
            stream.deliverIncoming(controlStreamPrefix())
            stream.deliverIncoming(goaway)
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.criticalStreamClosureCode == null) delay(5)
                    true
                }
            assertEquals(true, ok)
            assertEquals(Http3ErrorCode.MISSING_SETTINGS, demux.criticalStreamClosureCode)
            assertNotNull(demux.peerH3ProtocolError)

            demuxScope.cancel()
        }
    }

    @Test
    fun control_stream_unexpected_data_frame_maps_to_frame_unexpected() {
        runBlocking {
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // Valid SETTINGS first, then an illegal DATA frame. RFC
            // 9114 §7.2.1: DATA on the control stream is
            // H3_FRAME_UNEXPECTED.
            val data =
                QuicWriter()
                    .apply {
                        writeVarint(Http3FrameType.DATA)
                        writeVarint(4L)
                        writeBytes(byteArrayOf(1, 2, 3, 4))
                    }.toByteArray()
            stream.deliverIncoming(controlStreamPrefix())
            stream.deliverIncoming(validSettingsFrame())
            stream.deliverIncoming(data)
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.criticalStreamClosureCode == null) delay(5)
                    true
                }
            assertEquals(true, ok)
            assertEquals(Http3ErrorCode.FRAME_UNEXPECTED, demux.criticalStreamClosureCode)

            demuxScope.cancel()
        }
    }

    @Test
    fun closure_code_is_idempotent_first_call_wins() {
        runBlocking {
            val stream = QuicStream(3L, QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL)
            val demuxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val demux = WtPeerStreamDemux(expectedConnectStreamId = 0L, scope = demuxScope)
            demux.process(stream)

            // Inject a protocol violation (sets the code) then FIN
            // (would set CLOSED_CRITICAL_STREAM if we didn't latch).
            // The latch keeps the violation code, not the FIN code.
            val data =
                QuicWriter()
                    .apply {
                        writeVarint(Http3FrameType.DATA)
                        writeVarint(0L)
                    }.toByteArray()
            stream.deliverIncoming(controlStreamPrefix())
            stream.deliverIncoming(validSettingsFrame())
            stream.deliverIncoming(data)
            stream.closeIncoming()

            val ok =
                withTimeoutOrNull(2_000L) {
                    while (demux.criticalStreamClosureCode == null) delay(5)
                    true
                }
            assertEquals(true, ok)
            // Either code is acceptable depending on timing; both
            // routes go through closeConnection(). The point is
            // [criticalStreamClosureCode] doesn't FLIP back and forth
            // — first writer wins.
            val firstCode = demux.criticalStreamClosureCode
            delay(50) // give the FIN-path coroutine time to also try
            assertEquals(firstCode, demux.criticalStreamClosureCode, "closure code should not flip after first observation")

            demuxScope.cancel()
        }
    }
}
