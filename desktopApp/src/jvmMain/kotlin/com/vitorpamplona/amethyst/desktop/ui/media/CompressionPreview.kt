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
package com.vitorpamplona.amethyst.desktop.ui.media

import com.vitorpamplona.amethyst.commons.service.upload.CompressionException
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import com.vitorpamplona.amethyst.commons.service.upload.ImageFormat
import com.vitorpamplona.amethyst.commons.service.upload.ImageFormatSniffer
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.PassReason
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.ReencodeResult
import java.io.File
import javax.imageio.ImageIO

/**
 * What the preview dialog renders for a single attachment.
 *
 * Built once per `Preview` click in the compose dialog; if the user
 * confirms via `Publish`, the items flow into the upload loop and the
 * orchestrator takes ownership of the cached temp files.
 */
sealed class PreviewItem {
    abstract val source: File
    abstract val originalSize: Long
    abstract val originalDims: Pair<Int, Int>?

    /** A reencoded image — the dialog shows side-by-side stats + a click-to-zoom. */
    data class Reencoded(
        override val source: File,
        override val originalSize: Long,
        override val originalDims: Pair<Int, Int>?,
        val compressedFile: File,
        val compressedSize: Long,
        val compressedDims: Pair<Int, Int>,
        val quality: CompressionQuality,
    ) : PreviewItem() {
        val savings: Double get() = 1.0 - (compressedSize.toDouble() / originalSize.toDouble())
    }

    /** ImageReencoder said pass-through (animated GIF/WebP, SVG, BypassByUser). */
    data class PassThrough(
        override val source: File,
        override val originalSize: Long,
        override val originalDims: Pair<Int, Int>?,
        val reason: PassReason,
    ) : PreviewItem()

    /** Reencode threw — user can still publish; original ships raw. */
    data class Failed(
        override val source: File,
        override val originalSize: Long,
        override val originalDims: Pair<Int, Int>?,
        val exception: CompressionException,
        val sourceFormat: ImageFormat,
    ) : PreviewItem()

    /** Non-image attachment — shown for context, uploaded straight through. */
    data class NonImage(
        override val source: File,
        override val originalSize: Long,
    ) : PreviewItem() {
        override val originalDims: Pair<Int, Int>? = null
    }
}

/**
 * Reads source dimensions from a file via ImageIO header (no decode).
 * Returns null on any failure — preview keeps rendering, just without
 * the dim string.
 */
internal fun readDimensions(file: File): Pair<Int, Int>? =
    try {
        ImageIO.createImageInputStream(file).use { iis ->
            iis ?: return null
            val readers = ImageIO.getImageReaders(iis)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            reader.input = iis
            try {
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    } catch (_: Exception) {
        null
    }

/**
 * Build the preview list for a batch of attachments. Runs ImageReencoder
 * eagerly so the dialog renders complete state once. Non-image files
 * are not sent through the reencoder.
 *
 * IMPORTANT: callers own the lifecycle of any [PreviewItem.Reencoded.compressedFile]
 * temps returned here. On Cancel: delete each. On Publish: hand off
 * to [com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator]
 * via its `preCompressed` parameter — the orchestrator cleans up.
 */
suspend fun buildPreview(
    attachments: List<File>,
    imageExtensions: Set<String>,
    quality: CompressionQuality,
): List<PreviewItem> =
    attachments.map { file ->
        val originalSize = file.length()
        val isImage = file.extension.lowercase() in imageExtensions
        if (!isImage) {
            return@map PreviewItem.NonImage(file, originalSize)
        }
        val originalDims = readDimensions(file)
        try {
            when (val result = ImageReencoder.reencode(file, quality)) {
                is ReencodeResult.Reencoded -> {
                    val compressedDims = readDimensions(result.file) ?: (0 to 0)
                    PreviewItem.Reencoded(
                        source = file,
                        originalSize = originalSize,
                        originalDims = originalDims,
                        compressedFile = result.file,
                        compressedSize = result.file.length(),
                        compressedDims = compressedDims,
                        quality = quality,
                    )
                }
                is ReencodeResult.PassThrough ->
                    PreviewItem.PassThrough(
                        source = file,
                        originalSize = originalSize,
                        originalDims = originalDims,
                        reason = result.reason,
                    )
            }
        } catch (e: CompressionException) {
            PreviewItem.Failed(
                source = file,
                originalSize = originalSize,
                originalDims = originalDims,
                exception = e,
                sourceFormat = ImageFormatSniffer.sniff(file),
            )
        }
    }

/** Delete any temp files held by Reencoded items. Called on Cancel. */
fun cleanupPreviewTemps(items: List<PreviewItem>) {
    items.forEach { item ->
        if (item is PreviewItem.Reencoded) {
            item.compressedFile.delete()
        }
    }
}

/** Compact byte formatter for the preview rows. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

internal fun formatDims(dims: Pair<Int, Int>?): String = dims?.let { "${it.first}×${it.second}" } ?: "unknown"

internal fun friendlyFailReason(e: CompressionException): String =
    when (e) {
        is CompressionException.UnsupportedFormat -> "format not supported (${e.format})"
        is CompressionException.InputTooLarge -> "image is larger than ${e.limit / 1_000_000} megapixels"
        is CompressionException.EncodeFailed -> "encoder error (${e.cause?.message ?: e.message})"
    }
