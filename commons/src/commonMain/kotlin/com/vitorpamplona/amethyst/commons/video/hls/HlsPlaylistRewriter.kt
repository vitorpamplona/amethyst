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

import com.vitorpamplona.amethyst.commons.util.resolveHttpUrl
import com.vitorpamplona.amethyst.commons.video.isHlsMedia

/**
 * Rewrites an HLS playlist (RFC 8216) so every file it names is fetched through a relay: each
 * URI line (a segment, or a variant after `#EXT-X-STREAM-INF`) and each `URI="…"` attribute of a
 * tag (`#EXT-X-KEY`, `#EXT-X-MAP`, `#EXT-X-MEDIA`, `#EXT-X-I-FRAME-STREAM-INF`,
 * `#EXT-X-SESSION-KEY`, `#EXT-X-PART`, `#EXT-X-PRELOAD-HINT`, …) is resolved against the URL the
 * playlist really came from (after redirects, RFC 3986 §5) and handed to `relay`, whose answer
 * replaces it. So the player never resolves a URI itself and never fetches one around the relay.
 *
 * Everything else is kept byte for byte: comments, the other attributes (`BYTERANGE` ranges are
 * the player's to ask for), line endings. `data:` URIs carry their bytes inline and stay.
 */
object HlsPlaylistRewriter {
    /** A real playlist is a few KiB, a long live window a few hundred; past this it is not one. */
    const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024

    /** What a playlist the relay rewrote is served as. */
    const val PLAYLIST_CONTENT_TYPE = "application/vnd.apple.mpegurl"

    // An attribute list starts after the tag's colon; attributes are comma separated.
    private val uriAttribute = Regex("""([:,])URI="([^"]*)"""")

    private const val BYTE_ORDER_MARK = "\uFEFF"

    /**
     * [playlist] with every URI replaced by `relay(absolute URI)`, or null when one of them cannot
     * go through the relay (a scheme other than http(s) or `data:`, such as FairPlay's `skd://`):
     * the player would have to fetch that one itself, so the whole playlist is refused instead.
     */
    fun rewrite(
        playlist: String,
        base: String,
        relay: (absoluteUrl: String) -> String,
    ): String? {
        var refused = false

        fun route(uri: String): String {
            val trimmed = uri.trim()
            if (trimmed.startsWith("data:", ignoreCase = true)) return uri
            val absolute = resolveHttpUrl(base, trimmed)
            if (absolute == null) {
                refused = true
                return ""
            }
            return relay(absolute)
        }

        val rewritten =
            playlist.removePrefix(BYTE_ORDER_MARK).split('\n').joinToString("\n") { raw ->
                val carriageReturn = raw.endsWith('\r')
                val line = if (carriageReturn) raw.dropLast(1) else raw
                val out =
                    when {
                        line.isBlank() -> line
                        line.startsWith("#EXT") -> uriAttribute.replace(line) { match -> "${match.groupValues[1]}URI=\"${route(match.groupValues[2])}\"" }
                        // A comment.
                        line.startsWith("#") -> line
                        else -> route(line)
                    }
                if (carriageReturn) "$out\r" else out
            }
        return if (refused) null else rewritten
    }

    /** Whether a response is a playlist by its `Content-Type` or its URL's `.m3u8`. */
    fun looksLikePlaylist(
        url: String,
        contentType: String?,
    ): Boolean {
        // The header can carry parameters (`; charset=utf-8`) the shared mime check does not strip.
        val type = contentType?.lowercase().orEmpty()
        return "mpegurl" in type || isHlsMedia(url, null)
    }

    /** Whether [head], the first bytes of a body, start a playlist (`#EXTM3U`, maybe after a BOM). */
    fun startsPlaylist(head: ByteArray): Boolean {
        val start = if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) 3 else 0
        val tag = "#EXTM3U"
        if (head.size - start < tag.length) return false
        return tag.indices.all { head[start + it] == tag[it].code.toByte() }
    }
}
