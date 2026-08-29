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
package com.vitorpamplona.amethyst.desktop.service.uploads

import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsConfig
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsContentTypes
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsError
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsListener
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsRenditionSummary
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsSegment
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsTranscoder
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsUploadResult
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsUploaded
import com.vitorpamplona.amethyst.commons.uploads.hls.Rendition
import com.vitorpamplona.amethyst.desktop.service.media.FfmpegBinary
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The desktop side of [HlsTranscoder]: one ffmpeg run per rung of the ladder.
 *
 * Per rung rather than one run with `-var_stream_map`, deliberately. The
 * interleaving is the feature — a rendition's files are uploaded as soon as
 * that rung finishes, so the progress bar moves and the disk never holds the
 * whole ladder — and a single multi-output run only reports "done" at the end.
 * Encoding is the slow part either way; the extra demuxes are noise beside it.
 *
 * fMP4 segments (`-hls_segment_type fmp4`) match what the Android side
 * produces, so the NIP-71 event and the players downstream see the same thing
 * on both platforms.
 */
class FfmpegHlsTranscoder(
    private val workDir: File,
) : HlsTranscoder {
    override suspend fun <T> run(
        sourceUri: String,
        config: HlsConfig,
        listener: HlsListener,
        uploadFile: suspend (path: String, contentType: String) -> HlsUploaded<T>,
    ): HlsUploadResult<T>? {
        val ffmpeg = FfmpegBinary.path ?: return null
        val input = sourceUri.toLocalFile()?.takeIf { it.isFile } ?: return null

        val session = File(workDir, "hls-${System.currentTimeMillis()}").also { it.mkdirs() }
        val uploads = LinkedHashMap<String, HlsUploaded<T>>()
        val summaries = mutableListOf<HlsRenditionSummary>()
        val completed = mutableListOf<Rendition>()

        listener.onStart(config.ladder.renditions.size)

        try {
            for (rendition in config.ladder.renditions) {
                listener.onRenditionStart(rendition)

                val dir = File(session, rendition.resolution.label).also { it.mkdirs() }
                val playlistFile = File(dir, "${rendition.resolution.label}.m3u8")

                val exit = encode(ffmpeg, input, dir, playlistFile, rendition, config)
                if (exit != 0) {
                    listener.onFailure(
                        HlsError(
                            "ffmpeg exited $exit for ${rendition.resolution.label}",
                            failedRenditions = listOf(rendition),
                            completedRenditions = completed.toList(),
                        ),
                    )
                    return null
                }

                // Upload this rung's files before starting the next encode, so a
                // long ladder does not accumulate on disk and progress moves.
                val produced = dir.listFiles()?.sortedBy { it.name }.orEmpty()
                produced.forEachIndexed { index, file ->
                    val contentType =
                        if (file.extension == "m3u8") HlsContentTypes.HLS_PLAYLIST else HlsContentTypes.FMP4_SEGMENT
                    uploads[file.name] = uploadFile(file.absolutePath, contentType)

                    if (file.extension != "m3u8") {
                        listener.onSegmentReady(
                            rendition,
                            HlsSegment(
                                path = file.absolutePath,
                                index = index,
                                durationSeconds = config.segmentDurationSeconds.toDouble(),
                                isInitSegment = file.name.startsWith("init"),
                            ),
                        )
                    }
                }

                val summary = summarise(rendition, playlistFile, config)
                summaries += summary
                completed += rendition
                listener.onRenditionComplete(rendition, summary)
                listener.onProgress(rendition, 100f)
            }

            val master = masterPlaylist(summaries)
            listener.onComplete(master)
            return HlsUploadResult(masterPlaylist = master, renditions = summaries, uploads = uploads)
        } finally {
            // The segments are on a server now; the local copies are scratch.
            session.deleteRecursively()
        }
    }

    private suspend fun encode(
        ffmpeg: String,
        input: File,
        dir: File,
        playlist: File,
        rendition: Rendition,
        config: HlsConfig,
    ): Int =
        withContext(Dispatchers.IO) {
            val command = command(ffmpeg, input, dir, playlist, rendition, config)
            runCatching {
                val process =
                    ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                if (!process.waitFor(ENCODE_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly()
                    Log.w("FfmpegHlsTranscoder") { "timed out on ${rendition.resolution.label}" }
                    -1
                } else {
                    process.exitValue()
                }
            }.getOrElse {
                Log.w("FfmpegHlsTranscoder") { "could not run ffmpeg: $it" }
                -1
            }
        }

    internal fun command(
        ffmpeg: String,
        input: File,
        dir: File,
        playlist: File,
        rendition: Rendition,
        config: HlsConfig,
    ): List<String> =
        buildList {
            add(ffmpeg)
            add("-y")
            add("-i")
            add(input.absolutePath)

            add("-vf")
            // Same rule as the plain transcoder: limit the short side, never
            // upscale, and let the free axis land on an even number.
            add(
                "scale='if(gt(iw,ih),-2,min(iw,${rendition.resolution.shortSide}))'" +
                    ":'if(gt(iw,ih),min(ih,${rendition.resolution.shortSide}),-2)'",
            )

            add("-c:v")
            add(if (config.codec == VideoCodecChoice.H265) "libx265" else "libx264")
            add("-b:v")
            add("${rendition.bitrateKbps}k")

            if (config.disableAudio) {
                add("-an")
            } else {
                add("-c:a")
                add("aac")
            }

            add("-f")
            add("hls")
            add("-hls_time")
            add("${config.segmentDurationSeconds}")
            // A VOD playlist keeps every segment listed; the default rolling
            // window would drop the start of the video from the playlist.
            add("-hls_playlist_type")
            add("vod")
            add("-hls_segment_type")
            add("fmp4")
            add("-hls_fmp4_init_filename")
            add("init.mp4")

            if (config.singleFilePerRendition) {
                // One file per rendition with byte ranges: fewer blobs to
                // upload and keep alive, which is what a blob server charges by.
                add("-hls_flags")
                add("single_file")
            }

            add("-hls_segment_filename")
            add(File(dir, "seg%03d.m4s").absolutePath)
            add(playlist.absolutePath)
        }

    /**
     * The encoded size comes from the playlist's own stream info where ffmpeg
     * wrote it, and from the requested short side otherwise. Guessing the
     * aspect ratio would put a wrong `dim` in the NIP-71 event, which players
     * use to lay out before the first frame arrives.
     */
    private fun summarise(
        rendition: Rendition,
        playlist: File,
        config: HlsConfig,
    ): HlsRenditionSummary {
        val text = runCatching { playlist.readText() }.getOrDefault("")
        val resolution = RESOLUTION.find(text)
        return HlsRenditionSummary(
            rendition = rendition,
            mediaPlaylist = text,
            playlistFilename = playlist.name,
            width = resolution?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            height = resolution?.groupValues?.get(2)?.toIntOrNull() ?: rendition.resolution.shortSide,
            codecString = if (config.codec == VideoCodecChoice.H265) "hvc1.1.6.L93.B0" else "avc1.640028",
            combinedFilename = if (config.singleFilePerRendition) "${rendition.resolution.label}.m4s" else null,
        )
    }

    /** The master playlist that points at each rung's media playlist. */
    internal fun masterPlaylist(summaries: List<HlsRenditionSummary>): String =
        buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            summaries.forEach { summary ->
                append("#EXT-X-STREAM-INF:BANDWIDTH=")
                append(summary.rendition.bitrateKbps * 1000)
                if (summary.width > 0 && summary.height > 0) {
                    append(",RESOLUTION=${summary.width}x${summary.height}")
                }
                appendLine(",CODECS=\"${summary.codecString}\"")
                appendLine(summary.playlistFilename)
            }
        }

    private fun String.toLocalFile(): File? = runCatching { if (startsWith("file:")) File(URI(this)) else File(this) }.getOrNull()

    companion object {
        private const val ENCODE_TIMEOUT_MINUTES = 60L
        private val RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")
    }
}
