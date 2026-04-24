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

    // ---- index directory walker --------------------------------------

    private fun walkDir(dir: Path): Sequence<Candidate> =
        sequence {
            if (!Files.isDirectory(dir)) return@sequence
            val entries = Files.list(dir).use { it.toList() }
            entries
                .asSequence()
                .mapNotNull { FsLayout.parseEntry(it.fileName.toString()) }
                .map { Candidate(it.first, it.second) }
                .sortedByDescending { it.createdAt }
                .forEach { yield(it) }
        }

    // ---- k-way-ish merge (materialised; step-8 can turn into a heap) -

    private fun mergeDesc(streams: List<Sequence<Candidate>>): Sequence<Candidate> =
        sequence {
            if (streams.isEmpty()) return@sequence
            if (streams.size == 1) {
                val seen = HashSet<HexKey>()
                streams[0].forEach { if (seen.add(it.id)) yield(it) }
                return@sequence
            }
            val merged = ArrayList<Candidate>()
            streams.forEach { s -> s.forEach { merged.add(it) } }
            merged.sortByDescending { it.createdAt }
            val seen = HashSet<HexKey>()
            merged.forEach { if (seen.add(it.id)) yield(it) }
        }

    // ---- helpers ------------------------------------------------------

    /** First tag filter with at least one value, preferring `tagsAll`. */
    private fun firstTagKey(filter: Filter): Pair<String, List<String>>? {
        filter.tagsAll?.firstNonEmpty()?.let { return it }
        filter.tags?.firstNonEmpty()?.let { return it }
        return null
    }

    private fun Map<String, List<String>>.firstNonEmpty(): Pair<String, List<String>>? = entries.firstOrNull { it.value.isNotEmpty() }?.let { it.key to it.value }
}
