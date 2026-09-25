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
 * Syntax tree of a Nostr SQL query. The relay never executes the client's
 * text: it parses into these nodes, checks table and function names, and
 * re-emits SQL from the tree ([SqlCompiler]). Positions ([pos]) point
 * back into the original text for error messages.
 */

// ---- Query -----------------------------------------------------------------

class Cte(
    val name: String,
    val columns: List<String>,
    val query: Query,
    val pos: Int,
)

class With(
    val recursive: Boolean,
    val ctes: List<Cte>,
)

enum class CompoundOp(
    val sql: String,
) {
    UNION("UNION"),
    UNION_ALL("UNION ALL"),
    INTERSECT("INTERSECT"),
    EXCEPT("EXCEPT"),
}

class OrderingTerm(
    val expr: Expr,
    val descending: Boolean,
    /** null = engine default (SQLite: NULLs first on ASC, last on DESC). */
    val nullsFirst: Boolean?,
)

/**
 * `[WITH …] core (op core)* [ORDER BY …] [LIMIT … [OFFSET …]]`.
 * SQLite evaluates compound operators left to right with equal
 * precedence, so [rest] is a flat, left-associative list.
 */
class Query(
    val with: With?,
    val first: SelectCore,
    val rest: List<Pair<CompoundOp, SelectCore>>,
    val orderBy: List<OrderingTerm>,
    val limit: Expr?,
    val offset: Expr?,
)

sealed interface SelectCore

class Select(
    val distinct: Boolean,
    val columns: List<ResultColumn>,
    val from: FromItem?,
    val where: Expr?,
    val groupBy: List<Expr>,
    val having: Expr?,
) : SelectCore

class Values(
    val rows: List<List<Expr>>,
) : SelectCore

sealed interface ResultColumn

class ExprColumn(
    val expr: Expr,
    val alias: String?,
    /** The expression exactly as written: SQLite names an un-aliased column after it. */
    val sourceText: String,
) : ResultColumn

/** `*` or `table.*`. */
class StarColumn(
    val table: String?,
) : ResultColumn

// ---- FROM ------------------------------------------------------------------

sealed interface FromItem

/** A named source: one of the profile's tables or a CTE in scope. */
class TableRef(
    val name: String,
    val alias: String?,
    val pos: Int,
) : FromItem

class SubqueryRef(
    val query: Query,
    val alias: String?,
) : FromItem

enum class JoinType(
    val sql: String,
) {
    INNER("JOIN"),
    LEFT("LEFT JOIN"),
    CROSS("CROSS JOIN"),

    /** `a, b` */
    COMMA(","),
}

class Join(
    val left: FromItem,
    val type: JoinType,
    val right: FromItem,
    val on: Expr?,
) : FromItem

// ---- Expressions -----------------------------------------------------------

sealed interface Expr {
    val pos: Int
}

enum class LiteralKind { NUMBER, STRING, NULL, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_DATE, CURRENT_TIME }

class Literal(
    val kind: LiteralKind,
    val text: String,
    override val pos: Int,
) : Expr

/** `?`, `?NNN` or `:name`, as written. */
class Param(
    val name: String,
    override val pos: Int,
) : Expr

class ColumnRef(
    val table: String?,
    val column: String,
    override val pos: Int,
) : Expr

class Unary(
    /** `-`, `+` or `NOT`. */
    val op: String,
    val operand: Expr,
    override val pos: Int,
) : Expr

class Binary(
    /** Normalized SQL operator: `OR`, `AND`, `=`, `<>`, `<`, `||`, `IS`, `IS NOT`, … */
    val op: String,
    val left: Expr,
    val right: Expr,
    override val pos: Int,
) : Expr

class Between(
    val expr: Expr,
    val not: Boolean,
    val low: Expr,
    val high: Expr,
    override val pos: Int,
) : Expr

class InList(
    val expr: Expr,
    val not: Boolean,
    val items: List<Expr>,
    override val pos: Int,
) : Expr

class InQuery(
    val expr: Expr,
    val not: Boolean,
    val query: Query,
    override val pos: Int,
) : Expr

/** `LIKE` / `GLOB`, with SQLite semantics (LIKE is ASCII case-insensitive, GLOB is case-sensitive). */
class Match(
    val expr: Expr,
    val not: Boolean,
    val op: String,
    val pattern: Expr,
    val escape: Expr?,
    override val pos: Int,
) : Expr

/** `x ISNULL` / `x NOTNULL` / `x IS [NOT] NULL` all normalize to this. */
class IsNull(
    val expr: Expr,
    val not: Boolean,
    override val pos: Int,
) : Expr

class Case(
    val operand: Expr?,
    val whens: List<Pair<Expr, Expr>>,
    val elseExpr: Expr?,
    override val pos: Int,
) : Expr

class Cast(
    val expr: Expr,
    /** One of [SqlProfile.CAST_TYPES], uppercased. */
    val type: String,
    override val pos: Int,
) : Expr

enum class FrameUnit { ROWS, RANGE, GROUPS }

enum class BoundKind(
    val sql: String,
) {
    UNBOUNDED_PRECEDING("UNBOUNDED PRECEDING"),
    PRECEDING("PRECEDING"),
    CURRENT_ROW("CURRENT ROW"),
    FOLLOWING("FOLLOWING"),
    UNBOUNDED_FOLLOWING("UNBOUNDED FOLLOWING"),
}

class FrameBound(
    val kind: BoundKind,
    /** Offset for [BoundKind.PRECEDING] / [BoundKind.FOLLOWING]. */
    val offset: Expr?,
)

class Frame(
    val unit: FrameUnit,
    val start: FrameBound,
    /** null for the short form `ROWS <start>`. */
    val end: FrameBound?,
)

class WindowSpec(
    val partitionBy: List<Expr>,
    val orderBy: List<OrderingTerm>,
    val frame: Frame?,
)

class FunctionCall(
    /** Lowercased. */
    val name: String,
    val distinct: Boolean,
    /** `count(*)` */
    val star: Boolean,
    val args: List<Expr>,
    val filter: Expr?,
    val over: WindowSpec?,
    override val pos: Int,
) : Expr

class Exists(
    val query: Query,
    override val pos: Int,
) : Expr

class ScalarSubquery(
    val query: Query,
    override val pos: Int,
) : Expr
