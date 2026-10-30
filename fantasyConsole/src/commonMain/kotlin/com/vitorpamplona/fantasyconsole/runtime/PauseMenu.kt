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
import com.vitorpamplona.fantasyconsole.lua.LuaFunction

/**
 * The pause menu the pause button (or `extcmd("pause")`) opens: continue, the cart's own
 * `menuitem`s, and reset. It is drawn over the frame in [draw] and never touches cart memory, so
 * the game picks up exactly where it was.
 */
internal class PauseMenu {
    class Item(
        val label: String,
        val callback: LuaFunction,
    )

    private sealed interface Entry {
        val label: String

        data object Continue : Entry {
            override val label = "continue"
        }

        class Custom(
            val index: Int,
            override val label: String,
        ) : Entry

        data object Reset : Entry {
            override val label = "reset cart"
        }
    }

    /** `menuitem(1..5, label, callback)`. */
    val items = arrayOfNulls<Item>(5)

    var isOpen = false
        private set
    private var selected = 0

    private fun entries(): List<Entry> =
        buildList {
            add(Entry.Continue)
            items.forEachIndexed { i, item -> if (item != null) add(Entry.Custom(i, item.label)) }
            add(Entry.Reset)
        }

    fun open() {
        isOpen = true
        selected = 0
    }

    fun close() {
        isOpen = false
    }

    /** What the menu asks the console to do after a frame of input. */
    sealed interface Action {
        data object None : Action

        data object Resume : Action

        data object Reset : Action

        /** Call a `menuitem` callback with button bits (1 left, 2 right, 32 X); the menu closes unless it returns true. */
        class Call(
            val callback: LuaFunction,
            val buttons: Int,
        ) : Action
    }

    fun step(
        input: Input,
        fps: Int,
    ): Action {
        val list = entries()
        if (input.btnp(2, 0, fps)) selected = (selected + list.size - 1) % list.size
        if (input.btnp(3, 0, fps)) selected = (selected + 1) % list.size
        if (input.btnp(6, 0, fps)) return Action.Resume
        val entry = list[selected.coerceIn(0, list.size - 1)]
        val left = input.btnp(0, 0, fps)
        val right = input.btnp(1, 0, fps)
        val confirm = input.btnp(4, 0, fps) || input.btnp(5, 0, fps)
        if (entry is Entry.Custom && (left || right || confirm)) {
            val bits = (if (left) 1 else 0) or (if (right) 2 else 0) or (if (confirm) 32 else 0)
            val item = items[entry.index] ?: return Action.None
            return Action.Call(item.callback, bits)
        }
        if (!confirm) return Action.None
        return when (entry) {
            Entry.Continue -> Action.Resume
            Entry.Reset -> Action.Reset
            else -> Action.None
        }
    }

    /** Draws the open menu over [out] (128x128 ARGB). */
    fun draw(out: IntArray) {
        val list = entries()
        val width = (list.maxOf { it.label.length } + 3) * Font.WIDTH + 8
        val height = list.size * 8 + 8
        val left = (128 - width) / 2
        val top = (128 - height) / 2
        val black = Graphics.rgbOf(0)
        val white = Graphics.rgbOf(7)
        for (y in top until top + height) {
            for (x in left until left + width) {
                val edge = x == left || x == left + width - 1 || y == top || y == top + height - 1
                out[y * 128 + x] = if (edge) white else black
            }
        }
        list.forEachIndexed { i, entry ->
            val y = top + 5 + i * 8
            if (i == selected) text(out, ">", left + 4, y, white)
            text(out, entry.label, left + 4 + 2 * Font.WIDTH, y, white)
        }
    }

    private fun text(
        out: IntArray,
        s: String,
        x: Int,
        y: Int,
        color: Int,
    ) {
        var cx = x
        for (c in s) {
            val glyph = Font.glyph(c.code and 0xff) ?: continue
            val top = 1 shl (glyph.width - 1)
            for (row in 0 until 5) {
                for (col in 0 until glyph.width) {
                    if (glyph.rows[row] and (top shr col) == 0) continue
                    val px = cx + col
                    val py = y + row
                    if (px in 0..127 && py in 0..127) out[py * 128 + px] = color
                }
            }
            cx += if (c.code >= 128) Font.WIDE_WIDTH else Font.WIDTH
        }
    }

    companion object {
        /** A `menuitem` label, as the console shows it: at most 16 characters. */
        fun label(value: Any?): String = Interpreter.tostr(value).take(16)

        fun index(n: Int): Int = Fix.toInt(n) - 1
    }
}
