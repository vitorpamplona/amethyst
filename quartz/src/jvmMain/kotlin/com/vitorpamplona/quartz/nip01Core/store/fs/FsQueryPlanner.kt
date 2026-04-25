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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Picks a driver index for a filter and streams candidate ids in
 * `createdAt` DESC order. The orchestrator re-reads the full JSON and
 * passes it through [Filter.match] for the final predicate check.
 *
 * Step-2 coverage:
 *  - `ids`            → direct canonical opens
 *  - `tagsAll`/`tags` → tag index union (first key)
 *  - `kinds`          → kind index union
 *  - `authors`        → author index union
 *  - otherwise        → full scan via every `idx/kind/<k>/` subtree
 *
 * The planner is intentionally dumb about selectivity — "first available
 * driver wins". A cost-based picker (smallest listing) can slot in
 * later without changing callers. All FilterMatcher semantics (tag
 * AND/OR, since/until, id, author, kind cross-checks) are enforced in
 * the orchestrator, so picking a loose driver is correctness-safe.
 */
internal class FsQueryPlanner(
    private val layout: FsLayout,
    private val hasher: TagNameValueHasher,
) {
    data class Candidate(
        val createdAt: Long,
        val id: HexKey,
    )

    fun plan(filter: Filter): Sequence<Candidate> {
        filter.ids?.let { ids ->
            return idsDriver(ids)
        }

        // Slot shortcut: for replaceable/addressable kinds with authors
        // pinned (and d-tag for addressable), the filesystem already holds
        // the winning event at a deterministic path. One stat + one read
        // per (kind, pubkey[, d]) triple — no directory walk, no sort.
        slotShortcut(filter)?.let { return it }

        // NIP-50 search drives by FTS first when present so the AND
        // intersection of token sets is the smallest possible candidate
        // pool. All other predicates are post-filtered via Filter.match.
        filter.search?.takeIf { it.isNotBlank() }?.let { search ->
            return ftsDriver(search)
        }

        firstTagKey(filter)?.let { (name, values) ->
            return mergeDesc(values.map { v -> walkDir(layout.tagValueDir(name, hasher.hash(name, v))) })
        }

        filter.kinds?.let { kinds ->
            return mergeDesc(kinds.map { walkDir(layout.kindDir(it)) })
        }

        filter.authors?.let { authors ->
            return mergeDesc(authors.map { walkDir(layout.authorDir(it)) })
        }

        return allKindsDriver()
    }

    /**
     * Direct-slot driver for replaceable / addressable queries. Returns
     * null when the filter doesn't fit the shortcut, so the caller can
     * fall through to the generic directory walk. Applies when:
     *
     *   - `authors` is set,
     *   - `kinds` is set and every kind is either replaceable or
     *     addressable (not a mix with non-unique kinds),
     *   - for addressable kinds, a `d`-tag filter is supplied,
     *   - no `search` clause (FTS wouldn't benefit from this).
     *
     * The biggest win is for `profileOf` / `relaysOf` / `contactsOf`
     * style reads (`Filter(authors=[pk], kinds=[0])` etc.) — those go
     * from "list `idx/kind/0/` and sort" to "one `exists()` + one
     * `readText`", even against a store with millions of kind-0 events.
     */
    private fun slotShortcut(filter: Filter): Sequence<Candidate>? {
        val kinds = filter.kinds?.takeIf { it.isNotEmpty() } ?: return null
        val authors = filter.authors?.takeIf { it.isNotEmpty() } ?: return null
        if (!filter.search.isNullOrBlank()) return null

        val allReplaceable = kinds.all { it.isReplaceable() }
        val allAddressable = kinds.all { it.isAddressable() }
        if (!allReplaceable && !allAddressable) return null

        val dTags: List<String>? =
            if (allAddressable) {
                (filter.tagsAll?.get("d") ?: filter.tags?.get("d"))?.takeIf { it.isNotEmpty() }
                    ?: return null // addressable without d-tag → can't resolve a slot
            } else {
                null
            }

        return sequence {
            val found = ArrayList<Candidate>(kinds.size * authors.size)
            for (kind in kinds) {
                for (pk in authors) {
                    if (allReplaceable) {
                        readSlot(layout.replaceableSlot(kind, pk))?.let(found::add)
                    } else {
                        for (d in dTags!!) {
                            readSlot(layout.addressableSlot(kind, pk, d))?.let(found::add)
                        }
                    }
                }
            }
            found.sortByDescending { it.createdAt }
            yieldAll(found)
        }
    }

    /**
     * Read a slot file into a `Candidate`. Parses enough of the JSON to
     * extract `id` and `createdAt`. Returns null for missing slots or
     * unreadable files — stale/corrupt slots are dropped silently, same
     * behaviour as the directory walkers.
     */
    private fun readSlot(path: Path): Candidate? {
        if (!Files.exists(path)) return null
        return try {
            val event =
                com.vitorpamplona.quartz.nip01Core.core.Event
                    .fromJson(Files.readString(path))
            Candidate(event.createdAt, event.id)
        } catch (_: java.io.IOException) {
            null
        } catch (_: com.fasterxml.jackson.core.JacksonException) {
            null
        }
    }

    // ---- drivers ------------------------------------------------------

    /**
     * For pure id lookups we don't have a timestamp in the filename — read
     * `mtime`, which [FsEventStore] sets to `event.createdAt` on write.
     * Cheap (one `stat` per id) and avoids opening/parsing the JSON just
     * to order the stream.
     */
    private fun idsDriver(ids: List<HexKey>): Sequence<Candidate> =
        sequence {
            val out = ArrayList<Candidate>(ids.size)
            for (id in ids) {
                val p = layout.canonical(id)
                if (!p.exists()) continue
                val ts =
                    try {
                        Files.getLastModifiedTime(p).to(java.util.concurrent.TimeUnit.SECONDS)
                    } catch (_: java.nio.file.NoSuchFileException) {
                        continue
                    }
                out.add(Candidate(ts, id))
            }
            out.sortByDescending { it.createdAt }
            yieldAll(out)
        }

    private fun allKindsDriver(): Sequence<Candidate> =
        sequence {
            if (!Files.isDirectory(layout.idxKind)) return@sequence
            val subs = Files.list(layout.idxKind).use { it.toList() }
            yieldAll(mergeDesc(subs.map { walkDir(it) }))
        }

    /**
     * NIP-50 driver. Tokenises the search string, then streams candidates
     * DESC by `createdAt` from the SMALLEST token's listing and confirms
     * each one against every other token's dir via a single `exists()`
     * probe per (candidate, token). Works because every FTS hardlink for
     * a given event shares the same `<padded_ts>-<id>` filename — so we
     * can stat-check membership without materialising other tokens'
     * listings.
     *
     * Memory: O(smallest_token_size) for the driver's sorted filenames;
     * we never build a `HashMap<HexKey, ...>` for the popular tokens.
     * AND semantics across tokens match SQLite FTS5 default `MATCH`.
     */
    private fun ftsDriver(search: String): Sequence<Candidate> =
        sequence {
            val tokens = FsSearchTokenizer.tokenize(search)
            if (tokens.isEmpty()) return@sequence

            // Count each token dir so the smallest drives the walk.
            // Counting reads filenames only, not event JSON — cheap.
            val sizes =
                tokens.map { token ->
                    val dir = layout.ftsTokenDir(token)
                    val size =
                        if (Files.isDirectory(dir)) {
                            Files.list(dir).use { it.count() }
                        } else {
                            0L
                        }
                    TokenInfo(dir, size)
                }
            if (sizes.any { it.size == 0L }) return@sequence
            val sorted = sizes.sortedBy { it.size }
            val probeDirs = sorted.drop(1).map { it.dir }

            walkDir(sorted[0].dir).forEach { candidate ->
                val name = FsLayout.entryName(candidate.createdAt, candidate.id)
                if (probeDirs.all { Files.exists(it.resolve(name)) }) {
                    yield(candidate)
                }
            }
        }

    private class TokenInfo(
        val dir: Path,
        val size: Long,
    )

    // ---- index directory walker --------------------------------------

    /**
     * Yields the contents of [dir] as `Candidate`s in `createdAt` DESC
     * order. Relies on the `<padded_ts>-<id>` filename convention: lex-
     * reverse sort of filenames == chronological DESC, so we sort name
     * strings (cheap) instead of `Candidate` objects (bigger).
     */
    private fun walkDir(dir: Path): Sequence<Candidate> =
        sequence {
            if (!Files.isDirectory(dir)) return@sequence
            val sortedNames =
                Files.list(dir).use { stream ->
                    stream.map { it.fileName.toString() }.sorted(Comparator.reverseOrder()).toList()
                }
            for (name in sortedNames) {
                val parsed = FsLayout.parseEntry(name) ?: continue
                yield(Candidate(parsed.first, parsed.second))
            }
        }

    // ---- k-way merge via max-heap on head createdAt ------------------

    /**
     * Lazy k-way merge of pre-sorted (DESC) candidate streams, deduped
     * by id. Memory is O(streams) — one head per input stream plus the
     * seen-id set. `limit` in the caller short-circuits naturally
     * because we yield one candidate per heap pop; we never materialise
     * the full result.
     */
    private fun mergeDesc(streams: List<Sequence<Candidate>>): Sequence<Candidate> =
        sequence {
            if (streams.isEmpty()) return@sequence
            if (streams.size == 1) {
                val seen = HashSet<HexKey>()
                streams[0].forEach { if (seen.add(it.id)) yield(it) }
                return@sequence
            }
            val heap = java.util.PriorityQueue(compareByDescending<Head> { it.top.createdAt })
            for (s in streams) {
                val it = s.iterator()
                if (it.hasNext()) heap.offer(Head(it, it.next()))
            }
            val seen = HashSet<HexKey>()
            while (heap.isNotEmpty()) {
                val h = heap.poll()
                if (seen.add(h.top.id)) yield(h.top)
                if (h.it.hasNext()) {
                    h.top = h.it.next()
                    heap.offer(h)
                }
            }
        }

    /** Stream head for the k-way merge. */
    private class Head(
        val it: Iterator<Candidate>,
        var top: Candidate,
    )

    // ---- helpers ------------------------------------------------------

    /** First tag filter with at least one value, preferring `tagsAll`. */
    private fun firstTagKey(filter: Filter): Pair<String, List<String>>? {
        filter.tagsAll?.firstNonEmpty()?.let { return it }
        filter.tags?.firstNonEmpty()?.let { return it }
        return null
    }

    private fun Map<String, List<String>>.firstNonEmpty(): Pair<String, List<String>>? = entries.firstOrNull { it.value.isNotEmpty() }?.let { it.key to it.value }
}
