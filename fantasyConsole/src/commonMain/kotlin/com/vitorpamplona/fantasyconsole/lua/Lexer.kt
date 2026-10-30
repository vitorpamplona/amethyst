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
package com.vitorpamplona.fantasyconsole.lua

enum class T {
    NAME,
    NUMBER,
    STRING,
    EOF,

    AND,
    BREAK,
    DO,
    ELSE,
    ELSEIF,
    END,
    FALSE,
    FOR,
    FUNCTION,
    GOTO,
    IF,
    IN,
    LOCAL,
    NIL,
    NOT,
    OR,
    REPEAT,
    RETURN,
    THEN,
    TRUE,
    UNTIL,
    WHILE,

    PLUS,
    MINUS,
    STAR,
    SLASH,
    BACKSLASH,
    PERCENT,
    CARET,
    HASH,
    AMP,
    TILDE,
    PIPE,
    XOR,
    SHL,
    SHR,
    LSHR,
    ROTL,
    ROTR,
    AT,
    DOLLAR,
    CONCAT,
    EQ,
    NE,
    LE,
    GE,
    LT,
    GT,
    ASSIGN,

    /** `+=`, `..=`, `>>>=`…: [Token.op] holds the operator. */
    COMPOUND,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    DBCOLON,
    SEMI,
    COLON,
    COMMA,
    DOT,
    DOTS,
    QUESTION,
}

class Token(
    val type: T,
    val line: Int,
    val text: String = "",
    val number: Int = 0,
    val op: T? = null,
)

class LuaSyntaxError(
    message: String,
    val line: Int,
) : Exception("syntax error line $line: $message")

/**
 * Splits console Lua into tokens. The source is P8SCII: one [Char] per byte, so glyphs past 127
 * are single characters and may appear in names (the button glyphs are predefined globals).
 *
 * On top of Lua 5.2 it reads `!=`, `//` comments, `\` (integer division), `^^` (xor),
 * `>>>` `<<>` `>><`, the peek operators `@` `%` `$`, `?` (print), every compound assignment
 * (`+=` … `>><=`), binary literals, and the P8SCII escapes `\*` `\#` `\-` `\|` `\+` `\^`.
 */
class Lexer(
    private val src: String,
) {
    private var pos = 0
    private var line = 1

    fun tokenize(): List<Token> {
        val out = ArrayList<Token>(src.length / 3)
        while (true) {
            val t = next()
            out.add(t)
            if (t.type == T.EOF) return out
        }
    }

    private fun peekChar(offset: Int = 0): Char = if (pos + offset < src.length) src[pos + offset] else '\u0000'

    private fun atEnd() = pos >= src.length

    private fun next(): Token {
        skipWhitespaceAndComments()
        if (atEnd()) return Token(T.EOF, line)
        val c = src[pos]
        val startLine = line

        if (isNameStart(c)) {
            val start = pos
            while (!atEnd() && isNamePart(src[pos])) pos++
            val word = src.substring(start, pos)
            val keyword = KEYWORDS[word]
            return if (keyword != null) Token(keyword, startLine, word) else Token(T.NAME, startLine, word)
        }
        if (c in '0'..'9' || (c == '.' && peekChar(1) in '0'..'9')) return number()
        if (c == '"' || c == '\'') return Token(T.STRING, startLine, quoted(c))
        if (c == '[' && (peekChar(1) == '[' || peekChar(1) == '=')) {
            val level = longBracketLevel()
            if (level >= 0) return Token(T.STRING, startLine, longString(level))
        }

        pos++

        fun compound(op: T): Token {
            pos++
            return Token(T.COMPOUND, startLine, op = op)
        }
        return when (c) {
            '+' -> if (peekChar() == '=') compound(T.PLUS) else Token(T.PLUS, startLine)
            '-' -> if (peekChar() == '=') compound(T.MINUS) else Token(T.MINUS, startLine)
            '*' -> if (peekChar() == '=') compound(T.STAR) else Token(T.STAR, startLine)
            '/' -> if (peekChar() == '=') compound(T.SLASH) else Token(T.SLASH, startLine)
            '\\' -> if (peekChar() == '=') compound(T.BACKSLASH) else Token(T.BACKSLASH, startLine)
            '%' -> if (peekChar() == '=') compound(T.PERCENT) else Token(T.PERCENT, startLine)
            '&' -> if (peekChar() == '=') compound(T.AMP) else Token(T.AMP, startLine)
            '|' -> if (peekChar() == '=') compound(T.PIPE) else Token(T.PIPE, startLine)
            '^' ->
                when {
                    peekChar() == '^' && peekChar(1) == '=' -> {
                        pos++
                        compound(T.XOR)
                    }
                    peekChar() == '^' -> {
                        pos++
                        Token(T.XOR, startLine)
                    }
                    peekChar() == '=' -> compound(T.CARET)
                    else -> Token(T.CARET, startLine)
                }
            '#' -> Token(T.HASH, startLine)
            '@' -> Token(T.AT, startLine)
            '$' -> Token(T.DOLLAR, startLine)
            '?' -> Token(T.QUESTION, startLine)
            '~' ->
                if (peekChar() == '=') {
                    pos++
                    Token(T.NE, startLine)
                } else {
                    Token(T.TILDE, startLine)
                }
            '!' ->
                if (peekChar() == '=') {
                    pos++
                    Token(T.NE, startLine)
                } else {
                    throw LuaSyntaxError("unexpected symbol near '!'", startLine)
                }
            '=' ->
                if (peekChar() == '=') {
                    pos++
                    Token(T.EQ, startLine)
                } else {
                    Token(T.ASSIGN, startLine)
                }
            '<' -> less(startLine)
            '>' -> greater(startLine)
            '(' -> Token(T.LPAREN, startLine)
            ')' -> Token(T.RPAREN, startLine)
            '{' -> Token(T.LBRACE, startLine)
            '}' -> Token(T.RBRACE, startLine)
            '[' -> Token(T.LBRACKET, startLine)
            ']' -> Token(T.RBRACKET, startLine)
            ';' -> Token(T.SEMI, startLine)
            ',' -> Token(T.COMMA, startLine)
            ':' ->
                if (peekChar() == ':') {
                    pos++
                    Token(T.DBCOLON, startLine)
                } else {
                    Token(T.COLON, startLine)
                }
            '.' ->
                when {
                    peekChar() == '.' && peekChar(1) == '.' -> {
                        pos += 2
                        Token(T.DOTS, startLine)
                    }
                    peekChar() == '.' && peekChar(1) == '=' -> {
                        pos++
                        compound(T.CONCAT)
                    }
                    peekChar() == '.' -> {
                        pos++
                        Token(T.CONCAT, startLine)
                    }
                    else -> Token(T.DOT, startLine)
                }
            else -> throw LuaSyntaxError("unexpected symbol near '$c'", startLine)
        }
    }

    private fun less(line: Int): Token {
        fun take(
            n: Int,
            type: T,
        ): Token {
            pos += n
            return if (peekChar() == '=') {
                pos++
                Token(T.COMPOUND, line, op = type)
            } else {
                Token(type, line)
            }
        }
        return when {
            peekChar() == '<' && peekChar(1) == '>' -> take(2, T.ROTL)
            peekChar() == '<' -> take(1, T.SHL)
            peekChar() == '=' -> {
                pos++
                Token(T.LE, line)
            }
            else -> Token(T.LT, line)
        }
    }

    private fun greater(line: Int): Token {
        fun take(
            n: Int,
            type: T,
        ): Token {
            pos += n
            return if (peekChar() == '=') {
                pos++
                Token(T.COMPOUND, line, op = type)
            } else {
                Token(type, line)
            }
        }
        return when {
            peekChar() == '>' && peekChar(1) == '>' -> take(2, T.LSHR)
            peekChar() == '>' && peekChar(1) == '<' -> take(2, T.ROTR)
            peekChar() == '>' -> take(1, T.SHR)
            peekChar() == '=' -> {
                pos++
                Token(T.GE, line)
            }
            else -> Token(T.GT, line)
        }
    }

    private fun skipWhitespaceAndComments() {
        while (!atEnd()) {
            val c = src[pos]
            when {
                c == '\n' -> {
                    line++
                    pos++
                }
                c == ' ' || c == '\t' || c == '\r' -> pos++
                c == '-' && peekChar(1) == '-' -> {
                    pos += 2
                    if (peekChar() == '[') {
                        val level = longBracketLevel()
                        if (level >= 0) {
                            longString(level)
                            continue
                        }
                    }
                    skipLine()
                }
                c == '/' && peekChar(1) == '/' -> skipLine()
                else -> return
            }
        }
    }

    private fun skipLine() {
        while (!atEnd() && src[pos] != '\n') pos++
    }

    /** At a `[`: the number of `=` in a long bracket `[==[`, or -1 (position unchanged) if not one. */
    private fun longBracketLevel(): Int {
        var i = pos + 1
        var level = 0
        while (i < src.length && src[i] == '=') {
            level++
            i++
        }
        if (i < src.length && src[i] == '[') {
            pos = i + 1
            return level
        }
        return -1
    }

    private fun longString(level: Int): String {
        // A newline right after the opening bracket is not part of the string.
        if (peekChar() == '\r') pos++
        if (peekChar() == '\n') {
            pos++
            line++
        }
        val sb = StringBuilder()
        val startLine = line
        while (true) {
            if (atEnd()) throw LuaSyntaxError("unfinished long string", startLine)
            val c = src[pos]
            if (c == ']') {
                var i = pos + 1
                var l = 0
                while (i < src.length && src[i] == '=') {
                    l++
                    i++
                }
                if (l == level && i < src.length && src[i] == ']') {
                    pos = i + 1
                    return sb.toString()
                }
            }
            if (c == '\n') line++
            sb.append(c)
            pos++
        }
    }

    private fun quoted(quote: Char): String {
        val startLine = line
        pos++
        val sb = StringBuilder()
        while (true) {
            if (atEnd() || src[pos] == '\n') throw LuaSyntaxError("unfinished string", startLine)
            val c = src[pos]
            if (c == quote) {
                pos++
                return sb.toString()
            }
            if (c != '\\') {
                sb.append(c)
                pos++
                continue
            }
            pos++
            val e = peekChar()
            pos++
            when (e) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                'a' -> sb.append('\u0007')
                'b' -> sb.append('\u0008')
                'f' -> sb.append('\u000c')
                'v' -> sb.append('\u000b')
                '*' -> sb.append('\u0001')
                '#' -> sb.append('\u0002')
                '-' -> sb.append('\u0003')
                '|' -> sb.append('\u0004')
                '+' -> sb.append('\u0005')
                '^' -> sb.append('\u0006')
                '\n' -> {
                    line++
                    sb.append('\n')
                }
                'x' -> {
                    val hi = hexDigit(peekChar())
                    val lo = hexDigit(peekChar(1))
                    if (hi < 0 || lo < 0) throw LuaSyntaxError("hexadecimal digit expected", line)
                    pos += 2
                    sb.append((hi * 16 + lo).toChar())
                }
                'z' -> {
                    while (!atEnd() && (src[pos] == ' ' || src[pos] == '\t' || src[pos] == '\r' || src[pos] == '\n')) {
                        if (src[pos] == '\n') line++
                        pos++
                    }
                }
                in '0'..'9' -> {
                    var v = e - '0'
                    var n = 1
                    while (n < 3 && peekChar() in '0'..'9') {
                        v = v * 10 + (peekChar() - '0')
                        pos++
                        n++
                    }
                    if (v > 255) throw LuaSyntaxError("decimal escape too large", line)
                    sb.append(v.toChar())
                }
                else -> sb.append(e)
            }
        }
    }

    private fun number(): Token {
        val start = pos
        val startLine = line
        if (src[pos] == '0' && (peekChar(1) == 'x' || peekChar(1) == 'X' || peekChar(1) == 'b' || peekChar(1) == 'B')) {
            val radix = if (peekChar(1) == 'x' || peekChar(1) == 'X') 16 else 2
            pos += 2
            while (!atEnd() && (hexDigit(src[pos]) in 0 until radix || src[pos] == '.')) {
                if (src[pos] == '.' && peekChar(1) == '.') break
                pos++
            }
        } else {
            while (!atEnd()) {
                val c = src[pos]
                if (c in '0'..'9') {
                    pos++
                } else if (c == '.' && peekChar(1) != '.') {
                    pos++
                } else {
                    break
                }
            }
        }
        val text = src.substring(start, pos)
        val value = Fix.parse(text) ?: throw LuaSyntaxError("malformed number near '$text'", startLine)
        return Token(T.NUMBER, startLine, text, value)
    }

    companion object {
        val KEYWORDS =
            mapOf(
                "and" to T.AND,
                "break" to T.BREAK,
                "do" to T.DO,
                "else" to T.ELSE,
                "elseif" to T.ELSEIF,
                "end" to T.END,
                "false" to T.FALSE,
                "for" to T.FOR,
                "function" to T.FUNCTION,
                "goto" to T.GOTO,
                "if" to T.IF,
                "in" to T.IN,
                "local" to T.LOCAL,
                "nil" to T.NIL,
                "not" to T.NOT,
                "or" to T.OR,
                "repeat" to T.REPEAT,
                "return" to T.RETURN,
                "then" to T.THEN,
                "true" to T.TRUE,
                "until" to T.UNTIL,
                "while" to T.WHILE,
            )

        fun isNameStart(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c.code in 128..255

        fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'

        fun hexDigit(c: Char): Int =
            when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> -1
            }
    }
}
