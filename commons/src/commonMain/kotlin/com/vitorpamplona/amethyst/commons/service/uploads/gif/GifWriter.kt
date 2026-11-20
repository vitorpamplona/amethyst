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
package com.vitorpamplona.amethyst.commons.service.uploads.gif

/**
 * Writes an animated GIF from whole frames (ARGB, alpha 0 or 255), with each frame's own colors.
 *
 * A frame with at most 256 colors (every frame cut from a GIF has, unless frames with different
 * local palettes overlap in it) keeps them exactly; past that the most used 256 stand in for the
 * rest. Each frame is written as the rectangle that changed since the one before. Drawing can't
 * make a pixel transparent again, so a frame followed by one that needs a pixel cleared is
 * written whole and clears after itself. Frames identical to the one before only lengthen it.
 */
internal class GifWriter(
    private val width: Int,
    private val height: Int,
    /** The NETSCAPE loop count to write (0 = forever); null writes none, which plays once. */
    private val loopCount: Int?,
    /**
     * Colors every frame is known to draw from (the source's global table), written once as the
     * global table with a slot for transparency, instead of a table in each frame. Used when it
     * has room for that slot; a frame with a color outside it still gets its own table.
     */
    sharedColors: IntArray? = null,
) {
    private val out = ByteBuffer(64 * 1024)
    private val global: Palette? = sharedColors?.takeIf { it.isNotEmpty() && it.size < 256 }?.let { Palette.shared(it) }

    /** The canvas a viewer has before the pending frame draws: empty (transparent) at the start. */
    private var shown = IntArray(width * height)
    private var pending: IntArray? = null
    private var pendingDelay = 0

    init {
        out.add("GIF89a".encodeToByteArray())
        out.addShort(width)
        out.addShort(height)
        val shared = global
        if (shared == null) {
            out.add(0) // No global color table: every frame carries its own.
        } else {
            out.add(0x80 or (shared.bits - 1))
        }
        out.add(0)
        out.add(0)
        if (shared != null) writeTable(shared)
        if (loopCount != null) {
            out.add(0x21)
            out.add(0xFF)
            out.add(11)
            out.add("NETSCAPE2.0".encodeToByteArray())
            out.add(3)
            out.add(1)
            out.addShort(loopCount)
            out.add(0)
        }
    }

    /** Adds a frame shown for [delayCs] hundredths of a second. [pixels] is copied. */
    fun add(
        pixels: IntArray,
        delayCs: Int,
    ) {
        val current = pending
        if (current != null && current.contentEquals(pixels) && pendingDelay + delayCs <= MAX_DELAY) {
            pendingDelay += delayCs
            return
        }
        flush(next = pixels)
        pending = pixels.copyOf()
        pendingDelay = delayCs
    }

    fun finish(): ByteArray {
        flush(next = null)
        out.add(0x3B)
        return out.toByteArray()
    }

    /** Writes the pending frame, now that [next] (null at the end) says whether it must clear after itself. */
    private fun flush(next: IntArray?) {
        val frame = pending ?: return
        if (next != null && clearsAPixel(frame, next)) {
            // Whole, cleared after: the next frame then draws on an empty canvas.
            writeFrame(frame, 0, 0, width, height, disposal = DISPOSE_TO_BACKGROUND)
            shown = IntArray(width * height)
        } else {
            val rect = changedRect(shown, frame)
            // Inside the rectangle, a pixel that didn't change is written transparent: the one below
            // shows through, and long runs of one index compress to almost nothing.
            val drawn = unchangedAsTransparent(frame, rect)
            writeFrame(drawn ?: frame, rect[0], rect[1], rect[2], rect[3], disposal = KEEP)
            shown = frame
        }
        pending = null
    }

    /**
     * [frame] with every pixel of [rect] that equals what is shown made transparent, or null when
     * that would cost exact colors (the rectangle's colors plus a transparent slot exceed 256).
     */
    private fun unchangedAsTransparent(
        frame: IntArray,
        rect: IntArray,
    ): IntArray? {
        val (left, top, w, h) = rect.toList()
        val drawn = frame.copyOf()
        val colors = HashSet<Int>()
        for (y in top until top + h) {
            for (x in left until left + w) {
                val i = y * width + x
                if (drawn[i] == shown[i]) {
                    drawn[i] = 0
                } else if (drawn[i] ushr 24 != 0) {
                    colors.add(drawn[i])
                }
            }
        }
        return if (colors.size <= 255) drawn else null
    }

    /** Whether some pixel opaque in [frame] is transparent in [next]. */
    private fun clearsAPixel(
        frame: IntArray,
        next: IntArray,
    ): Boolean {
        for (i in frame.indices) if (frame[i] ushr 24 != 0 && next[i] ushr 24 == 0) return true
        return false
    }

    /** The smallest rectangle holding every pixel that differs; a 1x1 one when none does. */
    private fun changedRect(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (a[row + x] != b[row + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    maxY = y
                }
            }
        }
        if (maxX < 0) return intArrayOf(0, 0, 1, 1)
        return intArrayOf(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun writeFrame(
        canvas: IntArray,
        left: Int,
        top: Int,
        w: Int,
        h: Int,
        disposal: Int,
    ) {
        val shared = global?.takeIf { it.covers(canvas, left, top, w, h, width) }
        val palette = shared ?: Palette.of(canvas, left, top, w, h, width)
        val indices = ByteArray(w * h)
        // Neighbouring pixels repeat: the last color's index skips most lookups.
        var lastColor = 0
        var lastIndex = palette.indexOf(0)
        for (y in 0 until h) {
            val row = (top + y) * width + left
            for (x in 0 until w) {
                val c = canvas[row + x]
                if (c != lastColor) {
                    lastColor = c
                    lastIndex = palette.indexOf(c)
                }
                indices[y * w + x] = lastIndex.toByte()
            }
        }

        // Graphic control extension: disposal, delay, the transparent index.
        out.add(0x21)
        out.add(0xF9)
        out.add(4)
        out.add((disposal shl 2) or (if (palette.transparentIndex >= 0) 1 else 0))
        out.addShort(pendingDelay.coerceIn(0, MAX_DELAY))
        out.add(maxOf(palette.transparentIndex, 0))
        out.add(0)

        // Image descriptor with a local color table.
        out.add(0x2C)
        out.addShort(left)
        out.addShort(top)
        out.addShort(w)
        out.addShort(h)
        if (shared != null) {
            out.add(0) // The global table.
        } else {
            out.add(0x80 or (palette.bits - 1))
            writeTable(palette)
        }

        val minCodeSize = maxOf(2, palette.bits)
        out.add(minCodeSize)
        val data = GifLzw.encode(indices, indices.size, minCodeSize)
        var at = 0
        while (at < data.size) {
            val size = minOf(255, data.size - at)
            out.add(size)
            out.add(data.copyOfRange(at, at + size))
            at += size
        }
        out.add(0)
    }

    private fun writeTable(palette: Palette) {
        for (i in 0 until (1 shl palette.bits)) {
            val color = palette.colors.getOrElse(i) { 0 }
            out.add((color ushr 16) and 0xFF)
            out.add((color ushr 8) and 0xFF)
            out.add(color and 0xFF)
        }
    }

    /** One frame's colors: exact when they fit in 256, else the most used ones with the rest mapped to the nearest. */
    private class Palette(
        val colors: IntArray,
        val transparentIndex: Int,
        private val lookup: IntIntMap,
    ) {
        /** Color-table bits: the table holds `1 shl bits` entries. */
        val bits: Int =
            run {
                var b = 1
                while ((1 shl b) < colors.size) b++
                b
            }

        /** Whether every opaque pixel of the rectangle has its exact color here. */
        fun covers(
            canvas: IntArray,
            left: Int,
            top: Int,
            w: Int,
            h: Int,
            stride: Int,
        ): Boolean {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = canvas[(top + y) * stride + left + x]
                    if (c ushr 24 != 0 && lookup.get(c) < 0) return false
                }
            }
            return true
        }

        fun indexOf(argb: Int): Int {
            if (argb ushr 24 == 0) return transparentIndex.coerceAtLeast(0)
            val known = lookup.get(argb)
            if (known >= 0) return known
            // Only past 256 colors: the nearest of the kept ones.
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in colors.indices) {
                if (i == transparentIndex) continue
                val d = distance(colors[i], argb)
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
            lookup.put(argb, best)
            return best
        }

        companion object {
            /** [colors] as they are, plus a transparent slot after them. */
            fun shared(colors: IntArray): Palette {
                val lookup = IntIntMap(colors.size)
                colors.forEachIndexed { i, c -> if (c ushr 24 != 0 && lookup.get(c) < 0) lookup.put(c, i) }
                return Palette(colors + 0, colors.size, lookup)
            }

            fun of(
                canvas: IntArray,
                left: Int,
                top: Int,
                w: Int,
                h: Int,
                stride: Int,
            ): Palette {
                val counts = IntIntMap(256)
                var anyTransparent = false
                for (y in 0 until h) {
                    val row = (top + y) * stride + left
                    for (x in 0 until w) {
                        val c = canvas[row + x]
                        if (c ushr 24 == 0) {
                            anyTransparent = true
                        } else {
                            counts.increment(c)
                        }
                    }
                }
                val room = if (anyTransparent) 255 else 256
                val all = ArrayList<Long>(counts.size)
                // Count in the high half, color in the low: sorting the longs ranks by use.
                counts.forEach { color, count -> all.add((count.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)) }
                val kept = if (all.size <= room) all.map { it.toInt() } else all.sortedDescending().take(room).map { it.toInt() }
                val colors = ArrayList<Int>(kept.size + 1)
                val lookup = IntIntMap(kept.size)
                var transparentIndex = -1
                if (anyTransparent) {
                    transparentIndex = 0
                    colors.add(0)
                }
                for (c in kept) {
                    lookup.put(c, colors.size)
                    colors.add(c)
                }
                if (colors.isEmpty()) colors.add(GifReader.OPAQUE_BLACK)
                return Palette(colors.toIntArray(), transparentIndex, lookup)
            }

            private fun distance(
                a: Int,
                b: Int,
            ): Int {
                val dr = ((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)
                val dg = ((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)
                val db = (a and 0xFF) - (b and 0xFF)
                return dr * dr + dg * dg + db * db
            }
        }
    }

    private companion object {
        const val KEEP = 1
        const val DISPOSE_TO_BACKGROUND = 2
        const val MAX_DELAY = 65535
    }
}
