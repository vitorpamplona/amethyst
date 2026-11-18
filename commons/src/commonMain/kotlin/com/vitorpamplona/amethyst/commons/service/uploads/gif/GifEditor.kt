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

import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.PictureHeaders
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim

/**
 * Edits a GIF and keeps it a GIF: the Studio's crop and trim, on every platform, with no encoder
 * from the platform. Colors come through exactly (each frame keeps its palette's colors), frames
 * keep their delays, and the file keeps its loop count. The two frames a trim cuts through are
 * shortened to the part that is kept, so the cut lands where it was asked.
 */
object GifEditor {
    /** The largest GIF file edited or read for frames; past it, the Studio's GIF edits fail rather than fill memory. */
    const val MAX_FILE_BYTES = 50L * 1024 * 1024

    /**
     * [gif] cropped to [crop] (null: all of it) and trimmed to [trim] (null: all of it), or null when
     * [gif] can't be read, is too large, or [keepGoing] turned false (a cancelled caller).
     */
    fun edit(
        gif: ByteArray,
        crop: ImageCrop?,
        trim: VideoTrim?,
        keepGoing: () -> Boolean = { true },
    ): ByteArray? =
        runCatching {
            if (gif.size > MAX_FILE_BYTES) return@runCatching null
            val reader = GifReader(gif)
            val (x, y, w, h) = crop?.pixels(reader.width, reader.height)?.toList() ?: listOf(0, 0, reader.width, reader.height)
            val writer = GifWriter(w, h, reader.loopCount, reader.sharedPalette)
            val cut = IntArray(w * h)
            var startMs = 0L
            var written = 0
            reader.forEachFrame(keepGoing) { frame ->
                val lengthMs = shownMs(frame.delayCs)
                val endMs = startMs + lengthMs
                val keptFrom = maxOf(startMs, trim?.startMs ?: Long.MIN_VALUE)
                val keptTo = minOf(endMs, trim?.endMs ?: Long.MAX_VALUE)
                if (keptTo > keptFrom) {
                    copyRect(frame.pixels, reader.width, x, y, w, h, cut)
                    // A frame the cut runs through shows only for the part kept.
                    val delay = if (keptTo - keptFrom == lengthMs) frame.delayCs else ((keptTo - keptFrom + 5) / 10).toInt().coerceAtLeast(MIN_DELAY_CS)
                    writer.add(cut, delay)
                    written++
                }
                startMs = endMs
                trim == null || startMs < trim.endMs
            }
            if (written == 0 || !keepGoing()) null else writer.finish()
        }.getOrNull()

    /** [framesAt] for one moment. */
    fun frameAt(
        gif: ByteArray,
        atMs: Long,
        maxEdge: Int,
        keepGoing: () -> Boolean = { true },
    ): ByteArray? = framesAt(gif, listOf(atMs), maxEdge, keepGoing).firstOrNull()

    /**
     * The frames of [gif] shown at each of [times] (past its end, as it loops), no larger than
     * [maxEdge] on their long side, each as a one-frame GIF, which every image loader draws. One
     * pass over the file for all of them. Nulls when [gif] can't be read or [keepGoing] turned false.
     */
    fun framesAt(
        gif: ByteArray,
        times: List<Long>,
        maxEdge: Int,
        keepGoing: () -> Boolean = { true },
    ): List<ByteArray?> =
        runCatching {
            if (gif.size > MAX_FILE_BYTES || times.isEmpty()) return@runCatching null
            val reader = GifReader(gif)
            val total = PictureHeaders.gifDurationMs(gif) ?: 0L
            val wanted = times.map { if (total > 0) it.coerceAtLeast(0) % total else 0L }
            val order = wanted.indices.sortedBy { wanted[it] }
            val out = arrayOfNulls<ByteArray>(times.size)
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(reader.width, reader.height))
            val w = (reader.width * scale).toInt().coerceAtLeast(1)
            val h = (reader.height * scale).toInt().coerceAtLeast(1)
            var next = 0
            var startMs = 0L
            reader.forEachFrame(keepGoing) { frame ->
                val endMs = startMs + shownMs(frame.delayCs)
                while (next < order.size && wanted[order[next]] < endMs) {
                    // Nearest neighbour: no new colors, so the frame keeps its palette.
                    val small = IntArray(w * h) { i -> frame.pixels[((i / w) * reader.height / h) * reader.width + (i % w) * reader.width / w] }
                    out[order[next]] = GifWriter(w, h, loopCount = null, reader.sharedPalette).apply { add(small, 0) }.finish()
                    next++
                }
                startMs = endMs
                next < order.size
            }
            if (!keepGoing()) null else out.toList()
        }.getOrNull() ?: List(times.size) { null }

    /** How long a frame with [delayCs] shows, as browsers draw it: under 2 hundredths, a tenth of a second. */
    internal fun shownMs(delayCs: Int): Long = if (delayCs < MIN_DELAY_CS) DEFAULT_DELAY_MS else delayCs * 10L

    private fun copyRect(
        canvas: IntArray,
        stride: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        out: IntArray,
    ) {
        for (row in 0 until h) canvas.copyInto(out, row * w, (y + row) * stride + x, (y + row) * stride + x + w)
    }

    private const val MIN_DELAY_CS = 2
    private const val DEFAULT_DELAY_MS = 100L
}
