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
import androidx.sqlite.SQLiteException
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory

class FullTextSearchModule : IModule {
    val tableName = "event_fts"
    val triggerName = "fts_foreign_key"
    val eventHeaderRowIdName = "event_header_row_id"
    val contentName = "content"

    override fun create(db: SQLiteConnection) {
        val ftsVersion = versionFinder(db)
        db.execSQL(
            """
                    CREATE VIRTUAL TABLE $tableName
                    USING fts$ftsVersion($eventHeaderRowIdName, $contentName)
                """,
        )

        // Foreign key cleanup for full text search
        db.execSQL(
            """
                CREATE TRIGGER $triggerName
                AFTER DELETE ON event_headers
                FOR EACH ROW
                BEGIN
                    DELETE FROM $tableName
                    WHERE old.row_id = $tableName.$eventHeaderRowIdName;
                END;
            """,
        )
    }

    override fun drop(db: SQLiteConnection) {
        db.execSQL("DROP TABLE IF EXISTS $tableName")
    }

    /**
     * Drop the cleanup trigger on its own. [drop] only removes the FTS
     * table; the trigger lives on `event_headers`, so a rebuild that
     * recreates the table without first dropping the trigger would fail
     * with "trigger already exists". (A schema upgrade doesn't hit this
     * because dropping `event_headers` takes its triggers with it.)
     */
    fun dropTrigger(db: SQLiteConnection) {
        db.execSQL("DROP TRIGGER IF EXISTS $triggerName")
    }

    val insertFTS =
        """
        INSERT OR ROLLBACK INTO $tableName ($eventHeaderRowIdName, $contentName)
        VALUES (?, ?)
        """.trimIndent()

    fun insert(
        event: Event,
        headerId: Long,
        db: SQLiteConnection,
    ) {
        if (event is SearchableEvent) {
            db.prepare(insertFTS).use { stmt ->
                stmt.bindLong(1, headerId)
                stmt.bindText(2, event.indexableContent())
                stmt.step()
            }
        }
    }

    fun versionFinder(db: SQLiteConnection): Int {
        // Defensive cleanup in case a previous probe left these behind
        // (e.g. a partial create() during an upgrade) — without this,
        // the next CREATE VIRTUAL TABLE would fail with "already exists".
        db.execSQL("DROP TABLE IF EXISTS dummy_fts5")
        db.execSQL("DROP TABLE IF EXISTS dummy_fts4")
        db.execSQL("DROP TABLE IF EXISTS dummy_fts3")

        val (table, version) =
            try {
                try {
                    db.execSQL("CREATE VIRTUAL TABLE dummy_fts5 USING fts5(dummy)")
                    "dummy_fts5" to 5
                } catch (e: SQLiteException) {
                    db.execSQL("CREATE VIRTUAL TABLE dummy_fts4 USING fts4(dummy)")
                    "dummy_fts4" to 4
                }
            } catch (e: SQLiteException) {
                db.execSQL("CREATE VIRTUAL TABLE dummy_fts3 USING fts3(dummy)")
                "dummy_fts3" to 3
            }

        // We only needed the table to probe FTS support — drop it now so
        // re-running create() on an already-probed DB stays idempotent.
        db.execSQL("DROP TABLE $table")

        return version
    }

    override fun deleteAll(db: SQLiteConnection) {
        db.execSQL("DELETE FROM event_fts")
    }

    /**
     * Wipe and rebuild the whole FTS index from `event_headers`.
     *
     * Wiping is done by dropping and recreating the virtual table, which
     * empties it in O(1) — far cheaper than DELETE-ing every row out of a
     * populated FTS index. The rebuild then streams only the rows whose
     * kind currently parses to a [SearchableEvent] (see
     * [searchableKindsPresent]) so the common non-searchable bulk —
     * reactions, zaps, follow lists — is never deserialised. A single
     * shared INSERT statement is reused across the scan.
     *
     * Must run inside the caller's write transaction.
     */
    fun reindexAll(db: SQLiteConnection) {
        dropTrigger(db)
        drop(db)
        create(db)

        val kinds = searchableKindsPresent(db)
        if (kinds.isEmpty()) return

        val selectSql =
            "SELECT row_id, id, pubkey, created_at, kind, tags, content, sig " +
                "FROM event_headers WHERE kind IN (${kinds.joinToString(",")})"

        db.prepare(insertFTS).use { write ->
            db.prepare(selectSql).use { read ->
                while (read.step()) {
                    val event =
                        EventFactory.create<Event>(
                            read.getText(1),
                            read.getText(2),
                            read.getLong(3),
                            read.getInt(4),
                            OptimizedJsonMapper.fromJsonToTagArray(read.getText(5)),
                            read.getText(6),
                            read.getText(7),
                        )
                    if (event is SearchableEvent) {
                        write.bindLong(1, read.getLong(0))
                        write.bindText(2, event.indexableContent())
                        write.step()
                        write.reset()
                    }
                }
            }
        }
    }

    /**
     * The distinct kinds present in `event_headers` that currently parse
     * to a [SearchableEvent]. Kind alone selects the event class in
     * [EventFactory], so one probe per distinct kind is authoritative;
     * the result drives a `kind IN (...)` filter on the rebuild scan so
     * non-searchable rows are skipped at the SQL layer.
     */
    private fun searchableKindsPresent(db: SQLiteConnection): List<Int> {
        val out = ArrayList<Int>()
        db.prepare("SELECT DISTINCT kind FROM event_headers").use { stmt ->
            while (stmt.step()) {
                val kind = stmt.getInt(0)
                if (isSearchableKind(kind)) out.add(kind)
            }
        }
        return out
    }

    private fun isSearchableKind(kind: Int): Boolean = EventFactory.create<Event>(PROBE_ID, PROBE_ID, 0L, kind, EMPTY_TAGS, "", "") is SearchableEvent

    companion object {
        // A non-blank id keeps kinds that lazily hash a missing id (e.g.
        // NIP-17 chat messages) from doing that work — the probe only
        // inspects the resulting runtime type.
        private const val PROBE_ID = "0"
        private val EMPTY_TAGS = emptyArray<Array<String>>()
    }
}
