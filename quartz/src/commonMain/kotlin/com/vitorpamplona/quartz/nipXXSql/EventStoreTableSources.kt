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

import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip01Core.tags.isIndexableTagName

/**
 * The profile's `events` / `tags` tables over Quartz's SQLite event store
 * (`event_headers`), with no schema change.
 *
 * `tags` is derived from the stored tag JSON with `json_each`. On its own
 * that scans every event; [forStore] adds a pushdown so a query that pins
 * `t0` and `t1` only expands the events `event_tags` says have that tag.
 */
object EventStoreTableSources {
    /** `events` over a table laid out like `event_headers`. */
    fun eventsSql(table: String) = "SELECT h.id, h.pubkey, h.created_at, h.kind, h.content, h.sig FROM $table h"

    /**
     * `tags` over a table laid out like `event_headers`: one row per tag,
     * expanded from the stored tag JSON. `json_remove` applies its paths
     * left to right, so removing `$[0]` five times drops tag[0..4] and leaves
     * the tail.
     */
    fun tagsSql(table: String) =
        """
        SELECT h.id AS event_id, j.key AS idx,
               json_extract(j.value, '$[0]') AS t0,
               json_extract(j.value, '$[1]') AS t1,
               json_extract(j.value, '$[2]') AS t2,
               json_extract(j.value, '$[3]') AS t3,
               json_extract(j.value, '$[4]') AS t4,
               CASE WHEN json_array_length(j.value) > 5
                    THEN json_remove(j.value, '$[0]', '$[0]', '$[0]', '$[0]', '$[0]') END AS rest,
               h.created_at, h.kind, h.pubkey
        FROM $table h, json_each(h.tags) j
        """.trimIndent()

    private val EVENTS = eventsSql("event_headers")
    private val TAGS = tagsSql("event_headers")

    /** Both tables, no pushdown: every `tags` query expands every event. */
    val sources = SqlTableSources(TableSource(EVENTS), TableSource(TAGS))

    /**
     * [sources] plus the `event_tags` pushdown. `event_tags` holds
     * `hasher.hash(tag[0], tag[1])` for every tag [DefaultIndexingStrategy]
     * indexes, which is every single-letter tag with a value, whatever the
     * kind. So for a single-letter name, the events with a matching hash are
     * a superset of the events holding that tag (a collision only adds
     * candidates, which the query's own `t0` / `t1` predicates drop).
     * Any other strategy may skip tags, so it gets no pushdown.
     */
    fun forStore(
        hasher: TagNameValueHasher,
        indexStrategy: IndexingStrategy,
    ): SqlTableSources {
        if (indexStrategy !is DefaultIndexingStrategy) return sources
        return SqlTableSources(
            events = TableSource(EVENTS),
            tags = TableSource(TAGS),
            tagsMatching = { c ->
                if (!isIndexableTagName(c.name)) {
                    null
                } else {
                    TableSource(
                        TAGS + "\nWHERE h.row_id IN (SELECT event_header_row_id FROM event_tags WHERE tag_hash IN (" +
                            c.values.joinToString(", ") { "?" } + "))",
                        c.values.map { hasher.hash(c.name, it) },
                    )
                }
            },
        )
    }
}
