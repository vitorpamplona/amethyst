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

import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

/**
 * Crops a picture for an avatar, banner or icon on the JVM: decodes it, applies the camera's
 * orientation, cuts the [ImageCrop] out of the picture as it displays, and writes the result
 * without metadata: PNG when the picture has transparency or was a PNG (a screenshot's text stays
 * sharp), JPEG otherwise.
 */
object ImageCropper {
    private const val TAG = "ImageCropper"
    private const val JPEG_QUALITY = 0.92f

    class Cropped(
        val file: File,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    /**
     * @param fallbackDecode decodes what ImageIO cannot (desktop passes Skia, for WebP); its
     *   result is taken as already upright.
     * @return null when the picture could not be decoded or is larger than
     *   [ImageReencoder.maxInputPixels].
     */
    suspend fun crop(
        source: File,
        crop: ImageCrop,
        fallbackDecode: ((ByteArray) -> BufferedImage?)? = null,
        /** The source is a PNG: the crop stays lossless. */
        keepPng: Boolean = false,
        tempFile: (suffix: String) -> File = { AmethystTempDir.createTempFile("amethyst_crop_", it) },
    ): Cropped? =
        withContext(Dispatchers.IO) {
            val upright = decodeUpright(source, fallbackDecode) ?: return@withContext null
            val (x, y, w, h) = crop.pixels(upright.width, upright.height).toList()
            val alpha = upright.colorModel.hasAlpha()
            val png = alpha || keepPng
            val out = BufferedImage(w, h, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            try {
                if (!alpha) {
                    g.color = Color.WHITE
                    g.fillRect(0, 0, w, h)
                }
                g.drawImage(upright, -x, -y, null)
            } finally {
                g.dispose()
            }
            val file = tempFile(if (png) ".png" else ".jpg")
            try {
                if (png) ImageIO.write(out, "png", file) else writeJpeg(out, file)
                Cropped(file, if (png) "image/png" else "image/jpeg", w, h)
            } catch (e: Throwable) {
                file.deleteOrWarn(TAG, "failed crop")
                throw e
            }
        }

    private fun decodeUpright(
        source: File,
        fallbackDecode: ((ByteArray) -> BufferedImage?)?,
    ): BufferedImage? {
        val decoded =
            runCatching {
                ImageIO.createImageInputStream(source)?.use { iis ->
                    val reader = ImageIO.getImageReaders(iis).asSequence().firstOrNull() ?: return@use null
                    try {
                        reader.input = iis
                        if (reader.getWidth(0).toLong() * reader.getHeight(0) > ImageReencoder.maxInputPixels) return@use null
                        reader.read(0)
                    } finally {
                        reader.dispose()
                    }
                }
            }.getOrNull()
        if (decoded != null) return ExifOrientation.apply(decoded, ExifOrientation.read(source))
        if (fallbackDecode == null || source.length() > MAX_FALLBACK_BYTES) return null
        return runCatching { fallbackDecode(source.readBytes()) }
            .getOrNull()
            ?.takeIf { it.width.toLong() * it.height <= ImageReencoder.maxInputPixels }
    }

    private fun writeJpeg(
        image: BufferedImage,
        out: File,
    ) {
        val writer = ImageIO.getImageWritersByMIMEType("image/jpeg").next()
        try {
            FileImageOutputStream(out).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JPEG_QUALITY
                    }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    private const val MAX_FALLBACK_BYTES = 64L * 1024 * 1024
}
