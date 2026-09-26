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

/*
 * Syntax tree of an NQL (NIP-FF) query. The parser builds it; the checker
 * ([NqlChecker]) resolves every name and fills in the static types and the
 * slots the executor ([NqlExecutor]) reads. Positions ([pos]) point back into
 * the query text for error messages.
 */

/** NQL's value types. [NULL] is only the type of an untyped `NULL` until its context gives it one. */
enum class NqlType {
    INTEGER,
    REAL,
    TEXT,
    BOOLEAN,
    NULL,
    ;

    val isNumeric get() = this == INTEGER || this == REAL
}

// ---- Query -----------------------------------------------------------------

class NqlQuery(
    val distinct: Boolean,
    /** Empty for `SELECT *`. */
    val results: List<NqlResultColumn>,
    val star: Boolean,
    val from: List<NqlSource>,
    val where: NqlExpr?,
    val groupBy: List<NqlExpr>,
    val having: NqlExpr?,
    val orderBy: List<NqlOrder>,
    val limit: NqlCount?,
    val offset: NqlCount?,
    val pos: Int,
) {
    // ---- Filled by the checker.

    /** The result columns, `*` expanded: expression, name (null when unnamed) and type. */
    var outputs: List<NqlOutput> = emptyList()

    /** Grouped: has GROUP BY or an aggregate in its results, HAVING or ORDER BY. */
    var grouped = false

    /** The aggregates this query computes, each with [NqlCall.aggregateSlot] set to its index. */
    var aggregates: List<NqlCall> = emptyList()

    /** Nesting depth from the outermost query (0). */
    var depth = 0
}

class NqlResultColumn(
    val expr: NqlExpr,
    /** As written; the result name is its lower case. */
    val alias: String?,
    val pos: Int,
)

class NqlOutput(
    val expr: NqlExpr,
    val name: String?,
    val type: NqlType,
)

/** One `FROM` entry; the first one has [join] null. */
class NqlSource(
    /** `events` or `tags`, lower case; null for a subquery. */
    val table: String?,
    val subquery: NqlQuery?,
    val alias: String?,
    val join: NqlJoin?,
    val on: NqlExpr?,
    val pos: Int,
) {
    /** The name columns qualify with. */
    val name: String get() = (alias ?: table!!).lowercase()

    /** Filled by the checker: this source's columns and their types. */
    var columns: List<Pair<String, NqlType>> = emptyList()
}

enum class NqlJoin { INNER, LEFT }

class NqlOrder(
    val expr: NqlExpr,
    val descending: Boolean,
) {
    /** Filled by the checker: the 0-based result column this term sorts by, or -1 for an expression. */
    var output = -1
}

/** `LIMIT` / `OFFSET`: a literal, or the index of a `?`. */
class NqlCount(
    val literal: Long?,
    val param: Int?,
    val pos: Int,
)

// ---- Expressions -----------------------------------------------------------

sealed class NqlExpr(
    val pos: Int,
) {
    /** Filled by the checker. */
    var type: NqlType = NqlType.NULL
}

/** `Long`, `Double`, `String`, `Boolean` or null. */
class NqlLiteral(
    val value: Any?,
    pos: Int,
) : NqlExpr(pos)

/** The [index]-th `?` (0-based). */
class NqlParam(
    val index: Int,
    pos: Int,
) : NqlExpr(pos)

class NqlColumnRef(
    val table: String?,
    val column: String,
    pos: Int,
) : NqlExpr(pos) {
    // ---- Filled by the checker.

    /** How many queries out the source is: 0 for the query the reference is in. */
    var up = 0

    /** Which source of that query's FROM. */
    var source = 0

    /** Which column of that source. */
    var index = 0

    /** Set instead of the three above when the name is a result column of the same query (GROUP BY / ORDER BY by alias). */
    var output = -1

    /** The query that owns the source; identity for the executor. */
    var owner: NqlQuery? = null
}

class NqlNegate(
    val operand: NqlExpr,
    pos: Int,
) : NqlExpr(pos)

class NqlNot(
    val operand: NqlExpr,
    pos: Int,
) : NqlExpr(pos)

/** `+ - * / % || = <> < <= > >= AND OR`. */
class NqlBinary(
    val op: String,
    val left: NqlExpr,
    val right: NqlExpr,
    pos: Int,
) : NqlExpr(pos)

class NqlIsNull(
    val expr: NqlExpr,
    val not: Boolean,
    pos: Int,
) : NqlExpr(pos)

class NqlBetween(
    val expr: NqlExpr,
    val not: Boolean,
    val low: NqlExpr,
    val high: NqlExpr,
    pos: Int,
) : NqlExpr(pos)

class NqlInList(
    val expr: NqlExpr,
    val not: Boolean,
    val items: List<NqlExpr>,
    pos: Int,
) : NqlExpr(pos)

class NqlInQuery(
    val expr: NqlExpr,
    val not: Boolean,
    val query: NqlQuery,
    pos: Int,
) : NqlExpr(pos)

class NqlLike(
    val expr: NqlExpr,
    val not: Boolean,
    val pattern: NqlExpr,
    pos: Int,
) : NqlExpr(pos)

class NqlCase(
    val whens: List<Pair<NqlExpr, NqlExpr>>,
    val elseExpr: NqlExpr?,
    pos: Int,
) : NqlExpr(pos)

class NqlCast(
    val expr: NqlExpr,
    val target: NqlType,
    pos: Int,
) : NqlExpr(pos)

class NqlCall(
    /** Lower case. */
    val name: String,
    val distinct: Boolean,
    /** `count(*)`. */
    val star: Boolean,
    val args: List<NqlExpr>,
    pos: Int,
) : NqlExpr(pos) {
    /** Filled by the checker: the aggregate's index in its query's [NqlQuery.aggregates], or -1 for a scalar function. */
    var aggregateSlot = -1
}

class NqlExists(
    val query: NqlQuery,
    pos: Int,
) : NqlExpr(pos)

class NqlScalarSubquery(
    val query: NqlQuery,
    pos: Int,
) : NqlExpr(pos)
