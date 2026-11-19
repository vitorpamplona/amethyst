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
package com.vitorpamplona.amethyst.commons.service.http

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Who posted each Blossom blob the app has shown, so a download that fails on the server a note
 * names can be retried on the author's other servers (BUD-03 kind 10063 list).
 *
 * A plain `https://host/<sha256>.jpg` names one server. When that one has lost the file the image
 * failed for good, even though the author's list usually holds mirrors that still serve it. The
 * image loader only sees a URL, so the parser files the note's author here under the blob's sha256
 * (the hash names the bytes on any server) and the loader reads it back on failure.
 *
 * Global, like the parser cache that fills it. Losing an entry to eviction only means the retry
 * asks the viewer's own servers alone.
 */
object BlossomAuthorHints {
    private val authorsByHash = LruCache<String, Set<HexKey>>(MAX_BLOBS)
    private val lock = KmpLock()

    /** Files [author] as a poster of the blob [url] names; ignored when [url] is not a Blossom blob. */
    fun add(
        url: String,
        author: HexKey,
    ) {
        val sha256 = BlossomBlobName.parse(url)?.sha256 ?: return
        // The parser runs on the feed prefetch and in composition at once: without the lock, two
        // authors of the same blob can each read "none yet" and one of them is lost.
        lock.withLock {
            val known = authorsByHash[sha256]
            if (known == null) {
                authorsByHash.put(sha256, setOf(author))
            } else if (author !in known && known.size < MAX_AUTHORS_PER_BLOB) {
                authorsByHash.put(sha256, known + author)
            }
        }
    }

    /** The authors known to have posted the blob with this [sha256], empty when none is. */
    fun authorsOf(sha256: String): Set<HexKey> = authorsByHash[sha256.lowercase()] ?: emptySet()

    private const val MAX_BLOBS = 1000
    private const val MAX_AUTHORS_PER_BLOB = 4
}

/**
 * A Blossom blob name (BUD-01): the last path segment of an http(s) URL that is exactly
 * `<sha256>` or `<sha256>.<ext>`. A filename that merely embeds a hash is not one.
 */
class BlossomBlobName(
    /** Lowercase hex. */
    val sha256: String,
    /** Without the dot, lowercase; null when the name has none. */
    val extension: String?,
) {
    companion object {
        private val BLOB_NAME = Regex("^([0-9a-fA-F]{64})(?:\\.([0-9A-Za-z]{1,8}))?$")

        fun parse(url: String): BlossomBlobName? {
            if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return null
            val path = url.substringBefore('#').substringBefore('?')
            // A bare host has no path segment to read.
            if (path.indexOf('/', path.indexOf("//") + 2) < 0) return null
            val match = BLOB_NAME.matchEntire(path.substringAfterLast('/')) ?: return null
            return BlossomBlobName(
                sha256 = match.groupValues[1].lowercase(),
                extension = match.groupValues[2].ifEmpty { null }?.lowercase(),
            )
        }
    }
}
