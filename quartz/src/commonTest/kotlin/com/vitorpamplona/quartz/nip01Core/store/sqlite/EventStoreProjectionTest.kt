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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
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
import kotlinx.coroutines.flow.first
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
    private lateinit var store: EventStore
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        Secp256k1Instance
        store = EventStore(dbName = null)
        scope = CoroutineScope(SupervisorJob())
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        store.close()
    }

    /**
     * Wait until [items][EventStoreProjection.items] reaches a state
     * for which [predicate] is true. We poll the StateFlow rather than
     * collect because the projection only re-emits on membership
     * change — in-place addressable updates intentionally don't move
     * the list reference.
     */
    private suspend fun <T : Event> EventStoreProjection<T>.awaitItems(
        timeoutMs: Long = 5_000,
        predicate: (List<MutableStateFlow<T>>) -> Boolean,
    ): List<MutableStateFlow<T>> =
        withTimeout(timeoutMs) {
            items.first { predicate(it) }
        }

    /**
     * Wait until [flow] reaches [expected]. Used to observe an
     * in-place addressable update where the list reference doesn't
     * change but the slot's value does.
     */
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
            store.insert(a)
            store.insert(b)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()

            val items = projection.items.value
            assertEquals(2, items.size)
            // Sorted by created_at DESC.
            assertEquals(b.id, items[0].value.id)
            assertEquals(a.id, items[1].value.id)
            projection.close()
        }

    @Test
    fun insertAddsNewSlot() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            store.insert(a)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            val before = projection.items.value
            assertEquals(1, before.size)

            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            store.insert(b)

            val after = projection.awaitItems { it.size == 2 }
            assertNotSame(before, after, "insert must produce a new list reference")
            assertEquals(b.id, after[0].value.id)
            assertEquals(a.id, after[1].value.id)
            projection.close()
        }

    @Test
    fun insertingNonMatchingEventDoesNotChangeList() =
        runBlocking {
            val text = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            store.insert(text)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            val seed = projection.items.value

            // Metadata kind doesn't match the filter.
            val meta = signer.sign(MetadataEvent.createNew("Vitor", createdAt = 200))
            store.insert(meta)

            // Give the projection time to process the change.
            delay(150)
            assertSame(seed, projection.items.value)
            projection.close()
        }

    @Test
    fun replaceableUpdateMutatesSlotInPlace() =
        runBlocking {
            val time = TimeUtils.now()
            val v1 = signer.sign(MetadataEvent.createNew("v1", createdAt = time))
            store.insert(v1)

            val projection =
                store.observe<MetadataEvent>(
                    Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(v1.pubKey)),
                    scope,
                )
            projection.ready.await()
            val seedList = projection.items.value
            assertEquals(1, seedList.size)
            val slot = seedList[0]
            assertEquals(v1.id, slot.value.id)

            val v2 = signer.sign(MetadataEvent.createNew("v2", createdAt = time + 1))
            store.insert(v2)

            // The slot's flow updates...
            awaitFlow(slot) { it.id == v2.id }

            // ...but the list reference is the SAME, and the slot is the SAME instance.
            assertSame(seedList, projection.items.value, "addressable replace must not change list reference")
            assertSame(slot, projection.items.value[0])
            projection.close()
        }

    @Test
    fun addressableUpdateMutatesSlotInPlace() =
        runBlocking {
            val time = TimeUtils.now()
            val v1 = signer.sign(LongTextNoteEvent.build("blog v1", "title", dTag = "blog", createdAt = time))
            store.insert(v1)

            val projection =
                store.observe<LongTextNoteEvent>(
                    Filter(
                        kinds = listOf(LongTextNoteEvent.KIND),
                        authors = listOf(v1.pubKey),
                        tags = mapOf("d" to listOf("blog")),
                    ),
                    scope,
                )
            projection.ready.await()
            val seedList = projection.items.value
            assertEquals(1, seedList.size)
            val slot = seedList[0]
            assertEquals(v1.id, slot.value.id)

            val v2 = signer.sign(LongTextNoteEvent.build("blog v2", "title", dTag = "blog", createdAt = time + 1))
            store.insert(v2)

            awaitFlow(slot) { it.id == v2.id }
            assertSame(seedList, projection.items.value, "addressable update must not change list reference")
            assertSame(slot, projection.items.value[0])
            projection.close()
        }

    @Test
    fun nip09DeletionRemovesSlot() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            store.insert(a)
            store.insert(b)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            assertEquals(2, projection.items.value.size)

            val deletion = signer.sign(DeletionEvent.build(listOf(a)))
            store.insert(deletion)

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(b.id, after[0].value.id)
            projection.close()
        }

    @Test
    fun nip62VanishRemovesAllAuthorsEvents() =
        runBlocking {
            val time = TimeUtils.now()
            val a = signer.sign(TextNoteEvent.build("a", createdAt = time))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = time + 1))
            store.insert(a)
            store.insert(b)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            assertEquals(2, projection.items.value.size)

            val vanish =
                signer.sign(
                    RequestToVanishEvent.build(
                        "wss://quartz.local".normalizeRelayUrl(),
                        createdAt = time + 2,
                    ),
                )
            store.insert(vanish)

            val after = projection.awaitItems { it.isEmpty() }
            assertTrue(after.isEmpty())
            projection.close()
        }

    @Test
    fun nip40ExpirationRemovesSlotOnSweep() =
        runBlocking {
            val time = TimeUtils.now()
            val safe = signer.sign(TextNoteEvent.build("safe", createdAt = time) { expiration(time + 100) })
            val short = signer.sign(TextNoteEvent.build("short", createdAt = time) { expiration(time + 1) })
            store.insert(safe)
            store.insert(short)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            assertEquals(2, projection.items.value.size)

            // Wait for the expiration to lapse, then run the sweep.
            delay(2000)
            store.deleteExpiredEvents()

            val after = projection.awaitItems { it.size == 1 }
            assertEquals(safe.id, after[0].value.id)
            projection.close()
        }

    @Test
    fun manualDeleteByIdRemovesSlot() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            store.insert(a)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            assertEquals(1, projection.items.value.size)

            store.store.delete(a.id)

            val after = projection.awaitItems { it.isEmpty() }
            assertTrue(after.isEmpty())
            projection.close()
        }

    @Test
    fun limitIsEnforcedOnInsertOverflow() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            val b = signer.sign(TextNoteEvent.build("b", createdAt = 200))
            store.insert(a)
            store.insert(b)

            val projection =
                store.observe<TextNoteEvent>(
                    Filter(kinds = listOf(TextNoteEvent.KIND), limit = 2),
                    scope,
                )
            projection.ready.await()
            assertEquals(2, projection.items.value.size)

            // Newer event arrives; it should push the oldest out.
            val c = signer.sign(TextNoteEvent.build("c", createdAt = 300))
            store.insert(c)

            val after = projection.awaitItems { it[0].value.id == c.id }
            assertEquals(2, after.size)
            assertEquals(c.id, after[0].value.id)
            assertEquals(b.id, after[1].value.id)
            projection.close()
        }

    @Test
    fun closeStopsListening() =
        runBlocking {
            val a = signer.sign(TextNoteEvent.build("a", createdAt = 100))
            store.insert(a)

            val projection = store.observe<TextNoteEvent>(Filter(kinds = listOf(TextNoteEvent.KIND)), scope)
            projection.ready.await()
            projection.close()

            // Subsequent inserts must not surface in the (now empty) projection.
            store.insert(signer.sign(TextNoteEvent.build("b", createdAt = 200)))
            delay(150)
            assertTrue(projection.items.value.isEmpty())
        }
}
