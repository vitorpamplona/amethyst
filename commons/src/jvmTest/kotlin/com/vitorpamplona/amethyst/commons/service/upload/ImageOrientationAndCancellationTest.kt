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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.ReencodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phone cameras store pixels sideways and record the turn in the EXIF orientation tag, which every
 * viewer applies. The re-encoder writes no EXIF, so it must apply the turn itself; the metadata
 * strip must keep the tag while it drops the rest. And a cancelled re-encode must not leave its
 * temp file behind.
 */
class ImageOrientationAndCancellationTest {
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun reencodeAppliesExifRotation() =
        runTest {
            // Stored 400 x 200 with a red block in the stored top-left; orientation 6 means "turn
            // 90 degrees clockwise to display", which puts that block in the displayed top-right.
            val src = makeJpegWithMarker(400, 200, orientation = 6)
            val out = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f))).file.also(createdFiles::add)

            val decoded = ImageIO.read(out)
            assertEquals(50, decoded.width, "displayed width")
            assertEquals(100, decoded.height, "displayed height")
            assertTrue(isRed(decoded.getRGB(decoded.width - 5, 5)), "marker lands top-right")
            assertTrue(!isRed(decoded.getRGB(5, 5)), "top-left is not the marker")
        }

    @Test
    fun reencodeKeepsUnrotatedImagesAsTheyAre() =
        runTest {
            val src = makeJpegWithMarker(400, 200, orientation = 1)
            val out = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f))).file.also(createdFiles::add)

            val decoded = ImageIO.read(out)
            assertEquals(100, decoded.width)
            assertEquals(50, decoded.height)
            assertTrue(isRed(decoded.getRGB(3, 3)), "marker stays top-left")
        }

    @Test
    fun stripExifKeepsOrientationAndDropsTheRest() {
        val src = makeJpegWithMarker(400, 200, orientation = 6, withGps = true)
        val stripped = MediaCompressor.stripExif(src).also(createdFiles::add)
        assertNotEquals(src, stripped, "a JPEG with GPS must be rewritten")

        val exif = (Imaging.getMetadata(stripped) as? JpegImageMetadata)
        assertEquals(6, exif?.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION)?.intValue, "orientation kept")
        assertNull(exif?.exif?.gpsInfo, "GPS removed")
    }

    @Test
    fun metadataReportsTheDisplayedSize() {
        val src = makeJpegWithMarker(400, 200, orientation = 6)
        val metadata = MediaMetadataReader.compute(src)
        assertEquals(200, metadata.width, "displayed width")
        assertEquals(400, metadata.height, "displayed height")
    }

    @Test
    fun cancellingDuringEncodeLeavesNoTempFile() =
        runBlocking {
            val src = makeNoisyJpeg(5000, 4000)
            val tempDir = AmethystTempDir.rootDir()

            fun compressTemps() = tempDir.listFiles { f -> f.name.startsWith("amethyst_compress_") }.orEmpty().toSet()
            val before = compressTemps()

            val job = async(Dispatchers.Default) { ImageReencoder.reencode(src, ImageSizeTarget(4000, 0.95f)) }
            // The temp file is created just before the (uncancellable) JPEG encode starts.
            var waited = 0
            while ((compressTemps() - before).isEmpty() && waited < 20_000) {
                delay(1)
                waited++
            }
            job.cancel()
            val outcome = runCatching { job.await() }
            outcome.getOrNull()?.let { (it as? ReencodeResult.Reencoded)?.file?.let(createdFiles::add) }
            assertTrue(outcome.isFailure, "the encode should still be running when cancelled; enlarge the image if not")

            assertEquals(emptySet(), compressTemps() - before, "the cancelled re-encode's temp file is gone")
        }

    // ---------- helpers ----------

    private fun isRed(argb: Int): Boolean {
        val c = Color(argb)
        return c.red > 200 && c.green < 60 && c.blue < 60
    }

    private fun makeJpegWithMarker(
        width: Int,
        height: Int,
        orientation: Int,
        withGps: Boolean = false,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color.RED
            g.fillRect(0, 0, width / 4, height / 4)
        } finally {
            g.dispose()
        }
        val plain = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()

        val set = TiffOutputSet()
        set.orCreateRootDirectory.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.toShort())
        if (withGps) set.setGpsInDegrees(-71.06, 42.36)
        val tagged = ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(plain, it, set) }.toByteArray()

        return File.createTempFile("orientation_src_", ".jpg").also {
            it.writeBytes(tagged)
            createdFiles += it
        }
    }

    private fun makeNoisyJpeg(
        width: Int,
        height: Int,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val rnd = Random(42)
        for (y in 0 until height) for (x in 0 until width) img.setRGB(x, y, rnd.nextInt())
        return File.createTempFile("cancel_src_", ".jpg").also {
            ImageIO.write(img, "jpg", it)
            createdFiles += it
        }
    }
}
