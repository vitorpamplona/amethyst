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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `authorsMissingOutbox()` against [FsEventStore], which does NOT override the
 * method — so this is the ONLY coverage of the `IEventStore` interface DEFAULT
 * implementation (the SQLite tests always hit the override). It pins the
 * default's behaviour, and asserts it agrees with the same scenarios the SQLite
 * suite checks: 10002 exclusion, NIP-09 deletion re-exposing an author, and the
 * giftwrap-sender carve-out.
 */
class FsAuthorsMissingOutboxTest {
    private lateinit var root: Path
    private lateinit var store: FsEventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-missing-outbox-")
        store = FsEventStore(root)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        if (root.exists()) {
            Files.walk(root).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    @Test
    fun defaultImplReportsOnlyAuthorsWithoutOutbox() =
        runBlocking {
            val withOutbox = NostrSignerSync()
            val noOutbox = NostrSignerSync()

            store.insert(withOutbox.sign(TextNoteEvent.build("a")))
            store.insert(AdvertisedRelayListEvent.create(emptyList(), withOutbox))
            store.insert(noOutbox.sign(TextNoteEvent.build("b")))

            assertEquals(setOf(noOutbox.pubKey), store.authorsMissingOutbox().toSet())
        }

    @Test
    fun defaultImplExcludesGiftWrapSenders() =
        runBlocking {
            val noteAuthor = NostrSignerSync()
            store.insert(noteAuthor.sign(TextNoteEvent.build("hi")))
            store.insert(
                EventFactory.create("bb".repeat(32), "aa".repeat(32), 1L, GiftWrapEvent.KIND, emptyArray(), "", "00".repeat(64)),
            )

            assertEquals(setOf(noteAuthor.pubKey), store.authorsMissingOutbox().toSet())
        }

    @Test
    fun defaultImplReExposesAuthorAfterOutboxDeleted() =
        runBlocking {
            val signer = NostrSignerSync()
            store.insert(signer.sign(TextNoteEvent.build("content")))
            val relayList = AdvertisedRelayListEvent.create(emptyList(), signer)
            store.insert(relayList)
            assertEquals(emptySet(), store.authorsMissingOutbox().toSet())

            store.insert(signer.sign(DeletionEvent.build(listOf(relayList))))
            assertEquals(setOf(signer.pubKey), store.authorsMissingOutbox().toSet())
        }
}
