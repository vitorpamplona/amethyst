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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegLocator
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegProbe
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegVideoCompressor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Studio's video trim and frames on the desktop, with the real ffmpeg. */
class DesktopVideoTrimTest {
    private val dir = Files.createTempDirectory("video-trim").toFile()
    private val compressor = FfmpegVideoCompressor()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun assumeFfmpeg() {
        assumeTrue("ffmpeg not installed", runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        assumeTrue("ffmpeg not found", compressor.available)
    }

    /** Six seconds of picture and sound with a key frame every second. */
    private fun clip(): File {
        val out = File(dir, "clip.mp4")
        ffmpeg(
            "-f",
            "lavfi",
            "-i",
            "testsrc=size=640x360:rate=30",
            "-f",
            "lavfi",
            "-i",
            "sine=frequency=440:sample_rate=44100",
            "-t",
            "6",
            "-g",
            "30",
            "-pix_fmt",
            "yuv420p",
            "-c:v",
            "libx264",
            "-c:a",
            "aac",
            "-shortest",
            out.absolutePath,
        )
        return out
    }

    @Test
    fun trimKeepsThePartAndBothTracks() {
        assumeFfmpeg()
        val source = clip()
        val trimmed = assertNotNull(runBlocking { compressor.trim(source, 2_000, 4_500) })
        try {
            val probe = assertNotNull(runBlocking { FfmpegProbe(FfmpegLocator.default.ffprobe).probe(trimmed) })
            val seconds = assertNotNull(probe.durationSeconds)
            assertTrue(seconds in 2.3..2.8, "trimmed to about 2.5 s, got $seconds")
            assertTrue(streams(trimmed).containsAll(listOf("video", "audio")), "both tracks kept: ${streams(trimmed)}")
            assertEquals(6_000L, runBlocking { compressor.durationMs(source) }?.let { (it + 50) / 100 * 100 })
        } finally {
            trimmed.delete()
        }
    }

    @Test
    fun framesAreSmallUprightJpegs() {
        assumeFfmpeg()
        val plain = clip()
        val rotated = File(dir, "rotated.mp4")
        ffmpeg("-display_rotation", "90", "-i", plain.absolutePath, "-c", "copy", rotated.absolutePath)

        val bytes = assertNotNull(runBlocking { compressor.frame(rotated, 3_000, 160) })
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        // Stored 640x360, shown turned a quarter: a portrait frame, its long side capped.
        assertEquals(160, maxOf(image.width, image.height))
        assertTrue(image.height > image.width, "upright: ${image.width}x${image.height}")
    }

    @Test
    fun oneRunGivesTheSameFramesAsOneRunEach() {
        assumeFfmpeg()
        val plain = clip()
        val rotated = File(dir, "rotated-strip.mp4")
        ffmpeg("-display_rotation", "90", "-i", plain.absolutePath, "-c", "copy", rotated.absolutePath)

        val times = listOf(500L, 2_000L, 4_500L)
        val strip = runBlocking { compressor.frames(rotated, times, 160) }
        assertEquals(times.size, strip.size)
        times.zip(strip).forEach { (atMs, bytes) ->
            val together = assertNotNull(ImageIO.read(ByteArrayInputStream(assertNotNull(bytes, "frame at $atMs"))))
            val alone = assertNotNull(ImageIO.read(ByteArrayInputStream(assertNotNull(runBlocking { compressor.frame(rotated, atMs, 160) }))))
            assertEquals(alone.width to alone.height, together.width to together.height, "frame at $atMs")
            assertTrue(together.height > together.width, "upright at $atMs")
            // The same moment: the test clip changes over time, so its pixels tell the moments apart.
            val diff = (0 until alone.width * alone.height).count { i -> alone.getRGB(i % alone.width, i / alone.width) != together.getRGB(i % alone.width, i / alone.width) }
            assertTrue(diff < alone.width * alone.height / 20, "frame at $atMs matches: $diff pixels differ")
        }
        // Past the end there is no frame, and the others still come back.
        val withEnd = runBlocking { compressor.frames(rotated, listOf(1_000L, 60_000L), 160) }
        assertNotNull(withEnd[0])
    }

    private fun streams(file: File): List<String> {
        val process =
            ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", file.absolutePath)
                .redirectErrorStream(true)
                .start()
        process.waitFor(30, TimeUnit.SECONDS)
        return process.inputStream
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun ffmpeg(vararg args: String) {
        val process = ProcessBuilder(listOf("ffmpeg", "-loglevel", "error", "-y") + args).redirectErrorStream(true).start()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
    }
}
