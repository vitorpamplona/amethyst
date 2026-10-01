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
package com.vitorpamplona.quartz.buzz.media

/**
 * Makes an image acceptable to a Buzz workspace's media server, which refuses (HTTP 422) any blob
 * carrying a metadata channel (`buzz-media/src/validation.rs`): in a JPEG every APP1–APP13/APP15
 * segment (EXIF, XMP, ICC…) and comment, plus any APP0/APP14 that isn't the canonical JFIF/Adobe
 * header; in a PNG every text/EXIF/ICC chunk and every ancillary chunk that isn't a known rendering
 * hint; and in both, any bytes after the end marker. Removing those leaves the pixels untouched.
 *
 * Other formats, and input that doesn't parse, come back unchanged — the server stays the judge.
 */
object BuzzMediaSanitizer {
    fun sanitize(
        bytes: ByteArray,
        mimeType: String?,
    ): ByteArray =
        when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> stripJpeg(bytes) ?: bytes
            "image/png" -> stripPng(bytes) ?: bytes
            else -> bytes
        }

    /** True when [mimeType] is one [sanitize] rewrites. */
    fun handles(mimeType: String?): Boolean = mimeType?.lowercase() in setOf("image/jpeg", "image/jpg", "image/png")

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xff

    /**
     * Walks the JPEG marker by marker, entropy-coded scans included, so a metadata segment after a
     * scan (progressive files interleave tables and scans) is dropped like one before it, and the
     * file ends at the end-of-image marker that closes the last scan — not at whatever `FFD9` some
     * appended payload (a motion-photo video, an extra JPEG) happens to contain.
     */
    fun stripJpeg(b: ByteArray): ByteArray? {
        if (b.size < 4 || b.u8(0) != 0xff || b.u8(1) != 0xd8) return null
        val out = ByteBuilder(b.size)
        out.write(b, 0, 2)
        var i = 2
        while (i < b.size) {
            if (b.u8(i) != 0xff) return null
            var j = i
            while (j < b.size && b.u8(j) == 0xff) j++
            if (j >= b.size) return null
            val marker = b.u8(j)
            when {
                marker == 0xd9 -> {
                    out.write(EOI, 0, 2)
                    return out.toByteArray()
                }

                marker in 0xd0..0xd7 || marker == 0x01 -> {
                    out.write(byteArrayOf(0xff.toByte(), marker.toByte()), 0, 2)
                    i = j + 1
                }

                marker == 0xd8 -> return null

                else -> {
                    if (j + 3 > b.size) return null
                    val len = (b.u8(j + 1) shl 8) or b.u8(j + 2)
                    if (len < 2) return null
                    val end = j + 1 + len
                    if (end > b.size) return null
                    if (keepJpegSegment(marker, b, j + 3, end)) {
                        out.write(FF, 0, 1)
                        out.write(b, j, end - j)
                    }
                    i = end
                    if (marker == 0xda) {
                        // Entropy-coded data runs to the next marker that isn't a stuffed byte
                        // (FF 00) or a restart (FF D0-D7).
                        val scanEnd = endOfScan(b, end) ?: return null
                        out.write(b, end, scanEnd - end)
                        i = scanEnd
                    }
                }
            }
        }
        return null
    }

    private fun endOfScan(
        b: ByteArray,
        from: Int,
    ): Int? {
        var k = from
        while (k + 1 < b.size) {
            if (b.u8(k) == 0xff) {
                val next = b.u8(k + 1)
                if (next != 0x00 && next != 0xff && next !in 0xd0..0xd7) return k
            }
            k++
        }
        return null
    }

    private fun keepJpegSegment(
        marker: Int,
        b: ByteArray,
        payloadStart: Int,
        end: Int,
    ): Boolean {
        val payloadLen = end - payloadStart
        return when {
            marker == 0xe0 ->
                payloadLen >= 14 &&
                    b.u8(payloadStart) == 'J'.code &&
                    b.u8(payloadStart + 1) == 'F'.code &&
                    b.u8(payloadStart + 2) == 'I'.code &&
                    b.u8(payloadStart + 3) == 'F'.code &&
                    b.u8(payloadStart + 4) == 0 &&
                    payloadLen == 14 + 3 * b.u8(payloadStart + 12) * b.u8(payloadStart + 13)

            marker == 0xee ->
                payloadLen == 12 &&
                    b.decodeToString(payloadStart, payloadStart + 5) == "Adobe"

            marker in 0xe1..0xed || marker == 0xef || marker == 0xfe -> false

            else -> true
        }
    }

    private val FF = byteArrayOf(0xff.toByte())

    private val EOI = byteArrayOf(0xff.toByte(), 0xd9.toByte())

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0d, 0x0a, 0x1a, 0x0a)

    // Ancillary chunks Buzz keeps: pure rendering hints. pHYs is excluded on purpose (identity channel).
    private val PNG_RENDERING_CHUNKS = setOf("cHRM", "gAMA", "sBIT", "sRGB", "bKGD", "hIST", "tRNS", "sPLT", "acTL", "fcTL", "fdAT")

    fun stripPng(b: ByteArray): ByteArray? {
        if (b.size < PNG_SIGNATURE.size) return null
        for (k in PNG_SIGNATURE.indices) if (b[k] != PNG_SIGNATURE[k]) return null
        val out = ByteBuilder(b.size)
        out.write(b, 0, PNG_SIGNATURE.size)
        var i = PNG_SIGNATURE.size
        while (i + 12 <= b.size) {
            val len = (b.u8(i) shl 24) or (b.u8(i + 1) shl 16) or (b.u8(i + 2) shl 8) or b.u8(i + 3)
            if (len < 0) return null
            val end = i + 12 + len
            if (end > b.size) return null
            val kind = b.decodeToString(i + 4, i + 8)
            val ancillary = (b.u8(i + 4) and 0x20) != 0
            if (!ancillary || kind in PNG_RENDERING_CHUNKS) out.write(b, i, end - i)
            i = end
            if (kind == "IEND") return out.toByteArray()
        }
        return null
    }

    private class ByteBuilder(
        capacity: Int,
    ) {
        private var buf = ByteArray(maxOf(capacity, 16))
        private var size = 0

        fun write(
            src: ByteArray,
            from: Int,
            count: Int,
        ) {
            if (size + count > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + count))
            src.copyInto(buf, size, from, from + count)
            size += count
        }

        fun toByteArray() = buf.copyOf(size)
    }
}
