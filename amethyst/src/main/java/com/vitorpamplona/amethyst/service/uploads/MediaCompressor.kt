/**
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
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.VideoResizer
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.util.MediaCompressorFileUtils
import com.vitorpamplona.quartz.utils.Log
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.default
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MediaCompressorResult(
    val uri: Uri,
    val contentType: String?,
    val size: Long?,
)

data class CompressionRule(
    val width: Int,
    val height: Int,
    val bitrateMbps: Float,
    val description: String,
) {
    fun getBitrateMbpsInt(): Int {
        // Library doesn't support float so we have to convert it to int and use 1 as minimum
        return if (bitrateMbps < 1) {
            1
        } else {
            bitrateMbps.roundToInt()
        }
    }
}

private data class VideoInfo(
    val resolution: VideoResolution,
    val framerate: Float,
)

data class VideoResolution(
    val width: Int,
    val height: Int,
) {
    val pixels: Int get() = width * height
    val isPortrait: Boolean get() = height > width

    fun getStandardName(): String =
        when {
            pixels >= 3840 * 2160 -> "4K"
            pixels >= 2560 * 1440 -> "1440p"
            pixels >= 1920 * 1080 -> "1080p"
            pixels >= 1280 * 720 -> "720p"
            pixels >= 854 * 480 -> "480p"
            pixels >= 640 * 360 -> "360p"
            pixels >= 426 * 240 -> "240p"
            else -> "${width}x$height"
        }
}

private val compressionRules =
    mapOf(
        CompressorQuality.LOW to
            mapOf(
                "4K" to CompressionRule(1280, 720, 2f, "4K→720p, 2Mbps"),
                "1440p" to CompressionRule(1280, 720, 2f, "1440p→720p, 2Mbps"),
                "1080p" to CompressionRule(854, 480, 1f, "1080p→480p, 1Mbps"),
                "720p" to CompressionRule(640, 360, 1f, "720p→360p, 1Mbps"),
                "480p" to CompressionRule(426, 240, 1f, "480p→240p, 1Mbps"),
                "360p" to CompressionRule(426, 240, 0.3f, "360p→240p, 0.3Mbps"),
                "240p" to CompressionRule(320, 180, 0.2f, "240p→180p, 0.2Mbps"),
                "default" to CompressionRule(854, 480, 1f, "Low quality fallback, 1Mbps"),
            ),
        CompressorQuality.MEDIUM to
            mapOf(
                "4K" to CompressionRule(1920, 1080, 6f, "4K→1080p, 6Mbps"),
                "1440p" to CompressionRule(1920, 1080, 6f, "1440p→1080p, 6Mbps"),
                "1080p" to CompressionRule(1280, 720, 3f, "1080p→720p, 3Mbps"),
                "720p" to CompressionRule(854, 480, 2f, "720p→480p, 2Mbps"),
                "480p" to CompressionRule(640, 360, 1f, "480p→360p, 1Mbps"),
                "360p" to CompressionRule(426, 240, 0.5f, "360p→240p, 0.5Mbps"),
                "240p" to CompressionRule(320, 180, 0.3f, "240p→180p, 0.3Mbps"),
                "default" to CompressionRule(1280, 720, 2f, "Medium quality fallback, 2Mbps"),
            ),
        CompressorQuality.HIGH to
            mapOf(
                "4K" to CompressionRule(3840, 2160, 16f, "4K→4K, 16Mbps"),
                "1440p" to CompressionRule(1920, 1080, 8f, "1440p→1080p, 8Mbps"),
                "1080p" to CompressionRule(1920, 1080, 6f, "1080p→1080p, 6Mbps"),
                "720p" to CompressionRule(1280, 720, 3f, "720p→720p, 3Mbps"),
                "480p" to CompressionRule(854, 480, 2f, "480p→480p, 2Mbps"),
                "360p" to CompressionRule(640, 360, 1f, "360p→360p, 1Mbps"),
                "240p" to CompressionRule(426, 240, 0.5f, "240p→240p, 0.5Mbps"),
                "default" to CompressionRule(1920, 1080, 3f, "High quality fallback, 3Mbps"),
            ),
    )

private fun getVideoInfo(
    uri: Uri,
    context: Context,
): VideoInfo? =
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val width = retriever.prepareVideoWidth()
        val height = retriever.prepareVideoHeight()
        val rotation = retriever.prepareRotation() ?: 0

        // Get framerate
        val framerateString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        val framerate = framerateString?.toFloatOrNull() ?: 30.0f

        retriever.release()

        if (width != null && height != null && width > 0 && height > 0) {
            // Account for rotation
            val resolution =
                if (rotation == 90 || rotation == 270) {
                    VideoResolution(height, width)
                } else {
                    VideoResolution(width, height)
                }
            VideoInfo(resolution, framerate)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("MediaCompressor", "Failed to get video resolution: ${e.message}")
        null
    }

/** The plan
 * xxx 1. Check input resolution and input fps
 * xxx 2. Create configuration matrix: for each quality level, set bitrate based on input resolution
 * 3. Create Configuration with no quality setting, a bitrate setting, resizer, streamable = true, isMinBitrateCheckEnabled=false
 * 4. Don't upload converted file if compression results in larger file (return MediaCompressorResult(uri, contentType, null))
 *
 *
 * Don't use Configuration.quality which only determines bitrate. Instead let's create aggressive bitrates based on input and selected quality
 * H265 is not supported, use bitrates that suit h264
 * Detect fps in source, multiple bitrate by 1.5 if 60fps or higher
 * Bitrate floor has to be 1Mbps for now
 * Future extension: Modify library to use bps for bitrate instead of Mbps which allows only 1Mbps as lowest and increments of 1 (Int)
 *
 */
class MediaCompressor {
    // ALL ERRORS ARE IGNORED. The original file is returned.
    suspend fun compress(
        uri: Uri,
        contentType: String?,
        mediaQuality: CompressorQuality,
        applicationContext: Context,
    ): MediaCompressorResult {
        // Skip compression if user selected uncompressed
        if (mediaQuality == CompressorQuality.UNCOMPRESSED) {
            Log.d("MediaCompressor", "UNCOMPRESSED quality selected, skipping compression.")
            return MediaCompressorResult(uri, contentType, null)
        }

        checkNotInMainThread()

        // branch into compression based on content type
        return when {
            contentType?.startsWith("video", ignoreCase = true) == true -> compressVideo(uri, contentType, applicationContext, mediaQuality)
            contentType?.startsWith("image", ignoreCase = true) == true &&
                !contentType.contains("gif") &&
                !contentType.contains("svg") ->
                compressImage(uri, contentType, applicationContext, mediaQuality)
            else -> MediaCompressorResult(uri, contentType, null)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun compressVideo(
        uri: Uri,
        contentType: String?,
        applicationContext: Context,
        mediaQuality: CompressorQuality,
    ): MediaCompressorResult {
        val videoInfo = getVideoInfo(uri, applicationContext)

        val videoBitrateInMbps =
            if (videoInfo != null) {
                val bitrate = compressionRules.getValue(mediaQuality).getValue(videoInfo.resolution.getStandardName()).getBitrateMbpsInt()
                Log.d("MediaCompressor", "Video bitrate calculated: ${bitrate}Mbps for ${videoInfo.resolution.getStandardName()} quality=$mediaQuality")
                bitrate
            } else {
                // Default/fallback logic when videoInfo is null
                Log.d("MediaCompressor", "Video bitrate fallback: 2Mbps (videoInfo unavailable)")
                2
            }

        val resizer =
            if (videoInfo != null) {
                val rules = compressionRules.getValue(mediaQuality).getValue(videoInfo.resolution.getStandardName())
                Log.d("MediaCompressor", "Video resizer: ${videoInfo.resolution.width}x${videoInfo.resolution.height} -> ${rules.width}x${rules.height} (${rules.description})")
                VideoResizer.limitSize(rules.width.toDouble(), rules.height.toDouble())
            } else {
                // null VideoResizer should result in unchanged resolution
                Log.d("MediaCompressor", "Video resizer: null (original resolution preserved)")
                null
            }

        val result =
            withTimeoutOrNull(30000) {
                suspendCancellableCoroutine { continuation ->
                    VideoCompressor.start(
                        // => This is required
                        context = applicationContext,
                        // => Source can be provided as content uris
                        uris = listOf(uri),
                        isStreamable = true,
                        // THIS STORAGE
                        // sharedStorageConfiguration = SharedStorageConfiguration(
                        //    saveAt = SaveLocation.movies, // => default is movies
                        //    videoName = "compressed_video" // => required name
                        // ),
                        // OR AND NOT BOTH
                        storageConfiguration = AppSpecificStorageConfiguration(),
                        configureWith =
                            Configuration(
                                videoBitrateInMbps = videoBitrateInMbps,
                                resizer = resizer,
                                // => required name
                                videoNames = listOf(Uuid.random().toString()),
                                isMinBitrateCheckEnabled = false,
                            ),
                        listener =
                            object : CompressionListener {
                                override fun onProgress(
                                    index: Int,
                                    percent: Float,
                                ) {}

                                override fun onStart(index: Int) {}

                                override fun onSuccess(
                                    index: Int,
                                    size: Long,
                                    path: String?,
                                ) {
                                    if (path != null) {
                                        Log.d("MediaCompressor", "Video compression success. Compressed size [$size]")
                                        continuation.resume(MediaCompressorResult(Uri.fromFile(File(path)), contentType, size))
                                    } else {
                                        Log.d("MediaCompressor", "Video compression successful, but returned null path")
                                        continuation.resume(null)
                                    }
                                }

                                override fun onFailure(
                                    index: Int,
                                    failureMessage: String,
                                ) {
                                    Log.d("MediaCompressor", "Video compression failed: $failureMessage")
                                    // keeps going with original video
                                    continuation.resume(null)
                                }

                                override fun onCancelled(index: Int) {
                                    continuation.resume(null)
                                }
                            },
                    )
                }
            }

        return result ?: MediaCompressorResult(uri, contentType, null)
    }

    private suspend fun compressImage(
        uri: Uri,
        contentType: String?,
        context: Context,
        mediaQuality: CompressorQuality,
    ): MediaCompressorResult {
        val imageQuality =
            when (mediaQuality) {
                CompressorQuality.VERY_LOW -> 40
                CompressorQuality.LOW -> 50
                CompressorQuality.MEDIUM -> 60
                CompressorQuality.HIGH -> 80
                CompressorQuality.VERY_HIGH -> 90
                else -> 60
            }

        return try {
            Log.d("MediaCompressor", "Using image compression $mediaQuality")
            val tempFile = MediaCompressorFileUtils.from(uri, context)
            val compressedImageFile =
                Compressor.compress(context, tempFile) {
                    default(width = 640, format = Bitmap.CompressFormat.JPEG, quality = imageQuality)
                }
            Log.d("MediaCompressor", "Image compression success. Original size [${tempFile.length()}], new size [${compressedImageFile.length()}]")
            MediaCompressorResult(compressedImageFile.toUri(), MimeTypes.IMAGE_JPEG, compressedImageFile.length())
        } catch (e: Exception) {
            Log.d("MediaCompressor", "Image compression failed: ${e.message}")
            if (e is CancellationException) throw e
            e.printStackTrace()
            MediaCompressorResult(uri, contentType, null)
        }
    }

    companion object {
        fun intToCompressorQuality(mediaQualityFloat: Int): CompressorQuality =
            when (mediaQualityFloat) {
                0 -> CompressorQuality.LOW
                1 -> CompressorQuality.MEDIUM
                2 -> CompressorQuality.HIGH
                3 -> CompressorQuality.UNCOMPRESSED
                else -> CompressorQuality.MEDIUM
            }

        fun compressorQualityToInt(compressorQuality: CompressorQuality): Int =
            when (compressorQuality) {
                CompressorQuality.LOW -> 0
                CompressorQuality.MEDIUM -> 1
                CompressorQuality.HIGH -> 2
                CompressorQuality.UNCOMPRESSED -> 3
                else -> 1
            }
    }
}

enum class CompressorQuality {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    UNCOMPRESSED,
}
