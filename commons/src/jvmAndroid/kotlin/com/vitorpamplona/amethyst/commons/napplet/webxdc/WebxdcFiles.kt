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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.normalizeStaticPath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Unpacked webxdc archives on disk, one directory each under [root]: `<sha256>/` for an archive the
 * post names by hash, `u<sha256 of its URL>/` for one it doesn't. A directory is only ever there
 * whole: it is unpacked beside it under a name of its own and renamed into place, so a crash or a
 * second launch mid-way never leaves a partial one served.
 */
class WebxdcBundles(
    root: File,
) {
    private val root = root.absoluteFile

    /**
     * The unpacked archive at [url] (sha256 [hash] when known), downloaded through [client] from it
     * or from [servers] when it isn't here yet, telling [onProgress] how far that download is. Null
     * when no copy could be had, it isn't a webxdc app (no `index.html`), or [cancelled] said to stop.
     */
    suspend fun open(
        url: String,
        hash: HexKey?,
        servers: List<String>,
        client: OkHttpClient,
        cancelled: () -> Boolean = { false },
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
    ): File? =
        withContext(Dispatchers.IO) {
            val key = hash?.lowercase() ?: ("u" + sha256(url.encodeToByteArray()).toHexKey())
            val dir = File(root, key)
            if (File(dir, COMPLETE).isFile) return@withContext dir
            // Process-wide: every launch of one archive (a note's and a window's) shares its files.
            locks.getOrPut(dir.path) { Mutex() }.withLock {
                if (File(dir, COMPLETE).isFile) return@withLock dir
                if (!root.isDirectory && !root.mkdirs()) {
                    Log.w(TAG) { "Could not create $root" }
                    return@withLock null
                }
                val archive = File.createTempFile("$key.", ".download", root)
                val staging = File.createTempFile("$key.", ".unpacking", root)
                try {
                    if (!staging.delete() || !staging.mkdirs()) return@withLock null
                    val candidates = listOf(url) + (hash?.let { StaticSiteResolver.candidateUrls(servers, it) } ?: emptyList())
                    if (WebxdcArchive.download(client, candidates, hash, archive, cancelled, onProgress) == null) return@withLock null
                    if (!WebxdcArchive.unpack(archive, staging)) return@withLock null
                    if (!File(staging, INDEX).isFile) {
                        Log.w(TAG) { "$url has no $INDEX" }
                        return@withLock null
                    }
                    // An archive may ship a file by the marker's name; the marker is ours alone.
                    val marker = File(staging, COMPLETE)
                    if (marker.exists() && !marker.deleteRecursively()) return@withLock null
                    if (!marker.createNewFile()) return@withLock null
                    // Only an unfinished copy can be here: a finished one returned above.
                    if (dir.exists() && !dir.deleteRecursively()) Log.w(TAG) { "Could not clear $dir" }
                    if (!staging.renameTo(dir)) {
                        Log.w(TAG) { "Could not move $staging into place" }
                        return@withLock null
                    }
                    dir
                } finally {
                    if (archive.exists() && !archive.delete()) Log.w(TAG) { "Could not delete $archive" }
                    if (staging.exists() && !staging.deleteRecursively()) Log.w(TAG) { "Could not delete $staging" }
                }
            }
        }

    companion object {
        /** Written last into a finished unpack; a directory without it is never served. */
        const val COMPLETE = ".amethyst-complete"
        const val INDEX = "index.html"
        private const val TAG = "WebxdcBundles"

        // By archive directory, for every WebxdcBundles in the process.
        private val locks = ConcurrentHashMap<String, Mutex>()

        fun dirFor(cacheDir: File) = File(cacheDir, "webxdc-apps")
    }
}

/**
 * What one webxdc launch serves on its origin: the files of its unpacked archive, and the
 * messenger's `webxdc.js`, which the spec says the app loads but never ships.
 */
class WebxdcFiles(
    private val bundles: WebxdcBundles,
    private val client: OkHttpClient,
    private val url: String,
    private val hash: HexKey?,
    private val servers: List<String>,
    runtimeJs: String,
) : NappletFiles {
    private val runtime = runtimeJs.encodeToByteArray()

    // The unpacked archive (and its canonical path), once had: every request after the first is a
    // file lookup.
    @Volatile
    private var dir: Pair<File, String>? = null
    private val opening = Mutex()

    // A miss is remembered: a page's every request would otherwise download the whole archive again.
    // Retry builds a new launch.
    @Volatile
    private var failed = false

    @Volatile
    private var closed = false

    private val downloading = MutableStateFlow<NappletDownloadProgress?>(null)

    override val download: StateFlow<NappletDownloadProgress?> = downloading.asStateFlow()

    private suspend fun dir(): Pair<File, String>? {
        dir?.let { return it }
        if (failed || closed) return null
        return opening.withLock {
            dir ?: if (failed || closed) {
                null
            } else {
                val opened =
                    try {
                        bundles.open(url, hash, servers, client, { closed }, ::onProgress)
                    } finally {
                        downloading.value = null
                    }
                if (opened == null) {
                    if (!closed) failed = true
                    null
                } else {
                    val canonical = opened.canonicalFile
                    (canonical to canonical.path + File.separator).also { dir = it }
                }
            }
        }
    }

    override fun close() {
        closed = true
    }

    @Volatile
    private var lastShownAt = 0L

    // Every 64 KB read is a call, and on Android each update crosses processes: a bar needs about ten
    // a second, whatever the archive's size or whether the server gave it.
    private fun onProgress(
        bytes: Long,
        total: Long?,
    ) {
        val shown = downloading.value
        val now = System.nanoTime()
        if (shown == null || shown.total != total || bytes < shown.bytes || bytes == total || now - lastShownAt >= PROGRESS_INTERVAL_NANOS) {
            lastShownAt = now
            downloading.value = NappletDownloadProgress(bytes, total)
        }
    }

    override suspend fun isAvailable(requestPath: String): Boolean = find(requestPath) != null

    override suspend fun find(requestPath: String): NappletBlobStore.Blob? {
        val path = normalizeStaticPath(NappletBlobStore.decodePath(requestPath.substringBefore('?').substringBefore('#')))
        if (path == RUNTIME_PATH) return NappletBlobStore.Blob("/$RUNTIME_PATH", "", "text/javascript", null, runtime)
        val (root, rootPrefix) = dir() ?: return null
        val relative = WebxdcArchive.entryPath(path) ?: return null
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith(rootPrefix) || !file.isFile || relative == WebxdcBundles.COMPLETE) return null
        val byExtension = guessStaticContentType(relative)
        val type = if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(head(file)) ?: byExtension else byExtension
        return NappletBlobStore.Blob("/$relative", hash.orEmpty(), type, file, null)
    }

    // Every file is in the archive or it isn't: a miss is a 404, never an error.
    override fun isListed(requestPath: String): Boolean = false

    private fun head(file: File): ByteArray =
        file.inputStream().use { input ->
            val buffer = ByteArray(64)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }

    companion object {
        const val RUNTIME_PATH = "webxdc.js"

        private const val PROGRESS_INTERVAL_NANOS = 100_000_000L

        /**
         * [client], given the time a large archive needs: the blob client gives a call 45 s, and a
         * 178 MB Half-Life takes minutes over Tor. Shares [client]'s route, pool and threads.
         */
        fun downloadClient(client: OkHttpClient): OkHttpClient =
            client
                .newBuilder()
                .callTimeout(15, TimeUnit.MINUTES)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}
