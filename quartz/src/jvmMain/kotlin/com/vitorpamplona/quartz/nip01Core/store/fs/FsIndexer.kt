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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * Writes and removes the hardlink indexes that sit alongside canonical
 * event files. Every entry is a hardlink into the canonical, so an
 * `ln(2)` adds one directory entry and no new inode; `unlink(2)` drops
 * one reference and the kernel GCs the inode once all links are gone.
 *
 * Index trees maintained:
 *   idx/kind/<k>/<ts>-<id>
 *   idx/author/<pk>/<ts>-<id>
 *   idx/owner/<owner_hex>/<ts>-<id>
 *   idx/tag/<name>/<hash_hex>/<ts>-<id>
 *
 * `owner` matches SQLite's `pubkey_owner_hash` — event pubkey for normal
 * events, recipient for GiftWrap — and is consumed by the NIP-09 /
 * NIP-62 cascades in later steps.
 */
internal class FsIndexer(
    private val layout: FsLayout,
    private val hasher: TagNameValueHasher,
    private val indexingStrategy: IndexingStrategy = DefaultIndexingStrategy(),
) {
    /** Paths every index link lives at for a given event. */
    fun pathsFor(event: Event): List<Path> {
        val out = ArrayList<Path>(8 + event.tags.size)
        out.add(layout.kindEntry(event.kind, event.createdAt, event.id))
        out.add(layout.authorEntry(event.pubKey, event.createdAt, event.id))
        out.add(layout.ownerEntry(ownerHash(event), event.createdAt, event.id))
        for (tag in event.tags) {
            if (!indexingStrategy.shouldIndex(event.kind, tag)) continue
            val h = hasher.hash(tag[0], tag[1])
            out.add(layout.tagEntry(tag[0], tag[1], h, event.createdAt, event.id))
        }
        val exp = event.expiration()
        if (exp != null && exp > 0) {
            out.add(layout.expirationEntry(exp, event.id))
        }
        if (event is SearchableEvent) {
            for (token in FsSearchTokenizer.tokenize(event.indexableContent())) {
                out.add(layout.ftsEntry(token, event.createdAt, event.id))
            }
        }
        return out
    }

    /** Create every index hardlink for this event. Idempotent. */
    fun link(
        event: Event,
        canonical: Path,
    ) {
        for (path in pathsFor(event)) {
            createLink(path, canonical)
        }
    }

    /** Remove every index hardlink for this event. Missing entries ignored. */
    fun unlink(event: Event) {
        for (path in pathsFor(event)) {
            path.deleteIfExists()
        }
    }

    fun ownerHash(event: Event): Long =
        if (event is GiftWrapEvent) {
            event.recipientPubKey()?.let { hasher.hash(it) } ?: hasher.hash(event.pubKey)
        } else {
            hasher.hash(event.pubKey)
        }

    private fun createLink(
        link: Path,
        target: Path,
    ) {
        Files.createDirectories(link.parent)
        try {
            Files.createLink(link, target)
        } catch (_: FileAlreadyExistsException) {
            // Another insert raced us to the same (ts, id) slot. Since
            // filenames are canonical and events are immutable, the
            // existing entry is equivalent — no-op.
        }
    }
}
