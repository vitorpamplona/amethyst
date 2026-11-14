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
package com.vitorpamplona.claudecode.protocol

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An image sent with a prompt, as the Messages API takes it: base64 data and its media type.
 * [name] is for the host to show; it is not sent.
 */
class ImageInput(
    val name: String,
    val mediaType: String,
    val base64: String,
) {
    companion object {
        /** The API refuses an image over 5 MB of base64; this many bytes stay under it. */
        const val MAX_BYTES = 3_750_000

        /** The longest side the API takes; past it the image is refused. */
        const val MAX_SIDE = 8_000

        /**
         * The longest side worth sending: the API scales anything larger down to about this
         * before the model sees it, so a shrunk image loses nothing.
         */
        const val SHRINK_TO = 1_568

        val EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")

        /**
         * Reads [file] for sending. PNG, JPEG, GIF and WebP go as they are; one too large for
         * the API is shrunk (WebP can't be, and is refused). Blocking: call it off the main thread.
         *
         * @throws IOException when the file can't be read, isn't one of those formats, or is
         *   too large and can't be shrunk.
         */
        fun fromFile(file: File): ImageInput {
            val bytes = file.readBytes()
            val type = mediaTypeOf(bytes) ?: throw IOException("${file.name} is not a PNG, JPEG, GIF or WebP image")
            // ImageIO can't read WebP: one that fits by size goes as it is.
            val fits = bytes.size <= MAX_BYTES && (type == "image/webp" || sideOf(bytes) <= MAX_SIDE)
            if (fits) return ImageInput(file.name, type, encode(bytes))
            val image = ImageIO.read(bytes.inputStream()) ?: throw IOException("${file.name} is too large to send and can't be shrunk")
            val (shrunkType, shrunk) = shrink(image)
            return ImageInput(file.name, shrunkType, encode(shrunk))
        }

        /** The media type from the file's first bytes, or null for anything else. */
        fun mediaTypeOf(bytes: ByteArray): String? {
            fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
            return when {
                starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
                starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                starts(0x47, 0x49, 0x46, 0x38) -> "image/gif"
                bytes.size >= 12 && starts(0x52, 0x49, 0x46, 0x46) && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> null
            }
        }

        /** The longest side, without decoding the pixels; 0 when it can't tell. */
        private fun sideOf(bytes: ByteArray): Int =
            ImageIO.createImageInputStream(bytes.inputStream())?.use { stream ->
                val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return 0
                try {
                    reader.input = stream
                    max(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            } ?: 0

        /** [image] at most [SHRINK_TO] on its longest side, as PNG, or JPEG when PNG is still too big. */
        private fun shrink(image: BufferedImage): Pair<String, ByteArray> {
            val scale = minOf(1.0, SHRINK_TO.toDouble() / max(image.width, image.height))
            val width = max(1, (image.width * scale).roundToInt())
            val height = max(1, (image.height * scale).roundToInt())
            val png = write(scaled(image, width, height, BufferedImage.TYPE_INT_ARGB), "png")
            if (png.size <= MAX_BYTES) return "image/png" to png
            // JPEG has no alpha: draw onto an opaque image first.
            val jpeg = write(scaled(image, width, height, BufferedImage.TYPE_INT_RGB), "jpg")
            if (jpeg.size <= MAX_BYTES) return "image/jpeg" to jpeg
            throw IOException("The image is too large to send")
        }

        private fun scaled(
            image: BufferedImage,
            width: Int,
            height: Int,
            type: Int,
        ): BufferedImage {
            val out = BufferedImage(width, height, type)
            val g = out.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                if (type == BufferedImage.TYPE_INT_RGB) {
                    g.color = Color.WHITE
                    g.fillRect(0, 0, width, height)
                }
                g.drawImage(image, 0, 0, width, height, null)
            } finally {
                g.dispose()
            }
            return out
        }

        private fun write(
            image: BufferedImage,
            format: String,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            if (!ImageIO.write(image, format, out)) throw IOException("No $format writer")
            return out.toByteArray()
        }

        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    }
}
