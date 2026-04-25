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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Replaceable- and addressable-event slot management.
 *
 * A *slot* is a single hardlink whose parent directory encodes the
 * Nostr uniqueness constraint:
 *
 *   replaceable/<kind>/<pubkey>.json                       # kinds 0, 3, 10000-19999
 *   addressable/<kind>/<pubkey>/<sha256(dTag)>.json        # kinds 30000-39999
 *
 * Because a directory can only hold one entry with a given name, the
 * filesystem *is* the `UNIQUE(kind, pubkey[, d])` constraint. Insertion
 * rules match SQLite's ReplaceableModule / AddressableModule triggers:
 *
 *   Tnew  = incoming event.createdAt
 *   Told  = current slot winner's createdAt (if any)
 *
 *   Tnew > Told             → atomically install new slot, evict old
 *   Tnew <= Told            → reject the insert entirely
 *   no existing slot        → install new slot
 *   not replaceable/addr.   → no-op (kinds that don't have slot semantics)
 *
 * Eviction deletes the old canonical file and all its index hardlinks.
 */
internal class FsSlots(
    private val layout: FsLayout,
    private val indexer: FsIndexer,
) {
    /** Path of the slot that owns this event's identity, or null if none. */
    fun slotPathFor(event: Event): Path? =
        when {
            event.kind.isReplaceable() -> {
                layout.replaceableSlot(event.kind, event.pubKey)
            }

            event is AddressableEvent && event.kind.isAddressable() -> {
                layout.addressableSlot(event.kind, event.pubKey, event.dTag())
            }

            else -> {
                null
            }
        }

    /**
     * Pre-insert check. Returns the *existing* winner if it blocks the
     * insert (i.e. its createdAt is >= incoming.createdAt). Returns null
     * when insertion may proceed — possibly with an evictable older winner,
     * which the caller looks up separately via [readSlot].
     */
    fun shouldBlock(
        event: Event,
        slot: Path,
    ): Boolean {
        val existing = readSlot(slot) ?: return false
        return existing.createdAt >= event.createdAt
    }

    fun readSlot(slot: Path): Event? {
        if (!slot.exists()) return null
        return try {
            Event.fromJson(slot.readText())
        } catch (_: java.io.IOException) {
            null
        } catch (_: com.fasterxml.jackson.core.JacksonException) {
            null
        }
    }

    /**
     * Install [canonical] at [slot] atomically, evicting [evicting] if
     * present and distinct from the new event. The old canonical and all
     * its index hardlinks are removed; the slot itself is swapped in via
     * `rename(2)` (REPLACE_EXISTING + ATOMIC_MOVE).
     */
    fun install(
        slot: Path,
        canonical: Path,
        newEvent: Event,
        evicting: Event?,
    ) {
        Files.createDirectories(slot.parent)
        val tmp = slot.resolveSibling("${slot.fileName}.tmp.${UUID.randomUUID()}")
        try {
            Files.createLink(tmp, canonical)
            Files.move(tmp, slot, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            throw t
        }
        if (evicting != null && evicting.id != newEvent.id) {
            indexer.unlink(evicting)
            layout.canonical(evicting.id).deleteIfExists()
        }
    }

    /** Remove a slot without installing a replacement. Used by NIP-09 cascades. */
    fun clear(slot: Path) {
        Files.deleteIfExists(slot)
    }
}
