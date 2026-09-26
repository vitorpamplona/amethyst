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
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.utils.EventFactory

/**
 * NIP-01 filters spelled in NQL (NIP-FF), so a client can read a relay's raw
 * store with the exact semantics of `REQ` minus its per-relay caps, ranking
 * and live tail.
 *
 * Every query is shaped so each source carries its own selective conditions:
 * a filter with tags is answered off the `tags` source first (name, values and
 * the filter's kinds/authors/time mirrored onto it), never as
 * `events WHERE id IN (subquery)`, which would make a store scan every event
 * of the kind to join a handful of tag rows.
 *
 * Results can be capped by the relay, so listings are ordered and paged: [ids]
 * newest first, ties by id, continuing after a given row; [events] by id;
 * [tags] by event and position. Events come back in steps, [ids], then [events] and [tags], because
 * NQL keeps tags in their own source.
 */
object FilterSql {
    class Query(
        val nql: String,
        val params: List<Any?>,
    )

    /** Ids per [events] / [tags] query: enough to amortise a round trip, few enough for one statement. */
    const val HYDRATE_CHUNK = 200

    /** True when [filter] can't match anything and no query need be sent. */
    fun matchesNothing(filter: Filter): Boolean =
        filter.limit?.let { it <= 0 } == true ||
            filter.ids?.isEmpty() == true ||
            filter.authors?.isEmpty() == true ||
            filter.kinds?.isEmpty() == true ||
            filter.tags?.values?.any { it.isEmpty() } == true ||
            (filter.since != null && filter.until != null && filter.since > filter.until)

    /**
     * `(id, created_at)` of the events [filter] matches, newest first, ties by
     * id, starting after [after] (a row a previous page ended with), at most
     * [limit]. `search` has no NQL spelling and is refused.
     */
    fun ids(
        filter: Filter,
        after: IdAndTime? = null,
        limit: Int? = filter.limit,
    ): Query {
        val b = Builder()
        val sql =
            if (hasTags(filter)) {
                // Answered off one tag condition; the rest ride as subqueries that carry the same mirrored bounds.
                val (first, restTags, restAll) = splitFirstTag(filter)
                val where = listOf(b.tagRow("t", first.first, first.second, filter)) + b.otherTags(filter, restTags, restAll, "t.event_id") + b.after("t", "event_id", after)
                "SELECT DISTINCT t.event_id AS id, t.created_at AS created_at FROM tags AS t WHERE " + where.joinToString(" AND ") +
                    " ORDER BY created_at DESC, id" + limit(limit)
            } else {
                val where = b.common("e", "id", filter) + b.after("e", "id", after)
                "SELECT e.id AS id, e.created_at AS created_at FROM events AS e" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
                    " ORDER BY created_at DESC, id" + limit(limit)
            }
        return Query(sql, b.params)
    }

    /** How many events [filter] matches. Like NIP-45, `limit` is ignored. */
    fun count(filter: Filter): Query {
        val b = Builder()
        val sql =
            if (hasTags(filter)) {
                val (first, restTags, restAll) = splitFirstTag(filter)
                val where = listOf(b.tagRow("t", first.first, first.second, filter)) + b.otherTags(filter, restTags, restAll, "t.event_id")
                "SELECT count(DISTINCT t.event_id) AS n FROM tags AS t WHERE " + where.joinToString(" AND ")
            } else {
                val where = b.common("e", "id", filter)
                "SELECT count(*) AS n FROM events AS e" + if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")
            }
        return Query(sql, b.params)
    }

    /**
     * The events' own fields for up to [HYDRATE_CHUNK] [ids], by id, starting
     * after [after] (a previous page's last id), for [Collector.addEvent].
     */
    fun events(
        ids: Collection<String>,
        after: String? = null,
    ): Query {
        require(ids.size <= HYDRATE_CHUNK) { "at most $HYDRATE_CHUNK ids per query" }
        val params = ArrayList<Any?>(ids)
        val page =
            if (after == null) {
                ""
            } else {
                params.add(after)
                " AND id > ?"
            }
        return Query("SELECT id, pubkey, created_at, kind, content, sig FROM events WHERE id IN (${marks(ids)})$page ORDER BY id", params)
    }

    /**
     * The tag rows of up to [HYDRATE_CHUNK] [ids], by event and position,
     * starting after [after] (`event_id`, `idx` of a previous page's last row),
     * for [Collector.addTag].
     */
    fun tags(
        ids: Collection<String>,
        after: Pair<String, Long>? = null,
    ): Query {
        require(ids.size <= HYDRATE_CHUNK) { "at most $HYDRATE_CHUNK ids per query" }
        val params = ArrayList<Any?>(ids)
        val page =
            if (after == null) {
                ""
            } else {
                params.addAll(listOf(after.first, after.first, after.second))
                " AND (event_id > ? OR (event_id = ? AND idx > ?))"
            }
        return Query("SELECT event_id, idx, t0, t1, t2, t3, t4 FROM tags WHERE event_id IN (${marks(ids)})$page ORDER BY event_id, idx", params)
    }

    /**
     * Rebuilds events from [events] and [tags] rows. NQL shows a tag's first
     * five elements only, so an event whose rebuilt id doesn't match (a longer
     * or empty tag) is reported in [Result.incomplete] for the caller to fetch
     * whole another way.
     */
    class Collector {
        class Result(
            val events: List<Event>,
            val incomplete: List<String>,
        )

        private val heads = LinkedHashMap<String, List<Any?>>()
        private val tags = HashMap<String, MutableList<Pair<Long, Array<String>>>>()

        fun addEvent(row: List<Any?>) {
            heads[row[0] as String] = row
        }

        fun addTag(row: List<Any?>) {
            val tag = ArrayList<String>(5)
            for (i in 2..6) tag.add(row[i] as? String ?: break)
            tags.getOrPut(row[0] as String) { ArrayList() }.add((row[1] as Number).toLong() to tag.toTypedArray())
        }

        fun finish(): Result {
            val out = ArrayList<Event>()
            val incomplete = ArrayList<String>()
            for ((id, h) in heads) {
                val pubKey = h[1] as String
                val createdAt = (h[2] as Number).toLong()
                val kind = (h[3] as Number).toInt()
                val content = h[4] as String
                val eventTags =
                    tags[id]
                        .orEmpty()
                        .sortedBy { it.first }
                        .map { it.second }
                        .toTypedArray()
                if (EventHasher.hashId(pubKey, createdAt, kind, eventTags, content) == id) {
                    out.add(EventFactory.create(id, pubKey, createdAt, kind, eventTags, content, h[5] as String))
                } else {
                    incomplete.add(id)
                }
            }
            return Result(out, incomplete)
        }
    }

    private fun marks(values: Collection<*>) = values.joinToString(",") { "?" }

    private fun hasTags(filter: Filter) = !filter.tags.isNullOrEmpty() || !filter.tagsAll.isNullOrEmpty()

    private fun limit(limit: Int?) = limit?.let { " LIMIT ${maxOf(it, 0)}" } ?: ""

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

        /** The filter's own columns on alias [a]; `id`/`event_id` picked by source. */
        fun common(
            a: String,
            idColumn: String,
            filter: Filter,
        ): List<String> {
            if (filter.search != null) throw SqlException.unsupported("a filter's search has no NQL spelling")
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

        /** Rows after [after] in newest-first, then-id order. */
        fun after(
            a: String,
            idColumn: String,
            after: IdAndTime?,
        ): List<String> {
            if (after == null) return emptyList()
            params.addAll(listOf(after.createdAt, after.createdAt, after.id))
            return listOf("($a.created_at < ? OR ($a.created_at = ? AND $a.$idColumn > ?))")
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
                c += "$outer IN (SELECT x$i.event_id FROM tags AS x$i WHERE ${tagRow("x$i", name, values, filter)})"
            }
            return c
        }
    }
}
