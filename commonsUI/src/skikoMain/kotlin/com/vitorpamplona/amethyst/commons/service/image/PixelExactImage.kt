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

import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import coil3.compose.AsyncImagePainter
import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
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
import kotlin.math.roundToInt

/**
 * A decoded picture that draws itself 1:1 on whole device pixels while Skia draws in software
 * ([SoftwareDrawing]); on a GPU it draws scaled, as a plain bitmap does.
 *
 * In software, a picture drawn at any size but its own, or at a fraction of a pixel, is resampled
 * pixel by pixel every frame: a 600x800 photo drawn into a 450x600 slot costs ~6 ms a frame, the same
 * photo drawn 1:1 ~0.4 ms. A feed card rarely draws an image at exactly the size it was decoded at,
 * so this scales a copy to the size on screen once ([ExactCopies]) and draws that copy at whole
 * pixels. Making a copy costs about one scaled draw, so a size that changes every frame (a resize
 * being dragged) costs what it did before, and the frame after it settles is fast. Targets under
 * [MIN_COPY_PIXELS] draw scaled: resampling an avatar costs microseconds, and its copy would cost
 * memory.
 *
 * It sees only the scale of the canvas it records into, not a `graphicsLayer` scale applied when the
 * layer plays back: under a layer that zooms in, the copy (made for the unzoomed size) is what gets
 * enlarged. A viewer that zooms that way draws the full bitmap instead ([withFullResolution]).
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

    override fun draw(canvas: Canvas) {
        if (!SoftwareDrawing.active) return drawScaled(canvas)
        val m = canvas.localToDeviceAsMatrix33.mat
        // Only a scale and a translation can land on whole pixels.
        val axisAligned = m[1] == 0f && m[3] == 0f && m[6] == 0f && m[7] == 0f && m[8] == 1f && m[0] > 0f && m[4] > 0f
        if (!axisAligned) return drawScaled(canvas)

        val w = (width * m[0]).roundToInt()
        val h = (height * m[4]).roundToInt()
        if (w <= 0 || h <= 0) return

        val copy =
            when {
                w == width && h == height -> original
                w.toLong() * h < MIN_COPY_PIXELS || w.toLong() * h > MAX_COPY_PIXELS -> return drawScaled(canvas)
                else -> ExactCopies.get(bitmap, w, h) ?: scaledCopy(w, h).also { ExactCopies.put(bitmap, w, h, it, size) }
            }

        // Where it lands, snapped to whole pixels, with no scale left for Skia to resample by. In a
        // recorded picture the reset is relative to where the picture is played.
        canvas.save()
        canvas.resetMatrix()
        canvas.drawImage(copy, m[2].roundToInt().toFloat(), m[5].roundToInt().toFloat())
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
            c.drawImageRect(original, 0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, w.toFloat(), h.toFloat(), exactScaleSampling(w < width || h < height), null, true)
        }
        target.setImmutable()
        return Image.makeFromBitmap(target)
    }

    private companion object {
        // 128x128: below it a scaled draw costs ~0.3 ms or less.
        const val MIN_COPY_PIXELS = 128L * 128

        // A 2560x1600 screen: one copy can't take more than a quarter of [ExactCopies]' budget.
        const val MAX_COPY_PIXELS = 4L * 1024 * 1024
    }
}

private val SHRINK = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
private val GROW = CubicResampler(1f / 3, 1f / 3)

/**
 * How to scale a picture once to the size it is drawn at, so it can then be drawn 1:1: through
 * mipmaps when [shrinking], a cubic when growing. Slower than a frame's bilinear draw, but paid once.
 */
fun exactScaleSampling(shrinking: Boolean): SamplingMode = if (shrinking) SHRINK else GROW

/**
 * This state with a loaded [PixelExactImage] drawn from its full bitmap, for a viewer that zooms with
 * a `graphicsLayer` scale (see [PixelExactImage]): the zoom then shows the picture's own pixels.
 */
fun AsyncImagePainter.State.withFullResolution(): AsyncImagePainter.State {
    if (this !is AsyncImagePainter.State.Success) return this
    val image = result.image as? PixelExactImage ?: return this
    return AsyncImagePainter.State.Success(BitmapPainter(image.bitmap.asComposeImageBitmap()), result)
}

/**
 * The on-screen copies [PixelExactImage] draws, least recently drawn first, up to [BUDGET_BYTES].
 * The budget counts each copy and the decoded bitmap its key keeps alive, so the images held here,
 * on screen or just scrolled past, stay within it even once Coil's memory cache has let them go.
 * Keyed by the bitmap, so two images wrapping the same pixels share their copies. Read and written
 * only from [PixelExactImage.draw], on the UI thread.
 */
internal object ExactCopies {
    private const val BUDGET_BYTES = 64L * 1024 * 1024

    private class Entry(
        val copy: Image,
        val bytes: Long,
    )

    private data class Key(
        val source: Bitmap,
        val width: Int,
        val height: Int,
    )

    // Insertion order is drawing order: a hit moves its entry to the end.
    private val copies = LinkedHashMap<Key, Entry>()
    private var bytes = 0L

    fun get(
        source: Bitmap,
        width: Int,
        height: Int,
    ): Image? {
        val key = Key(source, width, height)
        val entry = copies.remove(key) ?: return null
        copies[key] = entry
        return entry.copy
    }

    fun put(
        source: Bitmap,
        width: Int,
        height: Int,
        copy: Image,
        sourceBytes: Long,
    ) {
        val key = Key(source, width, height)
        copies.remove(key)?.let { bytes -= it.bytes }
        val entry = Entry(copy, copy.width.toLong() * copy.height * 4 + sourceBytes)
        copies[key] = entry
        bytes += entry.bytes
        val oldest = copies.values.iterator()
        while (bytes > BUDGET_BYTES && oldest.hasNext()) {
            val evicted = oldest.next()
            if (evicted === entry) break
            bytes -= evicted.bytes
            oldest.remove()
            // A picture recorded with it keeps its own reference to the pixels.
            evicted.copy.close()
        }
    }

    /** Bytes held, for tests. */
    internal val heldBytes: Long get() = bytes

    /** Copies held, for tests. */
    internal val size: Int get() = copies.size

    internal fun clear() {
        copies.values.forEach { it.copy.close() }
        copies.clear()
        bytes = 0
    }
}
