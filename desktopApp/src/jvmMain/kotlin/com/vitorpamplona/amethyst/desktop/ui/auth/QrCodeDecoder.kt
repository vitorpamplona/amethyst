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
package com.vitorpamplona.amethyst.desktop.ui.auth

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private fun decodeQrFromImage(image: BufferedImage): String? =
    try {
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().decode(bitmap).text
    } catch (_: NotFoundException) {
        null
    }

private fun findFfmpeg(): String? =
    listOf("ffmpeg", "/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg")
        .firstOrNull { path ->
            try {
                ProcessBuilder(path, "-version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }

/**
 * Opens a webcam scanner dialog using ffmpeg to capture frames.
 * Continuously decodes frames looking for QR codes.
 * Returns decoded text when found, or null if cancelled / no webcam.
 * Must be called off the EDT (e.g. Dispatchers.IO).
 */
fun scanQrFromWebcam(): String? {
    val ffmpeg = findFfmpeg() ?: return null

    val result = AtomicReference<String?>(null)
    val running = AtomicBoolean(true)

    val imagePanel =
        object : JPanel() {
            var currentImage: BufferedImage? = null

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                currentImage?.let { img ->
                    val scale =
                        minOf(
                            width.toDouble() / img.width,
                            height.toDouble() / img.height,
                        )
                    val w = (img.width * scale).toInt()
                    val h = (img.height * scale).toInt()
                    val x = (width - w) / 2
                    val y = (height - h) / 2
                    g.drawImage(img, x, y, w, h, null)
                }
            }
        }

    val statusLabel = JLabel("Point camera at QR code...", SwingConstants.CENTER)

    val dialog = JDialog(null as JFrame?, "Scan QR Code", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.layout = BorderLayout()
    dialog.add(imagePanel, BorderLayout.CENTER)
    dialog.add(statusLabel, BorderLayout.SOUTH)
    dialog.preferredSize = Dimension(640, 520)
    dialog.pack()
    dialog.setLocationRelativeTo(null)

    dialog.addWindowListener(
        object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                running.set(false)
            }
        },
    )

    // Capture thread — runs ffmpeg to grab single frames in a loop
    val captureThread =
        Thread {
            while (running.get()) {
                try {
                    val process =
                        ProcessBuilder(
                            ffmpeg,
                            "-f",
                            "avfoundation",
                            "-video_size",
                            "640x480",
                            "-framerate",
                            "15",
                            "-i",
                            "0",
                            "-frames:v",
                            "1",
                            "-f",
                            "image2pipe",
                            "-vcodec",
                            "mjpeg",
                            "-q:v",
                            "5",
                            "pipe:1",
                        ).redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start()

                    val baos = ByteArrayOutputStream()
                    process.inputStream.use { it.copyTo(baos) }
                    process.waitFor()

                    val bytes = baos.toByteArray()
                    if (bytes.isEmpty()) continue

                    val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: continue

                    // Update preview
                    imagePanel.currentImage = image
                    SwingUtilities.invokeLater { imagePanel.repaint() }

                    // Try to decode QR
                    val decoded = decodeQrFromImage(image)
                    if (decoded != null) {
                        result.set(decoded)
                        running.set(false)
                        SwingUtilities.invokeLater { dialog.dispose() }
                        break
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                    Thread.sleep(500)
                }
            }
        }
    captureThread.isDaemon = true
    captureThread.start()

    // Blocks until dialog is closed (modal)
    dialog.isVisible = true
    running.set(false)

    return result.get()
}
