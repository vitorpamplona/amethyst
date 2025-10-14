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
import com.abedelazizshe.lightcompressorlibrary.VideoCodec
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.VideoResizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

// TODO: add Auto setting. Focus on small fast streams. 4->1080p, 1080p->720p, 720p and below stay the same resolution. Use existing matrix to determine bitrate.
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

private const val MBPS_TO_BPS_MULTIPLIER = 1_000_000

data class CompressionRule(
    val width: Int,
    val height: Int,
    val bitrateMbps: Float,
    val description: String,
) {
    fun getBitrateBps(
        framerate: Float,
        useH265: Boolean,
    ): Int {
        // Apply 1.3x multiplier for 60fps+ videos, 0.7x multiplier for H265
        val framerateMultiplier = if (framerate >= 60f) 1.3f else 1.0f
        val codecMultiplier = if (useH265) 0.7f else 1.0f
        val finalMultiplier = framerateMultiplier * codecMultiplier

        Log.d("VideoCompressionHelper", "framerate: $framerate, useH265: $useH265, Bitrate multiplier: $finalMultiplier")

        return (bitrateMbps * finalMultiplier * MBPS_TO_BPS_MULTIPLIER).toInt()
    }
}

object VideoCompressionHelper {
    private const val LOG_TAG = "VideoCompressionHelper"

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

    suspend fun compressVideo(
        uri: Uri,
        contentType: String?,
        applicationContext: Context,
        mediaQuality: CompressorQuality,
        useH265: Boolean,
        timeoutMs: Long = 60_000L, // configurable, default 60s
    ): MediaCompressorResult {
        val videoInfo = getVideoInfo(uri, applicationContext)

        val (videoBitrateInBps, resizer) =
            videoInfo?.let { info ->
                val rule =
                    compressionRules
                        .getValue(mediaQuality)
                        .getValue(info.resolution.getStandard())

                val bitrateBps = rule.getBitrateBps(info.framerate, useH265)
                Log.d(LOG_TAG, "Bitrate: ${bitrateBps}bps for ${info.resolution.getStandard()} quality=$mediaQuality framerate=${info.framerate}fps useH265=$useH265.")

                Log.d(
                    LOG_TAG,
                    "Resizer: ${info.resolution.width}x${info.resolution.height} -> " +
                        "${rule.width}x${rule.height} (${rule.description})",
                )
                val resizer = VideoResizer.limitSize(rule.width.toDouble(), rule.height.toDouble())

                Pair(bitrateBps, resizer)
            } ?: run {
                Log.w(LOG_TAG, "Video bitrate fallback: 2Mbps (videoInfo unavailable)")
                Log.d(LOG_TAG, "Resizer: null (original resolution preserved)")
                Pair(2 * MBPS_TO_BPS_MULTIPLIER, null)
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
                                videoBitrateInBps = videoBitrateInBps.toLong(),
                                resizer = resizer,
                                videoNames = listOf(UUID.randomUUID().toString()),
                                isMinBitrateCheckEnabled = false,
                                videoCodec = if (useH265) VideoCodec.H265 else VideoCodec.H264,
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
                                        )
                                    }

                                    Log.d(
                                        LOG_TAG,
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
                                        Log.ERROR,
                                    )
                                    if (continuation.isActive) continuation.resume(null)
                                }

                                override fun onCancelled(index: Int) {
                                    Log.w(LOG_TAG, "Video compression cancelled")
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
            Log.w(LOG_TAG, "Failed to get file size: ${e.message}")
            0L
        }

    private fun Context.notifyUser(
        message: String,
        logLevel: Int = Log.DEBUG,
        duration: Int = Toast.LENGTH_LONG,
    ) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, duration).show()
        }
        when (logLevel) {
            Log.ERROR -> Log.e(LOG_TAG, message)
            Log.WARN -> Log.w(LOG_TAG, message)
            else -> Log.d(LOG_TAG, message)
        }
    }

    private fun getVideoInfo(
        uri: Uri,
        context: Context,
    ): VideoInfo? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val width = retriever.prepareVideoWidth()
            val height = retriever.prepareVideoHeight()
            val rotation = retriever.prepareRotation() ?: 0

            // Get framerate
            val framerateString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val framerate = framerateString?.toFloatOrNull() ?: 30.0f

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
            Log.w(LOG_TAG, "Failed to get video resolution: ${e.message}")
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Failed to release MediaMetadataRetriever: ${e.message}")
            }
        }
    }
}
