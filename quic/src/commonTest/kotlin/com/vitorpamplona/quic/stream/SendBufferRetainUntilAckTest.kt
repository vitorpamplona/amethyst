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
 * Retain-until-ACK semantics for [SendBuffer]. Companion to
 * [SendBufferConcurrencyTest] which exercises the Mutex; this file
 * exercises the new [SendBuffer.markAcked] / [SendBuffer.markLost]
 * paths and the priority order between retransmit and fresh sends.
 */
class SendBufferRetainUntilAckTest {
    @Test
    fun takeChunk_releasesNothingUntilAcked() {
        val buf = SendBuffer()
        buf.enqueue("hello".encodeToByteArray())
        val chunk = buf.takeChunk(1024)
        assertNotNull(chunk)
        assertEquals(0L, chunk.offset)
        assertEquals("hello", chunk.data.decodeToString())
        // Bytes still retained: a fresh takeChunk produces nothing
        // (no pending fresh, no retransmit), but the buffer keeps
        // them around for potential lost-marking.
        assertNull(buf.takeChunk(1024))
        // sentOffset reflects fresh-send high-water; nextOffset is
        // also 5; readableBytes is 0 (nothing more to send).
        assertEquals(5L, buf.sentOffset)
        assertEquals(5L, buf.nextOffset)
        assertEquals(0, buf.readableBytes)
    }

    @Test
    fun markAcked_full_releasesBytes_andDoesNotResend() {
        val buf = SendBuffer()
        buf.enqueue("hello world".encodeToByteArray())
        val first = buf.takeChunk(1024)!!
        assertEquals(0L, first.offset)
        assertEquals("hello world", first.data.decodeToString())
        // ACK the whole range. Buffer drained.
        buf.markAcked(offset = 0L, length = 11L)
        assertNull(buf.takeChunk(1024))
        assertEquals(0, buf.readableBytes)
    }

    @Test
    fun markLost_movesBytesToRetransmitQueue_takeChunkReplaysSameOffset() {
        val buf = SendBuffer()
        buf.enqueue("hello".encodeToByteArray())
        val first = buf.takeChunk(1024)!!
        assertEquals(0L, first.offset)
        // Declare lost.
        buf.markLost(offset = 0L, length = 5L, fin = false)
        // Next takeChunk replays the same offset + bytes.
        val replay = buf.takeChunk(1024)
        assertNotNull(replay)
        assertEquals(0L, replay.offset)
        assertEquals("hello", replay.data.decodeToString())
        assertEquals(5L, buf.sentOffset, "sentOffset is fresh-send high-water — does not advance on retransmit")
    }

    @Test
    fun markLost_partialRange_splitsInFlight() {
        val buf = SendBuffer()
        buf.enqueue("0123456789".encodeToByteArray())
        val first = buf.takeChunk(1024)!! // takes all 10 bytes as one in-flight range
        assertEquals(10, first.data.size)
        // Lose the middle 4 bytes [3, 7).
        buf.markLost(offset = 3L, length = 4L, fin = false)
        // Retransmit replay first.
        val replay = buf.takeChunk(1024)
        assertNotNull(replay)
        assertEquals(3L, replay.offset)
        assertEquals("3456", replay.data.decodeToString())
        // Nothing else to send (fresh exhausted).
        assertNull(buf.takeChunk(1024))
    }

    @Test
    fun markAcked_partialRange_splitsInFlight() {
        val buf = SendBuffer()
        buf.enqueue("0123456789".encodeToByteArray())
        buf.takeChunk(1024)
        // ACK [0, 3) and [7, 10), leaving [3, 7) still in-flight.
        buf.markAcked(offset = 0L, length = 3L)
        buf.markAcked(offset = 7L, length = 3L)
        // Now lose the middle:
        buf.markLost(offset = 3L, length = 4L, fin = false)
        val replay = buf.takeChunk(1024)
        assertNotNull(replay)
        assertEquals(3L, replay.offset)
        assertEquals("3456", replay.data.decodeToString())
    }

    @Test
    fun retransmitDrainsBeforeFreshBytes() {
        val buf = SendBuffer()
        buf.enqueue("AAAA".encodeToByteArray()) // fresh [0,4)
        val a = buf.takeChunk(1024)!!
        buf.markLost(offset = 0L, length = 4L, fin = false) // queued for retransmit
        buf.enqueue("BBBB".encodeToByteArray()) // fresh [4,8)
        // Retransmit comes first.
        val first = buf.takeChunk(1024)!!
        assertEquals(0L, first.offset)
        assertEquals("AAAA", first.data.decodeToString())
        // Then fresh.
        val second = buf.takeChunk(1024)!!
        assertEquals(4L, second.offset)
        assertEquals("BBBB", second.data.decodeToString())
        assertEquals(a.offset, 0L)
    }

    @Test
    fun maxBytesSplits_acrossRetransmitAndFresh() {
        val buf = SendBuffer()
        buf.enqueue("ABCD".encodeToByteArray()) // [0,4)
        buf.takeChunk(1024)
        buf.markLost(offset = 0L, length = 4L, fin = false)
        // Cap at 2 — should pull only 2 bytes from retransmit, leave
        // remaining 2 still in retransmit.
        val first = buf.takeChunk(2)!!
        assertEquals(0L, first.offset)
        assertEquals("AB", first.data.decodeToString())
        // Next call still drains retransmit before any fresh.
        val second = buf.takeChunk(2)!!
        assertEquals(2L, second.offset)
        assertEquals("CD", second.data.decodeToString())
        assertNull(buf.takeChunk(2))
    }

    @Test
    fun fin_carriedOnFinalDataChunk_andRetransmittedOnLoss() {
        val buf = SendBuffer()
        buf.enqueue("hello".encodeToByteArray())
        buf.finish()
        val first = buf.takeChunk(1024)!!
        assertEquals(true, first.fin, "FIN attaches to the last data chunk")
        assertEquals(true, buf.finSent)
        // Lose the chunk including FIN. Retransmit must re-set FIN.
        buf.markLost(offset = 0L, length = 5L, fin = true)
        assertEquals(false, buf.finSent, "lost FIN clears finSent so it gets re-emitted")
        val replay = buf.takeChunk(1024)!!
        assertEquals(true, replay.fin)
        assertEquals(true, buf.finSent)
    }

    @Test
    fun fin_only_emittedAfterDataDrained() {
        val buf = SendBuffer()
        buf.finish() // FIN with no data
        val chunk = buf.takeChunk(1024)
        assertNotNull(chunk)
        assertEquals(0L, chunk.offset)
        assertEquals(0, chunk.data.size)
        assertEquals(true, chunk.fin)
        assertNull(buf.takeChunk(1024), "no further chunks after FIN-only sent")
    }

    @Test
    fun fin_only_lostAndRetransmits() {
        val buf = SendBuffer()
        buf.finish()
        buf.takeChunk(1024)
        // Mark FIN-only chunk as lost (length=0, fin=true).
        buf.markLost(offset = 0L, length = 0L, fin = true)
        assertEquals(false, buf.finSent)
        val replay = buf.takeChunk(1024)
        assertNotNull(replay)
        assertEquals(true, replay.fin)
    }

    @Test
    fun markAcked_advancesFlushedFloor_releasesMemory() {
        val buf = SendBuffer()
        buf.enqueue(ByteArray(1000) { it.toByte() })
        buf.takeChunk(1000)
        // Before ACK: nothing released.
        // After ACK [0, 1000): floor advances and storage shifts.
        buf.markAcked(offset = 0L, length = 1000L)
        // Re-use of buffer with new bytes still works.
        buf.enqueue("after".encodeToByteArray())
        val next = buf.takeChunk(1024)!!
        assertEquals(1000L, next.offset, "next fresh send picks up where we left off")
        assertEquals("after", next.data.decodeToString())
    }

    @Test
    fun markAcked_outOfOrder_preventsFloorAdvance() {
        val buf = SendBuffer()
        buf.enqueue("AAAABBBB".encodeToByteArray())
        // Send as two halves.
        buf.takeChunk(4) // [0, 4)
        buf.takeChunk(4) // [4, 8)
        // ACK only the second half — the first half is still in-flight,
        // so floor cannot advance.
        buf.markAcked(offset = 4L, length = 4L)
        // Lost retransmit of the first half still works.
        buf.markLost(offset = 0L, length = 4L, fin = false)
        val replay = buf.takeChunk(4)!!
        assertEquals(0L, replay.offset)
        assertEquals("AAAA", replay.data.decodeToString())
    }

    @Test
    fun markLost_belowFlushedFloor_isNoop() {
        // ACK'd-and-released bytes can still receive a stale loss
        // notification (race between an ACK and a lost-token dispatch
        // for the same range). Defensive: drop silently.
        val buf = SendBuffer()
        buf.enqueue("hello".encodeToByteArray())
        buf.takeChunk(1024)
        buf.markAcked(offset = 0L, length = 5L)
        buf.markLost(offset = 0L, length = 5L, fin = false) // bytes already gone
        assertEquals(0, buf.readableBytes)
        assertNull(buf.takeChunk(1024))
    }

    @Test
    fun finAcked_latchesTrueOnce() {
        val buf = SendBuffer()
        buf.enqueue("x".encodeToByteArray())
        buf.finish()
        buf.takeChunk(1024) // sends "x" + FIN
        assertEquals(false, buf.finAcked)
        buf.markAcked(offset = 0L, length = 1L)
        assertTrue(buf.finAcked, "ACK of the FIN-bearing range latches finAcked")
    }

    @Test
    fun readableBytes_reflectsRetransmitPlusFresh() {
        val buf = SendBuffer()
        buf.enqueue("AAAA".encodeToByteArray())
        buf.takeChunk(1024)
        buf.markLost(offset = 0L, length = 4L, fin = false)
        buf.enqueue("BBBB".encodeToByteArray())
        assertEquals(8, buf.readableBytes, "4 retransmit + 4 fresh")
        buf.takeChunk(1024) // drain retransmit
        assertEquals(4, buf.readableBytes, "fresh remains")
        buf.takeChunk(1024) // drain fresh
        assertEquals(0, buf.readableBytes)
    }

    @Test
    fun multipleSendsWithinSingleEnqueue_acksIndependently() {
        // Common pattern: enqueue once, writer sends in MTU-sized
        // chunks, peer ACKs each individually.
        val buf = SendBuffer()
        buf.enqueue(ByteArray(300) { it.toByte() })
        val a = buf.takeChunk(100)!! // [0, 100)
        val b = buf.takeChunk(100)!! // [100, 200)
        val c = buf.takeChunk(100)!! // [200, 300)
        assertEquals(0L, a.offset)
        assertEquals(100L, b.offset)
        assertEquals(200L, c.offset)
        // ACK middle, lose the others.
        buf.markAcked(offset = 100L, length = 100L)
        buf.markLost(offset = 0L, length = 100L, fin = false)
        buf.markLost(offset = 200L, length = 100L, fin = false)
        val replay1 = buf.takeChunk(1024)!!
        val replay2 = buf.takeChunk(1024)!!
        // Retransmit FIFO order: lost-first wins.
        assertEquals(0L, replay1.offset)
        assertEquals(200L, replay2.offset)
        assertNull(buf.takeChunk(1024))
    }
}
