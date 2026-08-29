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
package coil3.gif

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.vitorpamplona.amethyst.stubs.PlatformGaps
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/**
 * JVM stand-ins for Coil's `coil-gif` decoders.
 *
 * Coil publishes `coil-gif` for Android only — its decoders wrap Android's
 * `ImageDecoder` and `Movie`. Skia, which Compose Desktop already draws
 * through, reads the same formats, so the *decode* works here: GIF, animated
 * WebP and animated AVIF all come back as an image rather than a broken one.
 *
 * What does not come back is the animation. Coil's Android decoders return a
 * self-invalidating drawable that advances its own frames; the equivalent here
 * needs an image that redraws on a clock, which is a real piece of work and is
 * not this. So the first frame is decoded and the gap is reported once, naming
 * the file — a still frame is a far better failure than a blank box, and it is
 * honest about being one.
 */
class SkiaAnimatedDecoder(
    private val source: SourceFetchResult,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val bytes = source.source.source().use { it.readByteArray() }
        val codec = runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull() ?: return null

        return codec.use {
            if (it.frameCount > 1) {
                PlatformGaps.report(
                    "coil.animatedImage",
                    "animation needs an image that advances its own frames; " +
                        "the first frame of this ${it.frameCount}-frame ${source.mimeType ?: "image"} is shown instead",
                )
            }

            val bitmap = Bitmap()
            bitmap.allocPixels(it.imageInfo)
            it.readPixels(bitmap, 0)
            DecodeResult(image = bitmap.asImage(), isSampled = false)
        }
    }
}

/**
 * Matches Coil's Android `GifDecoder` — the pre-API-28 flavour — and its
 * `AnimatedImageDecoder`, which the app picks between by SDK level. Both resolve
 * to the same Skia decode here, so the branch the app already writes is
 * harmless.
 */
class GifDecoder {
    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? = if (isAnimated(result.mimeType)) SkiaAnimatedDecoder(result) else null
    }
}

class AnimatedImageDecoder(
    source: coil3.decode.ImageSource,
    options: Options,
) : Decoder by SkiaAnimatedDecoder(SourceFetchResult(source, null, coil3.decode.DataSource.DISK)) {
    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? = if (isAnimated(result.mimeType)) SkiaAnimatedDecoder(result) else null
    }
}

/** The formats Coil routes to these decoders on Android. */
private fun isAnimated(mimeType: String?): Boolean =
    when (mimeType) {
        "image/gif", "image/webp", "image/avif", "image/heif", "image/heic" -> true
        else -> false
    }
