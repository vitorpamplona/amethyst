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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.isIndexableTagName

/**
 * What one reference to `events` or `tags` in a query needs from the store:
 * the conditions the query puts on that reference's rows, in the terms a
 * Nostr store can answer. Every field is a constraint (null = none).
 *
 * The rows a store returns for a spec must be a **superset** of the rows
 * that satisfy the query's conditions on that reference; the query's own
 * predicates still run afterwards. [exact] says the spec captures every
 * condition on the reference, which is what lets a store answer an
 * aggregate natively without the residual predicates.
 */
class ScanSpec(
    /** [SqlProfile.EVENTS] or [SqlProfile.TAGS]. */
    val table: String,
    /** `events.id` / `tags.event_id`. */
    val ids: Set<String>? = null,
    val authors: Set<String>? = null,
    val kinds: Set<Int>? = null,
    /** Inclusive. */
    val since: Long? = null,
    /** Inclusive. */
    val until: Long? = null,
    /** `tags` only: the row's own `name` (may be set without [tagValues]). */
    val tagName: String? = null,
    /** `tags` only: the row's own `value` (may be set without [tagName]). */
    val tagValues: Set<String>? = null,
    /** `tags` only: `value <> ''` (so also not null), the rows a tag-value index holds. */
    val valueNonEmpty: Boolean = false,
    /** Newest-first cap on events, only set when the whole query is a plain newest-first listing. */
    val limit: Int? = null,
    val exact: Boolean = false,
) {
    /** A set emptied by contradictory conditions (e.g. `kind = 1 AND kind = 2`): nothing matches. */
    val matchesNothing: Boolean
        get() =
            ids?.isEmpty() == true || authors?.isEmpty() == true || kinds?.isEmpty() == true ||
                tagValues?.isEmpty() == true || (since != null && until != null && since > until)

    /** Narrows the store's work: a condition on id, author, kind or an indexable tag. */
    val isSelective: Boolean
        get() = ids != null || authors != null || kinds != null || (tagValues != null && tagName != null && isIndexableTagName(tagName))

    /**
     * The Nostr filter whose events cover this spec: for `events`, the events
     * themselves; for `tags`, the events whose tags hold the matching rows.
     * A tag condition only makes it into the filter for single-letter names,
     * the ones Nostr filters (and store indexes) can express.
     */
    fun toFilter(): Filter =
        Filter(
            ids = ids?.toList(),
            authors = authors?.toList(),
            kinds = kinds?.toList(),
            tags =
                if (tagName != null && tagValues != null && isIndexableTagName(tagName)) {
                    mapOf(tagName to tagValues.toList())
                } else {
                    null
                },
            since = since,
            until = until,
            limit = limit,
        )

    fun withLimit(limit: Int) = ScanSpec(table, ids, authors, kinds, since, until, tagName, tagValues, valueNonEmpty, limit, exact)

    fun withTimeRange(
        since: Long?,
        until: Long?,
    ) = ScanSpec(table, ids, authors, kinds, since, until, tagName, tagValues, valueNonEmpty, null, exact)

    override fun toString() =
        "ScanSpec($table ids=$ids authors=$authors kinds=$kinds since=$since until=$until " +
            "tag=$tagName:$tagValues nonEmpty=$valueNonEmpty limit=$limit exact=$exact)"
}

/**
 * Reads the conditions on one table reference out of the predicates that
 * must hold for its rows (see [SqlCompiler]'s caller for which those are).
 * Only simple comparisons of a column with a constant are understood; any
 * other predicate is left to the query and makes the spec inexact.
 */
internal class ScanAnalyzer(
    private val constant: (Expr) -> Any?,
) {
    fun analyze(
        table: String,
        alias: String,
        single: Boolean,
        preds: List<Expr>,
    ): ScanSpec {
        val isTags = table == SqlProfile.TAGS
        var ids: Set<String>? = null
        var authors: Set<String>? = null
        var kinds: Set<Int>? = null
        var since: Long? = null
        var until: Long? = null
        var names: Set<String>? = null
        var values: Set<String>? = null
        var valueNonEmpty = false
        var exact = true

        fun column(e: Expr): String? {
            if (e !is ColumnRef) return null
            if (e.table != null && e.table.lowercase() != alias) return null
            if (e.table == null && !single) return null
            return e.column.lowercase()
        }

        fun <T> intersect(
            current: Set<T>?,
            next: Set<T>,
        ) = current?.intersect(next) ?: next

        fun strings(e: Expr): Set<String>? = (constant(e) as? String)?.let { setOf(it) }

        fun longs(e: Expr): Long? = constant(e) as? Long

        for (p in preds) {
            var used = false
            when {
                // col = const / const = col
                p is Binary && p.op == "=" -> {
                    val (col, other) = column(p.left)?.let { it to p.right } ?: column(p.right)?.let { it to p.left } ?: (null to null)
                    if (col != null && other != null) {
                        used = true
                        when (col) {
                            "id", "event_id" -> {
                                if ((col == "id") == !isTags) {
                                    strings(other)?.let { ids = intersect(ids, it) } ?: run { used = false }
                                } else {
                                    used = false
                                }
                            }
                            "pubkey" -> strings(other)?.let { authors = intersect(authors, it) } ?: run { used = false }
                            "kind" -> longs(other)?.let { kinds = intersect(kinds, setOf(it.toIntOrNull() ?: Int.MIN_VALUE)) } ?: run { used = false }
                            "created_at" ->
                                longs(other)?.let {
                                    since = maxOf(since ?: it, it)
                                    until = minOf(until ?: it, it)
                                } ?: run { used = false }
                            "name" -> if (isTags) strings(other)?.let { names = intersect(names, it) } ?: run { used = false } else used = false
                            "value" -> if (isTags) strings(other)?.let { values = intersect(values, it) } ?: run { used = false } else used = false
                            else -> used = false
                        }
                    }
                }

                // col IN (consts)
                p is InList && !p.not && p.items.isNotEmpty() -> {
                    val col = column(p.expr)
                    if (col != null) {
                        val consts = p.items.map { constant(it) }
                        used = true
                        when {
                            (col == "id" && !isTags) || (col == "event_id" && isTags) -> {
                                if (consts.all { it is String }) ids = intersect(ids, consts.map { it as String }.toSet()) else used = false
                            }
                            col == "pubkey" -> {
                                if (consts.all { it is String }) authors = intersect(authors, consts.map { it as String }.toSet()) else used = false
                            }
                            col == "kind" -> {
                                if (consts.all { it is Long }) kinds = intersect(kinds, consts.map { (it as Long).toIntOrNull() ?: Int.MIN_VALUE }.toSet()) else used = false
                            }
                            col == "name" && isTags -> {
                                if (consts.all { it is String }) names = intersect(names, consts.map { it as String }.toSet()) else used = false
                            }
                            col == "value" && isTags -> {
                                if (consts.all { it is String }) values = intersect(values, consts.map { it as String }.toSet()) else used = false
                            }
                            else -> used = false
                        }
                    }
                }

                // value <> '' / '' <> value
                p is Binary && p.op == "<>" && isTags &&
                    ((column(p.left) == "value" && constant(p.right) == "") || (column(p.right) == "value" && constant(p.left) == "")) -> {
                    used = true
                    valueNonEmpty = true
                }

                // created_at <op> const, either side
                p is Binary && p.op in RANGE_OPS -> {
                    val leftCol = column(p.left)
                    val rightCol = column(p.right)
                    val (op, value) =
                        when {
                            leftCol == "created_at" -> p.op to longs(p.right)
                            rightCol == "created_at" -> FLIP[p.op]!! to longs(p.left)
                            else -> null to null
                        }
                    if (op != null && value != null) {
                        used = true
                        when (op) {
                            // Saturate rather than overflow: a wider range is still a superset.
                            ">" -> since = maxOf(since ?: Long.MIN_VALUE, if (value == Long.MAX_VALUE) value else value + 1)
                            ">=" -> since = maxOf(since ?: Long.MIN_VALUE, value)
                            "<" -> until = minOf(until ?: Long.MAX_VALUE, if (value == Long.MIN_VALUE) value else value - 1)
                            "<=" -> until = minOf(until ?: Long.MAX_VALUE, value)
                        }
                    }
                }

                // created_at BETWEEN a AND b
                p is Between && !p.not && column(p.expr) == "created_at" -> {
                    val lo = longs(p.low)
                    val hi = longs(p.high)
                    if (lo != null && hi != null) {
                        used = true
                        since = maxOf(since ?: lo, lo)
                        until = minOf(until ?: hi, hi)
                    }
                }
            }
            if (!used) exact = false
        }

        // Two different names can't both hold for one tag row: nothing matches.
        if (names?.isEmpty() == true) return ScanSpec(table, ids = emptySet(), exact = true)
        val name = names?.singleOrNull()
        return ScanSpec(
            table = table,
            ids = ids,
            authors = authors,
            kinds = kinds,
            since = since,
            until = until,
            tagName = name,
            tagValues = values,
            valueNonEmpty = valueNonEmpty,
            // Several candidate names (`name IN ('a','b')`) aren't expressible.
            exact = exact && (names == null || name != null),
        )
    }

    private fun Long.toIntOrNull(): Int? = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null

    companion object {
        private val RANGE_OPS = setOf("<", "<=", ">", ">=")
        private val FLIP = mapOf("<" to ">", "<=" to ">=", ">" to "<", ">=" to "<=")
    }
}
