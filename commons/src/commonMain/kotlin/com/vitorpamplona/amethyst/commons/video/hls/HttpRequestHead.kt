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
package com.vitorpamplona.amethyst.commons.video.hls

/**
 * The request line and headers of an HTTP/1.x request (RFC 9112 §2-3), as far as a loopback
 * relay needs them. Header names are matched without case.
 */
class HttpRequestHead(
    val method: String,
    val target: String,
    private val headers: Map<String, String>,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    companion object {
        /** Parses the lines before the empty line, or null when they are not an HTTP/1.x request. */
        fun parse(lines: List<String>): HttpRequestHead? {
            val requestLine = lines.firstOrNull()?.split(' ') ?: return null
            if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/1.")) return null
            val (method, target) = requestLine
            if (method.isEmpty() || method.any { !it.isLetter() } || !target.startsWith("/")) return null
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) return null
                // The first of a repeated header: the relay only reads ones that do not repeat.
                headers.getOrPut(line.substring(0, colon).trim().lowercase()) { line.substring(colon + 1).trim() }
            }
            return HttpRequestHead(method, target, headers)
        }
    }
}
