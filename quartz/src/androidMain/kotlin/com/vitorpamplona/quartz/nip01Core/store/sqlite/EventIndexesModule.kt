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
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.sql.where
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.EventFactory

class EventIndexesModule(
    val fts: FullTextSearchModule,
    val hasher: (db: SQLiteDatabase) -> TagNameValueHasher,
    val tagIndexStrategy: IndexingStrategy = DefaultIndexingStrategy(),
) : IModule {
    override fun create(db: SQLiteDatabase) {
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
                sig TEXT NOT NULL,
                pubkey_owner_hash INTEGER NOT NULL,
                etag_hash INTEGER,
                atag_hash INTEGER
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE event_tags (
                event_header_row_id INTEGER NOT NULL,
                tag_hash INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL("CREATE UNIQUE INDEX event_headers_id       ON event_headers (id)")
        db.execSQL("CREATE INDEX query_by_kind_pubkey_dtag_idx ON event_headers (kind, pubkey, d_tag)")
        db.execSQL("CREATE INDEX query_by_created_at_id        ON event_headers (created_at DESC, id)")

        // need to check if this is actually needed.
        db.execSQL("CREATE INDEX query_by_created_at_kind_key  ON event_headers (created_at DESC, kind, pubkey)")

        db.execSQL("CREATE INDEX fk_event_tags_header_id       ON event_tags (event_header_row_id)")

        // This is a very slow index to build (half the insert time goes here) but it is extremely effective.
        db.execSQL("CREATE INDEX query_by_tags_hash            ON event_tags (tag_hash, created_at DESC)")

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

    override fun drop(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS event_tags")
        db.execSQL("DROP TABLE IF EXISTS event_headers")
    }

    val sqlInsertHeader =
        """
        INSERT INTO event_headers
            (id, pubkey, created_at, kind, tags, content, sig, d_tag, pubkey_owner_hash, etag_hash, atag_hash)
        VALUES
            (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    val sqlInsertTags =
        """
        INSERT OR ROLLBACK INTO event_tags
            (event_header_row_id, tag_hash, created_at)
        VALUES
            (?,?,?)
        """.trimIndent()

    fun insert(
        event: Event,
        db: SQLiteDatabase,
    ): Long {
        val hasher = hasher(db)
        val stmt = db.compileStatement(sqlInsertHeader)

        val kindLong = event.kind.toLong()
        val pubkeyHash = hasher.hash(event.pubKey)

        val eventOwnerHash =
            if (event is GiftWrapEvent) {
                event.recipientPubKey()?.let { hasher.hash(it) } ?: pubkeyHash
            } else {
                pubkeyHash
            }

        val eTagHash = hasher.hashETag(event.id)

        stmt.bindString(1, event.id)
        stmt.bindString(2, event.pubKey)
        stmt.bindLong(3, event.createdAt)
        stmt.bindLong(4, kindLong)
        stmt.bindString(5, OptimizedJsonMapper.toJson(event.tags))
        stmt.bindString(6, event.content)
        stmt.bindString(7, event.sig)
        if (event is AddressableEvent) {
            val dTag = event.dTag()
            stmt.bindString(8, dTag)
            stmt.bindLong(9, eventOwnerHash)
            stmt.bindLong(10, eTagHash)
            stmt.bindLong(11, hasher.hashATag(AddressSerializer.assemble(event.kind, event.pubKey, dTag)))
        } else {
            stmt.bindNull(8)
            stmt.bindLong(9, eventOwnerHash)
            stmt.bindLong(10, eTagHash)
            stmt.bindNull(11)
        }

        val headerId = stmt.executeInsert()

        val stmtTags = db.compileStatement(sqlInsertTags)

        // sorting helps SQLLite by avoiding
        // rebalancing the tree every new insert
        val indexableTags = ArrayList<Long>()
        for (idx in event.tags.indices) {
            if (tagIndexStrategy.shouldIndex(event.kind, event.tags[idx])) {
                indexableTags.add(hasher.hash(event.tags[idx][0], event.tags[idx][1]))
            }
        }
        indexableTags.sort()
        indexableTags.forEach {
            stmtTags.bindLong(1, headerId)
            stmtTags.bindLong(2, it)
            stmtTags.bindLong(3, event.createdAt)
            stmtTags.executeInsert()
        }

        return headerId
    }

    fun planQuery(
        filter: Filter,
        hasher: TagNameValueHasher,
        db: SQLiteDatabase,
    ): String {
        val rowIdSubQuery = prepareRowIDSubQueries(filter, hasher)

        return if (rowIdSubQuery == null) {
            val query = makeEverythingQuery()
            db.explainQuery(query)
        } else {
            val query = makeQueryIn(rowIdSubQuery.sql)
            db.explainQuery(query, rowIdSubQuery.args.toTypedArray())
        }
    }

    fun <T : Event> query(
        filter: Filter,
        db: SQLiteDatabase,
    ): List<T> {
        val rowIdSubQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdSubQuery == null) {
            db.runQuery(makeEverythingQuery())
        } else {
            db.runQuery(makeQueryIn(rowIdSubQuery.sql), rowIdSubQuery.args)
        }
    }

    fun <T : Event> query(
        filter: Filter,
        db: SQLiteDatabase,
        onEach: (T) -> Unit,
    ) {
        val rowIdSubQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdSubQuery == null) {
            db.runQuery(makeEverythingQuery(), onEach = onEach)
        } else {
            db.runQuery(makeQueryIn(rowIdSubQuery.sql), rowIdSubQuery.args, onEach)
        }
    }

    fun planQuery(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
        db: SQLiteDatabase,
    ): String {
        val rowIdSubQuery = unionSubqueriesIfNeeded(filters, hasher)

        return if (rowIdSubQuery == null) {
            val query = makeEverythingQuery()
            db.explainQuery(query)
        } else {
            val query = makeQueryIn(rowIdSubQuery.sql)
            db.explainQuery(query, rowIdSubQuery.args.toTypedArray())
        }
    }

    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): List<T> {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db)) ?: return db.runQuery(makeEverythingQuery())
        return db.runQuery(makeQueryIn(rowIdSubqueries.sql), rowIdSubqueries.args)
    }

    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteDatabase,
        onEach: (T) -> Unit,
    ) {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db))

        if (rowIdSubqueries == null) {
            db.runQuery(makeEverythingQuery(), onEach = onEach)
        } else {
            db.runQuery(makeQueryIn(rowIdSubqueries.sql), rowIdSubqueries.args, onEach)
        }
    }

    private fun makeEverythingQuery() = "SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers ORDER BY created_at DESC, id"

    private fun makeQueryIn(rowIdQuery: String) =
        """
        SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
        INNER JOIN (
            $rowIdQuery
        ) AS filtered
        ON event_headers.row_id = filtered.row_id
        ORDER BY created_at DESC, id
        """.trimIndent()

    private fun <T : Event> SQLiteDatabase.runQuery(
        sql: String,
        args: List<String> = emptyList(),
    ): List<T> =
        rawQuery(sql, args.toTypedArray()).use { cursor ->
            ArrayList<T>(cursor.count).apply {
                while (cursor.moveToNext()) {
                    add(cursor.toEvent())
                }
            }
        }

    private inline fun <T : Event> SQLiteDatabase.runQuery(
        sql: String,
        args: List<String> = emptyList(),
        onEach: (T) -> Unit,
    ) = rawQuery(sql, args.toTypedArray()).use { cursor ->
        while (cursor.moveToNext()) {
            onEach(cursor.toEvent())
        }
    }

    private fun <T : Event> Cursor.toEvent() =
        EventFactory.create<T>(
            getString(0).intern(),
            getString(1).intern(),
            getLong(2),
            getInt(3),
            OptimizedJsonMapper.fromJsonToTagArray(getString(4)),
            getString(5),
            getString(6),
        )

    class RawEvent(
        val id: HexKey,
        val pubKey: HexKey,
        val createdAt: Long,
        val kind: Kind,
        val jsonTags: String,
        val content: String,
        val sig: HexKey,
    ) {
        fun <T : Event> toEvent() =
            EventFactory.create<T>(
                id.intern(),
                pubKey.intern(),
                createdAt,
                kind,
                OptimizedJsonMapper.fromJsonToTagArray(jsonTags),
                content,
                sig,
            )
    }

    private fun Cursor.toRawEvent() =
        RawEvent(
            getString(0),
            getString(1),
            getLong(2),
            getInt(3),
            getString(4),
            getString(5),
            getString(6),
        )

    // --------------
    // Counts
    // -------------
    fun count(
        filter: Filter,
        db: SQLiteDatabase,
    ): Int {
        val rowIdSubQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdSubQuery == null) {
            db.countEverything()
        } else {
            db.countIn(rowIdSubQuery.sql, rowIdSubQuery.args)
        }
    }

    fun count(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): Int {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db)) ?: return db.countEverything()

        return db.countIn(rowIdSubqueries.sql, rowIdSubqueries.args)
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
    ): Int {
        val rowIdQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdQuery == null) {
            0
        } else {
            db.runDelete(rowIdQuery.sql, rowIdQuery.args)
        }
    }

    fun delete(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): Int {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db)) ?: return 0

        return db.runDelete(rowIdSubqueries.sql, rowIdSubqueries.args)
    }

    private fun SQLiteDatabase.runDelete(
        sql: String,
        args: List<String> = emptyList(),
    ): Int = delete("event_headers", "row_id IN ($sql)", args.toTypedArray())

    // ---------------------------------
    // Prepare unions of all the filters
    // ---------------------------------
    fun unionSubqueriesIfNeeded(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
    ): RowIdSubQuery? {
        val inner =
            filters.mapNotNull { filter ->
                prepareRowIDSubQueries(filter, hasher)
            }

        if (inner.isEmpty()) return null

        return if (inner.size == 1) {
            inner.first()
        } else {
            RowIdSubQuery(
                sql = inner.joinToString("\n            UNION\n            ") { "SELECT row_id FROM (${it.sql})" },
                args = inner.flatMap { it.args },
            )
        }
    }

    sealed class TagNameForQuery {
        class InTags(
            val tagName: String,
        ) : TagNameForQuery()

        class AllTags(
            val tagName: String,
            val tagValueIndex: Int,
        ) : TagNameForQuery()
    }

    // ----------------------------
    // Inner row id selections
    // ----------------------------
    fun prepareRowIDSubQueries(
        filter: Filter,
        hasher: TagNameValueHasher,
    ): RowIdSubQuery? {
        if (!filter.isFilledFilter()) return null

        val mustJoinSearch = (filter.search != null)

        val nonDTagsIn = filter.tags?.filter { it.key != "d" } ?: emptyMap()

        val nonDTagsAll = filter.tagsAll?.filter { it.key != "d" } ?: emptyMap()

        val reverseLookup = nonDTagsIn.isNotEmpty() || nonDTagsAll.isNotEmpty()

        val needHeaders =
            with(filter) {
                (ids != null) ||
                    (authors != null && authors.isNotEmpty()) ||
                    (kinds != null && kinds.isNotEmpty()) ||
                    (tags != null && tags.containsKey("d"))
            }

        val hasHeaders =
            with(filter) {
                (ids != null) ||
                    (authors != null && authors.isNotEmpty()) ||
                    (kinds != null && kinds.isNotEmpty()) ||
                    (tags != null && tags.containsKey("d")) ||
                    (since != null) ||
                    (until != null) ||
                    (limit != null)
            }

        var defaultTagKey: TagNameForQuery? = null

        val projection =
            buildString {
                // always do tags if there are any
                if (reverseLookup) {
                    append("SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags ")

                    // it's quite rare to have 2 tags in the filter, but possible
                    nonDTagsIn.keys.forEachIndexed { index, tagName ->
                        if (defaultTagKey != null) {
                            append("INNER JOIN event_tags as event_tagsIn$index ON event_tagsIn$index.event_header_row_id = event_tags.event_header_row_id AND event_tagsIn$index.created_at = event_tags.created_at ")
                        } else {
                            defaultTagKey = TagNameForQuery.InTags(tagName)
                        }
                    }

                    nonDTagsAll.keys.forEachIndexed { index, tagName ->
                        nonDTagsAll[tagName]!!.forEachIndexed { valueIndex, tagValue ->
                            if (defaultTagKey != null) {
                                append("INNER JOIN event_tags as event_tagsAll${index}_$valueIndex ON event_tagsAll${index}_$valueIndex.event_header_row_id = event_tags.event_header_row_id AND event_tagsAll${index}_$valueIndex.created_at = event_tags.created_at ")
                            } else {
                                defaultTagKey = TagNameForQuery.AllTags(tagName, valueIndex)
                            }
                        }
                    }

                    if (needHeaders) {
                        append("INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id ")
                    }

                    if (mustJoinSearch) {
                        append("INNER JOIN ${fts.tableName} ON ${fts.tableName}.${fts.eventHeaderRowIdName} = event_tags.event_header_row_id ")
                    }
                } else if (mustJoinSearch) {
                    append("SELECT ${fts.tableName}.${fts.eventHeaderRowIdName} as row_id FROM ${fts.tableName} ")

                    if (hasHeaders) {
                        append("INNER JOIN event_headers ON event_headers.row_id = ${fts.tableName}.${fts.eventHeaderRowIdName}")
                    }
                } else {
                    // no tags and no search.
                    append("SELECT event_headers.row_id as row_id FROM event_headers ")
                }
            }

        val clause =
            where {
                // the order should match indexes
                // ids reduce the filter the most
                filter.ids?.let { equalsOrIn("event_headers.id", it) }

                // it's quite rare to have 2 tags in the filter, but possible
                nonDTagsIn.keys.forEachIndexed { index, tagName ->
                    val column =
                        if (defaultTagKey == null || (defaultTagKey is TagNameForQuery.InTags && defaultTagKey.tagName == tagName)) {
                            "event_tags.tag_hash"
                        } else {
                            "event_tagsIn$index.tag_hash"
                        }

                    equalsOrIn(
                        column,
                        nonDTagsIn[tagName]!!.map {
                            hasher.hash(tagName, it)
                        },
                    )
                }

                // there are indexes for these, starting with tags.
                nonDTagsAll.keys.forEachIndexed { index, tagName ->
                    nonDTagsAll[tagName]!!.forEachIndexed { valueIndex, tagValue ->
                        val column =
                            if (defaultTagKey == null || (defaultTagKey is TagNameForQuery.AllTags && defaultTagKey.tagName == tagName && defaultTagKey.tagValueIndex == valueIndex)) {
                                "event_tags.tag_hash"
                            } else {
                                "event_tagsAll${index}_$valueIndex.tag_hash"
                            }

                        equals(column, hasher.hash(tagName, tagValue))
                    }
                }

                // range search is bad but most of the time these are up the top with few elements.
                if (reverseLookup) {
                    filter.since?.let { greaterThanOrEquals("event_tags.created_at", it) }
                    filter.until?.let { lessThanOrEquals("event_tags.created_at", it) }
                } else {
                    filter.since?.let { greaterThanOrEquals("event_headers.created_at", it) }
                    filter.until?.let { lessThanOrEquals("event_headers.created_at", it) }
                }

                filter.kinds?.let { equalsOrIn("event_headers.kind", it) }
                filter.authors?.let { equalsOrIn("event_headers.pubkey", it) }

                // there are indexes for these, starting with tags.
                filter.tags?.forEach { (tagName, tagValues) ->
                    if (tagName == "d") {
                        equalsOrIn("event_headers.d_tag", tagValues)
                    }
                }

                // if search is included, SQLLite will always start here.
                filter.search?.let {
                    if (it.isNotBlank()) {
                        match(fts.tableName, it)
                    }
                }
            }

        val whereClause =
            if (filter.limit != null) {
                if (reverseLookup) {
                    "${clause.conditions} ORDER BY event_tags.created_at DESC LIMIT ${filter.limit}"
                } else {
                    "${clause.conditions} ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT ${filter.limit}"
                }
            } else {
                clause.conditions
            }

        return RowIdSubQuery("$projection WHERE $whereClause", clause.args)
    }

    override fun deleteAll(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM event_tags")
        db.execSQL("DELETE FROM event_headers")
    }

    data class RowIdSubQuery(
        val sql: String,
        val args: List<String>,
    )
}
