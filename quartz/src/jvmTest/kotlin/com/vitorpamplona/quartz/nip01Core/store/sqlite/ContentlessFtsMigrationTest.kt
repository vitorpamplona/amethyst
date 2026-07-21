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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
 * Verifies the real v4 → v5 FTS upgrade path: a database written with the old
 * `fts5(event_header_row_id, content)` index (auto-assigned rowid, content
 * stored) must, on next open, drop that table and rebuild a contentless index
 * keyed by `event_headers.row_id`, with search intact.
 *
 * The v4 state is fabricated by opening a fresh v5 store, then rewriting its
 * `event_fts` to the old schema (with deliberately wrong content, to prove the
 * rebuild wipes it) and stamping `user_version = 4`.
 */
class ContentlessFtsMigrationTest {
    private val signer = NostrSignerSync()
    private lateinit var dbFile: Path

    private fun path() = dbFile.toAbsolutePath().toString()

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        dbFile = Files.createTempFile("contentless-fts-migration-", ".db")
        Files.deleteIfExists(dbFile)
    }

    @AfterTest
    fun tearDown() {
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    @Test
    fun upgradesOldFtsSchemaAndRebuildsSearch() =
        runBlocking {
            val alpha = signer.sign(TextNoteEvent.build("uniqalpha searchable body", createdAt = 1_700_000_000L))
            val beta = signer.sign(TextNoteEvent.build("uniqbeta searchable body", createdAt = 1_700_000_100L))

            // 1. Fresh v5 store, seed events, close.
            EventStore(dbName = path(), relay = null).also {
                it.insert(alpha)
                it.insert(beta)
                it.close()
            }

            // 2. Rewrite event_fts to the pre-v5 schema with WRONG content and
            //    downgrade user_version to 4 — the state a v4 database is in.
            BundledSQLiteDriver().open(path()).use { db ->
                db.exec("DROP TRIGGER IF EXISTS fts_foreign_key")
                db.exec("DROP TABLE IF EXISTS event_fts")
                db.exec("CREATE VIRTUAL TABLE event_fts USING fts5(event_header_row_id, content)")
                db.exec(
                    """
                    CREATE TRIGGER fts_foreign_key AFTER DELETE ON event_headers FOR EACH ROW
                    BEGIN DELETE FROM event_fts WHERE old.row_id = event_fts.event_header_row_id; END
                    """.trimIndent(),
                )
                // Stale/garbage rows: a real v4 index would hold correct data,
                // but seeding garbage proves the migration rebuilds from
                // event_headers rather than trusting the old table.
                db.exec("INSERT INTO event_fts(event_header_row_id, content) VALUES (1, 'uniqstale garbage')")
                db.exec("PRAGMA user_version = 4")
            }

            // 3. Reopen with current code → onUpgrade(4→5) → migrateV4ToContentless.
            val store = EventStore(dbName = path(), relay = null)
            try {
                // Rebuilt from event_headers: real content is searchable...
                assertEquals(alpha.id, store.query<Event>(Filter(search = "uniqalpha")).single().id)
                assertEquals(beta.id, store.query<Event>(Filter(search = "uniqbeta")).single().id)
                // ...and the old garbage is gone.
                assertTrue(store.query<Event>(Filter(search = "uniqstale")).isEmpty())

                // The new rowid IS event_headers.row_id: the join returns the
                // right event for each FTS rowid.
                store.store.pool.useReader { db ->
                    db
                        .prepare(
                            "SELECT h.id FROM event_fts f JOIN event_headers h ON h.row_id = f.rowid ORDER BY f.rowid",
                        ).use { stmt ->
                            val ids = ArrayList<String>()
                            while (stmt.step()) ids.add(stmt.getText(0))
                            assertEquals(listOf(alpha.id, beta.id), ids, "FTS rowid must map to event_headers.row_id")
                        }
                }

                // The rebuilt delete trigger still cleans up FTS on delete.
                store.store.delete(beta.id)
                assertTrue(store.query<Event>(Filter(search = "uniqbeta")).isEmpty())
                assertEquals(alpha.id, store.query<Event>(Filter(search = "uniqalpha")).single().id)
            } finally {
                store.close()
            }
        }

    private fun SQLiteConnection.exec(sql: String) = prepare(sql).use { it.step() }
}
