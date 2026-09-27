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
package com.vitorpamplona.quartz.nip01Core.relay.server.backend

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The NIP-77 snapshot cache: kept across writes that cannot change its set, dropped by those that can. */
class LiveEventStoreSnapshotCacheTest {
    /** Counts how often a snapshot is actually scanned out of the store. */
    private class Counting(
        private val inner: IEventStore,
    ) : IEventStore by inner {
        var scans = 0

        override suspend fun snapshotIdsForNegentropy(
            filters: List<Filter>,
            maxEntries: Int?,
            onProgress: ((collected: Int) -> Unit)?,
        ): List<IdAndTime> {
            scans++
            return inner.snapshotIdsForNegentropy(filters, maxEntries, onProgress)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val store = Counting(EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(indexEventsByPubkeyAlone = true)))
    private val live = LiveEventStore(store, IngestQueue(store, scope.coroutineContext))
    private val alice = NostrSignerSync()
    private var clock = 1_700_000_000L

    private val notes = listOf(Filter(kinds = listOf(1)))
    private val reactions = listOf(Filter(kinds = listOf(7)))

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun event(
        kind: Int,
        tags: Array<Array<String>> = emptyArray(),
    ) = alice.sign<Event>(clock++, kind, tags, "")

    private fun publish(event: Event) =
        runBlocking {
            val done = CompletableDeferred<IEventStore.InsertOutcome>()
            live.submit(event) { done.complete(it) }
            assertEquals(IEventStore.InsertOutcome.Accepted, done.await())
        }

    private fun open(filters: List<Filter>) = runBlocking { live.sealedNegentropyStorage(filters, maxEntries = 1_000) }

    @Test
    fun twoFiltersInTurnAreEachScannedOnce() {
        publish(event(1))
        repeat(3) {
            open(notes)
            open(reactions)
        }
        assertEquals(2, store.scans)
    }

    @Test
    fun aWriteOutsideTheSetKeepsTheSnapshot() {
        open(notes)
        publish(event(7))
        open(notes)
        assertEquals(1, store.scans)
    }

    @Test
    fun aWriteInsideTheSetRebuildsIt() {
        open(notes)
        publish(event(1))
        open(notes)
        assertEquals(2, store.scans)
    }

    @Test
    fun aDeletionDropsEverySnapshot() {
        open(notes)
        open(reactions)
        publish(event(5, arrayOf(arrayOf("e", "a".repeat(64)))))
        open(notes)
        open(reactions)
        assertEquals(4, store.scans)
    }

    @Test
    fun aReplacementDropsASnapshotItsOldVersionWasIn() {
        // The stored profile is in the set by its tag; its replacement has no such tag and so does not
        // match the filter, but it removes the old version from the set all the same.
        val tagged = listOf(Filter(kinds = listOf(0), tags = mapOf("t" to listOf("nostr"))))
        publish(event(0, arrayOf(arrayOf("t", "nostr"))))
        open(tagged)
        publish(event(0))
        open(tagged)
        assertEquals(2, store.scans)
    }
}
