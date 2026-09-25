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
 * The fixed surface of the Nostr SQL profile: the virtual tables every
 * relay exposes and the functions a query may call. Semantics are
 * SQLite's — a relay on another engine embeds SQLite rather than
 * translating — so the only thing the profile has to pin down is *what
 * is reachable*, not how each operator behaves.
 */
object SqlProfile {
    const val EVENTS = "events"
    const val TAGS = "tags"

    /** Column order is part of the spec: it is what `SELECT *` returns. */
    val EVENTS_COLUMNS = listOf("id", "pubkey", "created_at", "kind", "content", "sig")

    /**
     * One row per tag. `value`, `v2`..`v4` are `tag[1]`..`tag[4]` (NULL
     * when absent); `rest` is a JSON array of `tag[5..]` (NULL when none).
     * `created_at` / `kind` / `pubkey` are copied from the parent event so
     * tag-only aggregates don't need a join.
     */
    val TAGS_COLUMNS = listOf("event_id", "idx", "name", "value", "v2", "v3", "v4", "rest", "created_at", "kind", "pubkey")

    val TABLES = mapOf(EVENTS to EVENTS_COLUMNS, TAGS to TAGS_COLUMNS)

    val CAST_TYPES = setOf("INTEGER", "REAL", "TEXT", "NUMERIC")

    val AGGREGATE_FUNCTIONS = setOf("count", "sum", "total", "avg", "min", "max", "group_concat")

    val WINDOW_FUNCTIONS =
        setOf(
            "row_number",
            "rank",
            "dense_rank",
            "percent_rank",
            "cume_dist",
            "ntile",
            "lag",
            "lead",
            "first_value",
            "last_value",
            "nth_value",
        )

    /**
     * Deterministic scalar functions with output bounded by their input.
     * Deliberately absent: anything touching the file system or extensions
     * (`load_extension`), engine internals (`sqlite_*`), unbounded
     * allocation (`randomblob`, `zeroblob`, `printf`/`format` with a
     * width), randomness (`random`) and JSON (tags are already a table).
     */
    val SCALAR_FUNCTIONS =
        setOf(
            "abs",
            "coalesce",
            "ifnull",
            "iif",
            "nullif",
            "instr",
            "length",
            "lower",
            "upper",
            "ltrim",
            "rtrim",
            "trim",
            "replace",
            "substr",
            "substring",
            "round",
            "typeof",
            "hex",
            "unicode",
            "date",
            "time",
            "datetime",
            "julianday",
            "unixepoch",
            "strftime",
        )

    val FUNCTIONS = AGGREGATE_FUNCTIONS + WINDOW_FUNCTIONS + SCALAR_FUNCTIONS
}
