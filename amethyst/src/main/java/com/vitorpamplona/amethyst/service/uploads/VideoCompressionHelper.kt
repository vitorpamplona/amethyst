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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter.formatFileSize
import android.util.Log
import android.widget.Toast
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.VideoResizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class VideoInfo(
    val resolution: VideoResolution,
    val framerate: Float,
)

data class VideoResolution(
    val width: Int,
    val height: Int,
) {
    val pixels: Int get() = width * height

    fun getStandard(): VideoStandard =
        when {
            pixels >= 3840 * 2160 -> VideoStandard.UHD_4K
            pixels >= 2560 * 1440 -> VideoStandard.QHD_1440P
            pixels >= 1920 * 1080 -> VideoStandard.FHD_1080P
            pixels >= 1280 * 720 -> VideoStandard.HD_720P
            pixels >= 854 * 480 -> VideoStandard.SD_480P
            pixels >= 640 * 360 -> VideoStandard.NHD_360P
            pixels >= 426 * 240 -> VideoStandard.QVGA_240P
            else -> VideoStandard.UNKNOWN
        }
}

enum class VideoStandard(
    val label: String,
) {
    UHD_4K("4K"),
    QHD_1440P("1440p"),
    FHD_1080P("1080p"),
    HD_720P("720p"),
    SD_480P("480p"),
    NHD_360P("360p"),
    QVGA_240P("240p"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = label
}

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

class VideoCompressionHelper {
    companion object {
        private val compressionRules =
            mapOf(
                CompressorQuality.LOW to
                    mapOf(
                        VideoStandard.UHD_4K to CompressionRule(1280, 720, 2f, "4K→720p, 2Mbps"),
                        VideoStandard.QHD_1440P to CompressionRule(1280, 720, 2f, "1440p→720p, 2Mbps"),
                        VideoStandard.FHD_1080P to CompressionRule(854, 480, 1f, "1080p→480p, 1Mbps"),
                        VideoStandard.HD_720P to CompressionRule(640, 360, 1f, "720p→360p, 1Mbps"),
                        VideoStandard.SD_480P to CompressionRule(426, 240, 1f, "480p→240p, 1Mbps"),
                        VideoStandard.NHD_360P to CompressionRule(426, 240, 0.3f, "360p→240p, 0.3Mbps"),
                        VideoStandard.QVGA_240P to CompressionRule(320, 180, 0.2f, "240p→180p, 0.2Mbps"),
                        VideoStandard.UNKNOWN to CompressionRule(854, 480, 1f, "Low quality fallback, 1Mbps"),
                    ),
                CompressorQuality.MEDIUM to
                    mapOf(
                        VideoStandard.UHD_4K to CompressionRule(1920, 1080, 6f, "4K→1080p, 6Mbps"),
                        VideoStandard.QHD_1440P to CompressionRule(1920, 1080, 6f, "1440p→1080p, 6Mbps"),
                        VideoStandard.FHD_1080P to CompressionRule(1280, 720, 3f, "1080p→720p, 3Mbps"),
                        VideoStandard.HD_720P to CompressionRule(854, 480, 2f, "720p→480p, 2Mbps"),
                        VideoStandard.SD_480P to CompressionRule(640, 360, 1f, "480p→360p, 1Mbps"),
                        VideoStandard.NHD_360P to CompressionRule(426, 240, 0.5f, "360p→240p, 0.5Mbps"),
                        VideoStandard.QVGA_240P to CompressionRule(320, 180, 0.3f, "240p→180p, 0.3Mbps"),
                        VideoStandard.UNKNOWN to CompressionRule(1280, 720, 2f, "Medium quality fallback, 2Mbps"),
                    ),
                CompressorQuality.HIGH to
                    mapOf(
                        VideoStandard.UHD_4K to CompressionRule(3840, 2160, 16f, "4K→4K, 16Mbps"),
                        VideoStandard.QHD_1440P to CompressionRule(1920, 1080, 8f, "1440p→1080p, 8Mbps"),
                        VideoStandard.FHD_1080P to CompressionRule(1920, 1080, 6f, "1080p→1080p, 6Mbps"),
                        VideoStandard.HD_720P to CompressionRule(1280, 720, 3f, "720p→720p, 3Mbps"),
                        VideoStandard.SD_480P to CompressionRule(854, 480, 2f, "480p→480p, 2Mbps"),
                        VideoStandard.NHD_360P to CompressionRule(640, 360, 1f, "360p→360p, 1Mbps"),
                        VideoStandard.QVGA_240P to CompressionRule(426, 240, 0.5f, "240p→240p, 0.5Mbps"),
                        VideoStandard.UNKNOWN to CompressionRule(1920, 1080, 3f, "High quality fallback, 3Mbps"),
                    ),
            )
    }

    suspend fun compressVideo(
        uri: Uri,
        contentType: String?,
        applicationContext: Context,
        mediaQuality: CompressorQuality,
        timeoutMs: Long = 60_000L, // configurable, default 60s
    ): MediaCompressorResult {
        val videoInfo = getVideoInfo(uri, applicationContext)

        val videoBitrateInMbps =
            if (videoInfo != null) {
                val baseBitrate =
                    compressionRules
                        .getValue(mediaQuality)
                        .getValue(videoInfo.resolution.getStandard())
                        .getBitrateMbpsInt()

                // Apply 1.5x multiplier for 60fps+
                val adjusted =
                    if (videoInfo.framerate >= 60f) {
                        (baseBitrate * 1.5f).roundToInt()
                    } else {
                        baseBitrate
                    }

                Log.d(
                    "VideoCompressionHelper",
                    "Bitrate: ${adjusted}Mbps for ${videoInfo.resolution.getStandard()} " +
                        "quality=$mediaQuality framerate=${videoInfo.framerate}fps",
                )
                adjusted
            } else {
                Log.w("VideoCompressionHelper", "Video bitrate fallback: 2Mbps (videoInfo unavailable)")
                2
            }

        val resizer =
            if (videoInfo != null) {
                val rules =
                    compressionRules
                        .getValue(mediaQuality)
                        .getValue(videoInfo.resolution.getStandard())
                Log.d(
                    "VideoCompressionHelper",
                    "Resizer: ${videoInfo.resolution.width}x${videoInfo.resolution.height} -> " +
                        "${rules.width}x${rules.height} (${rules.description})",
                )
                VideoResizer.limitSize(rules.width.toDouble(), rules.height.toDouble())
            } else {
                Log.d("VideoCompressionHelper", "Resizer: null (original resolution preserved)")
                null
            }

        // Get original file size safely
        val originalSize = applicationContext.getFileSize(uri)

        val result =
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    VideoCompressor.start(
                        context = applicationContext,
                        uris = listOf(uri),
                        isStreamable = true,
                        storageConfiguration = AppSpecificStorageConfiguration(),
                        configureWith =
                            Configuration(
                                videoBitrateInMbps = videoBitrateInMbps,
                                resizer = resizer,
                                videoNames = listOf(UUID.randomUUID().toString()),
                                isMinBitrateCheckEnabled = false,
                            ),
                        listener =
                            object : CompressionListener {
                                override fun onStart(index: Int) {}

                                override fun onProgress(
                                    index: Int,
                                    percent: Float,
                                ) {}

                                override fun onSuccess(
                                    index: Int,
                                    size: Long,
                                    path: String?,
                                ) {
                                    if (path == null) {
                                        applicationContext.notifyUser(
                                            "Video compression succeeded, but path was null",
                                            "VideoCompressionHelper",
                                            Log.WARN,
                                        )
                                        if (continuation.isActive) continuation.resume(null)
                                        return
                                    }

                                    val reductionPercent =
                                        if (originalSize > 0) {
                                            ((originalSize - size) * 100.0 / originalSize).toInt()
                                        } else {
                                            0
                                        }

                                    // Sanity check: compression not smaller than original
                                    if (originalSize > 0 && size >= originalSize) {
                                        applicationContext.notifyUser(
                                            "Compressed file larger than original. Using original.",
                                            "VideoCompressionHelper",
                                            Log.WARN,
                                        )
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                MediaCompressorResult(uri, contentType, null),
                                            )
                                        }
                                        return
                                    }

                                    // Show compression result
                                    if (originalSize > 0 && size > 0) {
                                        val sizeLabel = formatFileSize(applicationContext, size)
                                        val percentLabel =
                                            if (reductionPercent >= 0) "-$reductionPercent%" else "+${-reductionPercent}%"
                                        applicationContext.notifyUser(
                                            "Video compressed: $sizeLabel ($percentLabel)",
                                            "VideoCompressionHelper",
                                        )
                                    }

                                    Log.d(
                                        "VideoCompressionHelper",
                                        "Compression success: Original [$originalSize] -> " +
                                            "Compressed [$size] ($reductionPercent% reduction)",
                                    )

                                    if (continuation.isActive) {
                                        continuation.resume(
                                            MediaCompressorResult(Uri.fromFile(File(path)), contentType, size),
                                        )
                                    }
                                }

                                override fun onFailure(
                                    index: Int,
                                    failureMessage: String,
                                ) {
                                    applicationContext.notifyUser(
                                        "Video compression failed: $failureMessage",
                                        "VideoCompressionHelper",
                                        Log.ERROR,
                                    )
                                    if (continuation.isActive) continuation.resume(null)
                                }

                                override fun onCancelled(index: Int) {
                                    Log.w("VideoCompressionHelper", "Video compression cancelled")
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            },
                    )
                }
            }

        return result ?: MediaCompressorResult(uri, contentType, null)
    }

    private fun Context.getFileSize(uri: Uri): Long =
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w("VideoCompressionHelper", "Failed to get file size: ${e.message}")
            0L
        }

    private fun Context.notifyUser(
        message: String,
        logTag: String,
        logLevel: Int = Log.DEBUG,
        duration: Int = Toast.LENGTH_LONG,
    ) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, duration).show()
        }
        when (logLevel) {
            Log.ERROR -> Log.e(logTag, message)
            Log.WARN -> Log.w(logTag, message)
            else -> Log.d(logTag, message)
        }
    }

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
            Log.w("VideoCompressionHelper", "Failed to get video resolution: ${e.message}")
            null
        }
}
