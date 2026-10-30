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
 * whole: it is unpacked beside it and renamed into place, so a crash mid-way leaves nothing served.
 */
class WebxdcBundles(
    private val root: File,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * The unpacked archive at [url] (sha256 [hash] when known), downloaded through [client] from it
     * or from [servers] when it isn't here yet. Null when no copy could be had or it isn't a webxdc
     * app (no `index.html`).
     */
    suspend fun open(
        url: String,
        hash: HexKey?,
        servers: List<String>,
        client: OkHttpClient,
    ): File? =
        withContext(Dispatchers.IO) {
            val key = hash?.lowercase() ?: ("u" + sha256(url.encodeToByteArray()).toHexKey())
            val dir = File(root, key)
            if (File(dir, COMPLETE).isFile) return@withContext dir
            locks.getOrPut(key) { Mutex() }.withLock {
                if (File(dir, COMPLETE).isFile) return@withLock dir
                if (!root.isDirectory && !root.mkdirs()) {
                    Log.w(TAG) { "Could not create $root" }
                    return@withLock null
                }
                val archive = File(root, "$key.download")
                val staging = File(root, "$key.unpacking")
                try {
                    staging.deleteRecursively()
                    val candidates = listOf(url) + (hash?.let { StaticSiteResolver.candidateUrls(servers, it) } ?: emptyList())
                    if (WebxdcArchive.download(client, candidates, hash, archive) == null) return@withLock null
                    if (!staging.mkdirs() || !WebxdcArchive.unpack(archive, staging)) return@withLock null
                    if (!File(staging, INDEX).isFile) {
                        Log.w(TAG) { "$url has no $INDEX" }
                        return@withLock null
                    }
                    if (!File(staging, COMPLETE).createNewFile()) return@withLock null
                    if (dir.exists()) dir.deleteRecursively()
                    if (!staging.renameTo(dir)) {
                        Log.w(TAG) { "Could not move $staging into place" }
                        return@withLock null
                    }
                    dir
                } finally {
                    if (archive.exists() && !archive.delete()) Log.w(TAG) { "Could not delete $archive" }
                    if (staging.exists()) staging.deleteRecursively()
                }
            }
        }

    companion object {
        /** Written last into a finished unpack; a directory without it is never served. */
        const val COMPLETE = ".amethyst-complete"
        const val INDEX = "index.html"
        private const val TAG = "WebxdcBundles"

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

    // The unpacked archive, once had: every request after the first is a file lookup.
    @Volatile
    private var dir: File? = null
    private val opening = Mutex()

    private suspend fun dir(): File? {
        dir?.let { return it }
        return opening.withLock { dir ?: bundles.open(url, hash, servers, client)?.also { dir = it } }
    }

    override suspend fun isAvailable(requestPath: String): Boolean = find(requestPath) != null

    override suspend fun find(requestPath: String): NappletBlobStore.Blob? {
        val path = normalizeStaticPath(NappletBlobStore.decodePath(requestPath.substringBefore('?').substringBefore('#')))
        if (path == RUNTIME_PATH) return NappletBlobStore.Blob("/$RUNTIME_PATH", "", "text/javascript", null, runtime)
        val root = dir() ?: return null
        val relative = WebxdcArchive.entryPath(path) ?: return null
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith(root.canonicalPath + File.separator) || !file.isFile || file.name == WebxdcBundles.COMPLETE) return null
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
