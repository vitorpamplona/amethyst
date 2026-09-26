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

import com.vitorpamplona.quartz.nip01Core.tags.isIndexableTagName
import com.vitorpamplona.quartz.nipXXSql.NqlType.BOOLEAN
import com.vitorpamplona.quartz.nipXXSql.NqlType.INTEGER
import com.vitorpamplona.quartz.nipXXSql.NqlType.REAL
import com.vitorpamplona.quartz.nipXXSql.NqlType.TEXT

/** A checked NQL query as one SQLite statement: its text, its `?NNN` values in order, its result columns. */
class NqlSqliteQuery(
    val sql: String,
    val binds: List<Any?>,
    val columns: List<NqlColumn>,
)

/**
 * Compiles a checked NQL query ([NqlChecker]) to SQLite SQL over Quartz's
 * event store (`event_headers`), so SQLite runs it natively, next to its
 * indexes. SQLite already follows most of NQL (joins, grouping, three-valued
 * logic, code-point text order, ASCII case mapping); the rest is rewritten:
 *
 * - NQL's errors (overflow, underflow, division by zero, domain, a scalar
 *   subquery with two rows) become a guard whose failing branch is
 *   `abs(-9223372036854775808)`, which SQLite refuses with `integer overflow`.
 *   `CASE` evaluates only the branch it takes, so the error is raised exactly
 *   where NQL raises it. The caller reruns a failed query in the interpreter
 *   for the error's exact message (and for an INTEGER `sum` whose partial sums
 *   overflow in SQLite but whose total fits).
 * - INTEGER `+ - * /` overflow to REAL in SQLite and stay REAL through the rest
 *   of the chain, so one `typeof` check at the top of each chain catches them.
 * - `CAST` from TEXT, `round`, `substr`, `ceil` / `floor` / `trunc` and NULL
 *   placement get their NQL definitions in plain SQL.
 * - `LIKE` needs `PRAGMA case_sensitive_like = ON` on the connection.
 *
 * A guarded value used several times is bound once through a one-row
 * subquery (`let`), unless it's cheap to repeat or holds an aggregate.
 *
 * `tags` comes from `event_tag_values` when the store keeps it ([tagValues]):
 * rows indexed by name and value, with the event's `row_id` in place of its id
 * and pubkey, which are looked up only where read. An equality or `IN` on a
 * tag's `event_id` becomes one on `row_id`, so joins between tags and their
 * events (and lookups of one event's tags) walk the indexes. Otherwise `tags`
 * is unpacked from each event's tag JSON, and [tagHash] enables the
 * `event_tags` pushdown: a `tags` source whose conditions pin `t0` to a
 * single-letter name and `t1` to constants only expands the events that index
 * says hold such a tag.
 */
class NqlSqliteCompiler(
    private val params: List<Any?>,
    private val tagHash: ((name: String, value: String) -> Long)?,
    /** The store keeps `event_tag_values` ([com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy.indexTagValues]). */
    private val tagValues: Boolean = false,
) {
    private val binds = ArrayList<Any?>()
    private val queryIds = HashMap<NqlQuery, Int>()
    private var lets = 0

    fun compile(q: NqlQuery): NqlSqliteQuery {
        val sql = query(q)
        return NqlSqliteQuery(sql, binds, q.outputs.map { NqlColumn(it.name ?: "", it.type) })
    }

    private fun bind(v: Any?): String {
        if (v == null) return "NULL"
        binds.add(if (v is Boolean) (if (v) 1L else 0L) else v)
        return "?${binds.size}"
    }

    private fun alias(
        q: NqlQuery,
        source: Int,
    ) = "q${queryIds.getOrPut(q) { queryIds.size }}_$source"

    // ---- Queries ------------------------------------------------------------

    private fun query(q: NqlQuery): String {
        val sb = StringBuilder("SELECT ")
        if (q.distinct) sb.append("DISTINCT ")
        sb.append(q.outputs.withIndex().joinToString(", ") { (i, o) -> value(o.expr) + " AS c$i" })
        if (q.from.isNotEmpty()) {
            val local = q.localPredicates()
            sb.append(" FROM ")
            q.from.forEachIndexed { i, src ->
                if (i > 0) sb.append(if (src.join == NqlJoin.LEFT) " LEFT JOIN " else " JOIN ")
                sb.append(source(q, i, local[i]))
                if (i > 0) sb.append(" ON ").append(condition(src.on!!))
            }
        }
        q.where?.let { sb.append(" WHERE ").append(condition(it)) }
        if (q.groupBy.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(q.groupBy.joinToString(", ") { value(if (it is NqlColumnRef && it.output >= 0) q.outputs[it.output].expr else it) })
        }
        q.having?.let { sb.append(" HAVING ").append(value(it)) }
        if (q.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(
                q.orderBy.joinToString(", ") { t ->
                    (if (t.output >= 0) "${t.output + 1}" else value(t.expr)) + if (t.descending) " DESC NULLS FIRST" else " NULLS LAST"
                },
            )
        }
        q.limit?.let { sb.append(" LIMIT ").append(bind(it.literal ?: params[it.param!!])) }
        q.offset?.let { sb.append(" OFFSET ").append(bind(it.literal ?: params[it.param!!])) }
        return sb.toString()
    }

    private fun source(
        q: NqlQuery,
        i: Int,
        local: List<NqlExpr>,
    ): String {
        val src = q.from[i]
        val alias = alias(q, i)
        if (src.subquery != null) return "(${query(src.subquery)}) AS $alias"
        if (src.table == SqlProfile.EVENTS) return "event_headers AS $alias"
        if (tagValues) {
            val lookup = { column: String -> "(SELECT h.$column FROM event_headers AS h WHERE h.row_id = v.event_header_row_id)" }
            return "(SELECT v.event_header_row_id AS row_id, ${lookup("id")} AS event_id, v.idx AS idx, v.t0 AS t0, v.t1 AS t1, v.t2 AS t2, " +
                "v.t3 AS t3, v.t4 AS t4, v.created_at AS created_at, v.kind AS kind, ${lookup("pubkey")} AS pubkey FROM event_tag_values AS v) AS $alias"
        }

        // tags: one row per non-empty tag, from the stored tag JSON, over only the
        // events the source's own conditions can match.
        val spec =
            ScanAnalyzer(
                { e -> if (e is NqlColumnRef && e.owner === q && e.output < 0 && e.source == i) src.columns[e.index].first else null },
                { e ->
                    when (e) {
                        is NqlLiteral -> e.value
                        is NqlParam -> params[e.index]
                        else -> null
                    }
                },
            ).analyze(SqlProfile.TAGS, local)
        val where = ArrayList<String>()
        where.add("json_type(j.value, '$[0]') IS NOT NULL")
        spec.ids?.let { where.add("h.id IN (" + it.joinToString(", ") { v -> bind(v) } + ")") }
        spec.authors?.let { where.add("h.pubkey IN (" + it.joinToString(", ") { v -> bind(v) } + ")") }
        spec.kinds?.let { where.add("h.kind IN (" + it.joinToString(", ") { v -> bind(v.toLong()) } + ")") }
        spec.since?.let { where.add("h.created_at >= " + bind(it)) }
        spec.until?.let { where.add("h.created_at <= " + bind(it)) }
        val name = spec.tagName
        val values = spec.tagValues
        if (tagHash != null && name != null && values != null && isIndexableTagName(name)) {
            where.add("h.row_id IN (SELECT event_header_row_id FROM event_tags WHERE tag_hash IN (" + values.joinToString(", ") { bind(tagHash.invoke(name, it)) } + "))")
        }
        // json_extract measures faster than `->>` here.
        return "(SELECT h.id AS event_id, j.key AS idx, json_extract(j.value, '$[0]') AS t0, json_extract(j.value, '$[1]') AS t1, " +
            "json_extract(j.value, '$[2]') AS t2, json_extract(j.value, '$[3]') AS t3, json_extract(j.value, '$[4]') AS t4, " +
            "h.created_at AS created_at, h.kind AS kind, h.pubkey AS pubkey " +
            "FROM event_headers AS h, json_each(h.tags) AS j WHERE ${where.joinToString(" AND ")}) AS $alias"
    }

    // ---- Conditions ---------------------------------------------------------

    /**
     * A WHERE / ON condition. Where it is a conjunct, FALSE and NULL filter alike,
     * which lets an `event_id` test run on the events' `row_id` (see [rowTest]).
     */
    private fun condition(e: NqlExpr): String = conjuncts(e).joinToString(" AND ") { rowTest(it) ?: value(it) }

    /** `row_id` of the event a `tags.event_id` / `events.id` reference names, when the store has one; else null. */
    private fun rowOf(e: NqlExpr): String? {
        if (!tagValues || e !is NqlColumnRef || e.output >= 0) return null
        val src = e.owner!!.from[e.source]
        val column = src.columns[e.index].first
        val isId = (src.table == SqlProfile.TAGS && column == "event_id") || (src.table == SqlProfile.EVENTS && column == "id")
        return if (isId) "${alias(e.owner!!, e.source)}.row_id" else null
    }

    private fun isTagEventId(e: NqlExpr) = rowOf(e) != null && e is NqlColumnRef && e.owner!!.from[e.source].table == SqlProfile.TAGS

    /**
     * An equality or `IN` on a tag's `event_id` as one on `row_id`: between two
     * id columns directly (ids and row ids are one to one), against anything else
     * through the unique index on `event_headers.id`. Only in conjunct position:
     * an id no event has gives NULL here where the original gives FALSE.
     */
    private fun rowTest(e: NqlExpr): String? {
        if (!tagValues) return null
        return when {
            e is NqlBinary && e.op == "=" -> {
                val l = rowOf(e.left)
                val r = rowOf(e.right)
                when {
                    l != null && r != null -> "($l = $r)"
                    isTagEventId(e.left) -> "($l = (SELECT row_id FROM event_headers WHERE id = ${value(e.right)}))"
                    isTagEventId(e.right) -> "($r = (SELECT row_id FROM event_headers WHERE id = ${value(e.left)}))"
                    else -> null
                }
            }

            e is NqlInList && !e.not && isTagEventId(e.expr) -> {
                "(${rowOf(e.expr)} IN (SELECT row_id FROM event_headers WHERE id IN (" + e.items.joinToString(", ") { value(it) } + ")))"
            }

            e is NqlInQuery && !e.not && isTagEventId(e.expr) -> {
                "(${rowOf(e.expr)} IN (SELECT row_id FROM event_headers WHERE id IN (SELECT c0 FROM (${query(e.query)}))))"
            }

            else -> {
                null
            }
        }
    }

    // ---- Expressions --------------------------------------------------------

    /** Cheap to repeat: evaluating it twice costs about as much as reading a bound one. */
    private fun simple(e: NqlExpr) = e is NqlLiteral || e is NqlParam || e is NqlColumnRef

    /**
     * [body] over [args] each evaluated once: repeated as written when cheap
     * (or when it holds an aggregate, which can't move into a subquery), else
     * bound through a one-row subquery.
     */
    private fun let(
        args: List<Pair<String, NqlExpr>>,
        body: (List<String>) -> String,
    ): String {
        if (args.all { (_, e) -> simple(e) || e.containsAggregate() }) return "(" + body(args.map { "(${it.first})" }) + ")"
        val name = "l${lets++}"
        val columns = args.indices.map { "$name.v$it" }
        return "(SELECT " + body(columns) + " FROM (SELECT " + args.withIndex().joinToString(", ") { (i, a) -> "${a.first} AS v$i" } + ") AS $name)"
    }

    private fun let1(
        e: NqlExpr,
        body: (String) -> String,
    ) = let(listOf(value(e) to e)) { body(it[0]) }

    private fun isIntegerArithmetic(e: NqlExpr) = e.type == INTEGER && ((e is NqlBinary && e.op in INTEGER_CHAIN) || e is NqlNegate)

    /** [e] as SQL whose value is NQL's, errors included. */
    private fun value(e: NqlExpr): String {
        if (!isIntegerArithmetic(e)) return expr(e)
        // SQLite turns an overflowing INTEGER result into a REAL, which stays REAL up the chain.
        val chain = chain(e)
        return "(CASE typeof($chain) WHEN 'real' THEN $RAISE ELSE $chain END)"
    }

    /** An INTEGER `+ - * /` chain, unguarded: its top checks it once. */
    private fun chain(e: NqlExpr): String {
        fun operand(x: NqlExpr) = if (isIntegerArithmetic(x)) chain(x) else value(x)
        return when (e) {
            is NqlNegate -> {
                "(- ${operand(e.operand)})"
            }

            is NqlBinary -> {
                val l = operand(e.left)
                val r = operand(e.right)
                if (e.op == "/") "(CASE WHEN $r = 0 THEN $RAISE ELSE $l / $r END)" else "($l ${e.op} $r)"
            }

            else -> {
                value(e)
            }
        }
    }

    /** [x] as REAL when it is an INTEGER compared with a REAL: NQL compares after promotion. */
    private fun promoted(
        x: NqlExpr,
        other: NqlType,
    ) = if (x.type == INTEGER && other == REAL) "CAST(${value(x)} AS REAL)" else value(x)

    private fun expr(e: NqlExpr): String =
        when (e) {
            is NqlLiteral -> {
                bind(e.value)
            }

            is NqlParam -> {
                bind(params[e.index])
            }

            is NqlColumnRef -> {
                column(e)
            }

            is NqlNegate -> {
                "(- ${value(e.operand)})"
            }

            is NqlNot -> {
                "(NOT ${value(e.operand)})"
            }

            is NqlBinary -> {
                binary(e)
            }

            is NqlIsNull -> {
                "(${value(e.expr)} IS ${if (e.not) "NOT " else ""}NULL)"
            }

            is NqlBetween -> {
                val t = listOf(e.expr, e.low, e.high).map { it.type }.let { if (REAL in it) REAL else it.first() }
                "(${promoted(e.expr, t)} ${if (e.not) "NOT " else ""}BETWEEN ${promoted(e.low, t)} AND ${promoted(e.high, t)})"
            }

            is NqlInList -> {
                val t = (e.items + e.expr).map { it.type }.let { if (REAL in it) REAL else e.expr.type }
                "(${promoted(e.expr, t)} ${if (e.not) "NOT " else ""}IN (" + e.items.joinToString(", ") { promoted(it, t) } + "))"
            }

            is NqlInQuery -> {
                "(${value(e.expr)} ${if (e.not) "NOT " else ""}IN (SELECT c0 FROM (${query(e.query)})))"
            }

            is NqlLike -> {
                "(${value(e.expr)} ${if (e.not) "NOT " else ""}LIKE ${value(e.pattern)})"
            }

            is NqlCase -> {
                "(CASE " + e.whens.joinToString(" ") { (c, v) -> "WHEN ${value(c)} THEN ${value(v)}" } +
                    (e.elseExpr?.let { " ELSE ${value(it)}" } ?: "") + " END)"
            }

            is NqlCast -> {
                cast(e)
            }

            is NqlCall -> {
                call(e)
            }

            is NqlExists -> {
                "(EXISTS (${query(e.query)}))"
            }

            is NqlScalarSubquery -> {
                // Two rows are an error; one, its value; none, NULL.
                "(SELECT CASE WHEN count(*) > 1 THEN $RAISE ELSE max(c0) END FROM (SELECT c0 FROM (${query(e.query)}) LIMIT 2))"
            }
        }

    private fun column(e: NqlColumnRef): String {
        val owner = e.owner!!
        val src = owner.from[e.source]
        val name = if (src.subquery != null) "c${e.index}" else src.columns[e.index].first
        return "${alias(owner, e.source)}.$name"
    }

    private fun binary(e: NqlBinary): String =
        when (e.op) {
            "AND", "OR", "||" -> {
                "(${value(e.left)} ${e.op} ${value(e.right)})"
            }

            "%" -> {
                val l = value(e.left)
                val r = value(e.right)
                "(CASE WHEN $r = 0 THEN $RAISE ELSE $l % $r END)"
            }

            "+", "-", "*", "/" -> {
                real(e)
            }

            else -> {
                val t = if (e.left.type == REAL || e.right.type == REAL) REAL else e.left.type
                "(${promoted(e.left, t)} ${e.op} ${promoted(e.right, t)})"
            }
        }

    /** REAL `+ - * /`: too large is an error, and so is rounding to zero something that isn't. */
    private fun real(e: NqlBinary): String =
        let(listOf(value(e.left) to e.left, value(e.right) to e.right)) { (a, b) ->
            val r = "($a ${e.op} $b)"
            val overflow = "WHEN abs($r) > $MAX_REAL THEN $RAISE"
            when (e.op) {
                "*" -> "CASE $overflow WHEN $r = 0 AND $a <> 0 AND $b <> 0 THEN $RAISE ELSE $r END"
                "/" -> "CASE WHEN $b = 0 THEN $RAISE $overflow WHEN $r = 0 AND $a <> 0 THEN $RAISE ELSE $r END"
                else -> "CASE $overflow ELSE $r END"
            }
        }

    private fun cast(e: NqlCast): String {
        val from = e.expr.type
        return when {
            from == e.target || from == NqlType.NULL -> {
                "CAST(${value(e.expr)} AS ${sqliteType(e.target)})"
            }

            from == BOOLEAN || (from == INTEGER && e.target != TEXT) -> {
                "CAST(${value(e.expr)} AS ${sqliteType(e.target)})"
            }

            from == INTEGER -> {
                "CAST(${value(e.expr)} AS TEXT)"
            }

            from == REAL -> {
                // Truncated toward zero, NULL when that doesn't fit.
                let1(e.expr) { v -> "CASE WHEN $v >= -9223372036854775808.0 AND $v < 9223372036854775808.0 THEN CAST($v AS INTEGER) END" }
            }

            e.target == INTEGER -> {
                let1(e.expr) { v -> textToInteger(v) }
            }

            else -> {
                let1(e.expr) { v -> textToReal(v) }
            }
        }
    }

    /** The whole text is an optionally signed run of digits whose value fits, or NULL. */
    private fun textToInteger(v: String): String {
        fun fits(
            digits: String,
            negative: String,
        ): String {
            val d = "ltrim($digits, '0')"
            return "(length($d) < 19 OR (length($d) = 19 AND ($d <= '9223372036854775807' OR ($negative AND $d = '9223372036854775808'))))"
        }
        return "CASE WHEN $v GLOB '[0-9]*' AND $v NOT GLOB '*[^0-9]*' AND ${fits(v, "0")} THEN CAST($v AS INTEGER) " +
            "WHEN $v GLOB '[+-][0-9]*' AND substr($v, 2) NOT GLOB '*[^0-9]*' AND ${fits("substr($v, 2)", "substr($v, 1, 1) = '-'")} THEN CAST($v AS INTEGER) END"
    }

    /**
     * The whole text is `[+-]digits[.digits][(e|E)[+-]digits]` whose value is finite
     * and, unless it is zero, doesn't round to zero; else NULL.
     */
    private fun textToReal(v: String): String {
        val u = "(CASE WHEN substr($v, 1, 1) IN ('+', '-') THEN substr($v, 2) ELSE $v END)"
        val e = "instr(lower($u), 'e')"
        val mantissa = "(CASE WHEN $e > 0 THEN substr($u, 1, $e - 1) ELSE $u END)"
        val exponent = "(CASE WHEN substr($u, $e + 1, 1) IN ('+', '-') THEN substr($u, $e + 2) ELSE substr($u, $e + 1) END)"
        val mantissaOk =
            "($mantissa GLOB '[0-9]*' AND ($mantissa NOT GLOB '*[^0-9]*' OR ($mantissa NOT GLOB '*[^0-9.]*' AND $mantissa NOT GLOB '*.*.*' " +
                "AND $mantissa GLOB '*[0-9]' AND $mantissa GLOB '[0-9]*.[0-9]*')))"
        val exponentOk = "($e = 0 OR ($exponent <> '' AND $exponent NOT GLOB '*[^0-9]*'))"
        val r = "CAST($v AS REAL)"
        return "CASE WHEN $mantissaOk AND $exponentOk AND abs($r) <= $MAX_REAL AND NOT ($r = 0 AND $mantissa GLOB '*[1-9]*') THEN $r END"
    }

    private fun sqliteType(t: NqlType) =
        when (t) {
            INTEGER -> "INTEGER"
            REAL -> "REAL"
            else -> "TEXT"
        }

    private fun call(e: NqlCall): String {
        val a = e.args
        if (e.aggregateSlot >= 0) {
            return when (e.name) {
                "count" -> {
                    when {
                        e.star -> "count(*)"
                        // Ids and row ids are one to one: count the ones that need no lookup.
                        e.distinct && isTagEventId(a[0]) -> "count(DISTINCT ${rowOf(a[0])})"
                        else -> "count(${if (e.distinct) "DISTINCT " else ""}${value(a[0])})"
                    }
                }

                "sum" -> {
                    val x = value(a[0])
                    if (a[0].type == REAL) {
                        "(CASE WHEN abs(sum($x)) > $MAX_REAL THEN $RAISE ELSE sum($x) END)"
                    } else {
                        // SQLite fails on any overflowing partial sum, NQL only on the final one: the caller reruns failures.
                        "sum($x)"
                    }
                }

                "avg" -> {
                    "(CASE WHEN abs(avg(${value(a[0])})) > $MAX_REAL THEN $RAISE ELSE avg(${value(a[0])}) END)"
                }

                else -> {
                    "${e.name}(${value(a[0])})"
                }
            }
        }
        return when (e.name) {
            "coalesce", "nullif", "length", "lower", "upper", "trim", "ltrim", "rtrim", "replace", "instr", "abs" -> {
                "${e.name}(" + a.joinToString(", ") { value(it) } + ")"
            }

            "substr" -> {
                if (a.size == 2) {
                    "substr(${value(a[0])}, max(${value(a[1])}, 1))"
                } else {
                    let(listOf(value(a[1]) to a[1], value(a[2]) to a[2])) { (s, n) ->
                        "CASE WHEN $n < 0 THEN NULL WHEN $s >= 1 THEN substr(${value(a[0])}, $s, $n) ELSE substr(${value(a[0])}, 1, max($s + $n - 1, 0)) END"
                    }
                }
            }

            "round" -> {
                // Ties to even; SQLite's own rounds ties away from zero and misreads 0.49999999999999994.
                let(listOf("CAST(${value(a[0])} AS REAL)" to a[0])) { (v) ->
                    "CASE WHEN $v - floor($v) < 0.5 THEN floor($v) WHEN $v - floor($v) > 0.5 THEN ceil($v) " +
                        "WHEN floor($v) - 2 * floor($v / 2) = 0 THEN floor($v) ELSE ceil($v) END"
                }
            }

            "ceil", "floor", "trunc" -> {
                "CAST(${e.name}(${value(a[0])}) AS REAL)"
            }

            "sqrt" -> {
                let1(a[0]) { v -> "CASE WHEN $v < 0 THEN $RAISE ELSE sqrt($v) END" }
            }

            "ln", "log10" -> {
                let1(a[0]) { v -> "CASE WHEN $v <= 0 THEN $RAISE ELSE ${e.name}($v) END" }
            }

            "exp" -> {
                let(listOf("exp(${value(a[0])})" to e)) { (r) -> "CASE WHEN $r = 0 OR abs($r) > $MAX_REAL THEN $RAISE ELSE $r END" }
            }

            "pow" -> {
                let(listOf("CAST(${value(a[0])} AS REAL)" to a[0], "CAST(${value(a[1])} AS REAL)" to a[1])) { (x, y) ->
                    val r = "pow($x, $y)"
                    "CASE WHEN $x = 0 AND $y < 0 THEN $RAISE WHEN $x < 0 AND $y <> floor($y) THEN $RAISE " +
                        "WHEN abs($r) > $MAX_REAL OR ($r = 0 AND $x <> 0) THEN $RAISE ELSE $r END"
                }
            }

            else -> {
                throw IllegalStateException("no SQLite spelling for ${e.name}")
            }
        }
    }

    companion object {
        /** SQLite refuses it with `integer overflow`: how the compiled query raises NQL's errors. */
        const val RAISE = "abs(-9223372036854775807 - 1)"

        /** The largest finite binary64. */
        const val MAX_REAL = "1.7976931348623157e308"

        private val INTEGER_CHAIN = setOf("+", "-", "*", "/")
    }
}
