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
package com.vitorpamplona.amethyst.commons.uploads.hls

import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice

/**
 * The vocabulary of an HLS publish, shared because none of it is
 * platform-specific: a rendition ladder is a list of sizes and bitrates, and a
 * rendition summary is what a NIP-71 `imeta` tag is built from. Only the
 * transcoding is platform work, and that is [HlsTranscoder].
 */
enum class Resolution(
    val shortSide: Int,
    val label: String,
) {
    UHD_4K(2160, "4K"),
    FHD_1080(1080, "1080p"),
    HD_720(720, "720p"),
    SD_540(540, "540p"),
    SD_360(360, "360p"),
}

data class Rendition(
    val resolution: Resolution,
    val bitrateKbps: Int,
)

/**
 * The set of renditions to produce, largest first.
 *
 * Publishing a rendition larger than the source would upscale it — more bytes
 * for a worse picture — so [forSource] trims the ladder to what the source can
 * actually fill.
 */
data class HlsLadder(
    val renditions: List<Rendition>,
) {
    fun drop(vararg labels: String): HlsLadder = HlsLadder(renditions.filterNot { it.resolution.label in labels })

    fun add(rendition: Rendition): HlsLadder = HlsLadder((renditions + rendition).sortedByDescending { it.resolution.shortSide })

    /** Keeps only the rungs at or below the source's short side, and never none. */
    fun forSource(shortSide: Int): HlsLadder {
        val kept = renditions.filter { it.resolution.shortSide <= shortSide }
        return HlsLadder(kept.ifEmpty { listOfNotNull(renditions.minByOrNull { it.resolution.shortSide }) })
    }

    companion object {
        fun default() =
            HlsLadder(
                listOf(
                    Rendition(Resolution.FHD_1080, 5000),
                    Rendition(Resolution.HD_720, 2800),
                    Rendition(Resolution.SD_540, 1400),
                    Rendition(Resolution.SD_360, 800),
                ),
            )

        fun defaultForSource(shortSide: Int) = default().forSource(shortSide)
    }
}

data class HlsConfig(
    val ladder: HlsLadder,
    val codec: VideoCodecChoice = VideoCodecChoice.H264,
    val segmentDurationSeconds: Int = 4,
    val disableAudio: Boolean = false,
    /**
     * One byte-range file per rendition instead of many small segments. Fewer
     * uploads and fewer blobs to keep alive, which matters when every file is
     * a separately addressed blob.
     */
    val singleFilePerRendition: Boolean = true,
)

/** One produced media file: an init segment, a media segment, or a whole rendition. */
data class HlsSegment(
    val path: String,
    val index: Int,
    val durationSeconds: Double,
    val isInitSegment: Boolean = false,
    val isCombinedRendition: Boolean = false,
)

/** What one rung produced. The width/height are the *actual* encoded size. */
data class HlsRenditionSummary(
    val rendition: Rendition,
    val mediaPlaylist: String,
    val playlistFilename: String,
    val width: Int,
    val height: Int,
    val codecString: String,
    val combinedFilename: String?,
)

data class HlsError(
    val message: String,
    val failedRenditions: List<Rendition> = emptyList(),
    val completedRenditions: List<Rendition> = emptyList(),
)

/** A file that made it to a server: where it is, and whatever the upload returned. */
data class HlsUploaded<T>(
    val url: String,
    val metadata: T,
)

data class HlsUploadResult<T>(
    val masterPlaylist: String,
    val renditions: List<HlsRenditionSummary>,
    val uploads: Map<String, HlsUploaded<T>>,
)

interface HlsListener {
    fun onStart(renditionCount: Int) {}

    fun onRenditionStart(rendition: Rendition) {}

    fun onSegmentReady(
        rendition: Rendition,
        segment: HlsSegment,
    ) {}

    fun onRenditionComplete(
        rendition: Rendition,
        summary: HlsRenditionSummary,
    ) {}

    fun onComplete(masterPlaylist: String) {}

    fun onFailure(error: HlsError) {}

    fun onProgress(
        rendition: Rendition,
        percent: Float,
    ) {}

    fun onCancelled() {}
}

/** The MIME types an HLS upload puts on its blobs. */
object HlsContentTypes {
    const val HLS_PLAYLIST = "application/vnd.apple.mpegurl"
    const val FMP4_SEGMENT = "video/mp4"
}
