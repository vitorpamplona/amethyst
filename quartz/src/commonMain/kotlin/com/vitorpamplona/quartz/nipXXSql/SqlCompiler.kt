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
 * A fragment of relay-authored SQL that produces one virtual table's
 * rows, with its own `?` bind arguments. It must return exactly the
 * profile's columns ([SqlProfile.TABLES]) in order.
 */
class TableSource(
    val sql: String,
    val args: List<Any?> = emptyList(),
)

/**
 * What `events` and `tags` mean for one session. This is the access-control
 * hook: a relay that hides gift wraps from unauthenticated sessions builds
 * sources whose `WHERE` drops them (from both tables), and every
 * reference to `events` / `tags` in the client's query is replaced by
 * these fragments. The client can never name anything else.
 */
class SqlTableSources(
    val events: TableSource,
    val tags: TableSource,
    /**
     * A narrower `tags` source for a reference the query constrains with
     * `name = <n> AND value IN (<values>)`, or null to keep [tags]. It must
     * return every row [tags] would for events having such a tag (extra
     * rows are fine: the query's own predicates still run on top).
     */
    val tagsMatching: ((TagConstraint) -> TableSource?)? = null,
    /**
     * When set, every base `events` / `tags` reference is resolved through
     * this instead, given what the query asks of that reference. Used to
     * run a query over rows a non-SQL store fetched per reference.
     */
    val perReference: ((ScanSpec) -> TableSource)? = null,
)

/** Equality constraints found on one `tags` reference: `name = [name]` and `value IN [values]`. */
class TagConstraint(
    val name: String,
    val values: List<String>,
)

class CompiledQuery(
    val sql: String,
    val args: List<Any?>,
)

/**
 * Turns a parsed [Query] into SQLite SQL. Nothing from the client's text
 * is copied through: literals are re-quoted, identifiers re-quoted,
 * every operator re-emitted fully parenthesized, CTEs renamed to
 * generated names, and every table reference resolved here — to a CTE in
 * scope or to one of the [SqlTableSources] — so the emitted statement can
 * only read what the sources expose.
 */
class SqlCompiler private constructor(
    private val sources: SqlTableSources,
    private val positional: List<Any?>,
    private val named: Map<String, Any?>,
) {
    private val sb = StringBuilder()
    private val args = ArrayList<Any?>()

    /** Innermost last. Each frame maps lowercased CTE name -> generated name. */
    private val cteScopes = ArrayList<HashMap<String, String>>()

    /** Base references of the SELECT being emitted, with what the query asks of each. */
    private var scanSpecs: Map<TableRef, ScanSpec> = emptyMap()
    private val analyzer = ScanAnalyzer(::constant)
    private var cteCounter = 0

    companion object {
        const val CTE_PREFIX = "nsq_cte_"

        fun compile(
            sql: String,
            sources: SqlTableSources,
            positional: List<Any?> = emptyList(),
            named: Map<String, Any?> = emptyMap(),
        ): CompiledQuery = compile(SqlParser.parse(sql), sources, positional, named)

        fun compile(
            query: Query,
            sources: SqlTableSources,
            positional: List<Any?> = emptyList(),
            named: Map<String, Any?> = emptyMap(),
        ): CompiledQuery {
            val c = SqlCompiler(sources, positional, named)
            c.query(query)
            return CompiledQuery(c.sb.toString(), c.args)
        }

        /**
         * Backticks, not double quotes: SQLite turns a double-quoted name
         * that matches no column into a string literal (the "DQS" legacy
         * behavior), so `SELECT "nope"` would silently return 'nope'.
         * Backtick-quoted names are always identifiers.
         */
        fun quoteIdent(name: String) = "`" + name.replace("`", "``") + "`"

        fun quoteString(value: String) = "'" + value.replace("'", "''") + "'"
    }

    // ---- query -------------------------------------------------------------

    private fun query(q: Query) {
        val with = q.with
        if (with != null) {
            cteScopes.add(HashMap())
            sb.append(if (with.recursive) "WITH RECURSIVE " else "WITH ")
            with.ctes.forEachIndexed { i, cte ->
                val key = cte.name.lowercase()
                val scope = cteScopes.last()
                if (key in scope) throw SqlException.invalid("duplicate common table expression ${cte.name}", cte.pos)
                val generated = CTE_PREFIX + (++cteCounter)
                // A CTE sees the ones declared before it and itself (SQLite
                // treats a self-reference as recursion); never later ones,
                // so a forward name can't fall through to something else.
                scope[key] = generated
                if (i > 0) sb.append(", ")
                sb.append(quoteIdent(generated))
                if (cte.columns.isNotEmpty()) {
                    sb.append('(')
                    cte.columns.forEachIndexed { j, col ->
                        if (j > 0) sb.append(", ")
                        sb.append(quoteIdent(col))
                    }
                    sb.append(')')
                }
                sb.append(" AS (")
                query(cte.query)
                sb.append(')')
            }
            sb.append(' ')
        }

        core(q.first)
        q.rest.forEach { (op, c) ->
            sb.append(' ').append(op.sql).append(' ')
            core(c)
        }
        if (q.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            ordering(q.orderBy)
        }
        q.limit?.let {
            sb.append(" LIMIT ")
            expr(it)
        }
        q.offset?.let {
            sb.append(" OFFSET ")
            expr(it)
        }

        if (with != null) cteScopes.removeAt(cteScopes.size - 1)
    }

    private fun core(c: SelectCore) {
        when (c) {
            is Values -> {
                sb.append("VALUES ")
                c.rows.forEachIndexed { i, row ->
                    if (i > 0) sb.append(", ")
                    sb.append('(')
                    exprList(row)
                    sb.append(')')
                }
            }

            is Select -> {
                select(c)
            }
        }
    }

    private fun select(s: Select) {
        sb.append("SELECT ")
        if (s.distinct) sb.append("DISTINCT ")
        s.columns.forEachIndexed { i, col ->
            if (i > 0) sb.append(", ")
            when (col) {
                is StarColumn -> {
                    if (col.table != null) sb.append(quoteIdent(col.table)).append('.')
                    sb.append('*')
                }

                is ExprColumn -> {
                    expr(col.expr)
                    // Always name the column: our re-emitted text differs from
                    // the client's, and SQLite names an un-aliased expression
                    // after its source text. A bare column keeps its own name.
                    val name = col.alias ?: if (col.expr is ColumnRef) col.expr.column else col.sourceText
                    sb.append(" AS ").append(quoteIdent(name))
                }
            }
        }
        s.from?.let {
            val outer = scanSpecs
            scanSpecs = if (sources.tagsMatching != null || sources.perReference != null) findScanSpecs(s, it) else emptyMap()
            sb.append(" FROM ")
            from(it)
            scanSpecs = outer
        }
        s.where?.let {
            sb.append(" WHERE ")
            expr(it)
        }
        if (s.groupBy.isNotEmpty()) {
            sb.append(" GROUP BY ")
            exprList(s.groupBy)
        }
        s.having?.let {
            sb.append(" HAVING ")
            expr(it)
        }
    }

    private fun from(f: FromItem) {
        when (f) {
            is TableRef -> {
                tableRef(f)
            }

            is SubqueryRef -> {
                sb.append('(')
                query(f.query)
                sb.append(')')
                f.alias?.let { sb.append(" AS ").append(quoteIdent(it)) }
            }

            is Join -> {
                from(f.left)
                if (f.type == JoinType.COMMA) sb.append(", ") else sb.append(' ').append(f.type.sql).append(' ')
                from(f.right)
                f.on?.let {
                    sb.append(" ON ")
                    expr(it)
                }
            }
        }
    }

    private fun tableRef(t: TableRef) {
        val key = t.name.lowercase()
        // Keep the name the client used as the alias so `events.id` and
        // `x.id` qualifiers resolve the same way they would against real tables.
        val alias = quoteIdent(t.alias ?: t.name)
        for (i in cteScopes.indices.reversed()) {
            val generated = cteScopes[i][key] ?: continue
            sb.append(quoteIdent(generated)).append(" AS ").append(alias)
            return
        }
        if (key != SqlProfile.EVENTS && key != SqlProfile.TAGS) {
            throw SqlException.invalid("no such table: ${t.name} (tables are ${SqlProfile.TABLES.keys.joinToString()})", t.pos)
        }
        val spec = scanSpecs[t] ?: ScanSpec(key)
        val perReference = sources.perReference
        val source =
            when {
                perReference != null -> {
                    perReference(spec)
                }

                key == SqlProfile.EVENTS -> {
                    sources.events
                }

                else -> {
                    val name = spec.tagName
                    val values = spec.tagValues
                    val narrowed = if (name != null && values != null) sources.tagsMatching?.invoke(TagConstraint(name, values.toList())) else null
                    narrowed ?: sources.tags
                }
            }
        sb
            .append('(')
            .append(source.sql)
            .append(") AS ")
            .append(alias)
        args.addAll(source.args)
    }

    // ---- scan analysis -----------------------------------------------------

    /**
     * For each base `events` / `tags` reference in [from], reads what the
     * query asks of its rows from the conjuncts that must hold for them: the
     * top-level ANDs of WHERE, and of the ON of any join except a LEFT JOIN
     * whose preserved (left) side holds the reference. The comparisons
     * understood are strict (NULL never passes), so a WHERE condition may
     * also narrow the null-extended side of a LEFT JOIN.
     */
    private fun findScanSpecs(
        s: Select,
        from: FromItem,
    ): Map<TableRef, ScanSpec> {
        val refs = ArrayList<TableRef>()
        collectBaseRefs(from, refs)
        if (refs.isEmpty()) return emptyMap()
        val whereConjuncts = conjuncts(s.where)
        val result = HashMap<TableRef, ScanSpec>()
        for (ref in refs) {
            result[ref] =
                analyzer.analyze(
                    table = ref.name.lowercase(),
                    alias = (ref.alias ?: ref.name).lowercase(),
                    single = from === ref,
                    preds = whereConjuncts + onConjunctsFor(ref, from),
                )
        }
        return result
    }

    private fun collectBaseRefs(
        f: FromItem,
        out: MutableList<TableRef>,
    ) {
        when (f) {
            is TableRef -> if (f.name.lowercase() in SqlProfile.TABLES && !isCte(f.name)) out.add(f)
            is Join -> {
                collectBaseRefs(f.left, out)
                collectBaseRefs(f.right, out)
            }
            is SubqueryRef -> {}
        }
    }

    private fun isCte(name: String): Boolean {
        val key = name.lowercase()
        return cteScopes.any { key in it }
    }

    /** ON conjuncts that restrict [ref]'s rows; see [findScanSpecs]. */
    private fun onConjunctsFor(
        ref: TableRef,
        f: FromItem,
    ): List<Expr> {
        if (f !is Join) return emptyList()
        val inRight = f.right === ref
        val inLeft = contains(f.left, ref)
        val own =
            when {
                f.on == null -> emptyList()
                inRight -> conjuncts(f.on)
                inLeft && f.type != JoinType.LEFT -> conjuncts(f.on)
                else -> emptyList()
            }
        return own + onConjunctsFor(ref, f.left)
    }

    private fun contains(
        f: FromItem,
        ref: TableRef,
    ): Boolean =
        when (f) {
            is TableRef -> f === ref
            is Join -> contains(f.left, ref) || contains(f.right, ref)
            is SubqueryRef -> false
        }

    private fun conjuncts(e: Expr?): List<Expr> =
        when {
            e == null -> emptyList()
            e is Binary && e.op == "AND" -> conjuncts(e.left) + conjuncts(e.right)
            else -> listOf(e)
        }

    /**
     * A value known at compile time: a string or number literal, or a
     * parameter. Integers come back as `Long`, reals as `Double`, the
     * same storage classes SQLite compares them in; null otherwise.
     */
    private fun constant(e: Expr): Any? =
        when (e) {
            is Literal -> {
                when (e.kind) {
                    LiteralKind.STRING -> e.text
                    LiteralKind.NUMBER -> e.text.toLongOrNull() ?: e.text.toDoubleOrNull()
                    else -> null
                }
            }

            is Param -> {
                when (val v = paramValue(e)) {
                    is Int -> v.toLong()
                    is Short -> v.toLong()
                    is Byte -> v.toLong()
                    is Float -> v.toDouble()
                    else -> v
                }
            }

            else -> {
                null
            }
        }

    private fun paramValue(p: Param): Any? =
        if (p.name.startsWith(":")) {
            named[p.name.substring(1)]
        } else {
            positional.getOrNull(p.name.substring(1).toInt() - 1)
        }

    private fun ordering(terms: List<OrderingTerm>) {
        terms.forEachIndexed { i, t ->
            if (i > 0) sb.append(", ")
            expr(t.expr)
            if (t.descending) sb.append(" DESC")
            when (t.nullsFirst) {
                true -> sb.append(" NULLS FIRST")
                false -> sb.append(" NULLS LAST")
                null -> {}
            }
        }
    }

    // ---- expressions -------------------------------------------------------

    private fun exprList(list: List<Expr>) {
        list.forEachIndexed { i, e ->
            if (i > 0) sb.append(", ")
            expr(e)
        }
    }

    private fun expr(e: Expr) {
        when (e) {
            is Literal -> {
                when (e.kind) {
                    LiteralKind.STRING -> sb.append(quoteString(e.text))
                    else -> sb.append(e.text)
                }
            }

            is Param -> {
                sb.append('?')
                args.add(bindValue(e))
            }

            is ColumnRef -> {
                e.table?.let { sb.append(quoteIdent(it)).append('.') }
                sb.append(quoteIdent(e.column))
            }

            is Unary -> {
                // The space matters: `(-` + `-1` would open a `--` comment.
                sb.append('(').append(e.op).append(' ')
                expr(e.operand)
                sb.append(')')
            }

            is Binary -> {
                sb.append('(')
                expr(e.left)
                sb.append(' ').append(e.op).append(' ')
                expr(e.right)
                sb.append(')')
            }

            is Between -> {
                sb.append('(')
                expr(e.expr)
                sb.append(if (e.not) " NOT BETWEEN " else " BETWEEN ")
                expr(e.low)
                sb.append(" AND ")
                expr(e.high)
                sb.append(')')
            }

            is InList -> {
                sb.append('(')
                expr(e.expr)
                sb.append(if (e.not) " NOT IN (" else " IN (")
                exprList(e.items)
                sb.append("))")
            }

            is InQuery -> {
                sb.append('(')
                expr(e.expr)
                sb.append(if (e.not) " NOT IN (" else " IN (")
                query(e.query)
                sb.append("))")
            }

            is Match -> {
                sb.append('(')
                expr(e.expr)
                sb.append(if (e.not) " NOT " else " ").append(e.op).append(' ')
                expr(e.pattern)
                e.escape?.let {
                    sb.append(" ESCAPE ")
                    expr(it)
                }
                sb.append(')')
            }

            is IsNull -> {
                sb.append('(')
                expr(e.expr)
                sb.append(if (e.not) " NOTNULL)" else " ISNULL)")
            }

            is Case -> {
                sb.append("(CASE")
                e.operand?.let {
                    sb.append(' ')
                    expr(it)
                }
                e.whens.forEach { (w, t) ->
                    sb.append(" WHEN ")
                    expr(w)
                    sb.append(" THEN ")
                    expr(t)
                }
                e.elseExpr?.let {
                    sb.append(" ELSE ")
                    expr(it)
                }
                sb.append(" END)")
            }

            is Cast -> {
                sb.append("CAST(")
                expr(e.expr)
                sb.append(" AS ").append(e.type).append(')')
            }

            is FunctionCall -> {
                function(e)
            }

            is Exists -> {
                sb.append("(EXISTS (")
                query(e.query)
                sb.append("))")
            }

            is ScalarSubquery -> {
                sb.append('(')
                query(e.query)
                sb.append(')')
            }
        }
    }

    private fun function(f: FunctionCall) {
        sb.append(f.name).append('(')
        if (f.star) {
            sb.append('*')
        } else {
            if (f.distinct) sb.append("DISTINCT ")
            exprList(f.args)
        }
        sb.append(')')
        f.filter?.let {
            sb.append(" FILTER (WHERE ")
            expr(it)
            sb.append(')')
        }
        f.over?.let { w ->
            sb.append(" OVER (")
            var needSpace = false
            if (w.partitionBy.isNotEmpty()) {
                sb.append("PARTITION BY ")
                exprList(w.partitionBy)
                needSpace = true
            }
            if (w.orderBy.isNotEmpty()) {
                if (needSpace) sb.append(' ')
                sb.append("ORDER BY ")
                ordering(w.orderBy)
                needSpace = true
            }
            w.frame?.let { frame ->
                if (needSpace) sb.append(' ')
                sb.append(frame.unit.name).append(' ')
                if (frame.end != null) {
                    sb.append("BETWEEN ")
                    bound(frame.start)
                    sb.append(" AND ")
                    bound(frame.end)
                } else {
                    bound(frame.start)
                }
            }
            sb.append(')')
        }
    }

    private fun bound(b: FrameBound) {
        b.offset?.let {
            expr(it)
            sb.append(' ')
        }
        sb.append(b.kind.sql)
    }

    // ---- parameters --------------------------------------------------------

    private fun bindValue(p: Param): Any? {
        val raw =
            if (p.name.startsWith(":")) {
                val key = p.name.substring(1)
                if (!named.containsKey(key)) throw SqlException.invalid("no value for parameter ${p.name}", p.pos)
                named[key]
            } else {
                val index = p.name.substring(1).toInt()
                if (index > positional.size) throw SqlException.invalid("no value for parameter ${p.name}", p.pos)
                positional[index - 1]
            }
        return when (raw) {
            null, is String, is Long, is Double -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            is Float -> raw.toDouble()
            is Boolean -> if (raw) 1L else 0L
            else -> throw SqlException.invalid("parameter ${p.name} must be a string, number, boolean or null", p.pos)
        }
    }
}
