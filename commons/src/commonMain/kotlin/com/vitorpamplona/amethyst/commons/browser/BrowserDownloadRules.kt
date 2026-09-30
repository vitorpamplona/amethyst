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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The platform-free rules behind the in-app browser's download consent: which file names deserve a
 * warning, how a page-supplied `data:` URL turns into the bytes a consent card describes, and how often
 * one site may ask.
 *
 * A page can start a download without any gesture (a navigation to an attachment, a script `.click()`
 * on an `<a download>`, or a hand-forged bridge envelope), so every page-initiated save asks the user
 * first. The card shows exactly the name and size these rules produce, and the save writes exactly that.
 */
object BrowserDownloadRules {
    /** Cap for bytes a page hands over inline (a `blob:`/`data:` download travels as base64 over the bridge). */
    const val MAX_INLINE_BYTES = 25 * 1024 * 1024

    /**
     * Extensions something can install or run from. The consent card flags them; the name itself is
     * never rewritten, so the user judges the real one.
     */
    val RISKY_EXTENSIONS =
        setOf(
            "apk",
            "apks",
            "apkm",
            "xapk",
            "aab",
            "jar",
            "dex",
            "so",
            "exe",
            "msi",
            "bat",
            "cmd",
            "com",
            "scr",
            "hta",
            "ps1",
            "vbs",
            "wsf",
            "lnk",
            "url",
            "dmg",
            "pkg",
            "app",
            "command",
            "deb",
            "rpm",
            "appimage",
            "run",
            "sh",
            "zsh",
            "bash",
            "desktop",
            "html",
            "htm",
            "xhtml",
            "svg",
        )

    /** Whether [fileName]'s last extension is one a user should double-check before saving. */
    fun isRisky(fileName: String): Boolean = fileName.substringAfterLast('.', "").trim().lowercase() in RISKY_EXTENSIONS

    /** A decoded `data:` URL: the declared MIME type (null when absent) and the payload bytes. */
    class DataUrl(
        val mimeType: String?,
        val bytes: ByteArray,
    )

    private val lenientBase64 = Base64.Mime.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    /**
     * Decodes `data:[<mime>][;param=v…][;base64],<payload>`, base64 or percent-encoded. Null when it is
     * not a data URL, is malformed, or decodes to more than [maxBytes] — a refused download, never a
     * truncated one. The encoded length is checked first, so an oversized payload is never decoded.
     */
    fun decodeDataUrl(
        dataUrl: String,
        maxBytes: Int = MAX_INLINE_BYTES,
    ): DataUrl? {
        if (!dataUrl.startsWith("data:", ignoreCase = true)) return null
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        val header = dataUrl.substring(5, comma)
        val params = header.split(';')
        val mime =
            params
                .first()
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
        val isBase64 = params.drop(1).any { it.trim().equals("base64", ignoreCase = true) }
        val payloadLength = dataUrl.length - comma - 1
        val bytes =
            if (isBase64) {
                // 4 chars per 3 bytes, plus slack for padding and the line breaks a lenient decoder skips.
                if (payloadLength > maxBytes / 3 * 4 + 1024) return null
                runCatching { lenientBase64.decode(dataUrl, comma + 1, dataUrl.length) }.getOrNull() ?: return null
            } else {
                // A percent escape is 3 chars per byte, a literal char up to 4 UTF-8 bytes.
                if (payloadLength > maxBytes) return null
                percentDecode(dataUrl, comma + 1) ?: return null
            }
        if (bytes.size > maxBytes) return null
        return DataUrl(mime, bytes)
    }

    /** RFC 3986 percent-decoding of [text] from [start] (a `+` stays a `+`); null on a broken escape. */
    private fun percentDecode(
        text: String,
        start: Int,
    ): ByteArray? {
        val source = text.substring(start).encodeToByteArray()
        val out = ByteArray(source.size)
        var read = 0
        var written = 0
        while (read < source.size) {
            val b = source[read]
            if (b == '%'.code.toByte()) {
                if (read + 2 >= source.size) return null
                val hi = hexValue(source[read + 1])
                val lo = hexValue(source[read + 2])
                if (hi < 0 || lo < 0) return null
                out[written++] = ((hi shl 4) or lo).toByte()
                read += 3
            } else {
                out[written++] = b
                read++
            }
        }
        return out.copyOf(written)
    }

    private fun hexValue(b: Byte): Int =
        when (val c = b.toInt().toChar()) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
}

/**
 * How often one browser surface (a window, or one embedded tab) lets a site show a download card: after
 * the user answers a site's card, that site can't raise another for [window]. Without it a page that is
 * refused can re-ask in a loop, so Cancel would never make the card go away. Main-thread only.
 */
class DownloadCooldown(
    private val window: Duration = 1.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val answeredAt = HashMap<String, TimeMark>()

    /** Whether [origin] may show a card now. */
    fun allows(origin: String): Boolean = answeredAt[origin]?.let { it.elapsedNow() >= window } ?: true

    /** Starts [origin]'s cooldown: the user just answered (or the surface dismissed) its card. */
    fun answered(origin: String) {
        answeredAt[origin] = timeSource.markNow()
    }
}
