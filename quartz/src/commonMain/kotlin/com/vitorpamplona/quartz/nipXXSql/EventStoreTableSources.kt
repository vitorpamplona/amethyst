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

/**
 * The profile's `events` / `tags` tables over Quartz's SQLite event store
 * (`event_headers`), with no schema change.
 *
 * `tags` is derived from the stored tag JSON with `json_each`, so a query
 * that filters tags scans every event: correct, not fast. A relay that
 * wants indexed tag queries adds a real text tag table and points
 * [SqlTableSources.tags] at it; the client-facing profile doesn't change.
 *
 * [hiddenKinds] drops whole kinds from both tables. It is the simplest
 * form of the per-session access control that REQ gets from its policy
 * (e.g. hide kind 1059 gift wraps from unauthenticated sessions); a relay
 * with finer rules writes its own [TableSource]s.
 */
object EventStoreTableSources {
    fun build(hiddenKinds: Set<Int> = emptySet()): SqlTableSources {
        val kindFilter =
            if (hiddenKinds.isEmpty()) {
                ""
            } else {
                " WHERE h.kind NOT IN (" + hiddenKinds.sorted().joinToString(",") + ")"
            }

        val events = TableSource("SELECT h.id, h.pubkey, h.created_at, h.kind, h.content, h.sig FROM event_headers h$kindFilter")

        // json_remove applies its paths left to right, so removing `$[0]`
        // five times drops tag[0..4] and leaves the tail.
        val tags =
            TableSource(
                """
                SELECT h.id AS event_id, j.key AS idx,
                       json_extract(j.value, '$[0]') AS name,
                       json_extract(j.value, '$[1]') AS value,
                       json_extract(j.value, '$[2]') AS v2,
                       json_extract(j.value, '$[3]') AS v3,
                       json_extract(j.value, '$[4]') AS v4,
                       CASE WHEN json_array_length(j.value) > 5
                            THEN json_remove(j.value, '$[0]', '$[0]', '$[0]', '$[0]', '$[0]') END AS rest,
                       h.created_at, h.kind, h.pubkey
                FROM event_headers h, json_each(h.tags) j$kindFilter
                """.trimIndent(),
            )

        return SqlTableSources(events, tags)
    }
}
