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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * NIP-01 filters spelled in the SQL profile, so a client can read a relay's
 * raw store over `SQL` with the exact semantics of `REQ` minus its per-relay
 * caps, ranking and live tail.
 *
 * Every query is shaped so each table reference carries its own selective
 * conditions: a filter with tags is answered off the `tags` table first
 * (name, values and the filter's kinds/authors/time mirrored onto it), never
 * as `events WHERE id IN (subquery)`, which would make a pushdown store scan
 * every event of the kind to join a handful of tag rows.
 *
 * Events come back in two steps, [ids] then [hydrate], because the profile
 * keeps tags in their own table.
 */
object FilterSql {
    class Query(
        val sql: String,
        val params: List<Any?>,
    )

    /** Ids per [hydrate] query: enough to amortise a round trip, few enough for one bound statement. */
    const val HYDRATE_CHUNK = 500

    /** True when [filter] can't match anything and no query need be sent. */
    fun matchesNothing(filter: Filter): Boolean =
        filter.limit?.let { it <= 0 } == true ||
            filter.ids?.isEmpty() == true ||
            filter.authors?.isEmpty() == true ||
            filter.kinds?.isEmpty() == true ||
            filter.tags?.values?.any { it.isEmpty() } == true ||
            (filter.since != null && filter.until != null && filter.since > filter.until)

    /**
     * `(id, created_at)` of every event [filter] matches, newest first (ties by
     * id), honouring its `limit`. `search` has no SQL spelling and is refused.
     */
    fun ids(filter: Filter): Query {
        val b = Builder()
        val sql =
            if (hasTags(filter)) {
                // Answered off one tag condition; the rest ride as subqueries that carry the same mirrored bounds.
                val (first, restTags, restAll) = splitFirstTag(filter)
                val where = b.tagRow("t", first.first, first.second, filter)
                val extra = b.otherTags(filter, restTags, restAll, "t.event_id")
                "SELECT DISTINCT t.event_id AS id, t.created_at AS created_at FROM tags t WHERE " + (listOf(where) + extra).joinToString(" AND ") +
                    " ORDER BY t.created_at DESC, t.event_id" + limit(filter)
            } else {
                "SELECT e.id AS id, e.created_at AS created_at FROM events e" + b.eventWhere(filter) +
                    " ORDER BY e.created_at DESC, e.id" + limit(filter)
            }
        return Query(sql, b.params)
    }

    /** How many events [filter] matches. Like NIP-45, `limit` is ignored. */
    fun count(filter: Filter): Query {
        val b = Builder()
        val sql =
            if (hasTags(filter)) {
                val (first, restTags, restAll) = splitFirstTag(filter)
                val where = b.tagRow("t", first.first, first.second, filter)
                val extra = b.otherTags(filter, restTags, restAll, "t.event_id")
                "SELECT count(DISTINCT t.event_id) FROM tags t WHERE " + (listOf(where) + extra).joinToString(" AND ")
            } else {
                "SELECT count(*) FROM events e" + b.eventWhere(filter)
            }
        return Query(sql, b.params)
    }

    /**
     * Full events for up to [HYDRATE_CHUNK] [ids], one row per tag (or one
     * tagless row), for [collect]. Both references carry the id list so a
     * pushdown store answers each with an id lookup.
     */
    fun hydrate(ids: Collection<String>): Query {
        require(ids.size <= HYDRATE_CHUNK) { "at most $HYDRATE_CHUNK ids per hydrate" }
        val marks = ids.joinToString(",") { "?" }
        val sql =
            "SELECT e.id, e.pubkey, e.created_at, e.kind, e.content, e.sig, t.idx, t.t0, t.t1, t.t2, t.t3, t.t4, t.rest " +
                "FROM events e LEFT JOIN tags t ON t.event_id = e.id AND t.event_id IN ($marks) " +
                "WHERE e.id IN ($marks) ORDER BY e.created_at DESC, e.id, t.idx"
        return Query(sql, ids.toList() + ids.toList())
    }

    /** Rebuilds [hydrate]'s rows into events, in row order. */
    class Collector {
        private val out = ArrayList<Event>()
        private var id: String? = null
        private var head: List<Any?>? = null
        private val tags = ArrayList<Array<String>>()

        fun add(row: List<Any?>) {
            val rowId = row[0] as String
            if (rowId != id) {
                flush()
                id = rowId
                head = row
            }
            if (row[7] != null) tags.add(tag(row))
        }

        fun finish(): List<Event> {
            flush()
            return out
        }

        private fun flush() {
            val h = head ?: return
            out.add(
                EventFactory.create(
                    id = h[0] as String,
                    pubKey = h[1] as String,
                    createdAt = (h[2] as Number).toLong(),
                    kind = (h[3] as Number).toInt(),
                    tags = tags.toTypedArray(),
                    content = h[4] as String,
                    sig = h[5] as String,
                ),
            )
            tags.clear()
            head = null
        }

        /** `[t0, t1, t2, t3, t4, ...rest]`, stopping at the first absent position. */
        private fun tag(row: List<Any?>): Array<String> {
            val t = ArrayList<String>(5)
            t.add(row[7] as String)
            for (i in 8..11) {
                val v = row[i] ?: return t.toTypedArray()
                t.add(v.toString())
            }
            (row[12] as? String)?.let { rest ->
                (Json.parseToJsonElement(rest) as JsonArray).forEach { t.add((it as JsonPrimitive).content) }
            }
            return t.toTypedArray()
        }
    }

    private fun hasTags(filter: Filter) = !filter.tags.isNullOrEmpty() || !filter.tagsAll.isNullOrEmpty()

    private fun limit(filter: Filter) = filter.limit?.let { " LIMIT $it" } ?: ""

    /** The tag condition the query is driven by, and what's left for subqueries. */
    private fun splitFirstTag(filter: Filter): Triple<Pair<String, List<String>>, Map<String, List<String>>, List<Pair<String, String>>> {
        val tags = filter.tags.orEmpty()
        val all = filter.tagsAll.orEmpty().flatMap { (k, vs) -> vs.map { k to it } }
        return if (tags.isNotEmpty()) {
            val first = tags.entries.first()
            Triple(first.key to first.value, tags - first.key, all)
        } else {
            Triple(all.first().first to listOf(all.first().second), emptyMap(), all.drop(1))
        }
    }

    private class Builder {
        val params = ArrayList<Any?>()

        fun marks(values: Collection<Any?>): String {
            params.addAll(values)
            return values.joinToString(",") { "?" }
        }

        /** The filter's own columns on alias [a]; `id`/`event_id` picked by table. */
        fun common(
            a: String,
            idColumn: String,
            filter: Filter,
        ): List<String> {
            if (filter.search != null) throw SqlException(SqlException.UNSUPPORTED, "a filter's search has no SQL spelling")
            val c = ArrayList<String>()
            filter.ids?.let { c += "$a.$idColumn IN (${marks(it)})" }
            filter.authors?.let { c += "$a.pubkey IN (${marks(it)})" }
            filter.kinds?.let { c += "$a.kind IN (${marks(it.map { k -> k.toLong() })})" }
            filter.since?.let {
                params += it
                c += "$a.created_at >= ?"
            }
            filter.until?.let {
                params += it
                c += "$a.created_at <= ?"
            }
            return c
        }

        fun eventWhere(filter: Filter): String {
            val c = common("e", "id", filter)
            return if (c.isEmpty()) "" else " WHERE " + c.joinToString(" AND ")
        }

        fun tagRow(
            a: String,
            name: String,
            values: List<String>,
            filter: Filter,
        ): String {
            params += name
            val head = "$a.t0 = ? AND $a.t1 IN (${marks(values)})"
            return (listOf(head) + common(a, "event_id", filter)).joinToString(" AND ")
        }

        fun otherTags(
            filter: Filter,
            tags: Map<String, List<String>>,
            all: List<Pair<String, String>>,
            outer: String,
        ): List<String> {
            val c = ArrayList<String>()
            val subqueries = tags.map { (name, values) -> name to values } + all.map { (name, value) -> name to listOf(value) }
            subqueries.forEachIndexed { i, (name, values) ->
                c += "$outer IN (SELECT x$i.event_id FROM tags x$i WHERE ${tagRow("x$i", name, values, filter)})"
            }
            return c
        }
    }
}
