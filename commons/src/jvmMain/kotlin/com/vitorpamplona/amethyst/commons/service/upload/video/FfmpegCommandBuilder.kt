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

import java.io.File

/** Builds the `ffmpeg` argument lists the desktop compressor runs. Pure, so it is unit-tested without ffmpeg. */
object FfmpegCommandBuilder {
    /**
     * Limits the short side to [shortSide] without ever upscaling and keeps both dimensions even
     * (a yuv420p requirement). Landscape keeps the width derived (`-2`), portrait the height, so a
     * phone video's short side is its width. Null only evens the dimensions.
     *
     * Square pixels first: an anamorphic source (1080x720 stored with 4:9 pixels is a 480x720
     * portrait video) is resized at its display shape, so the limit applies to the side people see
     * and the output plays right in players that ignore the pixel ratio. `sar` is 1 when the source
     * declares none, which leaves an ordinary video as it is.
     */
    fun scaleFilter(shortSide: Int?): String =
        if (shortSide == null) {
            "scale=trunc(iw*sar/2)*2:trunc(ih/2)*2,setsar=1"
        } else {
            "scale=trunc(iw*sar/2)*2:ih,setsar=1,scale='if(gt(iw,ih),-2,trunc(min($shortSide,iw)/2)*2)':'if(gt(iw,ih),trunc(min($shortSide,ih)/2)*2,-2)'"
        }

    fun encode(
        ffmpeg: String,
        input: File,
        output: File,
        encoder: String,
        bitrateBps: Int,
        shortSide: Int?,
    ): List<String> =
        buildList {
            addAll(prefix(ffmpeg, input))
            // First video and (optional) first audio stream only; no subtitles, data tracks or chapters.
            addAll(listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-map_chapters", "-1"))
            addAll(listOf("-c:v", encoder, "-b:v", "$bitrateBps", "-maxrate", "$bitrateBps", "-bufsize", "${2L * bitrateBps}"))
            addAll(listOf("-pix_fmt", "yuv420p", "-vf", scaleFilter(shortSide)))
            addAll(listOf("-c:a", "aac", "-b:a", "128k"))
            addAll(STRIP_METADATA)
            addAll(listOf("-movflags", "+faststart"))
            if (FfmpegEncoders.isHevc(encoder)) addAll(listOf("-tag:v", "hvc1")) // so Apple players accept it
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    /**
     * Copies the video and audio streams into a fresh MP4 with no metadata. Never `-map 0`: a
     * MOV with a timecode data track (what iPhones write) then fails with exit 234.
     */
    fun remux(
        ffmpeg: String,
        input: File,
        output: File,
    ): List<String> =
        buildList {
            addAll(prefix(ffmpeg, input))
            addAll(listOf("-map", "0:v", "-map", "0:a?", "-dn", "-sn", "-c", "copy"))
            addAll(STRIP_METADATA)
            addAll(listOf("-map_chapters", "-1", "-movflags", "+faststart"))
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    /**
     * Copies [durationMs] of the video and audio from [startMs] into a fresh MP4 with no metadata,
     * as [remux] does. Stream copy cuts on packets, so the video starts at the key frame at or
     * before [startMs]; `-avoid_negative_ts make_zero` starts the result's clock at zero.
     */
    fun trim(
        ffmpeg: String,
        input: File,
        startMs: Long,
        durationMs: Long,
        output: File,
    ): List<String> =
        buildList {
            addAll(listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-ss", seconds(startMs), "-i", input.absolutePath))
            addAll(listOf("-t", seconds(durationMs)))
            addAll(listOf("-map", "0:v", "-map", "0:a?", "-dn", "-sn", "-c", "copy"))
            addAll(STRIP_METADATA)
            addAll(listOf("-map_chapters", "-1", "-avoid_negative_ts", "make_zero", "-movflags", "+faststart"))
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    /** One frame near [atMs], upright and no larger than [maxEdge] on its long side, as a JPEG in [output]. */
    fun frame(
        ffmpeg: String,
        input: File,
        atMs: Long,
        maxEdge: Int,
        output: File,
    ): List<String> =
        listOf(
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-nostdin",
            "-y",
            "-ss",
            seconds(atMs),
            "-i",
            input.absolutePath,
            "-frames:v",
            "1",
            "-vf",
            "scale=w='min($maxEdge,iw)':h='min($maxEdge,ih)':force_original_aspect_ratio=decrease",
            "-q:v",
            "4",
            "-f",
            "image2",
            "-c:v",
            "mjpeg",
            output.path,
        )

    /**
     * One frame near each of [times], like [frame], each into its own JPEG in [outputs], from one
     * ffmpeg: [input] is opened once per time with its own fast seek, so the Trim strip starts one
     * process instead of one per frame.
     */
    fun frames(
        ffmpeg: String,
        input: File,
        times: List<Long>,
        maxEdge: Int,
        outputs: List<File>,
    ): List<String> =
        buildList {
            addAll(listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y"))
            times.forEach { atMs -> addAll(listOf("-ss", seconds(atMs.coerceAtLeast(0)), "-i", input.absolutePath)) }
            outputs.forEachIndexed { i, output ->
                addAll(listOf("-map", "$i:v:0", "-frames:v", "1"))
                addAll(listOf("-vf", "scale=w='min($maxEdge,iw)':h='min($maxEdge,ih)':force_original_aspect_ratio=decrease"))
                addAll(listOf("-q:v", "4", "-f", "image2", "-c:v", "mjpeg", output.path))
            }
        }

    /**
     * Copies the sound of [input] into [output] (same container, by its extension) with no tags:
     * no artist, title or location, no chapters, no cover art (`-map 0:a`), and, for MP3, no ID3
     * block at all. `+bitexact` keeps ffmpeg from writing its own name and version into the tags.
     */
    fun stripAudio(
        ffmpeg: String,
        input: File,
        output: File,
    ): List<String> =
        buildList {
            addAll(prefix(ffmpeg, input))
            addAll(listOf("-map", "0:a", "-c", "copy"))
            addAll(STRIP_METADATA)
            addAll(listOf("-map_chapters", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact"))
            when (output.extension.lowercase()) {
                "mp3" -> addAll(listOf("-id3v2_version", "0", "-write_id3v1", "0"))
                "m4a", "mp4" -> addAll(listOf("-movflags", "+faststart"))
            }
            addAll(listOf("-progress", "pipe:1", output.path))
        }

    /** [ms] as ffmpeg's seconds, always with a dot. */
    private fun seconds(ms: Long): String = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"

    private val STRIP_METADATA = listOf("-map_metadata", "-1", "-map_metadata:s", "-1")

    private fun prefix(
        ffmpeg: String,
        input: File,
    ) = listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", input.absolutePath)
}
