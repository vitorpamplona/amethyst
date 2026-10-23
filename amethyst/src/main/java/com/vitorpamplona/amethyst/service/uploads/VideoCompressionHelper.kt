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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter.formatFileSize
import android.widget.Toast
import com.davotoula.lightcompressor.CompressionListener
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.VideoCompressor
import com.davotoula.lightcompressor.config.AppSpecificStorageConfiguration
import com.davotoula.lightcompressor.config.Configuration
import com.davotoula.lightcompressor.config.VideoResizer
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoCompressionRules
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

// TODO: add Auto setting. Focus on small fast streams. 4->1080p, 1080p->720p, 720p and below stay the same resolution. Use existing matrix to determine bitrate.
object VideoCompressionHelper {
    private const val LOG_TAG = "VideoCompressionHelper"

    suspend fun compressVideo(
        uri: Uri,
        contentType: String?,
        applicationContext: Context,
        mediaQuality: CompressorQuality,
        useH265: Boolean,
        timeoutMs: Long = 60_000L, // configurable, default 60s
    ): MediaCompressorResult {
        // An anamorphic source is resized in square pixels (see SquarePixelsResizer), so the rules
        // judge its display size and the upload plays at the right shape everywhere.
        val pixelAspectRatio = videoPixelAspectRatio(applicationContext, uri)
        val videoInfo = getVideoInfo(uri, applicationContext, pixelAspectRatio)

        val (videoBitrateInBps, resizer) =
            videoInfo?.let { info ->
                val rule =
                    VideoCompressionRules.ruleFor(mediaQuality, info.resolution.getStandard())
                        ?: return MediaCompressorResult(uri, contentType, null)

                val bitrateBps = rule.getBitrateBps(info.framerate, useH265)
                Log.d(LOG_TAG) { "Bitrate: ${bitrateBps}bps for ${info.resolution.getStandard()} quality=$mediaQuality framerate=${info.framerate}fps useH265=$useH265." }

                Log.d(LOG_TAG) {
                    "Resizer: ${info.resolution.width}x${info.resolution.height} -> " +
                        "shortSide=${rule.shortSide} (${rule.description})"
                }
                val limit = VideoResizer.limitShortSide(rule.shortSide.toDouble())
                val resizer = pixelAspectRatio?.let { SquarePixelsResizer(it, limit) } ?: limit

                Pair(bitrateBps, resizer)
            } ?: run {
                Log.w(LOG_TAG, "Video bitrate fallback: 2Mbps (videoInfo unavailable)")
                Log.d(LOG_TAG, "Resizer: null (original resolution preserved)")
                Pair(VideoCompressionRules.PROBE_FAILED_BITRATE_BPS, pixelAspectRatio?.let { SquarePixelsResizer(it, null) })
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
                                override fun onStart(index: Int) {
                                    // No action needed on compression start
                                }

                                override fun onProgress(
                                    index: Int,
                                    percent: Float,
                                ) {
                                    // Progress tracking not needed for this use case
                                }

                                override fun onSuccess(
                                    index: Int,
                                    size: Long,
                                    path: String?,
                                ) {
                                    if (path == null) {
                                        applicationContext.notifyUser(
                                            "Video compression succeeded, but path was null",
                                            LogLevel.WARN,
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
                                    if (originalSize in 1..size) {
                                        if (!File(path).delete()) {
                                            Log.w("VideoCompressionHelper") { "Failed to delete compressed file: $path" }
                                        }
                                        applicationContext.notifyUser(
                                            "Compressed file larger than original. Using original.",
                                            LogLevel.WARN,
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

                                    Log.d(LOG_TAG) {
                                        "Compression success: Original [$originalSize] -> " +
                                            "Compressed [$size] ($reductionPercent% reduction)"
                                    }

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
                                        LogLevel.ERROR,
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
            Log.w(LOG_TAG) { "Failed to get file size: ${e.message}" }
            0L
        }

    private fun Context.notifyUser(
        message: String,
        logLevel: LogLevel = LogLevel.DEBUG,
        duration: Int = Toast.LENGTH_LONG,
    ) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, duration).show()
        }
        when (logLevel) {
            LogLevel.ERROR -> Log.e(LOG_TAG, message)
            LogLevel.WARN -> Log.w(LOG_TAG, message)
            LogLevel.INFO -> Log.i(LOG_TAG, message)
            LogLevel.DEBUG -> Log.d(LOG_TAG, message)
        }
    }

    private fun getVideoInfo(
        uri: Uri,
        context: Context,
        pixelAspectRatio: Float?,
    ): VideoInfo? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val width = retriever.prepareVideoWidth()?.let { displayWidth(it, pixelAspectRatio) }
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
            Log.w(LOG_TAG) { "Failed to get video resolution: ${e.message}" }
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.w(LOG_TAG) { "Failed to release MediaMetadataRetriever: ${e.message}" }
            }
        }
    }
}
