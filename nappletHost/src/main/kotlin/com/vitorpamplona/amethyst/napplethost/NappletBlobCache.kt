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
package com.vitorpamplona.amethyst.napplethost

import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File

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
 */
class NappletBlobCache(
    private val dir: File,
) {
    fun has(sha256: String): Boolean = fileFor(sha256).isFile

    fun get(sha256: String): ByteArray? = fileFor(sha256).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    /** Stores [bytes] under [sha256] iff they actually hash to it. No-op if already present or mismatched. */
    fun put(
        sha256: String,
        bytes: ByteArray,
    ) {
        val target = fileFor(sha256)
        if (target.isFile) return
        if (sha256(bytes).toHexKey() != sha256.lowercase()) return // verify-on-write: never cache wrong content
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$sha256.tmp.${System.nanoTime()}")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.deleteOrWarn("NappletBlobCache", "leftover temp file")
            }
        }
    }

    /** Best-effort eviction: if the store exceeds [maxBytes], delete oldest blobs until under it. */
    fun trimToSize(maxBytes: Long) {
        runCatching {
            val files =
                dir
                    .listFiles()
                    ?.filter { it.isFile && !it.name.contains(".tmp.") }
                    ?.map { Triple(it, it.length(), it.lastModified()) } ?: return
            var total = files.sumOf { it.second }
            if (total <= maxBytes) return
            files.sortedBy { it.third }.forEach { (f, length, _) ->
                if (total <= maxBytes) return
                if (f.deleteOrWarn("NappletBlobCache", "blob")) {
                    total -= length
                }
            }
        }
    }

    private fun fileFor(sha256: String) = File(dir, sha256.lowercase())

    companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024

        /** The shared cache directory, identical across processes since they share the app's cacheDir. */
        fun dirFor(cacheDir: File) = File(cacheDir, "napplet-blobs")
    }
}
