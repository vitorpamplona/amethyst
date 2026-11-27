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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

/** The dark/light modules of an encoded QR code, row-major, without its quiet zone. */
class QrMatrix(
    val width: Int,
    val height: Int,
    private val modules: BooleanArray,
) {
    operator fun get(
        x: Int,
        y: Int,
    ): Boolean = modules[y * width + x]
}

/**
 * Error correction Q first, for codes photographed off a screen; a payload too long for Q at the
 * largest version still fits at M or L, and a slightly less forgiving code beats the empty square
 * that used to be drawn in its place.
 */
private val ERROR_CORRECTION_FALLBACK = listOf(QrEncoder.Level.Q, QrEncoder.Level.M, QrEncoder.Level.L)

/**
 * Encodes [contents] (UTF-8, error correction Q, falling back to M then L for payloads too long
 * for Q) into its module grid, or null when it is empty or does not fit a QR code at all. The
 * same encoder on every platform ([QrEncoder]), drawing the code ZXing drew on Android.
 */
fun encodeQrMatrix(contents: String): QrMatrix? {
    if (contents.isEmpty()) return null
    return ERROR_CORRECTION_FALLBACK.firstNotNullOfOrNull { QrEncoder.encode(contents, it) }
}
