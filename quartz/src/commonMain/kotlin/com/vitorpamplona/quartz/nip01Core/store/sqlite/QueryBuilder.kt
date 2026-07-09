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
import androidx.sqlite.SQLiteStatement
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip01Core.store.sqlite.sql.where
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.EventFactory

class QueryBuilder(
    val fts: FullTextSearchModule,
    val hasher: (db: SQLiteConnection) -> TagNameValueHasher,
    val indexStrategy: IndexingStrategy,
) {
    // ------------
    // Main methods
    // ------------
    fun <T : Event> query(
        filter: Filter,
        db: SQLiteConnection,
    ): List<T> {
        val merge = filter.toFilterWithDTags()
        if (MergeQueryExecutor.streamCount(merge, indexStrategy) > 0) {
            val out = ArrayList<T>(merge.limit!!)
            MergeQueryExecutor.run(db, merge, indexStrategy) { out.add(it.toEvent()) }
            return out
        }
        return db.runQuery(toSql(filter, hasher(db)))
    }

    fun <T : Event> query(
        filter: Filter,
        db: SQLiteConnection,
        onEach: (T) -> Unit,
    ) {
        val merge = filter.toFilterWithDTags()
        if (MergeQueryExecutor.streamCount(merge, indexStrategy) > 0) {
            MergeQueryExecutor.run(db, merge, indexStrategy) { onEach(it.toEvent()) }
            return
        }
        db.runQuery(toSql(filter, hasher(db)), onEach)
    }

    // A single-filter list is the home-feed REQ shape — route it through the
    // single-filter path so the k-way merge (MergeQueryExecutor) applies.
    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteConnection,
    ): List<T> =
        if (filters.size == 1) {
            query(filters[0], db)
        } else {
            db.runQuery(toSql(filters, hasher(db)))
        }

    fun <T : Event> query(
        filters: List<Filter>,
        db: SQLiteConnection,
        onEach: (T) -> Unit,
    ) {
        if (filters.size == 1) {
            query(filters[0], db, onEach)
        } else {
            db.runQuery(toSql(filters, hasher(db)), onEach)
        }
    }

    // ---------------------------
    // Raw methods for performance
    // ---------------------------
    fun rawQuery(
        filter: Filter,
        db: SQLiteConnection,
    ): List<RawEvent> {
        val merge = filter.toFilterWithDTags()
        if (MergeQueryExecutor.streamCount(merge, indexStrategy) > 0) {
            val out = ArrayList<RawEvent>(merge.limit!!)
            MergeQueryExecutor.run(db, merge, indexStrategy) { out.add(it.toRawEvent()) }
            return out
        }
        return db.runRawQuery(toSql(filter, hasher(db)))
    }

    fun rawQuery(
        filter: Filter,
        db: SQLiteConnection,
        onEach: (RawEvent) -> Unit,
    ) {
        val merge = filter.toFilterWithDTags()
        if (MergeQueryExecutor.streamCount(merge, indexStrategy) > 0) {
            MergeQueryExecutor.run(db, merge, indexStrategy) { onEach(it.toRawEvent()) }
            return
        }
        db.runRawQuery(toSql(filter, hasher(db)), onEach)
    }

    fun rawQuery(
        filters: List<Filter>,
        db: SQLiteConnection,
    ): List<RawEvent> =
        if (filters.size == 1) {
            rawQuery(filters[0], db)
        } else {
            db.runRawQuery(toSql(filters, hasher(db)))
        }

    fun rawQuery(
        filters: List<Filter>,
        db: SQLiteConnection,
        onEach: (RawEvent) -> Unit,
    ) {
        if (filters.size == 1) {
            rawQuery(filters[0], db, onEach)
        } else {
            db.runRawQuery(toSql(filters, hasher(db)), onEach)
        }
    }

    // -----------
    // Debug Tools
    // -----------
    fun planQuery(
        filter: Filter,
        hasher: TagNameValueHasher,
        db: SQLiteConnection,
    ): String {
        val query = toSql(filter, hasher)
        return db.explainQuery(query.sql, query.args.toTypedArray())
    }

    fun planQuery(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
        db: SQLiteConnection,
    ): String {
        val query = toSql(filters, hasher)
        return db.explainQuery(query.sql, query.args.toTypedArray())
    }

    fun toSql(
        filter: Filter,
        hasher: TagNameValueHasher,
    ): QuerySpec {
        val newFilter = filter.toFilterWithDTags()

        // With FTS off there is no event_fts table to MATCH against, so a
        // search term can never be satisfied — the filter matches nothing.
        if (searchDisabledMatchesNothing(newFilter.search)) {
            return QuerySpec(makeMatchesNothingQuery())
        }

        if (newFilter.isSimpleQuery()) {
            return makeSimpleQuery(
                project = true,
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

    // -----------------------------------------------------------------
    // NIP-77 negentropy snapshot path
    //
    // Projects only (id, created_at) — no content/tags/sig decode —
    // so the relay can build a StorageVector without materialising
    // full Event objects. ~40 B/entry instead of ~1 KB/entry.
    // No ORDER BY: negentropy's seal() re-sorts. No limit injection:
    // the per-session cap is enforced upstream as a count check.
    // -----------------------------------------------------------------
    fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        db: SQLiteConnection,
        maxEntries: Int? = null,
    ): List<IdAndTime> {
        val inner =
            if (filters.size == 1) {
                toSnapshotIdsSql(filters.first(), hasher(db))
            } else {
                toSnapshotIdsSql(filters, hasher(db))
            }
        // Safety cap: wrap with `LIMIT maxEntries + 1` so we can
        // detect overflow without scanning beyond the cap. The +1
        // sentinel lets the caller distinguish "exactly capped" from
        // "too many to fit". Matches strfry's `maxSyncEvents` guard.
        val query =
            if (maxEntries != null) {
                QuerySpec(
                    "SELECT id, created_at FROM (${inner.sql}) LIMIT ${maxEntries + 1}",
                    inner.args,
                )
            } else {
                inner
            }
        return db.runIdAndTimeQuery(query)
    }

    private fun toSnapshotIdsSql(
        filter: Filter,
        hasher: TagNameValueHasher,
    ): QuerySpec {
        val newFilter = filter.toFilterWithDTags()

        // With FTS off a search term can never be satisfied — no matches.
        if (searchDisabledMatchesNothing(newFilter.search)) {
            return QuerySpec("SELECT id, created_at FROM event_headers WHERE 0")
        }

        // Simple path — no tag joins, no FTS — collapses to a single
        // SELECT against event_headers.
        if (newFilter.isSimpleQuery()) {
            return makeSimpleIdsQuery(
                ids = newFilter.ids,
                authors = newFilter.authors,
                kinds = newFilter.kinds,
                dTags = newFilter.dTags,
                since = newFilter.since,
                until = newFilter.until,
                limit = newFilter.limit,
            )
        }

        // Search path — FTS join. Project id+created_at off
        // event_headers via the FTS row_id linkage.
        if (newFilter.isSimpleSearch()) {
            return makeSimpleIdsSearch(
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

        // Tag-join path — reuse the existing row_id subquery and
        // join back to event_headers for the projection.
        val rowIdSubquery = prepareRowIDSubQueries(filter, hasher)
        return if (rowIdSubquery == null) {
            QuerySpec("SELECT id, created_at FROM event_headers")
        } else {
            QuerySpec(
                """
                SELECT event_headers.id, event_headers.created_at FROM event_headers
                INNER JOIN (
                    ${rowIdSubquery.sql}
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                """.trimIndent(),
                rowIdSubquery.args,
            )
        }
    }

    private fun toSnapshotIdsSql(
        filters: List<Filter>,
        hasher: TagNameValueHasher,
    ): QuerySpec {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher)
        return if (rowIdSubqueries == null) {
            QuerySpec("SELECT id, created_at FROM event_headers")
        } else {
            QuerySpec(
                """
                SELECT DISTINCT event_headers.id, event_headers.created_at FROM event_headers
                INNER JOIN (
                    ${rowIdSubqueries.sql}
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                """.trimIndent(),
                rowIdSubqueries.args,
            )
        }
    }

    private fun makeSimpleIdsQuery(
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
                ids?.let { equalsOrIn("id", it) }
                kinds?.let { equalsOrIn("kind", it) }
                authors?.let { equalsOrIn("pubkey", it) }
                dTags?.let { equalsOrIn("d_tag", it) }
                since?.let { greaterThanOrEquals("created_at", it) }
                until?.let { lessThanOrEquals("created_at", it) }
                if (dTags != null && kinds != null) {
                    if (kinds.all { it.isAddressable() }) {
                        raw("(kind >= 30000 AND kind < 40000)")
                    }
                }
            }

        val sql =
            buildString {
                append("SELECT id, created_at FROM event_headers")
                if (clause.conditions.isNotEmpty()) {
                    append("\nWHERE ")
                    append(clause.conditions)
                }
                // Negentropy honors filter `limit` like REQ does
                // (matches strfry's NostrFilterGroup behaviour).
                // ORDER BY is required for LIMIT to be meaningful.
                if (limit != null) {
                    append("\nORDER BY created_at DESC")
                    if (indexStrategy.useAndIndexIdOnOrderBy) {
                        append(", id ASC")
                    }
                    append("\nLIMIT ")
                    append(limit)
                }
            }

        return QuerySpec(sql, clause.args)
    }

    private fun makeSimpleIdsSearch(
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
                ids?.let { equalsOrIn("event_headers.id", it) }
                match(fts.tableName, search)
                kinds?.let { equalsOrIn("event_headers.kind", it) }
                authors?.let { equalsOrIn("event_headers.pubkey", it) }
                dTags?.let { equalsOrIn("event_headers.d_tag", it) }
                since?.let { greaterThanOrEquals("event_headers.created_at", it) }
                until?.let { lessThanOrEquals("event_headers.created_at", it) }
                if (dTags != null && kinds != null) {
                    if (kinds.all { it.isAddressable() }) {
                        raw("(event_headers.kind >= 30000 AND kind < 40000)")
                    }
                }
            }

        val sql =
            buildString {
                append("SELECT event_headers.id, event_headers.created_at FROM event_headers")
                append("\nINNER JOIN ${fts.tableName} ON event_headers.row_id = ${fts.tableName}.${fts.eventHeaderRowIdName}")
                if (clause.conditions.isNotEmpty()) {
                    append("\nWHERE ${clause.conditions}")
                }
                if (limit != null) {
                    append("\nORDER BY event_headers.created_at DESC")
                    if (indexStrategy.useAndIndexIdOnOrderBy) {
                        append(", event_headers.id ASC")
                    }
                    append("\nLIMIT ")
                    append(limit)
                }
            }

        return QuerySpec(sql, clause.args)
    }

    private fun SQLiteConnection.runIdAndTimeQuery(query: QuerySpec): List<IdAndTime> =
        prepare(query.sql).use { stmt ->
            query.args.forEachIndexed { index, arg ->
                stmt.bindText(index + 1, arg)
            }
            val results = ArrayList<IdAndTime>()
            while (stmt.step()) {
                results.add(IdAndTime(stmt.getLong(1), stmt.getText(0)))
            }
            results
        }

    /**
     * True when full-text search is turned off ([FullTextSearchModule.enabled]
     * is `false`) and the filter carries a non-empty `search` term. Such a
     * filter can never be satisfied — there is no `event_fts` table to
     * MATCH — so callers short-circuit to a "matches nothing" query. An
     * empty-string search imposes no constraint and is left to the normal
     * (non-search) query path, matching the FTS-enabled behaviour.
     */
    private fun searchDisabledMatchesNothing(search: String?): Boolean = !fts.enabled && search != null && search.isNotEmpty()

    private fun makeMatchesNothingQuery() = "SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers WHERE 0"

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

    private fun <T : Event> SQLiteConnection.runQuery(query: QuerySpec): List<T> =
        prepare(query.sql).use { stmt ->
            query.args.forEachIndexed { index, arg ->
                stmt.bindText(index + 1, arg)
            }
            val results = ArrayList<T>()
            while (stmt.step()) {
                results.add(stmt.toEvent())
            }
            results
        }

    private fun SQLiteConnection.runRawQuery(query: QuerySpec): List<RawEvent> =
        prepare(query.sql).use { stmt ->
            query.args.forEachIndexed { index, arg ->
                stmt.bindText(index + 1, arg)
            }
            val results = ArrayList<RawEvent>()
            while (stmt.step()) {
                results.add(stmt.toRawEvent())
            }
            results
        }

    private inline fun <T : Event> SQLiteConnection.runQuery(
        query: QuerySpec,
        onEach: (T) -> Unit,
    ) = prepare(query.sql).use { stmt ->
        query.args.forEachIndexed { index, arg ->
            stmt.bindText(index + 1, arg)
        }
        while (stmt.step()) {
            onEach(stmt.toEvent())
        }
    }

    private inline fun SQLiteConnection.runRawQuery(
        query: QuerySpec,
        onEach: (RawEvent) -> Unit,
    ) = prepare(query.sql).use { stmt ->
        query.args.forEachIndexed { index, arg ->
            stmt.bindText(index + 1, arg)
        }
        while (stmt.step()) {
            onEach(stmt.toRawEvent())
        }
    }

    private fun <T : Event> SQLiteStatement.toEvent(): T =
        EventFactory.create<T>(
            getText(0),
            getText(1),
            getLong(2),
            getInt(3),
            OptimizedJsonMapper.fromJsonToTagArray(getText(4)),
            getText(5),
            getText(6),
        )

    private fun SQLiteStatement.toRawEvent() =
        RawEvent(
            getText(0),
            getText(1),
            getLong(2),
            getInt(3),
            getText(4),
            getText(5),
            getText(6),
        )

    // --------------
    // Counts
    // -------------
    fun count(
        filter: Filter,
        db: SQLiteConnection,
    ): Int {
        val newFilter = filter.toFilterWithDTags()

        // With FTS off a search term can never be satisfied — count is 0.
        if (searchDisabledMatchesNothing(newFilter.search)) return 0

        if (newFilter.isSimpleQuery()) {
            val sql =
                makeSimpleQuery(
                    project = false,
                    ids = newFilter.ids,
                    authors = newFilter.authors,
                    kinds = newFilter.kinds,
                    dTags = newFilter.dTags,
                    since = newFilter.since,
                    until = newFilter.until,
                    limit = newFilter.limit,
                )
            return db.countIn(sql.sql, sql.args)
        }

        val rowIdSubQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdSubQuery == null) {
            db.countEverything()
        } else {
            db.countIn(rowIdSubQuery.sql, rowIdSubQuery.args)
        }
    }

    fun count(
        filters: List<Filter>,
        db: SQLiteConnection,
    ): Int {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db)) ?: return db.countEverything()

        return db.countIn(rowIdSubqueries.sql, rowIdSubqueries.args)
    }

    // -----------------------------------------------------------------
    // Anti-join projections
    //
    // Set-difference over authors — "who is missing an event of kind K"
    // — which the positive-only nostr Filter grammar can't express, so
    // it lives here as a dedicated SELECT rather than going through the
    // filter → SQL path.
    // -----------------------------------------------------------------

    /**
     * Distinct identity authors with at least one stored event that have NO
     * stored event of [kind], as an `EXCEPT` of two sets over `event_headers`:
     * all authors, minus the authors that have a [kind]. Both sides are
     * answered index-only off `query_by_kind_pubkey_created`
     * (kind, pubkey, …) — which is created unconditionally, so this does not
     * depend on the optional pubkey-alone index — and `EXCEPT` diffs them
     * through one temp b-tree. That is ~3× faster than a
     * `DISTINCT … NOT EXISTS` correlated scan, which pays one index seek per
     * distinct author; the gap widens with author cardinality. Order is
     * unspecified (`EXCEPT` returns pubkey-sorted, which callers must not rely
     * on).
     *
     * GiftWraps (kind 1059) are excluded from the "authors" set: their
     * `pubkey` is a random one-time key (the real recipient lives only in
     * `pubkey_owner_hash`), so counting them would return an unbounded set of
     * ephemeral keys that can never own a [kind] event.
     */
    fun authorsMissingKind(
        kind: Int,
        db: SQLiteConnection,
    ): List<HexKey> {
        val sql =
            """
            SELECT DISTINCT pubkey FROM event_headers WHERE kind <> ${GiftWrapEvent.KIND}
            EXCEPT
            SELECT pubkey FROM event_headers WHERE kind = ?
            """.trimIndent()
        return db.prepare(sql).use { stmt ->
            stmt.bindLong(1, kind.toLong())
            val out = ArrayList<HexKey>()
            while (stmt.step()) {
                out.add(stmt.getText(0))
            }
            out
        }
    }

    private fun SQLiteConnection.countEverything() = runCount("SELECT count(*) as count FROM event_headers")

    private fun SQLiteConnection.countIn(
        rowIdQuery: String,
        args: List<String>,
    ) = runCount("SELECT COUNT(*) as count FROM ($rowIdQuery)", args)

    private fun SQLiteConnection.runCount(
        sql: String,
        args: List<String> = emptyList(),
    ): Int =
        prepare(sql).use { stmt ->
            args.forEachIndexed { index, arg ->
                stmt.bindText(index + 1, arg)
            }
            stmt.step()
            stmt.getInt(0)
        }

    // --------------
    // Deletes
    // -------------

    /**
     * Safe-by-default: an empty filter (or a list of only empty filters)
     * deletes nothing and returns 0, so a stray `delete(Filter())` cannot
     * wipe the entire store. This is asymmetric with `query(Filter())`,
     * which intentionally returns every event.
     */
    fun delete(
        filter: Filter,
        db: SQLiteConnection,
    ): Int {
        val rowIdQuery = prepareRowIDSubQueries(filter, hasher(db))

        return if (rowIdQuery == null) {
            0
        } else {
            db.runDelete(rowIdQuery.sql, rowIdQuery.args)
        }
    }

    /** See [delete] for the empty-filter contract. */
    fun delete(
        filters: List<Filter>,
        db: SQLiteConnection,
    ): Int {
        val rowIdSubqueries = unionSubqueriesIfNeeded(filters, hasher(db)) ?: return 0

        return db.runDelete(rowIdSubqueries.sql, rowIdSubqueries.args)
    }

    private fun SQLiteConnection.runDelete(
        sql: String,
        args: List<String> = emptyList(),
    ): Int {
        prepare("DELETE FROM event_headers WHERE row_id IN ($sql)").use { stmt ->
            args.forEachIndexed { index, arg ->
                stmt.bindText(index + 1, arg)
            }
            stmt.step()
        }
        return changes()
    }

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

        // With FTS off there is no event_fts table to MATCH/JOIN against.
        // A non-empty search can never match, so this filter contributes
        // no row ids (returns an empty branch to any UNION/COUNT/DELETE);
        // an empty search imposes no constraint and is simply dropped below.
        if (searchDisabledMatchesNothing(filter.search)) {
            return QuerySpec("SELECT event_headers.row_id as row_id FROM event_headers WHERE 0")
        }

        val mustJoinSearch = filter.search != null && fts.enabled

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
                filter.ids?.let {
                    equalsOrIn("event_headers.id", it)
                }

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
                    filter.kinds?.let {
                        equalsOrIn("event_tags.kind", it)
                    }
                    filter.authors?.let {
                        equalsOrIn("event_tags.pubkey_hash", it.map { hasher.hash(it) })
                    }

                    filter.since?.let {
                        greaterThanOrEquals("event_tags.created_at", it)
                    }
                    filter.until?.let {
                        lessThanOrEquals("event_tags.created_at", it)
                    }

                    // there are indexes for these, starting with tags.
                    filter.tags?.forEach { (tagName, tagValues) ->
                        if (tagName == "d") {
                            equalsOrIn("event_headers.d_tag", tagValues)
                        }
                    }
                } else {
                    filter.kinds?.let {
                        equalsOrIn("event_headers.kind", it)
                    }
                    filter.authors?.let {
                        equalsOrIn("event_headers.pubkey", it)
                    }

                    // there are indexes for these, starting with tags.
                    filter.tags?.forEach { (tagName, tagValues) ->
                        if (tagName == "d") {
                            equalsOrIn("event_headers.d_tag", tagValues)
                        }
                    }

                    filter.since?.let {
                        greaterThanOrEquals("event_headers.created_at", it)
                    }
                    filter.until?.let {
                        lessThanOrEquals("event_headers.created_at", it)
                    }

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
                ids?.let {
                    equalsOrIn("event_headers.id", it)
                }

                match(fts.tableName, search)

                kinds?.let {
                    equalsOrIn("event_headers.kind", it)
                }
                authors?.let {
                    equalsOrIn("event_headers.pubkey", it)
                }

                // there are indexes for these, starting with tags.
                dTags?.let {
                    equalsOrIn("event_headers.d_tag", it)
                }

                since?.let {
                    greaterThanOrEquals("event_headers.created_at", it)
                }
                until?.let {
                    lessThanOrEquals("event_headers.created_at", it)
                }

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

        return QuerySpec(sql, clause.args)
    }

    private fun makeSimpleQuery(
        project: Boolean,
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
                ids?.let {
                    equalsOrIn("id", it)
                }

                kinds?.let {
                    equalsOrIn("kind", it)
                }
                authors?.let {
                    equalsOrIn("pubkey", it)
                }

                // there are indexes for these, starting with tags.
                dTags?.let {
                    equalsOrIn("d_tag", it)
                }

                since?.let {
                    greaterThanOrEquals("created_at", it)
                }
                until?.let {
                    lessThanOrEquals("created_at", it)
                }

                // if this is a dTag filter, it is likely that all kinds are addressables
                // and so force the use of the addressable index
                if (dTags != null && kinds != null) {
                    if (kinds.all { it.isAddressable() }) {
                        // matches unique index kind >= 30000 AND kind < 40000
                        raw("(kind >= 30000 AND kind < 40000)")
                    }
                }
            }

        // A multi-author query (`pubkey IN (…)`) with no limit, combined with
        // `ORDER BY created_at DESC`, mis-costs in SQLite: it satisfies the
        // order for free off `query_by_kind_created` (kind, created_at) by
        // scanning an *entire* kind, rather than doing N seeks on the
        // selective `query_by_kind_pubkey_created` (kind, pubkey, …) and
        // sorting the (small) result — the ~100× `profiles` regression at 1M.
        // Pin the selective index for exactly that shape:
        //  - single author (`pubkey = ?`) is costed correctly and already
        //    seeks — no pin needed;
        //  - *with* a limit, the `created_at` scan + early LIMIT is the better
        //    plan (the 150-author home feed), so leave it to the planner;
        //  - d-tag/addressable filters have their own index.
        // Order is preserved (the ORDER BY stays); this only redirects the
        // index. See quartz/plans/2026-07-04-profiles-query-plan.md.
        val pinKindPubkeyIndex =
            project &&
                kinds != null &&
                authors != null &&
                authors.size > 1 &&
                ids == null &&
                dTags == null &&
                limit == null

        val sql =
            buildString {
                if (project) {
                    append("SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers")
                } else {
                    append("SELECT row_id FROM event_headers")
                }
                if (pinKindPubkeyIndex) {
                    append(" INDEXED BY query_by_kind_pubkey_created")
                }
                if (clause.conditions.isNotEmpty()) {
                    append("\nWHERE ")
                    append(clause.conditions)
                }
                if (project) {
                    append("\nORDER BY created_at DESC")
                    if (indexStrategy.useAndIndexIdOnOrderBy) {
                        append(", id ASC")
                    }
                }
                if (limit != null) {
                    append("\nLIMIT ")
                    append(limit)
                }
            }

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
            search != null &&
                search.isNotEmpty() &&
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
