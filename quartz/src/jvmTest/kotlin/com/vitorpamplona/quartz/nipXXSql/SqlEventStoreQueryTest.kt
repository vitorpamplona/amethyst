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
package com.vitorpamplona.quartz.nipXXSql

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Runs profile queries end to end against a real Quartz [EventStore]:
 * parse, compile against [EventStoreTableSources], execute on a
 * `query_only` connection, page through [SqlCursor].
 */
class SqlEventStoreQueryTest {
    private val alice = NostrSignerSync()
    private val bob = NostrSignerSync()
    private val carol = NostrSignerSync()

    private lateinit var dbFile: Path
    private lateinit var store: EventStore
    private lateinit var conn: SQLiteConnection

    private lateinit var aliceNote: Event
    private var eventCount = 0

    private fun NostrSignerSync.event(
        createdAt: Long,
        kind: Int,
        content: String,
        vararg tags: Array<String>,
    ): Event = sign<Event>(createdAt, kind, arrayOf(*tags), content).also { runBlocking { store.insert(it) } }.also { eventCount++ }

    @BeforeTest
    fun setup() {
        dbFile = Files.createTempFile("nostr-sql-", ".db")
        Files.deleteIfExists(dbFile)
        store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null)

        alice.event(1000, 0, "{\"name\":\"alice\"}")
        alice.event(1001, 3, "", arrayOf("p", bob.pubKey))
        bob.event(1002, 3, "", arrayOf("p", carol.pubKey))
        carol.event(1003, 3, "", arrayOf("p", alice.pubKey))

        aliceNote = alice.event(1010, 1, "hello nostr", arrayOf("t", "nostr"), arrayOf("t", "sql"))
        alice.event(1011, 1, "sql over websockets", arrayOf("t", "nostr"), arrayOf("t", "sql"))
        alice.event(1012, 1, "latest from alice", arrayOf("t", "nostr"), arrayOf("t", "sql"))
        bob.event(1020, 1, "bob one", arrayOf("t", "nostr"))
        bob.event(1021, 1, "latest from bob", arrayOf("t", "nostr"))
        carol.event(1030, 1, "latest from carol", arrayOf("t", "Nostr"))

        bob.event(1040, 7, "+", arrayOf("e", aliceNote.id), arrayOf("p", alice.pubKey))
        carol.event(1050, 1059, "sealed", arrayOf("p", alice.pubKey))
        carol.event(
            1060,
            20,
            "pic",
            arrayOf("imeta", "url https://x/y.jpg", "m image/jpeg", "dim 10x10", "alt cat", "blurhash abc", "x deadbeef"),
        )

        conn = BundledSQLiteDriver().open(dbFile.toAbsolutePath().toString())
        conn.execSQL("PRAGMA query_only = ON")
    }

    @AfterTest
    fun tearDown() {
        conn.close()
        store.close()
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    private fun run(
        sql: String,
        vararg params: Any?,
        sources: SqlTableSources = EventStoreTableSources.build(),
    ): List<List<Any?>> = SqlCursor(conn, SqlCompiler.compile(sql, sources, params.toList())).use { it.fetch(Int.MAX_VALUE) }

    @Test
    fun countByKind() {
        assertEquals(
            listOf(
                listOf(0L, 1L),
                listOf(1L, 6L),
                listOf(3L, 3L),
                listOf(7L, 1L),
                listOf(20L, 1L),
                listOf(1059L, 1L),
            ),
            run("SELECT kind, count(*) AS n FROM events GROUP BY kind ORDER BY kind"),
        )
    }

    @Test
    fun topHashtagsFromTheTagsTable() {
        assertEquals(
            listOf(listOf("nostr", 6L), listOf("sql", 3L)),
            run("SELECT lower(value) AS tag, count(*) AS n FROM tags WHERE name = 't' GROUP BY 1 ORDER BY n DESC, tag"),
        )
    }

    @Test
    fun latestNotePerAuthorWithAWindow() {
        val rows =
            run(
                """
                SELECT content FROM (
                  SELECT content, row_number() OVER (PARTITION BY pubkey ORDER BY created_at DESC) AS rn
                  FROM events WHERE kind = 1
                ) WHERE rn = 1 ORDER BY content
                """.trimIndent(),
            )
        assertEquals(listOf("latest from alice", "latest from bob", "latest from carol"), rows.map { it[0] })
    }

    @Test
    fun followsOfFollowsWithARecursiveCte() {
        val rows =
            run(
                """
                WITH RECURSIVE reach(pk, depth) AS (
                  SELECT ?, 0
                  UNION
                  SELECT t.value, r.depth + 1
                  FROM reach r JOIN tags t ON t.pubkey = r.pk AND t.kind = 3 AND t.name = 'p'
                  WHERE r.depth < 2
                )
                SELECT pk, min(depth) FROM reach GROUP BY pk ORDER BY 2
                """.trimIndent(),
                alice.pubKey,
            )
        assertEquals(listOf(listOf(alice.pubKey, 0L), listOf(bob.pubKey, 1L), listOf(carol.pubKey, 2L)), rows)
    }

    @Test
    fun reactionsToAnAuthorWithJoinsAndNamedParams() {
        val c =
            SqlCompiler.compile(
                """
                SELECT count(*) FROM events r
                JOIN tags t ON t.event_id = r.id AND t.name = 'e'
                JOIN events n ON n.id = t.value
                WHERE r.kind = 7 AND n.pubkey = :author
                """.trimIndent(),
                EventStoreTableSources.build(),
                named = mapOf("author" to alice.pubKey),
            )
        assertEquals(listOf(listOf(1L)), SqlCursor(conn, c).use { it.fetch(10) })
    }

    @Test
    fun hiddenKindsVanishFromBothTables() {
        val open = EventStoreTableSources.build()
        val closed = EventStoreTableSources.build(hiddenKinds = setOf(1059))
        val q = "SELECT (SELECT count(*) FROM events WHERE kind = 1059), (SELECT count(*) FROM tags WHERE kind = 1059)"
        assertEquals(listOf(listOf(1L, 1L)), run(q, sources = open))
        assertEquals(listOf(listOf(0L, 0L)), run(q, sources = closed))
        // A CTE can't be used to get around the substitution either.
        assertEquals(
            listOf(listOf(0L)),
            run("WITH x AS (SELECT * FROM events) SELECT count(*) FROM x WHERE kind = 1059", sources = closed),
        )
    }

    @Test
    fun longTagsKeepTheirTail() {
        val rows = run("SELECT idx, value, v2, v3, v4, rest FROM tags WHERE name = 'imeta'")
        assertEquals(
            listOf(listOf(0L, "url https://x/y.jpg", "m image/jpeg", "dim 10x10", "alt cat", "[\"blurhash abc\",\"x deadbeef\"]")),
            rows,
        )
        assertEquals(listOf(listOf(null)), run("SELECT rest FROM tags WHERE name = 'e'"))
    }

    @Test
    fun cursorPagesThroughEverything() {
        SqlCursor(conn, SqlCompiler.compile("SELECT id FROM events ORDER BY created_at", EventStoreTableSources.build())).use { cursor ->
            assertEquals(listOf("id"), cursor.columns)
            val all = ArrayList<List<Any?>>()
            var pages = 0
            while (!cursor.isDone) {
                val page = cursor.fetch(4)
                assertTrue(page.size <= 4)
                all.addAll(page)
                pages++
            }
            assertEquals(eventCount, all.size)
            assertEquals(4, pages) // 13 rows in pages of 4: 4 + 4 + 4 + 1
        }
    }

    @Test
    fun columnNamesAreSqlites() {
        SqlCursor(
            conn,
            SqlCompiler.compile("SELECT count(*), kind, max( created_at ), e.pubkey AS who FROM events e GROUP BY kind", EventStoreTableSources.build()),
        ).use {
            assertEquals(listOf("count(*)", "kind", "max( created_at )", "who"), it.columns)
        }
    }

    @Test
    fun engineErrorsSurfaceAsExceptions() {
        // Semantic errors are SQLite's to report: a relay maps these to CLOSED "error: …".
        assertFails { run("SELECT * FROM events WHERE count(*) > 1") }
        assertFails { run("SELECT nope FROM events") }
    }
}
