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
package com.vitorpamplona.amethyst.service.uploads.transcode

import android.content.Context
import androidx.core.net.toUri
import com.davotoula.lightcompressor.CompressionListener
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.VideoCompressor
import com.davotoula.lightcompressor.config.AppSpecificStorageConfiguration
import com.davotoula.lightcompressor.config.Configuration
import com.davotoula.lightcompressor.config.VideoResizer
import com.davotoula.lightcompressor.utils.CompressorUtils
import com.davotoula.lightcompressor.video.GifToMp4Converter
import com.vitorpamplona.amethyst.commons.uploads.ConvertedGif
import com.vitorpamplona.amethyst.commons.uploads.GifToVideoConverter
import com.vitorpamplona.amethyst.commons.uploads.TranscodeConfig
import com.vitorpamplona.amethyst.commons.uploads.TranscodeListener
import com.vitorpamplona.amethyst.commons.uploads.TranscodeSource
import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.commons.uploads.VideoTranscoder

/**
 * The Android side of [VideoTranscoder]: LightCompressor, which encodes through
 * `MediaCodec` and so exists only here.
 *
 * A straight forward, deliberately. All the judgement — target bitrate, short
 * side, whether the result was worth keeping — stayed with the caller when the
 * seam was introduced, so this file is only the translation between two shapes.
 */
class LightCompressorTranscoder(
    private val context: Context,
) : VideoTranscoder {
    override fun start(
        source: TranscodeSource,
        config: TranscodeConfig,
        listener: TranscodeListener,
    ) {
        VideoCompressor.start(
            context = context,
            uris = listOf(source.uri.toUri()),
            isStreamable = source.streamable,
            storageConfiguration = AppSpecificStorageConfiguration(),
            configureWith =
                Configuration(
                    videoBitrateInBps = config.videoBitrateInBps,
                    resizer = config.shortSideLimit?.let { VideoResizer.limitShortSide(it.toDouble()) },
                    videoNames = listOf(config.outputName),
                    isMinBitrateCheckEnabled = config.minBitrateCheck,
                    videoCodec = if (config.codec == VideoCodecChoice.H265) VideoCodec.H265 else VideoCodec.H264,
                ),
            listener =
                object : CompressionListener {
                    override fun onStart(index: Int) = listener.onStart()

                    override fun onProgress(
                        index: Int,
                        percent: Float,
                    ) = listener.onProgress(percent)

                    override fun onSuccess(
                        index: Int,
                        size: Long,
                        path: String?,
                    ) = listener.onSuccess(size, path)

                    override fun onFailure(
                        index: Int,
                        failureMessage: String,
                    ) = listener.onFailure(failureMessage)

                    override fun onCancelled(index: Int) = listener.onCancelled()
                },
        )
    }

    override fun cancel() = VideoCompressor.cancel()

    override val supportsH265: Boolean get() = CompressorUtils.isHevcEncodingSupported()
}

/** The Android side of [GifToVideoConverter]; also `MediaCodec`-backed. */
class LightCompressorGifConverter(
    private val context: Context,
) : GifToVideoConverter {
    override suspend fun convert(uri: String): ConvertedGif? =
        GifToMp4Converter.convert(uri.toUri(), context)?.let {
            ConvertedGif(path = it.file.absolutePath, mimeType = it.mimeType, size = it.size)
        }
}
