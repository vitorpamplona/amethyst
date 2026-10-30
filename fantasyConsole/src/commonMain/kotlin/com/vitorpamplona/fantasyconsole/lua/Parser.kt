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

/** One function being parsed: its locals in scope, their slots, and the upvalues it pulls in. */
private class FuncState(
    val parent: FuncState?,
    val name: String,
) {
    val actives = ArrayList<LocalVar>()
    val params = ArrayList<LocalVar>()
    val upvals = ArrayList<UpvalDesc>()
    var freeSlot = 0
    var maxSlots = 0
    var isVararg = false

    fun declare(name: String): LocalVar {
        val v = LocalVar(name, freeSlot++)
        if (freeSlot > maxSlots) maxSlots = freeSlot
        actives.add(v)
        return v
    }

    fun findLocal(name: String): LocalVar? {
        for (i in actives.indices.reversed()) if (actives[i].name == name) return actives[i]
        return null
    }

    /** The index of an upvalue for [name] in this function, adding it (and its parents') if needed. */
    fun findUpval(name: String): Int? {
        val existing = upvals.indexOfFirst { it.name == name }
        if (existing >= 0) return existing
        val p = parent ?: return null
        val local = p.findLocal(name)
        if (local != null) {
            local.captured = true
            upvals.add(UpvalDesc(name, local, -1))
            return upvals.size - 1
        }
        val index = p.findUpval(name) ?: return null
        upvals.add(UpvalDesc(name, null, index))
        return upvals.size - 1
    }

    fun finish(
        body: Block,
        line: Int,
    ) = FunctionProto(name, params.toTypedArray(), isVararg, upvals.toTypedArray(), body, maxSlots, line)
}

/**
 * Parses console Lua (Lua 5.2 plus the console's shorthands) straight into runnable nodes.
 *
 * The one-line forms `if (c) stmt [else stmt]` and `while (c) stmt` are recognized the way the
 * console does: the condition is in parentheses and the token after them can't continue an
 * expression (a name, a keyword, `?`), so `if (a) and b then` stays a normal if.
 */
class Parser(
    source: String,
    private val chunkName: String = "main",
) {
    private val tokens = Lexer(source).tokenize()
    private var p = 0
    private var fs = FuncState(null, chunkName)

    private val peek: Token get() = tokens[p]

    private fun peekAt(offset: Int): Token = tokens[minOf(p + offset, tokens.size - 1)]

    private fun next(): Token = tokens[p].also { if (p < tokens.size - 1) p++ }

    private fun check(type: T) = peek.type == type

    private fun accept(type: T): Boolean {
        if (peek.type != type) return false
        next()
        return true
    }

    private fun expect(
        type: T,
        what: String,
    ): Token {
        if (peek.type != type) throw error("'$what' expected near '${describe(peek)}'")
        return next()
    }

    private fun expectName(): String = expect(T.NAME, "<name>").text

    private fun error(message: String) = LuaSyntaxError(message, peek.line)

    private fun describe(t: Token): String =
        when (t.type) {
            T.EOF -> "<eof>"
            T.NAME, T.NUMBER -> t.text
            T.STRING -> "string"
            else -> t.text.ifEmpty { t.type.name.lowercase() }
        }

    fun parseChunk(): FunctionProto {
        fs.isVararg = true
        val body = blockNoScope(-1)
        if (!check(T.EOF)) throw error("'<eof>' expected near '${describe(peek)}'")
        return fs.finish(body, 0)
    }

    // ---------------------------------------------------------------- blocks

    private fun block(lineLimit: Int = -1): Block {
        val mark = fs.actives.size
        val slotMark = fs.freeSlot
        val b = blockNoScope(lineLimit)
        leaveScope(mark, slotMark)
        return b
    }

    private fun leaveScope(
        mark: Int,
        slotMark: Int,
    ) {
        while (fs.actives.size > mark) fs.actives.removeAt(fs.actives.size - 1)
        fs.freeSlot = slotMark
    }

    /** True where a block's statements stop; a one-line body also stops at the end of its line. */
    private fun blockFollow(lineLimit: Int): Boolean =
        when (peek.type) {
            T.ELSE, T.ELSEIF, T.END, T.UNTIL, T.EOF -> true
            else -> lineLimit >= 0 && peek.line != lineLimit
        }

    private fun blockNoScope(lineLimit: Int): Block {
        val stats = ArrayList<Stat>()
        while (!blockFollow(lineLimit)) {
            if (check(T.RETURN)) {
                stats.add(returnStat(lineLimit))
                break
            }
            statement()?.let { stats.add(it) }
        }
        return Block(stats.toTypedArray())
    }

    // ---------------------------------------------------------------- statements

    private fun statement(): Stat? {
        val line = peek.line
        return when (peek.type) {
            T.SEMI -> {
                next()
                null
            }
            T.IF -> ifStat()
            T.WHILE -> whileStat()
            T.DO -> {
                next()
                val b = block()
                expect(T.END, "end")
                DoStat(line, b)
            }
            T.FOR -> forStat()
            T.REPEAT -> repeatStat()
            T.FUNCTION -> functionStat()
            T.LOCAL -> {
                next()
                if (accept(T.FUNCTION)) localFunction(line) else localStat(line)
            }
            T.DBCOLON -> {
                next()
                val name = expectName()
                expect(T.DBCOLON, "::")
                LabelStat(line, name)
            }
            T.BREAK -> {
                next()
                BreakStat(line)
            }
            T.GOTO -> {
                next()
                GotoStat(line, expectName())
            }
            T.QUESTION -> {
                next()
                val args = if (peek.line == line && !blockFollow(line)) exprList() else emptyArray()
                CallStat(line, CallExpr(GlobalExpr("print"), args))
            }
            else -> exprStat()
        }
    }

    private fun returnStat(lineLimit: Int): Stat {
        val line = next().line
        val values =
            if (blockFollow(lineLimit) || check(T.SEMI) || (lineLimit >= 0 && peek.line != line)) {
                emptyArray()
            } else {
                exprList()
            }
        accept(T.SEMI)
        return ReturnStat(line, values)
    }

    /**
     * At `(`: whether this `if`/`while` is the one-line form, i.e. the parenthesized condition is
     * not followed by [keyword] and the next token on its line can't continue the expression.
     */
    private fun isShortForm(keyword: T): Boolean {
        if (!check(T.LPAREN)) return false
        var depth = 0
        var i = p
        while (true) {
            val t = tokens[i]
            when (t.type) {
                T.LPAREN -> depth++
                T.RPAREN -> {
                    depth--
                    if (depth == 0) break
                }
                T.EOF -> return false
                else -> {}
            }
            i++
        }
        val close = tokens[i]
        val after = tokens[minOf(i + 1, tokens.size - 1)]
        if (after.type == keyword || after.line != close.line || after.type == T.EOF) return false
        return after.type !in CONTINUES_EXPRESSION
    }

    private fun ifStat(): Stat {
        val line = next().line
        if (isShortForm(T.THEN)) {
            val cond = expr()
            val limit = tokens[p - 1].line
            val body = block(limit)
            var elseBlock: Block? = null
            if (check(T.ELSE) && peek.line == limit) {
                next()
                elseBlock = block(limit)
            }
            return IfStat(line, arrayOf(cond), arrayOf(body), elseBlock)
        }
        val conds = ArrayList<Expr>()
        val blocks = ArrayList<Block>()
        conds.add(expr())
        expectThen()
        blocks.add(block())
        var elseBlock: Block? = null
        while (true) {
            if (accept(T.ELSEIF)) {
                conds.add(expr())
                expectThen()
                blocks.add(block())
            } else if (accept(T.ELSE)) {
                elseBlock = block()
                expect(T.END, "end")
                break
            } else {
                expect(T.END, "end")
                break
            }
        }
        return IfStat(line, conds.toTypedArray(), blocks.toTypedArray(), elseBlock)
    }

    /** `then`, or `do`, which the console also takes after an if's condition. */
    private fun expectThen() {
        if (!accept(T.DO)) expect(T.THEN, "then")
    }

    private fun whileStat(): Stat {
        val line = next().line
        if (isShortForm(T.DO)) {
            val cond = expr()
            return WhileStat(line, cond, block(tokens[p - 1].line))
        }
        val cond = expr()
        expect(T.DO, "do")
        val body = block()
        expect(T.END, "end")
        return WhileStat(line, cond, body)
    }

    private fun repeatStat(): Stat {
        val line = next().line
        // The condition sees the body's locals, so the scope closes after it.
        val mark = fs.actives.size
        val slotMark = fs.freeSlot
        val body = blockNoScope(-1)
        expect(T.UNTIL, "until")
        val cond = expr()
        leaveScope(mark, slotMark)
        return RepeatStat(line, body, cond)
    }

    private fun forStat(): Stat {
        val line = next().line
        val first = expectName()
        val mark = fs.actives.size
        val slotMark = fs.freeSlot
        if (accept(T.ASSIGN)) {
            val start = expr()
            expect(T.COMMA, ",")
            val limit = expr()
            val step = if (accept(T.COMMA)) expr() else null
            expect(T.DO, "do")
            val v = fs.declare(first)
            val body = block()
            expect(T.END, "end")
            leaveScope(mark, slotMark)
            return NumForStat(line, v, start, limit, step, body)
        }
        val names = arrayListOf(first)
        while (accept(T.COMMA)) names.add(expectName())
        expect(T.IN, "in")
        val exprs = exprList()
        expect(T.DO, "do")
        val vars = names.map { fs.declare(it) }.toTypedArray()
        val body = block()
        expect(T.END, "end")
        leaveScope(mark, slotMark)
        return GenForStat(line, vars, exprs, body)
    }

    private fun functionStat(): Stat {
        val line = next().line
        var name = expectName()
        var target: Expr = singleVar(name)
        var isMethod = false
        while (check(T.DOT) || check(T.COLON)) {
            isMethod = next().type == T.COLON
            val field = expectName()
            name = field
            target = IndexExpr(target, ConstExpr(field))
            if (isMethod) break
        }
        val fn = functionBody(isMethod, name, line)
        return AssignStat(line, arrayOf(target), arrayOf(fn))
    }

    private fun localFunction(line: Int): Stat {
        val name = expectName()
        val v = fs.declare(name)
        return LocalFunctionStat(line, v, functionBody(false, name, line))
    }

    private fun localStat(line: Int): Stat {
        val names = arrayListOf(expectName())
        while (accept(T.COMMA)) names.add(expectName())
        val exprs = if (accept(T.ASSIGN)) exprList() else emptyArray()
        // Declared after the values, so `local x = x` reads the outer x.
        val vars = names.map { fs.declare(it) }.toTypedArray()
        return LocalStat(line, vars, exprs)
    }

    private fun exprStat(): Stat {
        val line = peek.line
        val e = suffixedExpr()
        if (check(T.ASSIGN) || check(T.COMMA)) {
            val targets = arrayListOf(e)
            while (accept(T.COMMA)) targets.add(suffixedExpr())
            expect(T.ASSIGN, "=")
            val exprs = exprList()
            for (t in targets) if (t !is Assignable) throw LuaSyntaxError("cannot assign to this expression", line)
            return AssignStat(line, targets.toTypedArray(), exprs)
        }
        if (check(T.COMPOUND)) {
            val op = next().op!!
            if (e !is Assignable) throw LuaSyntaxError("cannot assign to this expression", line)
            // Like the console, `a[f()] += 1` evaluates the target twice.
            return AssignStat(line, arrayOf(e), arrayOf(makeBinary(op, e, expr())))
        }
        if (e is CallExpr || e is MethodCallExpr) return CallStat(line, e)
        throw LuaSyntaxError("syntax error near '${describe(peek)}'", line)
    }

    // ---------------------------------------------------------------- functions

    private fun functionBody(
        isMethod: Boolean,
        name: String,
        line: Int,
    ): FunctionExpr {
        val child = FuncState(fs, name)
        fs = child
        if (isMethod) child.params.add(child.declare("self"))
        expect(T.LPAREN, "(")
        if (!check(T.RPAREN)) {
            do {
                if (accept(T.DOTS)) {
                    child.isVararg = true
                    break
                }
                child.params.add(child.declare(expectName()))
            } while (accept(T.COMMA))
        }
        expect(T.RPAREN, ")")
        val body = block()
        expect(T.END, "end")
        fs = child.parent!!
        return FunctionExpr(child.finish(body, line))
    }

    // ---------------------------------------------------------------- expressions

    private fun exprList(): Array<Expr> {
        val list = arrayListOf(expr())
        while (accept(T.COMMA)) list.add(expr())
        return list.toTypedArray()
    }

    private fun expr(): Expr = subExpr(0)

    private fun subExpr(limit: Int): Expr {
        var left =
            if (peek.type in UNARY_OPS) {
                val op = next().type
                val operand = subExpr(UNARY_PRIORITY)
                val constant = (operand as? ConstExpr)?.value
                if (op == T.MINUS && constant is Int) {
                    ConstExpr(-constant)
                } else {
                    UnExpr(op, operand)
                }
            } else {
                simpleExpr()
            }
        while (true) {
            val priority = BINARY_PRIORITY[peek.type] ?: break
            if (priority.first <= limit) break
            val op = next().type
            val right = subExpr(priority.second)
            left = makeBinary(op, left, right)
        }
        return left
    }

    private fun makeBinary(
        op: T,
        a: Expr,
        b: Expr,
    ): Expr =
        when (op) {
            T.AND -> AndExpr(a, b)
            T.OR -> OrExpr(a, b)
            else -> BinExpr(op, a, b)
        }

    private fun simpleExpr(): Expr {
        val t = peek
        return when (t.type) {
            T.NUMBER -> {
                next()
                ConstExpr(t.number)
            }
            T.STRING -> {
                next()
                ConstExpr(t.text)
            }
            T.NIL -> {
                next()
                ConstExpr(null)
            }
            T.TRUE -> {
                next()
                ConstExpr(true)
            }
            T.FALSE -> {
                next()
                ConstExpr(false)
            }
            T.DOTS -> {
                next()
                if (!fs.isVararg) throw error("cannot use '...' outside a vararg function")
                VarargExpr()
            }
            T.LBRACE -> table()
            T.FUNCTION -> functionBody(false, "anonymous", next().line)
            T.QUESTION -> {
                // `?` is shorthand for print( … ) to the end of the line, even inside an expression.
                val line = next().line
                val args = if (peek.line == line && !blockFollow(line)) exprList() else emptyArray()
                CallExpr(GlobalExpr("print"), args)
            }
            else -> suffixedExpr()
        }
    }

    private fun primaryExpr(): Expr =
        when (peek.type) {
            T.NAME -> singleVar(next().text)
            T.LPAREN -> {
                next()
                val e = expr()
                expect(T.RPAREN, ")")
                if (e.isMulti) ParenExpr(e) else e
            }
            else -> throw error("unexpected symbol near '${describe(peek)}'")
        }

    private fun suffixedExpr(): Expr {
        var e = primaryExpr()
        while (true) {
            e =
                when (peek.type) {
                    T.DOT -> {
                        next()
                        IndexExpr(e, ConstExpr(expectName()))
                    }
                    T.LBRACKET -> {
                        next()
                        val k = expr()
                        expect(T.RBRACKET, "]")
                        IndexExpr(e, k)
                    }
                    T.COLON -> {
                        next()
                        val name = expectName()
                        MethodCallExpr(e, name, callArgs())
                    }
                    T.LPAREN, T.STRING, T.LBRACE -> CallExpr(e, callArgs())
                    else -> return e
                }
        }
    }

    private fun callArgs(): Array<Expr> =
        when (peek.type) {
            T.STRING -> arrayOf(ConstExpr(next().text))
            T.LBRACE -> arrayOf(table())
            T.LPAREN -> {
                next()
                if (accept(T.RPAREN)) {
                    emptyArray()
                } else {
                    val args = exprList()
                    expect(T.RPAREN, ")")
                    args
                }
            }
            else -> throw error("function arguments expected")
        }

    private fun table(): Expr {
        expect(T.LBRACE, "{")
        val items = ArrayList<TableItem>()
        while (!check(T.RBRACE)) {
            if (check(T.LBRACKET)) {
                next()
                val k = expr()
                expect(T.RBRACKET, "]")
                expect(T.ASSIGN, "=")
                items.add(TableItem(k, expr()))
            } else if (check(T.NAME) && peekAt(1).type == T.ASSIGN) {
                val k = next().text
                next()
                items.add(TableItem(ConstExpr(k), expr()))
            } else {
                items.add(TableItem(null, expr()))
            }
            if (!accept(T.COMMA) && !accept(T.SEMI)) break
        }
        expect(T.RBRACE, "}")
        return TableExpr(items.toTypedArray())
    }

    /** A name: a local, an upvalue, a field of a local `_ENV`, or a global. */
    private fun singleVar(name: String): Expr {
        resolve(name)?.let { return it }
        resolve("_ENV")?.let { return IndexExpr(it, ConstExpr(name)) }
        return if (name == "_ENV") GlobalsExpr() else GlobalExpr(name)
    }

    private fun resolve(name: String): Expr? {
        fs.findLocal(name)?.let { return LocalExpr(it) }
        val index = fs.findUpval(name) ?: return null
        return UpvalExpr(index, name)
    }

    companion object {
        private const val UNARY_PRIORITY = 12

        private val UNARY_OPS = setOf(T.NOT, T.MINUS, T.HASH, T.TILDE, T.AT, T.PERCENT, T.DOLLAR)

        private val BINARY_PRIORITY: Map<T, Pair<Int, Int>> =
            mapOf(
                T.OR to (1 to 1),
                T.AND to (2 to 2),
                T.LT to (3 to 3),
                T.GT to (3 to 3),
                T.LE to (3 to 3),
                T.GE to (3 to 3),
                T.NE to (3 to 3),
                T.EQ to (3 to 3),
                T.PIPE to (4 to 4),
                T.XOR to (5 to 5),
                T.TILDE to (5 to 5),
                T.AMP to (6 to 6),
                T.SHL to (7 to 7),
                T.SHR to (7 to 7),
                T.LSHR to (7 to 7),
                T.ROTL to (7 to 7),
                T.ROTR to (7 to 7),
                T.CONCAT to (9 to 8),
                T.PLUS to (10 to 10),
                T.MINUS to (10 to 10),
                T.STAR to (11 to 11),
                T.SLASH to (11 to 11),
                T.BACKSLASH to (11 to 11),
                T.PERCENT to (11 to 11),
                T.CARET to (14 to 13),
            )

        /** Tokens that, right after `if (…)`, mean the condition goes on rather than a one-line body starting. */
        private val CONTINUES_EXPRESSION: Set<T> =
            BINARY_PRIORITY.keys + setOf(T.DOT, T.LBRACKET, T.COLON, T.LPAREN, T.STRING, T.LBRACE)
    }
}
