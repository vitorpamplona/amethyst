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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import com.vitorpamplona.amethyst.service.images.BlurhashWrapper
import com.vitorpamplona.amethyst.service.images.ThumbhashWrapper
import com.vitorpamplona.amethyst.service.uploads.isAvif
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import java.nio.ByteBuffer

/**
 * Result of precomputing placeholder metadata during an upload. The bitmap or video thumbnail is
 * decoded exactly once and both hashes are computed from the same pixels to keep the hot upload
 * path cheap.
 */
data class PreviewHashes(
    val blurhash: BlurhashWrapper? = null,
    val thumbhash: ThumbhashWrapper? = null,
    val dim: DimensionTag? = null,
) {
    companion object {
        val EMPTY = PreviewHashes()
    }
}

object PreviewMetadataCalculator {
    private const val LOG_TAG = "PreviewMetadataCalc"

    private fun isImage(mimeType: String?) = mimeType?.startsWith("image/", ignoreCase = true) == true

    private fun isVideo(mimeType: String?) = mimeType?.startsWith("video/", ignoreCase = true) == true

    fun shouldAttempt(mimeType: String?): Boolean = isImage(mimeType) || isVideo(mimeType)

    private fun createBitmapOptions() =
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

    fun computeFromBytes(
        data: ByteArray,
        mimeType: String?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes =
        when {
            isAvif(mimeType) -> {
                val bitmap = decodeAvifBytes(data)
                processImage(bitmap, dimPrecomputed)
            }

            isImage(mimeType) -> {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, createBitmapOptions())
                processImage(bitmap, dimPrecomputed)
            }

            isVideo(mimeType) -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ByteArrayMediaDataSource(data))
                    processRetriever(retriever, dimPrecomputed)
                } finally {
                    retriever.release()
                }
            }

            else -> {
                PreviewHashes(dim = dimPrecomputed)
            }
        }

    fun computeFromUri(
        context: Context,
        uri: Uri,
        mimeType: String?,
        dimPrecomputed: DimensionTag? = null,
    ): PreviewHashes? {
        if (!shouldAttempt(mimeType)) return null

        return try {
            when {
                isAvif(mimeType) -> {
                    val bitmap = decodeAvifFromUri(context, uri)
                    processImage(bitmap, dimPrecomputed)
                }

                isImage(mimeType) -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream, null, createBitmapOptions())
                        processImage(bitmap, dimPrecomputed)
                    } ?: PreviewHashes(dim = dimPrecomputed)
                }

                isVideo(mimeType) -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        processRetriever(retriever, dimPrecomputed)
                    } finally {
                        retriever.release()
                    }
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to compute metadata from uri", e)
            null
        }
    }

    private fun processImage(
        bitmap: Bitmap?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes {
        val hashes = processBitmap(bitmap)
        return hashes.copy(dim = hashes.dim ?: dimPrecomputed)
    }

    private fun processBitmap(bitmap: Bitmap?): PreviewHashes =
        if (bitmap != null) {
            try {
                val blurhash =
                    runCatching { BlurhashWrapper(bitmap.toBlurhash()) }
                        .onFailure { Log.w(LOG_TAG, "blurhash generation failed", it) }
                        .getOrNull()
                val thumbhash =
                    runCatching { ThumbhashWrapper(bitmap.toThumbhash()) }
                        .onFailure { Log.w(LOG_TAG, "thumbhash generation failed", it) }
                        .getOrNull()
                PreviewHashes(
                    blurhash = blurhash,
                    thumbhash = thumbhash,
                    dim = DimensionTag(bitmap.width, bitmap.height),
                )
            } finally {
                bitmap.recycle()
            }
        } else {
            PreviewHashes.EMPTY
        }

    private fun processRetriever(
        retriever: MediaMetadataRetriever,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes {
        val dim = retriever.prepareDimFromVideo() ?: dimPrecomputed
        val thumb = retriever.getThumbnail()
        if (thumb == null) {
            Log.w(LOG_TAG) { "video frame extraction returned null; no blurhash/thumbhash will be generated" }
        }
        val hashes = processBitmap(thumb)
        val finalDim = if (dim?.hasSize() == true) dim else hashes.dim
        return hashes.copy(dim = finalDim)
    }

    /**
     * Decode an AVIF [ImageDecoder.Source] into a bitmap. Uses [ImageDecoder.ALLOCATOR_SOFTWARE]
     * because hardware bitmaps reject `getPixels()`, which is required for blurhash computation.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAvif(source: ImageDecoder.Source): Bitmap? =
        try {
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "AVIF decode failed: ${e.message}", e)
            null
        }

    private fun decodeAvifBytes(data: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28")
            return null
        }
        if (data.isEmpty()) return null
        return decodeAvif(ImageDecoder.createSource(ByteBuffer.wrap(data)))
    }

    private fun decodeAvifFromUri(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28")
            return null
        }
        return decodeAvif(ImageDecoder.createSource(context.contentResolver, uri))
    }
}
