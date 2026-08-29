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

import com.vitorpamplona.amethyst.commons.uploads.ConvertedGif
import com.vitorpamplona.amethyst.commons.uploads.GifToVideoConverter
import com.vitorpamplona.amethyst.commons.uploads.TranscodeConfig
import com.vitorpamplona.amethyst.commons.uploads.TranscodeListener
import com.vitorpamplona.amethyst.commons.uploads.TranscodeSource
import com.vitorpamplona.amethyst.commons.uploads.VideoCodecChoice
import com.vitorpamplona.amethyst.commons.uploads.VideoTranscoder
import com.vitorpamplona.amethyst.desktop.service.media.FfmpegBinary
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * The desktop side of [VideoTranscoder]: the ffmpeg the app already ships for
 * video thumbnails, asked to re-encode instead of to grab a frame.
 *
 * Everything about *what* to encode — bitrate, short side, codec — was decided
 * by the caller before it got here, so this is only the command line and the
 * progress parsing. ffmpeg writes its progress to stderr as `time=HH:MM:SS.xx`,
 * which against the source duration is a real percentage rather than a made-up
 * one; with no duration the listener simply gets no progress, which is what it
 * already tolerates.
 */
class FfmpegVideoTranscoder(
    private val outputDir: File,
) : VideoTranscoder {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicReference<Process?>(null)

    override fun start(
        source: TranscodeSource,
        config: TranscodeConfig,
        listener: TranscodeListener,
    ) {
        scope.launch { run(source, config, listener) }
    }

    override fun cancel() {
        running.getAndSet(null)?.destroy()
    }

    private fun run(
        source: TranscodeSource,
        config: TranscodeConfig,
        listener: TranscodeListener,
    ) {
        val ffmpeg = FfmpegBinary.path
        if (ffmpeg == null) {
            listener.onFailure("no ffmpeg available to re-encode with")
            return
        }

        val input = source.uri.toLocalFile()
        if (input == null || !input.isFile) {
            listener.onFailure("cannot read ${source.uri}")
            return
        }

        outputDir.mkdirs()
        val output = File(outputDir, "${config.outputName}.mp4")
        listener.onStart()

        val process =
            runCatching {
                ProcessBuilder(command(ffmpeg, input, output, config, source.streamable))
                    .redirectErrorStream(true)
                    .start()
            }.getOrElse {
                listener.onFailure("could not start ffmpeg: $it")
                return
            }

        running.set(process)
        val duration = probeDurationSeconds(ffmpeg, input)

        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                progressOf(line, duration)?.let(listener::onProgress)
            }
            val finished = process.waitFor(TRANSCODE_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            when {
                !finished -> {
                    process.destroyForcibly()
                    listener.onFailure("ffmpeg timed out after $TRANSCODE_TIMEOUT_MINUTES minutes")
                }
                running.get() == null -> listener.onCancelled()
                process.exitValue() != 0 -> listener.onFailure("ffmpeg exited ${process.exitValue()}")
                !output.isFile || output.length() == 0L -> listener.onFailure("ffmpeg produced no output")
                else -> listener.onSuccess(output.length(), output.absolutePath)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            listener.onCancelled()
        } finally {
            running.compareAndSet(process, null)
        }
    }

    /**
     * `-vf scale` keeps the aspect ratio and only shrinks: `min(iw,limit)` on
     * the short side means a video already smaller than the target is left
     * alone rather than upscaled, which is what the Android resizer does too.
     * `-2` lets ffmpeg pick the other side, rounded to an even number, which
     * H.264 requires.
     */
    internal fun command(
        ffmpeg: String,
        input: File,
        output: File,
        config: TranscodeConfig,
        streamable: Boolean,
    ): List<String> =
        buildList {
            add(ffmpeg)
            add("-y")
            add("-i")
            add(input.absolutePath)

            config.shortSideLimit?.let { limit ->
                add("-vf")
                // Portrait and landscape need the limit on different axes.
                add("scale='if(gt(iw,ih),-2,min(iw,$limit))':'if(gt(iw,ih),min(ih,$limit),-2)'")
            }

            add("-c:v")
            add(if (config.codec == VideoCodecChoice.H265) "libx265" else "libx264")
            add("-b:v")
            add("${config.videoBitrateInBps}")
            add("-c:a")
            add("aac")

            if (streamable) {
                // The same thing LightCompressor's isStreamable does: move the
                // index to the front so playback can start before the download
                // finishes.
                add("-movflags")
                add("+faststart")
            }

            add("-progress")
            add("pipe:2")
            add(output.absolutePath)
        }

    private fun probeDurationSeconds(
        ffmpeg: String,
        input: File,
    ): Double? =
        runCatching {
            val probe =
                ProcessBuilder(ffmpeg, "-i", input.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            val text = probe.inputStream.bufferedReader().readText()
            if (!probe.waitFor(10, TimeUnit.SECONDS)) probe.destroyForcibly()
            DURATION.find(text)?.let { match ->
                val (h, m, s) = match.destructured
                h.toDouble() * 3600 + m.toDouble() * 60 + s.toDouble()
            }
        }.getOrNull()

    internal fun progressOf(
        line: String,
        durationSeconds: Double?,
    ): Float? {
        if (durationSeconds == null || durationSeconds <= 0) return null
        val match = TIME.find(line) ?: return null
        val (h, m, s) = match.destructured
        val elapsed = h.toDouble() * 3600 + m.toDouble() * 60 + s.toDouble()
        return ((elapsed / durationSeconds) * 100).coerceIn(0.0, 100.0).roundToInt().toFloat()
    }

    private fun String.toLocalFile(): File? =
        runCatching {
            if (startsWith("file:")) File(URI(this)) else File(this)
        }.getOrNull()

    companion object {
        private const val TRANSCODE_TIMEOUT_MINUTES = 30L
        private val DURATION = Regex("""Duration:\s*(\d+):(\d+):(\d+\.?\d*)""")
        private val TIME = Regex("""time=(\d+):(\d+):(\d+\.?\d*)""")
    }
}

/**
 * The desktop side of [GifToVideoConverter]. An animated GIF is a video ffmpeg
 * already knows how to read, so this is one command; the pixel-format and
 * even-dimension flags are what make the result playable everywhere rather than
 * only in a browser.
 */
class FfmpegGifConverter(
    private val outputDir: File,
) : GifToVideoConverter {
    override suspend fun convert(uri: String): ConvertedGif? =
        withContext(Dispatchers.IO) {
            val ffmpeg = FfmpegBinary.path ?: return@withContext null
            val input = runCatching { if (uri.startsWith("file:")) File(URI(uri)) else File(uri) }.getOrNull()
            if (input == null || !input.isFile) return@withContext null

            outputDir.mkdirs()
            val output = File(outputDir, "${input.nameWithoutExtension}-${System.currentTimeMillis()}.mp4")

            val exit =
                runCatching {
                    val process =
                        ProcessBuilder(
                            ffmpeg,
                            "-y",
                            "-i",
                            input.absolutePath,
                            "-movflags",
                            "+faststart",
                            // yuv420p and even dimensions are what every player
                            // outside a browser expects; a GIF is usually neither.
                            "-pix_fmt",
                            "yuv420p",
                            "-vf",
                            "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                            output.absolutePath,
                        ).redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start()
                    if (!process.waitFor(5, TimeUnit.MINUTES)) {
                        process.destroyForcibly()
                        -1
                    } else {
                        process.exitValue()
                    }
                }.getOrDefault(-1)

            if (exit != 0 || !output.isFile || output.length() == 0L) {
                Log.w("FfmpegGifConverter") { "GIF to MP4 failed (exit $exit); the GIF will be uploaded as-is" }
                return@withContext null
            }
            ConvertedGif(path = output.absolutePath, mimeType = "video/mp4", size = output.length())
        }
}
