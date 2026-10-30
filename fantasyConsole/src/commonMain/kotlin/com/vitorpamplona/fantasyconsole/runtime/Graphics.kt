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

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The 128x128, 16-color screen and every drawing call. All draw state (palettes, clip, camera,
 * pen, cursor, fill pattern, memory remaps) lives in [Ram] at its documented address, so a cart
 * that pokes it gets what it asked for; each call reads what it needs once, at its start.
 *
 * Color arguments arrive as raw 16:16 numbers: with 0x5f34 bit 0 set, a color whose bit
 * 0x1000.0000 is set also carries a fill pattern in its low 16 bits and fill flags above them.
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
    private var fillSprites = false
    private var fillGlobal = false
    private var invert = false
    private var gfxBase = Addr.GFX
    private var screenBase = Addr.SCREEN
    private var mapBase = Addr.MAP
    private var mapWidth = 128
    private var mapHeight = 64
    private var mapRegionStart = 0x1000
    private var mapRegionEnd = 0x3000

    /** Where the last `line` ended, for `line(x, y)` continuations. */
    private var lineX = 0
    private var lineY = 0
    private var lineValid = false

    /** Fraction bits of `tline`'s map coordinates: 13 means tiles, 16 means pixels (`tline(16)`). */
    private var tlinePrecision = 13

    /** `reset()`: the draw and hardware state at 0x5f00-0x5f7f back to the defaults. */
    fun reset() {
        ram.fill(Addr.DRAW_PAL, 0, 0x80)
        resetPalettes()
        resetClip()
        ram[Addr.PEN] = 6
        ram[Addr.GFX_ADDR] = 0x00
        ram[Addr.SCREEN_ADDR] = 0x60
        ram[Addr.MAP_ADDR] = 0x20
        ram[Addr.MAP_WIDTH] = 128
        lineValid = false
        tlinePrecision = 13
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
        val mode = ram[Addr.FILL_MODE]
        fillTransparent = mode and 1 != 0
        fillSprites = mode and 2 != 0
        fillGlobal = mode and 4 != 0
        invert = false
        gfxBase = videoBase(ram[Addr.GFX_ADDR], 0x00)
        screenBase = videoBase(ram[Addr.SCREEN_ADDR], 0x60)
        loadMap()
    }

    /** GFX and SCREEN may live at 0x0000, 0x6000 or in upper memory (0x8000, 0xa000, …). */
    private fun videoBase(
        value: Int,
        default: Int,
    ): Int =
        when (value) {
            0x00, 0x60, 0x80, 0xa0, 0xc0, 0xe0 -> value shl 8
            else -> default shl 8
        }

    /**
     * Where the map lives and how big it is: 0x10-0x2f (0x30-0x3f meaning 0x10-0x1f) inside the
     * 0x1000-0x2fff region, which wraps (so rows 32-63 of the default map are the shared half),
     * or 0x80 and above; the height is what fits.
     */
    private fun loadMap() {
        var page = ram[Addr.MAP_ADDR]
        if (page in 0x30..0x3f) page -= 0x20
        mapWidth = ram[Addr.MAP_WIDTH].let { if (it == 0) 256 else it }
        if (page >= 0x80) {
            mapBase = page shl 8
            mapRegionStart = mapBase
            mapRegionEnd = 0x10000
        } else {
            mapBase = (if (page in 0x10..0x2f) page else 0x20) shl 8
            mapRegionStart = 0x1000
            mapRegionEnd = 0x3000
        }
        mapHeight = (mapRegionEnd - mapRegionStart) / mapWidth
    }

    /**
     * Resolves a call's color argument (raw 16:16 bits): given, it also becomes the pen; null
     * keeps the pen. With 0x5f34 bit 0 set it may also set the fill pattern and, with bit 1,
     * invert this call's filled shape.
     */
    private fun pen(colBits: Int?): Int {
        if (colBits == null) return ram[Addr.PEN]
        val col = (colBits shr 16) and 0xff
        val flags = ram[Addr.COLOR_FILL_FLAGS]
        if (flags and 1 != 0 && colBits and 0x10000000 != 0) {
            ram.poke2(Addr.FILL_PATTERN, colBits and 0xffff)
            ram[Addr.FILL_MODE] = (colBits shr 24) and 7
            fill = colBits and 0xffff
            fillTransparent = colBits and 0x01000000 != 0
            fillSprites = colBits and 0x02000000 != 0
            fillGlobal = colBits and 0x04000000 != 0
            invert = flags and 2 != 0 && colBits and 0x08000000 != 0
        }
        ram[Addr.PEN] = col
        return col
    }

    fun resetPalettes() {
        for (i in 0 until 16) {
            ram[Addr.DRAW_PAL + i] = i
            ram[Addr.SCREEN_PAL + i] = i
            ram[Addr.SECONDARY_PAL + i] = i or (i shl 4)
        }
        ram[Addr.DRAW_PAL] = 0x10
    }

    /** `pal(c0, c1, p)`: 0 draw palette, 1 screen palette, 2 secondary palette (two colors per entry). */
    fun pal(
        c0: Int,
        c1: Int,
        p: Int,
    ) {
        val i = c0 and 0xf
        when (p) {
            1 -> ram[Addr.SCREEN_PAL + i] = c1 and 0x8f
            2 -> ram[Addr.SECONDARY_PAL + i] = c1 and 0xff
            else -> ram[Addr.DRAW_PAL + i] = (ram[Addr.DRAW_PAL + i] and 0x10) or (c1 and 0xf)
        }
    }

    /** `pal(p)`: one palette back to its defaults. */
    fun resetPalette(p: Int) {
        for (i in 0 until 16) {
            when (p) {
                1 -> ram[Addr.SCREEN_PAL + i] = i
                2 -> ram[Addr.SECONDARY_PAL + i] = i or (i shl 4)
                else -> ram[Addr.DRAW_PAL + i] = (ram[Addr.DRAW_PAL + i] and 0x10) or i
            }
        }
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

    /** `clip(x, y, w, h, [clip_previous])`: the new rectangle, optionally inside the old one. */
    fun clip(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        intersect: Boolean = false,
    ) {
        var x0 = x.coerceIn(0, 128)
        var y0 = y.coerceIn(0, 128)
        var x1 = (x + w).coerceIn(0, 128)
        var y1 = (y + h).coerceIn(0, 128)
        if (intersect) {
            x0 = maxOf(x0, ram[Addr.CLIP])
            y0 = maxOf(y0, ram[Addr.CLIP + 1])
            x1 = minOf(x1, ram[Addr.CLIP + 2])
            y1 = minOf(y1, ram[Addr.CLIP + 3])
        }
        ram[Addr.CLIP] = x0
        ram[Addr.CLIP + 1] = y0
        ram[Addr.CLIP + 2] = maxOf(x0, x1)
        ram[Addr.CLIP + 3] = maxOf(y0, y1)
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

    /** `color(colBits)` with a raw 16:16 color, which may carry a fill pattern (see [pen]). */
    fun colorBits(colBits: Int) {
        pen(colBits)
    }

    fun cursor(
        x: Int,
        y: Int,
    ) {
        ram[Addr.CURSOR_X] = x
        ram[Addr.CURSOR_Y] = y
    }

    /**
     * `fillp(p)`: the integer part is the 4x4 pattern (bit 15 top-left); the fraction holds
     * flags: `.5` set bits transparent, `.25` apply to sprites, `.125` secondary palette everywhere.
     */
    fun fillp(p: Int) {
        ram.poke2(Addr.FILL_PATTERN, p shr 16)
        ram[Addr.FILL_MODE] = ((p shr 15) and 1) or ((p shr 13) and 2) or ((p shr 11) and 4)
    }

    // ---------------------------------------------------------------- pixels

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

    private fun patternBit(
        sx: Int,
        sy: Int,
    ): Boolean = fill != 0 && (fill shr (15 - ((sy and 3) * 4 + (sx and 3)))) and 1 != 0

    /** One pixel of a primitive at screen position [sx], [sy]: clip, draw palette, fill pattern. */
    private fun plot(
        sx: Int,
        sy: Int,
        col: Int,
    ) {
        if (sx < clipX0 || sx >= clipX1 || sy < clipY0 || sy >= clipY1) return
        var primary = ram[Addr.DRAW_PAL + (col and 0xf)] and 0xf
        var secondary = ram[Addr.DRAW_PAL + ((col shr 4) and 0xf)] and 0xf
        if (fillGlobal) {
            val entry = ram[Addr.SECONDARY_PAL + primary]
            primary = entry and 0xf
            secondary = entry shr 4
        }
        if (patternBit(sx, sy)) {
            if (fillTransparent) return
            primary = secondary
        }
        setScreenPixel(sx, sy, primary)
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

    /**
     * A filled shape given as one span per row (screen coordinates, null where the row is
     * empty). Inverted, it fills everything in the clip rectangle except the shape.
     */
    private inline fun fillRows(
        top: Int,
        bottom: Int,
        col: Int,
        span: (Int) -> Long?,
    ) {
        if (!invert) {
            for (y in maxOf(top, clipY0)..minOf(bottom, clipY1 - 1)) {
                val s = span(y) ?: continue
                hspan((s shr 32).toInt(), s.toInt(), y, col)
            }
            return
        }
        for (y in clipY0 until clipY1) {
            val s = if (y in top..bottom) span(y) else null
            if (s == null) {
                hspan(clipX0, clipX1 - 1, y, col)
            } else {
                val a = (s shr 32).toInt()
                val b = s.toInt()
                if (a > clipX0) hspan(clipX0, a - 1, y, col)
                if (b < clipX1 - 1) hspan(b + 1, clipX1 - 1, y, col)
            }
        }
    }

    private fun span(
        a: Int,
        b: Int,
    ): Long = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

    fun cls(col: Int) {
        load()
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
        val sx = x - camX
        val sy = y - camY
        if (sx !in 0..127 || sy !in 0..127) return outOfBounds(Addr.OOB_PGET)
        return screenPixel(sx, sy)
    }

    /** What an out-of-bounds read returns: 0, or the value at [addr] when 0x5f36 bit 0x10 asks for it. */
    private fun outOfBounds(addr: Int): Int = if (ram[Addr.MISC_FLAGS] and 0x10 != 0) ram[addr] else 0

    fun line(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        col: Int?,
    ) {
        load()
        val c = pen(col)
        walkLine(x0 - camX, y0 - camY, x1 - camX, y1 - camY) { x, y -> plot(x, y, c) }
        lineX = x1
        lineY = y1
        lineValid = true
    }

    private inline fun walkLine(
        x0: Int,
        y0: Int,
        ex: Int,
        ey: Int,
        each: (Int, Int) -> Unit,
    ) {
        var x = x0
        var y = y0
        val dx = abs(ex - x)
        val dy = -abs(ey - y)
        val stepX = if (x < ex) 1 else -1
        val stepY = if (y < ey) 1 else -1
        var err = dx + dy
        while (true) {
            each(x, y)
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
            val row = span(l, r)
            fillRows(t, b, c) { row }
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
        if (filled) {
            fillRows(t, bottom, c) { row ->
                val k = inset(minOf(row - t, bottom - row))
                span(l + k, right - k)
            }
            return
        }
        for (j in 0 until h) {
            val k = inset(minOf(j, h - 1 - j))
            plot(l + k, t + j, c)
            plot(right - k, t + j, c)
        }
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
        if (filled) {
            // The midpoint walk gives each row's half width.
            val half = IntArray(r + 1)
            var x = r
            var y = 0
            var err = 1 - r
            while (x >= y) {
                half[y] = maxOf(half[y], x)
                half[x] = maxOf(half[x], y)
                y++
                if (err < 0) {
                    err += 2 * y + 1
                } else {
                    x--
                    err += 2 * (y - x) + 1
                }
            }
            fillRows(y0 - r, y0 + r, c) { row ->
                val w = half[abs(row - y0)]
                span(x0 - w, x0 + w)
            }
            return
        }
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            plot(x0 + x, y0 + y, c)
            plot(x0 - x, y0 + y, c)
            plot(x0 + x, y0 - y, c)
            plot(x0 - x, y0 - y, c)
            plot(x0 + y, y0 + x, c)
            plot(x0 - y, y0 + x, c)
            plot(x0 + y, y0 - x, c)
            plot(x0 - y, y0 - x, c)
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

        fun rowSpan(y: Int): Long {
            val dy = if (ry == 0.0) 0.0 else (y - cy) / ry
            val half = rx * sqrt(maxOf(0.0, 1 - dy * dy))
            return span((cx - half + 0.5).toInt(), (cx + half + 0.5).toInt())
        }
        if (filled) {
            fillRows(t, b, c) { rowSpan(it) }
            return
        }
        for (y in t..b) {
            val s = rowSpan(y)
            plot((s shr 32).toInt(), y, c)
            plot(s.toInt(), y, c)
        }
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
        if (x !in 0..127 || y !in 0..127) return outOfBounds(Addr.OOB_SGET)
        val b = ram[gfxBase(), y, x]
        return if (x and 1 == 0) b and 0xf else b shr 4
    }

    private fun gfxBase(): Int = videoBase(ram[Addr.GFX_ADDR], 0x00)

    private operator fun Ram.get(
        base: Int,
        y: Int,
        x: Int,
    ): Int = this[base + y * 64 + (x shr 1)]

    /** A sprite-sheet pixel with the base already resolved; no bounds or fallback. */
    private fun sheetPixel(
        x: Int,
        y: Int,
    ): Int {
        if (x !in 0..127 || y !in 0..127) return 0
        val b = ram[gfxBase + y * 64 + (x shr 1)]
        return if (x and 1 == 0) b and 0xf else b shr 4
    }

    fun sset(
        x: Int,
        y: Int,
        col: Int?,
    ) {
        if (x !in 0..127 || y !in 0..127) return
        val c = ram[Addr.DRAW_PAL + (pen(col) and 0xf)] and 0xf
        val addr = gfxBase() + y * 64 + (x shr 1)
        val b = ram[addr]
        ram[addr] = if (x and 1 == 0) (b and 0xf0) or c else (b and 0x0f) or (c shl 4)
    }

    /**
     * A sprite-sheet pixel drawn at screen [sx], [sy]: transparent colors skip, the draw palette
     * maps, and with the fill pattern applied to sprites the secondary palette picks two colors.
     */
    private fun blit(
        sx: Int,
        sy: Int,
        c: Int,
    ) {
        if (sx < clipX0 || sx >= clipX1 || sy < clipY0 || sy >= clipY1) return
        val mapped = ram[Addr.DRAW_PAL + c]
        if (mapped and 0x10 != 0) return
        var out = mapped and 0xf
        if (fillSprites) {
            val entry = ram[Addr.SECONDARY_PAL + out]
            if (patternBit(sx, sy)) {
                if (fillTransparent) return
                out = entry shr 4
            } else {
                out = entry and 0xf
            }
        }
        setScreenPixel(sx, sy, out)
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
        drawSprite(n, x - camX, y - camY, w, h, flipX, flipY)
    }

    private fun drawSprite(
        n: Int,
        dx: Int,
        dy: Int,
        w: Int,
        h: Int,
        flipX: Boolean,
        flipY: Boolean,
    ) {
        val sx = (n and 15) * 8
        val sy = (n shr 4 and 15) * 8
        for (j in 0 until h) {
            val py = if (flipY) h - 1 - j else j
            for (i in 0 until w) {
                val px = if (flipX) w - 1 - i else i
                blit(dx + i, dy + j, sheetPixel(sx + px, sy + py))
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
        if (dw == 0 || dh == 0 || sw == 0 || sh == 0) return
        // A negative destination size draws flipped on that axis, extending left/up from the origin.
        var fx = flipX
        var fy = flipY
        var w = dw
        var h = dh
        var ox = dx - camX
        var oy = dy - camY
        if (w < 0) {
            w = -w
            ox -= w
            fx = !fx
        }
        if (h < 0) {
            h = -h
            oy -= h
            fy = !fy
        }
        for (j in 0 until h) {
            val v = (j * sh) / h
            val py = if (fy) sh - 1 - v else v
            for (i in 0 until w) {
                val u = (i * sw) / w
                val px = if (fx) sw - 1 - u else u
                blit(ox + i, oy + j, sheetPixel(sx + px, sy + py))
            }
        }
    }

    /** The address of map cell [x], [y], or -1 outside the map. */
    private fun mapAddr(
        x: Int,
        y: Int,
    ): Int {
        if (x !in 0 until mapWidth || y !in 0 until mapHeight) return -1
        val offset = mapBase - mapRegionStart + y * mapWidth + x
        return mapRegionStart + offset % (mapRegionEnd - mapRegionStart)
    }

    fun mget(
        x: Int,
        y: Int,
    ): Int {
        loadMap()
        val addr = mapAddr(x, y)
        return if (addr < 0) outOfBounds(Addr.OOB_MGET) else ram[addr]
    }

    fun mset(
        x: Int,
        y: Int,
        v: Int,
    ) {
        loadMap()
        val addr = mapAddr(x, y)
        if (addr >= 0) ram[addr] = v
    }

    /** The whole map's size, `map()`'s default. */
    fun mapSize(): Pair<Int, Int> {
        loadMap()
        return mapWidth to mapHeight
    }

    private fun drawsTile(
        tile: Int,
        layers: Int,
    ): Boolean {
        if (tile == 0 && ram[Addr.MISC_FLAGS] and 0x8 == 0) return false
        return layers == 0 || ram[Addr.FLAGS + tile] and layers == layers
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
        load()
        for (j in 0 until ch) {
            val y = sy + j * 8 - camY
            if (y + 8 <= clipY0 || y >= clipY1) continue
            for (i in 0 until cw) {
                val x = sx + i * 8 - camX
                if (x + 8 <= clipX0 || x >= clipX1) continue
                val addr = mapAddr(cx + i, cy + j)
                if (addr < 0) continue
                val tile = ram[addr]
                if (!drawsTile(tile, layers)) continue
                drawSprite(tile, x, y, 8, 8, false, false)
            }
        }
    }

    /** `tline(bits)`: how many fraction bits the next calls' map coordinates use (13 = tiles, 16 = pixels). */
    fun tlinePrecision(bits: Int) {
        tlinePrecision = bits.coerceIn(0, 31)
    }

    /** `tline`: a line textured from the map, stepping map coordinates by [mdx], [mdy] per pixel. */
    fun tline(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        mx0: Int,
        my0: Int,
        mdx: Int,
        mdy: Int,
        layers: Int,
    ) {
        load()
        val maskX = ram[Addr.TLINE_MASK_X].let { if (it == 0) 256 else it } - 1
        val maskY = ram[Addr.TLINE_MASK_Y].let { if (it == 0) 256 else it } - 1
        val offX = ram[Addr.TLINE_OFFSET_X]
        val offY = ram[Addr.TLINE_OFFSET_Y]
        val shift = tlinePrecision
        var mx = mx0.toLong()
        var my = my0.toLong()
        walkLine(x0 - camX, y0 - camY, x1 - camX, y1 - camY) { x, y ->
            val pxX = (mx shr shift).toInt()
            val pxY = (my shr shift).toInt()
            val tx = ((pxX shr 3) and maskX) + offX
            val ty = ((pxY shr 3) and maskY) + offY
            val addr = mapAddr(tx, ty)
            val tile = if (addr < 0) 0 else ram[addr]
            if (drawsTile(tile, layers)) {
                blit(x, y, sheetPixel((tile and 15) * 8 + (pxX and 7), (tile shr 4) * 8 + (pxY and 7)))
            }
            mx += mdx
            my += mdy
        }
    }

    // ---------------------------------------------------------------- text (see Printer)

    /** Loads the draw state for a run of [textPixel] calls. */
    internal fun beginText() = load()

    /** A text pixel at world position [x], [y]: camera, clip and draw palette apply, the fill pattern does not. */
    internal fun textPixel(
        x: Int,
        y: Int,
        col: Int,
    ) {
        val sx = x - camX
        val sy = y - camY
        if (sx < clipX0 || sx >= clipX1 || sy < clipY0 || sy >= clipY1) return
        setScreenPixel(sx, sy, ram[Addr.DRAW_PAL + (col and 0xf)] and 0xf)
    }

    /** Moves the whole screen up [rows] pixels, filling the bottom with color 0 (cursor printing past the end). */
    fun scroll(rows: Int) {
        if (rows <= 0) return
        load()
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
        screenBase = videoBase(ram[Addr.SCREEN_ADDR], 0x60)
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
