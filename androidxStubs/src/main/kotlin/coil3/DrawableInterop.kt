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
package coil3

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import java.awt.image.BufferedImage

/**
 * The `Image.asDrawable(Resources)` bridge Coil ships only on Android.
 *
 * Every call site here is the same shape —
 * `(image.asDrawable(resources) as? BitmapDrawable)?.bitmap` — a notification
 * icon or an avatar being unwrapped for its pixels. On the JVM Coil hands back
 * a Skia bitmap instead, so this is the conversion, not a placeholder: a
 * version that returned null would make every one of those reads look like an
 * image that failed to load.
 */
fun Image.asDrawable(resources: Resources? = null): Drawable = BitmapDrawable(toAndroidBitmap())

/** The same conversion without the Android wrapper. */
fun Image.toAndroidBitmap(): Bitmap = Bitmap.wrap(toBitmap().toBufferedImage())

/**
 * The other direction — the `Bitmap.asImage()` Coil ships only on Android.
 *
 * The fetchers build a bitmap (a blurhash, a thumbhash, a base64 data URI) and
 * hand it to Coil as an [Image]; on the JVM Coil wants a Skia bitmap, so this
 * is the conversion those call sites need to keep reading as they do.
 */
fun Bitmap.asImage(shareable: Boolean = true): Image = image.toSkiaBitmap().asImage(shareable)

/** ARGB straight across: both sides are 32-bit and unpremultiplied here. */
internal fun BufferedImage.toSkiaBitmap(): org.jetbrains.skia.Bitmap {
    val info =
        org.jetbrains.skia.ImageInfo(
            org.jetbrains.skia.ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, null),
            width,
            height,
        )
    val bitmap = org.jetbrains.skia.Bitmap()
    bitmap.allocPixels(info)

    val argb = getRGB(0, 0, width, height, null, 0, width)
    val bytes = ByteArray(argb.size * 4)
    for (i in argb.indices) {
        val p = argb[i]
        val o = i * 4
        bytes[o] = (p and 0xFF).toByte()
        bytes[o + 1] = ((p shr 8) and 0xFF).toByte()
        bytes[o + 2] = ((p shr 16) and 0xFF).toByte()
        bytes[o + 3] = ((p shr 24) and 0xFF).toByte()
    }
    bitmap.installPixels(bytes)
    return bitmap
}

/**
 * Skia stores its pixels in the platform's native order, which is BGRA on the
 * desktops this runs on and RGBA elsewhere. Both are handled directly; anything
 * else re-encodes through PNG, which is lossless and correct for any colour
 * type at the cost of a round trip.
 */
private fun org.jetbrains.skia.Bitmap.toBufferedImage(): BufferedImage {
    val info = imageInfo
    val pixels = readPixels()

    if (pixels != null && info.colorType in setOf(ColorType.BGRA_8888, ColorType.RGBA_8888)) {
        val bgra = info.colorType == ColorType.BGRA_8888
        val premultiplied = info.colorAlphaType == ColorAlphaType.PREMUL
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val argb = IntArray(width * height)

        for (i in argb.indices) {
            val o = i * 4
            val a = pixels[o + 3].toInt() and 0xFF
            var r = pixels[o + if (bgra) 2 else 0].toInt() and 0xFF
            var g = pixels[o + 1].toInt() and 0xFF
            var b = pixels[o + if (bgra) 0 else 2].toInt() and 0xFF

            // BufferedImage's ARGB is straight alpha; Skia's default is
            // premultiplied, and skipping the un-multiply darkens every
            // partially transparent pixel.
            if (premultiplied && a in 1..254) {
                r = (r * 255 / a).coerceAtMost(255)
                g = (g * 255 / a).coerceAtMost(255)
                b = (b * 255 / a).coerceAtMost(255)
            }
            argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setRGB(0, 0, width, height, argb, 0, width)
        return out
    }

    val encoded =
        org.jetbrains.skia.Image
            .makeFromBitmap(this)
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
            ?.bytes
    return encoded
        ?.let { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it)) }
        ?: BufferedImage(maxOf(1, width), maxOf(1, height), BufferedImage.TYPE_INT_ARGB)
}
