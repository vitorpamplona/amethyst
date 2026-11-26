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

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.setLastModifiedMillis
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.use
import kotlin.random.Random

/**
 * A content-addressed, on-disk cache for verified Blossom blobs, shared across the app's processes.
 *
 * Blobs are keyed by their sha256 and written atomically (temp file + rename), so the store needs no
 * journal and is safe for the **main** process (the prefetcher) and the **`:napplet`** process (the
 * content server) to read/write concurrently — unlike OkHttp's `DiskLruCache`, which is single-process
 * only. Both processes derive the same directory from the app's shared `cacheDir`.
 *
 * Every [put] re-hashes the bytes and only stores them if they match the key, so the store can only
 * ever hold correct content; a caller may still re-verify on read (the resolver does).
 *
 * Files are kept per site and per account ([forSite]): one shared cache would tell a site which files
 * this device already holds (no request reaches its server, the page loads instantly), so a site
 * could learn what else was opened, or link two accounts on one device. [trimToSize] covers them all.
 *
 * [fileSystem] is the platform's; the modification times that order [trimToSize] are set on it
 * directly ([setLastModifiedMillis]), so a test may swap it only for one backed by the same disk.
 */
class NappletBlobCache private constructor(
    private val dir: Path,
    // The cache's top folder, which trimming walks whichever site's cache it is called on.
    private val root: Path,
    val fileSystem: FileSystem,
) {
    constructor(dir: Path, fileSystem: FileSystem = platformFileSystem) : this(dir, dir, fileSystem)

    /** The files [partition] (see [partitionOf]) may see: its own, never another site's or account's. */
    fun forSite(partition: String): NappletBlobCache = NappletBlobCache(root / sha256(partition.encodeToByteArray()).toHexKey().take(PARTITION_NAME_LENGTH), root, fileSystem)

    fun has(sha256: String): Boolean = fileFor(sha256)?.let(::isFile) == true

    fun get(sha256: String): ByteArray? = file(sha256)?.let { runCatching { fileSystem.read(it) { readByteArray() } }.getOrNull() }

    /**
     * The cached blob's file, to stream instead of loading, or null when it isn't cached. A hit
     * marks the blob as used, so [trimToSize] evicts what was used longest ago, not what was
     * downloaded first: a napplet opened every day stays cached.
     */
    fun file(sha256: String): Path? {
        val file = fileFor(sha256) ?: return null
        val metadata = fileSystem.metadataOrNull(file)?.takeIf { it.isRegularFile } ?: return null
        val now = TimeUtils.nowMillis()
        // At most once an hour per blob: a page's requests shouldn't each cost a metadata write.
        if (now - (metadata.lastModifiedAtMillis ?: 0L) > TOUCH_INTERVAL_MS && !setLastModifiedMillis(file, now)) {
            Log.d("NappletBlobCache") { "Could not mark $sha256 as used" }
        }
        return file
    }

    /** Stores [bytes] under [sha256] iff they actually hash to it. No-op if already present or mismatched. */
    fun put(
        sha256: String,
        bytes: ByteArray,
    ) {
        if (sha256(bytes).toHexKey() != sha256.lowercase()) return // verify-on-write: never cache wrong content
        putVerified(sha256, bytes)
    }

    /**
     * Stores [bytes] under [sha256], which the caller has just checked they hash to (quartz's
     * resolver does), so a multi-megabyte blob isn't hashed twice.
     */
    fun putVerified(
        sha256: String,
        bytes: ByteArray,
    ) {
        val target = fileFor(sha256) ?: return
        if (isFile(target)) return
        try {
            fileSystem.createDirectories(dir)
        } catch (e: IOException) {
            Log.w("NappletBlobCache") { "Cannot create $dir" }
            return
        }
        val tmp = dir / "$sha256$TEMP_MARKER${Random.nextLong().toULong()}"
        try {
            // Synced before the rename: reads never re-hash, so a file under a hash's name must be
            // whole even after a power loss. (A file handle's flush is an fsync on every platform.)
            fileSystem.openReadWrite(tmp, mustCreate = true, mustExist = false).use { handle ->
                handle.write(0, bytes, 0, bytes.size)
                handle.flush()
            }
            fileSystem.atomicMove(tmp, target)
        } catch (e: IOException) {
            // A full disk, most likely: the partial file goes, the blob is simply not cached.
            Log.w("NappletBlobCache", "Could not cache $sha256", e)
            deleteOrWarn(tmp, "partial temp file")
        }
    }

    /**
     * Best-effort eviction across every site's files: if they exceed [maxBytes], delete the least
     * recently used until under it.
     */
    fun trimToSize(maxBytes: Long) {
        runCatching {
            val files =
                filesToDepthTwo()
                    .mapNotNull { path -> fileSystem.metadataOrNull(path)?.takeIf { it.isRegularFile }?.let { path to it } }
                    .filter { (path, metadata) -> !isStaleTemp(path, metadata.lastModifiedAtMillis ?: 0L) && !path.name.contains(TEMP_MARKER) }
                    .map { (path, metadata) -> Triple(path, metadata.size ?: 0L, metadata.lastModifiedAtMillis ?: 0L) }
            var total = files.sumOf { it.second }
            if (total <= maxBytes) return
            files.sortedBy { it.third }.forEach { (f, length, _) ->
                if (total <= maxBytes) return
                if (deleteOrWarn(f, "blob")) {
                    total -= length
                }
            }
        }
    }

    // The root's files and its partitions' files, as File.walkTopDown().maxDepth(2) found them.
    private fun filesToDepthTwo(): List<Path> {
        val top = fileSystem.listOrNull(root) ?: return emptyList()
        return top + top.flatMap { child -> if (fileSystem.metadataOrNull(child)?.isDirectory == true) fileSystem.listOrNull(child).orEmpty() else emptyList() }
    }

    /**
     * A temp file a crashed or killed write left behind: one being written now is younger than this,
     * so it stays. Deleted here, as trimming walks the directory anyway.
     */
    private fun isStaleTemp(
        file: Path,
        lastModified: Long,
    ): Boolean {
        if (!file.name.contains(TEMP_MARKER) || TimeUtils.nowMillis() - lastModified < STALE_TEMP_MS) return false
        deleteOrWarn(file, "stale temp file")
        return true
    }

    private fun isFile(path: Path): Boolean = fileSystem.metadataOrNull(path)?.isRegularFile == true

    // As File.deleteOrWarn: true when the file is gone, a warning when it couldn't be removed.
    private fun deleteOrWarn(
        path: Path,
        what: String,
    ): Boolean =
        try {
            fileSystem.delete(path, mustExist = false)
            true
        } catch (e: IOException) {
            Log.w("NappletBlobCache") { "Could not delete $what $path" }
            false
        }

    /**
     * The blob's file, or null when [sha256] is not one. The name comes from a site's manifest, so it is
     * checked here too: a `../` in it would otherwise read and write outside the cache directory.
     */
    private fun fileFor(sha256: String): Path? = sha256.lowercase().takeIf { it.length == 64 && Hex.isHex64(it) }?.let { dir / it }

    companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024
        private const val TOUCH_INTERVAL_MS = 60L * 60 * 1000
        private const val STALE_TEMP_MS = 60L * 60 * 1000
        private const val PARTITION_NAME_LENGTH = 32

        /**
         * The cache partition of a site ([author], [identifier], as a launch's identity names it) seen
         * by [account] (any stable per-account name). A card's prefetch and the launch compute the same.
         */
        fun partitionOf(
            account: String,
            author: String,
            identifier: String,
        ): String = "$account|$author:$identifier"

        private const val TEMP_MARKER = ".tmp."

        /** The shared cache directory, identical across processes since they share the app's cacheDir. */
        fun dirFor(cacheDir: Path): Path = cacheDir / "napplet-blobs"
    }
}
