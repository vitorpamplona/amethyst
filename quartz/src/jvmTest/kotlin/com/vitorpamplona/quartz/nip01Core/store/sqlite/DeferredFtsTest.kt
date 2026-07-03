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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract of [IndexingStrategy.deferFullTextSearchIndexing]: inserts skip
 * tokenization; [EventStore.ftsCatchUp] indexes from the persisted
 * watermark, idempotently and resumably; search sees everything once the
 * catch-up reports done.
 */
class DeferredFtsTest {
    private val signer = NostrSignerSync()
    private lateinit var dbFile: Path
    private lateinit var store: EventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        dbFile = Files.createTempFile("deferred-fts-", ".db")
        Files.deleteIfExists(dbFile)
        store =
            EventStore(
                dbName = dbFile.toAbsolutePath().toString(),
                relay = null,
                indexStrategy = DefaultIndexingStrategy(deferFullTextSearchIndexing = true),
            )
    }

    @AfterTest
    fun tearDown() {
        store.close()
        dbFile.deleteIfExists()
    }

    private fun note(
        text: String,
        createdAt: Long,
    ): Event = signer.sign(TextNoteEvent.build(text, createdAt = createdAt))

    private suspend fun search(term: String): List<Event> = store.query(Filter(search = term))

    @Test
    fun deferThenCatchUpMakesEventsSearchable() =
        runBlocking {
            assertTrue(store.needsFtsCatchUp)

            store.insert(note("the purple ostrich flies at midnight", 1000))
            store.insert(note("nothing to see here", 1001))

            // Deferred: not tokenized yet, so search finds nothing…
            assertEquals(0, search("ostrich").size)

            // …until the catch-up drains.
            while (!store.ftsCatchUp(batchSize = 1)) {
                // batchSize 1 forces multiple resumable batches
            }
            assertEquals(1, search("ostrich").size)
            assertEquals(0, search("zebra").size)

            // Idempotent: draining again neither duplicates nor loses rows.
            while (!store.ftsCatchUp()) {}
            assertEquals(1, search("ostrich").size)

            // New inserts after a completed catch-up start deferred again.
            store.insert(note("a second ostrich appears", 1002))
            assertEquals(1, search("ostrich").size)
            while (!store.ftsCatchUp()) {}
            assertEquals(2, search("ostrich").size)
        }

    @Test
    fun synchronousStoreNeedsNoCatchUp() =
        runBlocking {
            val syncStore =
                EventStore(
                    dbName = null,
                    relay = null,
                    indexStrategy = DefaultIndexingStrategy(),
                )
            syncStore.use {
                assertTrue(!it.needsFtsCatchUp)
                it.insert(note("immediate emu sighting", 2000))
                assertEquals(1, it.query<Event>(Filter(search = "emu")).size)
                // ftsCatchUp is a harmless no-op on synchronous stores.
                assertTrue(it.ftsCatchUp())
            }
        }
}
