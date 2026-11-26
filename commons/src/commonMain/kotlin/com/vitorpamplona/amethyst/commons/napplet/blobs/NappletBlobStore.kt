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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.normalizeStaticPath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer
import okio.use

/**
 * A napplet's or nSite's files: from the shared content-addressed [cache] first, and from its
 * Blossom servers through [http] on a miss. Every download is hashed and checked against the
 * event's sha256 before it is cached or returned, and the cache holds only bytes that hashed to
 * their name, so nothing but the signed files can come out of here.
 *
 * One per launch: [viaTor] is that launch's route (Tor or the open web), and [paths] are its
 * files, looked up through a map built once instead of a scan per request.
 */
class NappletBlobStore(
    private val cache: NappletBlobCache,
    private val http: NappletHttp,
    paths: List<PathTag>,
    private val servers: List<String>,
    private val viaTor: Boolean = false,
) : NappletFiles {
    /** One verified file: streamed from the cache when it is there, else held from its download. */
    class Blob(
        val path: String,
        val hash: String,
        val contentType: String,
        private val file: Path?,
        private val bytes: ByteArray?,
        private val fileSystem: FileSystem = platformFileSystem,
    ) {
        val length: Long get() = bytes?.size?.toLong() ?: file?.let { fileSystem.metadataOrNull(it)?.size } ?: 0L

        /** The bytes from the start. Throws when the cached file went away since it was found. */
        fun open(): Source = bytes?.let { Buffer().write(it) } ?: fileSystem.source(requireNotNull(file))

        fun readBytes(): ByteArray = bytes ?: fileSystem.read(requireNotNull(file)) { readByteArray() }
    }

    // canonical path ("index.html", "assets/app.js") -> its tag. The first tag for a path wins, as in
    // quartz's resolver, so both read a duplicated path the same way.
    private val byPath: Map<String, PathTag> =
        HashMap<String, PathTag>(paths.size).apply {
            paths.forEach {
                val key = it.path.removePrefix("./").removePrefix("/")
                if (key !in this) put(key, it)
            }
        }

    // A blob being downloaded: concurrent requests for it wait for the one download.
    private val inFlight = HashMap<String, CompletableDeferred<ByteArray?>>()
    private val inFlightLock = KmpLock()

    override fun isListed(requestPath: String): Boolean = lookup(requestPath) != null

    /**
     * The tag that serves [requestPath], as quartz's resolver matches it (`/` is `index.html`). Browsers
     * send paths percent-encoded (`/my%20file.js`), while the event lists them as written.
     */
    fun lookup(requestPath: String): PathTag? = byPath[normalizeStaticPath(decodePath(requestPath.substringBefore('?').substringBefore('#')))]

    /** Whether [requestPath] is cached already or can be fetched now, without reading it. */
    override suspend fun isAvailable(requestPath: String): Boolean {
        val match = lookup(requestPath) ?: return false
        return cache.file(match.hash) != null || download(match) != null
    }

    /** The verified file serving [requestPath], or null when the event doesn't list it or no server has it. */
    override suspend fun find(requestPath: String): Blob? {
        val match = lookup(requestPath) ?: return null
        cache.file(match.hash)?.let { return Blob(match.path, match.hash, contentTypeOf(match.path) { head(it) }, it, null, cache.fileSystem) }
        val bytes = download(match) ?: return null
        // Served from the file just written, so the downloaded array can go; held only if it couldn't be.
        val file = cache.file(match.hash)
        return Blob(match.path, match.hash, contentTypeOf(match.path) { bytes }, file, bytes.takeIf { file == null }, cache.fileSystem)
    }

    /** As [find], for callers that want quartz's resolution. */
    suspend fun resolve(requestPath: String): StaticSiteResolution {
        val match = lookup(requestPath) ?: return StaticSiteResolution.PathNotInManifest
        val blob = find(requestPath) ?: return StaticSiteResolution.Unresolvable(match.hash)
        return StaticSiteResolution.Resolved(blob.path, blob.hash, blob.contentType, blob.readBytes(), server = CACHE_SERVER)
    }

    private suspend fun download(match: PathTag): ByteArray? {
        val hash = match.hash.lowercase()
        val mine = CompletableDeferred<ByteArray?>()
        val running = inFlightLock.withLock { inFlight[hash].also { if (it == null) inFlight[hash] = mine } }
        if (running != null) return running.await()
        // Other requests wait on this download, so it finishes even if the request that started it
        // goes away: the verified bytes are cached and handed to the waiters inside the block, since
        // withContext drops its result on the way back to a cancelled caller. A failure is a miss
        // for all of them, never an exception that reaches the browser's thread.
        return withContext(NonCancellable + Dispatchers.IO) {
            val bytes =
                try {
                    StaticSiteResolver.candidateUrls(servers, hash).firstNotNullOfOrNull { url ->
                        fetch(url)?.takeIf { StaticSiteResolver.verify(it, hash) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Downloading $hash failed", e)
                    null
                }
            try {
                if (bytes != null) cache.putVerified(hash, bytes)
            } finally {
                mine.complete(bytes)
                inFlightLock.withLock { if (inFlight[hash] === mine) inFlight.remove(hash) }
            }
            bytes
        }
    }

    // One server's copy, or null when it has none, it was too big, or the fetch failed: the next
    // server is tried, never an exception that reaches the browser's thread.
    private suspend fun fetch(url: String): ByteArray? =
        try {
            http
                .get(NappletHttpRequest(url, viaTor, MAX_BLOB_BYTES, BLOB_CALL_TIMEOUT_MS))
                .takeIf { it.isSuccessful }
                ?.body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Blob fetch failed for $url", e)
            null
        }

    /** The extension's type; only a file the extension says nothing about is sniffed, from its first bytes. */
    private inline fun contentTypeOf(
        path: String,
        firstBytes: () -> ByteArray,
    ): String {
        val byExtension = guessStaticContentType(path)
        return if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(firstBytes()) ?: byExtension else byExtension
    }

    private fun head(file: Path): ByteArray =
        runCatching {
            cache.fileSystem.source(file).buffer().use { input ->
                input.request(SNIFF_BYTES.toLong())
                input.buffer.readByteArray(minOf(input.buffer.size, SNIFF_BYTES.toLong()))
            }
        }.getOrDefault(ByteArray(0))

    companion object {
        /** Marker "server" for a blob served from the local cache. */
        const val CACHE_SERVER = "cache"

        /** The largest file a launch downloads. */
        const val MAX_BLOB_BYTES = 20L * 1024 * 1024

        // Bounds a single blob fetch end-to-end: a stalled Tor exit (which trickles bytes under a read
        // timeout) must not block the browser's thread that asked for it indefinitely.
        const val BLOB_CALL_TIMEOUT_MS = 45_000L

        // Enough for every signature quartz's sniffer knows.
        private const val SNIFF_BYTES = 64

        private const val TAG = "NappletBlobStore"

        /**
         * Decodes a URL path's percent escapes as UTF-8. `+` stays a plus: that rule is for query
         * strings. A malformed escape leaves the path as it came.
         */
        fun decodePath(path: String): String {
            if ('%' !in path) return path
            val out = Buffer()
            var i = 0
            while (i < path.length) {
                val c = path[i]
                if (c == '%') {
                    if (i + 2 >= path.length) return path
                    val value = path.substring(i + 1, i + 3).toIntOrNull(16) ?: return path
                    out.writeByte(value)
                    i += 3
                } else {
                    out.writeUtf8(c.toString())
                    i++
                }
            }
            return out.readByteArray().decodeToString()
        }

        /** The sha256 a Blossom URL names: its last segment, without an extension. */
        fun blobHashOf(url: String): String =
            url
                .substringAfterLast('/')
                .substringBefore('.')
                .lowercase()
    }
}
