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
 * Which columns a query reads from each table reference, across the whole
 * query (subqueries and CTEs included), so the pushdown can skip what no one
 * reads. Conservative by construction: any `*` makes every reference read
 * everything, a qualifier counts for every reference with that alias
 * wherever it appears, and an unqualified name counts for every reference
 * whose table has such a column.
 */
internal class ColumnUsage private constructor(
    /** Predicates to leave out: what a spec already guarantees of its rows. */
    private val excluded: Set<Expr>,
) {
    private val qualified = HashMap<String, MutableSet<String>>()
    private val unqualified = HashSet<String>()
    private val starred = HashSet<String>()
    private var anyStar = false

    /** The columns the query reads from the reference [alias] of [table], or null for all of them. */
    fun columnsFor(
        alias: String,
        table: String,
    ): Set<String>? {
        if (anyStar || alias in starred) return null
        val own = SqlProfile.TABLES[table] ?: return null
        return qualified[alias].orEmpty() + unqualified.filter { it in own }
    }

    private fun query(q: Query) {
        q.with?.ctes?.forEach { query(it.query) }
        core(q.first)
        q.rest.forEach { core(it.second) }
        q.orderBy.forEach { expr(it.expr) }
        q.limit?.let(::expr)
        q.offset?.let(::expr)
    }

    private fun core(c: SelectCore) {
        when (c) {
            is Select -> {
                c.columns.forEach { col ->
                    when (col) {
                        is ExprColumn -> expr(col.expr)
                        is StarColumn -> if (col.table == null) anyStar = true else starred.add(col.table.lowercase())
                    }
                }
                c.from?.let(::from)
                c.where?.let(::expr)
                c.groupBy.forEach(::expr)
                c.having?.let(::expr)
            }

            is Values -> c.rows.forEach { row -> row.forEach(::expr) }
        }
    }

    private fun from(f: FromItem) {
        when (f) {
            is TableRef -> {}
            is SubqueryRef -> query(f.query)
            is Join -> {
                from(f.left)
                from(f.right)
                f.on?.let(::expr)
            }
        }
    }

    private fun expr(e: Expr) {
        if (e in excluded) return
        when (e) {
            is Literal, is Param -> {}
            is ColumnRef -> {
                val column = e.column.lowercase()
                if (e.table == null) unqualified.add(column) else qualified.getOrPut(e.table.lowercase()) { HashSet() }.add(column)
            }
            is Unary -> expr(e.operand)
            is Binary -> {
                expr(e.left)
                expr(e.right)
            }
            is Between -> {
                expr(e.expr)
                expr(e.low)
                expr(e.high)
            }
            is InList -> {
                expr(e.expr)
                e.items.forEach(::expr)
            }
            is InQuery -> {
                expr(e.expr)
                query(e.query)
            }
            is Match -> {
                expr(e.expr)
                expr(e.pattern)
                e.escape?.let(::expr)
            }
            is IsNull -> expr(e.expr)
            is Case -> {
                e.operand?.let(::expr)
                e.whens.forEach { (w, t) ->
                    expr(w)
                    expr(t)
                }
                e.elseExpr?.let(::expr)
            }
            is Cast -> expr(e.expr)
            is FunctionCall -> {
                e.args.forEach(::expr)
                e.filter?.let(::expr)
                e.over?.let { w ->
                    w.partitionBy.forEach(::expr)
                    w.orderBy.forEach { expr(it.expr) }
                    w.frame?.let { fr ->
                        fr.start.offset?.let(::expr)
                        fr.end?.offset?.let(::expr)
                    }
                }
            }
            is Exists -> query(e.query)
            is ScalarSubquery -> query(e.query)
        }
    }

    companion object {
        fun of(
            q: Query,
            excluded: Set<Expr> = emptySet(),
        ) = ColumnUsage(excluded).apply { query(q) }
    }
}
