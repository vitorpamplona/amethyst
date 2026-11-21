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
package com.vitorpamplona.amethyst.desktop.ui.media

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
import kotlin.test.assertTrue

class AnimatedGifImageRenderTest {
    @AfterTest
    fun reset() {
        SoftwareDrawing.active = false
    }

    /** Two frames, a 1-pixel checkerboard and its inverse: any resampling at draw time changes them. */
    private fun checkerGif(): File {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val file = File.createTempFile("checker", ".gif").apply { deleteOnExit() }
        ImageIO.createImageOutputStream(file).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            for (phase in 0..1) {
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

    /** Frame [frame] of [file] scaled to 160x120 with the player's filter, as RGBA bytes. */
    private fun prescaled(
        file: File,
        frame: Int,
    ): ByteArray {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        val source = Bitmap().apply { allocN32Pixels(40, 30) }
        codec.readPixels(source, frame)
        val out = Bitmap().apply { allocN32Pixels(160, 120) }
        Image.makeFromBitmap(source).use { image ->
            Canvas(out).use { it.drawImageRect(image, 0f, 0f, 40f, 30f, 0f, 0f, 160f, 120f, exactScaleSampling(false), null, true) }
        }
        return out.readPixels()!!
    }

    @Test
    fun inSoftwareTheFramesDrawScaledOnceAndThen1to1() {
        SoftwareDrawing.active = true
        val file = checkerGif()
        val expected = listOf(prescaled(file, 0), prescaled(file, 1))
        val scene =
            ImageComposeScene(160, 120, Density(1f)) {
                AnimatedGifImage(url = file.path, local = true, modifier = Modifier.size(160.dp, 120.dp), contentScale = ContentScale.Fit)
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
}
