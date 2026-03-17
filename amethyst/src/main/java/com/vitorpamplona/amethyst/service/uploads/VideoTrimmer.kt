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
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

object VideoTrimmer {
    private const val LOG_TAG = "VideoTrimmer"

    suspend fun trim(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
    ): Uri? =
        suspendCancellableCoroutine { continuation ->
            val outputFile = File(context.cacheDir, "trimmed_${UUID.randomUUID()}.mp4")
            val outputPath = outputFile.absolutePath

            val clippingConfig =
                MediaItem.ClippingConfiguration
                    .Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()

            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(clippingConfig)
                    .build()

            val transformer =
                Transformer
                    .Builder(context)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                Log.d(LOG_TAG, "Video trim completed: $outputPath")
                                if (continuation.isActive) {
                                    continuation.resume(outputFile.toUri())
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                Log.e(LOG_TAG, "Video trim failed", exportException)
                                outputFile.delete()
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        },
                    ).build()

            transformer.start(mediaItem, outputPath)

            continuation.invokeOnCancellation {
                transformer.cancel()
                outputFile.delete()
            }
        }
}
