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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class UrlPreview {
    suspend fun fetch(
        url: String,
        transport: UrlPreviewTransport,
        onComplete: suspend (urlInfo: UrlInfoItem) -> Unit,
        onFailed: suspend (t: Throwable) -> Unit,
    ) = try {
        onComplete(getDocument(url, transport))
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        onFailed(t)
    }

    suspend fun getDocument(
        url: String,
        transport: UrlPreviewTransport,
    ): UrlInfoItem =
        withContext(Dispatchers.IO) {
            transport.get(url) { response ->
                if (response.code in 200..299) {
                    val mimeType =
                        response.contentType?.let { PreviewMediaType.parse(it) }
                            ?: throw IllegalArgumentException("Website returned unknown mimetype: ${response.contentType}")
                    when {
                        mimeType.type == "text" && mimeType.subtype == "html" -> {
                            val metaTags = HtmlParser().parseHtml(response.body.readPrefix(MAX_PREVIEW_BYTES), mimeType.charsetName())
                            val data = OpenGraphParser().extractUrlInfo(metaTags)
                            UrlInfoItem(
                                url,
                                data.title,
                                data.description,
                                data.image,
                                mimeType.toString(),
                                data.audio,
                                data.audioType,
                                data.video,
                                data.videoType,
                                data.type,
                                data.icon,
                                data.siteName,
                                data.author,
                            )
                        }

                        mimeType.type == "image" -> {
                            UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                        }

                        mimeType.type == "video" -> {
                            UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                        }

                        // A URL with no audio extension that turns out to serve audio anyway
                        // (`/download?id=7`) used to land in the `else` below and throw, losing
                        // the note's link entirely. The renderer plays it off the MIME. `image`
                        // carries the URL for the same reason the video branch above does: it
                        // is what UrlInfoItem.fetchComplete reads to decide the fetch produced
                        // something renderable.
                        mimeType.type == "audio" -> {
                            UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                        }

                        mimeType.type == "application" && mimeType.subtype == "pdf" -> {
                            UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                        }

                        else -> {
                            throw IllegalArgumentException("Website returned unknown encoding for previews: $mimeType")
                        }
                    }
                } else {
                    throw IllegalArgumentException("Website returned: " + response.code)
                }
            }
        }
}

/**
 * Link previews only need the `<head>` (MetaTagsParser stops at `</head>`), and
 * the URL is attacker-controlled: any note can point at a host that streams
 * `text/html` forever. Read until the head closes, and never past [max] bytes,
 * instead of the whole body. Stopping at the head matters on slow links: a GitHub page
 * is ~500 KB but closes its head ~34 KB in, and the card's image only starts
 * loading once this returns.
 */
internal suspend fun UrlPreviewBody.readPrefix(max: Int): ByteArray {
    var buffer = ByteArray(minOf(max, INITIAL_BUFFER_BYTES))
    var size = 0
    var searched = 0
    while (size < max) {
        if (size == buffer.size) buffer = buffer.copyOf(minOf(max, buffer.size * 2))
        val read = read(buffer, size, minOf(buffer.size - size, READ_STEP_BYTES))
        if (read < 0) break
        if (read == 0) continue
        size += read
        // Back up by the marker's length, so one split across two reads is still found.
        val from = maxOf(0, searched - HEAD_CLOSE.length)
        searched = size
        if (closesHead(buffer, size, from)) break
    }
    return buffer.copyOf(size)
}

/**
 * Whether the first [size] bytes of [buffer] have the head's end, looking for a new one from [from].
 * The bytes are only a cheap first test: they also match a `</head>` inside a script or a comment,
 * which the parser skips, and stopping there would cut off the meta tags still to come. Tags are
 * ASCII, so Latin-1 keeps them.
 */
private fun closesHead(
    buffer: ByteArray,
    size: Int,
    from: Int,
): Boolean {
    if (!buffer.hasHeadClose(size, from)) return false
    return MetaTagsParser.reachesHeadEnd(buffer.latin1(size))
}

/** Whether `</head` appears, in either case the old OkHttp check looked for, in `[from, size)`. */
private fun ByteArray.hasHeadClose(
    size: Int,
    from: Int,
): Boolean {
    val last = size - HEAD_CLOSE.length
    var i = from
    while (i <= last) {
        if (matchesAt(i, HEAD_CLOSE) || matchesAt(i, HEAD_CLOSE_UPPER)) return true
        i++
    }
    return false
}

private fun ByteArray.matchesAt(
    index: Int,
    marker: String,
): Boolean {
    for (j in marker.indices) {
        if (this[index + j].toInt() != marker[j].code) return false
    }
    return true
}

private fun ByteArray.latin1(size: Int): String {
    val chars = CharArray(size)
    for (i in 0 until size) chars[i] = (this[i].toInt() and 0xFF).toChar()
    return chars.concatToString()
}

private const val HEAD_CLOSE = "</head"
private const val HEAD_CLOSE_UPPER = "</HEAD"

private const val READ_STEP_BYTES = 16 * 1024
private const val INITIAL_BUFFER_BYTES = 64 * 1024

// Large enough for real `<head>`s, not just typical ones. YouTube's watch page front-loads its
// head with inline script: youtu.be/fwcKTYvupJw (2026-09) is 1.29 MB, and its og: block starts
// 708,776 bytes in, before `</head>` at 716,956. The earlier 512 KB cut it off, so every YouTube
// link lost its card. 2 MB leaves headroom for that head to grow while still bounding a body
// that never ends.
internal const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024
