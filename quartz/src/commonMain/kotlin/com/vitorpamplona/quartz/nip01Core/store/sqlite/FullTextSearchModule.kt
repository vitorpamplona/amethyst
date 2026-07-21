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
import com.vitorpamplona.quartz.nip01Core.store.FtsReindexProgress
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory

/**
 * NIP-50 full-text search index over event content.
 *
 * The index is a **contentless** FTS5 table (`content=''`,
 * `contentless_delete=1`) whose `rowid` is the event's
 * `event_headers.row_id`. Two consequences:
 *
 *  - **Deletes.** The `fts_foreign_key` trigger deletes by `rowid` (an FTS5
 *    primary-key seek, O(log n)). The old `fts5(event_header_row_id,
 *    content)` schema deleted by a *regular* column, which FTS5 cannot seek —
 *    it scans the whole index per delete (O(n)), so deletion throughput
 *    degraded with corpus size. Every event removal fires this trigger
 *    (replaceable rotation, kind-5, expiration, right-to-vanish), so the
 *    seek matters. Measured `Fts5CapabilityProbe`/`FtsSearchScalingBenchmark`:
 *    ~78× at 8k rows and widening with the table.
 *  - **Size.** A contentless table stores only the inverted index, not a
 *    second copy of the tokenized text. The indexed text is *derived*
 *    ([SearchableEvent.indexableContent], not any raw column), so FTS5
 *    external-content — which reads the source column from the base table —
 *    cannot express it; contentless is the correct primitive.
 *
 * Search results are ordered by **relevance**, per NIP-50 ("descending order
 * by quality of search result ... not by the usual `.created_at`", limit
 * applied after the score) — via FTS5 bm25 (`ORDER BY event_fts.rank`), with
 * `created_at DESC` only as a tie-break. This is [QueryBuilder.makeSimpleSearch]
 * (the `search [+ kinds/authors/since/until] + limit` shape); the rarer
 * `search + specific tag` combination still sorts by `created_at` (its row-id
 * subquery can't carry the rank through), and the negentropy snapshot keeps
 * `created_at` (a sync set, not a ranked result). bm25 must score every match,
 * so search latency still grows with the match set regardless of ordering;
 * corpus-independent search needs an external engine, not this index.
 *
 * When [enabled] is `false` the module becomes an inert no-op: no
 * `event_fts` virtual table and no `fts_foreign_key` delete trigger are
 * created, inserts skip the per-event tokenization cost, and the reindex
 * entry points do nothing. This is for deployments that never query the
 * SQLite store for search — e.g. a relay that offloads NIP-50 to an
 * external engine such as Vespa — and don't want to pay the write-time
 * FTS indexing (or the per-delete trigger) overhead. Search filters are
 * handled by [QueryBuilder], which returns no matches when search is off.
 */
class FullTextSearchModule(
    val enabled: Boolean = true,
    /**
     * Skip tokenization in [insert] and let [catchUpBatch] index from the
     * persisted watermark instead. See
     * [IndexingStrategy.deferFullTextSearchIndexing] for the contract —
     * something must drive the catch-up (the relay server does).
     */
    val deferIndexing: Boolean = false,
) : IModule {
    val tableName = "event_fts"
    val triggerName = "fts_foreign_key"

    /**
     * The FTS column that links back to `event_headers`. It is the implicit
     * `rowid` of the (contentless) FTS table, which we set equal to
     * `event_headers.row_id` on every insert — so the join is
     * `event_headers.row_id = event_fts.rowid`, with no stored column.
     */
    val rowIdColumn = "rowid"
    val contentName = "content"
    val stateTableName = "fts_catchup_state"

    /**
     * Whether the on-disk `event_fts` is an FTS5 table (vs the fts4/fts3
     * fallback). Only FTS5 supports the contentless schema and the
     * `merge`/`optimize` maintenance commands; the fallback stores content
     * and skips maintenance. Read only on the (single-threaded) writer.
     * Cached lazily from `sqlite_master` so a reopened DB — where [create]
     * never runs — still resolves it.
     */
    private var isFts5: Boolean? = null

    override fun create(db: SQLiteConnection) {
        if (!enabled) return
        val ftsVersion = versionFinder(db)
        isFts5 = ftsVersion >= 5
        // FTS5: contentless index (no stored content copy) with delete
        // support. fts4/fts3 (bundled driver never selects them) fall back to
        // a plain content-storing table — rowid-explicit insert and
        // delete-by-rowid work there too, only the size win is FTS5-only.
        val columns =
            if (ftsVersion >= 5) {
                "$contentName, content='', contentless_delete=1"
            } else {
                contentName
            }
        db.execSQL("CREATE VIRTUAL TABLE $tableName USING fts$ftsVersion($columns)")

        // Foreign key cleanup for full text search. Deletes by the FTS rowid
        // (= event_headers.row_id); a header with no FTS row (non-searchable
        // kind) deletes nothing, which is a harmless no-op.
        db.execSQL(
            """
                CREATE TRIGGER $triggerName
                AFTER DELETE ON event_headers
                FOR EACH ROW
                BEGIN
                    DELETE FROM $tableName WHERE $tableName.rowid = old.row_id;
                END;
            """,
        )

        createStateTable(db)
    }

    private fun resolveIsFts5(db: SQLiteConnection): Boolean {
        isFts5?.let { return it }
        val sql =
            db.prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?").use { stmt ->
                stmt.bindText(1, tableName)
                if (stmt.step()) stmt.getText(0) else ""
            }
        return sql.contains("fts5", ignoreCase = true).also { isFts5 = it }
    }

    /**
     * Watermark for the deferred path: everything with
     * `row_id <= last_row_id` is guaranteed indexed. Idempotent — also
     * used as the v3→v4 migration for databases created before the
     * deferred mode existed; those seeded their FTS synchronously, so
     * the watermark starts at the current MAX(row_id).
     */
    fun createStateTable(db: SQLiteConnection) {
        if (!enabled) return
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $stateTableName (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                last_row_id INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO $stateTableName (id, last_row_id)
            SELECT 1, COALESCE(MAX(row_id), 0) FROM event_headers
            """.trimIndent(),
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
        INSERT OR ROLLBACK INTO $tableName (rowid, $contentName)
        VALUES (?, ?)
        """.trimIndent()

    val deleteFTSByRowId =
        """
        DELETE FROM $tableName WHERE rowid = ?
        """.trimIndent()

    fun insert(
        event: Event,
        headerId: Long,
        db: SQLiteConnection,
    ) {
        if (!enabled || deferIndexing) return
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
        if (!enabled) return
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
        if (!enabled) return
        dropTrigger(db)
        drop(db)
        create(db)
        populateAll(db)
        // The rebuild just wrote every row as its own tiny segment; compact
        // them into one so the first search after a reindex isn't a scan over
        // hundreds of segments.
        optimize(db)
    }

    /**
     * Scan every stored searchable event and insert its derived content into
     * the (already created, empty) FTS index, keyed by `event_headers.row_id`.
     * The caller owns the transaction and the create/drop lifecycle.
     */
    private fun populateAll(db: SQLiteConnection) {
        val kinds = searchableKindsPresent(db)
        if (kinds.isEmpty()) return

        val selectSql = "$SELECT_EVENT_COLUMNS WHERE kind IN (${kinds.joinToString(",")})"

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
     * v4 → v5 migration: the pre-v5 index was `fts5(event_header_row_id,
     * content)` with an auto-assigned rowid unrelated to `event_headers`, and
     * it stored a second copy of the content. v5 is the contentless,
     * rowid = row_id schema. The old rowids can't be remapped in place, so the
     * table is dropped and repopulated. Runs inside the migration transaction.
     *
     *  - **synchronous** stores rebuild now (client corpora are small).
     *  - **deferred** stores reset the catch-up watermark to 0 so the relay's
     *    background worker repopulates without a long migration transaction.
     */
    fun migrateV4ToContentless(db: SQLiteConnection) {
        if (!enabled) return
        dropTrigger(db)
        drop(db)
        create(db)
        if (deferIndexing) {
            db.prepare("UPDATE $stateTableName SET last_row_id = 0 WHERE id = 1").use { it.step() }
        } else {
            populateAll(db)
            optimize(db)
        }
    }

    /**
     * Full FTS5 segment compaction — merges the b-tree segments left by
     * incremental inserts into one, so a `MATCH` touches a single segment
     * instead of dozens. Expensive (rewrites the whole index); call it once
     * after a rebuild, not per batch. No-op on the fts4/fts3 fallback.
     */
    fun optimize(db: SQLiteConnection) {
        if (!enabled || !resolveIsFts5(db)) return
        db.prepare("INSERT INTO $tableName($tableName) VALUES ('optimize')").use { it.step() }
    }

    /**
     * Bounded incremental segment merge — does at most [pages] pages of merge
     * work, so it stays cheap enough to run on a periodic maintenance tick
     * while the index keeps growing from deferred catch-up. No-op on the
     * fts4/fts3 fallback.
     */
    fun mergeSegments(
        db: SQLiteConnection,
        pages: Int = 16,
    ) {
        if (!enabled || !resolveIsFts5(db)) return
        db.prepare("INSERT INTO $tableName($tableName, rank) VALUES ('merge', ?)").use { stmt ->
            stmt.bindLong(1, pages.toLong())
            stmt.step()
        }
    }

    /**
     * Process one batch of a resumable rebuild: re-derive the FTS rows
     * for up to [batchSize] events whose `row_id > ` [afterRowId] and
     * whose kind is searchable, ordered by `row_id`.
     *
     * `row_id` is a monotonic AUTOINCREMENT key, so it is a stable
     * cursor that needs no extra bookkeeping. Each event is
     * delete-then-insert, which keeps the batch idempotent (a replay
     * after a crash is harmless) and avoids duplicate FTS rows for
     * events that were already indexed by the normal insert path. Rows
     * not yet reached keep their previous FTS content, so search stays
     * usable while the rebuild is in flight.
     *
     * Must run inside the caller's per-batch write transaction.
     */
    fun reindexBatch(
        db: SQLiteConnection,
        afterRowId: Long,
        batchSize: Int,
    ): FtsReindexProgress {
        if (!enabled) return FtsReindexProgress(cursor = null, processedThisBatch = 0, done = true)
        // A non-positive page would select no rows yet never report done,
        // spinning the caller's loop forever — clamp to at least one.
        val limit = batchSize.coerceAtLeast(1)
        val kinds = searchableKindsPresent(db)
        if (kinds.isEmpty()) return FtsReindexProgress(cursor = null, processedThisBatch = 0, done = true)

        val selectSql = selectSearchablePageSql(kinds)

        var last = afterRowId
        var processed = 0
        db.prepare(deleteFTSByRowId).use { del ->
            db.prepare(insertFTS).use { write ->
                db.prepare(selectSql).use { read ->
                    read.bindLong(1, afterRowId)
                    read.bindLong(2, limit.toLong())
                    while (read.step()) {
                        val rowId = read.getLong(0)
                        // Clear any existing row for this event first so a
                        // replay or an already-indexed event can't duplicate.
                        del.bindLong(1, rowId)
                        del.step()
                        del.reset()

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
                            write.bindLong(1, rowId)
                            write.bindText(2, event.indexableContent())
                            write.step()
                            write.reset()
                        }
                        last = rowId
                        processed++
                    }
                }
            }
        }

        // Fewer than a full page came back ⇒ we hit the end of the table.
        val done = processed < limit
        return FtsReindexProgress(
            cursor = if (done) null else last.toString(),
            processedThisBatch = processed,
            done = done,
        )
    }

    /**
     * One catch-up step of the deferred path: index up to [batchSize]
     * events past the persisted watermark, then advance it. Returns
     * `true` when the index has caught up with the table (within this
     * transaction's snapshot). Must run inside the caller's write
     * transaction so the watermark advances atomically with the FTS
     * rows it covers — a crash replays the batch, and [reindexBatch]'s
     * delete-then-insert keeps the replay harmless.
     */
    fun catchUpBatch(
        db: SQLiteConnection,
        batchSize: Int,
    ): Boolean {
        if (!enabled) return true

        val watermark =
            db.prepare("SELECT last_row_id FROM $stateTableName WHERE id = 1").use { stmt ->
                if (stmt.step()) stmt.getLong(0) else 0L
            }

        // Unlike [reindexBatch] there is NO per-row delete here: rows past
        // the watermark were never indexed (deferred mode skips insert()),
        // and the watermark advances atomically with the FTS rows it
        // covers, so a crash replay is impossible. (Delete-by-rowid is now
        // O(log n) on the contentless index, so this is purely about not
        // doing redundant work.) Consequence: switching a database back and
        // forth between deferred and synchronous strategies requires a
        // [reindexAll] in between (same rule as a searchable-kinds change).
        val limit = batchSize.coerceAtLeast(1)
        val kinds = searchableKindsPresent(db)
        var last = watermark
        var processed = 0
        if (kinds.isNotEmpty()) {
            val selectSql = selectSearchablePageSql(kinds)
            db.prepare(insertFTS).use { write ->
                db.prepare(selectSql).use { read ->
                    read.bindLong(1, watermark)
                    read.bindLong(2, limit.toLong())
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
                        last = read.getLong(0)
                        processed++
                    }
                }
            }
        }

        val done = processed < limit
        val newWatermark =
            if (done) {
                // Scan reached the end of the table: everything visible in
                // this snapshot is indexed.
                db.prepare("SELECT COALESCE(MAX(row_id), 0) FROM event_headers").use { stmt ->
                    stmt.step()
                    stmt.getLong(0)
                }
            } else {
                last
            }

        if (newWatermark != watermark) {
            db.prepare("UPDATE $stateTableName SET last_row_id = ? WHERE id = 1").use { stmt ->
                stmt.bindLong(1, newWatermark)
                stmt.step()
            }
        }
        return done
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

        /** Column order matches the positional `read.getText(1)`…`getText(7)` event rebuilds. */
        private const val SELECT_EVENT_COLUMNS =
            "SELECT row_id, id, pubkey, created_at, kind, tags, content, sig FROM event_headers"

        /** One `row_id`-cursored page of searchable events; binds: cursor, limit. */
        private fun selectSearchablePageSql(kinds: List<Int>) = "$SELECT_EVENT_COLUMNS WHERE row_id > ? AND kind IN (${kinds.joinToString(",")}) ORDER BY row_id LIMIT ?"
    }
}
