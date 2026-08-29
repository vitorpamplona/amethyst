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
package com.vitorpamplona.amethyst.commons.uploads

/**
 * The seam between "shrink this video before uploading it" and whatever
 * actually re-encodes it.
 *
 * Android drives LightCompressor, which is an AAR around `MediaCodec` and
 * cannot run anywhere else; the desktop drives FFmpeg. Neither belongs in the
 * shared decision-making — which bitrate, which resolution, whether the result
 * was actually smaller — and that logic is worth keeping in one place, because
 * it is what decides what users' uploads look like.
 *
 * The callback shape mirrors the one the Android caller already had, so the
 * behaviour around it (the size sanity check, the user notification, the
 * cancellation path) is unchanged by the move.
 */
interface VideoTranscoder {
    /**
     * Starts one transcode. [listener] is called on an unspecified thread and
     * exactly one of its terminal callbacks fires.
     */
    fun start(
        source: TranscodeSource,
        config: TranscodeConfig,
        listener: TranscodeListener,
    )

    /** Cancels whatever is in flight. Safe when nothing is. */
    fun cancel()

    /**
     * Whether this platform can encode H.265/HEVC. Asked before offering the
     * choice, because a device or an ffmpeg build without it would fail the
     * encode rather than fall back — and the user would have picked a codec
     * that never had a chance.
     */
    val supportsH265: Boolean get() = false

    companion object {
        /**
         * The platform's transcoder, installed at startup. Null means this
         * build has none, and the caller uploads the original — which is the
         * same path it already takes when the video's properties cannot be
         * read.
         */
        @Volatile
        var installed: VideoTranscoder? = null

        /** False with no transcoder, which is also the safe default. */
        fun supportsH265(): Boolean = installed?.supportsH265 == true
    }
}

/**
 * Where the video is. A platform URI string, because that is what both sides
 * already hold: a `content://` on Android and a `file://` on the desktop.
 */
data class TranscodeSource(
    val uri: String,
    val streamable: Boolean = true,
)

data class TranscodeConfig(
    val videoBitrateInBps: Long,
    /** Null keeps the source resolution. */
    val shortSideLimit: Int?,
    val codec: VideoCodecChoice,
    val outputName: String,
    /**
     * LightCompressor's "don't bother below this bitrate" guard. Off here,
     * because the caller has already decided the target bitrate.
     */
    val minBitrateCheck: Boolean = false,
)

enum class VideoCodecChoice { H264, H265 }

interface TranscodeListener {
    fun onStart()

    fun onProgress(percent: Float)

    /** [path] is the finished file; [size] its bytes. */
    fun onSuccess(
        size: Long,
        path: String?,
    )

    fun onFailure(message: String)

    fun onCancelled()
}

/**
 * Turning an animated GIF into an MP4 before upload — a separate seam because
 * it is a separate pipeline on both platforms, and because a build can have one
 * without the other.
 */
interface GifToVideoConverter {
    /** Null when the conversion is not possible; the caller uploads the GIF. */
    suspend fun convert(uri: String): ConvertedGif?

    companion object {
        @Volatile
        var installed: GifToVideoConverter? = null
    }
}

data class ConvertedGif(
    val path: String,
    val mimeType: String,
    val size: Long,
)
