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
import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/** One aggregate of an [AggregatePlan]: `count(*)` has a null [column]. */
class Aggregate(
    /** `count`, `min`, `max` or `sum`. */
    val function: String,
    val column: String?,
)

/**
 * A single-table aggregate or DISTINCT a store may answer natively:
 * `SELECT <groupBy…>, <aggregates…> FROM <scan.table> WHERE <scan> GROUP BY <groupBy…>`.
 * [scan] is exact: it holds every condition of the query.
 *
 * The answer is one row per group, the [groupBy] values followed by the
 * [aggregates], in any order; with no [groupBy], exactly one row (even over
 * no matches, where `count` is 0 and `min`/`max`/`sum` are null). Values
 * follow NQL's rules: `min`/`max` of TEXT in code point order, an INTEGER
 * `sum` exact. The executor applies the query's ORDER BY, LIMIT and OFFSET.
 * For `tags`, rows are tag rows (an event with the same tag twice counts twice).
 */
class AggregatePlan(
    val scan: ScanSpec,
    val groupBy: List<String>,
    val aggregates: List<Aggregate>,
)

/**
 * How a store answers NQL (NIP-FF). The executor ([NqlExecutor]) asks for a
 * native answer first, then falls back to per-source scans whose events it
 * evaluates the query over.
 */
interface SqlStoreBackend {
    /**
     * Every event covering [spec] (a superset is fine: the query still
     * filters). For a `tags` spec, the events whose tags hold the rows.
     * When [ScanSpec.limit] is set, the newest [ScanSpec.limit] by
     * `created_at` (ties in any order: the executor completes the tie group).
     */
    suspend fun events(
        spec: ScanSpec,
        onEvent: (Event) -> Unit,
    )

    /** False to refuse a scan as too broad; the query then fails `unsupported`. */
    fun acceptsScan(spec: ScanSpec): Boolean = true

    /** A native answer for [plan], or null to fall back to scans. */
    suspend fun aggregate(plan: AggregatePlan): List<List<Any?>>? = null

    /**
     * `(id, created_at)` of every event covering [spec], for an `events`
     * reference the query reads nothing else of ([ScanSpec.needsOnlyIdsAndTimes]):
     * an id walk instead of whole documents. Same coverage and [ScanSpec.limit]
     * rules as [events]. Returns false, having emitted nothing, when the store
     * has no cheaper way than [events].
     */
    suspend fun idsAndTimes(
        spec: ScanSpec,
        onEach: (id: String, createdAt: Long) -> Unit,
    ): Boolean = false
}

/**
 * The generic backend any [IEventStore] gets: scans through
 * [IEventStore.query], and `count(*)` through [IEventStore.count].
 */
open class FilterStoreBackend(
    private val store: IEventStore,
) : SqlStoreBackend {
    override suspend fun events(
        spec: ScanSpec,
        onEvent: (Event) -> Unit,
    ) {
        store.query<Event>(spec.toFilter(), onEvent)
    }

    override suspend fun aggregate(plan: AggregatePlan): List<List<Any?>>? {
        val isCountAll =
            plan.scan.table == SqlProfile.EVENTS && plan.groupBy.isEmpty() &&
                plan.aggregates.size == 1 && plan.aggregates[0].function == "count" && plan.aggregates[0].column == null
        if (!isCountAll || !plan.scan.coversAllConditions()) return null
        return listOf(listOf(store.count(plan.scan.toFilter()).toLong()))
    }

    /** True when [ScanSpec.toFilter] expresses the whole spec (no tag condition it had to drop). */
    protected fun ScanSpec.coversAllConditions() = tagName == null && tagValues == null
}
