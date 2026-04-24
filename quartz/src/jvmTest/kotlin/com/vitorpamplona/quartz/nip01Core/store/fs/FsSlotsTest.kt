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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsSlotsTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-slot-")
        store = FsEventStore(root)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        if (root.exists()) {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    // ------------------------------------------------------------------
    // Replaceable (kind 0, 3, 10000-19999)
    // ------------------------------------------------------------------

    private fun metadata(
        name: String,
        createdAt: Long,
    ) = signer.sign<MetadataEvent>(
        createdAt = createdAt,
        kind = MetadataEvent.KIND,
        tags = emptyArray(),
        content = "{\"name\":\"$name\"}",
    )

    @Test
    fun `newer replaceable evicts older`() {
        val v1 = metadata("old", 100)
        val v2 = metadata("new", 200)
        store.insert(v1)
        store.insert(v2)

        // Only the newer survives a query by author.
        val got = store.query<MetadataEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(MetadataEvent.KIND)))
        assertEquals(listOf(v2.id), got.map { it.id })

        // The older canonical is gone.
        assertFalse(store.hasCanonical(v1.id), "older canonical should be removed")
    }

    @Test
    fun `older replaceable is rejected when newer exists`() {
        val newer = metadata("new", 200)
        val older = metadata("old", 100)
        store.insert(newer)
        store.insert(older)

        // Newer still wins.
        val got = store.query<MetadataEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(MetadataEvent.KIND)))
        assertEquals(listOf(newer.id), got.map { it.id })

        // And the older was never persisted.
        assertFalse(store.hasCanonical(older.id), "older should have been rejected")
    }

    @Test
    fun `equal timestamp replaceable is rejected`() {
        val a = metadata("a", 100)
        val b = metadata("b", 100)
        store.insert(a)
        store.insert(b)

        val got = store.query<MetadataEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(MetadataEvent.KIND)))
        assertEquals(1, got.size)
        assertEquals(a.id, got.single().id)
    }

    @Test
    fun `replaceable slot file contains the current winner`() {
        val v = metadata("only", 100)
        store.insert(v)

        val slot = root.resolve("replaceable/${MetadataEvent.KIND}/${signer.pubKey}.json")
        assertTrue(slot.exists(), "slot must exist")
        val parsed = Event.fromJson(slot.readText())
        assertEquals(v.id, parsed.id)
    }

    @Test
    fun `replaceable slot survives canonical deletion via hardlink`() {
        val v = metadata("x", 100)
        store.insert(v)

        // Simulate a user (or bug) removing the canonical file.
        val canonical =
            root
                .resolve("events")
                .resolve(v.id.substring(0, 2))
                .resolve(v.id.substring(2, 4))
                .resolve("${v.id}.json")
        assertTrue(Files.deleteIfExists(canonical))

        val slot = root.resolve("replaceable/${MetadataEvent.KIND}/${signer.pubKey}.json")
        assertTrue(slot.exists(), "slot should persist even if canonical is gone (hardlink to same inode)")
        val parsed = Event.fromJson(slot.readText())
        assertEquals(v.id, parsed.id)
    }

    @Test
    fun `eviction unlinks index hardlinks for the old winner`() {
        val v1 = metadata("old", 100)
        val v2 = metadata("new", 200)
        store.insert(v1)
        store.insert(v2)

        // Author index should have exactly one entry — the winner.
        val authorDir = root.resolve("idx/author/${signer.pubKey}")
        val entries =
            Files.list(authorDir).use { s ->
                s.toList().map { it.fileName.toString() }
            }
        assertEquals(1, entries.size, "author index should only hold the winner")
        assertTrue(entries.single().endsWith("-${v2.id}"), "author index entry must point at winner")
    }

    @Test
    fun `delete of current replaceable winner clears the slot`() {
        val v = metadata("only", 100)
        store.insert(v)

        val slot = root.resolve("replaceable/${MetadataEvent.KIND}/${signer.pubKey}.json")
        assertTrue(slot.exists())

        store.delete(v.id)
        assertFalse(slot.exists(), "slot should be cleared when winner is deleted")
    }

    // ------------------------------------------------------------------
    // Addressable (kinds 30000-39999)
    // ------------------------------------------------------------------

    private fun article(
        slug: String,
        body: String,
        createdAt: Long,
    ): LongTextNoteEvent =
        signer.sign(
            createdAt = createdAt,
            kind = LongTextNoteEvent.KIND,
            tags = arrayOf(arrayOf("d", slug)),
            content = body,
        )

    @Test
    fun `newer addressable evicts older for same d-tag`() {
        val v1 = article("intro", "draft 1", 10)
        val v2 = article("intro", "draft 2", 20)
        store.insert(v1)
        store.insert(v2)

        val got = store.query<LongTextNoteEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))
        assertEquals(listOf(v2.id), got.map { it.id })
        assertFalse(store.hasCanonical(v1.id), "older draft canonical should be removed")
    }

    @Test
    fun `addressable with different d-tags coexist`() {
        val intro = article("intro", "hello", 10)
        val about = article("about", "bio", 15)
        store.insert(intro)
        store.insert(about)

        val got = store.query<LongTextNoteEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))
        assertEquals(setOf(intro.id, about.id), got.map { it.id }.toSet())
    }

    @Test
    fun `older addressable is rejected when newer exists`() {
        val newer = article("slug", "new", 200)
        val older = article("slug", "old", 100)
        store.insert(newer)
        store.insert(older)

        val got = store.query<LongTextNoteEvent>(Filter(authors = listOf(signer.pubKey)))
        assertEquals(listOf(newer.id), got.map { it.id })
    }

    @Test
    fun `addressable slot file contains the current winner`() {
        val v = article("intro", "hello", 10)
        store.insert(v)

        val dHash = FsLayout.sha256Hex("intro")
        val slot = root.resolve("addressable/${LongTextNoteEvent.KIND}/${signer.pubKey}/$dHash.json")
        assertTrue(slot.exists())
        val parsed = Event.fromJson(slot.readText())
        assertEquals(v.id, parsed.id)
    }

    @Test
    fun `empty d-tag gets its own slot`() {
        val v = article("", "homepage", 1)
        store.insert(v)

        val dHash = FsLayout.sha256Hex("")
        val slot = root.resolve("addressable/${LongTextNoteEvent.KIND}/${signer.pubKey}/$dHash.json")
        assertTrue(slot.exists())
    }

    @Test
    fun `delete of current addressable winner clears the slot`() {
        val v = article("intro", "hello", 10)
        store.insert(v)
        val dHash = FsLayout.sha256Hex("intro")
        val slot = root.resolve("addressable/${LongTextNoteEvent.KIND}/${signer.pubKey}/$dHash.json")
        assertTrue(slot.exists())

        store.delete(v.id)
        assertFalse(slot.exists())
    }

    // ------------------------------------------------------------------
    // Non-replaceable events: no slot involvement
    // ------------------------------------------------------------------

    @Test
    fun `regular text note has no slot`() {
        val note =
            signer.sign<Event>(
                createdAt = 1,
                kind = 1,
                tags = emptyArray(),
                content = "plain",
            )
        store.insert(note)

        // No entries under replaceable/ or addressable/ — only the scaffolded dirs exist.
        val replaceableDir = root.resolve("replaceable")
        val addressableDir = root.resolve("addressable")
        assertEquals(
            0,
            Files.walk(replaceableDir).use { s -> s.filter { Files.isRegularFile(it) }.count() },
        )
        assertEquals(
            0,
            Files.walk(addressableDir).use { s -> s.filter { Files.isRegularFile(it) }.count() },
        )
    }

    // helper — check canonical existence
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
