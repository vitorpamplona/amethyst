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
package com.vitorpamplona.amethyst.desktop.ui.qrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecodeQrCodeTest {
    private val npub = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

    /** Draws [text] as a QR code with a quiet zone, dark on light unless [inverted]. */
    private fun qrImage(
        text: String,
        inverted: Boolean = false,
    ): BufferedImage {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val dark = if (inverted) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val light = if (inverted) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                image.setRGB(x, y, if (matrix[x, y]) dark else light)
            }
        }
        return image
    }

    @Test
    fun decodesAPictureOfACode() {
        assertEquals(npub, decodeQrCode(qrImage(npub)))
    }

    @Test
    fun decodesALightOnDarkScreenshot() {
        assertEquals(npub, decodeQrCode(qrImage(npub, inverted = true)))
    }

    @Test
    fun returnsNullWhenThePictureHasNoCode() {
        val blank = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        assertNull(decodeQrCode(blank))
    }
}
