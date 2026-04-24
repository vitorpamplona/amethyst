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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsDeletionTest {
    private val signer = NostrSignerSync()
    private val otherSigner = NostrSignerSync()
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-del-")
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
        signer: NostrSignerSync = this.signer,
    ) = signer.sign<TextNoteEvent>(TextNoteEvent.build(body, createdAt = ts))

    private fun article(
        slug: String,
        body: String,
        ts: Long,
    ) = signer.sign<LongTextNoteEvent>(
        createdAt = ts,
        kind = LongTextNoteEvent.KIND,
        tags = arrayOf(arrayOf("d", slug)),
        content = body,
    )

    // ------------------------------------------------------------------
    // Delete by id
    // ------------------------------------------------------------------

    @Test
    fun `kind-5 cascade-deletes a target by id`() {
        val n1 = note("one", 10)
        val n2 = note("two", 20)
        store.insert(n1)
        store.insert(n2)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(n1), createdAt = 30))
        store.insert(del)

        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(ids = listOf(n1.id))).map { it.id })
        assertEquals(listOf(n2.id), store.query<TextNoteEvent>(Filter(ids = listOf(n2.id))).map { it.id })
        assertEquals(listOf(del.id), store.query<DeletionEvent>(Filter(ids = listOf(del.id))).map { it.id })
    }

    @Test
    fun `deletion blocks re-insertion of the same id`() {
        val n1 = note("one", 10)
        store.insert(n1)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(n1), createdAt = 30))
        store.insert(del)

        store.insert(n1) // should be blocked
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(ids = listOf(n1.id))).map { it.id })
    }

    @Test
    fun `deletion by non-author does not cascade but still installs id tombstone`() {
        // other signer authors a note
        val theirs = note("not yours", 10, signer = otherSigner)
        store.insert(theirs)

        // Our signer attempts to delete it.
        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(theirs), createdAt = 30))
        store.insert(del)

        // Cascade did NOT run — not our author.
        assertEquals(listOf(theirs.id), store.query<TextNoteEvent>(Filter(ids = listOf(theirs.id))).map { it.id })

        // But a tombstone is installed for the id, blocking future re-inserts.
        // Re-inserting the note is effectively a no-op because it already
        // exists. Instead we delete then try to re-insert.
        store.delete(theirs.id)
        store.insert(theirs)
        assertEquals(emptyList(), store.query<TextNoteEvent>(Filter(ids = listOf(theirs.id))).map { it.id })
    }

    // ------------------------------------------------------------------
    // Delete by address (addressable)
    // ------------------------------------------------------------------

    @Test
    fun `kind-5 by address cascades addressable slot`() {
        val v1 = article("intro", "draft 1", 10)
        val v2 = article("intro", "draft 2", 20)
        store.insert(v1)
        store.insert(v2)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v2), createdAt = 30))
        store.insert(del)

        // Slot cleared, canonical removed, indexes gone.
        val dHash = FsLayout.sha256Hex("intro")
        val slot = root.resolve("addressable/${LongTextNoteEvent.KIND}/${signer.pubKey}/$dHash.json")
        assertFalse(slot.exists(), "addressable slot should be cleared")
        assertEquals(emptyList(), store.query<LongTextNoteEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND))).map { it.id })
    }

    @Test
    fun `newer event at a deleted address may pass the cutoff`() {
        val v1 = article("intro", "draft 1", 10)
        store.insert(v1)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v1), createdAt = 20))
        store.insert(del)

        // A newer addressable at the same address should still be accepted.
        val v3 = article("intro", "draft 3", 30)
        store.insert(v3)

        val got = store.query<LongTextNoteEvent>(Filter(authors = listOf(signer.pubKey), kinds = listOf(LongTextNoteEvent.KIND)))
        assertEquals(listOf(v3.id), got.map { it.id })
    }

    @Test
    fun `older event at a deleted address is blocked by cutoff`() {
        val v1 = article("intro", "draft 1", 10)
        store.insert(v1)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v1), createdAt = 20))
        store.insert(del)

        // Attempting to re-insert an event authored earlier than the deletion should fail.
        val older = article("intro", "even-older", 5)
        store.insert(older)
        assertEquals(emptyList(), store.query<LongTextNoteEvent>(Filter(ids = listOf(older.id))).map { it.id })
    }

    @Test
    fun `equal-timestamp event at a deleted address is blocked`() {
        val v = article("intro", "v", 10)
        store.insert(v)

        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v), createdAt = 15))
        store.insert(del)

        val equal = article("intro", "equal", 15)
        store.insert(equal)
        assertEquals(emptyList(), store.query<LongTextNoteEvent>(Filter(ids = listOf(equal.id))).map { it.id })
    }

    // ------------------------------------------------------------------
    // Multiple deletions: strongest cutoff wins
    // ------------------------------------------------------------------

    @Test
    fun `later kind-5 raises the address cutoff`() {
        val v = article("intro", "v", 10)
        store.insert(v)

        val del1 = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v), createdAt = 20))
        store.insert(del1)

        val del2Target = article("intro", "v2", 30) // inserted only to give del2 a target
        store.insert(del2Target)
        val del2 = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(del2Target), createdAt = 40))
        store.insert(del2)

        // Cutoff should now be 40, so an event at createdAt=35 is blocked.
        val mid = article("intro", "mid", 35)
        store.insert(mid)
        assertEquals(emptyList(), store.query<LongTextNoteEvent>(Filter(ids = listOf(mid.id))).map { it.id })
    }

    @Test
    fun `earlier kind-5 does not lower an existing stronger cutoff`() {
        val v1 = article("slug", "v1", 10)
        val v2 = article("slug", "v2", 20)
        store.insert(v1)
        store.insert(v2)

        val strongDel = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(v2), createdAt = 100))
        store.insert(strongDel)

        // Now insert a weaker (earlier) deletion for the same address.
        val weakTarget = article("slug", "target-for-weak", 30)
        store.insert(weakTarget) // this passes? No: cutoff=100, target@30 is blocked. Actually we want to
        // construct a DeletionEvent that targets the slug address directly. The simplest way:
        val weakDel =
            signer.sign<DeletionEvent>(
                DeletionEvent.buildAddressOnly(listOf(v1), createdAt = 50),
            )
        store.insert(weakDel)

        // Cutoff should still be 100 — an event at 60 must still be blocked.
        val blocked = article("slug", "should-be-blocked", 60)
        store.insert(blocked)
        assertEquals(emptyList(), store.query<LongTextNoteEvent>(Filter(ids = listOf(blocked.id))).map { it.id })
    }

    // ------------------------------------------------------------------
    // Deletion event itself remains queryable
    // ------------------------------------------------------------------

    @Test
    fun `deletion event itself is indexed and queryable`() {
        val n = note("x", 10)
        store.insert(n)
        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(n), createdAt = 20))
        store.insert(del)

        val byKind = store.query<DeletionEvent>(Filter(kinds = listOf(DeletionEvent.KIND)))
        assertEquals(listOf(del.id), byKind.map { it.id })
    }

    // ------------------------------------------------------------------
    // Tombstone files use hardlinks to the kind-5 canonical
    // ------------------------------------------------------------------

    @Test
    fun `id tombstone is a hardlink to the kind-5 event`() {
        val n = note("x", 10)
        store.insert(n)
        val del = signer.sign<DeletionEvent>(DeletionEvent.build(listOf(n), createdAt = 20))
        store.insert(del)

        val tomb = root.resolve("tombstones/id/${n.id}.json")
        assertTrue(tomb.exists())
        val canonical = root.resolve("events/${del.id.substring(0, 2)}/${del.id.substring(2, 4)}/${del.id}.json")
        assertEquals(
            Files.readAttributes(tomb, java.nio.file.attribute.BasicFileAttributes::class.java).fileKey(),
            Files.readAttributes(canonical, java.nio.file.attribute.BasicFileAttributes::class.java).fileKey(),
            "tombstone and kind-5 canonical should share an inode",
        )
    }
}
