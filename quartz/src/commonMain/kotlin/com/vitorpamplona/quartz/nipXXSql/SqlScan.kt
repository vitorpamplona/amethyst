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
    /** `tags` only: the row's `t0` (may be set without [tagValues]). */
    val tagName: String? = null,
    /** `tags` only: the row's `t1` (may be set without [tagName]). */
    val tagValues: Set<String>? = null,
    /** `tags` only: `t1 <> ''` (so also not null), the rows a tag-value index holds. */
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

    /** Equalities tying this reference to another in the same FROM, for the executor's join-key propagation. */
    internal var links: List<ScanLink> = emptyList()

    /**
     * The predicates this spec holds exactly (`kind IN …`, `pubkey = …`, time bounds, ids): every
     * row a store returns for the spec satisfies them.
     */
    internal var captured: List<NqlExpr> = emptyList()

    /**
     * The columns the query reads from this reference outside [captured], or null for all of them.
     */
    internal var columns: Set<String>? = null

    /**
     * An `events` reference the query reads nothing of but `id` / `created_at` (and what [captured]
     * checks): a store's id walk answers it, no documents needed. The rows it gets carry a member
     * of [kinds] / [authors] for those columns, which satisfies [captured] and is read by nothing else.
     */
    val needsOnlyIdsAndTimes: Boolean
        get() = table == SqlProfile.EVENTS && columns?.all { it == "id" || it == "created_at" } == true

    /**
     * This spec further restricted to rows whose [column] is one of [values],
     * or null when [column] isn't one a store can look up by (`id`/`event_id`,
     * `pubkey`).
     */
    internal fun narrowedTo(
        column: String,
        values: Set<String>,
    ): ScanSpec? =
        when {
            column == (if (table == SqlProfile.TAGS) "event_id" else "id") ->
                ScanSpec(table, ids?.intersect(values) ?: values, authors, kinds, since, until, tagName, tagValues, valueNonEmpty, limit, false)
            column == "pubkey" ->
                ScanSpec(table, ids, authors?.intersect(values) ?: values, kinds, since, until, tagName, tagValues, valueNonEmpty, limit, false)
            else -> null
        }

    override fun toString() =
        "ScanSpec($table ids=$ids authors=$authors kinds=$kinds since=$since until=$until " +
            "tag=$tagName:$tagValues nonEmpty=$valueNonEmpty limit=$limit exact=$exact)"
}

/**
 * `column = target.targetColumn` holds for every row of the reference that
 * can reach the query's output, [target] being another table source of the
 * same FROM (its index there).
 */
internal class ScanLink(
    val column: String,
    val target: Int,
    val targetColumn: String,
)

/**
 * Reads the conditions on one table source out of the predicates that must
 * hold for its rows. Only comparisons of one of its columns with a constant
 * are understood; any other predicate is left to the query and makes the spec
 * inexact. [column] names the source column an expression is, if it is one;
 * [constant] gives an expression's value when it is fixed for this run of the
 * query (a literal, a parameter, a column of an enclosing query), else null.
 */
internal class ScanAnalyzer(
    private val column: (NqlExpr) -> String?,
    private val constant: (NqlExpr) -> Any?,
) {
    fun analyze(
        table: String,
        preds: List<NqlExpr>,
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
        val captured = ArrayList<NqlExpr>()

        fun <T> intersect(
            current: Set<T>?,
            next: Set<T>,
        ) = current?.intersect(next) ?: next

        fun text(e: NqlExpr): String? = constant(e) as? String

        fun long(e: NqlExpr): Long? = constant(e) as? Long

        /** Narrows the spec by `col = value` / `col IN (values)`; false when it can't. */
        fun equals(
            col: String,
            items: List<NqlExpr>,
        ): Boolean {
            when (col) {
                "id", "event_id" -> {
                    if ((col == "id") == isTags) return false
                    ids = intersect(ids, items.map { text(it) ?: return false }.toSet())
                }

                "pubkey" -> {
                    authors = intersect(authors, items.map { text(it) ?: return false }.toSet())
                }

                "kind" -> {
                    kinds = intersect(kinds, items.map { (long(it) ?: return false).toIntOrNull() ?: Int.MIN_VALUE }.toSet())
                }

                "t0" -> {
                    if (!isTags) return false
                    names = intersect(names, items.map { text(it) ?: return false }.toSet())
                }

                "t1" -> {
                    if (!isTags) return false
                    values = intersect(values, items.map { text(it) ?: return false }.toSet())
                }

                "created_at" -> {
                    val v = items.singleOrNull()?.let(::long) ?: return false
                    since = maxOf(since ?: v, v)
                    until = minOf(until ?: v, v)
                }

                else -> {
                    return false
                }
            }
            return true
        }

        for (p in preds) {
            val used =
                when {
                    p is NqlBinary && p.op == "=" -> {
                        val left = column(p.left)
                        val right = column(p.right)
                        when {
                            left != null && right == null -> equals(left, listOf(p.right))
                            right != null && left == null -> equals(right, listOf(p.left))
                            else -> false
                        }
                    }

                    p is NqlInList && !p.not -> {
                        column(p.expr)?.let { equals(it, p.items) } ?: false
                    }

                    // t1 <> '' / '' <> t1: the rows a tag-value index holds.
                    p is NqlBinary && p.op == "<>" && isTags &&
                        ((column(p.left) == "t1" && constant(p.right) == "") || (column(p.right) == "t1" && constant(p.left) == "")) -> {
                        valueNonEmpty = true
                        true
                    }

                    p is NqlBinary && p.op in RANGE_OPS -> {
                        val (op, value) =
                            when {
                                column(p.left) == "created_at" -> p.op to long(p.right)
                                column(p.right) == "created_at" -> FLIP[p.op]!! to long(p.left)
                                else -> null to null
                            }
                        if (op != null && value != null) {
                            when (op) {
                                // Saturate rather than overflow: a wider range is still a superset.
                                ">" -> since = maxOf(since ?: Long.MIN_VALUE, if (value == Long.MAX_VALUE) value else value + 1)
                                ">=" -> since = maxOf(since ?: Long.MIN_VALUE, value)
                                "<" -> until = minOf(until ?: Long.MAX_VALUE, if (value == Long.MIN_VALUE) value else value - 1)
                                "<=" -> until = minOf(until ?: Long.MAX_VALUE, value)
                            }
                            true
                        } else {
                            false
                        }
                    }

                    p is NqlBetween && !p.not && column(p.expr) == "created_at" -> {
                        val lo = long(p.low)
                        val hi = long(p.high)
                        if (lo != null && hi != null) {
                            since = maxOf(since ?: lo, lo)
                            until = minOf(until ?: hi, hi)
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        false
                    }
                }
            if (used) captured.add(p) else exact = false
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
            // Several candidate names (`t0 IN ('a','b')`) aren't expressible.
            exact = exact && (names == null || name != null),
        ).also { it.captured = captured }
    }

    private fun Long.toIntOrNull(): Int? = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null

    companion object {
        private val RANGE_OPS = setOf("<", "<=", ">", ">=")
        private val FLIP = mapOf("<" to ">", "<=" to ">=", ">" to "<", ">=" to "<=")
    }
}
