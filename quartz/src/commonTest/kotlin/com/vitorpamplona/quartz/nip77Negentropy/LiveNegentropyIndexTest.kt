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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.IStorage
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiveNegentropyIndexTest {
    private fun id(seed: Int): String = seed.toString(16).padStart(64, '0')

    private fun entry(
        time: Long,
        seed: Int = time.toInt(),
    ) = IdAndTime(time, id(seed))

    /** The index content, read back through the sealed storage. */
    private fun IStorage.toEntries(): List<IdAndTime> = map { IdAndTime(it.timestamp, it.id.toHexString()) }

    private fun contentOf(index: LiveNegentropyIndex): List<IdAndTime> = assertNotNull(index.sealedSnapshot(Int.MAX_VALUE)).toEntries()

    @Test
    fun answersNothingUntilRebuilt() {
        val index = LiveNegentropyIndex()
        assertNull(index.sealedSnapshot(1000))

        // Mutations before the first rebuild are folded into the scan
        // that populates it — they must not resurrect a dropped index.
        index.insert(entry(1))
        assertNull(index.sealedSnapshot(1000))
        assertEquals(0, index.size())
    }

    @Test
    fun rebuildSortsAndSnapshotMatches() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(5), entry(1), entry(3)))

        assertTrue(index.isPopulated())
        assertEquals(3, index.size())
        assertEquals(listOf(entry(1), entry(3), entry(5)), contentOf(index))
    }

    @Test
    fun insertsKeepOrderIncludingBackfillAndTies() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(10), entry(30)))

        index.insert(entry(40)) // tail (the common, cheap case)
        index.insert(entry(20)) // out-of-order backfill
        index.insert(IdAndTime(10, id(99))) // same timestamp, distinct id

        assertEquals(
            listOf(entry(10), IdAndTime(10, id(99)), entry(20), entry(30), entry(40)),
            contentOf(index),
        )
    }

    @Test
    fun duplicateInsertIsIgnored() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1)))

        index.insert(entry(1))

        assertEquals(1, index.size())
    }

    @Test
    fun removeDropsExactlyTheEntry() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1), entry(2), entry(3)))

        index.remove(entry(2))
        index.remove(entry(7)) // absent: no-op

        assertEquals(listOf(entry(1), entry(3)), contentOf(index))
    }

    @Test
    fun invalidateDropsEverythingUntilNextRebuild() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1), entry(2)))

        index.invalidate()

        assertTrue(!index.isPopulated())
        assertNull(index.sealedSnapshot(1000))

        index.rebuild(listOf(entry(9)))
        assertEquals(listOf(entry(9)), contentOf(index))
    }

    @Test
    fun snapshotIsMemoizedUntilTheNextMutation() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1)))

        val first = index.sealedSnapshot(1000)
        val second = index.sealedSnapshot(1000)
        assertSame(first, second)

        index.insert(entry(2))
        val third = index.sealedSnapshot(1000)
        assertNotSame(first, third)
    }

    @Test
    fun snapshotIsImmutableUnderLaterMutations() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1), entry(2)))

        val snapshot = assertNotNull(index.sealedSnapshot(1000))
        index.insert(entry(3))
        index.remove(entry(1))

        // The reconcile a peer started before those writes still sees
        // the state it opened against.
        assertEquals(listOf(entry(1), entry(2)), snapshot.toEntries())
        assertEquals(listOf(entry(2), entry(3)), contentOf(index))
    }

    @Test
    fun overCapAnswersNullSoTheCallerSendsNegErr() {
        val index = LiveNegentropyIndex()
        index.rebuild(listOf(entry(1), entry(2), entry(3)))

        assertNull(index.sealedSnapshot(2))
        assertNotNull(index.sealedSnapshot(3))
    }

    @Test
    fun snapshotDrivesARealReconcileSession() {
        // End-to-end sanity: the sealed snapshot must be a valid server
        // storage for an actual NegentropyServerSession handshake.
        val index = LiveNegentropyIndex()
        index.rebuild((1..50).map { entry(it.toLong()) })

        val storage = assertNotNull(index.sealedSnapshot(1000))
        val server = NegentropyServerSession("sub", storage)

        // A client with an empty set opens the session; the server's
        // first response must be a parseable NEG-MSG (non-null).
        val clientInitial = Negentropy(NegentropyServerSession.sealVector(emptyList())).initiate()
        val response = server.processMessage(Hex.encode(clientInitial))
        assertNotNull(response)
    }
}
