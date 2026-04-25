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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FsMaintenanceTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-maint-")
        store = FsEventStore(root)
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
    ) = signer.sign<TextNoteEvent>(TextNoteEvent.build(body, createdAt = ts))

    // ------------------------------------------------------------------
    // Lock file
    // ------------------------------------------------------------------

    @Test
    fun `lock file is created on open`() {
        assertTrue(root.resolve(".lock").exists())
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    @Test
    fun `transaction commits all inserts on success`() {
        val a = note("a", 1)
        val b = note("b", 2)
        val c = note("c", 3)

        store.transaction {
            insert(a)
            insert(b)
            insert(c)
        }

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signer.pubKey)))
        assertEquals(setOf(a.id, b.id, c.id), got.map { it.id }.toSet())
    }

    @Test
    fun `transaction propagates exceptions and stops processing`() {
        val a = note("a", 1)
        val b = note("b", 2)
        val c = note("c", 3)

        assertFailsWith<IllegalStateException> {
            store.transaction {
                insert(a)
                insert(b)
                throw IllegalStateException("boom")
                // unreachable
                @Suppress("UNREACHABLE_CODE")
                insert(c)
            }
        }

        // Events written before the throw are kept (per the plan: atomic-
        // per-event, serialised across writers — not all-or-nothing).
        assertTrue(store.count(Filter(ids = listOf(a.id))) == 1)
        assertTrue(store.count(Filter(ids = listOf(b.id))) == 1)
        assertTrue(store.count(Filter(ids = listOf(c.id))) == 0)
    }

    @Test
    fun `transaction is re-entrant on the same thread`() {
        val a = note("a", 1)
        // If flock were non-reentrant we'd self-deadlock here because
        // insert() acquires the same lock the transaction already holds.
        store.transaction {
            insert(a)
            // Call an outer-locking method from within the transaction.
            store.deleteExpiredEvents()
        }
        assertEquals(1, store.count(Filter(ids = listOf(a.id))))
    }

    // ------------------------------------------------------------------
    // scrub — rebuild idx/ from canonical
    // ------------------------------------------------------------------

    @Test
    fun `scrub rebuilds idx entries after a manual wipe`() {
        val a = note("hello bitcoin", 10)
        val b = note("nostr stuff", 20)
        store.insert(a)
        store.insert(b)

        // Blow away the entire idx/ tree behind the store's back.
        Files.walk(root.resolve("idx")).use {
            it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) }
        }

        // Without scrub, index-driven queries find nothing.
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(authors = listOf(signer.pubKey))).map { it.id })

        store.scrub()

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signer.pubKey))).map { it.id }.toSet()
        assertEquals(setOf(a.id, b.id), got)

        // FTS recovered too.
        assertEquals(listOf(a.id), store.query<TextNoteEvent>(Filter(search = "bitcoin")).map { it.id })
    }

    @Test
    fun `scrub leaves replaceable slot intact`() {
        // Replaceable slots pin events via hardlink even without the
        // canonical. Scrub must not wipe slots.
        val meta =
            signer.sign<com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent>(
                createdAt = 10,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
            )
        store.insert(meta)
        val slot = root.resolve("replaceable/0/${signer.pubKey}.json")
        assertTrue(slot.exists())

        store.scrub()
        assertTrue(slot.exists(), "replaceable slot must survive scrub")
    }

    // ------------------------------------------------------------------
    // compact — drop dangling idx entries
    // ------------------------------------------------------------------

    @Test
    fun `compact drops idx entries whose canonical is gone`() {
        val a = note("x", 10)
        store.insert(a)

        // Externally delete the canonical without touching idx/.
        val canonical = root.resolve("events/${a.id.substring(0, 2)}/${a.id.substring(2, 4)}/${a.id}.json")
        assertTrue(Files.deleteIfExists(canonical))

        val kindDir = root.resolve("idx/kind/1")
        assertEquals(1, kindDir.listDirectoryEntries().size, "dangling entry still present pre-compact")

        store.compact()

        assertEquals(0, kindDir.listDirectoryEntries().size, "dangling entry dropped post-compact")
    }

    @Test
    fun `compact leaves valid entries alone`() {
        val a = note("x", 10)
        store.insert(a)

        store.compact()

        val kindDir = root.resolve("idx/kind/1")
        assertEquals(1, kindDir.listDirectoryEntries().size, "valid entry should not be touched")
        assertEquals(listOf(a.id), store.query<TextNoteEvent>(Filter(ids = listOf(a.id))).map { it.id })
    }

    // ------------------------------------------------------------------
    // close
    // ------------------------------------------------------------------

    @Test
    fun `close is idempotent`() {
        store.close()
        store.close()
    }

    @Test
    fun `reopen after close works`() {
        val a = note("a", 1)
        store.insert(a)
        store.close()

        val reopened = FsEventStore(root)
        try {
            assertEquals(listOf(a.id), reopened.query<TextNoteEvent>(Filter(ids = listOf(a.id))).map { it.id })
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------------
    // Concurrency — two writer threads serialise cleanly
    // ------------------------------------------------------------------

    @Test
    fun `concurrent inserts on two threads are both persisted`() {
        val events = (1..20).map { note("n$it", it.toLong()) }
        val half = events.size / 2

        val t1 =
            Thread {
                events.take(half).forEach { store.insert(it) }
            }
        val t2 =
            Thread {
                events.drop(half).forEach { store.insert(it) }
            }
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signer.pubKey))).map { it.id }.toSet()
        assertEquals(events.map { it.id }.toSet(), got)
    }
}
