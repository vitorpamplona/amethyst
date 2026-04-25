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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity matrix: drive both [EventStore] (SQLite) and [FsEventStore]
 * with identical event streams and assert their `query()` results
 * agree. This is the strongest end-to-end check that the file-backed
 * store implements the same semantics as the SQLite reference.
 *
 * SQLite throws on blocked inserts (NIP-09 tombstones, NIP-62 vanish);
 * the FS store silently skips. Both observable outcomes are "event not
 * persisted", so the parity helper catches insert exceptions on the
 * SQLite side and proceeds — what matters is the post-insert state.
 */
class FsParityTest {
    private val signer = NostrSignerSync()
    private val otherSigner = NostrSignerSync()

    private lateinit var fsRoot: Path
    private lateinit var fs: FsEventStore
    private lateinit var sqlite: EventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        fsRoot = Files.createTempDirectory("fs-parity-")
        fs = FsEventStore(fsRoot)
        // Pass dbName=null so SQLite uses an in-memory database.
        sqlite = EventStore(dbName = null)
    }

    @AfterTest
    fun tearDown() {
        try {
            fs.close()
        } catch (_: Throwable) {
        }
        try {
            sqlite.close()
        } catch (_: Throwable) {
        }
        if (fsRoot.exists()) {
            Files.walk(fsRoot).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    /** Insert into both stores. Swallow SQLite rejections (we only care about the resulting state). */
    private fun insertBoth(event: Event) {
        try {
            sqlite.insert(event)
        } catch (_: Throwable) {
        }
        try {
            fs.insert(event)
        } catch (_: Throwable) {
        }
    }

    /** Assert both stores return the same ids (as a set) for the given filter. */
    private fun assertParity(
        filter: Filter,
        message: String = "",
    ) {
        val sqliteIds = sqlite.query<Event>(filter).map { it.id }.toSet()
        val fsIds = fs.query<Event>(filter).map { it.id }.toSet()
        assertEquals(sqliteIds, fsIds, "parity mismatch: $message")
    }

    /** Same, but expect a stable DESC-by-createdAt ordering. */
    private fun assertParityOrdered(
        filter: Filter,
        message: String = "",
    ) {
        val sqliteIds = sqlite.query<Event>(filter).map { it.id }
        val fsIds = fs.query<Event>(filter).map { it.id }
        assertEquals(sqliteIds, fsIds, "ordered parity mismatch: $message")
    }

    private fun note(
        body: String,
        ts: Long,
        s: NostrSignerSync = signer,
    ): TextNoteEvent = s.sign(TextNoteEvent.build(body, createdAt = ts))

    private fun article(
        slug: String,
        body: String,
        ts: Long,
    ): LongTextNoteEvent =
        signer.sign(
            createdAt = ts,
            kind = LongTextNoteEvent.KIND,
            tags = arrayOf(arrayOf("d", slug)),
            content = body,
        )

    // ------------------------------------------------------------------
    // Basic insert / query
    // ------------------------------------------------------------------

    @Test
    fun `id lookup matches`() {
        val n = note("hello", 10)
        insertBoth(n)
        assertParity(Filter(ids = listOf(n.id)))
    }

    @Test
    fun `kind + author query matches`() {
        val a = note("a", 1)
        val b = note("b", 2)
        val c = note("c", 3, s = otherSigner)
        listOf(a, b, c).forEach(::insertBoth)

        assertParityOrdered(Filter(kinds = listOf(1), authors = listOf(signer.pubKey)))
        assertParityOrdered(Filter(authors = listOf(signer.pubKey, otherSigner.pubKey)))
    }

    @Test
    fun `since until limit match`() {
        repeat(10) { i -> insertBoth(note("n$i", i.toLong() + 1)) }
        assertParityOrdered(Filter(authors = listOf(signer.pubKey), since = 4, until = 8))
        assertParityOrdered(Filter(authors = listOf(signer.pubKey), limit = 3))
    }

    // ------------------------------------------------------------------
    // Tag indexing
    // ------------------------------------------------------------------

    @Test
    fun `single-letter tag queries match`() {
        val tagged =
            signer.sign<Event>(
                createdAt = 5,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr"), arrayOf("t", "bitcoin")),
                content = "x",
            )
        val plain = note("plain", 6)
        insertBoth(tagged)
        insertBoth(plain)

        assertParity(Filter(tags = mapOf("t" to listOf("nostr"))))
        assertParity(Filter(tags = mapOf("t" to listOf("nostr", "bitcoin"))))
    }

    // ------------------------------------------------------------------
    // Replaceable / Addressable
    // ------------------------------------------------------------------

    @Test
    fun `replaceable newer wins parity`() {
        val v1 =
            signer.sign<Event>(
                createdAt = 100,
                kind = 0,
                tags = emptyArray(),
                content = "{\"name\":\"v1\"}",
            )
        val v2 =
            signer.sign<Event>(
                createdAt = 200,
                kind = 0,
                tags = emptyArray(),
                content = "{\"name\":\"v2\"}",
            )
        insertBoth(v1)
        insertBoth(v2)

        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(0)))
        assertParity(Filter(ids = listOf(v1.id)))
    }

    @Test
    fun `replaceable older rejected parity`() {
        val newer =
            signer.sign<Event>(createdAt = 200, kind = 0, tags = emptyArray(), content = "{\"name\":\"new\"}")
        val older =
            signer.sign<Event>(createdAt = 100, kind = 0, tags = emptyArray(), content = "{\"name\":\"old\"}")
        insertBoth(newer)
        insertBoth(older)

        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(0)))
    }

    @Test
    fun `addressable d-tag dedup parity`() {
        val v1 = article("intro", "v1", 10)
        val v2 = article("intro", "v2", 20)
        val v3 = article("about", "bio", 15)
        insertBoth(v1)
        insertBoth(v2)
        insertBoth(v3)

        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))
    }

    // ------------------------------------------------------------------
    // Deletion (NIP-09)
    // ------------------------------------------------------------------

    @Test
    fun `deletion by id parity`() {
        val a = note("a", 10)
        val b = note("b", 20)
        insertBoth(a)
        insertBoth(b)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(a), createdAt = 30))
        insertBoth(del)

        assertParity(Filter(ids = listOf(a.id)))
        assertParity(Filter(ids = listOf(b.id)))
        assertParity(Filter(kinds = listOf(DeletionEvent.KIND)))

        // Re-insert blocked.
        insertBoth(a)
        assertParity(Filter(ids = listOf(a.id)))
    }

    @Test
    fun `deletion by address parity`() {
        val v = article("intro", "v1", 10)
        insertBoth(v)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v), createdAt = 20))
        insertBoth(del)

        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))

        // Older event at this address must be blocked, newer must pass.
        insertBoth(article("intro", "older", 5))
        insertBoth(article("intro", "newer", 100))

        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))
    }

    // ------------------------------------------------------------------
    // Expiration (NIP-40)
    // ------------------------------------------------------------------

    @Test
    fun `expiration sweep parity`() {
        // Build events with future-then-past expirations relative to now.
        val now =
            com.vitorpamplona.quartz.utils.TimeUtils
                .now()
        val expired =
            signer.sign<Event>(
                createdAt = now - 100,
                kind = 1,
                tags = arrayOf(arrayOf("expiration", (now - 50).toString())),
                content = "old",
            )
        val alive =
            signer.sign<Event>(
                createdAt = now - 100,
                kind = 1,
                tags = arrayOf(arrayOf("expiration", (now + 1_000_000).toString())),
                content = "still here",
            )
        insertBoth(expired) // both stores reject (already expired)
        insertBoth(alive)

        assertParity(Filter(ids = listOf(expired.id)))
        assertParity(Filter(ids = listOf(alive.id)))

        // Sweep both; alive survives.
        sqlite.deleteExpiredEvents()
        fs.deleteExpiredEvents()
        assertParity(Filter(ids = listOf(alive.id)))
    }

    // ------------------------------------------------------------------
    // Search (NIP-50)
    // ------------------------------------------------------------------

    @Test
    fun `search parity`() {
        val a = note("hello bitcoin", 1)
        val b = note("nostr only", 2)
        val c = note("bitcoin and nostr", 3)
        insertBoth(a)
        insertBoth(b)
        insertBoth(c)

        // Tokenizers differ slightly between SQLite FTS5 unicode61 and
        // our Kotlin port, so we stick to plain ASCII single-token queries
        // where both should agree.
        assertParity(Filter(search = "bitcoin"))
        assertParity(Filter(search = "nostr"))
    }

    // ------------------------------------------------------------------
    // Count
    // ------------------------------------------------------------------

    @Test
    fun `count parity across mixed stream`() {
        listOf(
            note("a", 1),
            note("b", 2),
            note("c", 3),
            note("from-other", 4, s = otherSigner),
        ).forEach(::insertBoth)

        val filter = Filter(authors = listOf(signer.pubKey))
        assertEquals(sqlite.count(filter), fs.count(filter))
    }

    // ------------------------------------------------------------------
    // Mixed kitchen-sink scenario
    // ------------------------------------------------------------------

    @Test
    fun `kitchen sink scenario`() {
        // Notes
        val n1 = note("first", 1)
        val n2 = note("second", 2)
        // Replaceable
        val meta1 =
            signer.sign<Event>(createdAt = 10, kind = 0, tags = emptyArray(), content = "{\"name\":\"v1\"}")
        val meta2 =
            signer.sign<Event>(createdAt = 20, kind = 0, tags = emptyArray(), content = "{\"name\":\"v2\"}")
        // Addressable
        val artA = article("a", "A v1", 30)
        val artB = article("b", "B v1", 30)
        val artBv2 = article("b", "B v2", 50)
        // Deletion of n1
        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(n1), createdAt = 40))

        listOf(n1, n2, meta1, meta2, artA, artB, artBv2, del).forEach(::insertBoth)

        // Snapshots that should match.
        assertParity(Filter(ids = listOf(n1.id)), "n1 deleted")
        assertParity(Filter(ids = listOf(n2.id)), "n2 alive")
        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(0)), "metadata winner")
        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)), "articles set")
        assertParity(Filter(authors = listOf(signer.pubKey), kinds = listOf(DeletionEvent.KIND)), "deletion present")
    }

    // ------------------------------------------------------------------
    // Multi-filter union
    // ------------------------------------------------------------------

    @Test
    fun `multi-filter union parity`() {
        val a = note("a", 1)
        val b = note("b", 2, s = otherSigner)
        insertBoth(a)
        insertBoth(b)

        val filters =
            listOf(
                Filter(authors = listOf(signer.pubKey)),
                Filter(authors = listOf(otherSigner.pubKey)),
            )

        assertEquals(
            sqlite.query<Event>(filters).map { it.id }.toSet(),
            fs.query<Event>(filters).map { it.id }.toSet(),
        )
    }

    // ------------------------------------------------------------------
    // Direct delete by filter
    // ------------------------------------------------------------------

    @Test
    fun `delete by filter parity`() {
        val toKill = note("dead", 5)
        val survivor = note("alive", 6)
        insertBoth(toKill)
        insertBoth(survivor)

        sqlite.delete(Filter(ids = listOf(toKill.id)))
        fs.delete(Filter(ids = listOf(toKill.id)))

        assertParity(Filter(authors = listOf(signer.pubKey)))
    }

    // ------------------------------------------------------------------
    // Helper: ensure SQLite store really does what we think
    // ------------------------------------------------------------------

    @Test
    fun `helper sanity - empty stores agree`() {
        assertParity(Filter(authors = listOf(signer.pubKey)))
        assertParity(Filter(kinds = listOf(1)))
    }

    @Suppress("unused")
    private fun debugDump(label: String): String {
        val sqIds =
            sqlite
                .query<Event>(Filter(authors = listOf(signer.pubKey, otherSigner.pubKey)))
                .map { "${it.kind}:${it.id.take(8)}@${it.createdAt}" }
                .sorted()
        val fsIds =
            fs
                .query<Event>(Filter(authors = listOf(signer.pubKey, otherSigner.pubKey)))
                .map { "${it.kind}:${it.id.take(8)}@${it.createdAt}" }
                .sorted()
        return "$label\n  sqlite: $sqIds\n  fs:     $fsIds"
    }

    @Suppress("unused")
    private fun ids(events: List<Event>): Set<HexKey> = events.map { it.id }.toSet()
}
