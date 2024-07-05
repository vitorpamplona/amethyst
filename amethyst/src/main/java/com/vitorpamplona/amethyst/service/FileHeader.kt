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
package com.vitorpamplona.amethyst.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.BitmapParams
import android.os.Build
import android.util.Log
import com.vitorpamplona.amethyst.ui.actions.ImageDownloader
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import io.trbl.blurhash.BlurHash
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.math.roundToInt

class FileHeader(
    val mimeType: String?,
    val hash: String,
    val size: Int,
    val dim: String?,
    val blurHash: String?,
) {
    companion object {
        suspend fun prepare(
            fileUrl: String,
            mimeType: String?,
            dimPrecomputed: String?,
            onReady: (FileHeader) -> Unit,
            onError: (String?) -> Unit,
        ) {
            try {
                val imageData: ByteArray? = ImageDownloader().waitAndGetImage(fileUrl)

                if (imageData != null) {
                    prepare(imageData, mimeType, dimPrecomputed, onReady, onError)
                } else {
                    onError(null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ImageDownload", "Couldn't download image from server: ${e.message}")
                onError(e.message)
            }
        }

        fun prepare(
            data: ByteArray,
            mimeType: String?,
            dimPrecomputed: String?,
            onReady: (FileHeader) -> Unit,
            onError: (String?) -> Unit,
        ) {
            try {
                val hash = CryptoUtils.sha256(data).toHexKey()
                val size = data.size

                val (blurHash, dim) =
                    if (mimeType?.startsWith("image/") == true) {
                        val opt = BitmapFactory.Options()
                        opt.inPreferredConfig = Bitmap.Config.ARGB_8888
                        val mBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opt)

                        val intArray = IntArray(mBitmap.width * mBitmap.height)
                        mBitmap.getPixels(
                            intArray,
                            0,
                            mBitmap.width,
                            0,
                            0,
                            mBitmap.width,
                            mBitmap.height,
                        )

                        val dim = "${mBitmap.width}x${mBitmap.height}"

                        val aspectRatio = (mBitmap.width).toFloat() / (mBitmap.height).toFloat()

                        if (aspectRatio > 1) {
                            Pair(
                                BlurHash.encode(
                                    intArray,
                                    mBitmap.width,
                                    mBitmap.height,
                                    9,
                                    (9 * (1 / aspectRatio)).roundToInt(),
                                ),
                                dim,
                            )
                        } else if (aspectRatio < 1) {
                            Pair(
                                BlurHash.encode(
                                    intArray,
                                    mBitmap.width,
                                    mBitmap.height,
                                    (9 * aspectRatio).roundToInt(),
                                    9,
                                ),
                                dim,
                            )
                        } else {
                            Pair(BlurHash.encode(intArray, mBitmap.width, mBitmap.height, 4, 4), dim)
                        }
                    } else if (mimeType?.startsWith("video/") == true) {
                        val mediaMetadataRetriever = MediaMetadataRetriever()
                        mediaMetadataRetriever.setDataSource(ByteArrayMediaDataSource(data))

                        val newDim = mediaMetadataRetriever.prepareDimFromVideo() ?: dimPrecomputed

                        val blurhash =
                            mediaMetadataRetriever.getThumbnail()?.let { thumbnail ->
                                val aspectRatio = (thumbnail.width).toFloat() / (thumbnail.height).toFloat()

                                val intArray = IntArray(thumbnail.width * thumbnail.height)
                                thumbnail.getPixels(
                                    intArray,
                                    0,
                                    thumbnail.width,
                                    0,
                                    0,
                                    thumbnail.width,
                                    thumbnail.height,
                                )

                                if (aspectRatio > 1) {
                                    BlurHash.encode(
                                        intArray,
                                        thumbnail.width,
                                        thumbnail.height,
                                        9,
                                        (9 * (1 / aspectRatio)).roundToInt(),
                                    )
                                } else if (aspectRatio < 1) {
                                    BlurHash.encode(
                                        intArray,
                                        thumbnail.width,
                                        thumbnail.height,
                                        (9 * aspectRatio).roundToInt(),
                                        9,
                                    )
                                } else {
                                    BlurHash.encode(intArray, thumbnail.width, thumbnail.height, 4, 4)
                                }
                            }

                        if (newDim != "0x0") {
                            Pair(blurhash, newDim)
                        } else {
                            Pair(blurhash, null)
                        }
                    } else {
                        Pair(null, null)
                    }

                onReady(FileHeader(mimeType, hash, size, dim, blurHash))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ImageDownload", "Couldn't convert image in to File Header: ${e.message}")
                onError(e.message)
            }
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

fun MediaMetadataRetriever.prepareDimFromVideo(): String? {
    val width = prepareVideoWidth() ?: return null
    val height = prepareVideoHeight() ?: return null

    return "${width}x$height"
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
