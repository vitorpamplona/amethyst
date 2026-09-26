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
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip01Core.tags.isIndexableTagName
import com.vitorpamplona.quartz.nipXXSql.AggregatePlan
import com.vitorpamplona.quartz.nipXXSql.FilterStoreBackend
import com.vitorpamplona.quartz.nipXXSql.ScanSpec
import com.vitorpamplona.quartz.nipXXSql.SqlProfile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * How [FsEventStore] answers NIP-FF (NQL) natively. Every index entry of an
 * event is named `<ts>-<id>` in every tree it sits in, so the directory
 * listings alone say which events match a kind, author or tag, and when, without
 * opening a JSON file: one tree drives, and the other conditions are `exists`
 * probes of the same name in their trees (the FTS driver's trick).
 *
 * That answers, with no event read:
 *  - id walks ([idsAndTimes]): the NIP-77 snapshots and id listings;
 *  - `count(*)`, alone or grouped by `kind` or `pubkey`, with `min` / `max` of
 *    `created_at`;
 *  - `DISTINCT t1` of an indexed tag name, from its value directories (a hashed
 *    one costs one read to recover its value).
 *
 * Everything else reads events through the store's own query, like any store.
 *
 * An entry is trusted without checking its canonical file: deletion unlinks the
 * entries before the file, so an entry outliving its event only comes from a
 * failed unlink, which the store's own reads would return too (the file is still
 * there) and `scrub` clears. The check would triple the cost of a count.
 */
internal class FsSqlBackend(
    store: IEventStore,
    private val layout: FsLayout,
    private val hasher: TagNameValueHasher,
    private val indexingStrategy: IndexingStrategy,
    private val readEvent: (HexKey) -> Event?,
) : FilterStoreBackend(store) {
    /** One matching event, from its index entry name. */
    private class Entry(
        val createdAt: Long,
        val id: HexKey,
        val name: String,
    )

    override suspend fun idsAndTimes(
        spec: ScanSpec,
        onEach: (id: String, createdAt: Long) -> Unit,
    ): Boolean {
        if (spec.table != SqlProfile.EVENTS) return false
        val walk = entries(spec, drive = Drive.ANY) ?: return false
        if (spec.limit == null) {
            walk.forEach { onEach(it.id, it.createdAt) }
        } else {
            // The newest `limit`, ties in any order (the executor completes the tie group).
            walk.sortedByDescending { it.createdAt }.take(spec.limit!!).forEach { onEach(it.id, it.createdAt) }
        }
        return true
    }

    override suspend fun aggregate(plan: AggregatePlan): List<List<Any?>>? {
        val spec = plan.scan
        if (spec.table == SqlProfile.TAGS) return distinctTagValues(plan)
        // count(*), min / max(created_at): everything the entry names hold.
        val supported = plan.aggregates.all { (it.function == "count" && it.column == null) || (it.function in MIN_MAX && it.column == "created_at") }
        if (!supported) return null
        val drive: Drive
        val key: (Path) -> Any?
        when (plan.groupBy) {
            emptyList<String>() -> {
                drive = Drive.ANY
                key = { null }
            }
            listOf("kind") -> {
                drive = Drive.KIND
                key = { dir -> dir.fileName.toString().toLong() }
            }
            listOf("pubkey") -> {
                drive = Drive.AUTHOR
                key = { dir -> dir.fileName.toString() }
            }
            else -> return null
        }
        val groups = LinkedHashMap<Any?, LongArray>()
        val walk = entries(spec, drive) { dir, entry -> fold(groups.getOrPut(key(dir)) { longArrayOf(0, Long.MAX_VALUE, Long.MIN_VALUE) }, entry) } ?: return null
        walk.forEach { }
        if (plan.groupBy.isEmpty() && groups.isEmpty()) groups[null] = longArrayOf(0, Long.MAX_VALUE, Long.MIN_VALUE)
        return groups.map { (k, acc) ->
            val row = ArrayList<Any?>()
            if (plan.groupBy.isNotEmpty()) row.add(k)
            plan.aggregates.forEach { a ->
                row.add(
                    when (a.function) {
                        "count" -> acc[0]
                        "min" -> if (acc[0] == 0L) null else acc[1]
                        else -> if (acc[0] == 0L) null else acc[2]
                    },
                )
            }
            row
        }
    }

    private fun fold(
        acc: LongArray,
        e: Entry,
    ) {
        acc[0]++
        if (e.createdAt < acc[1]) acc[1] = e.createdAt
        if (e.createdAt > acc[2]) acc[2] = e.createdAt
    }

    private enum class Drive { ANY, KIND, AUTHOR }

    /**
     * The entries of the events [spec] matches, each once, or null when the
     * indexes can't say (a condition no tree holds). [drive] picks the tree that
     * drives, so [onGroup] learns each entry's kind or author from its directory.
     */
    private fun entries(
        spec: ScanSpec,
        drive: Drive,
        onGroup: ((Path, Entry) -> Unit)? = null,
    ): Sequence<Entry>? {
        if (spec.table != SqlProfile.EVENTS || spec.tagName != null || spec.tagValues != null) return null
        val kindDirs = spec.kinds?.map { layout.kindDir(it) }
        val authorDirs = spec.authors?.map { layout.authorDir(it) }
        val ids = spec.ids

        val driving: List<Path>? =
            when (drive) {
                Drive.KIND -> kindDirs ?: children(layout.idxKind)
                Drive.AUTHOR -> authorDirs ?: children(layout.idxAuthor)
                Drive.ANY -> if (ids != null) null else authorDirs ?: kindDirs ?: children(layout.idxKind)
            }
        // Conditions the driving tree doesn't hold, checked by name in their trees.
        val probes = ArrayList<List<Path>>()
        if (kindDirs != null && driving !== kindDirs) probes.add(kindDirs)
        if (authorDirs != null && driving !== authorDirs) probes.add(authorDirs)

        fun keep(e: Entry): Boolean =
            (spec.since == null || e.createdAt >= spec.since!!) &&
                (spec.until == null || e.createdAt <= spec.until!!) &&
                (ids == null || e.id in ids) &&
                probes.all { dirs -> dirs.any { Files.exists(it.resolve(e.name)) } }

        if (driving == null) {
            // Ids: one stat each, as the store's own id driver does.
            return sequence {
                for (id in ids!!) {
                    val p = layout.canonical(id)
                    val ts = runCatching { Files.getLastModifiedTime(p).to(TimeUnit.SECONDS) }.getOrNull() ?: continue
                    val e = Entry(ts, id, FsLayout.entryName(ts, id))
                    if (keep(e)) yield(e)
                }
            }
        }
        return sequence {
            val seen = HashSet<HexKey>()
            for (dir in driving) {
                if (!Files.isDirectory(dir)) continue
                val names = Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }
                for (name in names) {
                    val (ts, id) = FsLayout.parseEntry(name) ?: continue
                    val e = Entry(ts, id, name)
                    if (!keep(e) || !seen.add(id)) continue
                    onGroup?.invoke(dir, e)
                    yield(e)
                }
            }
        }
    }

    private fun children(dir: Path): List<Path> = if (Files.isDirectory(dir)) Files.list(dir).use { it.toList() } else emptyList()

    /**
     * `SELECT DISTINCT t1 FROM tags WHERE t0 = <name> AND <t1 is non-empty or one of values> …`
     * from `idx/tag/<name>/`: a value is there if any of its entries passes the
     * event conditions. Only for the tags the default strategy indexes, all of them
     * with a value, so none is left out.
     */
    private fun distinctTagValues(plan: AggregatePlan): List<List<Any?>>? {
        val spec = plan.scan
        val name = spec.tagName ?: return null
        if (plan.groupBy != listOf("t1") || plan.aggregates.isNotEmpty()) return null
        if (indexingStrategy !is DefaultIndexingStrategy || !isIndexableTagName(name)) return null
        // Without these, a name-only tag's NULL t1 would be a group the index can't see.
        if (!spec.valueNonEmpty && spec.tagValues == null) return null
        if (spec.ids != null) return null

        val eventSpec = ScanSpec(SqlProfile.EVENTS, authors = spec.authors, kinds = spec.kinds, since = spec.since, until = spec.until)
        val kindDirs = eventSpec.kinds?.map { layout.kindDir(it) }
        val authorDirs = eventSpec.authors?.map { layout.authorDir(it) }

        fun passes(entry: String): Boolean {
            val (ts, _) = FsLayout.parseEntry(entry) ?: return false
            return (eventSpec.since == null || ts >= eventSpec.since!!) &&
                (eventSpec.until == null || ts <= eventSpec.until!!) &&
                (kindDirs == null || kindDirs.any { Files.exists(it.resolve(entry)) }) &&
                (authorDirs == null || authorDirs.any { Files.exists(it.resolve(entry)) })
        }

        val valueDirs =
            spec.tagValues?.map { layout.tagValueDir(name, it, hasher.hash(name, it)) }
                ?: children(layout.tagDir(name))
        val out = LinkedHashSet<String>()
        for (dir in valueDirs) {
            if (!Files.isDirectory(dir)) continue
            val entries = Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }
            val hit = entries.firstOrNull { passes(it) } ?: continue
            val dirName = dir.fileName.toString()
            val values =
                if (!dirName.startsWith(FsLayout.HASH_PREFIX)) {
                    listOf(dirName)
                } else {
                    // A hashed directory: read its event for the values whose hash it is.
                    val (_, id) = FsLayout.parseEntry(hit)!!
                    val event = readEvent(id) ?: continue
                    event.tags
                        .filter { it.size >= 2 && it[0] == name && FsLayout.tagValueDirName(it[1], hasher.hash(name, it[1])) == dirName }
                        .map { it[1] }
                }
            values.forEach { v -> if ((!spec.valueNonEmpty || v.isNotEmpty()) && (spec.tagValues == null || v in spec.tagValues!!)) out.add(v) }
        }
        return out.map { listOf(it) }
    }

    private companion object {
        val MIN_MAX = setOf("min", "max")
    }
}
