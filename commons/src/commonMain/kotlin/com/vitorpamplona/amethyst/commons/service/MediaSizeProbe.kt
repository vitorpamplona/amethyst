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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.audio.MediaTrackProbeCache
import com.vitorpamplona.amethyst.commons.audio.mayHoldVideo
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** Which of the app's HTTP clients a size probe goes through, so it follows that media's Tor routing. */
enum class MediaSizeRole {
    IMAGE,
    VIDEO,
    PREVIEW,
}

/**
 * File sizes learned without downloading the file, by URL: from a `HEAD` or from the MP4 track
 * probe's range answer. Remembers misses too, so a host that won't say isn't asked again. Copy-on-write
 * over a [MutableStateFlow] like [MediaTrackProbeCache], safe to read from composition and write from
 * IO threads.
 */
object MediaSizeCache {
    private const val MAX_ENTRIES = 500
    private const val MISS = -1L
    private val state = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Every answer by URL; a miss is a negative value. Observe one URL with `map { it[url] }`. */
    val entries: StateFlow<Map<String, Long>> = state

    /** The size of [url], or null when it is unknown or the host wouldn't say. */
    fun get(url: String): Long? = state.value[url]?.takeIf { it > 0 }

    /** Whether [url] has been asked about, whatever the answer was. */
    fun isAnswered(url: String): Boolean = state.value.containsKey(url)

    /** Records [size] for [url]; null records a miss. */
    fun put(
        url: String,
        size: Long?,
    ) {
        val stored = size?.takeIf { it > 0 } ?: MISS
        // The track probe re-reports a size it already gave: don't copy the map for nothing.
        if (state.value[url] == stored) return
        state.update { current ->
            val next = LinkedHashMap(current)
            next.remove(url)
            next[url] = stored
            while (next.size > MAX_ENTRIES) next.remove(next.keys.first())
            next
        }
    }

    internal fun clear() {
        state.value = emptyMap()
    }
}

/**
 * The size of [url] for a placeholder, without downloading it. When the MP4 track probe may be about to
 * read the file it is given up to [trackProbeWaitMs] to record the size from its own range answer; past
 * that, or for any other file, it falls back to [probeMediaSize]. The wait is bounded because gallery
 * tiles, cover images and a probe cancelled by scrolling never record an answer.
 */
suspend fun IRoleBasedHttpClientBuilder.resolveMediaSize(
    url: String,
    mimeType: String?,
    role: MediaSizeRole,
    trackProbeWaitMs: Long = 2_000,
): Long? {
    if (MediaSizeCache.isAnswered(url)) return MediaSizeCache.get(url)
    if (mayHoldVideo(mimeType, url) && MediaTrackProbeCache.get(url) == null) {
        withTimeoutOrNull(trackProbeWaitMs) { MediaTrackProbeCache.answers.map { it[url] }.first { it != null } }
    }
    // Answers from the cache when the track probe recorded a size meanwhile.
    return probeMediaSize(url, mimeType, role)
}

/**
 * Asks the host for [url]'s size with a `HEAD`, once per URL. Only `http(s)` files are asked: a
 * `blossom:` URI isn't an HTTP address, and an HLS playlist's length says nothing about the stream.
 * A cancelled request records nothing, so the size can still arrive when the placeholder comes back.
 */
suspend fun IRoleBasedHttpClientBuilder.probeMediaSize(
    url: String,
    mimeType: String?,
    role: MediaSizeRole,
): Long? {
    if (MediaSizeCache.isAnswered(url)) return MediaSizeCache.get(url)
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    if (isHlsMedia(url, mimeType)) return null
    val size = headContentLength(url, role)
    MediaSizeCache.put(url, size)
    return size
}

/** A 2xx `HEAD` answer's positive, unencoded `Content-Length`, or null. iOS has no HTTP client yet. */
internal expect suspend fun IRoleBasedHttpClientBuilder.headContentLength(
    url: String,
    role: MediaSizeRole,
): Long?

/** The total in a `Content-Range` header (`bytes 0-1023/12400000`), or null when it is unknown or absent. */
fun parseContentRangeTotal(header: String?): Long? =
    header
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
