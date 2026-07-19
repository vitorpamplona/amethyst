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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.StoreBackend
import com.vitorpamplona.amethyst.cli.StoreFactory
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * `amy store <stat|sweep-expired|scrub|compact>` — direct introspection
 * and maintenance of the shared event store under `<data-dir>/shared/`.
 *
 * The store backend is selected by `AMY_STORE` (SQLite by default, or the
 * FS tree with `AMY_STORE=fs` — see [StoreFactory]); each verb adapts to
 * whichever is active:
 *
 * - `stat`           total event count, disk bytes, backend, plus (FS only)
 *                    the per-kind histogram and mtime range — pure read,
 *                    no relay traffic.
 * - `sweep-expired`  delete events whose NIP-40 `expiration` tag has
 *                    passed (per the store's own sweep logic). Run
 *                    from cron / scheduler / `amy` periodically.
 * - `scrub`          FS: rebuild every `idx/` entry from the canonical
 *                    events, recovering from partial-write crashes or
 *                    external edits. SQLite: a no-op (indexes are updated
 *                    transactionally and can't drift).
 * - `compact`        FS: drop dangling `idx/` entries whose canonical is
 *                    gone. SQLite: `VACUUM` the database to reclaim space.
 * - `reindex-fts`    wipe and rebuild only the NIP-50 full-text search
 *                    index from the stored events. Run after a quartz
 *                    upgrade that changes which kinds are searchable.
 */
object StoreCommands {
    val USAGE: String =
        """
        |Local event store (shared, under `<data-dir>/shared/`; backend via AMY_STORE):
        |  store stat                                 event count + disk usage (kind histogram/mtime on fs)
        |  store sweep-expired                        delete events past their NIP-40 expiration
        |  store scrub                                fs: rebuild idx/ from canonical events; sqlite: no-op
        |  store compact                              fs: drop dangling idx entries; sqlite: VACUUM
        |  store reindex-fts                          rebuild the NIP-50 search index (after a searchable-kinds change)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "store",
            tail,
            "store <stat|sweep-expired|scrub|compact|reindex-fts>",
            help = USAGE,
            routes =
                mapOf(
                    "stat" to { _ -> stat(dataDir) },
                    "sweep-expired" to { _ -> sweepExpired(dataDir) },
                    "scrub" to { _ -> scrub(dataDir) },
                    "compact" to { _ -> compact(dataDir) },
                    "reindex-fts" to { _ -> reindexFts(dataDir) },
                ),
        )

    private suspend fun stat(dataDir: DataDir): Int =
        when (StoreFactory.backend()) {
            StoreBackend.SQLITE -> sqliteStat(dataDir)
            StoreBackend.FS -> fsStat(dataDir)
        }

    /**
     * SQLite `stat`: total count via `COUNT(*)` and on-disk bytes from the
     * DB file plus its `-wal`/`-shm` sidecars. The per-kind histogram and
     * mtime range are FS-store concepts (they read the `idx/kind` tree and
     * file mtimes), so they're omitted here.
     */
    private suspend fun sqliteStat(dataDir: DataDir): Int {
        val dbFile = dataDir.eventsDbFile
        if (!dbFile.exists()) {
            Output.emit(
                mapOf(
                    "backend" to "sqlite",
                    "events" to 0,
                    "disk_bytes" to 0L,
                    "root" to dbFile.absolutePath,
                ),
            )
            return 0
        }
        val count =
            EventStore(dbName = dbFile.absolutePath, relay = null).use { store ->
                store.count(Filter())
            }
        val diskBytes =
            listOf("", "-wal", "-shm").sumOf { suffix ->
                val f = File(dbFile.absolutePath + suffix)
                if (f.isFile) f.length() else 0L
            }
        Output.emit(
            mapOf(
                "backend" to "sqlite",
                "events" to count,
                "disk_bytes" to diskBytes,
                "root" to dbFile.absolutePath,
            ),
        )
        return 0
    }

    private fun fsStat(dataDir: DataDir): Int {
        val storeRoot = dataDir.eventsDir.toPath()
        if (!storeRoot.exists()) {
            Output.emit(
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

        Output.emit(
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

    private suspend fun sweepExpired(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            if (store is FsEventStore) {
                // The FS store exposes its expiration index as a directory,
                // so we can report exactly how many entries the sweep cleared.
                val expiresAtDir = dataDir.eventsDir.toPath().resolve("idx/expires_at")
                val before = countEntries(expiresAtDir)
                store.deleteExpiredEvents()
                val after = countEntries(expiresAtDir)
                Output.emit(
                    mapOf(
                        "swept" to (before - after).coerceAtLeast(0L),
                        "remaining" to after,
                    ),
                )
            } else {
                store.deleteExpiredEvents()
                Output.emit(mapOf("ok" to true))
            }
            0
        }

    private suspend fun scrub(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            when (store) {
                is FsEventStore -> {
                    store.scrub()
                    Output.emit(mapOf("ok" to true))
                }
                // SQLite indexes are written in the same transaction as the
                // event, so they can't drift the way the FS `idx/` tree can —
                // there is nothing to rebuild.
                else ->
                    Output.emit(
                        mapOf(
                            "ok" to true,
                            "note" to "scrub is a no-op for the sqlite backend (indexes update transactionally)",
                        ),
                    )
            }
            0
        }

    private suspend fun compact(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            when (store) {
                // FS: drop dangling idx/ postings. SQLite: VACUUM to rebuild
                // the file and hand freed pages back to the OS.
                is FsEventStore -> store.compact()
                is EventStore -> store.store.vacuum()
                else -> Unit
            }
            Output.emit(mapOf("ok" to true))
            0
        }

    private suspend fun reindexFts(dataDir: DataDir): Int =
        withStore(dataDir) { store ->
            val fsBacked = store is FsEventStore
            val ftsDir = dataDir.eventsDir.toPath().resolve("idx/fts")
            val before = if (fsBacked) countEntries(ftsDir) else 0L
            // Drive the resumable, batched path to completion so a huge
            // store is processed without holding the writer lock for the
            // whole pass. A real long-running caller would persist the
            // cursor between calls; here we just loop until done.
            var cursor: String? = null
            var processed = 0L
            var batches = 0
            do {
                val progress = store.reindexFullTextSearch(cursor, IEventStore.DEFAULT_FTS_REINDEX_BATCH)
                cursor = progress.cursor
                processed += progress.processedThisBatch
                batches++
            } while (!progress.done)
            val out =
                linkedMapOf<String, Any?>(
                    "ok" to true,
                    "processed" to processed,
                    "batches" to batches,
                )
            if (fsBacked) {
                // Token-file counts are an FS-store notion (idx/fts is a
                // directory); the SQLite FTS index doesn't expose one.
                out["tokens_before"] = before
                out["tokens_after"] = countEntries(ftsDir)
            }
            Output.emit(out)
            0
        }

    /**
     * Maintenance verbs only need the store — not identity, not relays,
     * not the signer. Skip [Context.open] (which throws if no identity has
     * been bootstrapped) and open the configured backend directly via
     * [StoreFactory], so `amy store` acts on whichever store the rest of
     * the CLI is using.
     */
    private suspend fun withStore(
        dataDir: DataDir,
        body: suspend (IEventStore) -> Int,
    ): Int {
        val store = StoreFactory.open(dataDir)
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
