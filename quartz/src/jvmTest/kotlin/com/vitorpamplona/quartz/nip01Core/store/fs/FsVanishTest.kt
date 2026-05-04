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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsVanishTest {
    private val signer = NostrSignerSync()
    private val otherSigner = NostrSignerSync()
    private val storeRelay = "wss://quartz.local".normalizeRelayUrl()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-van-")
        store = FsEventStore(root, relay = storeRelay)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        if (root.exists()) {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    private fun note(
        body: String,
        ts: Long,
        s: NostrSignerSync = signer,
    ) = s.sign<TextNoteEvent>(TextNoteEvent.build(body, createdAt = ts))

    private fun vanish(
        ts: Long,
        relayUrl: String = storeRelay.url,
        s: NostrSignerSync = signer,
    ) = s.sign<RequestToVanishEvent>(RequestToVanishEvent.build(relayUrl.normalizeRelayUrl(), createdAt = ts))

    private fun vanishEverywhere(
        ts: Long,
        s: NostrSignerSync = signer,
    ) = s.sign<RequestToVanishEvent>(RequestToVanishEvent.buildVanishFromEverywhere(createdAt = ts))

    // ------------------------------------------------------------------
    // Cascade
    // ------------------------------------------------------------------

    @Test
    fun `vanish for this relay cascades older events from the same author`() =
        runBlocking {
            val n1 = note("a", 10)
            val n2 = note("b", 20)
            val n3 = note("c", 30) // same ts as vanish — survives because cascade uses strict <
            store.insert(n1)
            store.insert(n2)
            store.insert(n3)

            val v = vanish(ts = 30)
            store.insert(v)

            assertFalse(store.hasCanonical(n1.id), "n1 should be cascade-deleted")
            assertFalse(store.hasCanonical(n2.id), "n2 should be cascade-deleted")
            assertTrue(store.hasCanonical(n3.id), "n3 (createdAt == vanish.createdAt) survives")
            assertTrue(store.hasCanonical(v.id))
        }

    @Test
    fun `vanish for a different relay does NOT cascade`() =
        runBlocking {
            val n = note("x", 10)
            store.insert(n)

            val v = vanish(ts = 20, relayUrl = "wss://elsewhere.example")
            store.insert(v)

            assertTrue(store.hasCanonical(n.id), "vanish scoped to another relay must not cascade")
            // The kind-62 event itself is still persisted (it's just a normal event).
            assertTrue(store.hasCanonical(v.id))
            // No vanish tombstone installed.
            val tombDir = root.resolve("tombstones/vanish")
            if (tombDir.exists()) {
                assertEquals(0, Files.list(tombDir).use { it.toList() }.size)
            }
        }

    @Test
    fun `vanishFromEverywhere always cascades regardless of relay`() =
        runBlocking {
            val n = note("x", 10)
            store.insert(n)

            val v = vanishEverywhere(ts = 20)
            store.insert(v)

            assertFalse(store.hasCanonical(n.id))
        }

    // ------------------------------------------------------------------
    // Block re-insert
    // ------------------------------------------------------------------

    @Test
    fun `events older than vanish are blocked from re-insertion`() =
        runBlocking {
            val n = note("a", 10)
            store.insert(n)

            val v = vanish(ts = 50)
            store.insert(v)

            // Re-insert blocked.
            store.insert(n)
            assertFalse(store.hasCanonical(n.id))

            // A brand-new older event by the same author also blocked.
            val older = note("older", 5)
            store.insert(older)
            assertFalse(store.hasCanonical(older.id))
        }

    @Test
    fun `events at vanish ts are blocked, parity with SQLite`() =
        runBlocking {
            val v = vanish(ts = 50)
            store.insert(v)

            val equal = note("equal", 50)
            store.insert(equal)
            assertFalse(store.hasCanonical(equal.id), "createdAt == vanish.createdAt should be blocked")
        }

    @Test
    fun `events newer than vanish still pass`() =
        runBlocking {
            val v = vanish(ts = 50)
            store.insert(v)

            val newer = note("newer", 100)
            store.insert(newer)
            assertTrue(store.hasCanonical(newer.id))
        }

    @Test
    fun `another author is unaffected by my vanish`() =
        runBlocking {
            val mine = note("mine", 10)
            store.insert(mine)
            val theirs = note("theirs", 5, s = otherSigner)
            store.insert(theirs)

            val v = vanish(ts = 50)
            store.insert(v)

            assertFalse(store.hasCanonical(mine.id), "my old event cascade-deleted")
            assertTrue(store.hasCanonical(theirs.id), "other author's event is unaffected")
        }

    // ------------------------------------------------------------------
    // Multiple vanish requests — strongest cutoff wins
    // ------------------------------------------------------------------

    @Test
    fun `later vanish raises the cutoff`() =
        runBlocking {
            val n100 = note("at-100", 100)
            store.insert(n100)
            store.insert(vanish(ts = 50))
            // n100 still around because 100 > 50.
            assertTrue(store.hasCanonical(n100.id))

            // Stronger vanish at ts=200 cascades it.
            store.insert(vanish(ts = 200))
            assertFalse(store.hasCanonical(n100.id))

            // And new events at ts=150 are now blocked.
            val mid = note("mid", 150)
            store.insert(mid)
            assertFalse(store.hasCanonical(mid.id))
        }

    @Test
    fun `earlier vanish does not lower a stronger cutoff`() =
        runBlocking {
            store.insert(vanish(ts = 200))
            store.insert(vanish(ts = 50)) // older — should be a no-op for the tombstone

            val mid = note("mid", 150)
            store.insert(mid)
            assertFalse(store.hasCanonical(mid.id), "stronger cutoff stays at 200")
        }

    // ------------------------------------------------------------------
    // Tombstone is a hardlink to the kind-62 event
    // ------------------------------------------------------------------

    @Test
    fun `vanish tombstone shares an inode with the kind-62 event`() =
        runBlocking {
            val v = vanish(ts = 30)
            store.insert(v)

            val tombDir = root.resolve("tombstones/vanish")
            val entries = Files.list(tombDir).use { it.toList() }
            assertEquals(1, entries.size)

            val canonical = root.resolve("events/${v.id.substring(0, 2)}/${v.id.substring(2, 4)}/${v.id}.json")
            val tombKey = Files.readAttributes(entries.single(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
            val canKey = Files.readAttributes(canonical, java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
            assertEquals(canKey, tombKey, "vanish tombstone should be a hardlink to the kind-62 canonical")
        }

    // ------------------------------------------------------------------
    // Vanish event itself remains queryable
    // ------------------------------------------------------------------

    @Test
    fun `vanish event itself is indexed and queryable`() =
        runBlocking {
            val v = vanish(ts = 30)
            store.insert(v)

            val byKind = store.query<Event>(Filter(kinds = listOf(RequestToVanishEvent.KIND)))
            assertEquals(listOf(v.id), byKind.map { it.id })
        }

    private fun FsEventStore.hasCanonical(id: String): Boolean {
        val p =
            root
                .resolve("events")
                .resolve(id.substring(0, 2))
                .resolve(id.substring(2, 4))
                .resolve("$id.json")
        return p.exists()
    }
}
