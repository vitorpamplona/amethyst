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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.service.upload.CompressionException.EncodeFailed
import com.vitorpamplona.amethyst.commons.service.upload.CompressionException.InputTooLarge
import com.vitorpamplona.amethyst.commons.service.upload.CompressionException.UnsupportedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Re-encode + downscale step of the desktop image upload pipeline.
 *
 * Caller contract (see [UploadOrchestrator]):
 * - `Reencoded` → caller uploads the returned file and owns its cleanup.
 * - `PassThrough` → caller uploads the *original* file unchanged.
 * - On `CompressionException` → caller surfaces the fail-loud dialog
 *   (`InputTooLarge` / `UnsupportedFormat` / `EncodeFailed`).
 *
 * The reencoder is serial: only one image is decoded + encoded at any
 * given time, regardless of how many callers run in parallel. This
 * keeps the peak heap footprint to a single in-flight BufferedImage.
 */
object ImageReencoder {
    private const val DEFAULT_MAX_INPUT_PIXELS = 50L * 1_000_000L

    /**
     * Refuse inputs above this pixel count to defend against memory
     * bombs (a malicious header claiming 99999×99999 would otherwise
     * cause ImageIO to attempt a ~40 GB allocation).
     *
     * Tunable via `-Damethyst.compression.maxPixels=N`; queried on
     * every call so tests can override per-test via a system property.
     */
    val maxInputPixels: Long
        get() =
            System.getProperty("amethyst.compression.maxPixels")?.toLongOrNull()
                ?: DEFAULT_MAX_INPUT_PIXELS

    /**
     * CPU-bound work runs on `Default` (not `IO`). The serial cap
     * keeps memory bounded — only one BufferedImage in flight at any
     * given moment, regardless of how many callers fan in.
     */
    @Suppress("OPT_IN_USAGE")
    private val compressionDispatcher = Dispatchers.Default.limitedParallelism(1)

    /**
     * Re-encode a file from disk.
     *
     * @throws CompressionException on any failure that the caller
     *   should surface in a fail-loud dialog.
     */
    suspend fun reencode(
        source: File,
        quality: CompressionQuality,
    ): ReencodeResult =
        withContext(compressionDispatcher) {
            coroutineContext.ensureActive()
            val format = ImageFormatSniffer.sniff(source)
            // 1) Decide between pass-through, refuse, or re-encode.
            when (format) {
                is ImageFormat.Gif -> if (format.animated) return@withContext ReencodeResult.PassThrough(PassReason.Animated)
                is ImageFormat.WebP -> if (format.animated) return@withContext ReencodeResult.PassThrough(PassReason.Animated)
                ImageFormat.Svg -> return@withContext ReencodeResult.PassThrough(PassReason.Vector)
                ImageFormat.Avif -> throw UnsupportedFormat("avif")
                ImageFormat.Heic -> throw UnsupportedFormat("heic")
                is ImageFormat.Unknown -> throw UnsupportedFormat(format.mimeType)
                else -> { /* fall through to re-encode */ }
            }
            // 2) Decode with the pre-decode pixel guard + subsampling.
            coroutineContext.ensureActive()
            val img = decodeWithGuard(source, quality.maxDim)
            // 3) Resize only when source exceeds the target box —
            //    Thumbnailator otherwise upscales by default.
            coroutineContext.ensureActive()
            val finalImage =
                if (img.width > quality.maxDim || img.height > quality.maxDim) {
                    Thumbnails
                        .of(img)
                        .size(quality.maxDim, quality.maxDim)
                        .asBufferedImage()
                } else {
                    img
                }
            // 4) Encode JPEG to a temp file, preserving ICC where possible.
            coroutineContext.ensureActive()
            val temp = AmethystTempDir.createTempFile("amethyst_compress_", ".jpg")
            try {
                encodeJpeg(finalImage, temp, quality.jpegQuality)
                ReencodeResult.Reencoded(temp)
            } catch (t: Throwable) {
                withContext(NonCancellable) { temp.delete() }
                throw if (t is CompressionException) t else EncodeFailed(t)
            }
        }

    /**
     * Decode `source` into a `BufferedImage`, refusing inputs above
     * [maxInputPixels] before any pixel buffer is allocated. When the
     * source is larger than `targetMaxDim`, decode at a sub-sampled
     * stride so we don't allocate the full-resolution buffer just to
     * downscale it.
     */
    private fun decodeWithGuard(
        source: File,
        targetMaxDim: Int,
    ): BufferedImage {
        ImageIO.createImageInputStream(source).use { iis ->
            requireNotNull(iis) { "Could not open image input stream" }
            val reader =
                ImageIO.getImageReaders(iis).let {
                    if (!it.hasNext()) throw UnsupportedFormat("no ImageIO reader for this input")
                    it.next()
                }
            reader.input = iis
            try {
                val w = reader.getWidth(0)
                val h = reader.getHeight(0)
                val pixels = w.toLong() * h.toLong()
                val cap = maxInputPixels
                if (pixels > cap) throw InputTooLarge(pixels, cap)
                val param = reader.defaultReadParam
                val downscaleStride = subsamplingStride(w, h, targetMaxDim)
                if (downscaleStride > 1) {
                    param.setSourceSubsampling(downscaleStride, downscaleStride, 0, 0)
                }
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * Compute the per-axis subsampling stride. Subsampling is a
     * memory optimization — Thumbnailator does the final precise
     * resize. Use `floor` so the post-subsampling image stays ABOVE
     * the target, leaving headroom for Thumbnailator to land
     * exactly on `targetMaxDim`. A `ceil` would drop us below the
     * target and Thumbnailator could not recover.
     *
     * Example: 4032 × 3024 with target 1920 → stride 2 → decodes to
     * 2016 × 1512, then Thumbnailator resizes precisely to 1920 ×
     * 1440.
     */
    private fun subsamplingStride(
        w: Int,
        h: Int,
        targetMaxDim: Int,
    ): Int {
        val longestEdge = maxOf(w, h)
        if (longestEdge <= targetMaxDim) return 1
        return (longestEdge / targetMaxDim).coerceAtLeast(1)
    }

    /**
     * Encode `image` as JPEG into `out` at the given `quality` factor.
     * When the source has a non-sRGB ICC profile, the JPEG writer's
     * default behavior is to embed the profile as APP2 markers so the
     * uploaded file retains color fidelity on Display P3 / Adobe RGB
     * inputs.
     */
    private fun encodeJpeg(
        image: BufferedImage,
        out: File,
        quality: Float,
    ) {
        val writer =
            ImageIO.getImageWritersByMIMEType("image/jpeg").let {
                if (!it.hasNext()) throw EncodeFailed(IllegalStateException("no JPEG writer registered"))
                it.next()
            }
        try {
            FileImageOutputStream(out).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionType = "JPEG"
                        compressionQuality = quality
                    }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    /**
     * Pass-through reason captured for telemetry and diagnostics. Not
     * surfaced to users — the orchestrator decides on user-facing
     * messaging.
     */
    enum class PassReason {
        /** Animated GIF / animated WebP — would lose frames on re-encode. */
        Animated,

        /** Vector format (SVG) — no raster re-encode is meaningful. */
        Vector,
    }

    /** Outcome of a [reencode] call. */
    sealed class ReencodeResult {
        /** A new temp file was produced. Caller owns cleanup. */
        data class Reencoded(
            val file: File,
        ) : ReencodeResult()

        /**
         * Source must be uploaded byte-identical (animated / vector).
         */
        data class PassThrough(
            val reason: PassReason,
        ) : ReencodeResult()
    }

    /**
     * Returns `true` if the source carries a non-sRGB ICC profile. The
     * fail-loud / wide-gamut warning path will use this to ask for
     * user consent before silently shifting colors. Not yet wired into
     * the orchestrator — see Phase 7 of the plan.
     */
    fun hasWideGamutProfile(image: BufferedImage): Boolean {
        val cs = image.colorModel?.colorSpace ?: return false
        if (cs.isCS_sRGB) return false
        // CS_LINEAR_RGB / CS_PYCC / CS_GRAY are all narrow-gamut or
        // colorimetrically neutral — only true ICC color spaces with
        // wider primaries (Display P3, Adobe RGB, ProPhoto) matter.
        return cs.type == ColorSpace.TYPE_RGB && !cs.isCS_sRGB
    }
}
