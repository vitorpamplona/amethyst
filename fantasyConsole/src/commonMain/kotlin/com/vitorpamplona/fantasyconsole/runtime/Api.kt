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
package com.vitorpamplona.fantasyconsole.runtime

import com.vitorpamplona.fantasyconsole.lua.Fix
import com.vitorpamplona.fantasyconsole.lua.Interpreter
import com.vitorpamplona.fantasyconsole.lua.Interpreter.Companion.tostr
import com.vitorpamplona.fantasyconsole.lua.Interpreter.Companion.truthy
import com.vitorpamplona.fantasyconsole.lua.LuaCoroutine
import com.vitorpamplona.fantasyconsole.lua.LuaError
import com.vitorpamplona.fantasyconsole.lua.LuaFunction
import com.vitorpamplona.fantasyconsole.lua.LuaTable
import com.vitorpamplona.fantasyconsole.lua.NONE
import com.vitorpamplona.fantasyconsole.lua.NativeFunction
import com.vitorpamplona.fantasyconsole.lua.Values
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Installs the console's global functions (graphics, input, sound, memory, math, tables…) into [interp]. */
internal class Api(
    private val console: FantasyConsole,
    private val interp: Interpreter,
) {
    private val gfx = console.graphics
    private val ram = console.ram

    /** The two words of the random generator. */
    private var rngA = 0
    private var rngB = 0

    // ---------------------------------------------------------------- argument helpers

    private fun Values.num(
        i: Int,
        default: Int = 0,
    ): Int = interp.toNumber(getOrNull(i)) ?: default

    /** An argument as a whole number (floored), in plain integers. */
    private fun Values.int(
        i: Int,
        default: Int = 0,
    ): Int = getOrNull(i)?.let { interp.toNumber(it)?.let(Fix::toInt) } ?: default

    private fun Values.intOrNull(i: Int): Int? = getOrNull(i)?.let { interp.toNumber(it)?.let(Fix::toInt) }

    /** A color argument as its raw 16:16 bits, which may carry a fill pattern (see [Graphics]). */
    private fun Values.colorOrNull(i: Int): Int? = getOrNull(i)?.let { interp.toNumber(it) }

    private fun Values.bool(i: Int): Boolean = truthy(getOrNull(i))

    private fun Values.table(i: Int): LuaTable? = getOrNull(i) as? LuaTable

    private fun num(v: Int): Values = listOf(v)

    private fun int(v: Int): Values = listOf(Fix.fromInt(v))

    private fun one(v: Any?): Values = listOf(v)

    private fun fn(
        name: String,
        body: suspend (Values) -> Values,
    ) = interp.register(name, body)

    /** A function that returns nothing; most drawing calls. */
    private fun proc(
        name: String,
        body: (Values) -> Unit,
    ) = interp.register(name) {
        body(it)
        NONE
    }

    fun install() {
        srand(0)
        graphicsApi()
        inputApi()
        soundApi()
        memoryApi()
        mathApi()
        stringApi()
        tableApi()
        coroutineApi()
        systemApi()
        // The glyphs 128-153 are globals: the buttons read as their numbers (btn(⬅️) is btn(0)),
        // the rest as fill patterns that look like them, with transparency (fillp(▒)).
        for ((glyph, value) in GLYPH_VALUES) interp.globals.rawset(glyph.toChar().toString(), value)
        // The main loop the console appends to the cart (see FantasyConsole.MAIN_LOOP).
        fn("__fc_fps") {
            console.fps = it.int(0, 30)
            NONE
        }
        fn("__fc_draw") {
            console.runDraw(it.getOrNull(0))
            NONE
        }
        fn("__fc_flip") {
            console.waitFrame()
            NONE
        }
    }

    // ---------------------------------------------------------------- graphics

    private fun graphicsApi() {
        proc("cls") { gfx.cls(it.int(0, 0)) }
        proc("pset") { gfx.pset(it.int(0), it.int(1), it.colorOrNull(2)) }
        fn("pget") { int(gfx.pget(it.int(0), it.int(1))) }
        proc("line") {
            when (it.size) {
                0, 1 -> gfx.resetLine()
                2, 3 -> gfx.lineTo(it.int(0), it.int(1), it.colorOrNull(2))
                else -> gfx.line(it.int(0), it.int(1), it.int(2), it.int(3), it.colorOrNull(4))
            }
        }
        proc("rect") { gfx.rect(it.int(0), it.int(1), it.int(2), it.int(3), it.colorOrNull(4), filled = false) }
        proc("rectfill") { gfx.rect(it.int(0), it.int(1), it.int(2), it.int(3), it.colorOrNull(4), filled = true) }
        proc("rrect") { gfx.rrect(it.int(0), it.int(1), it.int(2), it.int(3), it.int(4), it.colorOrNull(5), filled = false) }
        proc("rrectfill") { gfx.rrect(it.int(0), it.int(1), it.int(2), it.int(3), it.int(4), it.colorOrNull(5), filled = true) }
        proc("circ") { gfx.circ(it.int(0), it.int(1), it.int(2, 4), it.colorOrNull(3), filled = false) }
        proc("circfill") { gfx.circ(it.int(0), it.int(1), it.int(2, 4), it.colorOrNull(3), filled = true) }
        proc("oval") { gfx.oval(it.int(0), it.int(1), it.int(2), it.int(3), it.colorOrNull(4), filled = false) }
        proc("ovalfill") { gfx.oval(it.int(0), it.int(1), it.int(2), it.int(3), it.colorOrNull(4), filled = true) }
        proc("spr") {
            // Width and height count sprites and may be fractional: 0.5 draws 4 pixels.
            val w = (it.num(3, Fix.ONE).toLong() * 8 shr 16).toInt()
            val h = (it.num(4, Fix.ONE).toLong() * 8 shr 16).toInt()
            gfx.spr(it.int(0), it.int(1), it.int(2), w, h, it.bool(5), it.bool(6))
        }
        proc("sspr") {
            val sw = it.int(2)
            val sh = it.int(3)
            gfx.sspr(it.int(0), it.int(1), sw, sh, it.int(4), it.int(5), it.int(6, sw), it.int(7, sh), it.bool(8), it.bool(9))
        }
        fn("sget") { int(gfx.sget(it.int(0), it.int(1))) }
        proc("sset") { gfx.sset(it.int(0), it.int(1), it.colorOrNull(2)) }
        fn("fget") {
            val flags = ram[Addr.FLAGS + (it.int(0) and 0xff)]
            val bit = it.intOrNull(1)
            if (bit == null) int(flags) else one((flags shr bit) and 1 != 0)
        }
        proc("fset") {
            val addr = Addr.FLAGS + (it.int(0) and 0xff)
            if (it.size >= 3) {
                val bit = 1 shl (it.int(1) and 7)
                ram[addr] = if (it.bool(2)) ram[addr] or bit else ram[addr] and bit.inv()
            } else {
                ram[addr] = it.int(1)
            }
        }
        fn("mget") { int(gfx.mget(it.int(0), it.int(1))) }
        proc("mset") { gfx.mset(it.int(0), it.int(1), it.int(2)) }
        val map: (Values) -> Unit = {
            val (w, h) = gfx.mapSize()
            gfx.map(it.int(0), it.int(1), it.int(2), it.int(3), it.int(4, w), it.int(5, h), it.int(6))
        }
        proc("map", map)
        proc("mapdraw", map)
        proc("tline") {
            if (it.size == 1) {
                gfx.tlinePrecision(it.int(0))
            } else {
                gfx.tline(it.int(0), it.int(1), it.int(2), it.int(3), it.num(4), it.num(5), it.num(6, Fix.ONE / 8), it.num(7), it.int(8))
            }
        }
        proc("pal") { args ->
            val first = args.getOrNull(0)
            when {
                first == null -> {
                    gfx.resetPalettes()
                    gfx.resetTransparency()
                }
                first is LuaTable -> {
                    val p = args.int(1)
                    for (k in first.keys()) {
                        val c0 = interp.toNumber(k) ?: continue
                        val c1 = interp.toNumber(first.rawget(k)) ?: continue
                        gfx.pal(Fix.toInt(c0), Fix.toInt(c1), p)
                    }
                }
                args.size == 1 -> gfx.resetPalette(args.int(0))
                else -> gfx.pal(args.int(0), args.int(1), args.int(2))
            }
        }
        proc("palt") {
            when (it.size) {
                0 -> gfx.resetTransparency()
                1 -> gfx.paltBits(it.int(0))
                else -> gfx.palt(it.int(0), it.bool(1))
            }
        }
        fn("color") {
            val old = ram[Addr.PEN]
            gfx.colorBits(it.colorOrNull(0) ?: Fix.fromInt(6))
            int(old)
        }
        proc("cursor") {
            gfx.cursor(it.int(0), it.int(1))
            it.colorOrNull(2)?.let(gfx::colorBits)
        }
        fn("camera") {
            val oldX = ram.peek2(Addr.CAMERA_X)
            val oldY = ram.peek2(Addr.CAMERA_Y)
            gfx.camera(it.int(0), it.int(1))
            listOf(Fix.fromInt(oldX), Fix.fromInt(oldY))
        }
        fn("clip") {
            val old = listOf(ram[Addr.CLIP], ram[Addr.CLIP + 1], ram[Addr.CLIP + 2] - ram[Addr.CLIP], ram[Addr.CLIP + 3] - ram[Addr.CLIP + 1])
            if (it.isEmpty()) gfx.resetClip() else gfx.clip(it.int(0), it.int(1), it.int(2), it.int(3), it.bool(4))
            old.map(Fix::fromInt)
        }
        fn("fillp") {
            val mode = ram[Addr.FILL_MODE]
            val old = (ram.peek2(Addr.FILL_PATTERN) shl 16) or ((mode and 1) shl 15) or ((mode and 2) shl 13) or ((mode and 4) shl 11)
            gfx.fillp(it.num(0))
            num(old)
        }
        fn("print") { print(it) }
        fn("flip") {
            console.waitFrame()
            NONE
        }
    }

    private suspend fun print(args: Values): Values {
        val text = tostr(args.getOrNull(0))
        if (args.size <= 2) {
            // At the cursor, which then moves down a line (scrolling the screen at the bottom).
            val col = args.intOrNull(1) ?: ram[Addr.PEN]
            val x = ram[Addr.CURSOR_X]
            val y = ram[Addr.CURSOR_Y]
            val r = console.printer.print(text, x, y, col, console::waitFrames)
            gfx.color(r.color)
            if (!r.stopped) {
                var nextY = r.lastY + r.lineHeight
                val bottom = 128 - Font.HEIGHT
                if (nextY > bottom && ram[Addr.MISC_FLAGS] and 0x40 == 0) {
                    gfx.scroll(nextY - bottom)
                    nextY = bottom
                }
                gfx.cursor(x, nextY)
            } else {
                gfx.cursor(ram[Addr.CURSOR_X], r.lastY)
            }
            return int(r.right)
        }
        val col = args.intOrNull(3) ?: ram[Addr.PEN]
        val r = console.printer.print(text, args.int(1), args.int(2), col, console::waitFrames)
        gfx.color(r.color)
        return int(r.right)
    }

    // ---------------------------------------------------------------- input and sound

    private fun inputApi() {
        fn("btn") { if (it.isEmpty()) int(console.input.bits()) else one(console.input.btn(it.int(0), it.int(1))) }
        fn("btnp") {
            if (it.isEmpty()) int(console.input.btnpBits(console.fps)) else one(console.input.btnp(it.int(0), it.int(1), console.fps))
        }
    }

    private fun soundApi() {
        proc("sfx") { console.audio.sfx(it.int(0), it.int(1, -1), it.int(2), it.int(3)) }
        proc("music") { console.audio.music(it.int(0), it.int(1), it.int(2)) }
    }

    // ---------------------------------------------------------------- memory

    private fun memoryApi() {
        fn("peek") { args ->
            val addr = args.int(0)
            val n = args.int(1, 1)
            if (n <= 1) int(ram[addr]) else List(minOf(n, 8192)) { Fix.fromInt(ram[addr + it]) }
        }
        fn("peek2") { args ->
            val addr = args.int(0)
            val n = args.int(1, 1)
            if (n <= 1) int(ram.peek2(addr)) else List(minOf(n, 8192)) { Fix.fromInt(ram.peek2(addr + it * 2)) }
        }
        fn("peek4") { args ->
            val addr = args.int(0)
            val n = args.int(1, 1)
            if (n <= 1) num(ram.peek4(addr)) else List(minOf(n, 8192)) { ram.peek4(addr + it * 4) }
        }
        proc("poke") { args -> for (i in 1 until maxOf(args.size, 2)) ram[args.int(0) + i - 1] = args.int(i) }
        proc("poke2") { args -> for (i in 1 until maxOf(args.size, 2)) ram.poke2(args.int(0) + (i - 1) * 2, args.int(i)) }
        proc("poke4") { args -> for (i in 1 until maxOf(args.size, 2)) ram.poke4(args.int(0) + (i - 1) * 4, args.num(i)) }
        proc("memcpy") { ram.copy(it.int(0), it.int(1), it.int(2)) }
        proc("memset") { ram.fill(it.int(0), it.int(1), it.int(2)) }
        proc("reload") {
            val dest = it.int(0)
            val src = it.int(1)
            val len = it.int(2, Addr.CART_DATA_END)
            // A fourth argument reads from another cartridge of the same game.
            val from = it.getOrNull(3)?.let { name -> console.otherCart(tostr(name)) ?: return@proc } ?: console.cart
            for (i in 0 until len) {
                val s = src + i
                if (s in 0 until Addr.CART_DATA_END) ram[dest + i] = from.rom[s].toInt()
            }
        }
        proc("cstore") {}
        fn("cartdata") { args ->
            if (console.cartDataId != null) return@fn one(false)
            // The original allows 64 characters; a longer one would only bloat the host's store.
            val id = tostr(args.getOrNull(0)).take(MAX_CART_DATA_ID)
            console.cartDataId = id
            val saved = console.host.loadCartData(id)
            saved?.forEachIndexed { i, v -> if (i < 64) ram.poke4(Addr.CART_DATA + i * 4, v) }
            one(saved != null)
        }
        fn("dget") { num(ram.peek4(Addr.CART_DATA + (it.int(0) and 63) * 4)) }
        proc("dset") {
            ram.poke4(Addr.CART_DATA + (it.int(0) and 63) * 4, it.num(1))
            console.cartDataDirty = true
        }
    }

    // ---------------------------------------------------------------- math

    private fun srand(seed: Int) {
        rngA = if (seed == 0) 0xdeadbeef.toInt() else seed
        rngB = rngA xor 0xbead29ba.toInt()
        repeat(32) { nextRandom() }
    }

    private fun nextRandom(): Int {
        rngB = ((rngB ushr 16) or (rngB shl 16)) + rngA
        rngA += rngB
        return rngB
    }

    /** A number in [0, range): the generator's bits taken modulo the range's raw pattern. */
    private fun rnd(range: Int): Int {
        val r = nextRandom()
        if (range == 0) return 0
        return ((r.toLong() and 0xffffffffL) % (range.toLong() and 0xffffffffL)).toInt()
    }

    private fun mathApi() {
        fn("max") { num(maxOf(it.num(0), it.num(1))) }
        fn("min") { num(minOf(it.num(0), it.num(1))) }
        fn("mid") {
            val a = it.num(0)
            val b = it.num(1)
            val c = it.num(2)
            num(maxOf(minOf(a, b), minOf(maxOf(a, b), c)))
        }
        fn("flr") { num(Fix.flr(it.num(0))) }
        fn("ceil") { num(Fix.ceil(it.num(0))) }
        fn("abs") { num(kotlin.math.abs(it.num(0))) }
        fn("sgn") { num(if (it.num(0) < 0) -Fix.ONE else Fix.ONE) }
        fn("sqrt") {
            val x = it.num(0)
            num(if (x <= 0) 0 else Fix.fromDouble(sqrt(Fix.toDouble(x))))
        }
        // Angles are in turns (0..1) and run clockwise on screen, so sin is inverted.
        fn("cos") { num(Fix.fromDouble(cos(Fix.toDouble(it.num(0)) * 2 * PI))) }
        fn("sin") { num(Fix.fromDouble(-sin(Fix.toDouble(it.num(0)) * 2 * PI))) }
        fn("atan2") {
            val dx = Fix.toDouble(it.num(0))
            val dy = Fix.toDouble(it.num(1))
            if (dx == 0.0 && dy == 0.0) return@fn num(Fix.ONE / 4)
            var a = atan2(-dy, dx) / (2 * PI)
            if (a < 0) a += 1.0
            num(Fix.fromDouble(a) and 0xffff)
        }
        fn("rnd") { args ->
            val t = args.getOrNull(0)
            if (t is LuaTable) {
                val n = t.length()
                if (n == 0) return@fn one(null)
                return@fn one(t.rawgetInt(1 + Fix.toInt(rnd(Fix.fromInt(n)))))
            }
            num(rnd(if (args.isEmpty() || t == null) Fix.ONE else args.num(0)))
        }
        proc("srand") { srand(it.num(0)) }
        fn("band") { num(it.num(0) and it.num(1)) }
        fn("bor") { num(it.num(0) or it.num(1)) }
        fn("bxor") { num(it.num(0) xor it.num(1)) }
        fn("bnot") { num(it.num(0).inv()) }
        fn("shl") { num(Fix.shl(it.num(0), it.num(1))) }
        fn("shr") { num(Fix.shr(it.num(0), it.num(1))) }
        fn("lshr") { num(Fix.lshr(it.num(0), it.num(1))) }
        fn("rotl") { num(Fix.rotl(it.num(0), it.num(1))) }
        fn("rotr") { num(Fix.rotr(it.num(0), it.num(1))) }
    }

    // ---------------------------------------------------------------- strings

    private fun stringApi() {
        fn("tostr") { args ->
            val v = args.getOrNull(0)
            if (args.isEmpty()) return@fn one("")
            val flags =
                when (val f = args.getOrNull(1)) {
                    true -> 1
                    is Int -> Fix.toInt(f)
                    else -> 0
                }
            if (v is Int && flags != 0) {
                return@fn one(
                    when {
                        flags and 1 != 0 && flags and 2 != 0 -> "0x" + v.toUInt().toString(16).padStart(8, '0')
                        flags and 1 != 0 -> Fix.toHexStr(v)
                        else -> v.toString()
                    },
                )
            }
            one(tostr(v))
        }
        fn("tonum") { args ->
            val v = args.getOrNull(0)
            val flags = args.int(1)
            val parsed: Int? =
                when (v) {
                    is Int -> v
                    true -> Fix.ONE
                    false -> 0
                    is String -> {
                        val s = v.trim()
                        val n = if (flags and 1 != 0) Fix.parse("0x$s") else Fix.parse(s)
                        if (n != null && flags and 2 != 0) n shr 16 else n
                    }
                    else -> null
                }
            one(parsed ?: if (flags and 4 != 0) 0 else null)
        }
        fn("chr") { args ->
            val sb = StringBuilder()
            for (i in args.indices) sb.append((args.int(i) and 0xff).toChar())
            one(sb.toString())
        }
        fn("ord") { args ->
            val s = args.getOrNull(0)?.let(::tostr) ?: return@fn NONE
            val start = args.int(1, 1)
            val n = args.int(2, 1)
            List(maxOf(n, 0)) { k ->
                val i = start + k
                if (i in 1..s.length) Fix.fromInt(s[i - 1].code) else null
            }
        }
        fn("sub") { args ->
            val s = (args.getOrNull(0) as? String) ?: tostr(args.getOrNull(0))
            var i = args.int(1, 1)
            var j = if (args.getOrNull(2) == true) i else args.int(2, -1)
            val n = s.length
            if (i < 0) {
                i = maxOf(n + i + 1, 1)
            } else if (i == 0) {
                i = 1
            }
            if (j < 0) {
                j = n + j + 1
            } else if (j > n) {
                j = n
            }
            one(if (i > j) "" else s.substring(i - 1, j))
        }
        fn("split") { args ->
            val s = tostr(args.getOrNull(0))
            val sep = args.getOrNull(1) ?: ","
            val convert = args.size < 3 || args.bool(2)
            val parts =
                when {
                    sep is Int -> s.chunked(maxOf(1, Fix.toInt(sep)))
                    sep == "" -> s.map { it.toString() }
                    else -> s.split(tostr(sep))
                }
            val t = LuaTable(parts.size)
            // Numbers convert even with spaces around them: "5, 6" gives 5 and 6.
            parts.forEachIndexed { i, p -> t.rawsetInt(i + 1, if (convert) Fix.parse(p.trim()) ?: p else p) }
            one(t)
        }
        fn("type") { one(Interpreter.typeName(it.getOrNull(0))) }
        // Lua's own names, which the console still answers to.
        interp.globals.rawset("tostring", interp.globals.rawget("tostr"))
        interp.globals.rawset("tonumber", interp.globals.rawget("tonum"))
    }

    // ---------------------------------------------------------------- tables

    private fun tableApi() {
        fn("add") { args ->
            val t = args.table(0) ?: return@fn NONE
            val v = args.getOrNull(1)
            val at = args.intOrNull(2)
            if (at == null) t.rawsetInt(t.length() + 1, v) else t.insert(at.coerceIn(1, t.length() + 1), v)
            one(v)
        }
        fn("del") { args ->
            val t = args.table(0) ?: return@fn NONE
            val v = args.getOrNull(1)
            for (i in 1..t.length()) {
                if (t.rawgetInt(i) == v) {
                    t.removeAt(i)
                    return@fn one(v)
                }
            }
            NONE
        }
        fn("deli") { args ->
            val t = args.table(0) ?: return@fn NONE
            if (t.length() == 0) return@fn one(null)
            one(t.removeAt(args.int(1, t.length()).coerceIn(1, t.length())))
        }
        fn("count") { args ->
            val t = args.table(0) ?: return@fn int(0)
            if (args.size < 2) return@fn int((1..t.length()).count { t.rawgetInt(it) != null })
            val v = args.getOrNull(1)
            var n = 0
            for (i in 1..t.length()) if (t.rawgetInt(i) == v) n++
            int(n)
        }
        fn("all") { args ->
            val t = args.table(0) ?: return@fn one(NativeFunction("all") { NONE })
            one(allIterator(t))
        }
        fn("foreach") { args ->
            val t = args.table(0) ?: return@fn NONE
            val f = args.getOrNull(1)
            val iterator = allIterator(t)
            while (true) {
                val v = iterator.call(interp, NONE).firstOrNull() ?: break
                interp.call(f, listOf(v))
            }
            NONE
        }
        fn("pairs") { args ->
            val t = args.getOrNull(0)
            // Like the console, a nil table iterates over nothing instead of raising an error.
            if (t == null) return@fn listOf(EMPTY_ITERATOR, null, null)
            if (t !is LuaTable) throw LuaError("bad argument #1 to 'pairs' (table expected, got ${Interpreter.typeName(t)})")
            val handler = t.metatable?.rawget("__pairs")
            if (handler != null) return@fn interp.call(handler, listOf(t)).take(3)
            val keys = t.keys()
            var i = 0
            val iter =
                NativeFunction("pairs") {
                    while (i < keys.size) {
                        val k = keys[i++]
                        val v = t.rawget(k)
                        if (v != null) return@NativeFunction listOf(k, v)
                    }
                    one(null)
                }
            listOf(iter, t, null)
        }
        val inext =
            NativeFunction("inext") { a ->
                val t = a.getOrNull(0) ?: return@NativeFunction one(null)
                val i = Fix.toInt(a.num(1)) + 1
                val v = interp.index(t, Fix.fromInt(i))
                if (v == null) one(null) else listOf(Fix.fromInt(i), v)
            }
        interp.globals.rawset("inext", inext)
        fn("ipairs") { args ->
            val t = args.getOrNull(0)
            if (t == null) return@fn listOf(EMPTY_ITERATOR, null, null)
            if (t !is LuaTable) throw LuaError("bad argument #1 to 'ipairs' (table expected, got ${Interpreter.typeName(t)})")
            listOf(inext, t, 0)
        }
        fn("next") { args ->
            val t = args.getOrNull(0) as? LuaTable ?: throw LuaError("bad argument #1 to 'next' (table expected)")
            val k = t.next(args.getOrNull(1)) ?: return@fn one(null)
            listOf(k, t.rawget(k))
        }
        fn("select") { args ->
            val n = args.getOrNull(0)
            if (n == "#") return@fn int(args.size - 1)
            var i = args.int(0)
            if (i < 0) {
                i += args.size
            } else if (i == 0) {
                throw LuaError("bad argument #1 to 'select' (index out of range)")
            }
            if (i < 1) throw LuaError("bad argument #1 to 'select' (index out of range)")
            if (i >= args.size) NONE else args.subList(i, args.size)
        }
        fn("unpack") { args ->
            val t = args.table(0) ?: return@fn NONE
            val i = args.int(1, 1)
            val j = args.int(2, t.length())
            if (j < i) NONE else List(minOf(j - i + 1, 8192)) { t.rawgetInt(i + it) }
        }
        fn("pack") { args ->
            val t = LuaTable(args.size)
            args.forEachIndexed { i, v -> t.rawsetInt(i + 1, v) }
            t.rawset("n", Fix.fromInt(args.size))
            one(t)
        }
        fn("setmetatable") { args ->
            val t = args.getOrNull(0) as? LuaTable ?: throw LuaError("bad argument #1 to 'setmetatable' (table expected)")
            t.metatable = args.getOrNull(1) as? LuaTable
            one(t)
        }
        fn("getmetatable") { one((it.getOrNull(0) as? LuaTable)?.metatable) }
        fn("rawget") { one((it.getOrNull(0) as? LuaTable)?.rawget(it.getOrNull(1))) }
        fn("rawset") {
            (it.getOrNull(0) as? LuaTable)?.rawset(it.getOrNull(1), it.getOrNull(2))
            one(it.getOrNull(0))
        }
        fn("rawequal") { one(it.getOrNull(0) == it.getOrNull(1)) }
        fn("rawlen") {
            when (val v = it.getOrNull(0)) {
                is LuaTable -> int(v.length())
                is String -> int(v.length)
                else -> int(0)
            }
        }
    }

    /**
     * The iterator `all` returns: walks 1..#t, and if the current item was deleted (`del` inside
     * the loop shifts the rest down) it stays on the same index instead of skipping one.
     */
    private fun allIterator(t: LuaTable): LuaFunction {
        var i = 0
        var previous: Any? = null
        return NativeFunction("all") {
            if (i > 0 && t.rawgetInt(i) == previous) i++
            if (i == 0) i = 1
            while (i <= t.length() && t.rawgetInt(i) == null) i++
            if (i > t.length()) {
                one(null)
            } else {
                previous = t.rawgetInt(i)
                one(previous)
            }
        }
    }

    // ---------------------------------------------------------------- coroutines

    private fun coroutineApi() {
        fn("cocreate") { args ->
            val f = args.getOrNull(0) as? LuaFunction ?: throw LuaError("bad argument #1 to 'cocreate' (function expected)")
            one(LuaCoroutine(f))
        }
        fn("coresume") { args ->
            val co = args.getOrNull(0) as? LuaCoroutine ?: throw LuaError("bad argument #1 to 'coresume' (coroutine expected)")
            interp.resume(co, if (args.size > 1) args.subList(1, args.size) else NONE)
        }
        fn("costatus") { args ->
            val co = args.getOrNull(0) as? LuaCoroutine ?: throw LuaError("bad argument #1 to 'costatus' (coroutine expected)")
            one(co.status)
        }
        fn("yield") { interp.yieldValues(it) }
    }

    // ---------------------------------------------------------------- system

    private fun systemApi() {
        val time: suspend (Values) -> Values = { num(console.timeBits) }
        fn("time", time)
        fn("t", time)
        fn("stat") { statValue(it.int(0)) ?: int(stat(it.int(0))) }
        proc("printh") { console.host.log(tostr(it.getOrNull(0))) }
        fn("assert") { args ->
            if (!truthy(args.getOrNull(0))) throw LuaError(args.getOrNull(1) ?: "assertion failed!")
            args
        }
        proc("menuitem") { args ->
            val index = args.int(0) - 1
            if (index !in 0..4) return@proc
            val callback = args.getOrNull(2) as? LuaFunction
            val label = args.getOrNull(1)
            console.menu.items[index] = if (label == null) null else callback?.let { PauseMenu.Item(PauseMenu.label(label), it) } ?: PauseMenu.Item(PauseMenu.label(label), NativeFunction("menuitem") { NONE })
        }
        proc("extcmd") {
            when (tostr(it.getOrNull(0))) {
                "pause" -> console.requestPause()
                "reset" -> console.requestRestart(null)
                "shutdown" -> console.requestStop(null)
                "breadcrumb", "go_back" -> console.goBack()
            }
        }
        proc("export") {}
        // Undocumented helpers some carts call.
        proc("_set_fps") { console.fps = it.int(0, 30).coerceIn(1, 60) }
        proc("_update_buttons") {}
        proc("holdframe") {}
        proc("_map_display") {}
        proc("_mark_cpu") {}
        proc("_startframe") {}
        proc("_update_framerate") {}
        proc("_set_mainloop_exists") {}
        proc("_pausemenu") {}
        proc("set_draw_slice") {}
        fn("_get_menu_item_selected") { int(0) }
        proc("trace") {}
        fn("serial") { int(0) }
        fn("ls") { one(null) }
        // Loading another cartridge (`load("#id")`) is not supported: the cart stops and says so.
        proc("load") {
            console.requestLoad(tostr(it.getOrNull(0)), it.getOrNull(1)?.let(::tostr), it.getOrNull(2)?.let(::tostr))
        }
        proc("reset") { gfx.reset() }
        proc("stop") { console.requestStop(it.getOrNull(0)?.let(::tostr)) }
        proc("run") { console.requestRestart(it.getOrNull(0)?.let(::tostr)) }
    }

    /** `stat(n)` values that are not plain whole numbers, or null to use [stat]. */
    private fun statValue(n: Int): Values? =
        when (n) {
            1 -> num(Fix.fromDouble(console.cpuLoad()))
            4 -> one("")
            6 -> one(console.runParam)
            30 -> one(devkit() && console.input.hasKey())
            31 -> one(if (devkit()) console.input.nextKey() else "")
            57 -> one(console.audio.musicPlaying())
            100 -> one(console.breadcrumbLabel)
            // No id on the original's site: the cart runs locally.
            101 -> one(null)
            110, 120, 121 -> one(false)
            in 80..85 -> int(Clock.utc()[n - 80])
            in 90..95 -> int(Clock.local(console.host.utcOffsetSeconds())[n - 90])
            else -> null
        }

    private fun devkit(): Boolean = ram[Addr.DEVKIT] and 1 != 0

    private fun stat(n: Int): Int =
        when (n) {
            7, 8, 9 -> console.fps
            in 16..19 -> console.audio.channelSfx(n - 16)
            in 20..23 -> console.audio.channelNote(n - 20)
            24 -> console.audio.currentPattern()
            25 -> console.audio.patternsPlayed()
            26 -> console.audio.patternTicks()
            32 -> if (devkit()) console.input.mouseX else 0
            33 -> if (devkit()) console.input.mouseY else 0
            34 -> if (devkit()) console.input.mouseButtons else 0
            36 -> if (devkit()) console.input.wheel else 0
            in 46..49 -> console.audio.channelSfx(n - 46)
            in 50..53 -> console.audio.channelNote(n - 50)
            54 -> console.audio.currentPattern()
            55 -> console.audio.patternsPlayed()
            56 -> console.audio.patternTicks()
            else -> 0
        }

    private companion object {
        const val MAX_CART_DATA_ID = 64

        val EMPTY_ITERATOR = NativeFunction("pairs") { listOf(null) }

        /** P8SCII 128-153 as globals: 4x4 pictures of the glyph (set bits transparent), and the buttons. */
        val GLYPH_VALUES: List<Pair<Int, Int>> =
            listOf(
                128 to 0x00008000, // █
                129 to 0x5a5a8000, // ▒
                130 to 0x511f8000, // 🐱
                131 to 0x00030000, // ⬇️ = 3
                132 to 0x7d7d8000, // ░
                133 to 0xb81d8000.toInt(), // ✽
                134 to 0xf99f8000.toInt(), // ●
                135 to 0x51bf8000, // ♥
                136 to 0xb5bf8000.toInt(), // ☉
                137 to 0x999f8000.toInt(), // 웃
                138 to 0xb11f8000.toInt(), // ⌂
                139 to 0x00000000, // ⬅️ = 0
                140 to 0xa0e08000.toInt(), // 😐
                141 to 0x9b3f8000.toInt(), // ♪
                142 to 0x00040000, // 🅾️ = 4
                143 to 0xb1bf8000.toInt(), // ◆
                144 to 0xf5ff8000.toInt(), // …
                145 to 0x00010000, // ➡️ = 1
                146 to 0xb15f8000.toInt(), // ★
                147 to 0x1b1f8000, // ⧗
                148 to 0x00020000, // ⬆️ = 2
                149 to 0xf5bf8000.toInt(), // ˇ
                150 to 0x7adf8000, // ∧
                151 to 0x00050000, // ❎ = 5
                152 to 0x0f0f8000, // ▤
                153 to 0x55558000, // ▥
            )
    }
}
