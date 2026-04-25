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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * `amy store <stat|sweep-expired|scrub|compact>` — direct introspection
 * and maintenance of the file-backed event store at
 * `<data-dir>/events-store/`.
 *
 * - `stat`           total event count, kind histogram, disk bytes,
 *                    mtime range — pure read, no relay traffic.
 * - `sweep-expired`  delete events whose NIP-40 `expiration` tag has
 *                    passed (per the store's own sweep logic). Run
 *                    from cron / scheduler / `amy` periodically.
 * - `scrub`          rebuild every `idx/` entry from the canonical
 *                    events. Recovers from partial-write crashes or
 *                    external edits.
 * - `compact`        drop dangling `idx/` entries whose canonical is
 *                    gone. Cheaper than scrub.
 */
object StoreCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "store <stat|sweep-expired|scrub|compact>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "stat" -> stat(dataDir)
            "sweep-expired" -> sweepExpired(dataDir)
            "scrub" -> scrub(dataDir)
            "compact" -> compact(dataDir)
            else -> Json.error("bad_args", "store ${tail[0]}")
        }
    }

    private fun stat(dataDir: DataDir): Int {
        val storeRoot = dataDir.eventsDir.toPath()
        if (!storeRoot.exists()) {
            Json.writeLine(
                mapOf(
                    "events" to 0,
                    "by_kind" to emptyMap<String, Long>(),
                    "disk_bytes" to 0L,
                    "oldest_at" to null,
                    "newest_at" to null,
                    "root" to storeRoot.toAbsolutePath().toString(),
                ),
            )
            return 0
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

        val diskBytes = walkSize(storeRoot)

        Json.writeLine(
            mapOf(
                "events" to count,
                "by_kind" to byKind,
                "disk_bytes" to diskBytes,
                "oldest_at" to oldest,
                "newest_at" to newest,
                "root" to storeRoot.toAbsolutePath().toString(),
            ),
        )
        return 0
    }

    private fun sweepExpired(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            val expiresAtDir = dataDir.eventsDir.toPath().resolve("idx/expires_at")
            val before = countEntries(expiresAtDir)
            store.deleteExpiredEvents()
            val after = countEntries(expiresAtDir)
            Json.writeLine(
                mapOf(
                    "swept" to (before - after).coerceAtLeast(0L),
                    "remaining" to after,
                ),
            )
            0
        }

    private fun scrub(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            store.scrub()
            Json.writeLine(mapOf("ok" to true))
            0
        }

    private fun compact(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            store.compact()
            Json.writeLine(mapOf("ok" to true))
            0
        }

    /**
     * Maintenance verbs only need the store — not identity, not relays,
     * not the signer. Skip [Context.open] (which throws if no identity
     * has been bootstrapped) and construct the [FsEventStore] directly
     * from [DataDir.eventsDir]. Pretty formatter matches what the rest
     * of the CLI uses for inspection-friendly output.
     */
    private inline fun withStore(
        dataDir: DataDir,
        body: (FsEventStore) -> Int,
    ): Int {
        val store =
            FsEventStore(
                root = dataDir.eventsDir.toPath(),
                eventToJson = JacksonMapper::toJsonPretty,
            )
        try {
            return body(store)
        } finally {
            store.close()
        }
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

    private fun countEntries(dir: Path): Long {
        if (!Files.isDirectory(dir)) return 0L
        return Files.list(dir).use { it.count() }
    }
}
