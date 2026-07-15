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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GrapeRankPublisher.reconcileLocal] is what makes a score run durable: the
 * local store must end up holding exactly the desired card set — changed ranks
 * re-signed, unchanged ranks untouched (no event-id churn), dropped targets
 * retracted via kind:5 (which the store applies on insert) with the tombstone
 * kept for a later relay sync.
 */
class GrapeRankPublisherTest {
    private fun hexKey(n: Int): String = n.toString(16).padStart(64, '0')

    private suspend fun cardsByTarget(
        store: IEventStore,
        provider: String,
    ): Map<String, Int?> =
        store
            .query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(provider)))
            .filterIsInstance<ContactCardEvent>()
            .associate { it.aboutUser() to it.rank() }

    @Test
    fun reconcileLocalUpsertsSkipsAndRetracts() =
        runBlocking {
            val store = EventStore(null)
            val signer = NostrSignerInternal(KeyPair())
            val provider = signer.pubKey
            val publisher = GrapeRankPublisher(store)

            val a = hexKey(0xA)
            val b = hexKey(0xB)
            val c = hexKey(0xC)

            // First run: every card is new.
            val r1 = publisher.reconcileLocal(signer, provider, listOf(a to 50, b to 10), createdAt = 1_000L)
            assertEquals(2, r1.signed)
            assertEquals(0, r1.unchanged)
            assertEquals(0, r1.retracted)
            assertEquals(mapOf(a to 50, b to 10), cardsByTarget(store, provider))
            val firstIds =
                store
                    .query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(provider)))
                    .map { it.id }
                    .toSet()

            // Same ranks again: nothing is re-signed, no event id churns.
            val r2 = publisher.reconcileLocal(signer, provider, listOf(a to 50, b to 10), createdAt = 2_000L)
            assertEquals(0, r2.signed)
            assertEquals(2, r2.unchanged)
            assertEquals(0, r2.retracted)
            val secondIds =
                store
                    .query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(provider)))
                    .map { it.id }
                    .toSet()
            assertEquals(firstIds, secondIds)

            // a's rank moved, b dropped out, c is new: a + c signed, b retracted.
            val r3 = publisher.reconcileLocal(signer, provider, listOf(a to 60, c to 5), createdAt = 3_000L)
            assertEquals(2, r3.signed)
            assertEquals(0, r3.unchanged)
            assertEquals(1, r3.retracted)

            // The store converged to exactly the desired set — b's card is gone…
            assertEquals(mapOf(a to 60, c to 5), cardsByTarget(store, provider))

            // …but its kind:5 tombstone remains, ready to sync to relays.
            val deletions = store.query<Event>(Filter(kinds = listOf(DeletionEvent.KIND), authors = listOf(provider)))
            assertEquals(1, deletions.size)

            store.close()
        }

    @Test
    fun retractedTargetCanBeReCardedLater() =
        runBlocking {
            val store = EventStore(null)
            val signer = NostrSignerInternal(KeyPair())
            val provider = signer.pubKey
            val publisher = GrapeRankPublisher(store)

            val a = hexKey(0xA)

            publisher.reconcileLocal(signer, provider, listOf(a to 40), createdAt = 1_000L)
            publisher.reconcileLocal(signer, provider, emptyList(), createdAt = 2_000L)
            assertEquals(emptyMap(), cardsByTarget(store, provider))

            // A NEWER card outranks the older kind:5 (NIP-09 deletions only cover
            // versions up to their created_at), so the target comes back cleanly.
            val r = publisher.reconcileLocal(signer, provider, listOf(a to 45), createdAt = 3_000L)
            assertEquals(1, r.signed)
            assertEquals(mapOf(a to 45), cardsByTarget(store, provider))

            store.close()
        }
}
