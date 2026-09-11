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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamCrypto
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamKeyContextV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamPublisher
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamSubscriber
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.PreviewStatus
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.RecordOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The receive half of `transports/quic.md`, which is where a preview either
 * stays honest or quietly stops being one.
 *
 * The rules it has to hold: `seq` is accepted at most once and never folded
 * out of order; a replayed record — which a broker WILL send after a
 * reconnect, from the start of its replay window — is discarded silently and
 * is never stream-fatal; a gap that cannot be backfilled makes the preview
 * unverifiable, because the transcript hash can no longer be completed and
 * therefore can no longer be checked against the final MLS message.
 */
class AgentTextStreamSubscriberTest {
    private val streamId = ByteArray(32) { 0x31 }
    private val startEventId = ByteArray(32) { 0x32 }
    private val secret = ByteArray(32) { 0x33 }

    private val crypto =
        AgentTextStreamCrypto(
            secret,
            AgentTextStreamKeyContextV1(
                groupId = ByteArray(32) { 0x34 },
                streamId = streamId,
                mlsEpoch = 11,
                senderId = ByteArray(32) { 0x35 },
                startEventId = startEventId,
            ),
        )

    /** Sealed records 1..n of a stream, as the publisher would have sent them. */
    private fun sealedRecords(vararg texts: String): List<AgentTextStreamRecordV1> =
        runBlocking {
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            texts.map { publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, it.encodeToByteArray()) }
        }

    private fun subscriber() = AgentTextStreamSubscriber(crypto)

    @Test
    fun textDeltasConcatenateIntoTheProvisionalPreview() {
        val sub = subscriber()
        sealedRecords("the ", "quick ", "brown fox").forEach {
            assertEquals(RecordOutcome.Accepted, sub.accept(it))
        }
        assertEquals("the quick brown fox", sub.previewText)
        assertEquals(PreviewStatus.LIVE, sub.status)
        assertEquals(3L, sub.transcript.chunkCount)
    }

    @Test
    fun aReplayedRecordIsDiscardedSilentlyAndChangesNothing() {
        val records = sealedRecords("a", "b")
        val sub = subscriber()
        records.forEach { sub.accept(it) }
        val hashBefore = sub.transcript.hash.toList()

        // A broker replays its backlog from the start of the window on
        // reconnect. Every one of these is at or below the high-water mark.
        records.forEach {
            assertEquals(
                "a replayed record is never stream-fatal",
                RecordOutcome.Replay,
                sub.accept(it),
            )
        }

        assertEquals("ab", sub.previewText)
        assertEquals(2L, sub.transcript.chunkCount)
        assertEquals(hashBefore, sub.transcript.hash.toList())
        assertEquals(PreviewStatus.LIVE, sub.status)
    }

    @Test
    fun aGapMakesThePreviewUnverifiableRatherThanWrong() {
        val records = sealedRecords("one", "two", "three")
        val sub = subscriber()
        sub.accept(records[0])

        // seq 2 never arrives. Folding seq 3 anyway would produce a transcript
        // hash that cannot match the publisher's, so the preview is marked
        // instead — the final MLS message is still authoritative.
        assertEquals(RecordOutcome.Gap, sub.accept(records[2]))
        assertEquals(PreviewStatus.UNVERIFIABLE, sub.status)
        assertEquals("one", sub.previewText)
        assertEquals(1L, sub.transcript.chunkCount)
    }

    @Test
    fun aGapCanBeBackfilledBeforeItIsFatal() {
        val records = sealedRecords("one", "two", "three")
        val sub = subscriber()
        sub.accept(records[0])
        sub.accept(records[2]) // gap
        assertEquals(PreviewStatus.UNVERIFIABLE, sub.status)

        // The binding says a receiver backfills the missing records from a
        // replay source; a stream that completes is verifiable again.
        assertEquals(RecordOutcome.Accepted, sub.accept(records[1]))
        assertEquals(RecordOutcome.Accepted, sub.accept(records[2]))
        assertEquals("onetwothree", sub.previewText)
        assertEquals(PreviewStatus.LIVE, sub.status)
    }

    @Test
    fun aRecordThatDoesNotOpenIsRejectedWithoutTouchingTheStream() {
        val records = sealedRecords("one", "two")
        val sub = subscriber()
        sub.accept(records[0])

        val tampered = records[1].frame.copyOf().also { it[0] = (it[0].toInt() xor 0xff).toByte() }
        assertEquals(RecordOutcome.Undecryptable, sub.accept(records[1].copyWithFrame(tampered)))

        assertEquals("one", sub.previewText)
        assertEquals(1L, sub.transcript.chunkCount)
        assertEquals(
            "a record that fails its AEAD never advances the high-water mark",
            1L,
            sub.highWaterMark,
        )
    }

    @Test
    fun aRecordForAnotherStreamIsRefused() {
        val sub = subscriber()
        val alien = AgentTextStreamRecordV1(ByteArray(32) { 0x66 }, seq = 1, recordType = 1, frame = ByteArray(32))
        assertEquals(RecordOutcome.WrongStream, sub.accept(alien))
        assertEquals(0L, sub.highWaterMark)
    }

    @Test
    fun onlyTextDeltasAppendToThePreview() {
        val publisher = runBlocking { AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore()) }
        val sub = subscriber()
        runBlocking {
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "answer".encodeToByteArray()))
            // Progress and status are agent chrome. The spec is explicit that
            // they MUST NOT reach preview text, notifications, indexes or
            // automation input.
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_PROGRESS_DELTA, "reading files".encodeToByteArray()))
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_STATUS, "thinking".encodeToByteArray()))
        }
        assertEquals("answer", sub.previewText)
        assertEquals("thinking", sub.latestStatus)
        assertEquals("reading files", sub.latestProgress)
        // Every accepted record still folds into the transcript — the hash
        // covers the stream, not just the text.
        assertEquals(3L, sub.transcript.chunkCount)
    }

    @Test
    fun aCheckpointReplacesThePreviewAndLaterDeltasAppendToIt() {
        val publisher = runBlocking { AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore()) }
        val sub = subscriber()
        runBlocking {
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "draft".encodeToByteArray()))
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_CHECKPOINT, "the whole answer".encodeToByteArray()))
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, " so far".encodeToByteArray()))
        }
        assertEquals("the whole answer so far", sub.previewText)
    }

    @Test
    fun anAbortCancelsThePreviewWithoutProducingText() {
        val publisher = runBlocking { AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore()) }
        val sub = subscriber()
        runBlocking {
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "half an ans".encodeToByteArray()))
            sub.accept(publisher.publish(AgentTextStreamRecordV1.TYPE_ABORT, ByteArray(0)))
        }
        assertEquals(PreviewStatus.ABORTED, sub.status)
        assertTrue(sub.previewText.isEmpty())
    }

    @Test
    fun theFinalMessageIsWhatDecidesWhetherWeSawTheRealStream() {
        val records = sealedRecords("a", "b", "c")
        val sub = subscriber()
        records.forEach { sub.accept(it) }

        val publisherSide = runBlocking { AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore()) }
        runBlocking { listOf("a", "b", "c").forEach { publisherSide.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, it.encodeToByteArray()) } }

        assertTrue(
            sub.matchesFinal(publisherSide.transcript.hash, publisherSide.transcript.chunkCount),
        )
        assertFalse(
            "a hash that disagrees means we saw a different stream, even though every record opened",
            sub.matchesFinal(ByteArray(32), 3),
        )
        assertFalse(
            "the same hash with a different count is still a different stream",
            sub.matchesFinal(publisherSide.transcript.hash, 2),
        )
    }

    @Test
    fun anUnverifiablePreviewNeverClaimsToMatchAFinal() {
        val records = sealedRecords("a", "b", "c")
        val sub = subscriber()
        sub.accept(records[0])
        sub.accept(records[2])
        assertEquals(PreviewStatus.UNVERIFIABLE, sub.status)
        // Even a hash that happens to agree cannot rehabilitate it: the
        // receiver knows it is missing a record it never folded.
        assertFalse(sub.matchesFinal(sub.transcript.hash, sub.transcript.chunkCount))
    }
}
