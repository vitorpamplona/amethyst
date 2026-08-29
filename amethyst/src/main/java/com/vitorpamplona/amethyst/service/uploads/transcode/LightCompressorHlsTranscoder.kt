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
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsUploadHelper
import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsConfig
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsError
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsListener
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsRenditionSummary
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsSegment
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsTranscoder
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsUploadResult
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsUploaded
import com.vitorpamplona.amethyst.commons.uploads.hls.Rendition
import com.vitorpamplona.amethyst.commons.uploads.hls.Resolution
import com.davotoula.lightcompressor.Resolution as LibResolution
import com.davotoula.lightcompressor.hls.HlsConfig as LibHlsConfig
import com.davotoula.lightcompressor.hls.HlsError as LibHlsError
import com.davotoula.lightcompressor.hls.HlsLadder as LibHlsLadder
import com.davotoula.lightcompressor.hls.HlsListener as LibHlsListener
import com.davotoula.lightcompressor.hls.HlsRenditionSummary as LibHlsRenditionSummary
import com.davotoula.lightcompressor.hls.HlsSegment as LibHlsSegment
import com.davotoula.lightcompressor.hls.HlsUploaded as LibHlsUploaded
import com.davotoula.lightcompressor.hls.Rendition as LibRendition

/**
 * The Android side of [HlsTranscoder]: LightCompressor's `MediaCodec` ladder.
 *
 * Nothing but translation between two vocabularies, in both directions — the
 * config goes down, the listener callbacks come back up. Keeping it that way is
 * deliberate: the ladder, the playlists and the NIP-71 event stayed shared, so
 * a bug in any of them is one bug, not one per platform.
 */
class LightCompressorHlsTranscoder(
    private val context: Context,
) : HlsTranscoder {
    override suspend fun <T> run(
        sourceUri: String,
        config: HlsConfig,
        listener: HlsListener,
        uploadFile: suspend (path: String, contentType: String) -> HlsUploaded<T>,
    ): HlsUploadResult<T> {
        val result =
            HlsUploadHelper.run(
                context = context,
                uri = sourceUri.toUri(),
                config = config.toLib(),
                listener = listener.toLib(),
                uploader = { file, contentType ->
                    val uploaded = uploadFile(file.absolutePath, contentType)
                    LibHlsUploaded(uploaded.url, uploaded.metadata)
                },
            )

        return HlsUploadResult(
            masterPlaylist = result.masterPlaylist,
            renditions = result.renditions.map { it.toShared() },
            uploads = result.uploads.mapValues { (_, value) -> HlsUploaded(value.url, value.metadata) },
        )
    }
}

private fun HlsConfig.toLib() =
    LibHlsConfig(
        ladder = LibHlsLadder(ladder.renditions.map { it.toLib() }),
        codec = if (codec == VideoCodecChoice.H265) VideoCodec.H265 else VideoCodec.H264,
        segmentDurationSeconds = segmentDurationSeconds,
        disableAudio = disableAudio,
        singleFilePerRendition = singleFilePerRendition,
    )

private fun Rendition.toLib() = LibRendition(resolution.toLib(), bitrateKbps)

private fun Resolution.toLib() =
    when (this) {
        Resolution.UHD_4K -> LibResolution.UHD_4K
        Resolution.FHD_1080 -> LibResolution.FHD_1080
        Resolution.HD_720 -> LibResolution.HD_720
        Resolution.SD_540 -> LibResolution.SD_540
        Resolution.SD_360 -> LibResolution.SD_360
    }

/**
 * Matched by short side rather than by name, so a rung the library adds that
 * this app does not know about still maps to the nearest one it does instead of
 * throwing mid-publish.
 */
private fun LibResolution.toShared(): Resolution = Resolution.entries.minByOrNull { kotlin.math.abs(it.shortSide - shortSide) } ?: Resolution.SD_360

private fun LibRendition.toShared() = Rendition(resolution.toShared(), bitrateKbps)

private fun LibHlsSegment.toShared() =
    HlsSegment(
        path = file.absolutePath,
        index = index,
        durationSeconds = durationSeconds,
        isInitSegment = isInitSegment,
        isCombinedRendition = isCombinedRendition,
    )

private fun LibHlsRenditionSummary.toShared() =
    HlsRenditionSummary(
        rendition = rendition.toShared(),
        mediaPlaylist = mediaPlaylist,
        playlistFilename = playlistFilename,
        width = width,
        height = height,
        codecString = codecString,
        combinedFilename = combinedFilename,
    )

private fun LibHlsError.toShared() =
    HlsError(
        message = message,
        failedRenditions = failedRenditions.map { it.toShared() },
        completedRenditions = completedRenditions.map { it.toShared() },
    )

private fun HlsListener.toLib() =
    object : LibHlsListener {
        override fun onStart(renditionCount: Int) = this@toLib.onStart(renditionCount)

        override fun onRenditionStart(rendition: LibRendition) = this@toLib.onRenditionStart(rendition.toShared())

        override fun onSegmentReady(
            rendition: LibRendition,
            segment: LibHlsSegment,
        ) = this@toLib.onSegmentReady(rendition.toShared(), segment.toShared())

        override fun onRenditionComplete(
            rendition: LibRendition,
            summary: LibHlsRenditionSummary,
        ) = this@toLib.onRenditionComplete(rendition.toShared(), summary.toShared())

        override fun onComplete(masterPlaylist: String) = this@toLib.onComplete(masterPlaylist)

        override fun onFailure(error: LibHlsError) = this@toLib.onFailure(error.toShared())

        override fun onProgress(
            rendition: LibRendition,
            percent: Float,
        ) = this@toLib.onProgress(rendition.toShared(), percent)

        override fun onCancelled() = this@toLib.onCancelled()
    }
