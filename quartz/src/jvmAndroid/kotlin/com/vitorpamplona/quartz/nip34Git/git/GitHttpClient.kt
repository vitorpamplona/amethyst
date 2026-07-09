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
package com.vitorpamplona.quartz.nip34Git.git

import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitFileChange
import com.vitorpamplona.quartz.nip34Git.patch.LineDiff
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.PriorityQueue

/**
 * High-level read-only browser for a git repository served over smart-HTTP.
 *
 * [open] downloads a shallow, blob-less snapshot of the default branch tip (one
 * request fetches the commit and every tree), returning a [GitRepoSnapshot] that
 * can walk directories offline and lazily fetch individual file blobs on demand.
 */
class GitHttpClient(
    okHttpClient: (String) -> OkHttpClient,
) {
    private val transport = GitSmartHttpTransport(okHttpClient)

    /**
     * Connects to [cloneUrl] and loads the tree of [ref] (default: the server's
     * HEAD branch). Only `http(s)` git endpoints are supported.
     */
    suspend fun open(
        cloneUrl: String,
        ref: String? = null,
    ): GitRepoSnapshot {
        require(cloneUrl.startsWith("http://") || cloneUrl.startsWith("https://")) {
            "unsupported git transport (only http/https): $cloneUrl"
        }

        val caps = transport.fetchCapabilities(cloneUrl)
        if (!caps.supportsLsRefs || !caps.supportsFetch) {
            throw GitHttpException("server does not speak git protocol v2 (ls-refs/fetch)")
        }

        val refs = transport.lsRefs(cloneUrl, caps)
        val head = selectHead(refs, ref) ?: throw GitHttpException("could not resolve a branch to browse")
        val branch = head.symrefTarget?.removePrefix("refs/heads/") ?: ref?.removePrefix("refs/heads/") ?: ref

        val branches = refs.mapNotNull { it.name.removePrefix("refs/heads/").takeIf { _ -> it.name.startsWith("refs/heads/") } }.distinct().sorted()
        val tags = refs.mapNotNull { it.name.removePrefix("refs/tags/").takeIf { _ -> it.name.startsWith("refs/tags/") } }.distinct().sorted()

        val pack =
            transport.fetchPack(
                cloneUrl = cloneUrl,
                caps = caps,
                wants = listOf(head.oid),
                deepen = 1,
                // When the server can't filter, a shallow fetch already pulls every blob, so the
                // snapshot is fully self-contained and no lazy per-file fetch is needed.
                filter = "blob:none",
            )
        val objects = Packfile.parse(pack)

        val commit = objects[head.oid] ?: throw GitHttpException("tip commit ${head.oid} missing from pack")
        if (commit.type != GitObjectType.COMMIT) throw GitHttpException("tip ${head.oid} is not a commit")
        val rootTreeOid =
            GitObjectParser.parseCommitTree(commit.data)
                ?: throw GitHttpException("commit ${head.oid} has no tree")

        val trees = HashMap<String, List<GitTreeEntry>>()
        val blobs = HashMap<String, ByteArray>()
        for ((oid, obj) in objects) {
            when (obj.type) {
                GitObjectType.TREE -> trees[oid] = GitObjectParser.parseTree(obj.data)
                GitObjectType.BLOB -> blobs[oid] = obj.data
                else -> {}
            }
        }

        val tipCommit = runCatching { GitObjectParser.parseCommit(head.oid, commit.data) }.getOrNull()

        return GitRepoSnapshot(
            cloneUrl = cloneUrl,
            headCommit = head.oid,
            branch = branch,
            branches = branches,
            tags = tags,
            rootTreeOid = rootTreeOid,
            trees = trees,
            blobs = blobs,
            transport = transport,
            caps = caps,
            tipCommit = tipCommit,
        )
    }

    /**
     * Loads up to [depth] commits of history starting at [startCommit] (or the
     * server's HEAD), most recent first. Uses a shallow `tree:0` fetch so only the
     * commit objects come down — no trees or blobs. The shallow boundary caps how
     * far back the log goes.
     */
    suspend fun loadHistory(
        cloneUrl: String,
        startCommit: String?,
        depth: Int = 50,
    ): List<GitCommit> {
        require(cloneUrl.startsWith("http://") || cloneUrl.startsWith("https://")) {
            "unsupported git transport (only http/https): $cloneUrl"
        }
        val caps = transport.fetchCapabilities(cloneUrl)
        if (!caps.supportsFetch) throw GitHttpException("server does not speak git protocol v2 (fetch)")

        val start =
            startCommit?.takeIf { it.isNotBlank() }
                ?: run {
                    if (!caps.supportsLsRefs) throw GitHttpException("server can't list refs")
                    val refs = transport.lsRefs(cloneUrl, caps)
                    selectHead(refs, null)?.oid ?: throw GitHttpException("could not resolve HEAD")
                }

        val pack =
            transport.fetchPack(
                cloneUrl = cloneUrl,
                caps = caps,
                wants = listOf(start),
                deepen = depth,
                // tree:0 → commits only; if the server can't filter, blob:none still works (we ignore the trees).
                filter = if (caps.supportsFilter) "tree:0" else "blob:none",
            )
        val objects = Packfile.parse(pack)
        val commits = HashMap<String, GitCommit>()
        for (obj in objects.values) {
            if (obj.type == GitObjectType.COMMIT) {
                val commit = GitObjectParser.parseCommit(obj.oid, obj.data)
                commits[commit.oid] = commit
            }
        }

        // Walk parents most-recent-first (like `git log`), bounded by depth and the shallow set.
        val result = ArrayList<GitCommit>()
        val visited = HashSet<String>()
        val frontier = PriorityQueue<GitCommit>(compareByDescending { it.authorTimeSec })
        commits[start]?.let {
            frontier.add(it)
            visited.add(start)
        }
        while (frontier.isNotEmpty() && result.size < depth) {
            val commit = frontier.poll()!!
            result.add(commit)
            for (parent in commit.parents) {
                if (parent !in visited) {
                    commits[parent]?.let {
                        frontier.add(it)
                        visited.add(parent)
                    }
                }
            }
        }
        return result
    }

    /**
     * Computes the diff a pull request introduces: the changes between
     * [baseCommit] (or, when null, the server's HEAD) and [headCommit]. Fetches
     * both commit trees, finds the changed files by oid, batch-fetches the
     * differing blobs and runs a line diff on each. Returns a [ParsedPatch] ready
     * for the same renderer the embedded-patch path uses.
     */
    suspend fun computeDiff(
        cloneUrl: String,
        headCommit: String,
        baseCommit: String?,
    ): ParsedPatch {
        require(cloneUrl.startsWith("http://") || cloneUrl.startsWith("https://")) {
            "unsupported git transport (only http/https): $cloneUrl"
        }
        val caps = transport.fetchCapabilities(cloneUrl)
        if (!caps.supportsFetch) throw GitHttpException("server does not speak git protocol v2 (fetch)")

        val base =
            baseCommit?.takeIf { it.isNotBlank() }
                ?: run {
                    if (!caps.supportsLsRefs) throw GitHttpException("no merge base and server can't list refs")
                    val refs = transport.lsRefs(cloneUrl, caps)
                    selectHead(refs, null)?.oid ?: throw GitHttpException("could not resolve a base commit")
                }

        // 1. Both commit trees (and, when the server can't filter, every blob too).
        val treePack =
            transport.fetchPack(
                cloneUrl = cloneUrl,
                caps = caps,
                wants = listOf(headCommit, base).distinct(),
                deepen = 1,
                filter = "blob:none",
            )
        val objects = Packfile.parse(treePack)

        val headTreeOid = commitTreeOf(objects, headCommit)
        val baseTreeOid = commitTreeOf(objects, base)
        val trees = HashMap<String, List<GitTreeEntry>>()
        val blobs = HashMap<String, ByteArray>()
        for ((oid, obj) in objects) {
            when (obj.type) {
                GitObjectType.TREE -> trees[oid] = GitObjectParser.parseTree(obj.data)
                GitObjectType.BLOB -> blobs[oid] = obj.data
                else -> {}
            }
        }

        val headFiles = collectFiles(trees, headTreeOid)
        val baseFiles = collectFiles(trees, baseTreeOid)

        // 2. Classify changes and gather the blob oids we still need.
        data class Change(
            val path: String,
            val oldOid: String?,
            val newOid: String?,
            val change: GitFileChange,
        )
        val changes = ArrayList<Change>()
        for (path in (headFiles.keys + baseFiles.keys).toSortedSet()) {
            val oldOid = baseFiles[path]
            val newOid = headFiles[path]
            when {
                oldOid == null && newOid != null -> changes.add(Change(path, null, newOid, GitFileChange.ADD))
                newOid == null && oldOid != null -> changes.add(Change(path, oldOid, null, GitFileChange.DELETE))
                oldOid != null && newOid != null && oldOid != newOid ->
                    changes.add(Change(path, oldOid, newOid, GitFileChange.MODIFY))
            }
        }

        val needed = changes.flatMap { listOfNotNull(it.oldOid, it.newOid) }.filter { it !in blobs }.distinct()
        if (needed.isNotEmpty() && caps.supportsFilter) {
            val blobPack = transport.fetchPack(cloneUrl, caps, needed, deepen = null, filter = "blob:none")
            for ((oid, obj) in Packfile.parse(blobPack)) {
                if (obj.type == GitObjectType.BLOB) blobs[oid] = obj.data
            }
        }

        // 3. Line-diff every changed file.
        val files =
            changes.map { change ->
                val oldBytes = change.oldOid?.let { blobs[it] }
                val newBytes = change.newOid?.let { blobs[it] }
                val binary = (oldBytes?.let(::isBinary) == true) || (newBytes?.let(::isBinary) == true)
                val hunks =
                    if (binary) {
                        emptyList()
                    } else {
                        LineDiff.hunks(toLines(oldBytes), toLines(newBytes))
                    }
                GitDiffFile(
                    oldPath = if (change.change == GitFileChange.ADD) null else change.path,
                    newPath = if (change.change == GitFileChange.DELETE) null else change.path,
                    change = change.change,
                    isBinary = binary,
                    hunks = hunks,
                )
            }

        return ParsedPatch(message = "", files = files)
    }

    private fun commitTreeOf(
        objects: Map<String, GitObject>,
        commitOid: String,
    ): String {
        val commit = objects[commitOid] ?: throw GitHttpException("commit $commitOid missing from pack")
        if (commit.type != GitObjectType.COMMIT) throw GitHttpException("$commitOid is not a commit")
        return GitObjectParser.parseCommitTree(commit.data) ?: throw GitHttpException("commit $commitOid has no tree")
    }

    private fun collectFiles(
        trees: Map<String, List<GitTreeEntry>>,
        rootTreeOid: String,
    ): Map<String, String> {
        val out = HashMap<String, String>()

        fun walk(
            treeOid: String,
            prefix: String,
        ) {
            val entries = trees[treeOid] ?: return
            for (entry in entries) {
                val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                when {
                    entry.isFolder -> walk(entry.oid, path)
                    entry.isSubmodule -> {} // submodule pointers aren't file content
                    else -> out[path] = entry.oid
                }
            }
        }
        walk(rootTreeOid, "")
        return out
    }

    private fun toLines(bytes: ByteArray?): List<String> {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        val text = bytes.decodeToString()
        val parts = text.split("\n")
        return if (text.endsWith("\n")) parts.dropLast(1) else parts
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8000)
        for (i in 0 until limit) if (bytes[i].toInt() == 0) return true
        return false
    }

    /** Picks the ref to browse: an explicit [ref] if given, otherwise HEAD. */
    private fun selectHead(
        refs: List<GitRef>,
        ref: String?,
    ): GitRef? {
        if (ref != null) {
            return refs.firstOrNull { it.name == ref } ?: refs.firstOrNull { it.name == "refs/heads/$ref" }
        }
        refs.firstOrNull { it.name == "HEAD" }?.let { return it }
        return refs.firstOrNull { it.name == "refs/heads/main" }
            ?: refs.firstOrNull { it.name == "refs/heads/master" }
            ?: refs.firstOrNull { it.name.startsWith("refs/heads/") }
    }
}

/**
 * An offline-navigable snapshot of a repository tree plus lazy blob access.
 * Directory listings are resolved from the in-memory tree map; file contents are
 * fetched on demand (and cached) unless they were already pulled up-front.
 */
class GitRepoSnapshot(
    val cloneUrl: String,
    val headCommit: String,
    val branch: String?,
    val branches: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    private val rootTreeOid: String,
    private val trees: Map<String, List<GitTreeEntry>>,
    blobs: Map<String, ByteArray>,
    private val transport: GitSmartHttpTransport,
    private val caps: GitCapabilities,
    /** The tip commit object, parsed up-front so the UI can show it without another fetch. */
    val tipCommit: GitCommit? = null,
) {
    private val blobs = HashMap<String, ByteArray>(blobs)
    private val blobMutex = Mutex()

    /** Entries at the repository root, folders first. */
    fun rootEntries(): List<GitTreeEntry> = sortForDisplay(trees[rootTreeOid].orEmpty())

    /**
     * Every blob (file) path reachable from the tip tree, depth-first. Folders and submodules
     * are not included. Drives the home screen's language breakdown; the whole tree is already
     * in memory (the snapshot is fetched with `blob:none`, so all trees came down).
     */
    fun walkFileNames(): List<String> {
        val out = ArrayList<String>()
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootTreeOid to "")
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val (treeOid, prefix) = stack.removeLast()
            if (!seen.add(treeOid + "@" + prefix)) continue
            val entries = trees[treeOid] ?: continue
            for (e in entries) {
                if (e.isFolder) {
                    stack.addLast(e.oid to "$prefix${e.name}/")
                } else if (!e.isSubmodule) {
                    out.add("$prefix${e.name}")
                }
            }
        }
        return out
    }

    /**
     * Entries inside the directory at [path] (a list of path segments). Returns
     * null if the path doesn't resolve to a directory.
     */
    fun entriesAt(path: List<String>): List<GitTreeEntry>? {
        var treeOid = rootTreeOid
        for (segment in path) {
            val entry = trees[treeOid]?.firstOrNull { it.name == segment && it.isFolder } ?: return null
            treeOid = entry.oid
        }
        return trees[treeOid]?.let { sortForDisplay(it) }
    }

    /** Resolves the entry of a file (or folder) at the full [path], or null. */
    fun entryAt(path: List<String>): GitTreeEntry? {
        if (path.isEmpty()) return null
        val parent = entriesAt(path.dropLast(1)) ?: return null
        return parent.firstOrNull { it.name == path.last() }
    }

    /**
     * Returns up to [limit] full file paths whose file name contains [query]
     * (case-insensitive), searching the whole tree. Folders are walked but not
     * matched. The tree is fully present (blob-less), so this is offline.
     */
    fun searchFiles(
        query: String,
        limit: Int = 100,
    ): List<List<String>> {
        if (query.isBlank()) return emptyList()
        val needle = query.lowercase()
        val result = ArrayList<List<String>>()

        fun walk(
            treeOid: String,
            prefix: List<String>,
        ) {
            if (result.size >= limit) return
            val entries = trees[treeOid] ?: return
            for (entry in entries) {
                if (result.size >= limit) return
                val path = prefix + entry.name
                if (entry.isFolder) {
                    walk(entry.oid, path)
                } else if (entry.name.lowercase().contains(needle)) {
                    result.add(path)
                }
            }
        }
        walk(rootTreeOid, emptyList())
        return result
    }

    fun hasBlob(oid: String): Boolean = blobs.containsKey(oid)

    /** Returns the bytes of a blob, fetching (and caching) it if not already present. */
    suspend fun readBlob(oid: String): ByteArray {
        blobs[oid]?.let { return it }
        return blobMutex.withLock {
            blobs[oid]?.let { return it }
            val pack =
                transport.fetchPack(
                    cloneUrl = cloneUrl,
                    caps = caps,
                    wants = listOf(oid),
                    deepen = null,
                    filter = "blob:none",
                )
            val obj =
                Packfile.parse(pack)[oid]
                    ?: throw GitHttpException("blob $oid missing from fetch response")
            obj.data.also { blobs[oid] = it }
        }
    }

    private fun sortForDisplay(entries: List<GitTreeEntry>): List<GitTreeEntry> =
        entries.sortedWith(
            compareByDescending<GitTreeEntry> { it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}
