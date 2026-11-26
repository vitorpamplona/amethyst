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

import kotlin.io.encoding.Base64

/**
 * The path of a loopback media relay address: `/<secret>/<stream>/<original>/<name>`.
 *
 * - `<secret>` is random per process, so the relay is no open proxy for other code that can reach
 *   127.0.0.1;
 * - `<stream>` names the playback the request belongs to: a stream's playlists hand it on to
 *   every file they name, so the relay can tell the player what failed;
 * - `<original>` is the whole original URL in unpadded base64url, so no character of it can trip a
 *   URI parser, and the player has nothing relative to resolve;
 * - `<name>` is the original's last path segment, for players that pick a demuxer by extension.
 */
object LoopbackRelayPath {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    private const val MAX_NAME = 100

    /** A request's target: the stream it belongs to and the URL to fetch. */
    data class Target(
        val stream: String,
        val original: String,
    )

    fun encode(
        secret: String,
        stream: String,
        original: String,
    ): String = "/$secret/$stream/${base64Url.encode(original.encodeToByteArray())}/${fileNameOf(original)}"

    /** The target of a request for [rawPath] (its query, if any, ignored), or null when it is not ours. */
    fun decode(
        secret: String,
        rawPath: String,
    ): Target? {
        val parts = rawPath.substringBefore('?').removePrefix("/").split('/')
        if (parts.size < 3 || parts[0] != secret) return null
        val stream = parts[1].takeIf { isToken(it) } ?: return null
        val original =
            try {
                base64Url.decode(parts[2]).decodeToString()
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (!original.startsWith("http://", ignoreCase = true) && !original.startsWith("https://", ignoreCase = true)) return null
        return Target(stream, original)
    }

    /** Whether [value] can be a `<secret>` or `<stream>`: 1 to 64 ASCII letters and digits. */
    fun isToken(value: String): Boolean = value.length in 1..64 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }

    /** The last path segment of [url], reduced to characters no URI parser objects to. */
    fun fileNameOf(url: String): String =
        url
            .substringBefore('#')
            .substringBefore('?')
            .substringAfter("://")
            .substringAfter('/', "")
            .substringAfterLast('/')
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }
            .takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
            ?: "media"
}
