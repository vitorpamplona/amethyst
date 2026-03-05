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

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamPanel
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

fun decodeQrFromImage(image: BufferedImage): String? =
    try {
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().decode(bitmap).text
    } catch (_: NotFoundException) {
        null
    }

/**
 * Opens a modal webcam scanner dialog. Scans frames for QR codes continuously.
 * Returns decoded text when found, or null if cancelled / no webcam available.
 * Must be called off the EDT (e.g. from a coroutine on Dispatchers.IO).
 */
fun scanQrFromWebcam(): String? {
    val webcam =
        try {
            Webcam.getDefault()
        } catch (_: Exception) {
            null
        } ?: return null

    webcam.viewSize =
        webcam.viewSizes
            .filter { it.width <= 1280 }
            .maxByOrNull { it.width * it.height }
            ?: webcam.viewSizes.first()

    webcam.open()

    var result: String? = null

    val dialog = JDialog(null as JFrame?, "Scan QR Code", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.layout = BorderLayout()

    val panel = WebcamPanel(webcam, false)
    panel.isFPSDisplayed = false
    panel.isMirrored = true
    dialog.add(panel, BorderLayout.CENTER)

    val statusLabel = JLabel("Point camera at QR code...", SwingConstants.CENTER)
    dialog.add(statusLabel, BorderLayout.SOUTH)

    dialog.preferredSize = Dimension(640, 520)
    dialog.pack()
    dialog.setLocationRelativeTo(null)

    // Scanning thread — reads frames and decodes
    val scanThread =
        Thread {
            panel.start()
            while (result == null && webcam.isOpen && dialog.isVisible) {
                try {
                    val image = webcam.image ?: continue
                    val decoded = decodeQrFromImage(image)
                    if (decoded != null) {
                        result = decoded
                        SwingUtilities.invokeLater { dialog.dispose() }
                        break
                    }
                } catch (_: Exception) {
                    // webcam may throw during shutdown
                }
                Thread.sleep(150)
            }
        }
    scanThread.isDaemon = true
    scanThread.start()

    // Blocks until dialog is closed (modal)
    dialog.isVisible = true

    // Cleanup
    try {
        panel.stop()
        webcam.close()
    } catch (_: Exception) {
    }

    return result
}
