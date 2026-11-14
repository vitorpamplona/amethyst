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
package com.vitorpamplona.claudecode

import com.vitorpamplona.claudecode.protocol.ClaudeInput
import com.vitorpamplona.claudecode.protocol.ImageInput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageInputTest {
    private fun tempImage(
        width: Int,
        height: Int,
        noisy: Boolean = false,
    ): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(1)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, if (noisy) random.nextInt() else 0xDC1E1E)
        return File.createTempFile("img", ".png").also {
            it.deleteOnExit()
            ImageIO.write(image, "png", it)
        }
    }

    private fun decoded(input: ImageInput) = ImageIO.read(Base64.getDecoder().decode(input.base64).inputStream())

    @Test
    fun aSmallImageGoesAsItIs() {
        val file = tempImage(40, 30)
        val input = ImageInput.fromFile(file)
        assertEquals("image/png", input.mediaType)
        assertEquals(file.readBytes().toList(), Base64.getDecoder().decode(input.base64).toList())
    }

    @Test
    fun anImageTooLargeForTheApiIsShrunk() {
        // Noise doesn't compress: about 12 MB as PNG.
        val file = tempImage(2000, 2000, noisy = true)
        assertTrue(file.length() > ImageInput.MAX_BYTES)
        val input = ImageInput.fromFile(file)
        val image = decoded(input)
        assertEquals(ImageInput.SHRINK_TO, maxOf(image.width, image.height))
        assertTrue(Base64.getDecoder().decode(input.base64).size <= ImageInput.MAX_BYTES)
    }

    @Test
    fun anImageTooWideIsShrunkEvenWhenSmall() {
        val input = ImageInput.fromFile(tempImage(9000, 10))
        assertEquals(ImageInput.SHRINK_TO, decoded(input).width)
    }

    @Test
    fun aFileThatIsNotAnImageIsRefused() {
        val file = File.createTempFile("notes", ".png").also { it.deleteOnExit() }
        file.writeText("not an image")
        assertFailsWith<IOException> { ImageInput.fromFile(file) }
    }

    @Test
    fun webpIsRecognizedByItsBytes() {
        val bytes = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray()
        assertEquals("image/webp", ImageInput.mediaTypeOf(bytes))
    }

    @Test
    fun imagesGoFirstThenTheText() {
        val line = ClaudeInput.userText("what color?", listOf(ImageInput("a.png", "image/png", "AAAA")))
        val content = line["message"]!!.jsonObject["content"]!!.jsonArray
        assertEquals("image", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        val source = content[0].jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("what color?", content[1].jsonObject["text"]!!.jsonPrimitive.content)
        // An image on its own sends no empty text block.
        val alone = ClaudeInput.userText(" ", listOf(ImageInput("a.png", "image/png", "AAAA")))["message"]!!.jsonObject["content"] as JsonArray
        assertEquals(1, alone.size)
        // Without images the content stays a plain string.
        assertTrue(ClaudeInput.userText("hi", emptyList())["message"]!!.jsonObject["content"] is JsonPrimitive)
        assertTrue(line["message"] is JsonObject)
    }
}
