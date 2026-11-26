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

import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpRequest
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSink
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import kotlin.random.Random

/**
 * Unpacked webxdc archives under [root], for a platform without the JVM's (iOS): fetched through the
 * launch's [NappletHttp] on its route ([viaTor]), checked against the hash the post names, then
 * unpacked with okio's zip reader under [WebxdcArchiveRules]' limits. The layout is the JVM's
 * `WebxdcBundles`: `<sha256>/` (or `u<sha256 of the URL>/`), unpacked beside it and moved into place
 * with its [WebxdcArchiveRules.COMPLETE] marker, so a partial one is never served.
 *
 * - The archive streams to disk as it comes ([NappletHttp.download]), hashed on the way, up to
 *   [WebxdcArchiveRules.MAX_ARCHIVE_BYTES], and its progress follows it (an HTTP stack that can't
 *   stream reads it whole first, and reports once).
 * - Nothing here trims: [root] belongs in a cache directory the system purges (iOS's Caches).
 * - okio's zip reader names every entry inside the archive's own root, so an entry that climbs out
 *   (`../../x`) is written as `x` inside the app, where the JVM's reader skips it. Neither lets one
 *   leave the app's directory.
 */
class OkioWebxdcBundles(
    private val root: Path,
    private val http: NappletHttp,
    private val viaTor: Boolean,
    override val fileSystem: FileSystem = platformFileSystem,
) : WebxdcBundleSource {
    override suspend fun open(
        url: String,
        hash: HexKey?,
        servers: List<String>,
        cancelled: () -> Boolean,
        onProgress: (bytes: Long, total: Long?) -> Unit,
        retain: Boolean,
    ): Path? =
        withContext(Dispatchers.IO) {
            val key = hash?.lowercase() ?: ("u" + sha256(url.encodeToByteArray()).toHexKey())
            val dir = root / key
            if (fileSystem.exists(dir / WebxdcArchiveRules.COMPLETE)) return@withContext dir
            lockFor(dir).withLock {
                if (fileSystem.exists(dir / WebxdcArchiveRules.COMPLETE)) return@withLock dir
                val suffix = Random.nextLong().toULong().toString(16)
                val archive = root / "$key.$suffix.download"
                val staging = root / "$key.$suffix.unpacking"
                try {
                    fileSystem.createDirectories(root)
                    val candidates = listOf(url) + (hash?.let { StaticSiteResolver.candidateUrls(servers, it) } ?: emptyList())
                    val size = download(candidates, hash, archive, cancelled, onProgress) ?: return@withLock null
                    fileSystem.createDirectories(staging)
                    if (!unpack(archive, staging, cancelled)) return@withLock null
                    if (fileSystem.metadataOrNull(staging / WebxdcArchiveRules.INDEX)?.isRegularFile != true) {
                        Log.w(TAG) { "$url has no ${WebxdcArchiveRules.INDEX}" }
                        return@withLock null
                    }
                    // An archive may ship a file by the marker's name; the marker is ours alone.
                    val marker = staging / WebxdcArchiveRules.COMPLETE
                    if (fileSystem.exists(marker)) fileSystem.deleteRecursively(marker)
                    fileSystem.write(marker) { writeUtf8(size.toString()) }
                    // Only an unfinished copy can be here: a finished one returned above.
                    if (fileSystem.exists(dir)) fileSystem.deleteRecursively(dir)
                    fileSystem.atomicMove(staging, dir)
                    dir
                } catch (e: IOException) {
                    Log.w(TAG, "Could not unpack $url", e)
                    null
                } finally {
                    runCatching { fileSystem.delete(archive) }
                    runCatching { if (fileSystem.exists(staging)) fileSystem.deleteRecursively(staging) }
                }
            }
        }

    // Nothing trims these: the system purges the cache directory they're in.
    override fun release(dir: Path) = Unit

    // The first of [urls] that answers with the archive, streamed into [archive] as it comes (never
    // whole in memory) and verified against [expectedHash] when there is one. Its size, or null.
    private suspend fun download(
        urls: List<String>,
        expectedHash: HexKey?,
        archive: Path,
        cancelled: () -> Boolean,
        onProgress: (bytes: Long, total: Long?) -> Unit,
    ): Long? {
        for (url in urls.distinct()) {
            if (cancelled()) break
            val hashing = HashingSink.sha256(fileSystem.sink(archive))
            val response =
                try {
                    hashing.use { http.download(NappletHttpRequest(url, viaTor, WebxdcArchiveRules.MAX_ARCHIVE_BYTES, DOWNLOAD_TIMEOUT_MS), it, onProgress) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Downloading $url failed", e)
                    continue
                }
            if (!response.isSuccessful || response.tooLarge) continue
            val hash = hashing.hash.hex()
            if (expectedHash == null || hash == expectedHash.lowercase()) return response.received
            Log.w(TAG) { "$url is not the archive the post names ($hash)" }
        }
        return null
    }

    // Every entry under [dir], inflated under the limits: past them it gives up, as the JVM's does.
    private fun unpack(
        zip: Path,
        dir: Path,
        cancelled: () -> Boolean,
    ): Boolean =
        try {
            val archive = fileSystem.openZip(zip)
            var inflated = 0L
            var entries = 0
            val buffer = okio.Buffer()
            for (entry in archive.listRecursively(ZIP_ROOT)) {
                if (cancelled()) return false
                if (++entries > WebxdcArchiveRules.MAX_ENTRIES) throw IOException("More than ${WebxdcArchiveRules.MAX_ENTRIES} entries")
                if (archive.metadataOrNull(entry)?.isRegularFile != true) continue
                val relative = WebxdcArchiveRules.entryPath(entry.toString().removePrefix("/"))
                val target = relative?.let { dir / it }?.takeIf { it.toString().startsWith(dir.toString() + Path.DIRECTORY_SEPARATOR) }
                target?.parent?.let(fileSystem::createDirectories)
                archive.source(entry).buffer().use { source ->
                    val sink = target?.let { fileSystem.sink(it).buffer() }
                    try {
                        while (true) {
                            val read = source.read(buffer, CHUNK)
                            if (read < 0) break
                            inflated += read
                            if (inflated > WebxdcArchiveRules.MAX_UNPACKED_BYTES) throw IOException("Unpacks to more than ${WebxdcArchiveRules.MAX_UNPACKED_BYTES} bytes")
                            // A skipped entry is inflated just the same, and counts.
                            if (sink != null) sink.write(buffer, read) else buffer.clear()
                        }
                    } finally {
                        sink?.close()
                    }
                }
            }
            if (entries == 0) throw IOException("Not a zip archive")
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not unpack $zip", e)
            false
        }

    companion object {
        private const val TAG = "OkioWebxdcBundles"
        private const val CHUNK = 64L * 1024

        /** A large archive over Tor takes minutes: the JVM's download client allows fifteen. */
        const val DOWNLOAD_TIMEOUT_MS = 15L * 60 * 1000

        private val ZIP_ROOT = "/".toPath()

        // By archive directory, for every bundle source in the process: two launches of one archive share it.
        private val locksLock = KmpLock()
        private val locks = HashMap<String, Mutex>()

        private fun lockFor(dir: Path): Mutex = locksLock.withLock { locks.getOrPut(dir.toString()) { Mutex() } }

        fun dirFor(cacheDir: Path): Path = cacheDir / "webxdc-apps"
    }
}
