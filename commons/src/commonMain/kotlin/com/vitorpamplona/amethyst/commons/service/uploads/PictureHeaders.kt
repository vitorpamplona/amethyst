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
package com.vitorpamplona.amethyst.commons.service.uploads

/**
 * What the first bytes of a picked picture say about it that its MIME type doesn't: a GIF's size,
 * whether a WebP moves, and how long a GIF plays. Pure byte reads, so every platform shares them.
 */
object PictureHeaders {
    /** Bytes [isAnimatedWebp] and [webpCanvasSize] need. */
    const val WEBP_HEADER_BYTES = 30

    /** Bytes [gifSize] needs. */
    const val GIF_HEADER_BYTES = 10

    /** A GIF's logical screen size (width to height), or null when [header] isn't a GIF's start. */
    fun gifSize(header: ByteArray): Pair<Int, Int>? {
        if (header.size < GIF_HEADER_BYTES || !isGif(header)) return null
        val width = u16(header, 6)
        val height = u16(header, 8)
        return if (width > 0 && height > 0) width to height else null
    }

    /** Whether [header] starts an extended WebP whose animation flag is set (`RIFF....WEBPVP8X`, flags bit 1). */
    fun isAnimatedWebp(header: ByteArray): Boolean {
        if (header.size < WEBP_HEADER_BYTES) return false
        if (!matches(header, 0, "RIFF") || !matches(header, 8, "WEBP") || !matches(header, 12, "VP8X")) return false
        return header[20].toInt() and ANIMATION_FLAG != 0
    }

    /** An extended WebP's canvas size (width to height), from its VP8X chunk; null for any other header. */
    fun webpCanvasSize(header: ByteArray): Pair<Int, Int>? {
        if (header.size < WEBP_HEADER_BYTES) return null
        if (!matches(header, 0, "RIFF") || !matches(header, 8, "WEBP") || !matches(header, 12, "VP8X")) return null
        return u24(header, 24) + 1 to u24(header, 27) + 1
    }

    /**
     * How long the GIF in [bytes] plays once, in milliseconds: the sum of its frames' delays, a
     * delay under 20 ms counting as 100 ms as browsers draw it. Null when [bytes] isn't a GIF or
     * is cut short before its first frame.
     */
    fun gifDurationMs(bytes: ByteArray): Long? {
        if (bytes.size < 13 || !isGif(bytes)) return null
        var at = 13
        val screenFlags = bytes[10].toInt() and 0xFF
        if (screenFlags and 0x80 != 0) at += 3 * (1 shl ((screenFlags and 0x07) + 1))
        var total = 0L
        var frames = 0
        var delay = 0
        while (at < bytes.size) {
            when (bytes[at].toInt() and 0xFF) {
                // Extension: a Graphic Control Extension carries the next frame's delay.
                0x21 -> {
                    if (at + 1 >= bytes.size) break
                    if (bytes[at + 1].toInt() and 0xFF == 0xF9 && at + 6 < bytes.size) delay = u16(bytes, at + 4)
                    at = skipSubBlocks(bytes, at + 2) ?: break
                }
                // Image: one frame, its delay set by the extension before it.
                0x2C -> {
                    if (at + 10 > bytes.size) break
                    val flags = bytes[at + 9].toInt() and 0xFF
                    at += 10
                    if (flags and 0x80 != 0) at += 3 * (1 shl ((flags and 0x07) + 1))
                    at = skipSubBlocks(bytes, at + 1) ?: break
                    frames++
                    total += if (delay < MIN_DELAY_CS) DEFAULT_DELAY_CS * 10L else delay * 10L
                    delay = 0
                }
                0x3B -> break
                else -> break
            }
        }
        return if (frames > 0) total else null
    }

    private fun skipSubBlocks(
        bytes: ByteArray,
        start: Int,
    ): Int? {
        var at = start
        while (at < bytes.size) {
            val size = bytes[at].toInt() and 0xFF
            at += 1 + size
            if (size == 0) return at
        }
        return null
    }

    private fun isGif(bytes: ByteArray) = matches(bytes, 0, "GIF87a") || matches(bytes, 0, "GIF89a")

    private fun matches(
        bytes: ByteArray,
        at: Int,
        text: String,
    ): Boolean {
        if (at + text.length > bytes.size) return false
        for (i in text.indices) if (bytes[at + i] != text[i].code.toByte()) return false
        return true
    }

    private fun u16(
        bytes: ByteArray,
        at: Int,
    ) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u24(
        bytes: ByteArray,
        at: Int,
    ) = u16(bytes, at) or ((bytes[at + 2].toInt() and 0xFF) shl 16)

    private const val ANIMATION_FLAG = 0x02
    private const val MIN_DELAY_CS = 2
    private const val DEFAULT_DELAY_CS = 10
}
