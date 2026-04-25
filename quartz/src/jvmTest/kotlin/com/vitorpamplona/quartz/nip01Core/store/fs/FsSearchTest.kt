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

import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsSearchTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-search-")
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
    // Tokenizer behaviour
    // ------------------------------------------------------------------

    @Test
    fun `tokenizer splits on whitespace and punctuation`() {
        assertEquals(setOf("hello", "world"), FsSearchTokenizer.tokenize("hello, world!"))
    }

    @Test
    fun `tokenizer is case insensitive`() {
        assertEquals(FsSearchTokenizer.tokenize("Bitcoin"), FsSearchTokenizer.tokenize("BITCOIN"))
        assertEquals(FsSearchTokenizer.tokenize("Bitcoin"), FsSearchTokenizer.tokenize("bitcoin"))
    }

    @Test
    fun `tokenizer handles empty and punctuation-only strings`() {
        assertEquals(emptySet<String>(), FsSearchTokenizer.tokenize(""))
        assertEquals(emptySet<String>(), FsSearchTokenizer.tokenize("..."))
        assertEquals(emptySet<String>(), FsSearchTokenizer.tokenize("   "))
    }

    @Test
    fun `tokenizer keeps unicode letters`() {
        assertEquals(setOf("café", "über"), FsSearchTokenizer.tokenize("Café Über"))
    }

    // ------------------------------------------------------------------
    // Index maintenance
    // ------------------------------------------------------------------

    @Test
    fun `searchable event creates one fts entry per unique token`() {
        val n = note("bitcoin nostr bitcoin", ts = 100)
        store.insert(n)

        val ftsRoot = root.resolve("idx/fts")
        val tokenDirs = ftsRoot.listDirectoryEntries().map { it.fileName.toString() }.toSet()
        // TextNoteEvent.indexableContent() prepends a "Subject: " prefix so
        // we get the content tokens plus the subject ones. What matters is
        // that each unique token yields exactly one entry under its dir.
        assertTrue("bitcoin" in tokenDirs)
        assertTrue("nostr" in tokenDirs)
        assertEquals(1, ftsRoot.resolve("bitcoin").listDirectoryEntries().size)
        assertEquals(1, ftsRoot.resolve("nostr").listDirectoryEntries().size)
    }

    @Test
    fun `non-searchable event does not produce fts entries`() {
        val meta =
            signer.sign<MetadataEvent>(
                createdAt = 1,
                kind = MetadataEvent.KIND,
                tags = emptyArray(),
                content = "{\"name\":\"vitor\"}",
            )
        store.insert(meta)

        val ftsRoot = root.resolve("idx/fts")
        assertEquals(0, ftsRoot.listDirectoryEntries().size, "MetadataEvent is not SearchableEvent")
    }

    @Test
    fun `delete removes fts entries`() {
        val n = note("bitcoin nostr", ts = 100)
        store.insert(n)
        store.delete(n.id)

        val ftsRoot = root.resolve("idx/fts")
        // Token directories may remain as empty husks.
        for (tokenDir in ftsRoot.listDirectoryEntries()) {
            assertEquals(0, tokenDir.listDirectoryEntries().size, "token entry leaked: $tokenDir")
        }
    }

    // ------------------------------------------------------------------
    // Search query semantics
    // ------------------------------------------------------------------

    @Test
    fun `single-token search returns the matching event`() {
        val a = note("bitcoin is fun", ts = 1)
        val b = note("nostr is also fun", ts = 2)
        store.insert(a)
        store.insert(b)

        val got = store.query<TextNoteEvent>(Filter(search = "bitcoin"))
        assertEquals(listOf(a.id), got.map { it.id })
    }

    @Test
    fun `multi-token search is AND across tokens`() {
        val a = note("bitcoin only", ts = 1)
        val b = note("nostr only", ts = 2)
        val c = note("bitcoin and nostr", ts = 3)
        store.insert(a)
        store.insert(b)
        store.insert(c)

        val got = store.query<TextNoteEvent>(Filter(search = "bitcoin nostr"))
        assertEquals(listOf(c.id), got.map { it.id }, "AND semantics: only the doc with both tokens matches")
    }

    @Test
    fun `search results are ordered by createdAt DESC`() {
        val older = note("bitcoin first", ts = 10)
        val newer = note("bitcoin again", ts = 20)
        store.insert(older)
        store.insert(newer)

        val got = store.query<TextNoteEvent>(Filter(search = "bitcoin"))
        assertEquals(listOf(newer.id, older.id), got.map { it.id })
    }

    @Test
    fun `search respects limit`() {
        repeat(5) { i -> store.insert(note("bitcoin doc $i", ts = i.toLong() + 1)) }
        val got = store.query<TextNoteEvent>(Filter(search = "bitcoin", limit = 2))
        assertEquals(2, got.size)
    }

    @Test
    fun `search composes with kinds and authors via post-filter`() {
        val match = note("bitcoin maximalism", ts = 5)
        store.insert(match)

        val got =
            store.query<TextNoteEvent>(
                Filter(search = "bitcoin", kinds = listOf(1), authors = listOf(signer.pubKey)),
            )
        assertEquals(listOf(match.id), got.map { it.id })

        val miss =
            store.query<TextNoteEvent>(
                Filter(search = "bitcoin", kinds = listOf(2)),
            )
        assertEquals(emptyList(), miss.map { it.id })
    }

    @Test
    fun `search with no matching token returns empty`() {
        store.insert(note("nostr only", ts = 1))
        assertEquals(
            emptyList(),
            store.query<TextNoteEvent>(Filter(search = "bitcoin")).map { it.id },
        )
    }

    @Test
    fun `blank search string is ignored`() {
        val a = note("anything", ts = 1)
        store.insert(a)
        // Blank search shouldn't drive by FTS — the planner falls through
        // to all-kinds, and the event surfaces.
        val got = store.query<TextNoteEvent>(Filter(search = "   "))
        assertEquals(listOf(a.id), got.map { it.id })
    }

    @Test
    fun `search survives reopen`() {
        val n = note("persistent token", ts = 100)
        store.insert(n)
        store.close()

        val reopened = FsEventStore(root)
        try {
            val got = reopened.query<TextNoteEvent>(Filter(search = "persistent"))
            assertEquals(listOf(n.id), got.map { it.id })
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------------
    // Maintenance under replaceable / deletion / vanish
    // ------------------------------------------------------------------

    @Test
    fun `fts entry is unlinked when event is deleted`() {
        val n = note("unique-token-zzz", ts = 1)
        store.insert(n)
        assertTrue(root.resolve("idx/fts/unique").exists())
        assertTrue(root.resolve("idx/fts/token").exists())
        assertTrue(root.resolve("idx/fts/zzz").exists())

        store.delete(n.id)
        assertFalse(
            root.resolve("idx/fts/zzz").let { it.exists() && it.listDirectoryEntries().isNotEmpty() },
            "zzz token entry should be unlinked",
        )

        // And a search no longer finds it.
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(search = "zzz")).map { it.id })
    }
}
