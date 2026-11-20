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
package com.vitorpamplona.amethyst.commons.service.uploads.gif

import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.PictureHeaders
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import org.w3c.dom.Node
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The GIF editor against an independent codec: test GIFs are written by the JDK's own GIF writer,
 * and what the editor writes is read back by the JDK's GIF reader as well as by [GifReader].
 */
class GifEditorTest {
    private val w = 40
    private val h = 30

    /** Frame [n]: a gradient whose colors and a moving block both depend on [n]. At most 256 colors. */
    private fun frame(n: Int): BufferedImage =
        BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until h) for (x in 0 until w) setRGB(x, y, ((x * 6) shl 16) or ((y * 8) shl 8) or (n * 40))
            for (y in 10 until 20) for (x in n * 5 until n * 5 + 8) setRGB(x, y, 0xFFFFFF)
        }

    /** An animated GIF from the JDK's writer: [frames] frames of [delayCs] each, looping forever. */
    private fun jdkGif(
        frames: Int,
        delayCs: Int,
    ): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            for (n in 0 until frames) {
                val image = frame(n)
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null)
                val root = metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
                child(root, "GraphicControlExtension").apply {
                    setAttribute("disposalMethod", "none")
                    setAttribute("userInputFlag", "FALSE")
                    setAttribute("transparentColorFlag", "FALSE")
                    setAttribute("delayTime", delayCs.toString())
                    setAttribute("transparentColorIndex", "0")
                }
                if (n == 0) {
                    val apps = child(root, "ApplicationExtensions")
                    apps.appendChild(
                        IIOMetadataNode("ApplicationExtension").apply {
                            setAttribute("applicationID", "NETSCAPE")
                            setAttribute("authenticationCode", "2.0")
                            userObject = byteArrayOf(1, 0, 0)
                        },
                    )
                }
                metadata.setFromTree(metadata.nativeMetadataFormatName, root)
                writer.writeToSequence(IIOImage(image, null, metadata), null)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun child(
        root: IIOMetadataNode,
        name: String,
    ): IIOMetadataNode {
        var node: Node? = root.firstChild
        while (node != null) {
            if (node.nodeName == name) return node as IIOMetadataNode
            node = node.nextSibling
        }
        return IIOMetadataNode(name).also { root.appendChild(it) }
    }

    /** Every frame [GifReader] composes, copied. */
    private fun frames(gif: ByteArray): List<GifReader.Frame> {
        val list = mutableListOf<GifReader.Frame>()
        GifReader(gif).forEachFrame {
            list += GifReader.Frame(it.pixels.copyOf(), it.delayCs)
            true
        }
        return list
    }

    private fun rgb(image: BufferedImage) = IntArray(image.width * image.height) { image.getRGB(it % image.width, it / image.width) }

    /** Every frame as the JDK's reader decodes it (whole frames, no disposal, so each image is what shows). */
    private fun jdkFrames(gif: ByteArray): List<BufferedImage> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(gif))
        return (0 until reader.getNumImages(true)).map { reader.read(it) }
    }

    @Test
    fun lzwRoundTrips() {
        val random = Random(7)
        for (bits in listOf(2, 4, 8)) {
            // Long enough to fill the 4096-code table and reset it several times.
            val data = ByteArray(200_000) { (random.nextInt(1 shl bits) and if (it % 3 == 0) 0x0F else 0xFF).toByte() }
            val encoded = GifLzw.encode(data, data.size, bits)
            val decoded = ByteArray(data.size)
            assertEquals(data.size, GifLzw.decode(encoded, bits, decoded, data.size))
            assertContentEquals(data, decoded)
        }
    }

    @Test
    fun readsTheJdksGifExactly() {
        val gif = jdkGif(frames = 4, delayCs = 25)
        val frames = frames(gif)
        val jdk = jdkFrames(gif)
        assertEquals(4, frames.size)
        for (n in frames.indices) {
            assertContentEquals(rgb(jdk[n]), frames[n].pixels, "frame $n")
            assertEquals(25, frames[n].delayCs)
        }
        assertEquals(0, GifReader(gif).loopCount)
    }

    @Test
    fun aCropKeepsEveryPixelAndTheJdkReadsIt() {
        val gif = jdkGif(frames = 4, delayCs = 25)
        // The middle half: x 10..30, y 0..15.
        val edited = assertNotNull(GifEditor.edit(gif, ImageCrop(0.25f, 0f, 0.75f, 0.5f), trim = null))
        val source = jdkFrames(gif)

        val frames = frames(edited)
        assertEquals(4, frames.size)
        for (n in frames.indices) {
            val expected = rgb(source[n].getSubimage(10, 0, 20, 15))
            assertContentEquals(expected, frames[n].pixels, "frame $n")
            assertEquals(25, frames[n].delayCs)
        }
        assertEquals(0, GifReader(edited).loopCount, "still loops forever")

        // An independent reader agrees on the first frame and the size.
        val first = ImageIO.read(ByteArrayInputStream(edited))
        assertEquals(20 to 15, first.width to first.height)
        assertContentEquals(rgb(source[0].getSubimage(10, 0, 20, 15)), rgb(first))
        assertEquals(
            4,
            ImageIO.getImageReadersByFormatName("gif").next().run {
                input = ImageIO.createImageInputStream(ByteArrayInputStream(edited))
                getNumImages(true)
            },
        )
    }

    @Test
    fun aTrimKeepsTheFramesItCoversAndCutsTheEnds() {
        // Four frames of 250 ms: 0-250, 250-500, 500-750, 750-1000.
        val gif = jdkGif(frames = 4, delayCs = 25)
        val edited = assertNotNull(GifEditor.edit(gif, crop = null, trim = VideoTrim(400, 650)))
        val source = jdkFrames(gif)
        val frames = frames(edited)
        assertEquals(2, frames.size)
        assertContentEquals(rgb(source[1]), frames[0].pixels)
        assertContentEquals(rgb(source[2]), frames[1].pixels)
        // 400-500 of frame 1, 500-650 of frame 2.
        assertEquals(listOf(10, 15), frames.map { it.delayCs })
        assertEquals(250L, PictureHeaders.gifDurationMs(edited))
    }

    @Test
    fun transparencyComesThrough() {
        // Frame 0 is half transparent, frame 1 clears a pixel frame 0 had opaque.
        val f0 = IntArray(w * h) { if (it % w < w / 2) 0 else 0xFF102030.toInt() }
        val f1 = IntArray(w * h) { if (it == w * h - 1) 0 else 0xFF405060.toInt() }
        val gif =
            GifWriter(w, h, loopCount = 0)
                .apply {
                    add(f0, 10)
                    add(f1, 10)
                }.finish()
        val frames = frames(gif)
        assertContentEquals(f0, frames[0].pixels)
        assertContentEquals(f1, frames[1].pixels, "a pixel cleared by the next frame comes out transparent")

        val edited = assertNotNull(GifEditor.edit(gif, ImageCrop(0.25f, 0f, 1f, 1f), trim = null))
        val cut = frames(edited)
        assertEquals(30 to 30, GifReader(edited).width to GifReader(edited).height)
        assertEquals(0, cut[0].pixels[0] ushr 24, "still transparent")
        assertEquals(0, cut[1].pixels.last() ushr 24)
    }

    @Test
    fun moreThan256ColorsStillWrites() {
        // 1200 colors in one frame: the most used 256 stand in.
        val many = IntArray(w * h) { 0xFF000000.toInt() or (it * 13) }
        val gif = GifWriter(w, h, loopCount = null).apply { add(many, 10) }.finish()
        val frame = frames(gif).single()
        assertTrue(frame.pixels.all { it ushr 24 == 0xFF })
        assertEquals(null, GifReader(gif).loopCount, "no loop block when none was asked")
    }

    @Test
    fun frameAtGivesThatMomentSmaller() {
        val gif = jdkGif(frames = 4, delayCs = 25)
        val still = assertNotNull(GifEditor.frameAt(gif, 600, maxEdge = 20))
        val image = ImageIO.read(ByteArrayInputStream(still))
        assertEquals(20 to 15, image.width to image.height)
        // 600 ms is frame 2: its block starts at x = 10, so x 5..8 (scaled 2:1) is white.
        assertEquals(0xFFFFFF, image.getRGB(6, 7) and 0xFFFFFF)
        // Past the end it loops: 1600 ms is frame 2 again.
        assertContentEquals(rgb(image), rgb(ImageIO.read(ByteArrayInputStream(assertNotNull(GifEditor.frameAt(gif, 1600, 20))))))
    }

    @Test
    fun framesAtMatchesFrameAtInTheOrderAsked() {
        val gif = jdkGif(frames = 4, delayCs = 25)
        // Out of order and past the end: each still lands where frameAt would put it.
        val times = listOf(900L, 0L, 1600L, 300L, 600L)
        val all = GifEditor.framesAt(gif, times, maxEdge = 20)
        assertEquals(times.size, all.size)
        times.zip(all).forEach { (atMs, bytes) ->
            val one = ImageIO.read(ByteArrayInputStream(assertNotNull(GifEditor.frameAt(gif, atMs, 20))))
            assertContentEquals(rgb(one), rgb(ImageIO.read(ByteArrayInputStream(assertNotNull(bytes)))), "at $atMs ms")
        }
    }

    @Test
    fun aCancelledCallerGetsNothing() {
        val gif = jdkGif(frames = 4, delayCs = 25)
        assertEquals(null, GifEditor.edit(gif, null, VideoTrim(0, 500)) { false })
        assertEquals(listOf(null, null), GifEditor.framesAt(gif, listOf(0, 600), 20) { false })
    }

    @Test
    fun aCanvasTooLargeToDecodeIsRefused() {
        val gif = jdkGif(frames = 2, delayCs = 25)
        // Claim a 4096x4096 screen: 64 MB of pixels per canvas, past the cap, so nothing is allocated.
        val huge =
            gif.copyOf().apply {
                this[6] = 0
                this[7] = 0x10
                this[8] = 0
                this[9] = 0x10
            }
        assertEquals(null, GifEditor.edit(huge, null, VideoTrim(0, 100)))
        assertEquals(null, GifEditor.frameAt(huge, 0, 20))
    }

    @Test
    fun aFileOverTheCapIsRefused() {
        val gif = jdkGif(frames = 2, delayCs = 25)
        val padded = gif.copyOf((GifEditor.MAX_FILE_BYTES + 1).toInt())
        assertEquals(null, GifEditor.edit(padded, null, VideoTrim(0, 100)))
        assertEquals(null, GifEditor.frameAt(padded, 0, 20))
    }

    @Test
    fun notAGifIsNull() {
        assertEquals(null, GifEditor.edit(ByteArray(50), null, VideoTrim(0, 100)))
        assertEquals(null, GifEditor.frameAt("PNG".encodeToByteArray(), 0, 10))
    }
}
