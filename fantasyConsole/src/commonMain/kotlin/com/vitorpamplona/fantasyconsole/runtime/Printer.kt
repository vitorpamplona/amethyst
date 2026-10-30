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

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * `print` and every P8SCII control code (the manual's Appendix A): colors and solid backgrounds,
 * cursor moves, tabs, wrapping, repeats, decorations, frame waits, raw memory writes, one-off
 * glyphs, inline sound, the custom font at 0x5600, and the wide, tall, stripey, inverted,
 * underlined and outlined modes. Attributes start from their defaults at 0x5f58-0x5f5b and are
 * reset by every call; only the cursor and the pen color carry over.
 */
internal class Printer(
    private val ram: Ram,
    private val gfx: Graphics,
    private val audio: Audio,
) {
    class Result(
        /** The right-most x reached, what `print` returns. */
        val right: Int,
        /** The y of the last line printed. */
        val lastY: Int,
        /** The height of that line. */
        val lineHeight: Int,
        /** True when a `\0` ended the text, so no newline follows. */
        val stopped: Boolean,
        /** The foreground color at the end, which becomes the pen. */
        val color: Int,
    )

    /** Everything a `print` call can change while it runs. */
    private inner class State(
        x: Int,
        y: Int,
        col: Int,
    ) {
        var cx = x
        var cy = y
        var homeX = x
        var homeY = y
        var right = x
        var fg = col and 0xf
        var bg = 0
        var solid = false
        var padding = true
        var wide = false
        var tall = false
        var stripey = false
        var invert = false
        var underline = false
        var customFont = false
        var outlineColor = -1
        var outlineMask = 0
        var outlineOnly = false
        var charW = Font.WIDTH
        var charW2 = Font.WIDE_WIDTH
        var charH = Font.HEIGHT
        var tabW = 16
        var offsetX = 0
        var offsetY = 0
        var wrapX = -1
        var delay = 0
        var lastCharX = x
        var lastCharY = y

        init {
            val flags = ram[DEFAULTS]
            if (flags and 1 != 0) {
                padding = flags and 0x2 != 0
                wide = flags and 0x4 != 0
                tall = flags and 0x8 != 0
                solid = flags and 0x10 != 0
                invert = flags and 0x20 != 0
                stripey = flags and 0x40 != 0
                customFont = flags and 0x80 != 0
                nibble(DEFAULTS + 1, 0) { charW = it }
                nibble(DEFAULTS + 1, 4) { charH = it }
                nibble(DEFAULTS + 2, 0) { charW2 = it }
                nibble(DEFAULTS + 2, 4) { tabW = 4 + (it - 1) * 4 }
                nibble(DEFAULTS + 3, 0) { offsetX = it }
                nibble(DEFAULTS + 3, 4) { offsetY = it }
            }
        }

        private fun nibble(
            addr: Int,
            shift: Int,
            set: (Int) -> Unit,
        ) {
            val v = (ram[addr] shr shift) and 0xf
            if (v != 0) set(v)
        }

        val scaleX get() = if (wide) 2 else 1
        val scaleY get() = if (tall) 2 else 1
        val lineHeight get() = height() * scaleY

        fun height() = if (customFont) ram[FONT + 2] else charH

        fun newline() {
            cx = homeX
            cy += lineHeight
        }
    }

    /** [print] for text with no frame waits (the error screen): never suspends. */
    fun printNow(
        text: String,
        x: Int,
        y: Int,
        col: Int,
    ) {
        val body: suspend () -> Unit = { print(text, x, y, col) {} }
        body.startCoroutine(Continuation(EmptyCoroutineContext) {})
    }

    suspend fun print(
        text: String,
        x: Int,
        y: Int,
        col: Int,
        waitFrames: suspend (Int) -> Unit,
    ): Result {
        gfx.beginText()
        val s = State(x, y, col)
        var i = 0

        fun param(): Int = if (i < text.length) paramDigit(text[i++]) else 0

        fun hex(n: Int): Int {
            var v = 0
            repeat(n) { v = v * 16 + (if (i < text.length) hexDigit(text[i++]) else 0) }
            return v
        }

        while (i < text.length) {
            val code = text[i++].code and 0xff
            when (code) {
                0 -> return result(s, stopped = true)
                1 -> {
                    val n = param()
                    if (i < text.length) {
                        val c = text[i++].code and 0xff
                        repeat(n) { drawChar(s, c, waitFrames) }
                    }
                }
                2 -> {
                    s.bg = param()
                    s.solid = true
                }
                3 -> s.cx += param() - 16
                4 -> s.cy += param() - 16
                5 -> {
                    s.cx += param() - 16
                    s.cy += param() - 16
                }
                6 -> {
                    if (i >= text.length) break
                    val command = text[i++]
                    when (command) {
                        in '1'..'9' -> waitFrames(1 shl (command - '1'))
                        'c' -> {
                            gfx.cls(param())
                            gfx.beginText()
                            s.cx = 0
                            s.cy = 0
                        }
                        'd' -> s.delay = param()
                        'g' -> {
                            s.cx = s.homeX
                            s.cy = s.homeY
                        }
                        'h' -> {
                            s.homeX = s.cx
                            s.homeY = s.cy
                        }
                        'j' -> {
                            s.cx = param() * 4
                            s.cy = param() * 4
                        }
                        'r' -> s.wrapX = param() * 4
                        's' -> s.tabW = param()
                        'x' -> s.charW = param()
                        'y' -> s.charH = param()
                        'o' -> {
                            val c = if (i < text.length) text[i++] else '$'
                            s.outlineOnly = c == '!'
                            s.outlineColor = if (c == '$' || c == '!') s.fg else paramDigit(c)
                            s.outlineMask = hex(2)
                        }
                        '-' -> if (i < text.length) setMode(s, text[i++], false)
                        '.', ',' -> {
                            val rows = IntArray(8) { if (i < text.length) text[i++].code and 0xff else 0 }
                            drawOneOff(s, rows, padded = command == ',')
                        }
                        ':', ';' -> {
                            val rows = IntArray(8) { hex(2) }
                            drawOneOff(s, rows, padded = command == ';')
                        }
                        '@' -> {
                            val addr = hex(4)
                            val count = hex(4)
                            for (k in 0 until count) if (i < text.length) ram[addr + k] = text[i++].code
                        }
                        '!' -> {
                            val addr = hex(4)
                            var k = 0
                            while (i < text.length) ram[addr + k++] = text[i++].code
                            return result(s, stopped = true)
                        }
                        else -> setMode(s, command, true)
                    }
                }
                7 -> {
                    val start = i
                    while (i < text.length && text[i].code > 32) i++
                    audio.playInline(text.substring(start, i))
                }
                8 -> s.cx -= (if (s.customFont) ram[FONT] else s.charW) * s.scaleX
                9 -> {
                    val tab = maxOf(1, s.tabW)
                    s.cx = s.homeX + ((s.cx - s.homeX) / tab + 1) * tab
                }
                10 -> s.newline()
                11 -> {
                    // \v: draw a character at an offset from the last one, then put the cursor back.
                    val offset = param()
                    if (i < text.length) {
                        val c = text[i++].code and 0xff
                        val keepX = s.cx
                        val keepY = s.cy
                        s.cx = s.lastCharX + (offset % 4) - 2
                        s.cy = s.lastCharY + (offset / 4) - 8
                        drawChar(s, c, waitFrames, decoration = true)
                        s.cx = keepX
                        s.cy = keepY
                    }
                }
                12 -> s.fg = param() and 0xf
                13 -> s.cx = s.homeX
                14 -> s.customFont = true
                15 -> s.customFont = false
                else -> drawChar(s, code, waitFrames)
            }
        }
        return result(s, stopped = false)
    }

    private fun result(
        s: State,
        stopped: Boolean,
    ) = Result(s.right, s.cy, s.lineHeight, stopped, s.fg)

    private fun setMode(
        s: State,
        mode: Char,
        on: Boolean,
    ) {
        when (mode) {
            'w' -> s.wide = on
            't' -> s.tall = on
            '=' -> s.stripey = on
            'p' -> {
                s.wide = on
                s.tall = on
                s.stripey = on
            }
            'i' -> s.invert = on
            'b' -> s.padding = on
            '#' -> s.solid = on
            'u' -> s.underline = on
        }
    }

    private suspend fun drawChar(
        s: State,
        code: Int,
        waitFrames: suspend (Int) -> Unit,
        decoration: Boolean = false,
    ) {
        val custom = s.customFont && code >= 16
        var advance: Int
        var height: Int
        var dy = s.offsetY
        var dx = s.offsetX
        val pixel: (Int, Int) -> Boolean
        if (custom) {
            advance = ram[FONT + if (code >= 128) 1 else 0]
            height = ram[FONT + 2]
            dx += ram[FONT + 3]
            dy += ram[FONT + 4]
            if (ram[FONT + 5] and 1 != 0) {
                // Per-character adjustments: width by 0,1,2,3,-4,-3,-2,-1 and one pixel up.
                val packed = ram[FONT + 8 + (code - 16) / 2]
                val nib = if ((code - 16) % 2 == 0) packed and 0xf else packed shr 4
                val adjust = nib and 7
                advance += if (adjust < 4) adjust else adjust - 8
                if (nib and 8 != 0) dy -= 1
            }
            val base = FONT + code * 8
            pixel = { px, py -> px < 8 && py < 8 && (ram[base + py] shr px) and 1 != 0 }
        } else {
            val glyph = Font.glyph(code) ?: return
            advance = if (code >= 128) s.charW2 else s.charW
            height = s.charH
            val top = 1 shl (glyph.width - 1)
            pixel = { px, py -> px < glyph.width && py < 5 && glyph.rows[py] and (top shr px) != 0 }
        }
        val sx = s.scaleX
        val sy = s.scaleY
        if (!decoration && s.wrapX > 0 && s.cx + advance * sx > s.wrapX) s.newline()

        val x0 = s.cx
        val y0 = s.cy
        if (s.solid && !s.invert) {
            val pad = if (s.padding) 1 else 0
            for (yy in y0 - pad until y0 + height * sy - pad) {
                for (xx in x0 - pad until x0 + advance * sx - pad) gfx.textPixel(xx, yy, s.bg)
            }
        }
        val cellW = minOf(advance, 8)
        val cellH = minOf(height, 8)
        if (s.invert) {
            for (py in 0 until cellH) for (px in 0 until cellW) if (!pixel(px, py)) block(s, x0 + dx + px * sx, y0 + dy + py * sy, s.fg)
        } else {
            if (s.outlineMask != 0) {
                for (py in 0 until cellH) {
                    for (px in 0 until cellW) {
                        if (!pixel(px, py)) continue
                        // The outline stays one pixel thick around the scaled pixel.
                        for (bit in 0 until 8) {
                            if (s.outlineMask and (1 shl bit) == 0) continue
                            val ox = NEIGHBOR_X[bit]
                            val oy = NEIGHBOR_Y[bit]
                            for (by in 0 until sy) {
                                for (bx in 0 until sx) {
                                    gfx.textPixel(x0 + dx + px * sx + bx + ox, y0 + dy + py * sy + by + oy, s.outlineColor)
                                }
                            }
                        }
                    }
                }
            }
            if (!s.outlineOnly) {
                for (py in 0 until cellH) for (px in 0 until cellW) if (pixel(px, py)) block(s, x0 + dx + px * sx, y0 + dy + py * sy, s.fg)
            }
        }
        if (s.underline) {
            for (xx in x0 until x0 + advance * sx) gfx.textPixel(xx, y0 + dy + 5 * sy, s.fg)
        }
        s.lastCharX = x0
        s.lastCharY = y0
        if (!decoration) {
            s.cx += advance * sx
            if (s.cx > s.right) s.right = s.cx
            if (s.delay > 0) waitFrames(s.delay)
        }
    }

    /** One glyph pixel at the current scale; stripey mode keeps only its top-left. */
    private fun block(
        s: State,
        x: Int,
        y: Int,
        col: Int,
    ) {
        for (by in 0 until s.scaleY) {
            for (bx in 0 until s.scaleX) {
                if (s.stripey && (bx > 0 || by > 0)) continue
                gfx.textPixel(x + bx, y + by, col)
            }
        }
    }

    /** `\^.`/`\^:` (8x8, no padding) and `\^,`/`\^;` (keeps the cell's padding): an inline glyph, low bit left. */
    private fun drawOneOff(
        s: State,
        rows: IntArray,
        padded: Boolean,
    ) {
        val x0 = s.cx + (if (padded && s.padding) 1 else 0)
        val y0 = s.cy + (if (padded && s.padding) 1 else 0)
        for (py in 0 until 8) for (px in 0 until 8) if ((rows[py] shr px) and 1 != 0) block(s, x0 + px * s.scaleX, y0 + py * s.scaleY, s.fg)
        s.lastCharX = s.cx
        s.lastCharY = s.cy
        s.cx += 8 * s.scaleX
        if (s.cx > s.right) s.right = s.cx
    }

    companion object {
        private const val FONT = 0x5600
        private const val DEFAULTS = 0x5f58

        // Neighbour offsets for the outline bits, low bit top-left, in reading order.
        private val NEIGHBOR_X = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        private val NEIGHBOR_Y = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        /** `0123456789abcdefghijklmnopqrstuv`: the digit P8SCII control codes take as a parameter. */
        fun paramDigit(c: Char): Int =
            when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'v' -> c - 'a' + 10
                in 'A'..'V' -> c - 'A' + 10
                else -> 0
            }

        fun hexDigit(c: Char): Int =
            when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> 0
            }
    }
}
