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
package com.vitorpamplona.amethyst.commons.service.image

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use // skia types are AutoCloseable only on JVM, so kotlin.use breaks iOS
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * A decoded picture that draws itself 1:1 on whole device pixels.
 *
 * Skia draws in software on native Wayland, and there a picture drawn at any size but its own, or at
 * a fraction of a pixel, is resampled pixel by pixel every frame: a 600x800 photo drawn into a
 * 450x600 slot costs ~6 ms a frame, the same photo drawn 1:1 ~0.15 ms. A feed card rarely draws an
 * image at exactly the size it was decoded at (the prefetch decodes to the list's width, the card is
 * narrower), so this scales a copy to the size on screen once, keeps it ([ExactCopies]), and draws
 * that copy at whole pixels. A size that changes from frame to frame (a zoom, a resize being
 * dragged) draws the picture scaled, as before, rather than making a copy per frame.
 */
class PixelExactImage(
    val bitmap: Bitmap,
) : coil3.Image {
    override val size: Long get() =
        bitmap.imageInfo
            .computeMinByteSize()
            .toLong()
            .coerceAtLeast(0)
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val shareable: Boolean get() = true

    private val original: Image by lazy { Image.makeFromBitmap(bitmap) }

    // The last size drawn at and when, to tell a size that settled from one that animates.
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastDrawn: TimeSource.Monotonic.ValueTimeMark? = null

    override fun draw(canvas: Canvas) {
        val m = canvas.localToDeviceAsMatrix33.mat
        // Only a scale and a translation can land on whole pixels.
        val axisAligned = m[1] == 0f && m[3] == 0f && m[6] == 0f && m[7] == 0f && m[8] == 1f && m[0] > 0f && m[4] > 0f
        if (!axisAligned) return drawScaled(canvas)

        val w = (width * m[0]).roundToInt()
        val h = (height * m[4]).roundToInt()
        if (w <= 0 || h <= 0) return
        val left = m[2].roundToInt().toFloat()
        val top = m[5].roundToInt().toFloat()

        val copy =
            if (w == width && h == height) {
                original
            } else {
                val now = TimeSource.Monotonic.markNow()
                val animating = (w != lastWidth || h != lastHeight) && lastDrawn.let { it != null && (now - it).inWholeMilliseconds < ANIMATING_MS }
                lastWidth = w
                lastHeight = h
                lastDrawn = now
                if (animating || w.toLong() * h > MAX_COPY_PIXELS) return drawScaled(canvas)
                ExactCopies.get(this, w, h) ?: scaledCopy(w, h).also { ExactCopies.put(this, w, h, it) }
            }

        // Already where it lands: no need to move it.
        if (copy === original && abs(m[0] - 1f) < 1e-4f && abs(m[4] - 1f) < 1e-4f && left == m[2] && top == m[5]) {
            canvas.drawImage(original, 0f, 0f)
            return
        }
        canvas.save()
        canvas.resetMatrix()
        canvas.drawImage(copy, left, top)
        canvas.restore()
    }

    private fun drawScaled(canvas: Canvas) {
        canvas.drawImageRect(original, 0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, width.toFloat(), height.toFloat(), SamplingMode.LINEAR, null, true)
    }

    /** The picture filtered once to [w] x [h]: through mipmaps when shrinking, a cubic when growing. */
    private fun scaledCopy(
        w: Int,
        h: Int,
    ): Image {
        val target = Bitmap()
        target.allocN32Pixels(w, h, bitmap.alphaType == ColorAlphaType.OPAQUE)
        Canvas(target).use { c ->
            c.clear(Color.TRANSPARENT)
            val sampling = if (w < width || h < height) SHRINK else GROW
            c.drawImageRect(original, 0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, w.toFloat(), h.toFloat(), sampling, null, true)
        }
        target.setImmutable()
        return Image.makeFromBitmap(target)
    }

    private companion object {
        const val ANIMATING_MS = 250L
        const val MAX_COPY_PIXELS = 16L * 1024 * 1024
        val SHRINK = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
        val GROW = CubicResampler(1f / 3, 1f / 3)
    }
}

/**
 * The on-screen copies [PixelExactImage] draws, least recently drawn first, up to [BUDGET_BYTES]: the
 * images on screen and the ones just scrolled past, not one copy for every image in the memory cache.
 * Read and written only from [PixelExactImage.draw], on the UI thread.
 */
internal object ExactCopies {
    private const val BUDGET_BYTES = 64L * 1024 * 1024

    private data class Key(
        val source: PixelExactImage,
        val width: Int,
        val height: Int,
    )

    // Insertion order is drawing order: a hit moves its entry to the end.
    private val copies = LinkedHashMap<Key, Image>()
    private var bytes = 0L

    fun get(
        source: PixelExactImage,
        width: Int,
        height: Int,
    ): Image? {
        val key = Key(source, width, height)
        val copy = copies.remove(key) ?: return null
        copies[key] = copy
        return copy
    }

    fun put(
        source: PixelExactImage,
        width: Int,
        height: Int,
        copy: Image,
    ) {
        copies.remove(Key(source, width, height))?.let { bytes -= it.byteSize() }
        copies[Key(source, width, height)] = copy
        bytes += copy.byteSize()
        val oldest = copies.entries.iterator()
        while (bytes > BUDGET_BYTES && oldest.hasNext()) {
            val entry = oldest.next()
            if (entry.value === copy) break
            bytes -= entry.value.byteSize()
            oldest.remove()
            // A picture recorded with it keeps its own reference to the pixels.
            entry.value.close()
        }
    }

    private fun Image.byteSize() = width.toLong() * height * 4
}
