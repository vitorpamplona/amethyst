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
    /** [gif] cropped to [crop] (null: all of it) and trimmed to [trim] (null: all of it), or null when [gif] can't be read. */
    fun edit(
        gif: ByteArray,
        crop: ImageCrop?,
        trim: VideoTrim?,
    ): ByteArray? =
        runCatching {
            val reader = GifReader(gif)
            val (x, y, w, h) = crop?.pixels(reader.width, reader.height)?.toList() ?: listOf(0, 0, reader.width, reader.height)
            val writer = GifWriter(w, h, reader.loopCount, reader.sharedPalette)
            val cut = IntArray(w * h)
            var startMs = 0L
            var written = 0
            reader.forEachFrame { frame ->
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
            if (written == 0) null else writer.finish()
        }.getOrNull()

    /**
     * The frame of [gif] shown at [atMs] (past its end, as it loops), no larger than [maxEdge] on its
     * long side, as a one-frame GIF, which every image loader draws. Null when [gif] can't be read.
     */
    fun frameAt(
        gif: ByteArray,
        atMs: Long,
        maxEdge: Int,
    ): ByteArray? =
        runCatching {
            val reader = GifReader(gif)
            var shown: IntArray? = null
            var startMs = 0L
            val total = PictureHeaders.gifDurationMs(gif) ?: 0L
            val at = if (total > 0) atMs.coerceAtLeast(0) % total else 0L
            reader.forEachFrame { frame ->
                val endMs = startMs + shownMs(frame.delayCs)
                if (at < endMs) {
                    shown = frame.pixels.copyOf()
                    return@forEachFrame false
                }
                startMs = endMs
                true
            }
            val pixels = shown ?: return@runCatching null
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(reader.width, reader.height))
            val w = (reader.width * scale).toInt().coerceAtLeast(1)
            val h = (reader.height * scale).toInt().coerceAtLeast(1)
            // Nearest neighbour: no new colors, so the frame keeps its palette.
            val small = IntArray(w * h) { i -> pixels[((i / w) * reader.height / h) * reader.width + (i % w) * reader.width / w] }
            GifWriter(w, h, loopCount = null, reader.sharedPalette).apply { add(small, 0) }.finish()
        }.getOrNull()

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
