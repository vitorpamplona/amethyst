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

internal enum class NqlTokenType { NAME, STRING, INTEGER, REAL, PARAM, SYMBOL, EOF }

internal class NqlToken(
    val type: NqlTokenType,
    /** Names: as written. Strings: the unescaped value. Numbers and symbols: as written. */
    val text: String,
    val pos: Int,
) {
    fun isKeyword(kw: String) = type == NqlTokenType.NAME && text.equals(kw, ignoreCase = true)

    fun isSymbol(sym: String) = type == NqlTokenType.SYMBOL && text == sym

    val isName get() = type == NqlTokenType.NAME && text.uppercase() !in NqlLexer.KEYWORDS
}

/**
 * NQL's lexical rules (NIP-FF, Syntax): names, keywords, integers, reals,
 * single-quoted strings, `?` and the operators. Anything else (comments,
 * quoted identifiers, `;`, blobs, numbered or named parameters) is invalid.
 */
internal object NqlLexer {
    val KEYWORDS =
        setOf(
            "AND",
            "AS",
            "ASC",
            "BETWEEN",
            "BY",
            "CASE",
            "CAST",
            "DESC",
            "DISTINCT",
            "ELSE",
            "END",
            "EXISTS",
            "FALSE",
            "FROM",
            "GROUP",
            "HAVING",
            "IN",
            "INTEGER",
            "IS",
            "JOIN",
            "LEFT",
            "LIKE",
            "LIMIT",
            "NOT",
            "NULL",
            "OFFSET",
            "ON",
            "OR",
            "ORDER",
            "REAL",
            "SELECT",
            "TEXT",
            "THEN",
            "TRUE",
            "WHEN",
            "WHERE",
        )

    private val TWO_CHAR = setOf("<=", ">=", "<>", "||")
    private const val ONE_CHAR = "(),.*+-/%<>="

    fun tokenize(text: String): List<NqlToken> {
        val out = ArrayList<NqlToken>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c.isWhitespace() -> {
                    i++
                }

                c == '\'' -> {
                    val sb = StringBuilder()
                    var j = i + 1
                    while (true) {
                        if (j >= n) throw SqlException.invalid("unterminated string", i)
                        if (text[j] == '\'') {
                            if (j + 1 < n && text[j + 1] == '\'') {
                                sb.append('\'')
                                j += 2
                                continue
                            }
                            break
                        }
                        sb.append(text[j])
                        j++
                    }
                    out.add(NqlToken(NqlTokenType.STRING, sb.toString(), i))
                    i = j + 1
                }

                c in '0'..'9' -> {
                    val start = i
                    while (i < n && text[i] in '0'..'9') i++
                    var real = false
                    if (i < n && text[i] == '.') {
                        i++
                        if (i >= n || text[i] !in '0'..'9') throw SqlException.invalid("a real needs digits after its point", start)
                        while (i < n && text[i] in '0'..'9') i++
                        real = true
                    }
                    if (i < n && (text[i] == 'e' || text[i] == 'E')) {
                        i++
                        if (i < n && (text[i] == '+' || text[i] == '-')) i++
                        if (i >= n || text[i] !in '0'..'9') throw SqlException.invalid("malformed exponent", start)
                        while (i < n && text[i] in '0'..'9') i++
                        real = true
                    }
                    if (i < n && (isNamePart(text[i]) || text[i] == '.')) throw SqlException.invalid("malformed number", start)
                    out.add(NqlToken(if (real) NqlTokenType.REAL else NqlTokenType.INTEGER, text.substring(start, i), start))
                }

                isNameStart(c) -> {
                    val start = i
                    while (i < n && isNamePart(text[i])) i++
                    out.add(NqlToken(NqlTokenType.NAME, text.substring(start, i), start))
                }

                c == '?' -> {
                    if (i + 1 < n && text[i + 1] in '0'..'9') throw SqlException.invalid("parameters are a bare ?", i)
                    out.add(NqlToken(NqlTokenType.PARAM, "?", i))
                    i++
                }

                else -> {
                    val two = if (i + 1 < n) text.substring(i, i + 2) else ""
                    if (two in TWO_CHAR) {
                        out.add(NqlToken(NqlTokenType.SYMBOL, two, i))
                        i += 2
                    } else if (c in ONE_CHAR) {
                        out.add(NqlToken(NqlTokenType.SYMBOL, c.toString(), i))
                        i++
                    } else {
                        throw SqlException.invalid("unexpected character '$c'", i)
                    }
                }
            }
        }
        out.add(NqlToken(NqlTokenType.EOF, "", n))
        return out
    }

    private fun isNameStart(c: Char) = c == '_' || c in 'a'..'z' || c in 'A'..'Z'

    private fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'
}

/**
 * Recursive-descent parser for NIP-FF's grammar. It accepts exactly the
 * grammar: everything else is `invalid`. Names and types are the checker's job.
 */
class NqlParser private constructor(
    text: String,
) {
    private val tokens = NqlLexer.tokenize(text)
    private var p = 0
    private var params = 0

    private val peek get() = tokens[p]

    private fun next() = tokens[p++]

    private fun fail(
        what: String,
        t: NqlToken = peek,
    ): Nothing = throw SqlException.invalid(if (t.type == NqlTokenType.EOF) "$what, found the end of the query" else "$what, found '${t.text}'", t.pos)

    private fun accept(kw: String): Boolean {
        if (peek.isKeyword(kw)) {
            p++
            return true
        }
        return false
    }

    private fun expect(kw: String) {
        if (!accept(kw)) fail("expected $kw")
    }

    private fun acceptSymbol(s: String): Boolean {
        if (peek.isSymbol(s)) {
            p++
            return true
        }
        return false
    }

    private fun expectSymbol(s: String) {
        if (!acceptSymbol(s)) fail("expected '$s'")
    }

    private fun name(): String {
        if (!peek.isName) fail("expected a name")
        return next().text
    }

    private fun query(): NqlQuery {
        val pos = peek.pos
        expect("SELECT")
        val distinct = accept("DISTINCT")
        val results = ArrayList<NqlResultColumn>()
        var star = false
        if (acceptSymbol("*")) {
            star = true
        } else {
            do {
                val start = peek.pos
                val e = expr()
                val alias = if (accept("AS")) name() else null
                results.add(NqlResultColumn(e, alias, start))
            } while (acceptSymbol(","))
        }

        val from = ArrayList<NqlSource>()
        if (accept("FROM")) {
            from.add(source(null, null))
            while (true) {
                val join =
                    when {
                        accept("JOIN") -> {
                            NqlJoin.INNER
                        }

                        peek.isKeyword("LEFT") -> {
                            p++
                            expect("JOIN")
                            NqlJoin.LEFT
                        }

                        else -> {
                            break
                        }
                    }
                val start = peek.pos
                val src = source(join, null)
                expect("ON")
                from.add(NqlSource(src.table, src.subquery, src.alias, join, expr(), start))
            }
        }
        val where = if (accept("WHERE")) expr() else null
        val groupBy = ArrayList<NqlExpr>()
        var having: NqlExpr? = null
        if (accept("GROUP")) {
            expect("BY")
            do groupBy.add(expr()) while (acceptSymbol(","))
            if (accept("HAVING")) having = expr()
        }
        val orderBy = ArrayList<NqlOrder>()
        if (accept("ORDER")) {
            expect("BY")
            do {
                val e = expr()
                val desc =
                    when {
                        accept("DESC") -> true
                        accept("ASC") -> false
                        else -> false
                    }
                orderBy.add(NqlOrder(e, desc))
            } while (acceptSymbol(","))
        }
        var limit: NqlCount? = null
        var offset: NqlCount? = null
        if (accept("LIMIT")) {
            limit = count()
            if (accept("OFFSET")) offset = count()
        }
        return NqlQuery(distinct, results, star, from, where, groupBy, having, orderBy, limit, offset, pos)
    }

    private fun count(): NqlCount {
        val t = next()
        return when (t.type) {
            NqlTokenType.INTEGER -> NqlCount(integer(t), null, t.pos)
            NqlTokenType.PARAM -> NqlCount(null, params++, t.pos)
            else -> fail("expected an integer or ?", t)
        }
    }

    private fun source(
        join: NqlJoin?,
        on: NqlExpr?,
    ): NqlSource {
        val start = peek.pos
        if (acceptSymbol("(")) {
            val q = query()
            expectSymbol(")")
            expect("AS")
            return NqlSource(null, q, name(), join, on, start)
        }
        val table = name()
        val alias = if (accept("AS")) name() else null
        return NqlSource(table.lowercase(), null, alias, join, on, start)
    }

    private fun expr(): NqlExpr {
        var left = and()
        while (peek.isKeyword("OR")) {
            val pos = next().pos
            left = NqlBinary("OR", left, and(), pos)
        }
        return left
    }

    private fun and(): NqlExpr {
        var left = not()
        while (peek.isKeyword("AND")) {
            val pos = next().pos
            left = NqlBinary("AND", left, not(), pos)
        }
        return left
    }

    private fun not(): NqlExpr {
        if (peek.isKeyword("NOT")) {
            val pos = next().pos
            return NqlNot(not(), pos)
        }
        return predicate()
    }

    private fun predicate(): NqlExpr {
        val left = sum()
        val t = peek
        if (t.type == NqlTokenType.SYMBOL && t.text in COMPARISONS) {
            p++
            return NqlBinary(t.text, left, sum(), t.pos)
        }
        if (t.isKeyword("IS")) {
            p++
            val not = accept("NOT")
            expect("NULL")
            return NqlIsNull(left, not, t.pos)
        }
        val not = t.isKeyword("NOT") && (tokens[p + 1].isKeyword("IN") || tokens[p + 1].isKeyword("LIKE") || tokens[p + 1].isKeyword("BETWEEN"))
        if (not) p++
        val op = peek
        return when {
            op.isKeyword("IN") -> {
                p++
                expectSymbol("(")
                if (peek.isKeyword("SELECT")) {
                    val q = query()
                    expectSymbol(")")
                    NqlInQuery(left, not, q, op.pos)
                } else {
                    val items = ArrayList<NqlExpr>()
                    do items.add(expr()) while (acceptSymbol(","))
                    expectSymbol(")")
                    NqlInList(left, not, items, op.pos)
                }
            }

            op.isKeyword("LIKE") -> {
                p++
                NqlLike(left, not, sum(), op.pos)
            }

            op.isKeyword("BETWEEN") -> {
                p++
                val low = sum()
                expect("AND")
                NqlBetween(left, not, low, sum(), op.pos)
            }

            else -> {
                left
            }
        }
    }

    private fun sum(): NqlExpr {
        var left = product()
        while (true) {
            val t = peek
            if (t.isSymbol("+") || t.isSymbol("-") || t.isSymbol("||")) {
                p++
                left = NqlBinary(t.text, left, product(), t.pos)
            } else {
                return left
            }
        }
    }

    private fun product(): NqlExpr {
        var left = unary()
        while (true) {
            val t = peek
            if (t.isSymbol("*") || t.isSymbol("/") || t.isSymbol("%")) {
                p++
                left = NqlBinary(t.text, left, unary(), t.pos)
            } else {
                return left
            }
        }
    }

    private fun unary(): NqlExpr {
        val t = peek
        if (t.isSymbol("-")) {
            p++
            val n = peek
            // The one integer literal that only exists negated.
            if (n.type == NqlTokenType.INTEGER && n.text.trimStart('0') == "9223372036854775808") {
                p++
                return NqlLiteral(Long.MIN_VALUE, t.pos)
            }
            return NqlNegate(unary(), t.pos)
        }
        return primary()
    }

    private fun primary(): NqlExpr {
        val t = next()
        return when (t.type) {
            NqlTokenType.INTEGER -> {
                NqlLiteral(integer(t), t.pos)
            }

            NqlTokenType.REAL -> {
                NqlLiteral(real(t), t.pos)
            }

            NqlTokenType.STRING -> {
                NqlLiteral(t.text, t.pos)
            }

            NqlTokenType.PARAM -> {
                NqlParam(params++, t.pos)
            }

            NqlTokenType.SYMBOL -> {
                if (t.text != "(") fail("expected an expression", t)
                if (peek.isKeyword("SELECT")) {
                    val q = query()
                    expectSymbol(")")
                    NqlScalarSubquery(q, t.pos)
                } else {
                    val e = expr()
                    expectSymbol(")")
                    e
                }
            }

            NqlTokenType.NAME -> {
                when (t.text.uppercase()) {
                    "NULL" -> {
                        NqlLiteral(null, t.pos)
                    }

                    "TRUE" -> {
                        NqlLiteral(true, t.pos)
                    }

                    "FALSE" -> {
                        NqlLiteral(false, t.pos)
                    }

                    "CAST" -> {
                        expectSymbol("(")
                        val e = expr()
                        expect("AS")
                        val target =
                            when {
                                accept("INTEGER") -> NqlType.INTEGER
                                accept("REAL") -> NqlType.REAL
                                accept("TEXT") -> NqlType.TEXT
                                else -> fail("expected INTEGER, REAL or TEXT")
                            }
                        expectSymbol(")")
                        NqlCast(e, target, t.pos)
                    }

                    "CASE" -> {
                        val whens = ArrayList<Pair<NqlExpr, NqlExpr>>()
                        if (!peek.isKeyword("WHEN")) fail("expected WHEN")
                        while (accept("WHEN")) {
                            val c = expr()
                            expect("THEN")
                            whens.add(c to expr())
                        }
                        val e = if (accept("ELSE")) expr() else null
                        expect("END")
                        NqlCase(whens, e, t.pos)
                    }

                    "EXISTS" -> {
                        expectSymbol("(")
                        val q = query()
                        expectSymbol(")")
                        NqlExists(q, t.pos)
                    }

                    else -> {
                        if (!t.isName) fail("expected an expression", t)
                        when {
                            acceptSymbol("(") -> call(t)
                            acceptSymbol(".") -> NqlColumnRef(t.text, name(), t.pos)
                            else -> NqlColumnRef(null, t.text, t.pos)
                        }
                    }
                }
            }

            NqlTokenType.EOF -> {
                fail("expected an expression", t)
            }
        }
    }

    private fun call(nameToken: NqlToken): NqlExpr {
        val name = nameToken.text.lowercase()
        if (acceptSymbol("*")) {
            expectSymbol(")")
            return NqlCall(name, distinct = false, star = true, args = emptyList(), nameToken.pos)
        }
        val distinct = accept("DISTINCT")
        val args = ArrayList<NqlExpr>()
        if (distinct || !peek.isSymbol(")")) {
            do args.add(expr()) while (acceptSymbol(","))
        }
        expectSymbol(")")
        return NqlCall(name, distinct, star = false, args = args, nameToken.pos)
    }

    private fun integer(t: NqlToken): Long = t.text.toLongOrNull() ?: throw SqlException.invalid("integer out of range", t.pos)

    private fun real(t: NqlToken): Double {
        val d = t.text.toDouble()
        if (!d.isFinite()) throw SqlException.invalid("real out of range", t.pos)
        if (d == 0.0 &&
            t.text
                .substringBefore('e')
                .substringBefore('E')
                .any { it in '1'..'9' }
        ) {
            throw SqlException.invalid("real out of range", t.pos)
        }
        return d
    }

    companion object {
        private val COMPARISONS = setOf("=", "<>", "<", "<=", ">", ">=")

        /** Parses [text] as one NQL query and returns it with the number of `?` it holds. */
        fun parse(text: String): Pair<NqlQuery, Int> {
            val parser = NqlParser(text)
            val q = parser.query()
            if (parser.peek.type != NqlTokenType.EOF) parser.fail("expected the end of the query")
            return q to parser.params
        }
    }
}
