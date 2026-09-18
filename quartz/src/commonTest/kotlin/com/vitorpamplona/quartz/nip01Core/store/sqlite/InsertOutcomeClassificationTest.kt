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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.RejectionReason
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `batchInsert` must name *why* a row didn't land, because the relay turns
 * that reason into the NIP-01 answer: [RejectionReason.DUPLICATE] and
 * [RejectionReason.SUPERSEDED] are `OK true` ("already covered"), everything
 * else is `OK false`, which clients retry.
 *
 * This suite pins the classification **independently of the SQLite driver's
 * exception text**. The bundled JVM driver raises
 * `UNIQUE constraint failed: event_headers.id`; Android's raises an
 * `android.database.SQLException` whose message is `null`. Reading the text
 * was therefore enough on one target and wrong on the other — a duplicate
 * came back as `error: SQLException`, an `OK false` the client re-offers
 * forever. Every case below runs on both targets and so fails on either if
 * the classifier ever goes back to trusting a message.
 */
class InsertOutcomeClassificationTest : BaseDBTest() {
    val signer = NostrSignerSync()

    private fun assertRejected(
        expectedReason: String,
        outcome: IEventStore.InsertOutcome,
    ) {
        assertTrue(outcome is IEventStore.InsertOutcome.Rejected, "expected Rejected, got $outcome")
        assertEquals(expectedReason, outcome.reason)
    }

    @Test
    fun duplicateIdIsRejectedAsDuplicate() =
        forEachDB { db ->
            val event = signer.sign(TextNoteEvent.build("hello", createdAt = TimeUtils.now()))

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(event))[0])
            assertRejected(RejectionReason.DUPLICATE, db.batchInsert(listOf<Event>(event))[0])
        }

    @Test
    fun reofferingAStoredReplaceableIsRejectedAsDuplicate() =
        forEachDB { db ->
            // Byte-for-byte the stored version, so it violates the id index *and*
            // replaceable_idx — and which one SQLite reports first is the driver's
            // choice. "Already have this event" is the answer that holds on all of
            // them.
            val event = signer.sign(MetadataEvent.createNew("Vitor", createdAt = TimeUtils.now()))

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(event))[0])
            assertRejected(RejectionReason.DUPLICATE, db.batchInsert(listOf<Event>(event))[0])
        }

    @Test
    fun olderReplaceableIsRejectedAsSuperseded() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val older = signer.sign(MetadataEvent.createNew("Vitor 1", createdAt = time))
            val newer = signer.sign(MetadataEvent.createNew("Vitor 2", createdAt = time + 1))

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(newer))[0])
            assertRejected(RejectionReason.SUPERSEDED, db.batchInsert(listOf<Event>(older))[0])
        }

    @Test
    fun sameSecondReplaceableTieLoserIsRejectedAsSuperseded() =
        forEachDB { db ->
            val time = TimeUtils.now()
            // NIP-01 breaks a created_at tie by lowest id, so the winner is
            // decided by sorting, not by insertion order.
            val (winner, loser) =
                listOf(
                    signer.sign(MetadataEvent.createNew("Vitor A", createdAt = time)),
                    signer.sign(MetadataEvent.createNew("Vitor B", createdAt = time)),
                ).sortedBy { it.id }

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(winner))[0])
            assertRejected(RejectionReason.SUPERSEDED, db.batchInsert(listOf<Event>(loser))[0])
        }

    @Test
    fun olderAddressableIsRejectedAsSuperseded() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val older = signer.sign(LongTextNoteEvent.build("v1", "title", dTag = "blog", createdAt = time))
            val newer = signer.sign(LongTextNoteEvent.build("v2", "title", dTag = "blog", createdAt = time + 1))

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(newer))[0])
            assertRejected(RejectionReason.SUPERSEDED, db.batchInsert(listOf<Event>(older))[0])
        }

    @Test
    fun aNewerVersionStillLandsAtAnOccupiedCoordinate() =
        forEachDB { db ->
            // The guard against the classifier over-claiming: a coordinate is
            // occupied here too, but this version wins, so nothing is rejected.
            val time = TimeUtils.now()
            val older = signer.sign(MetadataEvent.createNew("Vitor 1", createdAt = time))
            val newer = signer.sign(MetadataEvent.createNew("Vitor 2", createdAt = time + 1))

            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(older))[0])
            assertEquals(IEventStore.InsertOutcome.Accepted, db.batchInsert(listOf<Event>(newer))[0])
        }
}
