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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A picture as it will be sent, with what an `imeta` tag says about it. */
class IosImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val blurhash: String?,
    val thumbhash: String?,
)

/**
 * Still pictures through UIKit: decoding applies the camera's orientation, and the re-encode
 * writes pixels only, so location and camera metadata never leave the phone.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosImageTranscoder {
    /** Types UIKit decodes and this re-encodes without losing what they are (not GIF, WebP, SVG, AVIF). */
    fun reencodes(mimeType: String?): Boolean =
        when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg", "image/heic", "image/heif", "image/png", "image/tiff", "image/bmp" -> true
            else -> false
        }

    /**
     * [bytes] scaled so the longer edge is at most [maxDimension] (never upscaled; null keeps the
     * size) and written again: a PNG stays a PNG, anything else becomes a JPEG at [jpegQuality]
     * (0..100). Null when UIKit can't decode it.
     */
    fun reencode(
        bytes: ByteArray,
        mimeType: String?,
        maxDimension: Int?,
        jpegQuality: Int,
    ): IosImage? {
        val image = decode(bytes) ?: return null
        val (width, height) = pixelSize(image)
        if (width <= 0 || height <= 0) return null
        val factor = maxDimension?.let { min(1.0, it.toDouble() / max(width, height)) } ?: 1.0
        val targetWidth = max(1, (width * factor).roundToInt())
        val targetHeight = max(1, (height * factor).roundToInt())
        val png = mimeType.equals("image/png", ignoreCase = true)
        val drawn = draw(image, targetWidth, targetHeight, opaque = !png)
        val data = (if (png) UIImagePNGRepresentation(drawn) else UIImageJPEGRepresentation(drawn, jpegQuality / 100.0)) ?: return null
        return IosImage(data.toByteArray(), if (png) "image/png" else "image/jpeg", targetWidth, targetHeight, blurhash(drawn), thumbhash(drawn))
    }

    /** [bytes] as they are, with their size and preview hashes; null when UIKit can't decode them. */
    fun describe(
        bytes: ByteArray,
        mimeType: String,
    ): IosImage? {
        val image = decode(bytes) ?: return null
        val (width, height) = pixelSize(image)
        return IosImage(bytes, mimeType, width, height, blurhash(image), thumbhash(image))
    }

    private fun decode(bytes: ByteArray): UIImage? = if (bytes.isEmpty()) null else UIImage.imageWithData(bytes.toNSData())

    // UIImage.size is in points and already accounts for the orientation.
    private fun pixelSize(image: UIImage): Pair<Int, Int> = image.size.useContents { (width * image.scale).roundToInt() to (height * image.scale).roundToInt() }

    private fun draw(
        image: UIImage,
        width: Int,
        height: Int,
        opaque: Boolean,
    ): UIImage {
        val format = UIGraphicsImageRendererFormat.preferredFormat()
        format.scale = 1.0
        format.opaque = opaque
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
        return renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())) }
    }

    private fun blurhash(image: UIImage): String? = runCatching { smallPixels(image)?.toBlurhash() }.getOrNull()

    private fun thumbhash(image: UIImage): String? = runCatching { smallPixels(image)?.toThumbhash() }.getOrNull()

    /** The picture at most 100 pixels on its longer edge, as ARGB pixels for the hash encoders. */
    private fun smallPixels(image: UIImage): PlatformImage? {
        val (width, height) = pixelSize(image)
        if (width <= 0 || height <= 0) return null
        val factor = min(1.0, HASH_EDGE.toDouble() / max(width, height))
        val w = max(1, (width * factor).roundToInt())
        val h = max(1, (height * factor).roundToInt())
        val cgImage = draw(image, w, h, opaque = false).CGImage ?: return null
        val rgba = ByteArray(w * h * 4)
        rgba.usePinned { pinned ->
            val space = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(pinned.addressOf(0), w.toULong(), h.toULong(), 8u, (w * 4).toULong(), space, CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value)
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)
            CGContextRelease(context)
            CGColorSpaceRelease(space)
        }
        val argb =
            IntArray(w * h) { i ->
                val o = i * 4
                ((rgba[o + 3].toInt() and 0xFF) shl 24) or
                    ((rgba[o].toInt() and 0xFF) shl 16) or
                    ((rgba[o + 1].toInt() and 0xFF) shl 8) or
                    (rgba[o + 2].toInt() and 0xFF)
            }
        return PlatformImage.create(argb, w, h)
    }

    private const val HASH_EDGE = 100
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
