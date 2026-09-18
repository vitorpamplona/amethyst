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
package com.vitorpamplona.contextvm.transfer.stream

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.contextvm.transfer.ProgressEnvelope
import com.vitorpamplona.contextvm.transfer.ProgressToken
import com.vitorpamplona.contextvm.transfer.TransferFrameException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `CVM-41-*`: open-ended streams. */
class OpenStreamTest {
    private val token = ProgressToken.Text("req-123")

    private fun receiver(
        requireAccept: Boolean = false,
        policy: OpenStreamPolicy = OpenStreamPolicy(),
        clock: () -> Long = { 0L },
    ) = OpenStreamReceiver(token, policy, requireAccept, clock)

    // --- happy paths ---

    @Test
    fun `CVM-41-01 delivers contiguous chunks incrementally`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))

        val first = rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "Hello"))
        assertIs<OpenStreamEvent.Delivered>(first)
        assertEquals(listOf("Hello"), first.fragments)

        val second = rx.accept(OpenStreamFrame.chunk(token, 3.0, 1, " world"))
        assertIs<OpenStreamEvent.Delivered>(second)
        assertEquals(listOf(" world"), second.fragments)
    }

    @Test
    fun `CVM-41-02 buffers a gap and releases the run once it closes`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))

        // index 1 arrives first; a gap is not an error while the stream is live.
        assertIs<OpenStreamEvent.Continue>(rx.accept(OpenStreamFrame.chunk(token, 2.0, 1, "b")))
        assertIs<OpenStreamEvent.Continue>(rx.accept(OpenStreamFrame.chunk(token, 3.0, 2, "c")))

        val released = rx.accept(OpenStreamFrame.chunk(token, 4.0, 0, "a"))
        assertIs<OpenStreamEvent.Delivered>(released)
        assertEquals(listOf("a", "b", "c"), released.fragments)
    }

    @Test
    fun `CVM-41-03 a zero-chunk stream is valid`() {
        // close straight after start, lastChunkIndex omitted.
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        val closed = rx.accept(OpenStreamFrame.close(token, 2.0))
        assertIs<OpenStreamEvent.Closed>(closed)
        assertEquals(null, closed.lastChunkIndex)
    }

    @Test
    fun `CVM-41-04 close with a satisfied lastChunkIndex succeeds`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a"))
        rx.accept(OpenStreamFrame.chunk(token, 3.0, 1, "b"))

        val closed = rx.accept(OpenStreamFrame.close(token, 4.0, lastChunkIndex = 1))
        assertIs<OpenStreamEvent.Closed>(closed)
        assertEquals(1L, closed.lastChunkIndex)
    }

    @Test
    fun `CVM-41-05 close without lastChunkIndex succeeds on an open-ended feed`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "tick"))
        assertIs<OpenStreamEvent.Closed>(rx.accept(OpenStreamFrame.close(token, 3.0)))
    }

    @Test
    fun `CVM-41-06 a ping must be answered with a pong carrying the same nonce`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        val event = rx.accept(OpenStreamFrame.ping(token, 2.0, "n-1"))
        assertIs<OpenStreamEvent.Pong>(event)
        assertEquals("n-1", event.nonce)
    }

    @Test
    fun `CVM-41-07 pong progress has no ordering relationship to the ping`() {
        // Pongs are matched by nonce only; each peer numbers its own sequence,
        // so a pong may legitimately carry a lower progress than the ping.
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 10.0))
        rx.markProbeSent("n-1")
        assertIs<OpenStreamEvent.Continue>(rx.accept(OpenStreamFrame.pong(token, 1.0, "n-1")))
        assertFalse(rx.probeExpired(atMs = 1_000_000))
    }

    @Test
    fun `CVM-41-08 keepalive fails the stream when a probe goes unanswered`() {
        var clock = 0L
        val rx = receiver(policy = OpenStreamPolicy(idleTimeoutMs = 30_000, probeTimeoutMs = 30_000)) { clock }
        rx.accept(OpenStreamFrame.start(token, 1.0))

        clock = 30_000
        assertTrue(rx.needsProbe(clock), "idle timeout elapsed, the peer must be probed")

        rx.markProbeSent("n-1", clock)
        clock = 59_000
        assertFalse(rx.probeExpired(clock), "still inside the probe window")

        clock = 60_000
        assertTrue(rx.probeExpired(clock), "probe window elapsed with no matching pong")
    }

    @Test
    fun `CVM-41-09 a bootstrap stream asks the receiver to send accept`() {
        val rx = receiver(requireAccept = true)
        assertIs<OpenStreamEvent.SendAccept>(rx.accept(OpenStreamFrame.start(token, 1.0)))
    }

    @Test
    fun `CVM-41-10 abort is terminal from either peer`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        val aborted = rx.accept(OpenStreamFrame.abort(token, 2.0, "resource exhaustion"))
        assertIs<OpenStreamEvent.Aborted>(aborted)
        assertEquals("resource exhaustion", aborted.reason)
        assertTrue(rx.isTerminal)
    }

    // --- rejections ---

    @Test
    fun `CVM-41-20 rejects a second start on a live stream`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.start(token, 2.0)) }
    }

    @Test
    fun `CVM-41-21 rejects frames after close`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.close(token, 2.0))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 3.0, 0, "late")) }
    }

    @Test
    fun `CVM-41-22 rejects close with a lastChunkIndex that is not satisfied`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a"))
        // Declares index 5 as the bound while only 0 arrived.
        assertFailsWith<OpenStreamException> {
            rx.accept(OpenStreamFrame.close(token, 3.0, lastChunkIndex = 5))
        }
    }

    @Test
    fun `CVM-41-23 rejects close with a bound while a gap remains unresolved`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a"))
        rx.accept(OpenStreamFrame.chunk(token, 3.0, 2, "c")) // index 1 missing
        assertFailsWith<OpenStreamException> {
            rx.accept(OpenStreamFrame.close(token, 4.0, lastChunkIndex = 2))
        }
    }

    @Test
    fun `CVM-41-24 rejects lastChunkIndex on a stream that carried no chunks`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        assertFailsWith<OpenStreamException> {
            rx.accept(OpenStreamFrame.close(token, 2.0, lastChunkIndex = 0))
        }
    }

    @Test
    fun `CVM-41-25 rejects a duplicate chunkIndex`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 1, "b"))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 3.0, 1, "b again")) }
    }

    @Test
    fun `CVM-41-26 rejects a chunkIndex that was already delivered`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a"))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 3.0, 0, "a again")) }
    }

    @Test
    fun `CVM-41-27 rejects a chunk before start`() {
        val rx = receiver()
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 1.0, 0, "a")) }
    }

    @Test
    fun `CVM-41-28 rejects a chunk before accept in a bootstrap stream`() {
        val rx = receiver(requireAccept = true)
        rx.accept(OpenStreamFrame.start(token, 1.0))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a")) }
    }

    @Test
    fun `CVM-41-29 rejects a repeated progress value from the peer`() {
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.accept(OpenStreamFrame.chunk(token, 2.0, 0, "a"))
        assertFailsWith<OpenStreamException> { rx.accept(OpenStreamFrame.chunk(token, 2.0, 1, "b")) }
    }

    @Test
    fun `CVM-41-30 rejects a frame belonging to another stream`() {
        val rx = receiver()
        assertFailsWith<OpenStreamException> {
            rx.accept(OpenStreamFrame.start(ProgressToken.Text("other"), 1.0))
        }
    }

    @Test
    fun `CVM-41-31 rejects an oversized nonce at parse time`() {
        val envelope =
            ProgressEnvelope(
                token = token,
                progress = 1.0,
                cvm =
                    buildJsonObject {
                        put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OPEN_STREAM))
                        put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive(OpenStreamFrame.PING))
                        put(OpenStreamFrame.NONCE, JsonPrimitive("x".repeat(65)))
                    },
            )
        assertFailsWith<TransferFrameException> { OpenStreamFrame.parseOrNull(envelope) }
    }

    @Test
    fun `CVM-41-32 rejects a chunk missing its chunkIndex`() {
        // progress is not a chunk counter, so a chunk without chunkIndex cannot
        // be positioned at all.
        val envelope =
            ProgressEnvelope(
                token = token,
                progress = 1.0,
                cvm =
                    buildJsonObject {
                        put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OPEN_STREAM))
                        put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive(OpenStreamFrame.CHUNK))
                        put(OpenStreamFrame.DATA, JsonPrimitive("a"))
                    },
            )
        assertFailsWith<TransferFrameException> { OpenStreamFrame.parseOrNull(envelope) }
    }

    @Test
    fun `CVM-41-33 ignores a pong for a nonce we never sent`() {
        // Not fatal: failing here would let a third party disrupt the stream by
        // replaying a stale pong. It simply is not liveness evidence.
        val rx = receiver()
        rx.accept(OpenStreamFrame.start(token, 1.0))
        rx.markProbeSent("real")
        assertIs<OpenStreamEvent.Continue>(rx.accept(OpenStreamFrame.pong(token, 2.0, "forged")))
        assertTrue(rx.probeExpired(atMs = 1_000_000), "the real probe is still outstanding")
    }

    @Test
    fun `CVM-41-34 the frame round-trips through a progress notification`() {
        val frame = OpenStreamFrame.chunk(token, 2.0, 7, "payload")
        val decoded = JsonRpcCodec.decode(JsonRpcCodec.encode(frame.envelope.toNotification()))
        val envelope = ProgressEnvelope.parseOrNull(decoded as JsonRpcNotification)
        assertEquals(frame, OpenStreamFrame.parseOrNull(envelope!!))
    }

    @Test
    fun `CVM-41-35 ignores an envelope belonging to another transfer profile`() {
        val envelope =
            ProgressEnvelope(
                token = token,
                progress = 1.0,
                cvm =
                    buildJsonObject {
                        put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OVERSIZED))
                        put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive("start"))
                    },
            )
        assertEquals(null, OpenStreamFrame.parseOrNull(envelope))
    }
}
