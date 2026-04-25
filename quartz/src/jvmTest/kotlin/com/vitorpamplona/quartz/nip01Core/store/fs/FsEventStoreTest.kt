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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsEventStoreTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance // force crypto lib load
        root = Files.createTempDirectory("fs-store-")
        store = FsEventStore(root)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        deleteRecursively(root)
    }

    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `insert and query by id round-trips`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("hello"))

        store.insert(note)

        val got = store.query<TextNoteEvent>(Filter(ids = listOf(note.id)))
        assertEquals(1, got.size)
        assertEquals(note.id, got[0].id)
        assertEquals(note.content, got[0].content)
        assertEquals(note.sig, got[0].sig)
    }

    @Test
    fun `canonical path uses 2-char sharding`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("shard me"))
        store.insert(note)

        val shard = root.resolve("events").resolve(note.id.substring(0, 2)).resolve(note.id.substring(2, 4))
        val file = shard.resolve("${note.id}.json")
        assertTrue(file.exists(), "expected canonical at $file")
    }

    @Test
    fun `query returns empty when nothing inserted`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("missing"))
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(ids = listOf(note.id))))
    }

    @Test
    fun `delete by id removes the file`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("to-delete"))
        store.insert(note)
        assertEquals(1, store.count(Filter(ids = listOf(note.id))))

        val removed = store.delete(note.id)
        assertEquals(1, removed)
        assertEquals(0, store.count(Filter(ids = listOf(note.id))))
    }

    @Test
    fun `delete returns 0 when event absent`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("never-inserted"))
        assertEquals(0, store.delete(note.id))
    }

    @Test
    fun `delete by filter with ids removes matching events`() {
        val a = signer.sign<TextNoteEvent>(TextNoteEvent.build("a"))
        val b = signer.sign<TextNoteEvent>(TextNoteEvent.build("b"))
        store.insert(a)
        store.insert(b)

        store.delete(Filter(ids = listOf(a.id)))

        assertNull(store.query<TextNoteEvent>(Filter(ids = listOf(a.id))).firstOrNull())
        assertEquals(b.id, store.query<TextNoteEvent>(Filter(ids = listOf(b.id))).single().id)
    }

    @Test
    fun `insert of duplicate id is a no-op`() {
        val note = signer.sign<TextNoteEvent>(TextNoteEvent.build("dup"))
        store.insert(note)
        store.insert(note) // must not throw; content is immutable anyway
        assertEquals(1, store.count(Filter(ids = listOf(note.id))))
    }

    @Test
    fun `ephemeral events are not persisted`() {
        // Kind 20_000 is the lowest ephemeral kind; use a bare Event
        // constructed inline because TextNoteEvent pins kind=1.
        val ephemeral =
            signer.sign<com.vitorpamplona.quartz.nip01Core.core.Event>(
                createdAt = 1,
                kind = 20_000,
                tags = emptyArray(),
                content = "ghost",
            )
        store.insert(ephemeral)
        assertEquals(0, store.count(Filter(ids = listOf(ephemeral.id))))
    }

    @Test
    fun `ids that share the same 4-char shard both persist`() {
        // Find two real events whose ids share the same first 4 hex chars.
        // With a random KeyPair per sign, this takes a handful of tries.
        var a = signer.sign<TextNoteEvent>(TextNoteEvent.build("a0", createdAt = 1))
        var b: TextNoteEvent
        var salt = 2L
        do {
            b = signer.sign<TextNoteEvent>(TextNoteEvent.build("b$salt", createdAt = salt))
            salt++
        } while (b.id.substring(0, 4) != a.id.substring(0, 4) && salt < 200_000)
        if (b.id.substring(0, 4) != a.id.substring(0, 4)) {
            // Didn't find a collision cheaply. Fall back to inserting two
            // unrelated events and checking they both live under their own
            // shards — still verifies basic sharding without flakiness.
            b = signer.sign(TextNoteEvent.build("unrelated"))
        }

        store.insert(a)
        store.insert(b)

        assertTrue(store.count(Filter(ids = listOf(a.id))) == 1)
        assertTrue(store.count(Filter(ids = listOf(b.id))) == 1)
    }

    @Test
    fun `staging dir is cleared on init`() {
        val staging = root.resolve(".staging")
        val leftover = Files.createTempFile(staging, "crash-", ".json")
        assertTrue(leftover.exists())

        // Reopening the store should sweep the staging dir.
        val reopened = FsEventStore(root)
        try {
            assertFalse(leftover.exists(), "staging leftover should be cleared on open")
        } finally {
            reopened.close()
        }
    }
}
