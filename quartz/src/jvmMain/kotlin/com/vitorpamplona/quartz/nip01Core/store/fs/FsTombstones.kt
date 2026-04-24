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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * NIP-09 deletion tombstones. Each tombstone is itself a hardlink to
 * the kind-5 event that authored the deletion — so the tombstone *is*
 * the deletion event, just indexed by what it targets.
 *
 * Two tombstone kinds:
 *   tombstones/id/<target_id>.json                  (NIP-09 by `e`-tag)
 *   tombstones/addr/<kind>/<pubkey>/<sha256(d)>.json (NIP-09 by `a`-tag;
 *                                                    empty d for replaceables)
 *
 * Semantics:
 *   - An `id` tombstone unconditionally blocks re-insertion of the exact
 *     event id (matching SQLite's ExistsCheck on etag_hash).
 *   - An `addr` tombstone holds the kind-5 `createdAt` as a cutoff. An
 *     insert is blocked iff `event.createdAt <= tombstone.createdAt`.
 *     Newer events at the same address may pass (matching SQLite's
 *     `created_at >= NEW.created_at` trigger condition).
 *   - When multiple kind-5s target the same tombstone path, the one
 *     with the larger `createdAt` wins (strongest cutoff). Atomic
 *     rename swaps it in.
 *
 * The caller (FsEventStore) handles cascade-deleting the target events
 * and slots; this class only manages the tombstone filesystem state.
 */
internal class FsTombstones(
    private val layout: FsLayout,
) {
    fun hasIdTombstone(id: HexKey): Boolean = layout.idTombstonePath(id).exists()

    /** Returns the `createdAt` cutoff from the tombstone, or null if none exists. */
    fun addrTombstoneCutoff(
        kind: Kind,
        pubkey: HexKey,
        dTag: String,
    ): Long? {
        val path = layout.addrTombstonePath(kind, pubkey, dTag)
        if (!path.exists()) return null
        return try {
            Event.fromJson(path.readText()).createdAt
        } catch (_: java.nio.file.NoSuchFileException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun installId(
        targetId: HexKey,
        deletionCanonical: Path,
    ) {
        val path = layout.idTombstonePath(targetId)
        if (path.exists()) return
        Files.createDirectories(path.parent)
        try {
            Files.createLink(path, deletionCanonical)
        } catch (_: FileAlreadyExistsException) {
            // Concurrent writer installed first — fine, either copy is equivalent.
        }
    }

    fun installAddr(
        kind: Kind,
        pubkey: HexKey,
        dTag: String,
        deletionEvent: Event,
        deletionCanonical: Path,
    ) {
        val path = layout.addrTombstonePath(kind, pubkey, dTag)
        val existing = addrTombstoneCutoff(kind, pubkey, dTag)
        if (existing != null && existing >= deletionEvent.createdAt) return

        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp.${UUID.randomUUID()}")
        try {
            Files.createLink(tmp, deletionCanonical)
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            throw t
        }
    }

    fun clearId(id: HexKey) {
        Files.deleteIfExists(layout.idTombstonePath(id))
    }

    fun clearAddr(
        kind: Kind,
        pubkey: HexKey,
        dTag: String,
    ) {
        Files.deleteIfExists(layout.addrTombstonePath(kind, pubkey, dTag))
    }
}
