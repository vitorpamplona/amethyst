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
package com.vitorpamplona.amethyst.commons.ui.components.animated

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.image.exactScaleSampling
import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedGifImageRenderTest {
    /** Reads the picked file from disk, as the desktop's fetcher does; never downloads. */
    private val files =
        object : AnimatedImageFetcher {
            override fun readLocal(url: String): ByteArray? = File(url).takeIf { it.isFile }?.readBytes()

            override fun mayCacheOnDisk(url: String): Boolean = false

            override suspend fun download(url: String): ByteArray? = null
        }

    @AfterTest
    fun reset() {
        SoftwareDrawing.active = false
    }

    /** Two frames, a 1-pixel checkerboard and its inverse: any resampling at draw time changes them. */
    private fun checkerGif(frames: Int = 2): File {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val file = File.createTempFile("checker", ".gif").apply { deleteOnExit() }
        ImageIO.createImageOutputStream(file).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            for (phase in 0 until frames) {
                val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until 30) for (x in 0 until 40) image.setRGB(x, y, if ((x + y + phase) % 2 == 0) 0x000000 else 0xFFFFFF)
                val param = writer.defaultWriteParam
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param)
                val format = metadata.nativeMetadataFormatName
                val root = metadata.getAsTree(format) as IIOMetadataNode
                (root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode).setAttribute("delayTime", "5")
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(image, null, metadata), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return file
    }

    /**
     * Frame [frame] of [file] scaled to 160x120 with the player's filter, then placed 1:1 in a
     * [boxWidth] x [boxHeight] box, centred (cropped when the box is smaller), as RGBA bytes.
     */
    private fun prescaled(
        file: File,
        frame: Int,
        boxWidth: Int = 160,
        boxHeight: Int = 120,
    ): ByteArray {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        val source = Bitmap().apply { allocN32Pixels(40, 30) }
        codec.readPixels(source, frame)
        val scaled = Bitmap().apply { allocN32Pixels(160, 120) }
        Image.makeFromBitmap(source).use { image ->
            Canvas(scaled).use { it.drawImageRect(image, 0f, 0f, 40f, 30f, 0f, 0f, 160f, 120f, exactScaleSampling(40, 30, 160, 120), null, true) }
        }
        val out = Bitmap().apply { allocN32Pixels(boxWidth, boxHeight) }
        Image.makeFromBitmap(scaled).use { image ->
            Canvas(out).use { it.drawImage(image, ((boxWidth - 160) / 2).toFloat(), ((boxHeight - 120) / 2).toFloat()) }
        }
        return out.readPixels()!!
    }

    /** Renders [file] in a [width] x [height] box until a frame equals one of [expected], up to 10 s. */
    private fun rendersOneOf(
        file: File,
        width: Int,
        height: Int,
        contentScale: ContentScale,
        expected: List<ByteArray>,
    ): Boolean {
        val scene =
            ImageComposeScene(width, height, Density(1f)) {
                AnimatedGifImage(url = file.path, local = true, fetcher = files, modifier = Modifier.size(width.dp, height.dp), contentScale = contentScale)
            }
        var matched = false
        val deadline = System.currentTimeMillis() + 10_000
        while (!matched && System.currentTimeMillis() < deadline) {
            val pixels = Bitmap.makeFromImage(scene.render(System.nanoTime())).readPixels()!!
            matched = expected.any { it.contentEquals(pixels) }
            if (!matched) Thread.sleep(30)
        }
        scene.close()
        return matched
    }

    @Test
    fun croppedFramesDrawScaledOnceAndThen1to1() {
        SoftwareDrawing.active = true
        val file = checkerGif()
        // A banner: 4x scales the 40x30 GIF to 160x120, of which the box shows the middle 80 rows.
        val expected = listOf(prescaled(file, 0, 160, 80), prescaled(file, 1, 160, 80))
        assertTrue(rendersOneOf(file, 160, 80, ContentScale.Crop, expected), "no rendered frame was a cropped frame scaled by the player and drawn 1:1")
    }

    @Test
    fun aStillGifDrawsItsFrameScaledOnceAndThen1to1() {
        SoftwareDrawing.active = true
        // One frame: nothing plays, so the first frame is what draws, every frame.
        val file = checkerGif(frames = 1)
        assertTrue(rendersOneOf(file, 160, 120, ContentScale.Fit, listOf(prescaled(file, 0))), "the still frame was not drawn 1:1 from a scaled copy")
    }

    @Test
    fun inSoftwareTheFramesDrawScaledOnceAndThen1to1() {
        SoftwareDrawing.active = true
        val file = checkerGif()
        val expected = listOf(prescaled(file, 0), prescaled(file, 1))
        val scene =
            ImageComposeScene(160, 120, Density(1f)) {
                AnimatedGifImage(url = file.path, local = true, fetcher = files, modifier = Modifier.size(160.dp, 120.dp), contentScale = ContentScale.Fit)
            }
        // The GIF loads and the player starts on other threads: render until a played frame is up.
        var matched = false
        val deadline = System.currentTimeMillis() + 10_000
        while (!matched && System.currentTimeMillis() < deadline) {
            val pixels = Bitmap.makeFromImage(scene.render(System.nanoTime())).readPixels()!!
            matched = expected.any { it.contentEquals(pixels) }
            if (!matched) Thread.sleep(30)
        }
        scene.close()
        assertTrue(matched, "no rendered frame was a frame scaled by the player and drawn 1:1")
    }

    @Test
    fun withAutoplayOffOnlyTheFirstFrameEverDraws() {
        SoftwareDrawing.active = true
        val file = checkerGif()
        val first = prescaled(file, 0)
        val second = prescaled(file, 1)
        val scene =
            ImageComposeScene(160, 120, Density(1f)) {
                AnimatedGifImage(url = file.path, local = true, fetcher = files, autoPlay = false, modifier = Modifier.size(160.dp, 120.dp), contentScale = ContentScale.Fit)
            }
        // The frames last 50 ms: a second of rendering would show the other one if a player ran.
        var sawFirst = false
        var sawSecond = false
        val deadline = System.currentTimeMillis() + 10_000
        var firstSeenAt = 0L
        while (System.currentTimeMillis() < deadline && (!sawFirst || System.currentTimeMillis() < firstSeenAt + 1_000)) {
            val pixels = Bitmap.makeFromImage(scene.render(System.nanoTime())).readPixels()!!
            if (pixels.contentEquals(first) && !sawFirst) {
                sawFirst = true
                firstSeenAt = System.currentTimeMillis()
            }
            if (pixels.contentEquals(second)) sawSecond = true
            Thread.sleep(30)
        }
        scene.close()
        assertTrue(sawFirst, "the first frame never drew")
        assertFalse(sawSecond, "a second frame drew although autoplay is off")
    }
}
