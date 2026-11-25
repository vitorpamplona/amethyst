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

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import com.vitorpamplona.amethyst.commons.service.image.exactScaleSampling
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GifPlayerTest {
    private val width = 40
    private val height = 30

    private class Frame(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val color: Color,
        val disposal: String,
    )

    // Partial frames over a full first one, with every disposal method, so frames depend on
    // earlier ones in each of the ways Skia tracks.
    private val frames =
        listOf(
            Frame(0, 0, width, height, Color.RED, "none"),
            Frame(5, 5, 10, 10, Color.GREEN, "doNotDispose"),
            Frame(20, 10, 10, 10, Color.BLUE, "restoreToPrevious"),
            Frame(10, 15, 15, 10, Color.YELLOW, "restoreToBackgroundColor"),
            Frame(2, 2, 8, 8, Color.CYAN, "doNotDispose"),
            Frame(25, 0, 15, 30, Color.MAGENTA, "restoreToPrevious"),
            Frame(0, 20, 40, 10, Color.ORANGE, "doNotDispose"),
        )

    private fun animatedGif(): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            frames.forEach { frame ->
                val image = BufferedImage(frame.w, frame.h, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.color = frame.color
                g.fillRect(0, 0, frame.w, frame.h)
                g.color = Color.BLACK
                g.drawLine(0, 0, frame.w - 1, frame.h - 1)
                g.dispose()

                val param = writer.defaultWriteParam
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param)
                val format = metadata.nativeMetadataFormatName
                val root = metadata.getAsTree(format) as IIOMetadataNode
                root.getElementsByTagName("GraphicControlExtension").item(0).let { it as IIOMetadataNode }.apply {
                    setAttribute("disposalMethod", frame.disposal)
                    setAttribute("delayTime", "5")
                }
                root.getElementsByTagName("ImageDescriptor").item(0).let { it as IIOMetadataNode }.apply {
                    setAttribute("imageLeftPosition", frame.x.toString())
                    setAttribute("imageTopPosition", frame.y.toString())
                }
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(image, null, metadata), param)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }

    // Each frame decoded on its own: Skia decodes every frame it depends on from scratch.
    private fun referenceFrames(bytes: ByteArray): List<ByteArray> {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        return (0 until codec.frameCount).map { frame ->
            Bitmap().run {
                allocN32Pixels(codec.width, codec.height)
                codec.readPixels(this, frame)
                readPixels()!!
            }
        }
    }

    @Test
    fun streamedFramesMatchFramesDecodedOnTheirOwn() {
        val bytes = animatedGif()
        val expected = referenceFrames(bytes)
        assertEquals(frames.size, expected.size)

        GifPlayer(bytes).use { player ->
            assertEquals(frames.size, player.frameCount)
            // Twice round, so the loop back to the first frame is covered too.
            repeat(2) {
                for (frame in 0 until player.frameCount) {
                    val shown = player.decode(frame).asSkiaBitmap().readPixels()!!
                    assertContentEquals(expected[frame], shown, "frame $frame")
                }
            }
        }
    }

    // Frame [frame] decoded on its own, scaled to [target] with the filter the player uses.
    private fun referenceScaled(
        bytes: ByteArray,
        frame: Int,
        target: IntSize,
    ): ByteArray {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val source = Bitmap().apply { allocN32Pixels(codec.width, codec.height) }
        codec.readPixels(source, frame)
        val out = Bitmap().apply { allocN32Pixels(target.width, target.height) }
        Image.makeFromBitmap(source).use { image ->
            Canvas(out).use { canvas ->
                canvas.clear(0)
                canvas.drawImageRect(image, 0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, target.width.toFloat(), target.height.toFloat(), exactScaleSampling(width, height, target.width, target.height), null, true)
            }
        }
        return out.readPixels()!!
    }

    @Test
    fun framesScaledToTheirDrawnSizeMatchTheFramesScaledOnTheirOwn() {
        val bytes = animatedGif()
        val target = IntSize(width * 2, height * 2)

        GifPlayer(bytes).use { player ->
            var previous: Pair<Image, ByteArray>? = null
            for (frame in 0 until player.frameCount) {
                val shown = player.decodeScaled(frame, FramePlan(target))
                assertEquals(target, IntSize(shown.width, shown.height))
                val pixels = Bitmap.makeFromImage(shown).readPixels()!!
                assertContentEquals(referenceScaled(bytes, frame, target), pixels, "frame $frame")
                // The frame before may still be on screen: decoding this one leaves it as it was.
                previous?.let { (image, before) -> assertContentEquals(before, Bitmap.makeFromImage(image).readPixels()!!, "frame ${frame - 1}") }
                previous = shown to pixels
            }
        }
    }

    @Test
    fun aCroppedPlanKeepsOnlyWhatShowsOfTheScaledFrame() {
        val bytes = animatedGif()
        val target = IntSize(width * 2, height * 2)
        // An 80x60 frame in a 60x40 box: the middle, 10 pixels in from each side.
        val plan = FramePlan(target, IntSize(60, 40), dx = -10, dy = -10)
        val full = referenceScaled(bytes, 0, target)
        val expected = ByteArray(60 * 40 * 4)
        for (y in 0 until 40) System.arraycopy(full, ((y + 10) * target.width + 10) * 4, expected, y * 60 * 4, 60 * 4)

        GifPlayer(bytes).use { player ->
            val shown = player.decodeScaled(0, plan)
            assertEquals(IntSize(60, 40), IntSize(shown.width, shown.height))
            assertContentEquals(expected, Bitmap.makeFromImage(shown).readPixels()!!)
        }
    }

    @Test
    fun frameDurationsComeFromTheGif() {
        GifPlayer(animatedGif()).use { player ->
            // 5 hundredths of a second, as written.
            assertEquals(50, player.duration(0))
        }
    }
}
