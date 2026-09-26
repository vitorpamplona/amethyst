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

import com.vitorpamplona.quartz.nipXXSql.NqlType.BOOLEAN
import com.vitorpamplona.quartz.nipXXSql.NqlType.INTEGER
import com.vitorpamplona.quartz.nipXXSql.NqlType.NULL
import com.vitorpamplona.quartz.nipXXSql.NqlType.REAL
import com.vitorpamplona.quartz.nipXXSql.NqlType.TEXT

/** NQL's two sources and their columns, in `SELECT *` order (NIP-FF, Data model). */
object SqlProfile {
    const val EVENTS = "events"
    const val TAGS = "tags"

    val EVENTS_COLUMNS =
        listOf("id" to TEXT, "pubkey" to TEXT, "created_at" to INTEGER, "kind" to INTEGER, "content" to TEXT, "sig" to TEXT)

    val TAGS_COLUMNS =
        listOf(
            "event_id" to TEXT,
            "idx" to INTEGER,
            "t0" to TEXT,
            "t1" to TEXT,
            "t2" to TEXT,
            "t3" to TEXT,
            "t4" to TEXT,
            "created_at" to INTEGER,
            "kind" to INTEGER,
            "pubkey" to TEXT,
        )

    val TABLES = mapOf(EVENTS to EVENTS_COLUMNS, TAGS to TAGS_COLUMNS)

    val AGGREGATES = setOf("count", "sum", "avg", "min", "max")

    val FUNCTIONS =
        AGGREGATES +
            setOf(
                "length",
                "lower",
                "upper",
                "trim",
                "ltrim",
                "rtrim",
                "replace",
                "instr",
                "substr",
                "coalesce",
                "nullif",
                "abs",
                "round",
                "ceil",
                "floor",
                "trunc",
                "sqrt",
                "exp",
                "ln",
                "log10",
                "pow",
            )
}

/**
 * Resolves every name in a parsed query and checks it against NIP-FF's rules:
 * scopes, static types, result names, grouping, `ORDER BY` / `GROUP BY` by
 * name, and `LIMIT` / `OFFSET`. Any breach is `invalid`, before anything runs.
 */
class NqlChecker private constructor(
    private val params: List<Any?>,
) {
    private class Scope(
        val query: NqlQuery,
        val parent: Scope?,
    ) {
        /** How many of [query]'s sources the expression being checked may see (an ON sees those joined so far). */
        var visible = query.from.size
    }

    private class Ctx(
        val scope: Scope,
        /** Aggregates may appear here, and belong to [NqlQuery.aggregates] of [scope]'s query. */
        val aggregates: MutableList<NqlCall>?,
    )

    private fun invalid(
        what: String,
        pos: Int,
    ): Nothing = throw SqlException.invalid(what, pos)

    private fun query(
        q: NqlQuery,
        parent: Scope?,
        needsNames: Boolean,
    ) {
        q.depth = (parent?.query?.depth ?: -1) + 1
        val scope = Scope(q, parent)

        // FROM, left to right: each ON sees the sources joined so far.
        val names = HashSet<String>()
        q.from.forEachIndexed { i, src ->
            if (!names.add(src.name)) invalid("two sources are named ${src.name}", src.pos)
            src.columns =
                if (src.subquery != null) {
                    // A subquery in FROM sees the enclosing queries, not its siblings.
                    query(src.subquery, parent, needsNames = true)
                    src.subquery.outputs.map { it.name!! to it.type }
                } else {
                    SqlProfile.TABLES[src.table] ?: invalid("no source named ${src.table}", src.pos)
                }
            if (src.on != null) {
                scope.visible = i + 1
                condition(src.on, Ctx(scope, null), "ON")
            }
        }
        scope.visible = q.from.size

        q.where?.let { condition(it, Ctx(scope, null), "WHERE") }

        val aggregates = ArrayList<NqlCall>()
        val ctx = Ctx(scope, aggregates)

        // Result columns.
        val outputs = ArrayList<NqlOutput>()
        if (q.star) {
            q.from.forEachIndexed { s, src ->
                src.columns.forEachIndexed { c, (name, type) ->
                    val ref = NqlColumnRef(src.name, name, q.pos)
                    ref.up = 0
                    ref.source = s
                    ref.index = c
                    ref.owner = q
                    ref.type = type
                    outputs.add(NqlOutput(ref, name, type))
                }
            }
            if (outputs.isEmpty()) invalid("SELECT * needs FROM", q.pos)
        } else {
            for (r in q.results) {
                val t = expr(r.expr, ctx)
                val name = r.alias?.lowercase() ?: (r.expr as? NqlColumnRef)?.column?.lowercase()
                if (name == null && needsNames) invalid("this result column needs an AS name", r.pos)
                outputs.add(NqlOutput(r.expr, name, t))
            }
        }
        if (needsNames) {
            val seen = HashSet<String>()
            outputs.forEach { if (!seen.add(it.name!!)) invalid("two result columns are named ${it.name}", q.pos) }
        }
        q.outputs = outputs

        // GROUP BY: an expression over the sources, or a result column's alias.
        for (term in q.groupBy) {
            val output = byName(term, q, scope)
            if (output >= 0) {
                val ref = term as NqlColumnRef
                ref.output = output
                ref.type = outputs[output].type
                if (outputs[output].expr.containsAggregate()) invalid("GROUP BY can't use an aggregate", term.pos)
            } else {
                expr(term, Ctx(scope, null))
            }
        }

        q.having?.let { condition(it, ctx, "HAVING") }

        for (term in q.orderBy) {
            val output = byName(term.expr, q, scope)
            if (output >= 0) {
                term.output = output
            } else {
                if (q.distinct) invalid("after DISTINCT, ORDER BY takes result columns only", term.expr.pos)
                expr(term.expr, ctx)
            }
            val t = if (output >= 0) outputs[output].type else term.expr.type
            if (t == BOOLEAN) invalid("can't order by a BOOLEAN", term.expr.pos)
        }

        q.aggregates = aggregates
        q.grouped = q.groupBy.isNotEmpty() || aggregates.isNotEmpty()
        if (q.grouped) grouping(q)

        q.limit?.let { count(it) }
        q.offset?.let { count(it) }

        // An untyped NULL result is TEXT.
        q.outputs = outputs.map { if (it.type == NULL) NqlOutput(it.expr, it.name, TEXT) else it }
    }

    /**
     * The result column a bare name in GROUP BY / ORDER BY stands for, or -1.
     * A name that is also a column of this query's sources must name that same column.
     */
    private fun byName(
        e: NqlExpr,
        q: NqlQuery,
        scope: Scope,
    ): Int {
        // SQL reads an integer here as a column position; NQL has none.
        if (e is NqlLiteral && e.value is Long) invalid("GROUP BY and ORDER BY take names or expressions, not positions", e.pos)
        if (e !is NqlColumnRef || e.table != null) return -1
        val name = e.column.lowercase()
        val output = q.outputs.indexOfFirst { it.name == name }
        if (output < 0) return -1
        val sources = q.from.withIndex().filter { (_, s) -> s.columns.any { it.first == name } }
        if (sources.isEmpty()) return output
        val o = q.outputs[output].expr
        val same =
            sources.size == 1 && o is NqlColumnRef && o.output < 0 && o.up == 0 && o.source == sources[0].index && o.owner == q &&
                q.from[o.source].columns[o.index].first == name
        if (!same) invalid("$name is both a result column and a source column", e.pos)
        return output
    }

    private fun count(c: NqlCount) {
        if (c.param != null) {
            val v = params.getOrNull(c.param)
            if (v !is Long || v < 0) invalid("LIMIT and OFFSET take a non-negative integer", c.pos)
        }
    }

    private fun condition(
        e: NqlExpr,
        ctx: Ctx,
        where: String,
    ) {
        val t = expr(e, ctx)
        if (t != BOOLEAN && t != NULL) invalid("$where must be BOOLEAN", e.pos)
        adopt(e, BOOLEAN)
    }

    /** Gives an untyped NULL the type its context calls for. */
    private fun adopt(
        e: NqlExpr,
        t: NqlType,
    ) {
        if (e.type != NULL || t == NULL) return
        e.type = t
        when (e) {
            is NqlCase -> {
                e.whens.forEach { adopt(it.second, t) }
                e.elseExpr?.let { adopt(it, t) }
            }

            is NqlCall -> {
                if (e.name == "coalesce" || e.name == "nullif") e.args.forEach { adopt(it, t) }
            }

            else -> {}
        }
    }

    private fun comparable(
        a: NqlType,
        b: NqlType,
    ) = a == NULL || b == NULL || (a.isNumeric && b.isNumeric) || a == b

    /** The one type all of [es] share (NULLs adopting it), or invalid. */
    private fun common(
        es: List<NqlExpr>,
        pos: Int,
    ): NqlType {
        val known = es.map { it.type }.filter { it != NULL }.distinct()
        if (known.size > 1) invalid("these values must have the same type, found ${known.joinToString()}", pos)
        val t = known.singleOrNull() ?: NULL
        es.forEach { adopt(it, t) }
        return t
    }

    private fun numeric(
        e: NqlExpr,
        what: String,
    ) {
        if (e.type != NULL && !e.type.isNumeric) invalid("$what needs a number, found ${e.type}", e.pos)
    }

    private fun require(
        e: NqlExpr,
        t: NqlType,
        what: String,
    ) {
        if (e.type != NULL && e.type != t) invalid("$what needs $t, found ${e.type}", e.pos)
        adopt(e, t)
    }

    private fun expr(
        e: NqlExpr,
        ctx: Ctx,
    ): NqlType {
        val t = typeOf(e, ctx)
        e.type = t
        return t
    }

    private fun typeOf(
        e: NqlExpr,
        ctx: Ctx,
    ): NqlType =
        when (e) {
            is NqlLiteral -> {
                when (e.value) {
                    null -> NULL
                    is Long -> INTEGER
                    is Double -> REAL
                    is String -> TEXT
                    is Boolean -> BOOLEAN
                    else -> invalid("unexpected literal", e.pos)
                }
            }

            is NqlParam -> {
                when (params.getOrElse(e.index) { invalid("no value for this ?", e.pos) }) {
                    null -> NULL
                    is Long -> INTEGER
                    is Double -> REAL
                    is String -> TEXT
                    is Boolean -> BOOLEAN
                    else -> invalid("parameters are strings, numbers, booleans or null", e.pos)
                }
            }

            is NqlColumnRef -> {
                resolve(e, ctx.scope)
            }

            is NqlNegate -> {
                expr(e.operand, ctx)
                numeric(e.operand, "-")
                adopt(e.operand, INTEGER)
                e.operand.type
            }

            is NqlNot -> {
                expr(e.operand, ctx)
                require(e.operand, BOOLEAN, "NOT")
                BOOLEAN
            }

            is NqlBinary -> {
                binary(e, ctx)
            }

            is NqlIsNull -> {
                expr(e.expr, ctx)
                BOOLEAN
            }

            is NqlBetween -> {
                val all = listOf(e.expr, e.low, e.high)
                all.forEach { expr(it, ctx) }
                val known = all.map { it.type }.filter { it != NULL }
                val ok = known.all { it.isNumeric } || known.all { it == TEXT }
                if (!ok) invalid("BETWEEN compares numbers or TEXT, found ${known.joinToString()}", e.pos)
                val t =
                    if (known.isEmpty()) {
                        TEXT
                    } else if (known.any { it == REAL }) {
                        REAL
                    } else {
                        known.first()
                    }
                all.forEach { adopt(it, t) }
                BOOLEAN
            }

            is NqlInList -> {
                expr(e.expr, ctx)
                e.items.forEach { expr(it, ctx) }
                val all = listOf(e.expr) + e.items
                val known = all.map { it.type }.filter { it != NULL }
                if (!(known.all { it.isNumeric } || known.distinct().size <= 1)) invalid("IN compares values of different types: ${known.distinct().joinToString()}", e.pos)
                val t =
                    if (known.isEmpty()) {
                        TEXT
                    } else if (known.any { it == REAL }) {
                        REAL
                    } else {
                        known.first()
                    }
                all.forEach { adopt(it, t) }
                BOOLEAN
            }

            is NqlInQuery -> {
                val x = expr(e.expr, ctx)
                query(e.query, ctx.scope, needsNames = false)
                if (e.query.outputs.size != 1) invalid("IN takes a query with one column", e.pos)
                val y = e.query.outputs[0].type
                if (!comparable(x, y)) invalid("IN compares $x with $y", e.pos)
                adopt(e.expr, y)
                BOOLEAN
            }

            is NqlLike -> {
                expr(e.expr, ctx)
                expr(e.pattern, ctx)
                require(e.expr, TEXT, "LIKE")
                require(e.pattern, TEXT, "LIKE")
                BOOLEAN
            }

            is NqlCase -> {
                e.whens.forEach { (c, r) ->
                    expr(c, ctx)
                    require(c, BOOLEAN, "WHEN")
                    expr(r, ctx)
                }
                e.elseExpr?.let { expr(it, ctx) }
                common(e.whens.map { it.second } + listOfNotNull(e.elseExpr), e.pos)
            }

            is NqlCast -> {
                val s = expr(e.expr, ctx)
                val ok =
                    when (s) {
                        NULL, INTEGER, TEXT -> true
                        REAL -> e.target != TEXT
                        BOOLEAN -> e.target == INTEGER
                    }
                if (!ok) invalid("can't CAST $s to ${e.target}", e.pos)
                e.target
            }

            is NqlCall -> {
                call(e, ctx)
            }

            is NqlExists -> {
                query(e.query, ctx.scope, needsNames = false)
                BOOLEAN
            }

            is NqlScalarSubquery -> {
                query(e.query, ctx.scope, needsNames = false)
                if (e.query.outputs.size != 1) invalid("a subquery used as a value must have one column", e.pos)
                e.query.outputs[0].type
            }
        }

    private fun binary(
        e: NqlBinary,
        ctx: Ctx,
    ): NqlType {
        val a = expr(e.left, ctx)
        val b = expr(e.right, ctx)
        return when (e.op) {
            "+", "-", "*", "/" -> {
                numeric(e.left, e.op)
                numeric(e.right, e.op)
                val t = if (a == REAL || b == REAL) REAL else INTEGER
                adopt(e.left, if (b == NULL) INTEGER else b)
                adopt(e.right, if (a == NULL) INTEGER else a)
                t
            }

            "%" -> {
                require(e.left, INTEGER, "%")
                require(e.right, INTEGER, "%")
                INTEGER
            }

            "||" -> {
                require(e.left, TEXT, "||")
                require(e.right, TEXT, "||")
                TEXT
            }

            "AND", "OR" -> {
                require(e.left, BOOLEAN, e.op)
                require(e.right, BOOLEAN, e.op)
                BOOLEAN
            }

            else -> {
                if (!comparable(a, b)) invalid("can't compare $a with $b", e.pos)
                val t =
                    if (a != NULL) {
                        a
                    } else if (b != NULL) {
                        b
                    } else {
                        TEXT
                    }
                if (e.op != "=" && e.op != "<>" && t == BOOLEAN) invalid("BOOLEAN supports only = and <>", e.pos)
                adopt(e.left, t)
                adopt(e.right, t)
                BOOLEAN
            }
        }
    }

    private fun call(
        e: NqlCall,
        ctx: Ctx,
    ): NqlType {
        if (e.name !in SqlProfile.FUNCTIONS) invalid("no function named ${e.name}", e.pos)
        if (e.star && e.name != "count") invalid("only count takes *", e.pos)
        if (e.distinct && e.name != "count") invalid("only count takes DISTINCT", e.pos)

        if (e.name in SqlProfile.AGGREGATES) {
            val list = ctx.aggregates ?: invalid("aggregates are allowed only in result columns, HAVING and ORDER BY", e.pos)
            // Arguments are checked where no aggregate may appear.
            e.args.forEach { expr(it, Ctx(ctx.scope, null)) }
            e.aggregateSlot = list.size
            list.add(e)
            if (!e.star && e.args.size != 1) invalid("${e.name} takes one argument", e.pos)
            val x = e.args.firstOrNull()
            return when (e.name) {
                "count" -> {
                    INTEGER
                }

                "sum" -> {
                    numeric(x!!, "sum")
                    adopt(x, INTEGER)
                    x.type
                }

                "avg" -> {
                    numeric(x!!, "avg")
                    adopt(x, INTEGER)
                    REAL
                }

                else -> {
                    if (x!!.type != NULL && !x.type.isNumeric && x.type != TEXT) invalid("${e.name} needs a number or TEXT", x.pos)
                    adopt(x, TEXT)
                    x.type
                }
            }
        }

        e.args.forEach { expr(it, ctx) }
        val args = e.args

        fun arity(vararg counts: Int) {
            if (args.size !in counts) invalid("${e.name} takes ${counts.joinToString(" or ")} arguments", e.pos)
        }

        fun text(i: Int) = require(args[i], TEXT, e.name)

        fun integer(i: Int) = require(args[i], INTEGER, e.name)

        fun number(i: Int) {
            numeric(args[i], e.name)
            adopt(args[i], REAL)
        }

        return when (e.name) {
            "length" -> {
                arity(1)
                text(0)
                INTEGER
            }

            "lower", "upper" -> {
                arity(1)
                text(0)
                TEXT
            }

            "trim", "ltrim", "rtrim" -> {
                arity(1, 2)
                args.indices.forEach(::text)
                TEXT
            }

            "replace" -> {
                arity(3)
                args.indices.forEach(::text)
                TEXT
            }

            "instr" -> {
                arity(2)
                text(0)
                text(1)
                INTEGER
            }

            "substr" -> {
                arity(2, 3)
                text(0)
                (1 until args.size).forEach(::integer)
                TEXT
            }

            "coalesce" -> {
                if (args.size < 2) invalid("coalesce takes 2 or more arguments", e.pos)
                common(args, e.pos)
            }

            "nullif" -> {
                arity(2)
                common(args, e.pos)
            }

            "abs" -> {
                arity(1)
                numeric(args[0], "abs")
                adopt(args[0], INTEGER)
                args[0].type
            }

            "pow" -> {
                arity(2)
                number(0)
                number(1)
                REAL
            }

            else -> {
                // round, ceil, floor, trunc, sqrt, exp, ln, log10
                arity(1)
                number(0)
                REAL
            }
        }
    }

    private fun resolve(
        e: NqlColumnRef,
        start: Scope,
    ): NqlType {
        val table = e.table?.lowercase()
        val column = e.column.lowercase()
        var scope: Scope? = start
        var up = 0
        while (scope != null) {
            val sources = scope.query.from.take(scope.visible)
            if (table != null) {
                val s = sources.indexOfFirst { it.name == table }
                if (s >= 0) {
                    val c = sources[s].columns.indexOfFirst { it.first == column }
                    if (c < 0) invalid("$table has no column $column", e.pos)
                    return bind(e, scope.query, up, s, c, sources[s].columns[c].second)
                }
            } else {
                val matches = sources.withIndex().filter { (_, src) -> src.columns.any { it.first == column } }
                if (matches.size > 1) invalid("$column is ambiguous: qualify it with a source name", e.pos)
                if (matches.size == 1) {
                    val (s, src) = matches[0]
                    val c = src.columns.indexOfFirst { it.first == column }
                    return bind(e, scope.query, up, s, c, src.columns[c].second)
                }
            }
            scope = scope.parent
            up++
        }
        if (table != null) invalid("no source named $table", e.pos)
        invalid("no column named $column", e.pos)
    }

    private fun bind(
        e: NqlColumnRef,
        owner: NqlQuery,
        up: Int,
        source: Int,
        index: Int,
        type: NqlType,
    ): NqlType {
        e.owner = owner
        e.up = up
        e.source = source
        e.index = index
        return type
    }

    /**
     * A grouped query's result columns, HAVING and ORDER BY may use its own
     * source columns only inside an expression written the same as a GROUP BY
     * term, or as a column that is a GROUP BY term.
     */
    private fun grouping(q: NqlQuery) {
        val terms = q.groupBy.map { if (it is NqlColumnRef && it.output >= 0) q.outputs[it.output].expr else it }

        fun groupedColumn(ref: NqlColumnRef) = terms.any { it is NqlColumnRef && it.output < 0 && it.owner == ref.owner && it.source == ref.source && it.index == ref.index }

        fun inSubquery(sub: NqlQuery) {
            sub.walkColumns { ref -> if (ref.owner == q && ref.output < 0 && !groupedColumn(ref)) invalid("${ref.column} must appear in GROUP BY", ref.pos) }
        }

        fun check(e: NqlExpr) {
            if (terms.any { same(it, e) }) return
            when (e) {
                is NqlCall -> {
                    if (e.aggregateSlot >= 0 && q.aggregates.getOrNull(e.aggregateSlot) === e) {
                        // An aggregate's arguments may read any row; its subqueries too.
                        return
                    }
                    e.args.forEach(::check)
                }

                is NqlColumnRef -> {
                    if (e.owner == q && e.output < 0 && !groupedColumn(e)) invalid("${e.column} must appear in GROUP BY or inside an aggregate", e.pos)
                }

                is NqlExists -> {
                    inSubquery(e.query)
                }

                is NqlScalarSubquery -> {
                    inSubquery(e.query)
                }

                is NqlInQuery -> {
                    check(e.expr)
                    inSubquery(e.query)
                }

                else -> {
                    e.children().forEach(::check)
                }
            }
        }

        q.outputs.forEach { check(it.expr) }
        q.having?.let(::check)
        q.orderBy.forEach { if (it.output < 0) check(it.expr) }
    }

    companion object {
        /**
         * Parses and checks [text] with [params] (`Long`, `Double`, `String`,
         * `Boolean` or null, one per `?`). Throws [SqlException] `invalid`.
         */
        fun check(
            text: String,
            params: List<Any?>,
        ): NqlQuery {
            val (q, count) = NqlParser.parse(text)
            if (count != params.size) throw SqlException.invalid("the query has $count parameters, ${params.size} were given")
            NqlChecker(params).query(q, null, needsNames = true)
            return q
        }

        /** Two expressions written the same (names resolved, case and whitespace ignored). */
        internal fun same(
            a: NqlExpr,
            b: NqlExpr,
        ): Boolean {
            if (a === b) return true
            return when (a) {
                is NqlLiteral -> b is NqlLiteral && a.value == b.value
                is NqlParam -> b is NqlParam && a.index == b.index
                is NqlColumnRef -> b is NqlColumnRef && a.output < 0 && b.output < 0 && a.owner == b.owner && a.source == b.source && a.index == b.index
                is NqlNegate -> b is NqlNegate && same(a.operand, b.operand)
                is NqlNot -> b is NqlNot && same(a.operand, b.operand)
                is NqlBinary -> b is NqlBinary && a.op == b.op && same(a.left, b.left) && same(a.right, b.right)
                is NqlIsNull -> b is NqlIsNull && a.not == b.not && same(a.expr, b.expr)
                is NqlBetween -> b is NqlBetween && a.not == b.not && same(a.expr, b.expr) && same(a.low, b.low) && same(a.high, b.high)
                is NqlInList -> b is NqlInList && a.not == b.not && same(a.expr, b.expr) && a.items.size == b.items.size && a.items.indices.all { same(a.items[it], b.items[it]) }
                is NqlLike -> b is NqlLike && a.not == b.not && same(a.expr, b.expr) && same(a.pattern, b.pattern)
                is NqlCase ->
                    b is NqlCase && a.whens.size == b.whens.size &&
                        a.whens.indices.all { same(a.whens[it].first, b.whens[it].first) && same(a.whens[it].second, b.whens[it].second) } &&
                        (a.elseExpr == null) == (b.elseExpr == null) && (a.elseExpr == null || same(a.elseExpr, b.elseExpr!!))
                is NqlCast -> b is NqlCast && a.target == b.target && same(a.expr, b.expr)
                is NqlCall -> b is NqlCall && a.name == b.name && a.distinct == b.distinct && a.star == b.star && a.args.size == b.args.size && a.args.indices.all { same(a.args[it], b.args[it]) }
                else -> false
            }
        }
    }
}

/** The direct sub-expressions of this one (subqueries excluded). */
internal fun NqlExpr.children(): List<NqlExpr> =
    when (this) {
        is NqlLiteral, is NqlParam, is NqlColumnRef, is NqlExists, is NqlScalarSubquery -> emptyList()
        is NqlNegate -> listOf(operand)
        is NqlNot -> listOf(operand)
        is NqlBinary -> listOf(left, right)
        is NqlIsNull -> listOf(expr)
        is NqlBetween -> listOf(expr, low, high)
        is NqlInList -> listOf(expr) + items
        is NqlInQuery -> listOf(expr)
        is NqlLike -> listOf(expr, pattern)
        is NqlCase -> whens.flatMap { listOf(it.first, it.second) } + listOfNotNull(elseExpr)
        is NqlCast -> listOf(expr)
        is NqlCall -> args
    }

/** The subqueries directly inside this expression. */
internal fun NqlExpr.subqueries(): List<NqlQuery> =
    when (this) {
        is NqlExists -> listOf(query)
        is NqlScalarSubquery -> listOf(query)
        is NqlInQuery -> listOf(query)
        else -> emptyList()
    }

/** True if this expression holds an aggregate call outside its subqueries. */
internal fun NqlExpr.containsAggregate(): Boolean = (this is NqlCall && name in SqlProfile.AGGREGATES) || children().any { it.containsAggregate() }

/** Every expression of this query, subqueries excluded: results, FROM's ON, WHERE, GROUP BY, HAVING, ORDER BY. */
internal fun NqlQuery.expressions(): List<NqlExpr> =
    (if (star) outputs.map { it.expr } else results.map { it.expr }) +
        from.mapNotNull { it.on } + listOfNotNull(where) + groupBy + listOfNotNull(having) + orderBy.map { it.expr }

/** Every subquery nested in this query, at any depth, this one included. */
internal fun NqlQuery.walkQueries(visit: (NqlQuery) -> Unit) {
    visit(this)
    from.forEach { it.subquery?.walkQueries(visit) }

    fun expr(e: NqlExpr) {
        e.subqueries().forEach { it.walkQueries(visit) }
        e.children().forEach(::expr)
    }
    expressions().forEach(::expr)
}

/** Every column reference in this query and its subqueries. */
internal fun NqlQuery.walkColumns(visit: (NqlColumnRef) -> Unit) {
    walkQueries { q ->
        fun expr(e: NqlExpr) {
            if (e is NqlColumnRef) visit(e)
            e.children().forEach(::expr)
        }
        q.expressions().forEach(::expr)
    }
}

/** The AND-ed terms of [e]. */
internal fun conjuncts(e: NqlExpr?): List<NqlExpr> =
    when {
        e == null -> emptyList()
        e is NqlBinary && e.op == "AND" -> conjuncts(e.left) + conjuncts(e.right)
        else -> listOf(e)
    }

/** The sources of this query that [e] reads (subqueries included). */
internal fun NqlQuery.sourcesOf(e: NqlExpr): Set<Int> {
    val q = this
    val out = HashSet<Int>()

    fun expr(x: NqlExpr) {
        if (x is NqlColumnRef && x.owner === q && x.output < 0) out.add(x.source)
        x.subqueries().forEach { sub -> sub.walkColumns { if (it.owner === q && it.output < 0) out.add(it.source) } }
        x.children().forEach(::expr)
    }
    expr(e)
    return out
}

/**
 * The predicates each source's rows must satisfy on their own: the WHERE terms
 * that read only that source (none for the right side of a LEFT JOIN, whose
 * rows WHERE sees NULL-extended), and the terms of its own ON that do.
 */
internal fun NqlQuery.localPredicates(): Array<List<NqlExpr>> {
    val where = conjuncts(where)
    return Array(from.size) { i ->
        val fromWhere = if (from[i].join == NqlJoin.LEFT) emptyList() else where.filter { sourcesOf(it) == setOf(i) }
        fromWhere + conjuncts(from[i].on).filter { sourcesOf(it) == setOf(i) }
    }
}
