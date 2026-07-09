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
package com.vitorpamplona.amethyst.cli

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * Read-only introspection of a file-backed Nostr event store on disk.
 *
 * Pure filesystem walk — no relay traffic, no writer lock, no [Context].
 * Shared by `amy store stat` (full detail) and `amy status` (a compact
 * roll-up alongside the account overview).
 */
data class StoreStats(
    val events: Long,
    /** Per-kind event counts derived from `idx/kind/<k>/`, sorted by kind string. */
    val byKind: Map<String, Long>,
    val diskBytes: Long,
    /** Oldest / newest event file mtime, in unix seconds. Null on an empty store. */
    val oldestAt: Long?,
    val newestAt: Long?,
    val root: Path,
) {
    val distinctKinds: Int get() = byKind.size

    companion object {
        /** Compute stats for the store rooted at [storeRoot]. Missing dir → all-zero. */
        fun of(storeRoot: Path): StoreStats {
            if (!storeRoot.exists()) {
                return StoreStats(0, emptyMap(), 0L, null, null, storeRoot.toAbsolutePath())
            }

            val eventsRoot = storeRoot.resolve("events")
            var count = 0L
            var oldest: Long? = null
            var newest: Long? = null
            if (Files.isDirectory(eventsRoot)) {
                Files.walk(eventsRoot).use { stream ->
                    for (p in stream) {
                        if (!Files.isRegularFile(p)) continue
                        if (!p.fileName.toString().endsWith(".json")) continue
                        count++
                        val mt =
                            try {
                                Files.getLastModifiedTime(p).to(TimeUnit.SECONDS)
                            } catch (_: IOException) {
                                continue
                            }
                        val o = oldest
                        if (o == null || mt < o) oldest = mt
                        val n = newest
                        if (n == null || mt > n) newest = mt
                    }
                }
            }

            // Histogram from idx/kind/<k>/ — for a healthy store this is
            // exactly one entry per (kind, event), so summing matches `count`.
            // Mismatch points at index drift; run `amy store scrub` to fix.
            val kindRoot = storeRoot.resolve("idx/kind")
            val byKind = sortedMapOf<String, Long>()
            if (Files.isDirectory(kindRoot)) {
                Files.list(kindRoot).use { stream ->
                    for (kindDir in stream) {
                        if (!Files.isDirectory(kindDir)) continue
                        val n = Files.list(kindDir).use { it.count() }
                        byKind[kindDir.fileName.toString()] = n
                    }
                }
            }

            return StoreStats(
                events = count,
                byKind = byKind,
                diskBytes = walkSize(storeRoot),
                oldestAt = oldest,
                newestAt = newest,
                root = storeRoot.toAbsolutePath(),
            )
        }

        private fun walkSize(root: Path): Long {
            if (!Files.exists(root)) return 0L
            var total = 0L
            Files.walk(root).use { stream ->
                for (p in stream) {
                    if (!Files.isRegularFile(p)) continue
                    total +=
                        try {
                            Files.size(p)
                        } catch (_: IOException) {
                            0L
                        }
                }
            }
            return total
        }
    }
}
