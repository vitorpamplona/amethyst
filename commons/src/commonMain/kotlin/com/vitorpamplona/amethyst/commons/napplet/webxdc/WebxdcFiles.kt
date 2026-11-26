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
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.normalizeStaticPath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.use
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Where one webxdc launch's unpacked archive comes from: `WebxdcBundles` on the JVM (OkHttp, the
 * JDK's zip reader), [OkioWebxdcBundles] elsewhere. A directory it returns is only ever there whole.
 */
interface WebxdcBundleSource {
    /** The file system the returned directories are on. */
    val fileSystem: FileSystem

    /**
     * The unpacked archive at [url] (sha256 [hash] when known), downloaded from it or from [servers]
     * when it isn't here yet, telling [onProgress] how far that download is. Null when no copy could
     * be had, it isn't a webxdc app (no `index.html`), or [cancelled] said to stop. With [retain], it
     * is kept from any trim until [release].
     */
    suspend fun open(
        url: String,
        hash: HexKey?,
        servers: List<String>,
        cancelled: () -> Boolean,
        onProgress: (bytes: Long, total: Long?) -> Unit,
        retain: Boolean,
    ): Path?

    /** The launch that [open]ed [dir] with `retain` is done with it. */
    fun release(dir: Path)
}

/**
 * What every host does with a `.xdc` the same way, wherever it unpacks it: the limits on an archive
 * a stranger made, and which entries may be written.
 */
object WebxdcArchiveRules {
    /** The largest archive downloaded; the largest one seen published is a 178 MB Half-Life. */
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024

    /** Every byte inflated out of one archive, kept or not: a small zip of zeros can't fill the disk. */
    const val MAX_UNPACKED_BYTES = 1024L * 1024 * 1024

    const val MAX_ENTRIES = 20_000

    /** Written last into a finished unpack; a directory without it is never served. */
    const val COMPLETE = ".amethyst-complete"

    const val INDEX = "index.html"

    /**
     * The relative path a zip entry may be written to, or null for one that must not be: empty,
     * absolute, climbing out with `..`, or macOS metadata.
     */
    fun entryPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || name.startsWith("/") || parts.any { it == ".." }) return null
        if (parts.first() == "__MACOSX" || parts.last() == ".DS_Store") return null
        return parts.joinToString("/")
    }
}

/**
 * What one webxdc launch serves on its origin: the files of its unpacked archive, and the
 * messenger's `webxdc.js`, which the spec says the app loads but never ships.
 */
class WebxdcFiles(
    private val bundles: WebxdcBundleSource,
    private val url: String,
    private val hash: HexKey?,
    private val servers: List<String>,
    runtimeJs: String,
) : NappletFiles {
    private val runtime = runtimeJs.encodeToByteArray()
    private val fileSystem = bundles.fileSystem

    // The unpacked archive (and its canonical path), once had: every request after the first is a
    // file lookup.
    @Volatile
    private var dir: Pair<Path, String>? = null
    private val opening = Mutex()

    // The directory [WebxdcBundleSource.open] retained for this launch, as it named it: released on [close].
    private val lock = KmpLock()
    private var retained: Path? = null

    // A miss is remembered: a page's every request would otherwise download the whole archive again.
    // Retry builds a new launch.
    @Volatile
    private var failed = false

    @Volatile
    private var closed = false

    private val downloading = MutableStateFlow<NappletDownloadProgress?>(null)

    override val download: StateFlow<NappletDownloadProgress?> = downloading.asStateFlow()

    private fun takeRetained(): Path? =
        lock.withLock {
            val it = retained
            retained = null
            it
        }

    private suspend fun dir(): Pair<Path, String>? {
        dir?.let { return it }
        if (failed || closed) return null
        return opening.withLock {
            dir ?: if (failed || closed) {
                null
            } else {
                val opened =
                    try {
                        bundles.open(url, hash, servers, { closed }, ::onProgress, retain = true)
                    } finally {
                        downloading.value = null
                    }
                if (opened == null) {
                    if (!closed) failed = true
                    null
                } else {
                    lock.withLock { retained = opened }
                    // Closed while it opened: give it back (once, whichever of the two gets here).
                    if (closed) {
                        takeRetained()?.let(bundles::release)
                        return@withLock null
                    }
                    val canonical = fileSystem.canonicalize(opened)
                    (canonical to canonical.toString() + Path.DIRECTORY_SEPARATOR).also { dir = it }
                }
            }
        }
    }

    override fun close() {
        closed = true
        takeRetained()?.let(bundles::release)
    }

    private val started = TimeSource.Monotonic.markNow()

    @Volatile
    private var lastShownAt = 0L

    // Every 64 KB read is a call, and on Android each update crosses processes: a bar needs about ten
    // a second, whatever the archive's size or whether the server gave it.
    private fun onProgress(
        bytes: Long,
        total: Long?,
    ) {
        val shown = downloading.value
        val now = started.elapsedNow().inWholeNanoseconds
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
        val relative = WebxdcArchiveRules.entryPath(path) ?: return null
        // A file that isn't there has no canonical path: a 404, as any other miss.
        val file =
            try {
                fileSystem.canonicalize(root / relative)
            } catch (e: IOException) {
                return null
            }
        if (!file.toString().startsWith(rootPrefix) || fileSystem.metadataOrNull(file)?.isRegularFile != true || relative == WebxdcArchiveRules.COMPLETE) return null
        val byExtension = guessStaticContentType(relative)
        val type = if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(head(file)) ?: byExtension else byExtension
        return NappletBlobStore.Blob("/$relative", hash.orEmpty(), type, file, null, fileSystem)
    }

    // Every file is in the archive or it isn't: a miss is a 404, never an error.
    override fun isListed(requestPath: String): Boolean = false

    private fun head(file: Path): ByteArray =
        fileSystem.source(file).use { source ->
            val buffer = Buffer()
            val read = source.read(buffer, 64)
            if (read <= 0) ByteArray(0) else buffer.readByteArray()
        }

    companion object {
        const val RUNTIME_PATH = "webxdc.js"

        private const val PROGRESS_INTERVAL_NANOS = 100_000_000L
    }
}
