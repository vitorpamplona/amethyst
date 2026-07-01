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

import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO

object ClipboardPasteHandler {
    private val PASTE_TIMESTAMP = SimpleDateFormat("yyyyMMdd-HHmmss")

    fun getClipboardFiles(): List<File> {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return try {
            when {
                clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) -> {
                    @Suppress("UNCHECKED_CAST")
                    (clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>) ?: emptyList()
                }

                clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> {
                    val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return emptyList()
                    val buffered = toBufferedImage(image)
                    // Temp lives under AmethystTempDir so the boot-time orphan
                    // sweep recovers it if the JVM crashes before the upload
                    // pipeline gets a chance to consume it. The intermediate
                    // PNG is paid for by clipboard semantics — Java's
                    // clipboard image flavor only exposes BufferedImage, so
                    // PNG is the lossless container we materialize before
                    // the orchestrator decides to re-encode.
                    val timestamp = synchronized(PASTE_TIMESTAMP) { PASTE_TIMESTAMP.format(Date()) }
                    val tempFile = AmethystTempDir.createTempFile("amethyst_paste_${timestamp}_", ".png")
                    ImageIO.write(buffered, "png", tempFile)
                    listOf(tempFile)
                }

                else -> {
                    emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toBufferedImage(image: Image): BufferedImage {
        if (image is BufferedImage) return image
        val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
        buffered.graphics.drawImage(image, 0, 0, null)
        return buffered
    }
}
