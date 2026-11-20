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
 * Reads a GIF frame by frame as it plays: each frame is the whole canvas (ARGB, alpha 0 or 255)
 * after the frame is drawn over what the previous frame's disposal left. Interlaced frames,
 * transparency, local color tables and the three disposals are followed as browsers do.
 */
internal class GifReader(
    private val bytes: ByteArray,
) {
    val width: Int
    val height: Int

    /** The NETSCAPE loop count (0 = forever), or null when the file has none, which plays once. */
    var loopCount: Int? = null
        private set

    /** Whether any frame marks a color transparent. */
    var hasTransparency: Boolean = false
        private set

    /**
     * Every color the file can draw, the global and all local tables together, when they are
     * fewer than 256; null when they are more. A writer can share them in one table.
     */
    var sharedPalette: IntArray? = null
        private set

    private val globalTable: IntArray?
    private val firstBlock: Int

    init {
        require(bytes.size >= 13 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) { "not a GIF" }
        width = u16(6)
        height = u16(8)
        require(width > 0 && height > 0) { "empty GIF" }
        // The canvas and the writer's few copies of it stay a few tens of MB, even on a phone.
        require(width.toLong() * height <= MAX_CANVAS_PIXELS) { "GIF too large: ${width}x$height" }
        val flags = bytes[10].toInt() and 0xFF
        var at = 13
        globalTable =
            if (flags and 0x80 != 0) {
                val size = 1 shl ((flags and 0x07) + 1)
                readTable(at, size).also { at += size * 3 }
            } else {
                null
            }
        firstBlock = at
        val colors = LinkedHashSet<Int>()
        globalTable?.forEach { colors.add(it) }
        scan(colors)
        sharedPalette = colors.takeIf { it.isNotEmpty() && it.size < 256 }?.toIntArray()
    }

    /** A frame as shown: the canvas and how long it stays, in hundredths of a second as written. */
    class Frame(
        val pixels: IntArray,
        val delayCs: Int,
    )

    /**
     * Calls [onFrame] with each frame in order; the canvas array is reused, so copy it to keep it.
     * Stops when [onFrame] returns false, or before the next frame once [keepGoing] says so (a
     * cancelled caller).
     */
    fun forEachFrame(
        keepGoing: () -> Boolean = { true },
        onFrame: (Frame) -> Boolean,
    ) {
        val canvas = IntArray(width * height)
        var restore: IntArray? = null
        var delay = 0
        var transparent = -1
        var disposal = 0
        // What the previous frame asked to be done with its rectangle before the next one draws.
        var lastDisposal = 0
        var lastRect = IntArray(4)

        var at = firstBlock
        while (at < bytes.size) {
            when (bytes[at].toInt() and 0xFF) {
                0x21 -> {
                    if (at + 1 >= bytes.size) return
                    val label = bytes[at + 1].toInt() and 0xFF
                    if (label == 0xF9 && at + 7 < bytes.size) {
                        val packed = bytes[at + 3].toInt() and 0xFF
                        disposal = (packed ushr 2) and 0x07
                        delay = u16(at + 4)
                        transparent = if (packed and 0x01 != 0) bytes[at + 6].toInt() and 0xFF else -1
                    }
                    at = skipSubBlocks(at + 2) ?: return
                }

                0x2C -> {
                    if (at + 10 > bytes.size || !keepGoing()) return
                    val left = u16(at + 1)
                    val top = u16(at + 3)
                    val w = u16(at + 5)
                    val h = u16(at + 7)
                    // A frame larger than any canvas allowed is a broken or hostile file.
                    if (w.toLong() * h > MAX_CANVAS_PIXELS) return
                    val flags = bytes[at + 9].toInt() and 0xFF
                    at += 10
                    val table =
                        if (flags and 0x80 != 0) {
                            val size = 1 shl ((flags and 0x07) + 1)
                            readTable(at, size).also { at += size * 3 }
                        } else {
                            globalTable
                        }
                    if (at >= bytes.size) return
                    val minCodeSize = bytes[at].toInt() and 0xFF
                    val (data, after) = collectSubBlocks(at + 1) ?: return
                    at = after

                    // The previous frame's disposal, now that a new frame comes.
                    when (lastDisposal) {
                        2 -> clearRect(canvas, lastRect)
                        3 -> restore?.copyInto(canvas)
                    }
                    if (disposal == 3) restore = canvas.copyOf()

                    val indices = ByteArray(w * h)
                    val decoded = GifLzw.decode(data, minCodeSize, indices, w * h)
                    if (table != null) draw(canvas, indices, decoded, left, top, w, h, flags and 0x40 != 0, table, transparent)

                    lastDisposal = disposal
                    lastRect = intArrayOf(left, top, w, h)
                    val keepGoing = onFrame(Frame(canvas, delay))
                    delay = 0
                    transparent = -1
                    disposal = 0
                    if (!keepGoing) return
                }

                else -> return
            }
        }
    }

    private fun draw(
        canvas: IntArray,
        indices: ByteArray,
        decoded: Int,
        left: Int,
        top: Int,
        w: Int,
        h: Int,
        interlaced: Boolean,
        table: IntArray,
        transparent: Int,
    ) {
        for (row in 0 until h) {
            val y = top + (if (interlaced) interlacedRow(row, h) else row)
            if (y !in 0 until height) continue
            for (col in 0 until w) {
                val p = row * w + col
                if (p >= decoded) return
                val x = left + col
                if (x !in 0 until width) continue
                val index = indices[p].toInt() and 0xFF
                if (index == transparent || index >= table.size) continue
                canvas[y * width + x] = table[index]
            }
        }
    }

    /** The image row stored [row]th of an interlaced frame [h] rows tall: rows 0, 8, …; then 4, 12, …; 2, 6, …; 1, 3, …. */
    private fun interlacedRow(
        row: Int,
        h: Int,
    ): Int {
        var r = row
        val pass1 = (h + 7) / 8
        if (r < pass1) return r * 8
        r -= pass1
        val pass2 = (h + 3) / 8
        if (r < pass2) return 4 + r * 8
        r -= pass2
        val pass3 = (h + 1) / 4
        if (r < pass3) return 2 + r * 4
        r -= pass3
        return 1 + r * 2
    }

    private fun clearRect(
        canvas: IntArray,
        rect: IntArray,
    ) {
        val (left, top, w, h) = rect.toList()
        for (y in maxOf(0, top) until minOf(height, top + h)) {
            for (x in maxOf(0, left) until minOf(width, left + w)) canvas[y * width + x] = 0
        }
    }

    /** One pass over the blocks for what a writer needs before the first frame: transparency, the loop count and every color. */
    private fun scan(colors: MutableSet<Int>) {
        var at = firstBlock
        while (at < bytes.size) {
            when (bytes[at].toInt() and 0xFF) {
                0x21 -> {
                    if (at + 1 >= bytes.size) return
                    val label = bytes[at + 1].toInt() and 0xFF
                    if (label == 0xF9 && at + 3 < bytes.size && bytes[at + 3].toInt() and 0x01 != 0) hasTransparency = true
                    if (label == 0xFF && loopCount == null && at + 17 < bytes.size && matches(at + 3, "NETSCAPE2.0") && bytes[at + 14].toInt() >= 3) loopCount = u16(at + 16)
                    at = skipSubBlocks(at + 2) ?: return
                }
                0x2C -> {
                    if (at + 10 > bytes.size) return
                    val flags = bytes[at + 9].toInt() and 0xFF
                    at += 10
                    if (flags and 0x80 != 0) {
                        val size = 1 shl ((flags and 0x07) + 1)
                        if (colors.size < 256) readTable(at, size).forEach { colors.add(it) }
                        at += 3 * size
                    }
                    at = skipSubBlocks(at + 1) ?: return
                }
                else -> return
            }
        }
    }

    private fun readTable(
        at: Int,
        size: Int,
    ): IntArray =
        IntArray(size) { i ->
            val p = at + i * 3
            if (p + 2 >= bytes.size) {
                OPAQUE_BLACK
            } else {
                OPAQUE_BLACK or ((bytes[p].toInt() and 0xFF) shl 16) or ((bytes[p + 1].toInt() and 0xFF) shl 8) or (bytes[p + 2].toInt() and 0xFF)
            }
        }

    private fun skipSubBlocks(start: Int): Int? {
        var at = start
        while (at < bytes.size) {
            val size = bytes[at].toInt() and 0xFF
            at += 1 + size
            if (size == 0) return at
        }
        return null
    }

    private fun collectSubBlocks(start: Int): Pair<ByteArray, Int>? {
        val data = ByteBuffer()
        var at = start
        while (at < bytes.size) {
            val size = bytes[at].toInt() and 0xFF
            if (size == 0) return data.toByteArray() to at + 1
            if (at + 1 + size > bytes.size) return data.toByteArray() to bytes.size
            data.add(bytes.copyOfRange(at + 1, at + 1 + size))
            at += 1 + size
        }
        return data.toByteArray() to at
    }

    private fun matches(
        at: Int,
        text: String,
    ): Boolean {
        if (at + text.length > bytes.size) return false
        for (i in text.indices) if (bytes[at + i] != text[i].code.toByte()) return false
        return true
    }

    private fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    companion object {
        const val OPAQUE_BLACK = 0xFF000000.toInt()

        /** The largest canvas read: 2048x2048, past any GIF people post. */
        const val MAX_CANVAS_PIXELS = 2048L * 2048L
    }
}
