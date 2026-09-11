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
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamSequenceState
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `features/agent-text-streams-quic.md`: "A publisher MUST NOT restart `seq`
 * or reuse any prior `seq` value for the same `AgentTextStreamKeyContextV1`,
 * including after reconnect, retry, process restart, or daemon resume. A
 * publisher MAY resume only when it has retained the next unused sequence
 * value. If it cannot prove which sequence value is next, it MUST stop
 * publishing preview records for that start payload."
 *
 * That is a durability requirement, not a bookkeeping one: the sequence number
 * is XORed into the record nonce, so re-using one under the same key context
 * reuses a ChaCha20-Poly1305 (key, nonce) pair — which leaks the XOR of two
 * plaintexts and forfeits authentication for the whole stream. The publisher
 * therefore reserves sequence values durably ahead of use and refuses to
 * publish at all when it cannot prove which value is next.
 */
class AgentTextStreamPublisherTest {
    private val groupId = ByteArray(32) { 0x51 }
    private val streamId = ByteArray(32) { 0x52 }
    private val senderId = ByteArray(32) { 0x53 }
    private val startEventId = ByteArray(32) { 0x54 }
    private val secret = ByteArray(32) { 0x55 }

    private fun context(epoch: Long = 7) =
        AgentTextStreamKeyContextV1(
            groupId = groupId,
            streamId = streamId,
            mlsEpoch = epoch,
            senderId = senderId,
            startEventId = startEventId,
        )

    private fun crypto(epoch: Long = 7) = AgentTextStreamCrypto(secret, context(epoch))

    @Test
    fun theFirstRecordIsSeqOneAndEachRecordAdvancesByOne() =
        runBlocking {
            val publisher = AgentTextStreamPublisher.open(crypto(), InMemoryAgentTextStreamSequenceStore())
            val seqs =
                (1..5).map {
                    publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "chunk $it".encodeToByteArray()).seq
                }
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), seqs)
        }

    @Test
    fun aRestartNeverReplaysASequenceValueItAlreadyHandedOut() =
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            val before = AgentTextStreamPublisher.open(crypto(), store)
            val used = (1..3).map { before.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "a".encodeToByteArray()).seq }

            // Process dies here — no close, no flush.
            val after = AgentTextStreamPublisher.resume(crypto(), store)!!
            val next = after.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "b".encodeToByteArray()).seq

            assertTrue(
                "a resumed publisher must hand out a sequence value strictly above every value used before the restart",
                next > used.max(),
            )
        }

    @Test
    fun reservationIsDurableBeforeTheRecordIsHandedOut() =
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            val publisher = AgentTextStreamPublisher.open(crypto(), store)
            val record = publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "a".encodeToByteArray())

            val persisted = store.load(context().encode())
            assertTrue(
                "the watermark must already cover the record we handed out — persisting after the fact would " +
                    "let a crash re-issue the same nonce",
                persisted!!.nextSeq > record.seq,
            )
        }

    @Test
    fun aPublisherThatCannotProveTheNextSequenceRefusesToPublish() =
        runBlocking {
            // Nothing retained for this key context: the spec says stop, not
            // start over from 1.
            assertNull(
                "resume must fail rather than restart the sequence",
                AgentTextStreamPublisher.resume(crypto(), InMemoryAgentTextStreamSequenceStore()),
            )
        }

    @Test
    fun aStreamThatEndedCannotBeResumed() {
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            val publisher = AgentTextStreamPublisher.open(crypto(), store)
            publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "a".encodeToByteArray())
            publisher.finish()

            assertNull(
                "a finished stream is closed for good — a later preview needs a fresh stream id and start payload",
                AgentTextStreamPublisher.resume(crypto(), store),
            )
            assertThrows(IllegalStateException::class.java) {
                runBlocking { publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "b".encodeToByteArray()) }
            }
        }
    }

    @Test
    fun anAbortClosesTheStreamToo() =
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            val publisher = AgentTextStreamPublisher.open(crypto(), store)
            val abort = publisher.abort()
            assertEquals(AgentTextStreamRecordV1.TYPE_ABORT, abort.recordType)
            assertNull(AgentTextStreamPublisher.resume(crypto(), store))
        }

    @Test
    fun aDifferentStartEventIsADifferentStreamWithItsOwnSequence() =
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            AgentTextStreamPublisher.open(crypto(epoch = 7), store).also {
                it.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "a".encodeToByteArray())
                it.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "b".encodeToByteArray())
            }

            // A new epoch is a different key context, so a fresh sequence is
            // correct here — the (key, nonce) pair cannot collide across it.
            val other = AgentTextStreamPublisher.open(crypto(epoch = 8), store)
            assertEquals(1L, other.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "a".encodeToByteArray()).seq)
        }

    @Test
    fun everyPublishedRecordIsSealedAndOpensBackToItsPlaintext() =
        runBlocking {
            val publisher = AgentTextStreamPublisher.open(crypto(), InMemoryAgentTextStreamSequenceStore())
            val plaintext = "hello from the agent".encodeToByteArray()
            val record = publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, plaintext)

            assertFalse("the wire record must carry ciphertext", record.frame.contentEquals(plaintext))
            assertEquals(plaintext.size + AgentTextStreamRecordV1.AEAD_TAG_LEN, record.frame.size)
            assertTrue(crypto().open(record).frame.contentEquals(plaintext))
        }

    @Test
    fun theTranscriptTracksWhatWasPublishedSoTheFinalMessageCanCarryIt() =
        runBlocking {
            val publisher = AgentTextStreamPublisher.open(crypto(), InMemoryAgentTextStreamSequenceStore())
            publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "one ".encodeToByteArray())
            publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, "two".encodeToByteArray())

            assertEquals(2L, publisher.transcript.chunkCount)
            assertEquals(32, publisher.transcript.hash.size)
        }

    @Test
    fun aFrameOverTheGroupsLimitIsRefusedBeforeItBurnsASequenceValue() {
        runBlocking {
            val store = InMemoryAgentTextStreamSequenceStore()
            val publisher = AgentTextStreamPublisher.open(crypto(), store, maxPlaintextFrameLen = 8)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, ByteArray(9)) }
            }
            assertEquals(
                "a refused frame must not consume a sequence value",
                1L,
                publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, ByteArray(8)).seq,
            )
        }
    }

    @Test
    fun theStoreRoundTripsItsState() =
        runBlocking {
            val store: AgentTextStreamSequenceStore = InMemoryAgentTextStreamSequenceStore()
            val key = context().encode()
            store.save(key, AgentTextStreamSequenceState(nextSeq = 42, closed = false))
            assertEquals(42L, store.load(key)!!.nextSeq)
            assertFalse(store.load(key)!!.closed)
            store.save(key, AgentTextStreamSequenceState(nextSeq = 42, closed = true))
            assertTrue(store.load(key)!!.closed)
        }
}
