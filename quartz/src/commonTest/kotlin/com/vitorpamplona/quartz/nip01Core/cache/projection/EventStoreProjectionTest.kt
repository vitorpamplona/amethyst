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
package com.vitorpamplona.quartz.nip01Core.cache.projection

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventStoreProjectionTest {
    private val signer = NostrSignerSync()
    private val otherSigner = NostrSignerSync()
    private lateinit var store: EventStore
    private lateinit var observable: ObservableEventStore
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        Secp256k1Instance
        store = EventStore(dbName = null)
        observable = ObservableEventStore(store)
        scope = CoroutineScope(SupervisorJob())
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        store.close()
    }

    /** Open a hot StateFlow over the projection for the lifetime of the test scope. */
    private fun <T : Event> projectionOf(filter: Filter): StateFlow<ProjectionState<T>> = observable.project<T>(filter).stateIn(scope, SharingStarted.Eagerly, ProjectionState.Loading)

    private fun <T : Event> projectionOf(filters: List<Filter>): StateFlow<ProjectionState<T>> = observable.project<T>(filters).stateIn(scope, SharingStarted.Eagerly, ProjectionState.Loading)

    /** Snapshot of the currently-loaded items, or empty if still seeding. */
    private val <T : Event> StateFlow<ProjectionState<T>>.items: List<MutableStateFlow<T>>
        get() = (value as? ProjectionState.Loaded)?.items.orEmpty()

    /** Suspends until the seed completes; returns the loaded list. */
    private suspend fun <T : Event> StateFlow<ProjectionState<T>>.awaitLoaded(timeoutMs: Long = 5_000): List<MutableStateFlow<T>> =
        withTimeout(timeoutMs) {
            (first { it is ProjectionState.Loaded } as ProjectionState.Loaded).items
        }

    private suspend fun <T : Event> StateFlow<ProjectionState<T>>.awaitItems(
        timeoutMs: Long = 5_000,
        predicate: (List<MutableStateFlow<T>>) -> Boolean,
    ): List<MutableStateFlow<T>> =
        withTimeout(timeoutMs) {
            (first { it is ProjectionState.Loaded && predicate(it.items) } as ProjectionState.Loaded).items
        }

    private suspend fun <T : Event> awaitFlow(
        flow: MutableStateFlow<T>,
        timeoutMs: Long = 5_000,
        predicate: (T) -> Boolean,
    ): T =
        withTimeout(timeoutMs) {
            flow.first { predicate(it) }
        }

    @Test
    fun seedReturnsExistingEvents() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            observable.insert(a)
            observable.insert(b)

            val projection = projectionOf<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()

            val items = projection.items
            assertEquals(2, items.size)
            assertEquals(b.id, items[0].value.id)
            assertEquals(a.id, items[1].value.id)
        }

    @Test
    fun insertAddsNewSlot() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            observable.insert(a)

            val projection = projectionOf<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            val before = projection.items
            assertEquals(1, before.size)

            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            observable.insert(b)

            val after = projection.awaitItems { it.size == 2 }
            assertNotSame(before, after, "insert must produce a new list reference")
            assertEquals(b.id, after[0].value.id)
        }

    @Test
    fun nonMatchingInsertDoesNotChangeList() =
        runBlocking {
            val text = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            observable.insert(text)

            val projection = projectionOf<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            val seed = projection.items

            val meta = signer.sign(MetadataEvent.createNew("Vitor", createdAt = 200))
            observable.insert(meta)

            delay(150)
            assertSame(seed, projection.items)
        }

    @Test
    fun replaceableUpdateMutatesSlotInPlace() =
        runBlocking {
            val time = TimeUtils.now()
            val v1 = signer.sign(MetadataEvent.createNew("v1", createdAt = time))
            observable.insert(v1)

            val projection =
                projectionOf<MetadataEvent>(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(v1.pubKey)))
            projection.awaitLoaded()
            val seedList = projection.items
            assertEquals(1, seedList.size)
            val slot = seedList[0]
            assertEquals(v1.id, slot.value.id)

            val v2 = signer.sign(MetadataEvent.createNew("v2", createdAt = time + 1))
            observable.insert(v2)

            awaitFlow(slot) { it.id == v2.id }
            assertSame(seedList, projection.items, "replaceable update must not change list reference")
            assertSame(slot, projection.items[0])
        }

    @Test
    fun addressableUpdateMutatesSlotInPlace() =
        runBlocking {
            val time = TimeUtils.now()
            val v1 = signer.sign(LongTextNoteEvent.build("blog v1", "title", dTag = "blog", createdAt = time))
            observable.insert(v1)

            val projection =
                projectionOf<LongTextNoteEvent>(
                    Filter(
                        kinds = listOf(LongTextNoteEvent.KIND),
                        authors = listOf(v1.pubKey),
                        tags = mapOf("d" to listOf("blog")),
                    ),
                )
            projection.awaitLoaded()
            val seedList = projection.items
            val slot = seedList[0]

            val v2 = signer.sign(LongTextNoteEvent.build("blog v2", "title", dTag = "blog", createdAt = time + 1))
            observable.insert(v2)

            awaitFlow(slot) { it.id == v2.id }
            assertSame(seedList, projection.items, "addressable update must not change list reference")
        }

    /**
     * If v2 of an addressable no longer matches the filter (e.g.
     * tag list changed), the slot is dropped. The projection
     * re-evaluates filter membership on every supersession.
     */
    @Test
    fun addressableUpdateDropsSlotWhenFilterStopsMatching() =
        runBlocking {
            val time = TimeUtils.now()
            // v1 carries tag "nostr"; v2 changes the tag to "bitcoin".
            val v1 =
                signer.sign(
                    LongTextNoteEvent.build("blog v1", "title", dTag = "blog", createdAt = time) {
                        hashtag("nostr")
                    },
                )
            observable.insert(v1)

            // Filter narrows to events that ALSO carry hashtag "nostr".
            val projection =
                projectionOf<LongTextNoteEvent>(
                    Filter(
                        kinds = listOf(LongTextNoteEvent.KIND),
                        authors = listOf(v1.pubKey),
                        tags = mapOf("t" to listOf("nostr")),
                    ),
                )
            projection.awaitLoaded()
            assertEquals(1, projection.items.size)

            val v2 =
                signer.sign(
                    LongTextNoteEvent.build("blog v2", "title", dTag = "blog", createdAt = time + 1) {
                        hashtag("bitcoin")
                    },
                )
            observable.insert(v2)

            // v2 doesn't match — slot drops and the snapshot is empty.
            val after = projection.awaitItems { it.isEmpty() }
            assertTrue(after.isEmpty())
        }

    /**
     * Out-of-order arrival: the projection sees the *newer* version
     * first (e.g. the relay sent v2 first), then v1. The projection
     * must keep v2 in place and not regress to v1 — that's the NIP-01
     * supersession contract from the projection's side, since the
     * store would have rejected v1 anyway.
     */
    @Test
    fun olderReplaceableArrivingAfterNewerIsRejected() =
        runBlocking {
            val time = TimeUtils.now()
            val v1 = signer.sign(MetadataEvent.createNew("v1", createdAt = time))
            val v2 = signer.sign(MetadataEvent.createNew("v2", createdAt = time + 5))

            // Seed the projection with v2 before v1 even hits the store
            // — by inserting v2 first.
            observable.insert(v2)

            val projection =
                projectionOf<MetadataEvent>(Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(v1.pubKey)))
            projection.awaitLoaded()
            val slot = projection.items[0]
            assertEquals(v2.id, slot.value.id)

            // The store rejects v1 because v2 already won; the
            // projection therefore never sees v1 on the inserts
            // stream. The slot must still hold v2.
            try {
                observable.insert(v1)
            } catch (_: Throwable) {
                // expected — store enforces the same rule
            }

            delay(150)
            assertEquals(v2.id, slot.value.id)
        }

    @Test
    fun nip09DeletionRemovesSlot() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            observable.insert(a)
            observable.insert(b)

            val projection = projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            assertEquals(2, projection.items.size)

            val deletion = signer.sign(DeletionEvent.build(listOf(a)))
            observable.insert(deletion)

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(b.id, after[0].value.id)
        }

    /**
     * NIP-09 cross-author deletions are inert. A different signer
     * publishing a kind-5 targeting `a` must not drop the slot.
     */
    @Test
    fun nip09CrossAuthorDeletionIsInert() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            observable.insert(a)

            val projection = projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            val seed = projection.items
            assertEquals(1, seed.size)

            val foreignDeletion = otherSigner.sign(DeletionEvent.build(listOf(a)))
            observable.insert(foreignDeletion)

            // Give the projection time to process the event.
            delay(150)
            assertSame(seed, projection.items)
            assertEquals(
                a.id,
                projection.items[0]
                    .value.id,
            )
        }

    @Test
    fun nip62VanishRemovesAuthorEvents() =
        runBlocking {
            val time = TimeUtils.now()
            val a = signer.sign(TextNoteEvent.build("a", createdAt = time))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = time + 1))
            observable.insert(a)
            observable.insert(b)

            val projection = projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            assertEquals(2, projection.items.size)

            val vanish =
                signer.sign(
                    RequestToVanishEvent.build(
                        "wss://quartz.local".normalizeRelayUrl(),
                        createdAt = time + 2,
                    ),
                )
            observable.insert(vanish)

            val after = projection.awaitItems { it.isEmpty() }
            assertTrue(after.isEmpty())
        }

    /**
     * NIP-62 only removes events from the same author. A vanish from
     * a different author must not touch slots owned by [signer].
     */
    @Test
    fun nip62OtherAuthorVanishLeavesEventsAlone() =
        runBlocking {
            val time = TimeUtils.now()
            val a = signer.sign(TextNoteEvent.build("a", createdAt = time))
            observable.insert(a)

            val projection = projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            val seed = projection.items

            val foreignVanish =
                otherSigner.sign(
                    RequestToVanishEvent.build(
                        "wss://quartz.local".normalizeRelayUrl(),
                        createdAt = time + 2,
                    ),
                )
            observable.insert(foreignVanish)

            delay(150)
            assertSame(seed, projection.items)
        }

    /**
     * NIP-40 expiration drops slots only when the application calls
     * `deleteExpiredEvents()` on the observable store — projections
     * no longer run their own ticker.
     */
    @Test
    fun nip40ExpirationDroppedOnStoreSweep() =
        runBlocking {
            val time = TimeUtils.now()
            val safe = signer.sign(TextNoteEvent.build("safe", createdAt = time) { expiration(time + 100) })
            val short = signer.sign(TextNoteEvent.build("short", createdAt = time) { expiration(time + 1) })
            observable.insert(safe)
            observable.insert(short)

            val projection =
                projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            assertEquals(2, projection.items.size)

            // Let the short expiration lapse, then ask the store to
            // sweep — the projection drops the expired slot in
            // response to the resulting StoreChange.Delete(Expired).
            delay(2000)
            observable.deleteExpiredEvents()

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(safe.id, after[0].value.id)
        }

    /**
     * `delete(filter)` on the observable propagates to open
     * projections: they drop every slot matching the filter using
     * the same Filter.match logic the store would.
     */
    @Test
    fun deleteByFilterRemovesMatchingSlots() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            val foreign = otherSigner.sign(TextNoteEvent.build("foreign", createdAt = 150))
            observable.insert(a)
            observable.insert(b)
            observable.insert(foreign)

            val projection =
                projectionOf<Event>(Filter(kinds = listOf(TextNoteEvent.KIND)))
            projection.awaitLoaded()
            assertEquals(3, projection.items.size)

            // Drop everything authored by `signer` — should leave
            // only the foreign event.
            observable.delete(Filter(authors = listOf(signer.pubKey)))

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(foreign.id, after[0].value.id)
        }

    @Test
    fun limitIsEnforcedOnInsertOverflow() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            observable.insert(a)
            observable.insert(b)

            val projection =
                projectionOf<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND), limit = 2))
            projection.awaitLoaded()
            assertEquals(2, projection.items.size)

            val c = signer.sign(TextNoteEvent.build("c", createdAt = 300))
            observable.insert(c)

            val after = projection.awaitItems { it[0].value.id == c.id }
            assertEquals(2, after.size)
            assertEquals(c.id, after[0].value.id)
            assertEquals(b.id, after[1].value.id)
        }

    /**
     * Per-filter limit: filter A caps at 2, filter B caps at 2, but
     * the events they match are disjoint, so the projection's union
     * is 4 (larger than any single filter's cap).
     */
    @Test
    fun perFilterLimitUnionExceedsSingleLimit() =
        runBlocking {
            val authorA = NostrSignerSync()
            val authorB = NostrSignerSync()
            val a1 = authorA.sign(TextNoteEvent.build("a1", createdAt = 100))
            val a2 = authorA.sign(TextNoteEvent.build("a2", createdAt = 200))
            val b1 = authorB.sign(TextNoteEvent.build("b1", createdAt = 110))
            val b2 = authorB.sign(TextNoteEvent.build("b2", createdAt = 210))
            observable.insert(a1)
            observable.insert(a2)
            observable.insert(b1)
            observable.insert(b2)

            val filterA = Filter(kinds = listOf(TextNoteEvent.KIND), authors = listOf(authorA.pubKey), limit = 2)
            val filterB = Filter(kinds = listOf(TextNoteEvent.KIND), authors = listOf(authorB.pubKey), limit = 2)
            val projection =
                projectionOf<TextNoteEvent>(
                    listOf(filterA, filterB),
                )
            projection.awaitLoaded()
            assertEquals(4, projection.items.size, "per-filter caps don't dedupe union")
        }

    /**
     * Each filter's cap is enforced on its own retained set: filter A
     * keeps only its 2 newest matches even when more arrive.
     */
    @Test
    fun perFilterLimitEvictsOldestWithinFilter() =
        runBlocking {
            val a1 = signer.sign(TextNoteEvent.build("a1", createdAt = 100))
            val a2 = signer.sign(TextNoteEvent.build("a2", createdAt = 200))
            val a3 = signer.sign(TextNoteEvent.build("a3", createdAt = 300))
            observable.insert(a1)
            observable.insert(a2)

            val projection =
                projectionOf<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND), limit = 2))
            projection.awaitLoaded()
            assertEquals(2, projection.items.size)

            observable.insert(a3)
            val after = projection.awaitItems { it[0].value.id == a3.id }
            assertEquals(2, after.size)
            assertEquals(a3.id, after[0].value.id)
            assertEquals(a2.id, after[1].value.id)
        }

    @Test
    fun cancellingScopeStopsListening() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            observable.insert(a)

            // Sub-scope so we can cancel just the projection's collector
            // without taking down the test's outer scope.
            val collectorScope = CoroutineScope(SupervisorJob())
            val projection =
                observable
                    .project<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)))
                    .stateIn(collectorScope, SharingStarted.Eagerly, ProjectionState.Loading)
            projection.awaitLoaded()
            assertEquals(1, projection.items.size)

            collectorScope.cancel()
            delay(50)

            // After the collector scope dies, new inserts don't update
            // the StateFlow — its value is frozen at the last emission.
            observable.insert(signer.sign(TextNoteEvent.build("b", createdAt = 200)))
            delay(150)
            assertEquals(1, projection.items.size)
            assertEquals(a.id, projection.items[0].value.id)
        }

    /**
     * Ephemeral events (kinds `20000-29999`) are never persisted, so
     * the inner SQLite store silently drops them. The
     * `ObservableEventStore` wrapper still routes them onto its
     * [events][com.vitorpamplona.quartz.nip01Core.store.projection.ObservableEventStore.changes]
     * flow, so an open projection sees them while it's alive. They
     * vanish from any future seed because the DB never had them.
     */
    @Test
    fun ephemeralEventsAppearInProjection() =
        runBlocking {
            val ephemeralKind = 22_000
            val projection =
                projectionOf<Event>(Filter(kinds = listOf(ephemeralKind)))
            projection.awaitLoaded()
            assertTrue(projection.items.isEmpty())

            val ephemeral: Event =
                signer.sign(
                    TimeUtils.now(),
                    ephemeralKind,
                    arrayOf(emptyArray()),
                    "live",
                )
            observable.insert(ephemeral)

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(ephemeral.id, after[0].value.id)

            // Confirmation that the inner SQLite store didn't persist.
            val persisted = store.query<Event>(Filter(kinds = listOf(ephemeralKind)))
            assertEquals(0, persisted.size)

            // A fresh projection on the same store gets nothing — the
            // event was only ever live, not durable.
            val freshProjection =
                projectionOf<Event>(Filter(kinds = listOf(ephemeralKind)))
            freshProjection.awaitLoaded()
            assertTrue(freshProjection.items.isEmpty())
        }
}
