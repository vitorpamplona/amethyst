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
 * The parsed program, which is also the program that runs: every node evaluates itself.
 * Locals are resolved to frame slots at parse time; a local that some inner function captures
 * lives in a [Cell] so both share it, and every other local sits in its slot directly.
 */

/** A local variable's slot. [captured] is final once parsing ends, before anything runs. */
class LocalVar(
    val name: String,
    val slot: Int,
) {
    var captured = false
}

/** Where a closure finds an upvalue when it is created: a local of its parent, or one of its parent's upvalues. */
class UpvalDesc(
    val name: String,
    val fromLocal: LocalVar?,
    val fromUpval: Int,
)

class FunctionProto(
    val name: String,
    val params: Array<LocalVar>,
    val isVararg: Boolean,
    val upvals: Array<UpvalDesc>,
    val body: Block,
    val numSlots: Int,
    val line: Int,
)

abstract class Expr {
    abstract suspend fun eval(f: Frame): Any?

    /** Calls and `...` produce several values where a list allows it; everything else produces one. */
    open val isMulti: Boolean get() = false

    open suspend fun evalMulti(f: Frame): Values = listOf(eval(f))

    /** How an error message names this expression: `global 'x'`, `field 'y'`, or null. */
    open fun describe(): String? = null
}

interface Assignable {
    suspend fun assign(
        f: Frame,
        value: Any?,
    )
}

class ConstExpr(
    val value: Any?,
) : Expr() {
    override suspend fun eval(f: Frame): Any? = value
}

class VarargExpr : Expr() {
    override val isMulti get() = true

    override suspend fun eval(f: Frame): Any? = f.varargs.firstOrNull()

    override suspend fun evalMulti(f: Frame): Values = f.varargs
}

class LocalExpr(
    val local: LocalVar,
) : Expr(),
    Assignable {
    override suspend fun eval(f: Frame): Any? {
        val v = f.slots[local.slot]
        return if (local.captured) (v as Cell).v else v
    }

    override suspend fun assign(
        f: Frame,
        value: Any?,
    ) {
        if (local.captured) (f.slots[local.slot] as Cell).v = value else f.slots[local.slot] = value
    }

    override fun describe() = "local '${local.name}'"
}

class UpvalExpr(
    val index: Int,
    val name: String,
) : Expr(),
    Assignable {
    override suspend fun eval(f: Frame): Any? = f.upvals[index].v

    override suspend fun assign(
        f: Frame,
        value: Any?,
    ) {
        f.upvals[index].v = value
    }

    override fun describe() = "upvalue '$name'"
}

class GlobalExpr(
    val name: String,
) : Expr(),
    Assignable {
    override suspend fun eval(f: Frame): Any? {
        val g = f.interp.globals
        return if (g.metatable == null) g.rawget(name) else f.interp.index(g, name)
    }

    override suspend fun assign(
        f: Frame,
        value: Any?,
    ) {
        val g = f.interp.globals
        if (g.metatable == null) g.rawset(name, value) else f.interp.setIndex(g, name, value)
    }

    override fun describe() = "global '$name'"
}

/** `_ENV` where no local `_ENV` is in scope: the globals table itself. */
class GlobalsExpr : Expr() {
    override suspend fun eval(f: Frame): Any? = f.interp.globals
}

class IndexExpr(
    val obj: Expr,
    val key: Expr,
) : Expr(),
    Assignable {
    override suspend fun eval(f: Frame): Any? {
        val o = obj.eval(f)
        val k = key.eval(f)
        if (o is LuaTable) {
            val v = o.rawget(k)
            if (v != null || o.metatable == null) return v
        }
        return f.interp.index(o, k, obj)
    }

    override suspend fun assign(
        f: Frame,
        value: Any?,
    ) {
        val o = obj.eval(f)
        val k = key.eval(f)
        if (o is LuaTable && o.metatable == null) o.rawset(k, value) else f.interp.setIndex(o, k, value, obj)
    }

    override fun describe(): String? {
        val k = (key as? ConstExpr)?.value
        return if (k is String) "field '$k'" else null
    }
}

class CallExpr(
    val fn: Expr,
    val args: Array<Expr>,
) : Expr() {
    override val isMulti get() = true

    override suspend fun eval(f: Frame): Any? = evalMulti(f).firstOrNull()

    override suspend fun evalMulti(f: Frame): Values {
        val callee = fn.eval(f)
        val argv = evalList(f, args)
        if (callee is LuaFunction) return callee.call(f.interp, argv)
        return f.interp.call(callee, argv, fn)
    }
}

class MethodCallExpr(
    val obj: Expr,
    val method: String,
    val args: Array<Expr>,
) : Expr() {
    override val isMulti get() = true

    override suspend fun eval(f: Frame): Any? = evalMulti(f).firstOrNull()

    override suspend fun evalMulti(f: Frame): Values {
        val self = obj.eval(f)
        val callee =
            if (self is LuaTable) {
                self.rawget(method) ?: if (self.metatable != null) f.interp.index(self, method) else null
            } else {
                f.interp.index(self, method, obj)
            }
        val argv = ArrayList<Any?>(args.size + 1)
        argv.add(self)
        appendList(f, args, argv)
        if (callee is LuaFunction) return callee.call(f.interp, argv)
        return f.interp.call(callee, argv, null, "method '$method'")
    }
}

class FunctionExpr(
    val proto: FunctionProto,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val descs = proto.upvals
        val cells =
            Array(descs.size) { i ->
                val d = descs[i]
                val local = d.fromLocal
                if (local != null) {
                    val v = f.slots[local.slot]
                    v as? Cell ?: Cell(v).also { f.slots[local.slot] = it }
                } else {
                    f.upvals[d.fromUpval]
                }
            }
        return Closure(proto, cells)
    }
}

/** `(f())`: parentheses cut a list down to its first value. */
class ParenExpr(
    val inner: Expr,
) : Expr() {
    override suspend fun eval(f: Frame): Any? = inner.eval(f)
}

class AndExpr(
    val a: Expr,
    val b: Expr,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val x = a.eval(f)
        return if (x == null || x == false) x else b.eval(f)
    }
}

class OrExpr(
    val a: Expr,
    val b: Expr,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val x = a.eval(f)
        return if (x == null || x == false) b.eval(f) else x
    }
}

class BinExpr(
    val op: T,
    val a: Expr,
    val b: Expr,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val x = a.eval(f)
        val y = b.eval(f)
        if (x is Int && y is Int) {
            when (op) {
                T.PLUS -> return x + y
                T.MINUS -> return x - y
                T.STAR -> return Fix.mul(x, y)
                T.SLASH -> return Fix.div(x, y)
                T.PERCENT -> return Fix.mod(x, y)
                T.BACKSLASH -> return Fix.idiv(x, y)
                T.LT -> return x < y
                T.LE -> return x <= y
                T.GT -> return x > y
                T.GE -> return x >= y
                T.EQ -> return x == y
                T.NE -> return x != y
                T.AMP -> return x and y
                T.PIPE -> return x or y
                T.XOR, T.TILDE -> return x xor y
                T.SHL -> return Fix.shl(x, y)
                T.SHR -> return Fix.shr(x, y)
                T.LSHR -> return Fix.lshr(x, y)
                T.ROTL -> return Fix.rotl(x, y)
                T.ROTR -> return Fix.rotr(x, y)
                T.CARET -> return Fix.pow(x, y)
                else -> {}
            }
        }
        return f.interp.binary(op, x, y, a, b)
    }
}

class UnExpr(
    val op: T,
    val a: Expr,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val x = a.eval(f)
        return when (op) {
            T.NOT -> x == null || x == false
            T.MINUS -> if (x is Int) -x else f.interp.unary(op, x, a)
            T.TILDE -> if (x is Int) x.inv() else f.interp.unary(op, x, a)
            else -> f.interp.unary(op, x, a)
        }
    }
}

class TableItem(
    val key: Expr?,
    val value: Expr,
)

class TableExpr(
    val items: Array<TableItem>,
) : Expr() {
    override suspend fun eval(f: Frame): Any? {
        val positional = items.count { it.key == null }
        val t = LuaTable(positional)
        var n = 1
        for (i in items.indices) {
            val item = items[i]
            if (item.key != null) {
                val k = item.key.eval(f) ?: throw LuaError("table index is nil")
                t.rawset(k, item.value.eval(f))
            } else if (i == items.size - 1 && item.value.isMulti) {
                for (v in item.value.evalMulti(f)) t.rawsetInt(n++, v)
            } else {
                t.rawsetInt(n++, item.value.eval(f))
            }
        }
        return t
    }
}

/** Evaluates an argument or expression list, letting the last item spread its values. */
suspend fun evalList(
    f: Frame,
    exprs: Array<Expr>,
): Values {
    if (exprs.isEmpty()) return NONE
    if (exprs.size == 1) {
        val e = exprs[0]
        return if (e.isMulti) e.evalMulti(f) else listOf(e.eval(f))
    }
    val out = ArrayList<Any?>(exprs.size)
    appendList(f, exprs, out)
    return out
}

suspend fun appendList(
    f: Frame,
    exprs: Array<Expr>,
    out: ArrayList<Any?>,
) {
    val last = exprs.size - 1
    for (i in exprs.indices) {
        val e = exprs[i]
        if (i == last && e.isMulti) out.addAll(e.evalMulti(f)) else out.add(e.eval(f))
    }
}

abstract class Stat(
    val line: Int,
) {
    /** Null to carry on, or a [BreakSignal], [ReturnSignal] or [GotoSignal] for the enclosing code. */
    abstract suspend fun exec(f: Frame): Any?
}

class Block(
    val stats: Array<Stat>,
) {
    private val labels: Map<String, Int>? =
        stats
            .withIndex()
            .filter { it.value is LabelStat }
            .associate { (it.value as LabelStat).name to it.index }
            .ifEmpty { null }

    suspend fun exec(f: Frame): Any? {
        var i = 0
        val stats = stats
        while (i < stats.size) {
            val s = stats[i]
            val signal =
                try {
                    s.exec(f)
                } catch (e: LuaError) {
                    if (e is LocatedError) throw e
                    throw LocatedError(e.luaMessage, s.line, e)
                }
            if (signal != null) {
                if (signal is GotoSignal && labels != null) {
                    val target = labels[signal.label]
                    if (target != null) {
                        f.interp.checkAbort()
                        i = target
                        continue
                    }
                }
                return signal
            }
            i++
        }
        return null
    }
}

/** A [LuaError] tagged with the line it was raised on. Its [luaMessage] carries the line, as `coresume` reports it. */
class LocatedError(
    message: Any?,
    val line: Int,
    cause: LuaError?,
) : LuaError(if (message is String) "line $line: $message" else message, cause) {
    val bareMessage: Any? = message
}

class LocalStat(
    line: Int,
    val vars: Array<LocalVar>,
    val exprs: Array<Expr>,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        if (vars.size == 1 && exprs.size == 1) {
            set(f, vars[0], exprs[0].eval(f))
            return null
        }
        val values = evalList(f, exprs)
        for (i in vars.indices) set(f, vars[i], values.getOrNull(i))
        return null
    }

    private fun set(
        f: Frame,
        v: LocalVar,
        value: Any?,
    ) {
        f.slots[v.slot] = if (v.captured) Cell(value) else value
    }
}

class LocalFunctionStat(
    line: Int,
    val local: LocalVar,
    val fn: FunctionExpr,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        // The function sees itself, so the local exists before the closure is made.
        if (local.captured) {
            val cell = Cell(null)
            f.slots[local.slot] = cell
            cell.v = fn.eval(f)
        } else {
            f.slots[local.slot] = fn.eval(f)
        }
        return null
    }
}

class AssignStat(
    line: Int,
    val targets: Array<Expr>,
    val exprs: Array<Expr>,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        if (targets.size == 1 && exprs.size == 1) {
            (targets[0] as Assignable).assign(f, exprs[0].eval(f))
            return null
        }
        // Every value is computed before anything is assigned: `a, b = b, a` swaps.
        val values = evalList(f, exprs)
        for (i in targets.indices) (targets[i] as Assignable).assign(f, values.getOrNull(i))
        return null
    }
}

class CallStat(
    line: Int,
    val call: Expr,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        call.evalMulti(f)
        return null
    }
}

class DoStat(
    line: Int,
    val block: Block,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? = block.exec(f)
}

class WhileStat(
    line: Int,
    val cond: Expr,
    val block: Block,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        while (true) {
            val c = cond.eval(f)
            if (c == null || c == false) return null
            f.interp.checkAbort()
            val signal = block.exec(f)
            if (signal != null) return if (signal === BreakSignal) null else signal
        }
    }
}

class RepeatStat(
    line: Int,
    val block: Block,
    val cond: Expr,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        while (true) {
            f.interp.checkAbort()
            val signal = block.exec(f)
            if (signal != null) return if (signal === BreakSignal) null else signal
            val c = cond.eval(f)
            if (c != null && c != false) return null
        }
    }
}

class IfStat(
    line: Int,
    val conds: Array<Expr>,
    val blocks: Array<Block>,
    val elseBlock: Block?,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        for (i in conds.indices) {
            val c = conds[i].eval(f)
            if (c != null && c != false) return blocks[i].exec(f)
        }
        return elseBlock?.exec(f)
    }
}

class NumForStat(
    line: Int,
    val local: LocalVar,
    val start: Expr,
    val limit: Expr,
    val step: Expr?,
    val block: Block,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        val interp = f.interp
        val from = interp.forNumber(start.eval(f), "initial")
        val to = interp.forNumber(limit.eval(f), "limit")
        val by = if (step == null) Fix.ONE else interp.forNumber(step.eval(f), "step")
        if (by == 0) throw LuaError("'for' step is zero")
        // Counting in Long keeps `for i=0,32767` from wrapping into an endless loop.
        var i = from.toLong()
        val end = to.toLong()
        while (if (by > 0) i <= end else i >= end) {
            interp.checkAbort()
            f.slots[local.slot] = if (local.captured) Cell(i.toInt()) else i.toInt()
            val signal = block.exec(f)
            if (signal != null) return if (signal === BreakSignal) null else signal
            i += by
        }
        return null
    }
}

class GenForStat(
    line: Int,
    val vars: Array<LocalVar>,
    val exprs: Array<Expr>,
    val block: Block,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? {
        val interp = f.interp
        val init = evalList(f, exprs)
        val iter = init.getOrNull(0)
        val state = init.getOrNull(1)
        var control = init.getOrNull(2)
        while (true) {
            interp.checkAbort()
            val r = if (iter is LuaFunction) iter.call(interp, listOf(state, control)) else interp.call(iter, listOf(state, control))
            val first = r.firstOrNull() ?: return null
            control = first
            for (j in vars.indices) {
                val v = vars[j]
                val value = r.getOrNull(j)
                f.slots[v.slot] = if (v.captured) Cell(value) else value
            }
            val signal = block.exec(f)
            if (signal != null) return if (signal === BreakSignal) null else signal
        }
    }
}

class ReturnStat(
    line: Int,
    val exprs: Array<Expr>,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? = ReturnSignal(evalList(f, exprs))
}

class BreakStat(
    line: Int,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? = BreakSignal
}

class GotoStat(
    line: Int,
    val label: String,
) : Stat(line) {
    private val signal = GotoSignal(label)

    override suspend fun exec(f: Frame): Any? = signal
}

class LabelStat(
    line: Int,
    val name: String,
) : Stat(line) {
    override suspend fun exec(f: Frame): Any? = null
}
