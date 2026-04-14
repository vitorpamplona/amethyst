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
package com.vitorpamplona.amethyst.service.uploads.hls

object HlsPlaylistRewriter {
    private val uriRegex = Regex("""URI="([^"]+)"""")

    fun rewrite(
        playlist: String,
        urlMap: Map<String, String>,
    ): String =
        playlist.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> rewriteUriInDirective(line, urlMap)
                else -> urlMap[line] ?: missing(line)
            }
        }

    private fun rewriteUriInDirective(
        line: String,
        urlMap: Map<String, String>,
    ): String =
        uriRegex.replace(line) { match ->
            val original = match.groupValues[1]
            val rewritten = urlMap[original] ?: missing(original)
            """URI="$rewritten""""
        }

    private fun missing(reference: String): Nothing = throw IllegalArgumentException("No uploaded URL for playlist reference: $reference")
}
