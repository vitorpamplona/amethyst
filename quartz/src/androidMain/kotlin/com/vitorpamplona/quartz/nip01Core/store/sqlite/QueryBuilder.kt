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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.sql.where
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.collections.component1
import kotlin.collections.component2

class QueryBuilder(
    val fts: FullTextSearchModule,
    val hasher: (db: SQLiteDatabase) -> TagNameValueHasher,
    val indexStrategy: IndexingStrategy,
) {
    // ------------
    // Main methods
    // ------------
    fun <T : Event> query(
        filter: Filter,
        db: SQLiteDatabase,
    ): List<T> = db.runQuery(toSql(filter, hasher(db)))

    fun <T : Event> query(
        filter: Filter,
        db: SQLiteDatabase,
        onEach: (T) -> Unit,
    ) = db.runQuery(toSql(filter, hasher(db)), onEach)

    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): List<T> = db.runQuery(toSql(filters, hasher(db)))

    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteDatabase,
        onEach: (T) -> Unit,
    ) = db.runQuery(toSql(filters, hasher(db)), onEach)

    // ---------------------------
    // Raw methods for performance
    // ---------------------------
    fun rawQuery(
        filter: Filter,
        db: SQLiteDatabase,
    ): List<RawEvent> = db.runRawQuery(toSql(filter, hasher(db)))

    fun rawQuery(
        filter: Filter,
        db: SQLiteDatabase,
        onEach: (RawEvent) -> Unit,
    ) = db.runRawQuery(toSql(filter, hasher(db)), onEach)

    fun rawQuery(
        filters: List<Filter>,
        db: SQLiteDatabase,
    ): List<RawEvent> = db.runRawQuery(toSql(filters, hasher(db)))

    fun rawQuery(
        filters: List<Filter>,
        db: SQLiteDatabase,
        onEach: (RawEvent) -> Unit,
    ) = db.runRawQuery(toSql(filters, hasher(db)), onEach)

    // -----------
    // Debug Tools
    // -----------
    fun planQuery(
        filter: Filter,
        hasher: TagNameValueHasher,
        db: SQLiteDatabase,
    ): String {
        val query = toSql(filter, hasher)
        return db.explainQuery(query.sql, query.args.toTypedArray())
    }

    fun planQuery(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
        db: SQLiteDatabase,
    ): String {
        val query = toSql(filters, hasher)
        return db.explainQuery(query.sql, query.args.toTypedArray())
    }

    fun toSql(
        filter: Filter,
        hasher: TagNameValueHasher,
    ): QuerySpec {
        val newFilter = filter.toFilterWithDTags()

        if (newFilter.isSimpleQuery()) {
            return makeSimpleQuery(
                ids = newFilter.ids,
                authors = newFilter.authors,
                kinds = newFilter.kinds,
                dTags = newFilter.dTags,
                since = newFilter.since,
                until = newFilter.until,
                limit = newFilter.limit,
            )
        }

        if (newFilter.isSimpleSearch()) {
            return makeSimpleSearch(
                search = newFilter.search!!,
                ids = newFilter.ids,
                authors = newFilter.authors,
                kinds = newFilter.kinds,
                dTags = newFilter.dTags,
                since = newFilter.since,
                until = newFilter.until,
                limit = newFilter.limit,
            )
        }

        val rowIdSubqueries = prepareRowIDSubQueries(filter, hasher)

        return if (rowIdSubqueries == null) {
            QuerySpec(makeEverythingQuery())
        } else {
            QuerySpec(
                makeQueryIn(rowIdSubqueries.sql),
                rowIdSubqueries.args,
            )
        }
    }

    fun toSql(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
    ): QuerySpec {
        if (filters.size == 1) return toSql(filters.first(), hasher)

        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher)

        return if (rowIdSubqueries == null) {
            QuerySpec(
                makeEverythingQuery(),
                emptyList(),
            )
        } else {
            QuerySpec(
                makeQueryIn(rowIdSubqueries.sql),
                rowIdSubqueries.args,
            )
        }
    }

    private fun makeEverythingQuery() = "SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers ORDER BY created_at DESC${if (indexStrategy.useAndIndexIdOnOrderBy) ", id ASC" else ""}"

    private fun makeQueryIn(rowIdQuery: String) =
        """
        SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
        INNER JOIN (
            $rowIdQuery
        ) AS filtered
        ON event_headers.row_id = filtered.row_id
        ORDER BY created_at DESC${if (indexStrategy.useAndIndexIdOnOrderBy) ", id ASC" else ""}
        """.trimIndent()

    private fun <T : Event> SQLiteDatabase.runQuery(query: QuerySpec): List<T> =
        rawQuery(query.sql, query.args.toTypedArray()).use { cursor ->
            ArrayList<T>(cursor.count).apply {
                while (cursor.moveToNext()) {
                    add(cursor.toEvent())
                }
            }
        }

    private fun SQLiteDatabase.runRawQuery(query: QuerySpec): List<RawEvent> =
        rawQuery(query.sql, query.args.toTypedArray()).use { cursor ->
            ArrayList<RawEvent>(cursor.count).apply {
                while (cursor.moveToNext()) {
                    add(cursor.toRawEvent())
                }
            }
        }

    private inline fun <T : Event> SQLiteDatabase.runQuery(
        query: QuerySpec,
        onEach: (T) -> Unit,
    ) = rawQuery(query.sql, query.args.toTypedArray()).use { cursor ->
        while (cursor.moveToNext()) {
            onEach(cursor.toEvent())
        }
    }

    private inline fun SQLiteDatabase.runRawQuery(
        query: QuerySpec,
        onEach: (RawEvent) -> Unit,
    ) = rawQuery(query.sql, query.args.toTypedArray()).use { cursor ->
        while (cursor.moveToNext()) {
            onEach(cursor.toRawEvent())
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
    ): QuerySpec? {
        val inner =
            filters.mapNotNull { filter ->
                prepareRowIDSubQueries(filter, hasher)
            }

        if (inner.isEmpty()) return null

        return if (inner.size == 1) {
            inner.first()
        } else {
            QuerySpec(
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
    ): QuerySpec? {
        if (filter.isEmpty()) return null

        val mustJoinSearch = (filter.search != null)

        val nonDTagsIn = filter.tags?.filter { it.key != "d" } ?: emptyMap()

        val nonDTagsAll = filter.tagsAll?.filter { it.key != "d" } ?: emptyMap()

        val reverseLookup = nonDTagsIn.isNotEmpty() || nonDTagsAll.isNotEmpty()

        val needHeaders =
            with(filter) {
                (ids != null) || (tags != null && tags.containsKey("d"))
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
                    append("SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags")

                    // it's quite rare to have 2 tags in the filter, but possible
                    nonDTagsIn.keys.forEachIndexed { index, tagName ->
                        if (defaultTagKey != null) {
                            append(" INNER JOIN event_tags as event_tagsIn$index ON event_tagsIn$index.event_header_row_id = event_tags.event_header_row_id AND event_tagsIn$index.created_at = event_tags.created_at")
                        } else {
                            defaultTagKey = TagNameForQuery.InTags(tagName)
                        }
                    }

                    nonDTagsAll.keys.forEachIndexed { index, tagName ->
                        nonDTagsAll[tagName]!!.forEachIndexed { valueIndex, tagValue ->
                            if (defaultTagKey != null) {
                                append(" INNER JOIN event_tags as event_tagsAll${index}_$valueIndex ON event_tagsAll${index}_$valueIndex.event_header_row_id = event_tags.event_header_row_id AND event_tagsAll${index}_$valueIndex.created_at = event_tags.created_at")
                            } else {
                                defaultTagKey = TagNameForQuery.AllTags(tagName, valueIndex)
                            }
                        }
                    }

                    if (needHeaders) {
                        append(" INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id")
                    }

                    if (mustJoinSearch) {
                        append(" INNER JOIN ${fts.tableName} ON ${fts.tableName}.${fts.eventHeaderRowIdName} = event_tags.event_header_row_id")
                    }
                } else if (mustJoinSearch) {
                    append("SELECT ${fts.tableName}.${fts.eventHeaderRowIdName} as row_id FROM ${fts.tableName}")

                    if (hasHeaders) {
                        append(" INNER JOIN event_headers ON event_headers.row_id = ${fts.tableName}.${fts.eventHeaderRowIdName}")
                    }
                } else {
                    // no tags and no search.
                    append("SELECT event_headers.row_id as row_id FROM event_headers")
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
                    filter.kinds?.let { equalsOrIn("event_tags.kind", it) }
                    filter.authors?.let { equalsOrIn("event_tags.pubkey_hash", it.map { hasher.hash(it) }) }

                    filter.since?.let { greaterThanOrEquals("event_tags.created_at", it) }
                    filter.until?.let { lessThanOrEquals("event_tags.created_at", it) }

                    // there are indexes for these, starting with tags.
                    filter.tags?.forEach { (tagName, tagValues) ->
                        if (tagName == "d") {
                            equalsOrIn("event_headers.d_tag", tagValues)
                        }
                    }
                } else {
                    filter.kinds?.let { equalsOrIn("event_headers.kind", it) }
                    filter.authors?.let { equalsOrIn("event_headers.pubkey", it) }

                    // there are indexes for these, starting with tags.
                    filter.tags?.forEach { (tagName, tagValues) ->
                        if (tagName == "d") {
                            equalsOrIn("event_headers.d_tag", tagValues)
                        }
                    }

                    filter.since?.let { greaterThanOrEquals("event_headers.created_at", it) }
                    filter.until?.let { lessThanOrEquals("event_headers.created_at", it) }

                    // no need to add the replaceable because query_by_kind_pubkey_created already covers it
                    val isAllAddressable = filter.kinds?.all { it.isAddressable() } ?: false
                    if (isAllAddressable) {
                        // matches unique index kind >= 30000 AND kind < 40000
                        raw("(event_headers.kind >= 30000 AND event_headers.kind < 40000)")
                    }
                }

                // if search is included, SQLLite will always start here.
                filter.search?.let {
                    if (it.isNotBlank()) {
                        match(fts.tableName, it)
                    }
                }
            }

        val sql =
            buildString {
                append(projection)
                if (clause.conditions.isNotEmpty()) {
                    append(" WHERE ${clause.conditions}")
                }
                if (filter.limit != null) {
                    if (reverseLookup) {
                        append(" ORDER BY event_tags.created_at DESC")
                        append(" LIMIT ")
                        append(filter.limit)
                    } else {
                        append(" ORDER BY event_headers.created_at DESC")
                        append(" LIMIT ")
                        append(filter.limit)
                    }
                }
            }

        return QuerySpec(sql, clause.args)
    }

    private fun makeSimpleSearch(
        search: String,
        ids: List<HexKey>? = null,
        authors: List<HexKey>? = null,
        kinds: List<Kind>? = null,
        dTags: List<String>? = null,
        since: Long? = null,
        until: Long? = null,
        limit: Int? = null,
    ): QuerySpec {
        val clause =
            where {
                // the order should match indexes
                // ids reduce the filter the most
                ids?.let { equalsOrIn("event_headers.id", it) }

                match(fts.tableName, search)

                kinds?.let { equalsOrIn("event_headers.kind", it) }
                authors?.let { equalsOrIn("event_headers.pubkey", it) }

                // there are indexes for these, starting with tags.
                dTags?.let { equalsOrIn("event_headers.d_tag", it) }

                since?.let { greaterThanOrEquals("event_headers.created_at", it) }
                until?.let { lessThanOrEquals("event_headers.created_at", it) }

                // if this is a dTag filter, it is likely that all kinds are addressables
                // and so force the use of the addressable index
                if (dTags != null && kinds != null) {
                    if (kinds.all { it.isAddressable() }) {
                        // matches unique index kind >= 30000 AND kind < 40000
                        raw("(event_headers.kind >= 30000 AND kind < 40000)")
                    }
                }
            }

        val sql =
            buildString {
                append("SELECT event_headers.id, event_headers.pubkey, event_headers.created_at, event_headers.kind, event_headers.tags, event_headers.content, event_headers.sig FROM event_headers")
                append("\nINNER JOIN ${fts.tableName} ON event_headers.row_id = ${fts.tableName}.${fts.eventHeaderRowIdName}")
                if (clause.conditions.isNotEmpty()) {
                    append("\nWHERE ${clause.conditions}")
                }
                append("\nORDER BY event_headers.created_at DESC")
                if (indexStrategy.useAndIndexIdOnOrderBy) {
                    append(", event_headers.id ASC")
                }
                if (limit != null) {
                    append("\nLIMIT ")
                    append(limit)
                }
            }

        println(sql)

        return QuerySpec(sql, clause.args)
    }

    private fun makeSimpleQuery(
        ids: List<HexKey>? = null,
        authors: List<HexKey>? = null,
        kinds: List<Kind>? = null,
        dTags: List<String>? = null,
        since: Long? = null,
        until: Long? = null,
        limit: Int? = null,
    ): QuerySpec {
        val clause =
            where {
                // the order should match indexes
                // ids reduce the filter the most
                ids?.let { equalsOrIn("id", it) }

                kinds?.let { equalsOrIn("kind", it) }
                authors?.let { equalsOrIn("pubkey", it) }

                // there are indexes for these, starting with tags.
                dTags?.let { equalsOrIn("d_tag", it) }

                since?.let { greaterThanOrEquals("created_at", it) }
                until?.let { lessThanOrEquals("created_at", it) }

                // if this is a dTag filter, it is likely that all kinds are addressables
                // and so force the use of the addressable index
                if (dTags != null && kinds != null) {
                    if (kinds.all { it.isAddressable() }) {
                        // matches unique index kind >= 30000 AND kind < 40000
                        raw("(kind >= 30000 AND kind < 40000)")
                    }
                }
            }

        val sql =
            buildString {
                append("SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers")
                if (clause.conditions.isNotEmpty()) {
                    append("\nWHERE ")
                    append(clause.conditions)
                }
                append("\nORDER BY created_at DESC")
                if (indexStrategy.useAndIndexIdOnOrderBy) {
                    append(", event_headers.id ASC")
                }
                if (limit != null) {
                    append("\nLIMIT ")
                    append(limit)
                }
            }

        println(sql)

        return QuerySpec(sql, clause.args)
    }

    class FilterWithDTags(
        val ids: List<HexKey>? = null,
        val authors: List<HexKey>? = null,
        val kinds: List<Kind>? = null,
        val dTags: List<String>? = null,
        val nonDTagsIn: Map<String, List<String>>? = null,
        val nonDTagsAll: Map<String, List<String>>? = null,
        val since: Long? = null,
        val until: Long? = null,
        val limit: Int? = null,
        val search: String? = null,
    ) {
        fun isSimpleSearch() =
            search != null && search.isNotEmpty() &&
                (nonDTagsIn == null || nonDTagsIn.isEmpty()) &&
                (nonDTagsAll == null || nonDTagsAll.isEmpty())

        // can be resolved with just event_headers
        fun isSimpleQuery() =
            (nonDTagsIn == null || nonDTagsIn.isEmpty()) &&
                (nonDTagsAll == null || nonDTagsAll.isEmpty()) &&
                (search == null || search.isEmpty())
    }

    fun Filter.toFilterWithDTags(): FilterWithDTags =
        FilterWithDTags(
            ids = ids,
            authors = authors,
            kinds = kinds,
            dTags = tags?.get("d") ?: tagsAll?.get("d"),
            nonDTagsIn = tags?.filter { it.key != "d" }?.ifEmpty { null },
            nonDTagsAll = tagsAll?.filter { it.key != "d" }?.ifEmpty { null },
            since = since,
            until = until,
            limit = limit,
            search = search,
        )

    data class QuerySpec(
        val sql: String,
        val args: List<String> = emptyList(),
    )
}
