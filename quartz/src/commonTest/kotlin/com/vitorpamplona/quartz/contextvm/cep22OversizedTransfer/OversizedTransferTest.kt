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
package com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.contextvm.transfer.ProgressEnvelope
import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.contextvm.transfer.TransferFrameException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `CVM-22-*`: bounded oversized payload transfer.
 *
 * The CEP is written almost entirely as failure conditions, so most of this is
 * negative. A suite of happy paths would prove nothing.
 */
class OversizedTransferTest {
    private val token = ProgressToken.Text("req-123")

    private val payload =
        JsonRpcCodec.encode(
            JsonRpcSuccess(
                JsonRpcId.Num(1),
                buildJsonObject { put("text", JsonPrimitive("a".repeat(200))) },
            ),
        )

    private fun sender(chunkChars: Int = 64) = OversizedTransferSender(chunkChars)

    private fun receiver(
        limits: OversizedLimits = OversizedLimits(),
        requireAccept: Boolean = false,
    ) = OversizedTransferReceiver(token, limits, requireAccept)

    // --- happy paths ---

    @Test
    fun `CVM-22-01 round-trips a fragmented message through the frames`() {
        val frames = sender().frame(token, payload)
        val rx = receiver()

        var completed: OversizedProgressResult.Completed? = null
        frames.forEach { frame ->
            val result = rx.accept(frame)
            if (result is OversizedProgressResult.Completed) completed = result
        }

        assertEquals(payload, completed?.raw)
        assertEquals(JsonRpcCodec.decode(payload), completed?.message)
    }

    @Test
    fun `CVM-22-02 assembles out-of-order chunks by progress, not arrival order`() {
        // Relays may reorder. `progress` is the canonical assembly index.
        val frames = sender().frame(token, payload)
        val start = frames.first()
        val end = frames.last()
        val chunks = frames.drop(1).dropLast(1).reversed()

        val rx = receiver()
        rx.accept(start)
        chunks.forEach { rx.accept(it) }
        val result = rx.accept(end)

        assertIs<OversizedProgressResult.Completed>(result)
        assertEquals(payload, result.raw)
    }

    @Test
    fun `CVM-22-03 surfaces nothing before validation succeeds`() {
        val frames = sender().frame(token, payload)
        val rx = receiver()

        frames.dropLast(1).forEach {
            assertTrue(
                rx.accept(it) is OversizedProgressResult.Continue,
                "no payload may be surfaced before end validates",
            )
        }
    }

    @Test
    fun `CVM-22-04 a bootstrap transfer asks the receiver to send accept`() {
        val frames = sender().frame(token, payload)
        val rx = receiver(requireAccept = true)
        assertIs<OversizedProgressResult.SendAccept>(rx.accept(frames.first()))
    }

    @Test
    fun `CVM-22-05 abort is terminal`() {
        val rx = receiver()
        rx.accept(sender().frame(token, payload).first())

        val aborted = rx.accept(OversizedFrame.abort(token, 99.0, "peer gave up"))
        assertIs<OversizedProgressResult.Aborted>(aborted)
        assertEquals("peer gave up", aborted.reason)
        assertTrue(rx.isTerminal)

        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.chunk(token, 100.0, "x"))
        }
    }

    @Test
    fun `CVM-22-06 the sender never splits a surrogate pair`() {
        // Each emoji is a surrogate pair. Splitting one would still produce two
        // valid JSON strings, so only the reassembled bytes catch it.
        val emoji = "🚀".repeat(40)
        val text = JsonRpcCodec.encode(JsonRpcSuccess(JsonRpcId.Num(1), JsonPrimitive(emoji)))

        val frames = sender(chunkChars = 9).frame(token, text)
        frames.filterIsInstance<OversizedFrame.Chunk>().forEach { chunk ->
            assertTrue(
                chunk.data.isEmpty() || !chunk.data.last().isHighSurrogate(),
                "a fragment must not end on an unpaired high surrogate",
            )
        }

        val rx = receiver()
        val result = frames.map { rx.accept(it) }.last()
        assertIs<OversizedProgressResult.Completed>(result)
        assertEquals(text, result.raw)
    }

    // --- rejections ---

    @Test
    fun `CVM-22-10 rejects a digest mismatch`() {
        val frames = sender().frame(token, payload).toMutableList()
        val start = frames.first() as OversizedFrame.Start
        frames[0] =
            OversizedFrame.start(
                token,
                start.progress,
                OversizedFrame.DIGEST_PREFIX_SHA256 + "00".repeat(32),
                start.totalBytes,
                start.totalChunks,
            )

        val rx = receiver()
        assertFailsWith<OversizedTransferException> { frames.forEach { rx.accept(it) } }
    }

    @Test
    fun `CVM-22-11 rejects a totalBytes mismatch`() {
        val frames = sender().frame(token, payload).toMutableList()
        val start = frames.first() as OversizedFrame.Start
        frames[0] =
            OversizedFrame.start(token, start.progress, start.digest, start.totalBytes + 1, start.totalChunks)

        val rx = receiver()
        assertFailsWith<OversizedTransferException> { frames.forEach { rx.accept(it) } }
    }

    @Test
    fun `CVM-22-12 rejects a totalChunks mismatch`() {
        val frames = sender().frame(token, payload).toMutableList()
        val start = frames.first() as OversizedFrame.Start
        frames[0] =
            OversizedFrame.start(token, start.progress, start.digest, start.totalBytes, start.totalChunks + 1)

        val rx = receiver()
        assertFailsWith<OversizedTransferException> { frames.forEach { rx.accept(it) } }
    }

    @Test
    fun `CVM-22-13 rejects a chunk before accept in a bootstrap transfer`() {
        val frames = sender().frame(token, payload)
        val rx = receiver(requireAccept = true)
        rx.accept(frames.first())
        assertFailsWith<OversizedTransferException> { rx.accept(frames[1]) }
    }

    @Test
    fun `CVM-22-14 rejects non-monotonic progress`() {
        val rx = receiver()
        rx.accept(OversizedFrame.start(token, 5.0, digestOf(""), 0, 0))
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.chunk(token, 4.0, "x"))
        }
    }

    @Test
    fun `CVM-22-15 rejects end with unresolved gaps`() {
        val frames = sender().frame(token, payload)
        val rx = receiver()
        rx.accept(frames.first())
        // Deliver every chunk but the last, then end.
        frames.drop(1).dropLast(2).forEach { rx.accept(it) }
        assertFailsWith<OversizedTransferException> { rx.accept(frames.last()) }
    }

    @Test
    fun `CVM-22-16 rejects an unknown completionMode at parse time`() {
        val envelope =
            ProgressEnvelope(
                token = token,
                progress = 1.0,
                cvm =
                    buildJsonObject {
                        put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OVERSIZED))
                        put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive(OversizedFrame.START))
                        put(OversizedFrame.COMPLETION_MODE, JsonPrimitive("stream-someday"))
                        put(OversizedFrame.DIGEST, JsonPrimitive(digestOf("")))
                        put(OversizedFrame.TOTAL_BYTES, JsonPrimitive(0))
                        put(OversizedFrame.TOTAL_CHUNKS, JsonPrimitive(0))
                    },
            )
        assertFailsWith<TransferFrameException> { OversizedFrame.parseOrNull(envelope) }
    }

    @Test
    fun `CVM-22-17 rejects declared totals over local policy at start`() {
        val rx = receiver(limits = OversizedLimits(maxTotalBytes = 10, maxTotalChunks = 2))
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.start(token, 1.0, digestOf(""), 1_000_000, 1))
        }
    }

    @Test
    fun `CVM-22-18 rejects a chunk arriving before start`() {
        val rx = receiver()
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.chunk(token, 1.0, "x"))
        }
    }

    @Test
    fun `CVM-22-19 rejects a second start on the same transfer`() {
        val rx = receiver()
        rx.accept(OversizedFrame.start(token, 1.0, digestOf(""), 0, 0))
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.start(token, 2.0, digestOf(""), 0, 0))
        }
    }

    @Test
    fun `CVM-22-20 rejects a frame belonging to another transfer`() {
        val rx = receiver()
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.start(ProgressToken.Text("other"), 1.0, digestOf(""), 0, 0))
        }
    }

    @Test
    fun `CVM-22-21 rejects an unsupported digest algorithm`() {
        val rx = receiver()
        assertFailsWith<OversizedTransferException> {
            rx.accept(OversizedFrame.start(token, 1.0, "md5:" + "00".repeat(16), 0, 0))
        }
    }

    @Test
    fun `CVM-22-22 rejects a duplicate chunk at the same progress`() {
        val rx = receiver()
        rx.accept(OversizedFrame.start(token, 1.0, digestOf("ab"), 2, 2))
        rx.accept(OversizedFrame.chunk(token, 2.0, "a"))
        assertFailsWith<OversizedTransferException> {
            // Same progress value, so this cannot be a distinct fragment.
            rx.accept(OversizedFrame.chunk(token, 2.0, "b"))
        }
    }

    @Test
    fun `CVM-22-23 the envelope round-trips through a progress notification`() {
        val frame = sender().frame(token, payload).first()
        val notification = frame.envelope.toNotification()
        val decoded = JsonRpcCodec.decode(JsonRpcCodec.encode(notification))

        val envelope = ProgressEnvelope.parseOrNull(decoded as com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification)
        assertEquals(frame, OversizedFrame.parseOrNull(envelope!!))
    }

    @Test
    fun `CVM-22-24 ignores an envelope belonging to another transfer profile`() {
        val envelope =
            ProgressEnvelope(
                token = token,
                progress = 1.0,
                cvm =
                    buildJsonObject {
                        put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OPEN_STREAM))
                        put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive("start"))
                    },
            )
        assertEquals(null, OversizedFrame.parseOrNull(envelope))
    }

    private fun digestOf(text: String) =
        OversizedFrame.DIGEST_PREFIX_SHA256 +
            com.vitorpamplona.quartz.utils.sha256
                .sha256(text.encodeToByteArray())
                .let { bytes -> bytes.joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') } }
}
