/**
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

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.sql.where
import com.vitorpamplona.quartz.utils.EventFactory

class EventIndexesModule(
    val fts: FullTextSearchModule,
    val tagIndexStrategy: IndexingStrategy = IndexingStrategy(),
) {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE event_headers (
                row_id INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL,
                pubkey TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                kind INTEGER NOT NULL,
                d_tag TEXT,
                tags TEXT NOT NULL,
                content TEXT NOT NULL,
                sig TEXT NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE event_tags (
                event_header_row_id INTEGER,
                tag_name TEXT NOT NULL,
                tag_value TEXT NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL("CREATE UNIQUE INDEX event_headers_id    ON event_headers (id)")
        db.execSQL("CREATE INDEX query_by_kind_pubkey_idx   ON event_headers (created_at desc, kind, pubkey, d_tag)")
        db.execSQL("CREATE INDEX query_by_id_idx            ON event_headers (created_at desc, id)")
        db.execSQL("CREATE INDEX query_by_tags_idx          ON event_tags (tag_name, tag_value)")

        // Prevent updates to maintain immutability
        db.execSQL(
            """
            CREATE TRIGGER event_headers_prevent_update
            BEFORE UPDATE ON event_headers
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'Error: Updates are not allowed.');
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER event_tags_prevent_update
            BEFORE UPDATE ON event_tags
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'Error: Updates are not allowed.');
            END;
            """.trimIndent(),
        )
    }

    val sqlInsertHeader =
        """
        INSERT INTO event_headers
            (id, pubkey, created_at, kind, tags, content, sig, d_tag)
        VALUES
            (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    val sqlInsertTags =
        """
        INSERT OR ROLLBACK INTO event_tags
            (event_header_row_id, tag_name, tag_value)
        VALUES
            (?,?,?)
        """.trimIndent()

    fun insert(
        event: Event,
        db: SQLiteDatabase,
    ): Long {
        val stmt = db.compileStatement(sqlInsertHeader)
        stmt.bindString(1, event.id)
        stmt.bindString(2, event.pubKey)
        stmt.bindLong(3, event.createdAt)
        stmt.bindLong(4, event.kind.toLong())
        stmt.bindString(5, OptimizedJsonMapper.toJson(event.tags))
        stmt.bindString(6, event.content)
        stmt.bindString(7, event.sig)
        if (event is AddressableEvent) {
            stmt.bindString(8, event.dTag())
        } else {
            stmt.bindNull(8)
        }
        val headerId = stmt.executeInsert()

        val tagsToIndex = event.tags.filter(tagIndexStrategy::shouldIndex)

        val reuseStatements = mutableMapOf<Int, SQLiteStatement>()

        for (chunk in tagsToIndex.chunked(300)) {
            if (chunk.isNotEmpty()) {
                val stmtTags =
                    reuseStatements[chunk.size - 1] ?: run {
                        val sql =
                            buildString {
                                append(sqlInsertTags)
                                repeat(chunk.size - 1) {
                                    append(",(?,?,?)")
                                }
                            }

                        val new = db.compileStatement(sql)
                        reuseStatements[chunk.size - 1] = new
                        new
                    }

                var index = 1
                chunk.forEach { tag ->
                    stmtTags.bindLong(index++, headerId)
                    stmtTags.bindString(index++, tag[0])
                    stmtTags.bindString(index++, tag[1])
                }

                stmtTags.executeInsert()
            }
        }

        return headerId
    }

    /**
     * By default, we index all tags that have a single letter name and some value
     */
    class IndexingStrategy {
        fun shouldIndex(tag: Tag) = tag.size >= 2 && tag[0].length == 1
    }

    fun planQuery(filter: Filter): String {
        val rowIdSubQuery = prepareRowIDSubQueries(filter) ?: return makeEverythingQuery()

        return makeQueryIn(rowIdSubQuery.sql)
    }

    fun query(
        filter: Filter,
        db: SQLiteDatabase,
    ): List<Event> {
        val rowIdSubQuery = prepareRowIDSubQueries(filter) ?: return db.runQuery(makeEverythingQuery())

        return db.runQuery(makeQueryIn(rowIdSubQuery.sql), rowIdSubQuery.args)
    }

    fun query(
        filter: Filter,
        db: SQLiteDatabase,
        onEach: (Event) -> Unit,
    ) {
        val rowIdSubQuery = prepareRowIDSubQueries(filter) ?: return db.runQueryEmitting(makeEverythingQuery(), onEach = onEach)

        db.runQueryEmitting(makeQueryIn(rowIdSubQuery.sql), rowIdSubQuery.args, onEach)
    }

    fun planQuery(filters: List<Filter>): String {
        val rowIdSubQueries = filters.mapNotNull { prepareRowIDSubQueries(it) }
        if (rowIdSubQueries.isEmpty()) return makeEverythingQuery()
        val unions = rowIdSubQueries.joinToString(" UNION ") { it.sql }
        return makeQueryIn(unions)
    }

    fun query(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): List<Event> {
        val rowIdSubQueries = filters.mapNotNull { prepareRowIDSubQueries(it) }

        if (rowIdSubQueries.isEmpty()) return db.runQuery(makeEverythingQuery())

        val unions = rowIdSubQueries.joinToString(" UNION ") { it.sql }
        val args = rowIdSubQueries.flatMap { it.args }

        return db.runQuery(makeQueryIn(unions), args)
    }

    fun query(
        filters: List<Filter>,
        db: SQLiteDatabase,
        onEach: (Event) -> Unit,
    ) {
        val rowIdSubQueries = filters.mapNotNull { prepareRowIDSubQueries(it) }

        if (rowIdSubQueries.isEmpty()) return db.runQueryEmitting(makeEverythingQuery(), onEach = onEach)

        val unions = rowIdSubQueries.joinToString(" UNION ") { it.sql }
        val args = rowIdSubQueries.flatMap { it.args }

        db.runQueryEmitting(makeQueryIn(unions), args, onEach)
    }

    private fun makeEverythingQuery() = "SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers ORDER BY created_at DESC, id"

    private fun makeQueryIn(rowIdQuery: String) =
        """
        SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
        INNER JOIN ($rowIdQuery) AS filtered
        ON event_headers.row_id = filtered.row_id
        ORDER BY created_at DESC, id
        """.trimIndent()

    private fun SQLiteDatabase.runQuery(
        sql: String,
        args: List<String> = emptyList(),
    ): List<Event> =
        rawQuery(sql, args.toTypedArray()).use { cursor ->
            parseResults(cursor)
        }

    private fun parseResults(cursor: Cursor): List<Event> {
        val events = ArrayList<Event>()

        while (cursor.moveToNext()) {
            events.add(
                EventFactory.create(
                    cursor.getString(0).intern(),
                    cursor.getString(1).intern(),
                    cursor.getLong(2),
                    cursor.getInt(3),
                    OptimizedJsonMapper.fromJsonToTagArray(cursor.getString(4)),
                    cursor.getString(5),
                    cursor.getString(6),
                ),
            )
        }

        return events
    }

    private fun SQLiteDatabase.runQueryEmitting(
        sql: String,
        args: List<String> = emptyList(),
        onEach: (Event) -> Unit,
    ) = rawQuery(sql, args.toTypedArray()).use { cursor ->
        emitResults(cursor, onEach)
    }

    private fun emitResults(
        cursor: Cursor,
        onEach: (Event) -> Unit,
    ) {
        while (cursor.moveToNext()) {
            onEach(
                EventFactory.create(
                    cursor.getString(0).intern(),
                    cursor.getString(1).intern(),
                    cursor.getLong(2),
                    cursor.getInt(3),
                    OptimizedJsonMapper.fromJsonToTagArray(cursor.getString(4)),
                    cursor.getString(5),
                    cursor.getString(6),
                ),
            )
        }
    }

    // --------------
    // Counts
    // -------------
    fun count(
        filter: Filter,
        db: SQLiteDatabase,
    ): Int {
        val rowIdSubQuery = prepareRowIDSubQueries(filter) ?: return db.countEverything()

        return db.countIn(rowIdSubQuery.sql, rowIdSubQuery.args)
    }

    fun count(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): Int {
        val rowIdSubQueries = filters.mapNotNull { prepareRowIDSubQueries(it) }

        if (rowIdSubQueries.isEmpty()) return db.countEverything()

        val unions = rowIdSubQueries.joinToString(" UNION ") { it.sql }
        val args = rowIdSubQueries.flatMap { it.args }

        return db.countIn(unions, args)
    }

    private fun SQLiteDatabase.countEverything() = runCount("SELECT count(*) as count FROM event_headers")

    private fun SQLiteDatabase.countIn(
        rowIdQuery: String,
        args: List<String>,
    ) = runCount("SELECT COUNT(*) as count FROM ($rowIdQuery)", args)

    private fun SQLiteDatabase.runCount(
        sql: String,
        args: List<String> = emptyList(),
    ): Int =
        rawQuery(sql, args.toTypedArray()).use { cursor ->
            cursor.moveToNext()
            cursor.getInt(0)
        }

    // --------------
    // Deletes
    // -------------
    fun delete(
        filter: Filter,
        db: SQLiteDatabase,
    ): Int? {
        val rowIdQuery = prepareRowIDSubQueries(filter) ?: return null
        return db.runDelete(rowIdQuery.sql, rowIdQuery.args)
    }

    fun delete(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): Int? {
        val rowIdSubqueries = filters.mapNotNull { prepareRowIDSubQueries(it) }

        if (rowIdSubqueries.isEmpty()) return null

        val unions = rowIdSubqueries.joinToString(" UNION ") { it.sql }
        val args = rowIdSubqueries.flatMap { it.args }

        return db.runDelete(unions, args)
    }

    private fun SQLiteDatabase.runDelete(
        sql: String,
        args: List<String> = emptyList(),
    ): Int = delete("event_headers", "row_id IN ($sql)", args.toTypedArray())

    // ----------------------------
    // Inner row id selections
    // ----------------------------
    fun prepareRowIDSubQueries(filter: Filter): RowIdSubQuery? {
        if (!filter.isFilledFilter()) return null

        val hasHeaders =
            with(filter) {
                (ids != null && ids.isNotEmpty()) ||
                    (authors != null && authors.isNotEmpty()) ||
                    (kinds != null && kinds.isNotEmpty()) ||
                    (since != null) ||
                    (until != null) ||
                    (tags != null && tags.containsKey("d"))
            }

        val hasSearch = (filter.search != null && filter.search.isNotBlank())

        val projection =
            buildString {
                val anchorColumn: String
                val joins = mutableListOf<String>()

                if (hasHeaders) {
                    append("SELECT event_headers.row_id as row_id FROM event_headers")
                    anchorColumn = "event_headers.row_id"

                    if (hasSearch) {
                        joins.add("INNER JOIN ${fts.tableName} ON ${fts.tableName}.${fts.eventHeaderRowIdName} = $anchorColumn")
                    }

                    filter.tags?.forEach { (tagName, _) ->
                        if (tagName != "d") {
                            joins.add("INNER JOIN event_tags as tag$tagName ON tag$tagName.event_header_row_id = $anchorColumn")
                        }
                    }
                } else if (hasSearch) {
                    append("SELECT ${fts.tableName}.${fts.eventHeaderRowIdName} as row_id FROM ${fts.tableName}")
                    anchorColumn = "${fts.tableName}.${fts.eventHeaderRowIdName}"

                    filter.tags?.forEach { (tagName, _) ->
                        if (tagName != "d") {
                            joins.add("INNER JOIN event_tags as tag$tagName ON tag$tagName.event_header_row_id = $anchorColumn")
                        }
                    }
                } else {
                    // has only tags
                    filter.tags?.forEach { (tagName, _) ->
                        if (tagName != "d") {
                            if (isEmpty()) {
                                append("SELECT tag$tagName.event_header_row_id as row_id FROM event_tags as tag$tagName")
                            } else {
                                joins.add("INNER JOIN event_tags as tag$tagName ON tag$tagName.event_header_row_id = tag${tagName.takeLast(1)}.event_header_row_id")
                            }
                        }
                    }

                    if (isEmpty()) {
                        // only limit is present
                        append("SELECT event_headers.row_id as row_id FROM event_headers")
                    }
                }

                if (joins.isNotEmpty()) {
                    append(" ${joins.joinToString(" ")}")
                }
            }

        val clause =
            where {
                filter.ids?.let { equalsOrIn("event_headers.id", it) }
                filter.kinds?.let { equalsOrIn("event_headers.kind", it) }
                filter.authors?.let { equalsOrIn("event_headers.pubkey", it) }
                filter.since?.let { greaterThanOrEquals("event_headers.created_at", it) }
                filter.until?.let { lessThanOrEquals("event_headers.created_at", it) }

                filter.tags?.forEach { (tagName, tagValues) ->
                    if (tagName == "d") {
                        equalsOrIn("event_headers.d_tag", tagValues)
                    } else {
                        equals("tag$tagName.tag_name", tagName)
                        equalsOrIn("tag$tagName.tag_value", tagValues)
                    }
                }

                filter.search?.let { match(fts.tableName, it) }
            }

        val whereClause =
            if (filter.limit != null) {
                "${clause.conditions} ORDER BY created_at DESC, id ASC LIMIT ${filter.limit}"
            } else {
                clause.conditions
            }

        return RowIdSubQuery("$projection WHERE $whereClause", clause.args)
    }

    fun deleteAll(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM event_tags")
        db.execSQL("DELETE FROM event_headers")
    }

    class RowIdSubQuery(
        val sql: String,
        val args: List<String>,
    )
}
