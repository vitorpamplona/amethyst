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

import kotlin.concurrent.Volatile
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/** What the `@`, `%` and `$` operators read: the console's memory. */
interface LuaMemory {
    fun peek(addr: Int): Int

    fun peek2(addr: Int): Int

    fun peek4(addr: Int): Int
}

/**
 * Runs a single task at a time so Lua coroutines never stack on each other: `coresume` and
 * `yield` post the other side and suspend, unwinding to [run], which picks the posted task up.
 * A cart that resumes a coroutine thousands of times in one frame keeps a flat native stack.
 */
class Scheduler {
    private var next: (() -> Unit)? = null

    fun post(task: () -> Unit) {
        check(next == null) { "two tasks posted at once" }
        next = task
    }

    fun run() {
        while (true) {
            val task = next ?: return
            next = null
            task()
        }
    }
}

/**
 * The state every running chunk shares: globals, the coroutine scheduler and the operations
 * that may reach a metatable. Nodes call in here only off their fast path (both operands
 * numbers, a plain table), so most arithmetic and indexing never does.
 */
class Interpreter {
    val globals = LuaTable()
    val scheduler = Scheduler()
    var memory: LuaMemory? = null

    /** Lua call depth of whatever runs now; each coroutine keeps its own. */
    var depth = 0

    /** The coroutine running now, null for the main program. */
    var current: LuaCoroutine? = null
        private set

    @Volatile
    var abortRequested = false

    /**
     * Called every [BACK_EDGE_SAMPLE] loop iterations and backward gotos. The console uses it to
     * end a frame that has run out of time, as the original does, so a cart that busy-waits on
     * `time()` or `btnp()` without `flip()` still sees time pass and buttons change.
     */
    var frameHook: (suspend () -> Unit)? = null
    private var backEdges = 0

    /** A loop's back edge: stops an aborted cart and, now and then, lets [frameHook] end the frame. */
    suspend fun backEdge() {
        if (abortRequested) throw LuaAbort()
        if (++backEdges and (BACK_EDGE_SAMPLE - 1) == 0) frameHook?.invoke()
    }

    /** Compiles [source] into the function a cart's top-level code runs as. */
    fun load(
        source: String,
        name: String = "main",
    ): Closure = Closure(Parser(source, name).parseChunk(), emptyArray())

    fun register(
        name: String,
        body: suspend (Values) -> Values,
    ) {
        globals.rawset(name, NativeFunction(name, body))
    }

    // ---------------------------------------------------------------- indexing

    suspend fun index(
        obj: Any?,
        key: Any?,
        source: Expr? = null,
    ): Any? {
        var o = obj
        repeat(MAX_META_CHAIN) {
            if (o is LuaTable) {
                val t = o
                val v = t.rawget(key)
                if (v != null) return v
                val handler = t.metatable?.rawget("__index") ?: return null
                if (handler is LuaFunction) return handler.call(this, listOf(t, key)).firstOrNull()
                o = handler
            } else if (o is String) {
                // `s[i]` is the i-th character (1-based); anything else on a string is nil.
                val s = o
                if (key !is Int || key and 0xffff != 0) return null
                val i = key shr 16
                return if (i in 1..s.length) s[i - 1].toString() else null
            } else {
                throw LuaError("attempt to index a ${typeName(o)} value${describe(source)}")
            }
        }
        throw LuaError("'__index' chain too long; possible loop")
    }

    suspend fun setIndex(
        obj: Any?,
        key: Any?,
        value: Any?,
        source: Expr? = null,
    ) {
        var o = obj
        repeat(MAX_META_CHAIN) {
            if (o is LuaTable) {
                val t = o
                val mt = t.metatable
                if (mt == null || t.rawget(key) != null) {
                    t.rawset(key, value)
                    return
                }
                val handler = mt.rawget("__newindex")
                if (handler == null) {
                    t.rawset(key, value)
                    return
                }
                if (handler is LuaFunction) {
                    handler.call(this, listOf(t, key, value))
                    return
                }
                o = handler
            } else {
                throw LuaError("attempt to index a ${typeName(o)} value${describe(source)}")
            }
        }
        throw LuaError("'__newindex' chain too long; possible loop")
    }

    // ---------------------------------------------------------------- calls

    suspend fun call(
        fn: Any?,
        args: Values,
        source: Expr? = null,
        what: String? = null,
    ): Values {
        if (fn is LuaFunction) return fn.call(this, args)
        if (fn is LuaTable) {
            val handler = fn.metatable?.rawget("__call")
            if (handler != null) return call(handler, listOf(fn) + args)
        }
        val name = what?.let { " ($it)" } ?: describe(source)
        throw LuaError("attempt to call a ${typeName(fn)} value$name")
    }

    private fun describe(source: Expr?): String = source?.describe()?.let { " ($it)" } ?: ""

    // ---------------------------------------------------------------- operators

    fun toNumber(v: Any?): Int? =
        when (v) {
            is Int -> v
            is String -> Fix.parse(v.trim())
            else -> null
        }

    fun forNumber(
        v: Any?,
        what: String,
    ): Int = toNumber(v) ?: throw LuaError("'for' $what value must be a number")

    private fun metamethod(
        a: Any?,
        b: Any?,
        event: String,
    ): Any? = (a as? LuaTable)?.metatable?.rawget(event) ?: (b as? LuaTable)?.metatable?.rawget(event)

    suspend fun binary(
        op: T,
        x: Any?,
        y: Any?,
        a: Expr? = null,
        b: Expr? = null,
    ): Any? {
        when (op) {
            T.EQ -> return equals(x, y)
            T.NE -> return !equals(x, y)
            T.LT -> return lessThan(x, y)
            T.LE -> return lessEqual(x, y)
            T.GT -> return lessThan(y, x)
            T.GE -> return lessEqual(y, x)
            T.CONCAT -> return concat(x, y, a, b)
            else -> {}
        }
        val nx = toNumber(x)
        val ny = toNumber(y)
        if (nx != null && ny != null) return arith(op, nx, ny)
        val event = ARITH_EVENTS[op] ?: throw LuaError("unsupported operator")
        val handler = metamethod(x, y, event)
        if (handler != null) return call(handler, listOf(x, y)).firstOrNull()
        val (culprit, source) = if (nx == null) x to a else y to b
        val verb = if (op in BITWISE) "perform bitwise operation on" else "perform arithmetic on"
        throw LuaError("attempt to $verb a ${typeName(culprit)} value${describe(source)}")
    }

    fun arith(
        op: T,
        x: Int,
        y: Int,
    ): Int =
        when (op) {
            T.PLUS -> x + y
            T.MINUS -> x - y
            T.STAR -> Fix.mul(x, y)
            T.SLASH -> Fix.div(x, y)
            T.PERCENT -> Fix.mod(x, y)
            T.BACKSLASH -> Fix.idiv(x, y)
            T.CARET -> Fix.pow(x, y)
            T.AMP -> x and y
            T.PIPE -> x or y
            T.XOR, T.TILDE -> x xor y
            T.SHL -> Fix.shl(x, y)
            T.SHR -> Fix.shr(x, y)
            T.LSHR -> Fix.lshr(x, y)
            T.ROTL -> Fix.rotl(x, y)
            T.ROTR -> Fix.rotr(x, y)
            else -> throw LuaError("unsupported operator")
        }

    suspend fun unary(
        op: T,
        x: Any?,
        source: Expr? = null,
    ): Any? {
        when (op) {
            T.HASH -> return length(x, source)
            T.AT, T.PERCENT, T.DOLLAR -> {
                val addr = Fix.toInt(toNumber(x) ?: 0)
                val mem = memory ?: return 0
                return when (op) {
                    T.AT -> Fix.fromInt(mem.peek(addr))
                    T.PERCENT -> Fix.fromInt(mem.peek2(addr))
                    else -> mem.peek4(addr)
                }
            }
            else -> {}
        }
        val n = toNumber(x)
        if (n != null) return if (op == T.MINUS) -n else n.inv()
        val handler = metamethod(x, null, if (op == T.MINUS) "__unm" else "__bnot")
        if (handler != null) return call(handler, listOf(x, x)).firstOrNull()
        val verb = if (op == T.TILDE) "perform bitwise operation on" else "perform arithmetic on"
        throw LuaError("attempt to $verb a ${typeName(x)} value${describe(source)}")
    }

    suspend fun length(
        x: Any?,
        source: Expr? = null,
    ): Any? {
        if (x is String) return Fix.fromInt(x.length)
        if (x is LuaTable) {
            val handler = x.metatable?.rawget("__len")
            if (handler != null) return call(handler, listOf(x)).firstOrNull()
            return Fix.fromInt(x.length())
        }
        throw LuaError("attempt to get length of a ${typeName(x)} value${describe(source)}")
    }

    suspend fun equals(
        x: Any?,
        y: Any?,
    ): Boolean {
        if (x == y) return true
        if (x is LuaTable && y is LuaTable) {
            val handler = metamethod(x, y, "__eq") ?: return false
            return truthy(call(handler, listOf(x, y)).firstOrNull())
        }
        return false
    }

    suspend fun lessThan(
        x: Any?,
        y: Any?,
    ): Boolean {
        if (x is Int && y is Int) return x < y
        if (x is String && y is String) return x < y
        val handler = metamethod(x, y, "__lt") ?: throw compareError(x, y)
        return truthy(call(handler, listOf(x, y)).firstOrNull())
    }

    suspend fun lessEqual(
        x: Any?,
        y: Any?,
    ): Boolean {
        if (x is Int && y is Int) return x <= y
        if (x is String && y is String) return x <= y
        val handler = metamethod(x, y, "__le")
        if (handler != null) return truthy(call(handler, listOf(x, y)).firstOrNull())
        val lt = metamethod(x, y, "__lt") ?: throw compareError(x, y)
        return !truthy(call(lt, listOf(y, x)).firstOrNull())
    }

    private fun compareError(
        x: Any?,
        y: Any?,
    ): LuaError {
        val tx = typeName(x)
        val ty = typeName(y)
        return LuaError(if (tx == ty) "attempt to compare two $tx values" else "attempt to compare $tx with $ty")
    }

    suspend fun concat(
        x: Any?,
        y: Any?,
        a: Expr? = null,
        b: Expr? = null,
    ): Any? {
        if ((x is String || x is Int) && (y is String || y is Int)) return tostr(x) + tostr(y)
        val handler = metamethod(x, y, "__concat")
        if (handler != null) return call(handler, listOf(x, y)).firstOrNull()
        val (culprit, source) = if (x is String || x is Int) y to b else x to a
        throw LuaError("attempt to concatenate a ${typeName(culprit)} value${describe(source)}")
    }

    // ---------------------------------------------------------------- coroutines

    /** `coresume`: runs [co] until it yields or ends; `true, values…` or `false, message`. */
    suspend fun resume(
        co: LuaCoroutine,
        args: Values,
    ): Values {
        if (co.status == "dead") return listOf(false, "cannot resume dead coroutine")
        if (co.status != "suspended") return listOf(false, "cannot resume non-suspended coroutine")
        val previous = current
        val callerDepth = depth
        previous?.status = "normal"
        co.status = "running"
        current = co
        depth = co.depth
        val result =
            suspendCoroutine { caller ->
                co.resumer = caller
                if (!co.started) {
                    co.started = true
                    scheduler.post {
                        val body: suspend () -> Values = { co.fn.call(this, args) }
                        body.startCoroutine(Continuation(EmptyCoroutineContext) { finish(co, it) })
                    }
                } else {
                    val body = co.body!!
                    co.body = null
                    scheduler.post { body.resume(args) }
                }
            }
        co.depth = depth
        depth = callerDepth
        current = previous
        previous?.status = "running"
        return result
    }

    private fun finish(
        co: LuaCoroutine,
        result: Result<Values>,
    ) {
        co.status = "dead"
        val resumer = co.resumer!!
        co.resumer = null
        val error = result.exceptionOrNull()
        when {
            error == null -> scheduler.post { resumer.resume(listOf<Any?>(true) + result.getOrThrow()) }
            error is LuaError -> scheduler.post { resumer.resume(listOf(false, error.luaMessage)) }
            else -> scheduler.post { resumer.resumeWithException(error) }
        }
    }

    /** `yield`: hands [values] to whoever resumed the running coroutine; returns what the next resume passes. */
    suspend fun yieldValues(values: Values): Values {
        val co = current ?: throw LuaError("attempt to yield from outside a coroutine")
        co.status = "suspended"
        return suspendCoroutine { k ->
            co.body = k
            val resumer = co.resumer!!
            co.resumer = null
            scheduler.post { resumer.resume(listOf<Any?>(true) + values) }
        }
    }

    companion object {
        /** Nested Lua calls before "stack overflow". The original allows deep recursion too. */
        const val MAX_DEPTH = 50000
        private const val BACK_EDGE_SAMPLE = 1024
        private const val MAX_META_CHAIN = 100

        private val ARITH_EVENTS =
            mapOf(
                T.PLUS to "__add",
                T.MINUS to "__sub",
                T.STAR to "__mul",
                T.SLASH to "__div",
                T.PERCENT to "__mod",
                T.CARET to "__pow",
                T.BACKSLASH to "__idiv",
                T.AMP to "__band",
                T.PIPE to "__bor",
                T.XOR to "__bxor",
                T.TILDE to "__bxor",
                T.SHL to "__shl",
                T.SHR to "__shr",
                T.LSHR to "__lshr",
                T.ROTL to "__rotl",
                T.ROTR to "__rotr",
            )

        private val BITWISE = setOf(T.AMP, T.PIPE, T.XOR, T.TILDE, T.SHL, T.SHR, T.LSHR, T.ROTL, T.ROTR)

        fun truthy(v: Any?): Boolean = v != null && v != false

        fun typeName(v: Any?): String =
            when (v) {
                null -> "nil"
                is Boolean -> "boolean"
                is Int -> "number"
                is String -> "string"
                is LuaTable -> "table"
                is LuaFunction -> "function"
                is LuaCoroutine -> "thread"
                else -> "userdata"
            }

        /** The console's `tostr`: numbers to four decimals, nil as `[nil]`, tables as `[table]`. */
        fun tostr(v: Any?): String =
            when (v) {
                null -> "[nil]"
                is Boolean -> if (v) "true" else "false"
                is Int -> Fix.toStr(v)
                is String -> v
                is LuaTable -> "[table]"
                is LuaFunction -> "[function]"
                is LuaCoroutine -> "[thread]"
                else -> v.toString()
            }
    }
}
