/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.BitmapParams
import android.os.Build
import android.util.Log
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.service.Blurhash
import com.vitorpamplona.quartz.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import kotlinx.coroutines.CancellationException
import java.io.IOException

class FileHeader(
    val mimeType: String?,
    val hash: String,
    val size: Int,
    val dim: Dimension?,
    val blurHash: Blurhash?,
) {
    class UnableToDownload(
        val fileUrl: String,
    ) : Exception()

    companion object {
        suspend fun prepare(
            fileUrl: String,
            mimeType: String?,
            dimPrecomputed: Dimension?,
            forceProxy: Boolean,
        ): Result<FileHeader> =
            try {
                val imageData: ImageDownloader.Blob? = ImageDownloader().waitAndGetImage(fileUrl, forceProxy)

                if (imageData != null) {
                    prepare(imageData.bytes, mimeType ?: imageData.contentType, dimPrecomputed)
                } else {
                    Result.failure(UnableToDownload(fileUrl))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ImageDownload", "Couldn't download image from server: ${e.message}")
                Result.failure(e)
            }

        fun prepare(
            data: ByteArray,
            mimeType: String?,
            dimPrecomputed: Dimension?,
        ): Result<FileHeader> =
            try {
                val hash = CryptoUtils.sha256(data).toHexKey()
                val size = data.size

                val (blurHash, dim) =
                    if (mimeType?.startsWith("image/") == true) {
                        val opt = BitmapFactory.Options()
                        opt.inPreferredConfig = Bitmap.Config.ARGB_8888
                        val mBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opt)
                        Pair(Blurhash(mBitmap.toBlurhash()), Dimension(mBitmap.width, mBitmap.height))
                    } else if (mimeType?.startsWith("video/") == true) {
                        val mediaMetadataRetriever = MediaMetadataRetriever()
                        mediaMetadataRetriever.setDataSource(ByteArrayMediaDataSource(data))

                        val newDim = mediaMetadataRetriever.prepareDimFromVideo() ?: dimPrecomputed
                        val blurhash = mediaMetadataRetriever.getThumbnail()?.toBlurhash()?.let { Blurhash(it) }

                        if (newDim?.hasSize() == true) {
                            Pair(blurhash, newDim)
                        } else {
                            Pair(blurhash, null)
                        }
                    } else {
                        Pair(null, null)
                    }

                Result.success(FileHeader(mimeType, hash, size, dim, blurHash))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ImageDownload", "Couldn't convert image in to File Header: ${e.message}")
                Result.failure(e)
            }
    }
}

fun MediaMetadataRetriever.getThumbnail(): Bitmap? {
    val raw: ByteArray? = getEmbeddedPicture()
    if (raw != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(raw))
        }
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val params = BitmapParams()
        params.preferredConfig = Bitmap.Config.ARGB_8888

        // Fall back to middle of video
        // Note: METADATA_KEY_DURATION unit is in ms, not us.
        val thumbnailTimeUs: Long =
            (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0) * 1000 / 2

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getFrameAtTime(thumbnailTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, params)
        } else {
            null
        }
    } else {
        null
    }
}

fun MediaMetadataRetriever.prepareDimFromVideo(): Dimension? {
    val width = prepareVideoWidth() ?: return null
    val height = prepareVideoHeight() ?: return null

    return if (width > 0 && height > 0) {
        Dimension(width, height)
    } else {
        null
    }
}

fun MediaMetadataRetriever.prepareVideoWidth(): Int? {
    val widthData = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    return if (widthData.isNullOrEmpty()) {
        null
    } else {
        widthData.toInt()
    }
}

fun MediaMetadataRetriever.prepareVideoHeight(): Int? {
    val heightData = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    return if (heightData.isNullOrEmpty()) {
        null
    } else {
        heightData.toInt()
    }
}

class ByteArrayMediaDataSource(
    var imageData: ByteArray,
) : MediaDataSource() {
    override fun getSize(): Long = imageData.size.toLong()

    @Throws(IOException::class)
    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        if (position >= imageData.size) {
            return -1
        }
        val newSize =
            if (position + size > imageData.size) {
                size - ((position.toInt() + size) - imageData.size)
            } else {
                size
            }

        imageData.copyInto(buffer, offset, position.toInt(), position.toInt() + newSize)

        return newSize
    }

    @Throws(IOException::class)
    override fun close() {}
}
