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

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlin.io.path.Path

/** On-disk backend for the shared event store. */
enum class StoreBackend {
    /**
     * Single SQLite database file at [DataDir.eventsDbFile]. Postings live
     * in shared B-tree pages, so an event's kind/author/tag indexes cost a
     * handful of rows — not one 4 KB-block file each, the way the FS store
     * lays them out. For crawl-scale corpora (hundreds of thousands of
     * follow lists) this is several times smaller on disk and the default.
     */
    SQLITE,

    /**
     * Filesystem tree at [DataDir.eventsDir] — one pretty-printed JSON file
     * per event plus one file per index posting. Human-inspectable with
     * `cat`/`jq`/`git diff`, but every posting rounds up to a filesystem
     * block, so a large corpus balloons. Opt in with `AMY_STORE=fs`.
     */
    FS,
}

/**
 * Chooses and opens the event-store backend for `amy`. The backend is
 * selected by the `AMY_STORE` environment variable and defaults to
 * [StoreBackend.SQLITE]; set `AMY_STORE=fs` for the legacy filesystem
 * store. Both backends implement [IEventStore], so every command works
 * unchanged regardless of the choice — the only user-visible difference
 * is where bytes land ([DataDir.eventsDbFile] vs [DataDir.eventsDir]) and
 * how much disk they take.
 */
object StoreFactory {
    const val ENV = "AMY_STORE"

    /** Resolve the configured backend. Unrecognised values fall back to the default. */
    fun backend(): StoreBackend =
        when (System.getenv(ENV)?.trim()?.lowercase()) {
            "fs", "file", "files", "filesystem" -> StoreBackend.FS
            else -> StoreBackend.SQLITE
        }

    /**
     * Open the store for [dataDir] using the configured [backend]. Events
     * are written pretty-printed on the FS backend so the on-disk JSON stays
     * inspection-friendly; the SQLite backend stores the compact NIP-01
     * form internally. Neither is re-used for signature checks (verification
     * always re-canonicalises), so the stored representation is purely an
     * implementation detail. Callers own [IEventStore.close].
     */
    fun open(dataDir: DataDir): IEventStore =
        when (backend()) {
            StoreBackend.SQLITE -> {
                // BundledSQLiteDriver won't create parent directories.
                dataDir.eventsDbFile.parentFile?.mkdirs()
                EventStore(dbName = dataDir.eventsDbFile.absolutePath, relay = null)
            }
            StoreBackend.FS ->
                FsEventStore(
                    root = Path(dataDir.eventsDir.absolutePath),
                    eventToJson = JacksonMapper::toJsonPretty,
                )
        }
}
