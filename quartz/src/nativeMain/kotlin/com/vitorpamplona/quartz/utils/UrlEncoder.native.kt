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
package com.vitorpamplona.quartz.utils

/**
 * Native actual for [UrlEncoder], shared by linuxX64 and every Apple target.
 *
 * ## Why this is not a library call any more
 *
 * Both native targets used to delegate to `net.thauvin.erik.urlencoder.UrlEncoderUtil`,
 * which implements RFC 3986 percent-encoding. The JVM/Android actual is
 * `java.net.URLEncoder`/`URLDecoder`, which implements
 * `application/x-www-form-urlencoded`. Those are different specifications, and the
 * difference was observable in three places:
 *
 * ```
 *                        JVM/Android      UrlEncoderUtil
 *   encode(" ")          "+"              "%20"
 *   encode("*")          "*"              "%2A"
 *   decode("a+b")        "a b"            "a+b"
 * ```
 *
 * That is not cosmetic. [encode] builds strings that leave the device — `TorrentEvent`
 * puts it in magnet links, `Nip54InlineMetadata` in inline metadata, `Nip47DeepLink` in
 * the `callback`, `appname` and `value` parameters of NWC deep links — so Android and
 * iOS were emitting different bytes for the same title. The decode row is worse than
 * cosmetic: a magnet link or deep link written by Android carries `+` for its spaces,
 * and reading it on iOS produced a string with literal plus signs instead of spaces, no
 * error anywhere.
 *
 * So this matches `URLEncoder`/`URLDecoder` exactly instead: the unreserved set is
 * alphanumerics plus `-`, `_`, `.` and `*` (note `*` survives and `~` does not — the
 * opposite of RFC 3986), space encodes to `+`, everything else to uppercase `%XX` of
 * its UTF-8 bytes, and decoding maps `+` back to a space. `UrlEncoderTest` in
 * `commonTest` pins all of it against the JVM on every target.
 *
 * Both directions short-circuit the way the `java.net` pair does: a string with nothing
 * to change is returned as-is rather than rebuilt.
 *
 * One deliberate edge difference: an *unpaired* UTF-16 surrogate encodes as `%EF%BF%BD`
 * (Kotlin's replacement character) where the JVM gives `%3F`. Nostr content is
 * well-formed UTF-16, and chasing it would cost a scan on every call.
 */
actual object UrlEncoder {
    private const val HEX = "0123456789ABCDEF"

    actual fun encode(value: String): String {
        var index = 0
        while (index < value.length && isUnreserved(value[index])) index++
        if (index == value.length) return value

        val result = StringBuilder(value.length + ESCAPE_HEADROOM)
        result.append(value, 0, index)

        while (index < value.length) {
            val char = value[index]
            when {
                isUnreserved(char) -> {
                    result.append(char)
                    index++
                }

                char == ' ' -> {
                    result.append('+')
                    index++
                }

                else -> {
                    // Escaped as a run rather than character by character, so a surrogate
                    // pair becomes one 4-byte sequence instead of two malformed 3-byte ones.
                    val start = index
                    do {
                        index++
                    } while (index < value.length && !isUnreserved(value[index]) && value[index] != ' ')
                    appendEscaped(result, value, start, index)
                }
            }
        }

        return result.toString()
    }

    actual fun decode(value: String): String {
        if (value.indexOf('%') < 0 && value.indexOf('+') < 0) return value

        val result = StringBuilder(value.length)
        var index = 0
        // Sized on first use for the longest run that could still follow, then reused —
        // one allocation for the whole string, as in URLDecoder.
        var escaped: ByteArray? = null

        while (index < value.length) {
            when (val char = value[index]) {
                '+' -> {
                    result.append(' ')
                    index++
                }

                '%' -> {
                    val buffer = escaped ?: ByteArray((value.length - index) / 3).also { escaped = it }
                    var count = 0
                    while (index + 2 < value.length && value[index] == '%') {
                        buffer[count++] = decodeEscape(value, index)
                        index += 3
                    }
                    if (index < value.length && value[index] == '%') {
                        throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
                    }
                    // Decoded as a run so a multi-byte UTF-8 sequence survives.
                    result.append(buffer.decodeToString(0, count))
                }

                else -> {
                    result.append(char)
                    index++
                }
            }
        }

        return result.toString()
    }

    /** `URLEncoder`'s `dontNeedEncoding` set: alphanumerics plus these four, and only these. */
    private fun isUnreserved(char: Char): Boolean =
        char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '-' ||
            char == '_' ||
            char == '.' ||
            char == '*'

    private fun appendEscaped(
        result: StringBuilder,
        value: String,
        start: Int,
        end: Int,
    ) {
        val bytes = value.substring(start, end).encodeToByteArray()
        for (byte in bytes) {
            val code = byte.toInt()
            result.append('%')
            result.append(HEX[(code shr 4) and 0xF])
            result.append(HEX[code and 0xF])
        }
    }

    private fun decodeEscape(
        value: String,
        index: Int,
    ): Byte {
        val high = hexDigit(value[index + 1])
        val low = hexDigit(value[index + 2])
        if (high < 0 || low < 0) {
            throw IllegalArgumentException(
                "URLDecoder: Illegal hex characters in escape (%) pattern - ${value.substring(index, index + 3)}",
            )
        }
        return ((high shl 4) or low).toByte()
    }

    private fun hexDigit(char: Char): Int =
        when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> -1
        }

    /** Enough for a handful of escapes before the builder has to grow. */
    private const val ESCAPE_HEADROOM = 16
}
