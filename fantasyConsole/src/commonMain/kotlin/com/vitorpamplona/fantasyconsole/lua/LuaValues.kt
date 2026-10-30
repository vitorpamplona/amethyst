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

/*
 * Lua values are plain Kotlin objects: null is nil, then Boolean, Int (a 16:16 number),
 * String (P8SCII, one char per byte), LuaTable, LuaFunction and LuaCoroutine.
 */

/** A runtime error raised by the cart or the runtime; [luaMessage] is what `coresume` hands back. */
open class LuaError(
    val luaMessage: Any?,
    cause: Throwable? = null,
) : Exception(luaMessage?.toString(), cause)

/** Thrown into the running cart when the host stops it. Never caught by Lua code. */
class LuaAbort : Exception("aborted")

typealias Values = List<Any?>

val NONE: Values = emptyList()

abstract class LuaFunction {
    abstract val name: String

    abstract suspend fun call(
        interp: Interpreter,
        args: Values,
    ): Values

    override fun toString(): String = "function"
}

/** A function written in Kotlin. Most never suspend; `yield`, `coresume` and `flip` do. */
class NativeFunction(
    override val name: String,
    private val body: suspend (Values) -> Values,
) : LuaFunction() {
    override suspend fun call(
        interp: Interpreter,
        args: Values,
    ): Values = body(args)
}

/** A local that a nested function captured: shared by reference between them. */
class Cell(
    var v: Any?,
)

/** A Lua function value: its compiled body plus the cells it closed over. */
class Closure(
    val proto: FunctionProto,
    val upvals: Array<Cell>,
) : LuaFunction() {
    override val name: String get() = proto.name

    override suspend fun call(
        interp: Interpreter,
        args: Values,
    ): Values {
        val p = proto
        val slots = arrayOfNulls<Any?>(p.numSlots)
        val params = p.params
        for (i in params.indices) {
            val v = if (i < args.size) args[i] else null
            val local = params[i]
            slots[local.slot] = if (local.captured) Cell(v) else v
        }
        val varargs = if (p.isVararg && args.size > params.size) args.subList(params.size, args.size) else NONE
        if (interp.depth >= Interpreter.MAX_DEPTH) throw LuaError("stack overflow")
        interp.depth++
        try {
            val signal = p.body.exec(Frame(slots, varargs, upvals, interp))
            return if (signal is ReturnSignal) signal.values else NONE
        } finally {
            interp.depth--
        }
    }
}

/** One activation of a [Closure]: its locals by slot, its varargs and its upvalues. */
class Frame(
    val slots: Array<Any?>,
    val varargs: Values,
    val upvals: Array<Cell>,
    val interp: Interpreter,
)

/** A Lua coroutine (`cocreate`), driven by [Interpreter.resume] and [Interpreter.yieldValues]. */
class LuaCoroutine(
    val fn: LuaFunction,
) {
    /** "suspended", "running", "normal" or "dead", as `costatus` reports it. */
    var status: String = "suspended"
    internal var started = false
    internal var depth = 0
    internal var resumer: kotlin.coroutines.Continuation<Values>? = null
    internal var body: kotlin.coroutines.Continuation<Values>? = null

    override fun toString(): String = "thread"
}

/** What a statement hands back besides "carry on": a break, a return or a goto. */
object BreakSignal

class ReturnSignal(
    val values: Values,
)

class GotoSignal(
    val label: String,
)
