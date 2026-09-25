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
import com.vitorpamplona.quartz.nip01Core.cache.interning.EventInterner
import com.vitorpamplona.quartz.nip01Core.cache.interning.InterningEventStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip01Core.store.sqlite.bindAny
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
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
        sources: SqlTableSources = EventStoreTableSources.sources,
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
                EventStoreTableSources.sources,
                named = mapOf("author" to alice.pubKey),
            )
        assertEquals(listOf(listOf(1L)), SqlCursor(conn, c).use { it.fetch(10) })
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
        SqlCursor(conn, SqlCompiler.compile("SELECT id FROM events ORDER BY created_at", EventStoreTableSources.sources)).use { cursor ->
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
            SqlCompiler.compile("SELECT count(*), kind, max( created_at ), e.pubkey AS who FROM events e GROUP BY kind", EventStoreTableSources.sources),
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

    @Test
    fun tagPushdownUsesTheTagIndex() {
        val pushdown = EventStoreTableSources.forStore(TagNameValueHasher(store.store.seedModule.getSeed(conn)), store.store.indexStrategy)
        val sql = "SELECT count(*) FROM tags WHERE name = 't' AND value = 'nostr'"
        val compiled = SqlCompiler.compile(sql, pushdown)

        val plan = ArrayList<String>()
        conn.prepare("EXPLAIN QUERY PLAN " + compiled.sql).use { stmt ->
            compiled.args.forEachIndexed { i, v -> stmt.bindAny(i + 1, v!!) }
            while (stmt.step()) plan.add(stmt.getText(3))
        }
        assertTrue(plan.any { it.contains("event_tags") && it.contains("INDEX") }, plan.joinToString("\n"))

        // Same answer as without the pushdown.
        assertEquals(run(sql), SqlCursor(conn, compiled).use { it.fetch(10) })
        assertEquals(listOf(listOf(5L)), run(sql))
    }

    // ---- IEventStore.sql -----------------------------------------------------

    private fun storeSql(
        target: IEventStore,
        query: String,
        params: List<Any?> = emptyList(),
        named: Map<String, Any?> = emptyMap(),
    ): Pair<List<String>, List<List<Any?>>> {
        var columns = emptyList<String>()
        val rows = ArrayList<List<Any?>>()
        runBlocking { target.sql(query, params, named, { columns = it }) { rows.add(it) } }
        return columns to rows
    }

    @Test
    fun storeSqlMatchesTheRelayPath() {
        val q = "SELECT lower(value) AS tag, count(*) AS n FROM tags WHERE name = 't' GROUP BY 1 ORDER BY n DESC, tag"
        val (columns, rows) = storeSql(store, q)
        assertEquals(listOf("tag", "n"), columns)
        assertEquals(run(q), rows)
    }

    @Test
    fun storeSqlBindsParamsAndStreamsLargeResults() {
        val (_, byAuthor) = storeSql(store, "SELECT count(*) FROM events WHERE pubkey = :who", named = mapOf("who" to alice.pubKey))
        assertEquals(listOf(listOf(5L)), byAuthor)
        // More rows than one internal batch.
        val (_, many) = storeSql(store, "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < ?) SELECT i FROM n", listOf(1234L))
        assertEquals(1234, many.size)
        assertEquals(listOf(1234L), many.last())
    }

    @Test
    fun storeSqlPassesThroughWrappersAndWorksInMemory() {
        assertEquals(listOf(listOf(eventCount.toLong())), storeSql(InterningEventStore(store, EventInterner()), "SELECT count(*) FROM events").second)

        val memory = EventStore(dbName = null, relay = null)
        try {
            runBlocking { memory.insert(alice.sign<Event>(1, 1, arrayOf(arrayOf("t", "x")), "m")) }
            assertEquals(listOf(listOf("x")), storeSql(memory, "SELECT value FROM tags WHERE name = 't' AND value = 'x'").second)
        } finally {
            memory.close()
        }
    }

    @Test
    fun storeSqlRejectsLikeTheRelay() {
        val e = assertFailsWith<SqlException> { storeSql(store, "DELETE FROM events") }
        assertEquals(SqlException.UNSUPPORTED, e.prefix)
        assertFailsWith<SqlException> { storeSql(store, "SELECT * FROM event_headers") }
        // A store without SQL says so.
        val none =
            object : IEventStore by store {
                override suspend fun sql(
                    query: String,
                    params: List<Any?>,
                    named: Map<String, Any?>,
                    onColumns: (List<String>) -> Unit,
                    onRow: (List<Any?>) -> Unit,
                ) = super<IEventStore>.sql(query, params, named, onColumns, onRow)
            }
        assertEquals(SqlException.UNSUPPORTED, assertFailsWith<SqlException> { storeSql(none, "SELECT 1") }.prefix)
    }
}
