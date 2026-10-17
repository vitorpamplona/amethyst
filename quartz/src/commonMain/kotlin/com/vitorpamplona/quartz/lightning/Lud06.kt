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
package com.vitorpamplona.quartz.lightning

import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.utils.Log

class Lud06 {
    companion object {
        /**
         * Longest LNURL we decode. An lnurlp URL is a host plus a short path, real ones are well
         * under 200 chars, and this runs on every kind 0 ingest (`convertLud06toLud16IfNeeded`),
         * so a hostile `lud06` must cost no more than a short scan.
         */
        const val MAX_URL_LENGTH = 2_000

        /** Bech32 length of a [MAX_URL_LENGTH]-byte payload: hrp, separator, 8/5 data, checksum. */
        private const val MAX_BECH32_LENGTH = 6 + (MAX_URL_LENGTH * 8 + 4) / 5 + 6

        private const val LNURLP_MARKER = "/.well-known/lnurlp/"

        // What `.` refuses to cross in a java.util.regex pattern without DOTALL / UNIX_LINES.
        private fun isLineTerminator(c: Char) = c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' '

        private fun startsWithIgnoreCase(
            str: String,
            at: Int,
            prefix: String,
        ) = str.regionMatches(at, prefix, 0, prefix.length, ignoreCase = true)
    }

    fun toLud16(str: String): String? {
        return try {
            val url = toLnUrlp(str) ?: return null
            lnurlpToLud16(url)
        } catch (t: Throwable) {
            Log.w("Lud06ToLud16", "Fail to convert LUD06 to LUD16", t)
            null
        }
    }

    /**
     * `scheme://<domain>/.well-known/lnurlp/<username>` → `username@domain`, or null when [url]
     * is not an lnurlp URL.
     *
     * This replaced the regex `(?i:http|https)://((.+)/)*\.well-known/lnurlp/(.*)`, whose nested
     * `((.+)/)*` backtracks through the path's slashes: on the JVM, `"https://" + "a/" * n` with
     * no marker grew quadratically (n=4,000: 250 ms; n=20,000: 4.7 s), once per kind 0 ingested
     * (`convertLud06toLud16IfNeeded`). The scan gives the
     * regex's answer for every lnurlp URL: the domain is everything between `://` and the LAST
     * marker on that line (greedy `.+`, so never empty), the username the rest of the line
     * (`.*`), and `http`/`https` match case-insensitively while the marker does not.
     */
    fun lnurlpToLud16(url: String): String? {
        if (url.length > MAX_URL_LENGTH) return null

        // `find` takes the leftmost scheme that leads to a match, so try each one in order.
        for (i in url.indices) {
            val schemeEnd =
                when {
                    startsWithIgnoreCase(url, i, "http://") -> i + 7
                    startsWithIgnoreCase(url, i, "https://") -> i + 8
                    else -> continue
                }

            var lineEnd = schemeEnd
            while (lineEnd < url.length && !isLineTerminator(url[lineEnd])) lineEnd++

            val line = url.substring(schemeEnd, lineEnd)
            val marker = line.lastIndexOf(LNURLP_MARKER)
            if (marker > 0) {
                return line.substring(marker + LNURLP_MARKER.length) + "@" + line.substring(0, marker)
            }
        }
        return null
    }

    fun toLnUrlp(str: String): String? {
        if (str.length > MAX_BECH32_LENGTH) return null
        return try {
            Bech32.decodeBytes(str, false).second.decodeToString()
        } catch (t: Throwable) {
            Log.w("Lud06ToLud16", "Fail to convert LUD06 to LUD16", t)
            null
        }
    }
}
