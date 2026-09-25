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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/** A query result read a page at a time. [fetch] may suspend while the store works. */
interface SqlRows : AutoCloseable {
    val columns: List<String>
    val isDone: Boolean

    /** Up to [max] more rows; after the last one [isDone] turns true. */
    suspend fun fetch(max: Int): List<List<Any?>>
}

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
 * no matches, where `count` is 0 and `min`/`max`/`sum` are null). The
 * executor applies the query's ORDER BY, LIMIT, OFFSET and column names.
 * For `tags`, rows are tag rows (an event with the same tag twice counts twice).
 */
class AggregatePlan(
    val scan: ScanSpec,
    val groupBy: List<String>,
    val aggregates: List<Aggregate>,
)

/**
 * How a store that can't run SQL answers the SQL profile efficiently. The
 * executor ([SqlPushdown]) asks for a native answer first, then falls back
 * to per-reference scans whose results a small local SQLite finishes.
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
        store.query<Event>(spec.toFilter()).forEach(onEvent)
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

/**
 * Runs the SQL profile against a [SqlStoreBackend]:
 *  1. A single-table aggregate or DISTINCT whose conditions the store can
 *     express exactly goes to [SqlStoreBackend.aggregate].
 *  2. Otherwise every `events` / `tags` reference is fetched with only the
 *     conditions the query puts on it (and a newest-first LIMIT when the
 *     whole query is such a listing), into a private in-memory SQLite that
 *     then runs the compiled query. Results are identical to the SQLite
 *     store by construction: the same compiler and the same `tags`
 *     derivation run over a superset of the rows.
 */
object SqlPushdown {
    suspend fun open(
        query: String,
        params: List<Any?>,
        named: Map<String, Any?>,
        backend: SqlStoreBackend,
    ): SqlRows {
        val parsed = SqlParser.parse(query)

        NativeShape.of(parsed, params, named)?.let { shape ->
            backend.aggregate(shape.plan)?.let { rows -> return shape.finish(rows) }
        }

        val conn = BundledSQLiteDriver().open(":memory:")
        try {
            val specs = ArrayList<ScanSpec>()
            val sources =
                SqlTableSources(
                    events = TableSource(""),
                    tags = TableSource(""),
                    perReference = { spec ->
                        val table = "s" + specs.size
                        specs.add(spec)
                        TableSource(if (spec.table == SqlProfile.TAGS) EventStoreTableSources.tagsSql(table) else EventStoreTableSources.eventsSql(table))
                    },
                )
            val compiled = SqlCompiler.compile(parsed, sources, params, named)
            val newestFirstLimit = if (specs.size == 1) NativeShape.newestFirstLimit(parsed, params, named) else null

            specs.forEachIndexed { i, _ ->
                conn.execSQL(
                    "CREATE TABLE s$i (row_id INTEGER PRIMARY KEY, id TEXT UNIQUE, pubkey TEXT, created_at INTEGER, " +
                        "kind INTEGER, tags TEXT, content TEXT, sig TEXT)",
                )
            }

            // Every reference the store accepts on its own conditions first; then the ones it
            // refuses, each narrowed by a join key another loaded reference pins down.
            val loaded = BooleanArray(specs.size)
            specs.forEachIndexed { i, spec ->
                if (spec.matchesNothing) {
                    loaded[i] = true
                } else if (backend.acceptsScan(spec)) {
                    val limited = newestFirstLimit != null && spec.exact && spec.table == SqlProfile.EVENTS
                    load(conn, i) { sink -> if (limited) loadNewest(backend, spec.withLimit(newestFirstLimit), sink) else backend.events(spec, sink) }
                    loaded[i] = true
                }
            }
            val index = specs.withIndex().associate { (i, spec) -> spec.ref to i }
            var progress = true
            while (progress) {
                progress = false
                for (i in specs.indices) {
                    if (loaded[i]) continue
                    for (link in specs[i].links) {
                        val j = index[link.target] ?: continue
                        if (!loaded[j]) continue
                        val values = joinKeys(conn, j, specs[j], link.targetColumn) ?: continue
                        val narrowed = specs[i].narrowedTo(link.column, values) ?: continue
                        if (!narrowed.matchesNothing) {
                            if (!backend.acceptsScan(narrowed)) continue
                            val keys = if (link.column == "pubkey") narrowed.authors!! else narrowed.ids!!
                            load(conn, i) { sink ->
                                for (chunk in keys.chunked(JOIN_KEY_CHUNK)) backend.events(specs[i].narrowedTo(link.column, chunk.toSet())!!, sink)
                            }
                        }
                        loaded[i] = true
                        progress = true
                        break
                    }
                }
            }
            if (!loaded.all { it }) {
                throw SqlException.unsupported(
                    "this relay needs a condition on kind, pubkey, id or a single-letter tag for every table in the query, " +
                        "or a join to one that has it",
                )
            }
            val cursor = SqlCursor(conn, compiled)
            return object : SqlRows {
                override val columns = cursor.columns
                override val isDone get() = cursor.isDone

                override suspend fun fetch(max: Int) = cursor.fetch(max)

                override fun close() {
                    cursor.close()
                    conn.close()
                }
            }
        } catch (e: Throwable) {
            conn.close()
            throw e
        }
    }

    /** Inserts what [fetch] hands over into scratch table `s[i]`. */
    private suspend fun load(
        conn: SQLiteConnection,
        i: Int,
        fetch: suspend ((Event) -> Unit) -> Unit,
    ) {
        conn.prepare("INSERT OR IGNORE INTO s$i (id, pubkey, created_at, kind, tags, content, sig) VALUES (?, ?, ?, ?, ?, ?, ?)").use { insert ->
            fetch { e: Event ->
                insert.bindText(1, e.id)
                insert.bindText(2, e.pubKey)
                insert.bindLong(3, e.createdAt)
                insert.bindLong(4, e.kind.toLong())
                insert.bindText(5, OptimizedJsonMapper.toJson(e.tags))
                insert.bindText(6, e.content)
                insert.bindText(7, e.sig)
                insert.step()
                insert.reset()
            }
        }
    }

    /**
     * The distinct text values of [column] over loaded reference `s[j]`, as its
     * profile table shows them (for `tags`, only rows of the spec's tag name), or
     * null for a column the profile doesn't have. A superset of the values any
     * joined row can carry, since `s[j]` holds a superset of that reference's rows.
     */
    private fun joinKeys(
        conn: SQLiteConnection,
        j: Int,
        spec: ScanSpec,
        column: String,
    ): Set<String>? {
        val isTags = spec.table == SqlProfile.TAGS
        if (column !in (if (isTags) SqlProfile.TAGS_COLUMNS else SqlProfile.EVENTS_COLUMNS)) return null
        val source = if (isTags) EventStoreTableSources.tagsSql("s$j") else EventStoreTableSources.eventsSql("s$j")
        val col = SqlCompiler.quoteIdent(column)
        val byName = if (isTags && spec.tagName != null) " AND x.`name` = ?" else ""
        val out = HashSet<String>()
        conn.prepare("SELECT DISTINCT x.$col FROM ($source) AS x WHERE typeof(x.$col) = 'text'$byName").use { stmt ->
            if (byName.isNotEmpty()) stmt.bindText(1, spec.tagName!!)
            while (stmt.step()) out.add(stmt.getText(0))
        }
        return out
    }

    /**
     * The newest [ScanSpec.limit] events, plus every event tied with the
     * oldest of them: the store may break `created_at` ties any way, so the
     * whole boundary group is fetched and the query's ORDER BY picks.
     */
    private suspend fun loadNewest(
        backend: SqlStoreBackend,
        spec: ScanSpec,
        load: (Event) -> Unit,
    ) {
        val limit = spec.limit ?: return backend.events(spec, load)
        var count = 0
        var oldest = Long.MAX_VALUE
        backend.events(spec) {
            count++
            if (it.createdAt < oldest) oldest = it.createdAt
            load(it)
        }
        if (count >= limit && limit > 0) {
            backend.events(spec.withTimeRange(oldest, oldest), load)
        }
    }

    /** Runs [query] and hands the rows to the callbacks; the default [IEventStore.sql]. */
    suspend fun run(
        query: String,
        params: List<Any?>,
        named: Map<String, Any?>,
        backend: SqlStoreBackend,
        onColumns: (List<String>) -> Unit,
        onRow: (List<Any?>) -> Unit,
    ) {
        open(query, params, named, backend).use { rows ->
            onColumns(rows.columns)
            while (!rows.isDone) rows.fetch(PAGE).forEach(onRow)
        }
    }

    private const val PAGE = 500

    /** Join keys per store call when a reference is fetched by another's keys. */
    private const val JOIN_KEY_CHUNK = 500
}

/**
 * Recognizes the query shapes a store may answer natively, and turns the
 * store's grouped rows back into the query's exact output.
 */
internal class NativeShape private constructor(
    val plan: AggregatePlan,
    /** Output column i = grouped-row column [outputs][i]. */
    private val outputs: List<Int>,
    private val names: List<String>,
    /** ORDER BY over output ordinals (1-based), with direction and NULLS. */
    private val orderBy: List<Triple<Int, Boolean, Boolean?>>,
    private val limit: Long?,
    private val offset: Long?,
) {
    /** Runs the query's ORDER BY / LIMIT / OFFSET over the grouped rows in SQLite, for exact ordering semantics. */
    fun finish(rows: List<List<Any?>>): SqlRows {
        val width = plan.groupBy.size + plan.aggregates.size
        val conn = BundledSQLiteDriver().open(":memory:")
        try {
            conn.execSQL("CREATE TABLE g (" + (0 until width).joinToString(", ") { "c$it" } + ")")
            if (width > 0) {
                conn.prepare("INSERT INTO g VALUES (" + (0 until width).joinToString(", ") { "?" } + ")").use { insert ->
                    rows.forEach { row ->
                        row.forEachIndexed { i, v -> bind(insert, i + 1, v) }
                        insert.step()
                        insert.reset()
                    }
                }
            }
            val sql =
                buildString {
                    append("SELECT ")
                    append(outputs.mapIndexed { i, c -> "c$c AS " + SqlCompiler.quoteIdent(names[i]) }.joinToString(", "))
                    append(" FROM g")
                    if (orderBy.isNotEmpty()) {
                        append(" ORDER BY ")
                        append(
                            orderBy.joinToString(", ") { (ordinal, desc, nullsFirst) ->
                                "$ordinal" + (if (desc) " DESC" else "") +
                                    when (nullsFirst) {
                                        true -> " NULLS FIRST"
                                        false -> " NULLS LAST"
                                        null -> ""
                                    }
                            },
                        )
                    }
                    if (limit != null) append(" LIMIT $limit")
                    if (offset != null) append(" OFFSET $offset")
                }
            val cursor = SqlCursor(conn, CompiledQuery(sql, emptyList()))
            return object : SqlRows {
                override val columns = cursor.columns
                override val isDone get() = cursor.isDone

                override suspend fun fetch(max: Int) = cursor.fetch(max)

                override fun close() {
                    cursor.close()
                    conn.close()
                }
            }
        } catch (e: Throwable) {
            conn.close()
            throw e
        }
    }

    private fun bind(
        stmt: SQLiteStatement,
        index: Int,
        v: Any?,
    ) {
        when (v) {
            null -> stmt.bindNull(index)
            is Long -> stmt.bindLong(index, v)
            is Int -> stmt.bindLong(index, v.toLong())
            is Double -> stmt.bindDouble(index, v)
            else -> stmt.bindText(index, v.toString())
        }
    }

    companion object {
        private val NATIVE_AGGREGATES = setOf("count", "min", "max", "sum")

        fun of(
            q: Query,
            params: List<Any?>,
            named: Map<String, Any?>,
        ): NativeShape? {
            if (q.with != null || q.rest.isNotEmpty()) return null
            val s = q.first as? Select ?: return null
            val ref = s.from as? TableRef ?: return null
            val table = ref.name.lowercase()
            if (table !in SqlProfile.TABLES || s.having != null) return null
            val alias = (ref.alias ?: ref.name).lowercase()

            fun columnOf(e: Expr): String? {
                if (e !is ColumnRef) return null
                if (e.table != null && e.table.lowercase() != alias) return null
                val c = e.column.lowercase()
                return if (c in SqlProfile.TABLES[table]!!) c else null
            }

            val spec = scanOf(q, ref, params, named)
            if (!spec.exact) return null

            val groupBy =
                if (s.groupBy.isNotEmpty()) {
                    s.groupBy.map { columnOf(it) ?: return null }.distinct()
                } else {
                    emptyList()
                }
            val aggregates = ArrayList<Aggregate>()
            val outputs = ArrayList<Int>()
            val names = ArrayList<String>()
            val keys = if (s.distinct) ArrayList<String>() else ArrayList(groupBy)
            for (col in s.columns) {
                val ec = col as? ExprColumn ?: return null
                val e = ec.expr
                names.add(ec.alias ?: if (e is ColumnRef) e.column else ec.sourceText)
                val column = columnOf(e)
                if (column != null) {
                    if (s.distinct) {
                        if (column !in keys) keys.add(column)
                    } else if (column !in groupBy) {
                        return null
                    }
                    outputs.add(keys.indexOf(column))
                } else {
                    if (s.distinct) return null
                    val f = e as? FunctionCall ?: return null
                    if (f.name !in NATIVE_AGGREGATES || f.distinct || f.filter != null || f.over != null) return null
                    val agg =
                        when {
                            f.star && f.name == "count" -> Aggregate("count", null)
                            !f.star && f.args.size == 1 -> Aggregate(f.name, columnOf(f.args[0]) ?: return null)
                            else -> return null
                        }
                    aggregates.add(agg)
                    outputs.add(-aggregates.size) // resolved below
                }
            }
            // Plain listings aren't aggregates: leave them to scans.
            if (!s.distinct && s.groupBy.isEmpty() && aggregates.isEmpty()) return null
            if (s.distinct && s.groupBy.isNotEmpty()) return null
            val resolved = outputs.map { if (it < 0) keys.size + (-it - 1) else it }

            val orderBy =
                q.orderBy.map { term ->
                    val ordinal =
                        when (val e = term.expr) {
                            is Literal -> e.text.toIntOrNull()?.takeIf { it in 1..names.size }
                            is ColumnRef -> if (e.table == null) names.indexOfFirst { it.equals(e.column, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1) else null
                            else -> null
                        } ?: return null
                    Triple(ordinal, term.descending, term.nullsFirst)
                }

            val limit = q.limit?.let { constantLong(it, params, named) ?: return null }
            val offset = q.offset?.let { constantLong(it, params, named) ?: return null }

            return NativeShape(AggregatePlan(spec, keys, aggregates), resolved, names, orderBy, limit, offset)
        }

        /**
         * For `SELECT … FROM events WHERE <exact> ORDER BY created_at DESC[, …]
         * LIMIT n [OFFSET m]` with no aggregates or subqueries, the n + m
         * newest events are all the query can ever read.
         */
        fun newestFirstLimit(
            q: Query,
            params: List<Any?>,
            named: Map<String, Any?>,
        ): Int? {
            if (q.with != null || q.rest.isNotEmpty()) return null
            val s = q.first as? Select ?: return null
            val ref = s.from as? TableRef ?: return null
            if (!ref.name.equals(SqlProfile.EVENTS, ignoreCase = true)) return null
            if (s.distinct || s.groupBy.isNotEmpty() || s.having != null) return null
            if (s.columns.any { it is ExprColumn && hasCallOrSubquery(it.expr) }) return null
            val first = q.orderBy.firstOrNull() ?: return null
            val firstCol = first.expr as? ColumnRef ?: return null
            if (!firstCol.column.equals("created_at", ignoreCase = true) || !first.descending || first.nullsFirst == true) return null
            val alias = (ref.alias ?: ref.name).lowercase()
            if (firstCol.table != null && firstCol.table.lowercase() != alias) return null
            // A later ORDER BY term that is a call or subquery could depend on other rows.
            if (q.orderBy.any { hasCallOrSubquery(it.expr) }) return null
            val limit = constantLong(q.limit ?: return null, params, named) ?: return null
            val offset = q.offset?.let { constantLong(it, params, named) ?: return null } ?: 0
            val total = limit + offset
            return if (limit >= 0 && offset >= 0 && total <= Int.MAX_VALUE) total.toInt() else null
        }

        private fun hasCallOrSubquery(e: Expr): Boolean =
            when (e) {
                is FunctionCall, is ScalarSubquery, is Exists, is InQuery -> true
                is Unary -> hasCallOrSubquery(e.operand)
                is Binary -> hasCallOrSubquery(e.left) || hasCallOrSubquery(e.right)
                is Between -> hasCallOrSubquery(e.expr) || hasCallOrSubquery(e.low) || hasCallOrSubquery(e.high)
                is InList -> hasCallOrSubquery(e.expr) || e.items.any { hasCallOrSubquery(it) }
                is Match -> hasCallOrSubquery(e.expr) || hasCallOrSubquery(e.pattern) || (e.escape?.let { hasCallOrSubquery(it) } ?: false)
                is IsNull -> hasCallOrSubquery(e.expr)
                is Case -> (e.operand?.let { hasCallOrSubquery(it) } ?: false) || e.whens.any { hasCallOrSubquery(it.first) || hasCallOrSubquery(it.second) } || (e.elseExpr?.let { hasCallOrSubquery(it) } ?: false)
                is Cast -> hasCallOrSubquery(e.expr)
                else -> false
            }

        /** The scan spec the compiler would derive for a single-reference query. */
        private fun scanOf(
            q: Query,
            ref: TableRef,
            params: List<Any?>,
            named: Map<String, Any?>,
        ): ScanSpec {
            var spec: ScanSpec? = null
            SqlCompiler.compile(
                q,
                SqlTableSources(TableSource(""), TableSource(""), perReference = {
                    if (spec == null) spec = it
                    TableSource("SELECT 1")
                }),
                params,
                named,
            )
            return spec ?: ScanSpec(ref.name.lowercase())
        }

        private fun constantLong(
            e: Expr,
            params: List<Any?>,
            named: Map<String, Any?>,
        ): Long? =
            when (e) {
                is Literal -> if (e.kind == LiteralKind.NUMBER) e.text.toLongOrNull() else null
                is Param -> {
                    val v = if (e.name.startsWith(":")) named[e.name.substring(1)] else params.getOrNull(e.name.substring(1).toInt() - 1)
                    when (v) {
                        is Long -> v
                        is Int -> v.toLong()
                        else -> null
                    }
                }
                else -> null
            }
    }
}
