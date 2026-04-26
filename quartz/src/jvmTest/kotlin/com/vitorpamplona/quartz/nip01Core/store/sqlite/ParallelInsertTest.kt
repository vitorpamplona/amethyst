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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress test for the SQLite connection pool. Pre-pool, two coroutines
 * inserting at the same time would race on the shared `SQLiteConnection`
 * (`androidx.sqlite` connections aren't thread-safe) and crash with
 * either `SQLITE_ERROR: cannot start a transaction within a transaction`
 * or a corrupted prepared statement (`SQLITE_MISUSE`).
 *
 * With [SQLiteConnectionPool] writes serialise behind a coroutine `Mutex`
 * and reads run in parallel against a fixed pool of reader connections,
 * matching what Room does. The test launches a fan-out of inserts and
 * concurrent reads, then asserts every inserted event is visible and the
 * count is exact.
 */
class ParallelInsertTest {
    private val signer = NostrSignerSync()
    private lateinit var dbFile: Path
    private lateinit var store: EventStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        // Use a real file so the pool can hand out independent reader
        // connections — :memory: would make every connection a separate DB.
        dbFile = Files.createTempFile("parallel-insert-", ".db")
        // Driver expects to open the file itself; ensure the placeholder
        // is gone so SQLite can create a fresh DB.
        Files.deleteIfExists(dbFile)
        store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null)
    }

    @AfterTest
    fun tearDown() {
        store.close()
        // SQLite leaves -wal / -shm sidecars next to the main file under WAL.
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            Path.of(dbFile.toString() + suffix).deleteIfExists()
        }
    }

    @Test
    fun `parallel inserts on N coroutines all succeed`() =
        runBlocking {
            val perCoroutine = 200
            val coroutines = 8
            val total = perCoroutine * coroutines

            val events =
                (0 until total).map { i ->
                    signer.sign(TextNoteEvent.build("p$i", createdAt = i.toLong() + 1))
                }

            // Fan out inserts across `coroutines` workers on the IO
            // dispatcher (multi-thread). Without the pool's writer mutex
            // these all race on a single SQLiteConnection and crash.
            coroutineScope {
                events.chunked(perCoroutine).forEach { chunk ->
                    launch(Dispatchers.IO) {
                        for (e in chunk) store.insert(e)
                    }
                }
            }

            assertEquals(total, store.count(Filter()), "every insert must be visible")

            val byId = store.query<TextNoteEvent>(Filter()).associateBy { it.id }
            for (e in events) {
                assertTrue(byId.containsKey(e.id), "missing event ${e.id.take(8)}")
            }
        }

    @Test
    fun `parallel reads run alongside writes without crashing`() =
        runBlocking {
            val writes = 500

            val events =
                (0 until writes).map { i ->
                    signer.sign(TextNoteEvent.build("rw$i", createdAt = i.toLong() + 1))
                }

            coroutineScope {
                // Writer feed.
                launch(Dispatchers.IO) {
                    for (e in events) store.insert(e)
                }
                // Multiple reader fans-out: count() and query() running
                // continuously while inserts are still in flight. Asserts
                // none of these crash with SQLITE_MISUSE.
                val readers =
                    List(4) {
                        async(Dispatchers.IO) {
                            var lastSeen = 0
                            repeat(100) {
                                val n = store.count(Filter())
                                assertTrue(n in 0..writes)
                                if (n > lastSeen) lastSeen = n
                            }
                            lastSeen
                        }
                    }
                readers.awaitAll()
            }

            assertEquals(writes, store.count(Filter()))
        }

    @Test
    fun `parallel transaction batches all commit`() =
        runBlocking {
            val batches = 8
            val perBatch = 50
            val total = batches * perBatch

            val events =
                (0 until total).map { i ->
                    signer.sign(TextNoteEvent.build("t$i", createdAt = i.toLong() + 1))
                }

            // Each coroutine wraps its slice in store.transaction { ... },
            // exercising the writer mutex around BEGIN/COMMIT pairs.
            coroutineScope {
                events.chunked(perBatch).forEach { chunk ->
                    launch(Dispatchers.IO) {
                        store.transaction {
                            for (e in chunk) insert(e)
                        }
                    }
                }
            }

            assertEquals(total, store.count(Filter()))
        }

    @Test
    fun `pool with file-backed db survives reopen`() =
        runBlocking {
            // Smoke test that the pool migration runs idempotently when
            // a writer connection is reopened against an existing DB.
            val first = signer.sign(TextNoteEvent.build("first", createdAt = 1))
            store.insert(first)
            store.close()

            val reopened = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null)
            try {
                assertTrue(dbFile.exists())
                val got = reopened.query<TextNoteEvent>(Filter(ids = listOf(first.id)))
                assertEquals(listOf(first.id), got.map { it.id })

                // And then more parallel inserts still work on the
                // reopened pool.
                val moreCount = 20
                val more = (0 until moreCount).map { signer.sign(TextNoteEvent.build("m$it", createdAt = it.toLong() + 100)) }
                coroutineScope {
                    more.forEach { e ->
                        launch(Dispatchers.IO) { reopened.insert(e) }
                    }
                }
                assertEquals(1 + moreCount, reopened.count(Filter()))
            } finally {
                reopened.close()
            }
        }
}
