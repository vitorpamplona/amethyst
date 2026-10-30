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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The 128x128, 16-color screen and every drawing call. All draw state (palettes, clip, camera,
 * pen, cursor, fill pattern) lives in [Ram] at its documented address, so a cart that pokes it
 * gets what it asked for; each call reads what it needs once, at its start.
 */
class Graphics(
    private val ram: Ram,
) {
    private var camX = 0
    private var camY = 0
    private var clipX0 = 0
    private var clipY0 = 0
    private var clipX1 = 128
    private var clipY1 = 128
    private var fill = 0
    private var fillTransparent = false

    /** Where the last `line` ended, for `line(x, y)` continuations. */
    private var lineX = 0
    private var lineY = 0
    private var lineValid = false

    fun reset() {
        resetPalettes()
        resetClip()
        ram[Addr.PEN] = 6
        ram[Addr.CURSOR_X] = 0
        ram[Addr.CURSOR_Y] = 0
        ram.poke2(Addr.CAMERA_X, 0)
        ram.poke2(Addr.CAMERA_Y, 0)
        ram.poke2(Addr.FILL_PATTERN, 0)
        ram[Addr.FILL_TRANSPARENT] = 0
        lineValid = false
    }

    // ---------------------------------------------------------------- state

    private fun load() {
        camX = ram.peek2(Addr.CAMERA_X)
        camY = ram.peek2(Addr.CAMERA_Y)
        clipX0 = ram[Addr.CLIP]
        clipY0 = ram[Addr.CLIP + 1]
        clipX1 = minOf(ram[Addr.CLIP + 2], 128)
        clipY1 = minOf(ram[Addr.CLIP + 3], 128)
        fill = ram[Addr.FILL_PATTERN] or (ram[Addr.FILL_PATTERN + 1] shl 8)
        fillTransparent = ram[Addr.FILL_TRANSPARENT] and 1 != 0
    }

    /** Resolves a call's color argument: given, it also becomes the pen; null keeps the pen. */
    private fun pen(col: Int?): Int {
        if (col == null) return ram[Addr.PEN]
        ram[Addr.PEN] = col and 0xff
        return col and 0xff
    }

    fun resetPalettes() {
        for (i in 0 until 16) {
            ram[Addr.DRAW_PAL + i] = i
            ram[Addr.SCREEN_PAL + i] = i
        }
        ram[Addr.DRAW_PAL] = 0x10
    }

    fun pal(
        c0: Int,
        c1: Int,
        p: Int,
    ) {
        val i = c0 and 0xf
        when (p) {
            1 -> ram[Addr.SCREEN_PAL + i] = c1 and 0x8f
            else -> ram[Addr.DRAW_PAL + i] = (ram[Addr.DRAW_PAL + i] and 0x10) or (c1 and 0xf)
        }
    }

    fun resetDrawPalette() {
        for (i in 0 until 16) ram[Addr.DRAW_PAL + i] = (ram[Addr.DRAW_PAL + i] and 0x10) or i
    }

    fun palt(
        c: Int,
        transparent: Boolean,
    ) {
        val i = c and 0xf
        ram[Addr.DRAW_PAL + i] = (ram[Addr.DRAW_PAL + i] and 0x0f) or (if (transparent) 0x10 else 0)
    }

    fun resetTransparency() {
        for (i in 0 until 16) palt(i, i == 0)
    }

    /** `palt(bits)`: bit 15 is color 0, bit 0 is color 15. */
    fun paltBits(bits: Int) {
        for (i in 0 until 16) palt(i, (bits shr (15 - i)) and 1 != 0)
    }

    fun clip(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        ram[Addr.CLIP] = x.coerceIn(0, 128)
        ram[Addr.CLIP + 1] = y.coerceIn(0, 128)
        ram[Addr.CLIP + 2] = (x + w).coerceIn(0, 128)
        ram[Addr.CLIP + 3] = (y + h).coerceIn(0, 128)
    }

    fun resetClip() = clip(0, 0, 128, 128)

    fun camera(
        x: Int,
        y: Int,
    ) {
        ram.poke2(Addr.CAMERA_X, x)
        ram.poke2(Addr.CAMERA_Y, y)
    }

    fun color(c: Int) {
        ram[Addr.PEN] = c and 0xff
    }

    fun cursor(
        x: Int,
        y: Int,
    ) {
        ram[Addr.CURSOR_X] = x
        ram[Addr.CURSOR_Y] = y
    }

    /** `fillp(p)`: the integer part is the 4x4 pattern (bit 15 top-left); `.5` makes set bits transparent. */
    fun fillp(p: Int) {
        ram.poke2(Addr.FILL_PATTERN, p shr 16)
        ram[Addr.FILL_TRANSPARENT] = if (p and 0x8000 != 0) 1 else 0
    }

    // ---------------------------------------------------------------- pixels

    private val screenBase = Addr.SCREEN

    fun screenPixel(
        x: Int,
        y: Int,
    ): Int {
        if (x !in 0..127 || y !in 0..127) return 0
        val b = ram[screenBase + y * 64 + (x shr 1)]
        return if (x and 1 == 0) b and 0xf else b shr 4
    }

    private fun setScreenPixel(
        x: Int,
        y: Int,
        c: Int,
    ) {
        val addr = screenBase + y * 64 + (x shr 1)
        val b = ram[addr]
        ram[addr] = if (x and 1 == 0) (b and 0xf0) or c else (b and 0x0f) or (c shl 4)
    }

    /** One pixel of a primitive at screen position [sx], [sy]: clip, fill pattern, draw palette. */
    private fun plot(
        sx: Int,
        sy: Int,
        col: Int,
    ) {
        if (sx < clipX0 || sx >= clipX1 || sy < clipY0 || sy >= clipY1) return
        var c = col and 0xf
        if (fill != 0 && (fill shr (15 - ((sy and 3) * 4 + (sx and 3)))) and 1 != 0) {
            if (fillTransparent) return
            c = (col shr 4) and 0xf
        }
        setScreenPixel(sx, sy, ram[Addr.DRAW_PAL + c] and 0xf)
    }

    private fun hspan(
        x0: Int,
        x1: Int,
        sy: Int,
        col: Int,
    ) {
        if (sy < clipY0 || sy >= clipY1) return
        val a = maxOf(minOf(x0, x1), clipX0)
        val b = minOf(maxOf(x0, x1), clipX1 - 1)
        for (x in a..b) plot(x, sy, col)
    }

    fun cls(col: Int) {
        val c = col and 0xf
        ram.fill(screenBase, c or (c shl 4), 0x2000)
        resetClip()
        cursor(0, 0)
    }

    fun pset(
        x: Int,
        y: Int,
        col: Int?,
    ) {
        load()
        plot(x - camX, y - camY, pen(col))
    }

    fun pget(
        x: Int,
        y: Int,
    ): Int {
        load()
        return screenPixel(x - camX, y - camY)
    }

    fun line(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        col: Int?,
    ) {
        load()
        val c = pen(col)
        var x = x0 - camX
        var y = y0 - camY
        val ex = x1 - camX
        val ey = y1 - camY
        val dx = abs(ex - x)
        val dy = -abs(ey - y)
        val stepX = if (x < ex) 1 else -1
        val stepY = if (y < ey) 1 else -1
        var err = dx + dy
        while (true) {
            plot(x, y, c)
            if (x == ex && y == ey) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += stepX
            }
            if (e2 <= dx) {
                err += dx
                y += stepY
            }
        }
        lineX = x1
        lineY = y1
        lineValid = true
    }

    /** `line(x, y, [col])`: continues from where the last line ended. */
    fun lineTo(
        x: Int,
        y: Int,
        col: Int?,
    ) {
        if (lineValid) {
            line(lineX, lineY, x, y, col)
        } else {
            lineX = x
            lineY = y
            lineValid = true
        }
    }

    fun resetLine() {
        lineValid = false
    }

    fun rect(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        col: Int?,
        filled: Boolean,
    ) {
        load()
        val c = pen(col)
        val l = minOf(x0, x1) - camX
        val r = maxOf(x0, x1) - camX
        val t = minOf(y0, y1) - camY
        val b = maxOf(y0, y1) - camY
        if (filled) {
            for (y in maxOf(t, clipY0)..minOf(b, clipY1 - 1)) hspan(l, r, y, c)
        } else {
            hspan(l, r, t, c)
            hspan(l, r, b, c)
            for (y in t + 1 until b) {
                plot(l, y, c)
                plot(r, y, c)
            }
        }
    }

    /** `rrect(x, y, w, h, r)`: a [w]x[h] rectangle whose corners are rounded with radius [r]. */
    fun rrect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        r: Int,
        col: Int?,
        filled: Boolean,
    ) {
        load()
        val c = pen(col)
        if (w <= 0 || h <= 0) return
        val radius = r.coerceIn(0, minOf(w, h) / 2)
        val l = x - camX
        val t = y - camY
        val right = l + w - 1
        val bottom = t + h - 1

        // How far a row (or column) [i] steps in from the edge [radius] away from a corner's center.
        fun inset(i: Int): Int {
            val d = radius - i
            if (d <= 0) return 0
            return radius - (sqrt((radius * radius - d * d).toDouble()) + 0.5).toInt()
        }
        for (j in 0 until h) {
            val k = inset(minOf(j, h - 1 - j))
            if (filled) {
                hspan(l + k, right - k, t + j, c)
            } else {
                plot(l + k, t + j, c)
                plot(right - k, t + j, c)
            }
        }
        if (filled) return
        for (i in 0 until w) {
            val k = inset(minOf(i, w - 1 - i))
            plot(l + i, t + k, c)
            plot(l + i, bottom - k, c)
        }
    }

    fun circ(
        cx: Int,
        cy: Int,
        r: Int,
        col: Int?,
        filled: Boolean,
    ) {
        load()
        val c = pen(col)
        if (r < 0) return
        val x0 = cx - camX
        val y0 = cy - camY
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            if (filled) {
                hspan(x0 - x, x0 + x, y0 + y, c)
                hspan(x0 - x, x0 + x, y0 - y, c)
                hspan(x0 - y, x0 + y, y0 + x, c)
                hspan(x0 - y, x0 + y, y0 - x, c)
            } else {
                plot(x0 + x, y0 + y, c)
                plot(x0 - x, y0 + y, c)
                plot(x0 + x, y0 - y, c)
                plot(x0 - x, y0 - y, c)
                plot(x0 + y, y0 + x, c)
                plot(x0 - y, y0 + x, c)
                plot(x0 + y, y0 - x, c)
                plot(x0 - y, y0 - x, c)
            }
            y++
            if (err < 0) {
                err += 2 * y + 1
            } else {
                x--
                err += 2 * (y - x) + 1
            }
        }
    }

    fun oval(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        col: Int?,
        filled: Boolean,
    ) {
        load()
        val c = pen(col)
        val l = minOf(x0, x1) - camX
        val r = maxOf(x0, x1) - camX
        val t = minOf(y0, y1) - camY
        val b = maxOf(y0, y1) - camY
        val cx = (l + r) / 2.0
        val cy = (t + b) / 2.0
        val rx = (r - l) / 2.0
        val ry = (b - t) / 2.0
        for (y in t..b) {
            val dy = if (ry == 0.0) 0.0 else (y - cy) / ry
            val half = rx * sqrt(maxOf(0.0, 1 - dy * dy))
            val left = (cx - half + 0.5).toInt()
            val right = (cx + half + 0.5).toInt()
            if (filled) {
                hspan(left, right, y, c)
            } else {
                plot(left, y, c)
                plot(right, y, c)
            }
        }
        if (filled) return
        // Columns too, so the flat top and bottom have no gaps.
        for (x in l..r) {
            val dx = if (rx == 0.0) 0.0 else (x - cx) / rx
            val half = ry * sqrt(maxOf(0.0, 1 - dx * dx))
            plot(x, (cy - half + 0.5).toInt(), c)
            plot(x, (cy + half + 0.5).toInt(), c)
        }
    }

    // ---------------------------------------------------------------- sprites and map

    fun sget(
        x: Int,
        y: Int,
    ): Int {
        if (x !in 0..127 || y !in 0..127) return 0
        val b = ram[Addr.GFX + y * 64 + (x shr 1)]
        return if (x and 1 == 0) b and 0xf else b shr 4
    }

    fun sset(
        x: Int,
        y: Int,
        col: Int?,
    ) {
        if (x !in 0..127 || y !in 0..127) return
        val c = ram[Addr.DRAW_PAL + (pen(col) and 0xf)] and 0xf
        val addr = Addr.GFX + y * 64 + (x shr 1)
        val b = ram[addr]
        ram[addr] = if (x and 1 == 0) (b and 0xf0) or c else (b and 0x0f) or (c shl 4)
    }

    /** A sprite-sheet pixel drawn at screen [sx], [sy]: transparent colors skip, the draw palette maps. */
    private fun blit(
        sx: Int,
        sy: Int,
        c: Int,
    ) {
        if (sx < clipX0 || sx >= clipX1 || sy < clipY0 || sy >= clipY1) return
        val mapped = ram[Addr.DRAW_PAL + c]
        if (mapped and 0x10 != 0) return
        setScreenPixel(sx, sy, mapped and 0xf)
    }

    fun spr(
        n: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        flipX: Boolean,
        flipY: Boolean,
    ) {
        load()
        val sx = (n and 15) * 8
        val sy = (n shr 4 and 15) * 8
        val dx = x - camX
        val dy = y - camY
        for (j in 0 until h) {
            for (i in 0 until w) {
                val px = if (flipX) w - 1 - i else i
                val py = if (flipY) h - 1 - j else j
                blit(dx + i, dy + j, sget(sx + px, sy + py))
            }
        }
    }

    fun sspr(
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        dw: Int,
        dh: Int,
        flipX: Boolean,
        flipY: Boolean,
    ) {
        load()
        if (dw <= 0 || dh <= 0 || sw == 0 || sh == 0) return
        val ox = dx - camX
        val oy = dy - camY
        for (j in 0 until dh) {
            val v = (j * sh) / dh
            val py = if (flipY) sh - 1 - v else v
            for (i in 0 until dw) {
                val u = (i * sw) / dw
                val px = if (flipX) sw - 1 - u else u
                blit(ox + i, oy + j, sget(sx + px, sy + py))
            }
        }
    }

    fun mget(
        x: Int,
        y: Int,
    ): Int {
        if (x !in 0..127 || y !in 0..63) return 0
        return if (y < 32) ram[Addr.MAP + y * 128 + x] else ram[Addr.MAP_SHARED + (y - 32) * 128 + x]
    }

    fun mset(
        x: Int,
        y: Int,
        v: Int,
    ) {
        if (x !in 0..127 || y !in 0..63) return
        if (y < 32) ram[Addr.MAP + y * 128 + x] = v else ram[Addr.MAP_SHARED + (y - 32) * 128 + x] = v
    }

    fun map(
        cx: Int,
        cy: Int,
        sx: Int,
        sy: Int,
        cw: Int,
        ch: Int,
        layers: Int,
    ) {
        for (j in 0 until ch) {
            for (i in 0 until cw) {
                val tile = mget(cx + i, cy + j)
                if (tile == 0) continue
                if (layers != 0 && ram[Addr.FLAGS + tile] and layers != layers) continue
                spr(tile, sx + i * 8, sy + j * 8, 8, 8, false, false)
            }
        }
    }

    /** `tline`: a line textured from the map, stepping map coordinates (in tiles) by [mdx], [mdy] per pixel. */
    fun tline(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        mx0: Int,
        my0: Int,
        mdx: Int,
        mdy: Int,
    ) {
        load()
        val maskX = ram[Addr.TLINE_MASK_X]
        val maskY = ram[Addr.TLINE_MASK_Y]
        val offX = ram[Addr.TLINE_OFFSET_X]
        val offY = ram[Addr.TLINE_OFFSET_Y]
        var mx = mx0
        var my = my0
        var x = x0 - camX
        var y = y0 - camY
        val ex = x1 - camX
        val ey = y1 - camY
        val dx = abs(ex - x)
        val dy = -abs(ey - y)
        val stepX = if (x < ex) 1 else -1
        val stepY = if (y < ey) 1 else -1
        var err = dx + dy
        while (true) {
            var tx = Fix.toInt(mx)
            var ty = Fix.toInt(my)
            if (maskX != 0) tx = (tx and (maskX - 1)) + offX
            if (maskY != 0) ty = (ty and (maskY - 1)) + offY
            val tile = mget(tx, ty)
            if (tile != 0) {
                val px = (mx shr 13) and 7
                val py = (my shr 13) and 7
                blit(x, y, sget((tile and 15) * 8 + px, (tile shr 4) * 8 + py))
            }
            mx += mdx
            my += mdy
            if (x == ex && y == ey) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += stepX
            }
            if (e2 <= dx) {
                err += dx
                y += stepY
            }
        }
    }

    // ---------------------------------------------------------------- text

    /**
     * Draws [text] with its top-left at [x], [y] and returns the x just past it. Handles the
     * control codes carts lean on: newline, carriage return, tab, backspace, `\f` (color),
     * `\#` (background), `\-` `\|` `\+` (nudges), `\^c` (clear) and `\0` (stop); other `\^`
     * commands and `\a` sounds are skipped.
     */
    fun print(
        text: String,
        x: Int,
        y: Int,
        col: Int,
    ): PrintResult {
        load()
        var fg = col and 0xf
        var bg = -1
        var cx = x
        var cy = y
        val homeX = x
        var right = x
        var lines = 1
        var i = 0
        while (i < text.length) {
            val code = text[i].code
            i++
            when (code) {
                0 -> return PrintResult(right, cy, lines, stopped = true)
                1 -> i += 2 // \* repeat: not drawn here
                2 -> if (i < text.length) bg = paramDigit(text[i++])
                3 -> if (i < text.length) cx += paramDigit(text[i++]) - 16
                4 -> if (i < text.length) cy += paramDigit(text[i++]) - 16
                5 -> {
                    if (i + 1 < text.length) {
                        cx += paramDigit(text[i++]) - 16
                        cy += paramDigit(text[i++]) - 16
                    }
                }
                6 -> i = command(text, i)
                7 -> while (i < text.length && text[i] != ' ' && text[i].code >= 32) i++
                8 -> cx -= 4
                9 -> cx = homeX + ((cx - homeX) / 16 + 1) * 16
                10 -> {
                    cx = homeX
                    cy += Font.HEIGHT
                    lines++
                }
                12 -> if (i < text.length) fg = paramDigit(text[i++]) and 0xf
                11 -> i += 2 // \v decoration: not drawn here
                13 -> cx = homeX
                14, 15 -> {}
                else -> {
                    val glyph = Font.glyph(code) ?: continue
                    if (bg >= 0) {
                        for (gy in -1 until Font.HEIGHT - 1) hspan(cx - 1 - camX, cx + glyph.advance - 2 - camX, cy + gy - camY, bg)
                    }
                    drawGlyph(glyph, cx - camX, cy - camY, fg)
                    cx += glyph.advance
                    if (cx > right) right = cx
                }
            }
        }
        return PrintResult(right, cy, lines, stopped = false)
    }

    class PrintResult(
        val right: Int,
        val lastY: Int,
        val lines: Int,
        val stopped: Boolean,
    )

    private fun command(
        text: String,
        start: Int,
    ): Int {
        if (start >= text.length) return start
        val c = text[start]
        var i = start + 1
        // How many parameter characters each `\^` command takes; most just toggle a mode.
        when (c) {
            'c' -> if (i < text.length) cls(paramDigit(text[i++]))
            'j' -> i += 2
            'o' -> i += 3
            '-', 'x', 'y', 's', 'r', 'd' -> i += 1
            '.' -> i += 8
            ':' -> i += 16
            '@', '!' -> i += 8
            else -> {}
        }
        return minOf(i, text.length)
    }

    private fun drawGlyph(
        glyph: Font.Glyph,
        sx: Int,
        sy: Int,
        col: Int,
    ) {
        val topBit = 1 shl (glyph.width - 1)
        for (row in 0 until 5) {
            val bits = glyph.rows[row]
            if (bits == 0) continue
            for (col2 in 0 until glyph.width) {
                if (bits and (topBit shr col2) != 0) plot(sx + col2, sy + row, col)
            }
        }
    }

    /** Moves the whole screen up [rows] pixels, filling the bottom with color 0 (cursor printing past the end). */
    fun scroll(rows: Int) {
        if (rows <= 0) return
        val bytes = minOf(rows, 128) * 64
        ram.copy(screenBase, screenBase + bytes, 0x2000 - bytes)
        ram.fill(screenBase + 0x2000 - bytes, 0, bytes)
    }

    // ---------------------------------------------------------------- output

    /**
     * The screen as 128x128 ARGB pixels, through the screen palette and the screen mode at
     * 0x5f2c: 1-3 stretch the left/top half (3 is a 64x64 zoom), 5-7 mirror it, 129-131 flip
     * and 133-135 rotate.
     */
    fun render(out: IntArray) {
        val pal = IntArray(16) { rgbOf(ram[Addr.SCREEN_PAL + it]) }
        val mode = ram[Addr.SCREEN_MODE]
        if (mode == 0 || mode == 4 || mode == 128 || mode == 132) {
            var o = 0
            for (addr in screenBase until screenBase + 0x2000) {
                val b = ram[addr]
                out[o++] = pal[b and 0xf]
                out[o++] = pal[b shr 4]
            }
            return
        }
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                var sx = x
                var sy = y
                when (mode) {
                    1 -> sx = x / 2
                    2 -> sy = y / 2
                    3 -> {
                        sx = x / 2
                        sy = y / 2
                    }
                    5 -> if (x >= 64) sx = 127 - x
                    6 -> if (y >= 64) sy = 127 - y
                    7 -> {
                        if (x >= 64) sx = 127 - x
                        if (y >= 64) sy = 127 - y
                    }
                    129 -> sx = 127 - x
                    130 -> sy = 127 - y
                    131 -> {
                        sx = 127 - x
                        sy = 127 - y
                    }
                    133 -> {
                        sx = y
                        sy = 127 - x
                    }
                    134 -> {
                        sx = 127 - x
                        sy = 127 - y
                    }
                    135 -> {
                        sx = 127 - y
                        sy = x
                    }
                }
                out[y * 128 + x] = pal[screenPixel(sx, sy)]
            }
        }
    }

    companion object {
        /** `0123456789abcdefghijklmnopqrstuv`: the digit P8SCII control codes take as a parameter. */
        fun paramDigit(c: Char): Int =
            when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'v' -> c - 'a' + 10
                in 'A'..'V' -> c - 'A' + 10
                else -> 0
            }

        private val BASE =
            intArrayOf(
                0x000000,
                0x1D2B53,
                0x7E2553,
                0x008751,
                0xAB5236,
                0x5F574F,
                0xC2C3C7,
                0xFFF1E8,
                0xFF004D,
                0xFFA300,
                0xFFEC27,
                0x00E436,
                0x29ADFF,
                0x83769C,
                0xFF77A8,
                0xFFCCAA,
            )

        private val EXTENDED =
            intArrayOf(
                0x291814,
                0x111D35,
                0x422136,
                0x125359,
                0x742F29,
                0x49333B,
                0xA28879,
                0xF3EF7D,
                0xBE1250,
                0xFF6C24,
                0xA8E72E,
                0x00B543,
                0x065AB5,
                0x754665,
                0xFF6E59,
                0xFF9D81,
            )

        /** ARGB for a screen-palette entry: 0-15, or 128-143 for the extended colors. */
        fun rgbOf(entry: Int): Int {
            val rgb = if (entry and 0x80 != 0) EXTENDED[entry and 0xf] else BASE[entry and 0xf]
            return (0xff shl 24) or rgb
        }
    }
}
