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
package com.vitorpamplona.quic.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Best-effort SendBuffer: lost ranges are dropped instead of being
 * re-queued for retransmit. The moq-lite group-stream case — Opus
 * audio frames arriving 200 ms late are worse than useless. We don't
 * want to bound this behavior with congestion control because the
 * audio workload is too small to warrant a CC implementation, but we
 * also don't want STREAM retransmit to spam stale audio onto a lossy
 * uplink.
 */
class SendBufferBestEffortTest {
    @Test
    fun normalBuffer_lostRangeIsRequeued() {
        // Sanity baseline: the default buffer (bestEffort = false)
        // re-queues lost bytes onto the retransmit FIFO so the next
        // takeChunk re-emits them.
        val buf = SendBuffer()
        buf.enqueue(byteArrayOf(1, 2, 3, 4, 5))
        val first = buf.takeChunk(maxBytes = 5)
        assertNotNull(first)
        assertEquals(5, first.data.size)

        buf.markLost(offset = 0L, length = 5L, fin = false)
        val replay = buf.takeChunk(maxBytes = 5)
        assertNotNull(replay, "lost bytes must re-emit on a reliable buffer")
        assertEquals(0L, replay.offset)
        assertEquals(5, replay.data.size)
    }

    @Test
    fun bestEffort_lostRangeIsDropped() {
        // bestEffort buffer: lost bytes vanish — no retransmit, nothing
        // for takeChunk to surface, the underlying byte storage
        // releases as if the bytes had been ACK'd.
        val buf = SendBuffer(bestEffort = true)
        buf.enqueue(byteArrayOf(10, 20, 30, 40))
        val sent = buf.takeChunk(maxBytes = 4)
        assertNotNull(sent)
        assertEquals(4, sent.data.size)
        assertEquals(4L, buf.sentOffset)

        buf.markLost(offset = 0L, length = 4L, fin = false)

        // No fresh bytes, no retransmit-queued bytes — takeChunk
        // returns null.
        assertNull(buf.takeChunk(maxBytes = 4), "best-effort: lost bytes are not re-emitted")
        // No more readable bytes pending either.
        assertEquals(0, buf.readableBytes)
    }

    @Test
    fun bestEffort_lostFin_isDropped_finSentRemainsTrue() {
        // FIN handling: in best-effort mode we don't retransmit FIN
        // either. _finSent stays true so the writer doesn't try to
        // re-emit the FIN-only chunk on the next drain. The peer's
        // stream may stay open from QUIC's view forever — that's the
        // cost of best-effort, and moq-lite's per-stream timeouts
        // handle it at the application layer.
        val buf = SendBuffer(bestEffort = true)
        buf.enqueue(byteArrayOf(1, 2, 3))
        buf.finish()
        val withFin = buf.takeChunk(maxBytes = 3)
        assertNotNull(withFin)
        assertTrue(withFin.fin, "FIN attached to last chunk")
        assertTrue(buf.finSent)

        buf.markLost(offset = 0L, length = 3L, fin = true)
        // _finSent should NOT flip back to false — best-effort means
        // we don't retransmit the FIN.
        assertTrue(buf.finSent, "best-effort: lost FIN is not resurrected for retransmit")
        // No retransmit pending.
        assertNull(buf.takeChunk(maxBytes = 8))
    }

    @Test
    fun bestEffort_partialOverlapLoss_dropsPartialButKeepsRest() {
        // Edge case: loss notification covers a partial overlap of an
        // in-flight range. The non-overlapped portion stays in
        // inFlight (so a later ACK / loss for that portion behaves
        // correctly); the overlapped piece is dropped.
        val buf = SendBuffer(bestEffort = true)
        buf.enqueue(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        val sent = buf.takeChunk(maxBytes = 10)
        assertNotNull(sent)

        // Lose the middle 4 bytes [3, 7).
        buf.markLost(offset = 3L, length = 4L, fin = false)

        // The dropped range vanishes; the kept-pieces around it stay
        // in inFlight. takeChunk returns null because there's no
        // retransmit queue and no fresh bytes.
        assertNull(buf.takeChunk(maxBytes = 10))

        // ACK the kept-pieces explicitly to verify they're tracked
        // correctly. Acking [0,3) should latch flushedFloor to 3
        // (since [3,7) is dropped — gone) only after the kept-piece
        // [7,10) is also released.
        buf.markAcked(offset = 0L, length = 3L)
        // The dropped [3,7) range was already "released" by the
        // best-effort drop, so [0,3) ACK + dropped [3,7) compact the
        // floor to 7.
        buf.markAcked(offset = 7L, length = 3L)
        // Now everything is gone.
        assertNull(buf.takeChunk(maxBytes = 10))
    }

    @Test
    fun bestEffort_repeatedLossIsIdempotent() {
        // Defensive: a stale loss notification arriving after the
        // bytes are already dropped should not throw or double-count.
        val buf = SendBuffer(bestEffort = true)
        buf.enqueue(byteArrayOf(1, 2, 3))
        buf.takeChunk(maxBytes = 3)
        buf.markLost(offset = 0L, length = 3L, fin = false)
        buf.markLost(offset = 0L, length = 3L, fin = false) // already gone
        assertNull(buf.takeChunk(maxBytes = 3))
    }

    @Test
    fun bestEffort_ackPathStillWorksNormally() {
        // bestEffort only changes loss behavior; ACK semantics are
        // unchanged. Sanity check that we didn't regress markAcked.
        val buf = SendBuffer(bestEffort = true)
        buf.enqueue(byteArrayOf(1, 2, 3, 4))
        val sent = buf.takeChunk(maxBytes = 4)
        assertNotNull(sent)
        buf.markAcked(offset = 0L, length = 4L)
        // Same as a reliable buffer: acked bytes release.
        assertNull(buf.takeChunk(maxBytes = 4))
    }
}
