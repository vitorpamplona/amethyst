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

enum class TokenType {
    /** Bare or quoted identifier, or a keyword (keywords are matched by text in the parser). */
    IDENT,

    /** `"quoted"` / `` `quoted` `` / `[quoted]` identifier: never a keyword. */
    QUOTED_IDENT,
    STRING,
    NUMBER,

    /** `?`, `?NNN` or `:name`. [SqlToken.text] keeps the raw spelling. */
    PARAM,
    SYMBOL,
    EOF,
}

class SqlToken(
    val type: TokenType,
    /** Identifiers: as written. Strings: the unescaped value. Symbols: the operator. */
    val text: String,
    val pos: Int,
    /** Offset just past the token in the source text. */
    val end: Int,
) {
    /** Case-insensitive keyword match; quoted identifiers never match. */
    fun isKeyword(kw: String) = type == TokenType.IDENT && text.equals(kw, ignoreCase = true)

    fun isSymbol(sym: String) = type == TokenType.SYMBOL && text == sym

    override fun toString() = "$type($text)@$pos"
}

/**
 * Tokenizer for the SQLite SELECT dialect. Rejects anything it cannot
 * classify — blob literals, bitwise operators and `->` are left out of
 * the profile on purpose, so they fail here with a position instead of
 * surfacing later as confusing parse errors.
 */
object SqlLexer {
    private val TWO_CHAR = setOf("<=", ">=", "<>", "!=", "==", "||")
    private const val ONE_CHAR = "(),.;*+-/%<>="

    fun tokenize(sql: String): List<SqlToken> {
        val out = ArrayList<SqlToken>()
        var i = 0
        val n = sql.length
        while (i < n) {
            val c = sql[i]
            when {
                c.isWhitespace() -> i++

                c == '-' && i + 1 < n && sql[i + 1] == '-' -> {
                    while (i < n && sql[i] != '\n') i++
                }

                c == '/' && i + 1 < n && sql[i + 1] == '*' -> {
                    val end = sql.indexOf("*/", i + 2)
                    if (end < 0) throw SqlException.invalid("unterminated comment", i)
                    i = end + 2
                }

                c == '\'' -> {
                    val (value, next) = readQuoted(sql, i, '\'')
                    out.add(SqlToken(TokenType.STRING, value, i, next))
                    i = next
                }

                c == '"' || c == '`' -> {
                    val (value, next) = readQuoted(sql, i, c)
                    if (value.isEmpty()) throw SqlException.invalid("empty identifier", i)
                    out.add(SqlToken(TokenType.QUOTED_IDENT, value, i, next))
                    i = next
                }

                c == '[' -> {
                    val end = sql.indexOf(']', i + 1)
                    if (end < 0) throw SqlException.invalid("unterminated identifier", i)
                    if (end == i + 1) throw SqlException.invalid("empty identifier", i)
                    out.add(SqlToken(TokenType.QUOTED_IDENT, sql.substring(i + 1, end), i, end + 1))
                    i = end + 1
                }

                (c == 'x' || c == 'X') && i + 1 < n && sql[i + 1] == '\'' -> {
                    throw SqlException.unsupported("blob literals", i)
                }

                c.isDigit() || (c == '.' && i + 1 < n && sql[i + 1].isDigit()) -> {
                    val start = i
                    i = readNumber(sql, i)
                    if (i < n && isIdentPart(sql[i])) throw SqlException.invalid("malformed number", start)
                    out.add(SqlToken(TokenType.NUMBER, sql.substring(start, i), start, i))
                }

                isIdentStart(c) -> {
                    val start = i
                    while (i < n && isIdentPart(sql[i])) i++
                    out.add(SqlToken(TokenType.IDENT, sql.substring(start, i), start, i))
                }

                c == '?' -> {
                    val start = i
                    i++
                    while (i < n && sql[i].isDigit()) i++
                    out.add(SqlToken(TokenType.PARAM, sql.substring(start, i), start, i))
                }

                c == ':' -> {
                    val start = i
                    i++
                    if (i >= n || !isIdentStart(sql[i])) throw SqlException.invalid("expected parameter name", start)
                    while (i < n && isIdentPart(sql[i])) i++
                    out.add(SqlToken(TokenType.PARAM, sql.substring(start, i), start, i))
                }

                else -> {
                    val two = if (i + 1 < n) sql.substring(i, i + 2) else ""
                    if (two in TWO_CHAR) {
                        out.add(SqlToken(TokenType.SYMBOL, two, i, i + 2))
                        i += 2
                    } else if (c in ONE_CHAR) {
                        out.add(SqlToken(TokenType.SYMBOL, c.toString(), i, i + 1))
                        i++
                    } else {
                        throw SqlException.unsupported("character '$c'", i)
                    }
                }
            }
        }
        out.add(SqlToken(TokenType.EOF, "", n, n))
        return out
    }

    private fun readQuoted(
        sql: String,
        start: Int,
        quote: Char,
    ): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < sql.length) {
            val c = sql[i]
            if (c == quote) {
                if (i + 1 < sql.length && sql[i + 1] == quote) {
                    sb.append(quote)
                    i += 2
                    continue
                }
                return sb.toString() to i + 1
            }
            sb.append(c)
            i++
        }
        throw SqlException.invalid("unterminated quoted text", start)
    }

    private fun readNumber(
        sql: String,
        start: Int,
    ): Int {
        var i = start
        val n = sql.length
        while (i < n && sql[i].isDigit()) i++
        if (i < n && sql[i] == '.') {
            i++
            while (i < n && sql[i].isDigit()) i++
        }
        if (i < n && (sql[i] == 'e' || sql[i] == 'E')) {
            var j = i + 1
            if (j < n && (sql[j] == '+' || sql[j] == '-')) j++
            if (j < n && sql[j].isDigit()) {
                i = j
                while (i < n && sql[i].isDigit()) i++
            }
        }
        return i
    }

    private fun isIdentStart(c: Char) = c == '_' || c in 'a'..'z' || c in 'A'..'Z'

    private fun isIdentPart(c: Char) = isIdentStart(c) || c.isDigit()
}
