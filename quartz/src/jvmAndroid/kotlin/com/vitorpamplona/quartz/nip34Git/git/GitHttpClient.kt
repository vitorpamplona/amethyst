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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

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
        val branch = head.symrefTarget?.removePrefix("refs/heads/") ?: ref?.removePrefix("refs/heads/")

        val pack =
            transport.fetchPack(
                cloneUrl = cloneUrl,
                caps = caps,
                wants = listOf(head.oid),
                deepen = 1,
                // When the server can't filter, a shallow fetch already pulls every blob, so the
                // snapshot is fully self-contained and no lazy per-file fetch is needed.
                filterBlobNone = caps.supportsFilter,
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

        return GitRepoSnapshot(
            cloneUrl = cloneUrl,
            headCommit = head.oid,
            branch = branch,
            rootTreeOid = rootTreeOid,
            trees = trees,
            blobs = blobs,
            transport = transport,
            caps = caps,
        )
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
    private val rootTreeOid: String,
    private val trees: Map<String, List<GitTreeEntry>>,
    blobs: Map<String, ByteArray>,
    private val transport: GitSmartHttpTransport,
    private val caps: GitCapabilities,
) {
    private val blobs = HashMap<String, ByteArray>(blobs)
    private val blobMutex = Mutex()

    /** Entries at the repository root, folders first. */
    fun rootEntries(): List<GitTreeEntry> = sortForDisplay(trees[rootTreeOid].orEmpty())

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
                    filterBlobNone = caps.supportsFilter,
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
