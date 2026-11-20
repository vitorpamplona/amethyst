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

import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegVideoCompressor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The size the desktop reports for what it uploads, which every `imeta` `dim` comes from. Real
 * files: ImageIO's formats, WebP (Skia), AVIF and videos (ffprobe, rotation included).
 */
class DesktopUploadMetadataTest {
    private val dir = Files.createTempDirectory("upload-dims").toFile()
    private val noVideo: suspend (File) -> Pair<Int, Int>? = { null }

    private fun size(file: File) = runBlocking { readDesktopUploadMetadata(file, noVideo) }.let { it.width to it.height }

    @Test
    fun pngThroughImageIO() {
        val png = File(dir, "a.png")
        ImageIO.write(BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB), "png", png)
        assertEquals(320 to 200, size(png))
    }

    @Test
    fun webpThroughSkia() {
        // ImageIO has no WebP reader; this read null before.
        val webp = File("../commonsUI/src/commonMain/composeResources/drawable-xxxhdpi/profile_banner.webp")
        val (w, h) = size(webp)
        assertEquals(true, w != null && h != null && w > 0 && h > 0, "got ${w}x$h")
    }

    @Test
    fun videoTakesTheProbedDisplaySize() {
        val mp4 = File(dir, "clip.mp4").apply { writeBytes(ByteArray(16)) }
        val read = runBlocking { readDesktopUploadMetadata(mp4) { 360 to 640 } }
        assertEquals(360 to 640, read.width to read.height)
    }

    /** End to end with the real ffprobe: a landscape-stored clip flagged to turn 90° plays portrait. */
    @Test
    fun rotatedPhoneClipWithRealFfprobe() {
        assumeTrue("ffmpeg not installed", runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        val plain = File(dir, "plain.mp4")
        val rotated = File(dir, "rotated.mp4")
        ffmpeg("-f", "lavfi", "-i", "testsrc=size=640x360:rate=10", "-t", "1", "-pix_fmt", "yuv420p", plain.absolutePath)
        ffmpeg("-display_rotation", "90", "-i", plain.absolutePath, "-c", "copy", rotated.absolutePath)

        val compressor = FfmpegVideoCompressor()
        assumeTrue("ffprobe not found", compressor.available)
        val probe: suspend (File) -> Pair<Int, Int>? = { compressor.displaySize(it) }
        val plainRead = runBlocking { readDesktopUploadMetadata(plain, probe) }
        val rotatedRead = runBlocking { readDesktopUploadMetadata(rotated, probe) }
        assertEquals(640 to 360, plainRead.width to plainRead.height)
        assertEquals(360 to 640, rotatedRead.width to rotatedRead.height)

        // Neither ImageIO nor Skia decodes AVIF; ffprobe does.
        val avif = File("../amethyst/src/androidTest/assets/avif/still-tiny-8x8.avif")
        val avifRead = runBlocking { readDesktopUploadMetadata(avif, probe) }
        assertEquals(8 to 8, avifRead.width to avifRead.height)
    }

    private fun ffmpeg(vararg args: String) {
        val process = ProcessBuilder(listOf("ffmpeg", "-loglevel", "error", "-y") + args).redirectErrorStream(true).start()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
    }
}
