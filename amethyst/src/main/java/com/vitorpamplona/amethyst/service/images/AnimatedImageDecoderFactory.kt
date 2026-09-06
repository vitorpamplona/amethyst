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
package com.vitorpamplona.amethyst.service.images

import android.os.Build.VERSION.SDK_INT
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.isAnimatedHeif
import coil3.gif.isAnimatedWebP
import coil3.gif.isGif
import coil3.request.Options
import okio.BufferedSource
import okio.buffer

/**
 * Drop-in replacement for Coil's `AnimatedImageDecoder.Factory` that keeps the platform
 * `ImageDecoder` reading straight from the disk-cache file instead of a RAM copy of it.
 *
 * The sniffing is identical to Coil's. Two things differ, both about *how the bytes reach*
 * `ImageDecoder`:
 *
 * 1. The source is re-homed onto `FileSystem.SYSTEM` — see [onSystemFileSystem] for why
 *    Amethyst's disk cache otherwise forfeits the file fast path on every network image.
 * 2. The GIF frame-delay rewrite Coil applies on API < 34 is asked for only when the file
 *    actually contains a sub-threshold delay — see [hasSubThresholdGifFrameDelay]. The
 *    rewrite is stream-backed, so requesting it unconditionally undoes (1) for every GIF.
 *
 * Together those keep a large GIF off the heap entirely: a 69.8 MB / 201-frame GIF cost
 * 66 MB of heap plus a 66 MB direct `ByteBuffer` per decode before this, and animated results
 * are never memory-cached, so that repeated on every scroll back into view.
 */
@RequiresApi(28)
class AnimatedImageDecoderFactory : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val source = result.source.source()
        val isGif = DecodeUtils.isGif(source)
        if (!isGif && !isAnimatedNonGif(source)) return null

        return newAnimatedImageDecoder(result.source, options, isGif)
    }

    private fun isAnimatedNonGif(source: BufferedSource): Boolean = DecodeUtils.isAnimatedWebP(source) || (SDK_INT >= 30 && DecodeUtils.isAnimatedHeif(source))
}

/**
 * Builds Coil's [AnimatedImageDecoder] over the cheapest source we can give it.
 *
 * [mayNeedFrameDelayRewrite] should be true only for GIFs: Coil's rewriter no-ops on every
 * other format, so scanning one would be pure IO for a decision already made.
 */
@RequiresApi(28)
fun newAnimatedImageDecoder(
    source: ImageSource,
    options: Options,
    mayNeedFrameDelayRewrite: Boolean,
): Decoder {
    val onSystem = source.onSystemFileSystem()
    val decoder = AnimatedImageDecoder(onSystem, options, enforceMinimumFrameDelay(onSystem, mayNeedFrameDelayRewrite))

    // A re-homed source is ours, so nobody else will close it: Coil's engine closes the
    // source it handed us, and AnimatedImageDecoder only closes the frame-delay wrapper.
    return if (onSystem === source) decoder else ClosingDecoder(onSystem, decoder)
}

/**
 * Whether to ask Coil to clamp this GIF's sub-threshold frame delays. From API 34 the
 * platform decoder does it itself, which is why Coil's own default turns the rewrite off
 * there; below that we pay for it only when the file really has a delay to clamp.
 */
private fun enforceMinimumFrameDelay(
    source: ImageSource,
    mayNeedFrameDelayRewrite: Boolean,
): Boolean {
    if (!mayNeedFrameDelayRewrite || SDK_INT >= 34) return false

    // Not file-backed: the decode squashes the stream into RAM either way, so there is no
    // fast path to protect and no reason to deviate from Coil's default.
    val file = source.fileOrNull() ?: return true

    return source.fileSystem
        .source(file)
        .buffer()
        .use { it.hasSubThresholdGifFrameDelay() }
}

/** Closes [source] once [delegate] is done with it. */
private class ClosingDecoder(
    private val source: ImageSource,
    private val delegate: Decoder,
) : Decoder {
    override suspend fun decode(): DecodeResult? =
        try {
            delegate.decode()
        } finally {
            try {
                source.close()
            } catch (_: Exception) {
            }
        }
}
