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
import kotlin.test.assertTrue

class FsQueryTest {
    private val signerA = NostrSignerSync()
    private val signerB = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-query-")
        store = FsEventStore(root)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        if (root.exists()) {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    private fun signA(
        text: String,
        ts: Long,
    ) = signerA.sign<TextNoteEvent>(TextNoteEvent.build(text, createdAt = ts))

    private fun signB(
        text: String,
        ts: Long,
    ) = signerB.sign<TextNoteEvent>(TextNoteEvent.build(text, createdAt = ts))

    // ------------------------------------------------------------------
    // Sort + limit
    // ------------------------------------------------------------------

    @Test
    fun `results ordered by created_at DESC`() {
        val a = signA("a", 1)
        val b = signA("b", 3)
        val c = signA("c", 2)
        listOf(a, b, c).forEach(store::insert)

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signerA.pubKey)))
        assertEquals(listOf(b.id, c.id, a.id), got.map { it.id })
    }

    @Test
    fun `limit caps the result count`() {
        repeat(5) { i -> store.insert(signA("n$i", i.toLong() + 1)) }
        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signerA.pubKey), limit = 2))
        assertEquals(2, got.size)
        // Highest timestamps come first.
        assertEquals("n4", got[0].content)
        assertEquals("n3", got[1].content)
    }

    @Test
    fun `limit of zero returns empty`() {
        store.insert(signA("x", 1))
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(authors = listOf(signerA.pubKey), limit = 0)))
    }

    // ------------------------------------------------------------------
    // Author / kind drivers
    // ------------------------------------------------------------------

    @Test
    fun `author filter isolates one user`() {
        val a = signA("from-a", 1)
        val b = signB("from-b", 2)
        store.insert(a)
        store.insert(b)

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signerA.pubKey)))
        assertEquals(listOf(a.id), got.map { it.id })
    }

    @Test
    fun `author filter with multiple authors unions them`() {
        val a = signA("a", 1)
        val b = signB("b", 2)
        store.insert(a)
        store.insert(b)

        val got = store.query<TextNoteEvent>(Filter(authors = listOf(signerA.pubKey, signerB.pubKey)))
        assertEquals(setOf(a.id, b.id), got.map { it.id }.toSet())
    }

    @Test
    fun `kind filter returns only the requested kinds`() {
        // Build two events of different kinds.
        val note = signA("note", 1)
        val ephemeralKinds = signerA.sign<Event>(createdAt = 2, kind = 30023, tags = arrayOf(arrayOf("d", "slug")), content = "article")
        store.insert(note)
        store.insert(ephemeralKinds)

        val onlyNotes = store.query<Event>(Filter(kinds = listOf(1)))
        assertEquals(listOf(note.id), onlyNotes.map { it.id })

        val onlyArticles = store.query<Event>(Filter(kinds = listOf(30023)))
        assertEquals(listOf(ephemeralKinds.id), onlyArticles.map { it.id })
    }

    @Test
    fun `kind + author intersect via post-filter`() {
        val a = signA("a", 1)
        val b = signB("b", 2)
        store.insert(a)
        store.insert(b)

        val got = store.query<TextNoteEvent>(Filter(kinds = listOf(1), authors = listOf(signerA.pubKey)))
        assertEquals(listOf(a.id), got.map { it.id })
    }

    // ------------------------------------------------------------------
    // Tag driver
    // ------------------------------------------------------------------

    @Test
    fun `tag filter matches single-letter tags`() {
        val tagged =
            signerA.sign<Event>(
                createdAt = 10,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr"), arrayOf("t", "bitcoin")),
                content = "tagged",
            )
        val untagged = signA("plain", 5)
        store.insert(tagged)
        store.insert(untagged)

        val got = store.query<Event>(Filter(tags = mapOf("t" to listOf("nostr"))))
        assertEquals(listOf(tagged.id), got.map { it.id })
    }

    @Test
    fun `tag OR within key returns union`() {
        val t1 = signerA.sign<Event>(createdAt = 1, kind = 1, tags = arrayOf(arrayOf("t", "nostr")), content = "n")
        val t2 = signerA.sign<Event>(createdAt = 2, kind = 1, tags = arrayOf(arrayOf("t", "bitcoin")), content = "b")
        val t3 = signerA.sign<Event>(createdAt = 3, kind = 1, tags = arrayOf(arrayOf("t", "other")), content = "o")
        listOf(t1, t2, t3).forEach(store::insert)

        val got = store.query<Event>(Filter(tags = mapOf("t" to listOf("nostr", "bitcoin"))))
        assertEquals(setOf(t1.id, t2.id), got.map { it.id }.toSet())
    }

    @Test
    fun `tagsAll across keys requires all matches`() {
        val both =
            signerA.sign<Event>(
                createdAt = 1,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr"), arrayOf("e", "a".repeat(64))),
                content = "both",
            )
        val onlyT = signerA.sign<Event>(createdAt = 2, kind = 1, tags = arrayOf(arrayOf("t", "nostr")), content = "t-only")
        val onlyE = signerA.sign<Event>(createdAt = 3, kind = 1, tags = arrayOf(arrayOf("e", "a".repeat(64))), content = "e-only")
        listOf(both, onlyT, onlyE).forEach(store::insert)

        val got =
            store.query<Event>(
                Filter(
                    tagsAll = mapOf("t" to listOf("nostr"), "e" to listOf("a".repeat(64))),
                ),
            )
        assertEquals(listOf(both.id), got.map { it.id })
    }

    // ------------------------------------------------------------------
    // Tag-value directory naming: raw when fs-safe, _h_<hash> otherwise.
    // ------------------------------------------------------------------

    @Test
    fun `safe ASCII tag values get raw directory names`() {
        val e =
            signerA.sign<Event>(
                createdAt = 10,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr")),
                content = "x",
            )
        store.insert(e)
        // The raw value is the directory name — directly inspectable.
        val rawDir = root.resolve("idx/tag/t/nostr")
        assertTrue(rawDir.exists(), "ASCII-safe tag should land in idx/tag/t/nostr/")
        assertEquals(1, rawDir.listDirectoryEntries().size)
    }

    @Test
    fun `pubkey p-tag uses raw 64-hex directory name`() {
        // The motivating case: notifications. p-tags pointing at a
        // pubkey land under idx/tag/p/<pubkey>/ — no hash, directly
        // ls-able.
        val target = signerB.pubKey
        val e =
            signerA.sign<Event>(
                createdAt = 5,
                kind = 1,
                tags = arrayOf(arrayOf("p", target)),
                content = "@you",
            )
        store.insert(e)
        val pDir = root.resolve("idx/tag/p/$target")
        assertTrue(pDir.exists(), "p-tag pubkey should be ls-able directly: idx/tag/p/$target/")
        assertEquals(1, pDir.listDirectoryEntries().size)
    }

    @Test
    fun `tag value with emoji falls back to hashed directory name`() {
        val e =
            signerA.sign<Event>(
                createdAt = 10,
                kind = 1,
                tags = arrayOf(arrayOf("t", "🔥")),
                content = "x",
            )
        store.insert(e)
        // Exactly one entry under t/ and it must be in the _h_ hash
        // bucket — emoji is not fs-safe.
        val tDir = root.resolve("idx/tag/t")
        val entries = tDir.listDirectoryEntries().map { it.fileName.toString() }
        assertEquals(1, entries.size, "expected one bucket dir, got: $entries")
        assertTrue(entries.single().startsWith("_h_"), "emoji tag must hash; got '${entries.single()}'")
    }

    @Test
    fun `tag value containing a slash falls back to hashed directory name`() {
        val e =
            signerA.sign<Event>(
                createdAt = 10,
                kind = 1,
                tags = arrayOf(arrayOf("r", "https://example.com/page")),
                content = "x",
            )
        store.insert(e)
        val rDir = root.resolve("idx/tag/r")
        val entries = rDir.listDirectoryEntries().map { it.fileName.toString() }
        assertEquals(1, entries.size, "expected one bucket dir, got: $entries")
        assertTrue(entries.single().startsWith("_h_"), "URL tag must hash; got '${entries.single()}'")
    }

    @Test
    fun `query round-trips for both raw and hashed values`() {
        // Each query must use the same naming rule as the writer or it
        // walks a directory that doesn't exist. Insert both a raw-safe
        // and a hash-required tag and verify they're both findable.
        val safe =
            signerA.sign<Event>(
                createdAt = 1,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr")),
                content = "safe",
            )
        val unsafe =
            signerA.sign<Event>(
                createdAt = 2,
                kind = 1,
                tags = arrayOf(arrayOf("t", "🔥")),
                content = "unsafe",
            )
        store.insert(safe)
        store.insert(unsafe)
        assertEquals(
            listOf(safe.id),
            store.query<Event>(Filter(tags = mapOf("t" to listOf("nostr")))).map { it.id },
        )
        assertEquals(
            listOf(unsafe.id),
            store.query<Event>(Filter(tags = mapOf("t" to listOf("🔥")))).map { it.id },
        )
    }

    @Test
    fun `non-single-letter tags are not reverse-indexed`() {
        // SQLite parity: DefaultIndexingStrategy only indexes single-letter
        // tag names, so a tag-driven query for `mytag = foo` finds no
        // candidates. The event is still persisted and can be fetched via
        // id / author / kind — just not via a reverse tag lookup.
        val e =
            signerA.sign<Event>(
                createdAt = 1,
                kind = 1,
                tags = arrayOf(arrayOf("mytag", "foo")),
                content = "x",
            )
        store.insert(e)

        assertEquals(emptyList(), store.query<Event>(Filter(tags = mapOf("mytag" to listOf("foo")))).map { it.id })
        assertEquals(listOf(e.id), store.query<Event>(Filter(authors = listOf(signerA.pubKey))).map { it.id })
        assertEquals(listOf(e.id), store.query<Event>(Filter(ids = listOf(e.id))).map { it.id })
    }

    // ------------------------------------------------------------------
    // since / until
    // ------------------------------------------------------------------

    @Test
    fun `since and until window filter`() {
        val e1 = signA("t1", 100)
        val e2 = signA("t2", 200)
        val e3 = signA("t3", 300)
        listOf(e1, e2, e3).forEach(store::insert)

        val got = store.query<TextNoteEvent>(Filter(since = 150, until = 250))
        assertEquals(listOf(e2.id), got.map { it.id })
    }

    // ------------------------------------------------------------------
    // count
    // ------------------------------------------------------------------

    @Test
    fun `count matches query size`() {
        repeat(4) { i -> store.insert(signA("n$i", i.toLong() + 1)) }
        val filter = Filter(authors = listOf(signerA.pubKey))
        assertEquals(store.query<TextNoteEvent>(filter).size, store.count(filter))
    }

    // ------------------------------------------------------------------
    // Index hardlink maintenance
    // ------------------------------------------------------------------

    @Test
    fun `insert creates hardlinks in every expected index dir`() {
        val tagged =
            signerA.sign<Event>(
                createdAt = 42,
                kind = 1,
                tags = arrayOf(arrayOf("t", "nostr")),
                content = "x",
            )
        store.insert(tagged)

        val kindDir = root.resolve("idx/kind/1")
        val authorDir = root.resolve("idx/author/${signerA.pubKey}")
        assertTrue(kindDir.exists() && kindDir.listDirectoryEntries().size == 1, "kind index missing")
        assertTrue(authorDir.exists() && authorDir.listDirectoryEntries().size == 1, "author index missing")
        val tagNameDir = root.resolve("idx/tag/t")
        assertTrue(tagNameDir.exists(), "tag 't' dir missing")
        val tagValueDirs = tagNameDir.listDirectoryEntries()
        assertEquals(1, tagValueDirs.size, "exactly one tag-value subdir expected")
        assertEquals(1, tagValueDirs[0].listDirectoryEntries().size, "tag-value dir should contain one entry")
    }

    @Test
    fun `delete removes hardlinks so directories become empty`() {
        val e = signerA.sign<Event>(createdAt = 1, kind = 1, tags = arrayOf(arrayOf("t", "nostr")), content = "x")
        store.insert(e)
        store.delete(e.id)

        val kindDir = root.resolve("idx/kind/1")
        val authorDir = root.resolve("idx/author/${signerA.pubKey}")
        val tagValueDirs =
            root
                .resolve("idx/tag/t")
                .takeIf { it.exists() }
                ?.listDirectoryEntries()
                .orEmpty()

        // Directories may remain as empty husks — what matters is the entries are gone.
        if (kindDir.exists()) assertEquals(0, kindDir.listDirectoryEntries().size, "kind entry leaked")
        if (authorDir.exists()) assertEquals(0, authorDir.listDirectoryEntries().size, "author entry leaked")
        tagValueDirs.forEach { assertEquals(0, it.listDirectoryEntries().size, "tag entry leaked") }
    }

    // ------------------------------------------------------------------
    // Seed persistence across reopen
    // ------------------------------------------------------------------

    @Test
    fun `reopening the store preserves queryability`() {
        val tagged = signerA.sign<Event>(createdAt = 1, kind = 1, tags = arrayOf(arrayOf("t", "nostr")), content = "x")
        store.insert(tagged)
        store.close()

        val reopened = FsEventStore(root)
        try {
            val got = reopened.query<Event>(Filter(tags = mapOf("t" to listOf("nostr"))))
            assertEquals(listOf(tagged.id), got.map { it.id })
        } finally {
            reopened.close()
        }
    }
}
