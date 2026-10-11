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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** What `ffprobe` tells about a video: the table key, the duration for progress and the HDR flag. */
data class ProbeResult(
    val info: VideoInfo,
    val durationSeconds: Double?,
    val isHdr: Boolean,
)

/** Parses the JSON of the `ffprobe` call in [FfmpegProbe]. */
object FfprobeOutputParser {
    private val mapper = ObjectMapper()
    private val hdrTransfers = setOf("smpte2084", "arib-std-b67")

    /** Null when the JSON is not JSON or has no video stream. */
    fun parse(json: String): ProbeResult? {
        val root = runCatching { mapper.readTree(json) }.getOrNull() ?: return null
        val stream = root.path("streams").firstOrNull() ?: return null
        val width = stream.path("width").asInt(0)
        val height = stream.path("height").asInt(0)
        if (width <= 0 || height <= 0) return null
        val framerate = parseFramerate(stream.path("avg_frame_rate").asText(""))
        val duration =
            root
                .path("format")
                .path("duration")
                .asText("")
                .toDoubleOrNull()
        val isHdr = stream.path("color_transfer").asText("") in hdrTransfers
        return ProbeResult(VideoInfo(VideoResolution(width, height), framerate), duration, isHdr)
    }

    /** `num/den` → num / den; 0 when the denominator is 0 or the text is not a ratio. */
    private fun parseFramerate(text: String): Float {
        val (num, den) = text.split("/").takeIf { it.size == 2 } ?: return text.toFloatOrNull() ?: 0f
        val d = den.toFloatOrNull() ?: return 0f
        if (d == 0f) return 0f
        return (num.toFloatOrNull() ?: return 0f) / d
    }
}

/**
 * Runs `ffprobe` on a file. Open so tests can fake it. Null when [ffprobe] is null, the process
 * fails or overruns, or the output has no video stream; the caller then uses the probe-failure
 * fallback (2 Mbps, no resize).
 */
open class FfmpegProbe(
    private val ffprobe: String?,
) {
    open suspend fun probe(file: File): ProbeResult? {
        val binary = ffprobe ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val process =
                    ProcessBuilder(
                        binary,
                        "-v",
                        "error",
                        "-select_streams",
                        "V:0",
                        "-show_entries",
                        "stream=width,height,avg_frame_rate,color_transfer",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "json",
                        file.absolutePath,
                    ).redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    Log.w(TAG) { "ffprobe overran on ${file.name}" }
                    return@runCatching null
                }
                if (process.exitValue() != 0) {
                    Log.w(TAG) { "ffprobe exited ${process.exitValue()} on ${file.name}" }
                    null
                } else {
                    FfprobeOutputParser.parse(output)
                }
            }.getOrElse {
                Log.w(TAG, "ffprobe failed on ${file.name}", it)
                null
            }
        }
    }

    private companion object {
        const val TAG = "FfmpegProbe"
    }
}
