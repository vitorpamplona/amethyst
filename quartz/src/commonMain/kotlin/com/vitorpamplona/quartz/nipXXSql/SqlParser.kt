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
 * Recursive-descent parser for the Nostr SQL profile: SQLite's SELECT
 * statement minus anything that can reach outside the virtual schema
 * (PRAGMA, ATTACH, table-valued functions, INDEXED BY, …) and minus a few
 * rarely used forms (COLLATE, NATURAL joins, named WINDOW clauses).
 *
 * Expression precedence mirrors SQLite's operator table, lowest first:
 * `OR` < `AND` < `NOT` < equality (`=` `<>` `IS` `IN` `LIKE` `GLOB`
 * `BETWEEN` `ISNULL` `NOTNULL`) < relational (`<` `<=` `>` `>=`) <
 * `+ -` < `* / %` < `||` < unary `- +`. The compiler re-emits every
 * operator fully parenthesized, so the tree built here is exactly what
 * SQLite runs.
 */
class SqlParser private constructor(
    private val source: String,
    private val tokens: List<SqlToken>,
) {
    private var p = 0
    private var depth = 0

    /** Largest parameter number so far: SQLite gives a bare `?` this plus one. */
    private var maxParam = 0

    companion object {
        /** Guards the JVM stack against `((((((…` style inputs. */
        const val MAX_DEPTH = 200

        /** Words that can never be a bare identifier or alias. */
        val RESERVED =
            setOf(
                "all",
                "and",
                "as",
                "asc",
                "between",
                "by",
                "case",
                "cast",
                "collate",
                "cross",
                "current_date",
                "current_time",
                "current_timestamp",
                "desc",
                "distinct",
                "else",
                "end",
                "escape",
                "except",
                "exists",
                "false",
                "filter",
                "from",
                "full",
                "glob",
                "group",
                "having",
                "in",
                "indexed",
                "inner",
                "intersect",
                "is",
                "isnull",
                "join",
                "left",
                "like",
                "limit",
                "match",
                "natural",
                "not",
                "notnull",
                "null",
                "offset",
                "on",
                "or",
                "order",
                "outer",
                "over",
                "partition",
                "recursive",
                "regexp",
                "right",
                "select",
                "then",
                "true",
                "union",
                "using",
                "values",
                "when",
                "where",
                "window",
                "with",
            )

        fun parse(sql: String): Query {
            val parser = SqlParser(sql, SqlLexer.tokenize(sql))
            return parser.parseStatement()
        }
    }

    // ---- token helpers -----------------------------------------------------

    private val cur get() = tokens[p]

    private fun peek(offset: Int = 1) = tokens[minOf(p + offset, tokens.size - 1)]

    private fun advance(): SqlToken = tokens[p].also { if (p < tokens.size - 1) p++ }

    private fun kw(word: String) = cur.isKeyword(word)

    private fun sym(s: String) = cur.isSymbol(s)

    private fun acceptKw(word: String): Boolean {
        if (kw(word)) {
            advance()
            return true
        }
        return false
    }

    private fun acceptSym(s: String): Boolean {
        if (sym(s)) {
            advance()
            return true
        }
        return false
    }

    private fun expectKw(word: String) {
        if (!acceptKw(word)) throw error("expected ${word.uppercase()}")
    }

    private fun expectSym(s: String) {
        if (!acceptSym(s)) throw error("expected '$s'")
    }

    private fun error(msg: String): SqlException {
        val near = if (cur.type == TokenType.EOF) "end of query" else "'${cur.text}'"
        return SqlException.invalid("$msg near $near", cur.pos)
    }

    private fun isIdentifier(t: SqlToken) = t.type == TokenType.QUOTED_IDENT || (t.type == TokenType.IDENT && t.text.lowercase() !in RESERVED)

    private fun identifier(what: String): String {
        val t = cur
        if (!isIdentifier(t)) throw error("expected $what")
        advance()
        return t.text
    }

    private inline fun <T> nested(block: () -> T): T {
        if (++depth > MAX_DEPTH) throw SqlException.unsupported("query nested deeper than $MAX_DEPTH levels", cur.pos)
        try {
            return block()
        } finally {
            depth--
        }
    }

    // ---- statement / query -------------------------------------------------

    private fun parseStatement(): Query {
        if (!kw("select") && !kw("with") && !kw("values")) {
            throw SqlException.unsupported("only SELECT queries are allowed", cur.pos)
        }
        val q = parseQuery()
        if (acceptSym(";")) {
            if (cur.type != TokenType.EOF) throw SqlException.unsupported("exactly one SELECT statement is allowed", cur.pos)
        } else if (cur.type != TokenType.EOF) {
            throw error("unexpected text")
        }
        return q
    }

    private fun startsQuery(t: SqlToken) = t.isKeyword("select") || t.isKeyword("with") || t.isKeyword("values")

    private fun parseQuery(): Query =
        nested {
            val with = if (kw("with")) parseWith() else null
            val first = parseCore()
            val rest = ArrayList<Pair<CompoundOp, SelectCore>>()
            while (true) {
                val op =
                    when {
                        acceptKw("union") -> if (acceptKw("all")) CompoundOp.UNION_ALL else CompoundOp.UNION
                        acceptKw("intersect") -> CompoundOp.INTERSECT
                        acceptKw("except") -> CompoundOp.EXCEPT
                        else -> break
                    }
                rest.add(op to parseCore())
            }
            val orderBy = if (kw("order")) parseOrderBy() else emptyList()
            var limit: Expr? = null
            var offset: Expr? = null
            if (acceptKw("limit")) {
                limit = parseExpr()
                if (acceptKw("offset")) {
                    offset = parseExpr()
                } else if (acceptSym(",")) {
                    // SQLite's `LIMIT <offset>, <count>`.
                    offset = limit
                    limit = parseExpr()
                }
            }
            Query(with, first, rest, orderBy, limit, offset)
        }

    private fun parseWith(): With {
        expectKw("with")
        val recursive = acceptKw("recursive")
        val ctes = ArrayList<Cte>()
        do {
            val pos = cur.pos
            val name = identifier("common table expression name")
            val cols = ArrayList<String>()
            if (acceptSym("(")) {
                do cols.add(identifier("column name")) while (acceptSym(","))
                expectSym(")")
            }
            expectKw("as")
            if (kw("materialized") || (kw("not") && peek().isKeyword("materialized"))) {
                throw SqlException.unsupported("MATERIALIZED hints", cur.pos)
            }
            expectSym("(")
            val q = parseQuery()
            expectSym(")")
            ctes.add(Cte(name, cols, q, pos))
        } while (acceptSym(","))
        return With(recursive, ctes)
    }

    private fun parseCore(): SelectCore {
        if (acceptKw("values")) {
            val rows = ArrayList<List<Expr>>()
            do {
                expectSym("(")
                rows.add(parseExprList())
                expectSym(")")
            } while (acceptSym(","))
            return Values(rows)
        }
        if (sym("(")) throw SqlException.unsupported("parenthesized compound operands (SQLite does not allow them)", cur.pos)
        expectKw("select")
        val distinct =
            if (acceptKw("distinct")) {
                true
            } else {
                acceptKw("all")
                false
            }
        val columns = ArrayList<ResultColumn>()
        do columns.add(parseResultColumn()) while (acceptSym(","))
        val from = if (acceptKw("from")) parseFrom() else null
        val where = if (acceptKw("where")) parseExpr() else null
        var groupBy: List<Expr> = emptyList()
        if (acceptKw("group")) {
            expectKw("by")
            groupBy = parseExprList()
        }
        val having = if (acceptKw("having")) parseExpr() else null
        if (kw("window")) throw SqlException.unsupported("named WINDOW clauses; write OVER (…) inline", cur.pos)
        return Select(distinct, columns, from, where, groupBy, having)
    }

    private fun parseResultColumn(): ResultColumn {
        if (acceptSym("*")) return StarColumn(null)
        if (isIdentifier(cur) && peek().isSymbol(".") && peek(2).isSymbol("*")) {
            val table = advance().text
            advance()
            advance()
            return StarColumn(table)
        }
        val start = cur.pos
        val expr = parseExpr()
        val text = source.substring(start, tokens[p - 1].end)
        return ExprColumn(expr, parseAlias(), text)
    }

    /** `[AS] alias`; a string literal is accepted as an alias like SQLite does. */
    private fun parseAlias(): String? {
        if (acceptKw("as")) {
            if (cur.type == TokenType.STRING) return advance().text
            return identifier("alias")
        }
        if (isIdentifier(cur)) return advance().text
        return null
    }

    private fun parseOrderBy(): List<OrderingTerm> {
        expectKw("order")
        expectKw("by")
        val terms = ArrayList<OrderingTerm>()
        do {
            val e = parseExpr()
            if (kw("collate")) throw SqlException.unsupported("COLLATE", cur.pos)
            val desc =
                if (acceptKw("desc")) {
                    true
                } else {
                    acceptKw("asc")
                    false
                }
            var nullsFirst: Boolean? = null
            if (cur.isKeyword("nulls")) {
                advance()
                nullsFirst =
                    when {
                        acceptKw("first") -> true
                        acceptKw("last") -> false
                        else -> throw error("expected FIRST or LAST")
                    }
            }
            terms.add(OrderingTerm(e, desc, nullsFirst))
        } while (acceptSym(","))
        return terms
    }

    // ---- FROM --------------------------------------------------------------

    private fun parseFrom(): FromItem {
        var left = parseFromItem()
        while (true) {
            val type =
                when {
                    acceptSym(",") -> {
                        JoinType.COMMA
                    }

                    acceptKw("cross") -> {
                        expectKw("join")
                        JoinType.CROSS
                    }

                    acceptKw("inner") -> {
                        expectKw("join")
                        JoinType.INNER
                    }

                    acceptKw("join") -> {
                        JoinType.INNER
                    }

                    acceptKw("left") -> {
                        acceptKw("outer")
                        expectKw("join")
                        JoinType.LEFT
                    }

                    kw("right") || kw("full") -> {
                        throw SqlException.unsupported("RIGHT and FULL joins", cur.pos)
                    }

                    kw("natural") -> {
                        throw SqlException.unsupported("NATURAL joins", cur.pos)
                    }

                    else -> {
                        break
                    }
                }
            val right = parseFromItem()
            var on: Expr? = null
            if (type == JoinType.INNER || type == JoinType.LEFT) {
                if (acceptKw("on")) {
                    on = parseExpr()
                } else if (kw("using")) {
                    throw SqlException.unsupported("JOIN … USING; write ON a.x = b.x", cur.pos)
                }
            }
            left = Join(left, type, right, on)
        }
        return left
    }

    private fun parseFromItem(): FromItem {
        if (sym("(")) {
            if (!startsQuery(peek())) throw SqlException.unsupported("parenthesized joins", cur.pos)
            advance()
            val q = parseQuery()
            expectSym(")")
            return SubqueryRef(q, parseAlias())
        }
        val pos = cur.pos
        val name = identifier("table name")
        if (sym(".")) throw SqlException.unsupported("schema-qualified table names", pos)
        if (sym("(")) throw SqlException.unsupported("table-valued functions", pos)
        val alias = parseAlias()
        if (kw("indexed") || (kw("not") && peek().isKeyword("indexed"))) throw SqlException.unsupported("INDEXED BY", cur.pos)
        return TableRef(name, alias, pos)
    }

    // ---- expressions -------------------------------------------------------

    private fun parseExprList(): List<Expr> {
        val list = ArrayList<Expr>()
        do list.add(parseExpr()) while (acceptSym(","))
        return list
    }

    private fun parseExpr(): Expr = nested { parseOr() }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (kw("or")) {
            val pos = advance().pos
            left = Binary("OR", left, parseAnd(), pos)
        }
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseNot()
        while (kw("and")) {
            val pos = advance().pos
            left = Binary("AND", left, parseNot(), pos)
        }
        return left
    }

    private fun parseNot(): Expr {
        if (kw("not")) {
            val pos = advance().pos
            return Unary("NOT", nested { parseNot() }, pos)
        }
        return parseEquality()
    }

    private fun parseEquality(): Expr {
        var left = parseRelational()
        while (true) {
            val pos = cur.pos
            left =
                when {
                    sym("=") || sym("==") -> {
                        advance()
                        Binary("=", left, parseRelational(), pos)
                    }

                    sym("<>") || sym("!=") -> {
                        advance()
                        Binary("<>", left, parseRelational(), pos)
                    }

                    kw("isnull") -> {
                        advance()
                        parseRelational(IsNull(left, false, pos))
                    }

                    kw("notnull") -> {
                        advance()
                        parseRelational(IsNull(left, true, pos))
                    }

                    kw("is") -> {
                        advance()
                        var not = acceptKw("not")
                        if (cur.isKeyword("distinct")) {
                            advance()
                            expectKw("from")
                            // IS DISTINCT FROM == IS NOT, IS NOT DISTINCT FROM == IS.
                            not = !not
                        }
                        Binary(if (not) "IS NOT" else "IS", left, parseRelational(), pos)
                    }

                    kw("not") && (peek().isKeyword("null")) -> {
                        advance()
                        advance()
                        parseRelational(IsNull(left, true, pos))
                    }

                    kw("in") || kw("like") || kw("glob") || kw("between") || kw("match") || kw("regexp") ||
                        (
                            kw("not") &&
                                (
                                    peek().isKeyword("in") || peek().isKeyword("like") || peek().isKeyword("glob") ||
                                        peek().isKeyword("between") || peek().isKeyword("match") || peek().isKeyword("regexp")
                                )
                        ) -> {
                        val not = acceptKw("not")
                        parseInLikeBetween(left, not, pos)
                    }

                    else -> {
                        return left
                    }
                }
        }
    }

    private fun parseInLikeBetween(
        left: Expr,
        not: Boolean,
        pos: Int,
    ): Expr =
        when {
            acceptKw("between") -> {
                val low = parseBetweenLow()
                expectKw("and")
                Between(left, not, low, parseRelational(), pos)
            }

            acceptKw("in") -> {
                expectSym("(")
                if (startsQuery(cur)) {
                    val q = parseQuery()
                    expectSym(")")
                    parseRelational(InQuery(left, not, q, pos))
                } else {
                    val items = if (sym(")")) emptyList() else parseExprList()
                    expectSym(")")
                    parseRelational(InList(left, not, items, pos))
                }
            }

            kw("like") || kw("glob") -> {
                val op = advance().text.uppercase()
                val pattern = parseRelational()
                val escape = if (op == "LIKE" && acceptKw("escape")) parseRelational() else null
                Match(left, not, op, pattern, escape, pos)
            }

            else -> {
                throw SqlException.unsupported("${cur.text.uppercase()} operator", cur.pos)
            }
        }

    /**
     * SQLite's BETWEEN lower bound is nearly a whole expression: it runs up
     * to the first top-level AND (which belongs to BETWEEN), but an OR may
     * pull ANDs into its right side. So `x BETWEEN 1 = 1 AND 9` is legal,
     * and `x BETWEEN 0 OR 1 AND 9` is incomplete input.
     */
    private fun parseBetweenLow(): Expr {
        var left = nested { parseNot() }
        while (kw("or")) {
            val pos = advance().pos
            left = Binary("OR", left, parseAnd(), pos)
        }
        return left
    }

    /**
     * The tighter levels take an optional, already-parsed left operand.
     * After a postfix form (`x NOTNULL`, `x IN (…)`) SQLite's LALR parser
     * lets tighter operators continue on the result:
     * `5 NOTNULL - rest < 1` is `((5 NOTNULL) - rest) < 1`.
     */
    private fun parseRelational(initial: Expr? = null): Expr {
        var left = parseAdditive(initial)
        while (sym("<") || sym("<=") || sym(">") || sym(">=")) {
            val t = advance()
            left = Binary(t.text, left, parseAdditive(), t.pos)
        }
        return left
    }

    private fun parseAdditive(initial: Expr? = null): Expr {
        var left = parseMultiplicative(initial)
        while (sym("+") || sym("-")) {
            val t = advance()
            left = Binary(t.text, left, parseMultiplicative(), t.pos)
        }
        return left
    }

    private fun parseMultiplicative(initial: Expr? = null): Expr {
        var left = parseConcat(initial)
        while (sym("*") || sym("/") || sym("%")) {
            val t = advance()
            left = Binary(t.text, left, parseConcat(), t.pos)
        }
        return left
    }

    private fun parseConcat(initial: Expr? = null): Expr {
        var left = initial ?: parseUnary()
        while (sym("||")) {
            val t = advance()
            left = Binary("||", left, parseUnary(), t.pos)
        }
        return left
    }

    private fun parseUnary(): Expr {
        // SQLite's grammar also accepts NOT as an operand (`1 = NOT 0`):
        // it then swallows everything that binds tighter than NOT.
        if (kw("not")) {
            val pos = advance().pos
            return Unary("NOT", nested { parseNot() }, pos)
        }
        if (sym("-") || sym("+")) {
            val t = advance()
            return Unary(t.text, nested { parseUnary() }, t.pos)
        }
        val e = parsePrimary()
        if (kw("collate")) throw SqlException.unsupported("COLLATE", cur.pos)
        return e
    }

    private fun parsePrimary(): Expr {
        val t = cur
        return when (t.type) {
            TokenType.NUMBER -> {
                advance()
                Literal(LiteralKind.NUMBER, t.text, t.pos)
            }

            TokenType.STRING -> {
                advance()
                Literal(LiteralKind.STRING, t.text, t.pos)
            }

            TokenType.PARAM -> {
                advance()
                Param(canonicalParam(t), t.pos)
            }

            TokenType.SYMBOL -> {
                if (!t.isSymbol("(")) throw error("expected an expression")
                advance()
                if (startsQuery(cur)) {
                    val q = parseQuery()
                    expectSym(")")
                    ScalarSubquery(q, t.pos)
                } else {
                    val e = parseExpr()
                    if (sym(",")) throw SqlException.unsupported("row values", cur.pos)
                    expectSym(")")
                    e
                }
            }

            TokenType.QUOTED_IDENT, TokenType.IDENT -> {
                parseNamedPrimary(t)
            }

            TokenType.EOF -> {
                throw error("expected an expression")
            }
        }
    }

    private fun parseNamedPrimary(t: SqlToken): Expr {
        if (t.type == TokenType.IDENT) {
            when (t.text.lowercase()) {
                "null" -> {
                    advance()
                    return Literal(LiteralKind.NULL, "NULL", t.pos)
                }

                "true" -> {
                    advance()
                    return Literal(LiteralKind.TRUE, "TRUE", t.pos)
                }

                "false" -> {
                    advance()
                    return Literal(LiteralKind.FALSE, "FALSE", t.pos)
                }

                "current_timestamp" -> {
                    advance()
                    return Literal(LiteralKind.CURRENT_TIMESTAMP, "CURRENT_TIMESTAMP", t.pos)
                }

                "current_date" -> {
                    advance()
                    return Literal(LiteralKind.CURRENT_DATE, "CURRENT_DATE", t.pos)
                }

                "current_time" -> {
                    advance()
                    return Literal(LiteralKind.CURRENT_TIME, "CURRENT_TIME", t.pos)
                }

                "exists" -> {
                    advance()
                    expectSym("(")
                    val q = parseQuery()
                    expectSym(")")
                    return Exists(q, t.pos)
                }

                "case" -> {
                    return parseCase()
                }

                "cast" -> {
                    return parseCast()
                }

                "raise" -> {
                    throw SqlException.unsupported("RAISE", t.pos)
                }
            }
            if (peek().isSymbol("(")) return parseFunction()
        }
        if (!isIdentifier(t)) throw error("expected an expression")
        advance()
        if (acceptSym(".")) {
            val col = identifier("column name")
            if (sym(".")) throw SqlException.unsupported("schema-qualified column names", cur.pos)
            return ColumnRef(t.text, col, t.pos)
        }
        return ColumnRef(null, t.text, t.pos)
    }

    /**
     * Numbers positional parameters the way SQLite does, in text order: `?NNN`
     * is explicit and a bare `?` is one more than the largest number so far.
     * Every positional parameter leaves as `?N`, so its value is known without
     * depending on the order the compiler emits things in.
     */
    private fun canonicalParam(t: SqlToken): String {
        if (t.text.startsWith(":")) return t.text
        val n =
            if (t.text == "?") {
                maxParam + 1
            } else {
                t.text
                    .substring(1)
                    .toIntOrNull()
                    ?.takeIf { it in 1..32766 }
                    ?: throw SqlException.invalid("parameter number out of range", t.pos)
            }
        maxParam = maxOf(maxParam, n)
        return "?$n"
    }

    private fun parseCase(): Expr {
        val pos = advance().pos
        val operand = if (!kw("when")) parseExpr() else null
        val whens = ArrayList<Pair<Expr, Expr>>()
        while (acceptKw("when")) {
            val w = parseExpr()
            expectKw("then")
            whens.add(w to parseExpr())
        }
        if (whens.isEmpty()) throw error("expected WHEN")
        val elseExpr = if (acceptKw("else")) parseExpr() else null
        expectKw("end")
        return Case(operand, whens, elseExpr, pos)
    }

    private fun parseCast(): Expr {
        val pos = advance().pos
        expectSym("(")
        val e = parseExpr()
        expectKw("as")
        val typePos = cur.pos
        val type = identifier("type name").uppercase()
        if (type !in SqlProfile.CAST_TYPES) {
            throw SqlException.unsupported("CAST to $type; use one of ${SqlProfile.CAST_TYPES.joinToString()}", typePos)
        }
        expectSym(")")
        return Cast(e, type, pos)
    }

    private fun parseFunction(): Expr {
        val nameTok = advance()
        val name = nameTok.text.lowercase()
        if (name !in SqlProfile.FUNCTIONS) throw SqlException.unsupported("function $name()", nameTok.pos)
        expectSym("(")
        var distinct = false
        var star = false
        var args: List<Expr> = emptyList()
        if (acceptSym("*")) {
            star = true
        } else if (!sym(")")) {
            distinct = acceptKw("distinct")
            args = parseExprList()
            if (kw("order")) throw SqlException.unsupported("ORDER BY inside aggregate calls", cur.pos)
        }
        expectSym(")")
        var filter: Expr? = null
        if (acceptKw("filter")) {
            expectSym("(")
            expectKw("where")
            filter = parseExpr()
            expectSym(")")
        }
        var over: WindowSpec? = null
        if (acceptKw("over")) {
            if (!sym("(")) throw SqlException.unsupported("named windows; write OVER (…) inline", cur.pos)
            over = parseWindow()
        }
        return FunctionCall(name, distinct, star, args, filter, over, nameTok.pos)
    }

    private fun parseWindow(): WindowSpec {
        expectSym("(")
        if (isIdentifier(cur)) throw SqlException.unsupported("base window names", cur.pos)
        var partitionBy: List<Expr> = emptyList()
        if (acceptKw("partition")) {
            expectKw("by")
            partitionBy = parseExprList()
        }
        val orderBy = if (kw("order")) parseOrderBy() else emptyList()
        var frame: Frame? = null
        val unit =
            when {
                cur.isKeyword("rows") -> FrameUnit.ROWS
                cur.isKeyword("range") -> FrameUnit.RANGE
                cur.isKeyword("groups") -> FrameUnit.GROUPS
                else -> null
            }
        if (unit != null) {
            advance()
            frame =
                if (acceptKw("between")) {
                    val start = parseBound()
                    expectKw("and")
                    Frame(unit, start, parseBound())
                } else {
                    Frame(unit, parseBound(), null)
                }
            if (cur.isKeyword("exclude")) throw SqlException.unsupported("EXCLUDE in window frames", cur.pos)
        }
        expectSym(")")
        return WindowSpec(partitionBy, orderBy, frame)
    }

    private fun parseBound(): FrameBound {
        if (cur.isKeyword("unbounded")) {
            advance()
            return when {
                cur.isKeyword("preceding") -> {
                    advance()
                    FrameBound(BoundKind.UNBOUNDED_PRECEDING, null)
                }

                cur.isKeyword("following") -> {
                    advance()
                    FrameBound(BoundKind.UNBOUNDED_FOLLOWING, null)
                }

                else -> {
                    throw error("expected PRECEDING or FOLLOWING")
                }
            }
        }
        if (cur.isKeyword("current")) {
            advance()
            if (!cur.isKeyword("row")) throw error("expected ROW")
            advance()
            return FrameBound(BoundKind.CURRENT_ROW, null)
        }
        val e = parseAdditive()
        return when {
            cur.isKeyword("preceding") -> {
                advance()
                FrameBound(BoundKind.PRECEDING, e)
            }

            cur.isKeyword("following") -> {
                advance()
                FrameBound(BoundKind.FOLLOWING, e)
            }

            else -> {
                throw error("expected PRECEDING or FOLLOWING")
            }
        }
    }
}
