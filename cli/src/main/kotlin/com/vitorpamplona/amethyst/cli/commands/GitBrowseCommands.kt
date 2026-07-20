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

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip34Git.git.GitHttpClient
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.git.GitTreeEntry
import java.io.File

/**
 * `amy git browse|cat|log` — read a repository's actual git content over the
 * git smart-HTTP v2 protocol (the same [GitHttpClient] the Android repo browser
 * uses). Resolves the http(s) clone URL from the kind:30617 announcement and
 * does a shallow fetch, so this is read-only and needs a reachable git host.
 * This is the git-object read side of `nak git download` / a shallow `clone`;
 * pushing objects back is still out of scope (see cli/ROADMAP.md).
 */
object GitBrowseCommands {
    /** `git browse REPO [PATH]` — list the tree entries at PATH (default: repo root). */
    suspend fun browse(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val path = args.positionalOrNull(1).orEmpty()
        val ref = args.flag("ref")
        val cloneOverride = args.flag("clone")
        args.rejectUnknown("relay")

        return withSnapshot(dataDir, coord, ref, cloneOverride, args) { _, snapshot ->
            val segments = splitPath(path)
            val entries =
                if (segments.isEmpty()) {
                    snapshot.rootEntries()
                } else {
                    snapshot.entriesAt(segments)
                        ?: return@withSnapshot Output.error("not_found", "no directory at path '$path'")
                }
            Output.emit(
                mapOf(
                    "clone_url" to snapshot.cloneUrl,
                    "branch" to snapshot.branch,
                    "head_commit" to snapshot.headCommit,
                    "path" to path,
                    "count" to entries.size,
                    "entries" to entries.map(::entrySummary),
                ),
            )
            0
        }
    }

    /** `git cat REPO PATH` — print (or `--out FILE`) a file's contents at a ref. */
    suspend fun cat(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val path = args.positional(1, "file-path")
        val ref = args.flag("ref")
        val cloneOverride = args.flag("clone")
        val out = args.flag("out")
        args.rejectUnknown("relay")

        return withSnapshot(dataDir, coord, ref, cloneOverride, args) { _, snapshot ->
            val entry =
                snapshot.entryAt(splitPath(path))
                    ?: return@withSnapshot Output.error("not_found", "no file at path '$path'")
            if (entry.isFolder) return@withSnapshot Output.error("bad_args", "'$path' is a directory (use `git browse`)")
            val bytes = snapshot.readBlob(entry.oid)
            val binary = isBinary(bytes)
            if (out != null) {
                File(out).writeBytes(bytes)
            }
            Output.emit(
                mapOf(
                    "clone_url" to snapshot.cloneUrl,
                    "path" to path,
                    "oid" to entry.oid,
                    "size_bytes" to bytes.size,
                    "binary" to binary,
                    "written_to" to out,
                    // Text content is inlined only when it isn't binary and wasn't written to a file.
                    "content" to if (!binary && out == null) bytes.decodeToString() else null,
                ),
            )
            0
        }
    }

    /** `git log REPO` — recent commit history (most recent first). */
    suspend fun log(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val coord = args.positional(0, "repo-naddr-or-coordinates")
        val ref = args.flag("ref")
        val cloneOverride = args.flag("clone")
        val depth = args.intFlag("depth", 50)
        args.rejectUnknown("relay")

        return withSnapshot(dataDir, coord, ref, cloneOverride, args) { http, snapshot ->
            val commits = http.loadHistory(snapshot.cloneUrl, snapshot.headCommit, depth)
            Output.emit(
                mapOf(
                    "clone_url" to snapshot.cloneUrl,
                    "branch" to snapshot.branch,
                    "count" to commits.size,
                    "commits" to
                        commits.map {
                            mapOf(
                                "oid" to it.oid,
                                "short_oid" to it.shortOid,
                                "summary" to it.summary,
                                "author" to it.authorName,
                                "author_email" to it.authorEmail,
                                "author_time" to it.authorTimeSec,
                                "parents" to it.parents,
                            )
                        },
                ),
            )
            0
        }
    }

    // ------------------------------------------------------------------

    /**
     * Resolve the repo announcement, pick its http(s) clone URLs, open a shallow
     * snapshot (trying each candidate URL), and hand the client + snapshot to
     * [block]. Emits a `not_found` / `unreachable` error when the repo or a
     * working clone URL can't be resolved.
     */
    private suspend fun withSnapshot(
        dataDir: DataDir,
        coord: String,
        ref: String?,
        cloneOverride: String?,
        args: Args,
        block: suspend (GitHttpClient, GitRepoSnapshot) -> Int,
    ): Int {
        // REPO may be a raw http(s) clone URL, an naddr, or `kind:pubkey:id`.
        val directUrl = coord.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val addr =
            if (directUrl == null) {
                GitSupport.resolveAddress(coord)
                    ?: return Output.error("bad_args", "expected a clone URL, an naddr, or kind:pubkey:identifier")
            } else {
                null
            }

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val cloneUrls =
                when {
                    cloneOverride != null -> listOf(cloneOverride)
                    directUrl != null -> listOf(directUrl)
                    else -> {
                        val repo =
                            GitSupport.fetchRepo(ctx, addr!!, args)
                                ?: return Output.error("not_found", "no repository announcement found for $coord")
                        repo.clones()
                    }
                }
            val candidates = candidateUrls(cloneUrls)
            if (candidates.isEmpty()) {
                return Output.error("bad_args", "repository has no http(s) clone URL (pass --clone URL)")
            }
            val http = GitHttpClient { ctx.okhttp }
            val errors = StringBuilder()
            for (url in candidates) {
                val snapshot =
                    try {
                        http.open(url, ref)
                    } catch (e: Exception) {
                        errors
                            .append(url)
                            .append(" → ")
                            .append(e.message ?: e.toString())
                            .append('\n')
                        null
                    }
                if (snapshot != null) return block(http, snapshot)
            }
            return Output.error("unreachable", "could not clone any candidate URL:\n${errors.toString().trim()}")
        }
    }

    /** http(s) clone URLs to try, in order, adding a `.git` variant when missing. */
    private fun candidateUrls(cloneUrls: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in cloneUrls) {
            val url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            out.add(url)
            if (!url.removeSuffix("/").endsWith(".git")) out.add(url.removeSuffix("/") + ".git")
        }
        return out.toList()
    }

    private fun splitPath(path: String): List<String> =
        path
            .trim()
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }

    private fun entrySummary(entry: GitTreeEntry): Map<String, Any?> =
        mapOf(
            "name" to entry.name,
            "type" to
                when {
                    entry.isFolder -> "dir"
                    entry.isSubmodule -> "submodule"
                    entry.isSymlink -> "symlink"
                    else -> "file"
                },
            "oid" to entry.oid,
        )

    /** A blob is treated as binary when it contains a NUL byte in its head. */
    private fun isBinary(bytes: ByteArray): Boolean = bytes.take(8000).any { it.toInt() == 0 }
}
