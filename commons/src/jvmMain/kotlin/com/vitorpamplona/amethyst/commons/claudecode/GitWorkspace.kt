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
package com.vitorpamplona.amethyst.commons.claudecode

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A NIP-34 repository, as its announcement's address (kind 30617, maintainer, `d` tag). */
@Immutable
data class NostrRepoRef(
    val pubKeyHex: String,
    val identifier: String,
    val kind: Int = REPOSITORY_KIND,
) {
    /** `30617:<pubkey>:<d>`, the form kept in git config. */
    fun toConfig() = "$kind:$pubKeyHex:$identifier"

    companion object {
        const val REPOSITORY_KIND = 30617

        fun fromConfig(value: String?): NostrRepoRef? {
            val parts = value?.split(':', limit = 3) ?: return null
            if (parts.size != 3) return null
            return NostrRepoRef(parts[1], parts[2], parts[0].toIntOrNull() ?: return null)
        }

        /**
         * The repository a `nostr://` remote names, the way ngit writes them:
         * `nostr://<npub>/<relay>/<identifier>`, `nostr://<npub>/<identifier>` or `nostr://<naddr>`.
         */
        fun fromNostrUrl(url: String): NostrRepoRef? {
            if (!url.startsWith("nostr://")) return null
            val parts =
                url
                    .removePrefix("nostr://")
                    .trimEnd('/')
                    .split('/')
                    .filter { it.isNotBlank() }
            val first = parts.firstOrNull() ?: return null
            return when (val entity = Nip19Parser.uriToRoute(first)?.entity) {
                is NAddress -> NostrRepoRef(entity.author, entity.dTag, entity.kind)
                is NPub ->
                    parts
                        .drop(1)
                        .lastOrNull()
                        ?.removeSuffix(".git")
                        ?.let { NostrRepoRef(entity.hex, it) }
                else -> null
            }
        }
    }
}

/** A commit on the session's branch that its base doesn't have. */
@Immutable
data class GitCommitLine(
    val sha: String,
    val subject: String,
    val body: String,
)

/** Where a session's folder stands in git: its branch, what it changed, and what it is linked to. */
@Immutable
data class GitWorkInfo(
    val root: String,
    val branch: String?,
    /** What the branch is compared with, e.g. `origin/main`. */
    val base: String?,
    val ahead: Int,
    val behind: Int,
    val commits: List<GitCommitLine>,
    /** Files changed since the base, uncommitted changes included. */
    val filesChanged: Int,
    val insertions: Int,
    val deletions: Int,
    /** Files with changes not committed yet. */
    val uncommitted: Int,
    /** The NIP-34 repository this checkout is: from a `nostr://` remote or the branch's link. */
    val repo: NostrRepoRef?,
    /** The proposal (kind 1618 event id) this branch was published as, or was checked out from. */
    val proposalId: String?,
    /** The issue (kind 1621 event id) this branch works on. */
    val issueId: String?,
    /** Review comments on the proposal already sent to a session on this branch. */
    val reviewed: Set<String> = emptySet(),
) {
    val hasChanges: Boolean get() = filesChanged > 0 || ahead > 0
}

/**
 * Reads and sets what [GitWorkInfo] holds by running `git` in the folder. The links between a
 * branch and its nostr repository, issue and proposal live in the branch's own config
 * (`branch.<name>.nostr-repo`, `nostr-issue`, `nostr-proposal`), so any checkout of the branch,
 * and the next session on it, finds them.
 */
object GitWorkspace {
    suspend fun inspect(dir: File): GitWorkInfo? =
        withContext(Dispatchers.IO) {
            val root = git(dir, "rev-parse", "--show-toplevel")?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
            val branch = git(dir, "branch", "--show-current")?.trim()?.takeIf { it.isNotEmpty() }
            val base = baseOf(dir, branch)
            val mergeBase = base?.let { git(dir, "merge-base", it, "HEAD")?.trim()?.takeIf { s -> s.isNotEmpty() } }

            val (behind, ahead) =
                base
                    ?.let { git(dir, "rev-list", "--left-right", "--count", "$it...HEAD") }
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] } ?: (0 to 0)

            val commits =
                if (base != null && ahead > 0) {
                    git(dir, "log", "--format=%H%x1f%s%x1f%b%x1e", "$base..HEAD")
                        .orEmpty()
                        .split('\u001e')
                        .mapNotNull { record ->
                            val f = record.trim().split('\u001f')
                            if (f.size < 2) null else GitCommitLine(f[0], f[1], f.getOrElse(2) { "" }.trim())
                        }
                } else {
                    emptyList()
                }

            val stat = git(dir, "diff", "--shortstat", mergeBase ?: "HEAD").orEmpty()
            val uncommitted = git(dir, "status", "--porcelain").orEmpty().lines().count { it.isNotBlank() }

            val config = { key: String -> branch?.let { git(dir, "config", "--get", "branch.$it.$key")?.trim()?.takeIf { v -> v.isNotEmpty() } } }

            GitWorkInfo(
                root = root,
                branch = branch,
                base = base,
                ahead = ahead,
                behind = behind,
                commits = commits,
                filesChanged =
                    SHORTSTAT_FILES
                        .find(stat)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0,
                insertions =
                    SHORTSTAT_INS
                        .find(stat)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0,
                deletions =
                    SHORTSTAT_DEL
                        .find(stat)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0,
                uncommitted = uncommitted,
                repo = NostrRepoRef.fromConfig(config(KEY_REPO)) ?: nostrRemoteRepo(dir),
                proposalId = config(KEY_PROPOSAL),
                issueId = config(KEY_ISSUE),
                reviewed =
                    branch
                        ?.let { git(dir, "config", "--get-all", "branch.$it.$KEY_REVIEWED") }
                        .orEmpty()
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
            )
        }

    /** The diff of everything the branch changed since its base, uncommitted work included. */
    suspend fun diff(dir: File): String =
        withContext(Dispatchers.IO) {
            val branch = git(dir, "branch", "--show-current")?.trim()?.takeIf { it.isNotEmpty() }
            val base = baseOf(dir, branch)
            val from = base?.let { git(dir, "merge-base", it, "HEAD")?.trim() }?.takeIf { it.isNotEmpty() } ?: "HEAD"
            git(dir, "diff", "--no-color", "--find-renames", from).orEmpty()
        }

    /** What isn't committed yet: tracked files against HEAD. */
    suspend fun uncommittedDiff(dir: File): String =
        withContext(Dispatchers.IO) {
            git(dir, "diff", "--no-color", "--find-renames", "HEAD").orEmpty()
        }

    /** What one commit changed. */
    suspend fun commitDiff(
        dir: File,
        sha: String,
    ): String =
        withContext(Dispatchers.IO) {
            git(dir, "show", "--no-color", "--find-renames", "--format=", sha).orEmpty()
        }

    /**
     * Files git doesn't track yet, as diffs that add them, so they read like the rest of the
     * change. Binary files and large ones are left out (they stay in [untracked]'s list).
     */
    suspend fun untrackedDiff(
        dir: File,
        paths: List<String>,
    ): String =
        withContext(Dispatchers.IO) {
            buildString {
                paths.forEach { path ->
                    val file = File(dir, path)
                    if (!file.isFile || file.length() > UNTRACKED_SHOWN) return@forEach
                    val bytes =
                        try {
                            file.readBytes()
                        } catch (e: IOException) {
                            return@forEach
                        }
                    if (bytes.contains(0)) return@forEach
                    val diff = LineDiff.unified(emptyList(), ToolViews.lines(bytes.decodeToString()), 1, 1)
                    if (diff.hunks.isEmpty()) return@forEach
                    append("diff --git a/")
                        .append(path)
                        .append(" b/")
                        .append(path)
                        .append('\n')
                    append("new file mode 100644\n--- /dev/null\n+++ b/").append(path).append('\n')
                    append(diff.hunks)
                }
            }
        }

    /** Files git doesn't track yet, which a plain diff leaves out. */
    suspend fun untracked(dir: File): List<String> =
        withContext(Dispatchers.IO) {
            git(dir, "ls-files", "--others", "--exclude-standard").orEmpty().lines().filter { it.isNotBlank() }
        }

    suspend fun link(
        dir: File,
        branch: String,
        repo: NostrRepoRef? = null,
        issueId: String? = null,
        proposalId: String? = null,
        base: String? = null,
    ) = withContext(Dispatchers.IO) {
        repo?.let { setConfig(dir, "branch.$branch.$KEY_REPO", it.toConfig()) }
        issueId?.let { setConfig(dir, "branch.$branch.$KEY_ISSUE", it) }
        proposalId?.let { setConfig(dir, "branch.$branch.$KEY_PROPOSAL", it) }
        base?.let { setConfig(dir, "branch.$branch.$KEY_BASE", it) }
    }

    /**
     * Lines [from]..[to] of [path] as of [rev] (a commit, or `HEAD`), numbered from 1; null when
     * the file or the commit isn't in the checkout.
     */
    suspend fun linesAt(
        dir: File,
        rev: String,
        path: String,
        from: Int,
        to: Int,
    ): List<Pair<Int, String>>? =
        withContext(Dispatchers.IO) {
            val text = git(dir, "show", "$rev:$path") ?: return@withContext null
            // A file ends with a newline; that is not one more (empty) line.
            val lines = text.removeSuffix("\n").split('\n')
            val first = from.coerceAtLeast(1)
            val last = to.coerceAtMost(lines.size)
            if (first > last) null else (first..last).map { it to lines[it - 1] }
        }

    /** The commit the branch forked from its base: where a comment on a removed line reads. */
    suspend fun mergeBase(dir: File): String? =
        withContext(Dispatchers.IO) {
            val branch = git(dir, "branch", "--show-current")?.trim()?.takeIf { it.isNotEmpty() }
            val base = baseOf(dir, branch) ?: return@withContext null
            git(dir, "merge-base", base, "HEAD")?.trim()?.takeIf { it.isNotEmpty() }
        }

    /** Remembers that the review comments [ids] were sent to Claude on this branch. */
    suspend fun markReviewed(
        dir: File,
        branch: String,
        ids: Collection<String>,
    ) = withContext(Dispatchers.IO) {
        ids.forEach { run(dir, listOf("git", "config", "--add", "branch.$branch.$KEY_REVIEWED", it)) }
    }

    /**
     * A folder for the app's own files about this checkout (logs), inside its git dir so they
     * never show as changes or get committed. Each worktree has its own. Null when [dir] is not
     * in a repository.
     */
    fun privateDir(dir: File): File? =
        git(dir, "rev-parse", "--absolute-git-dir")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { File(it, "amethyst") }

    /** The repository a `nostr://` remote of the checkout points at, as ngit sets them up. */
    fun nostrRemoteRepo(dir: File): NostrRepoRef? =
        git(dir, "remote", "-v")
            .orEmpty()
            .lines()
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(1) }
            .firstNotNullOfOrNull { NostrRepoRef.fromNostrUrl(it) }

    /** The URLs of the checkout's remotes, to match against a repository's clone URLs. */
    fun remoteUrls(dir: File): List<String> =
        git(dir, "remote", "-v")
            .orEmpty()
            .lines()
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(1) }
            .distinct()

    /**
     * What the branch is compared with: the base it was started from, else the remote's default
     * branch (`origin/HEAD`), else `main` or `master`.
     */
    private fun baseOf(
        dir: File,
        branch: String?,
    ): String? {
        branch?.let { git(dir, "config", "--get", "branch.$it.$KEY_BASE")?.trim()?.takeIf { v -> v.isNotEmpty() } }?.let { return it }
        git(dir, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return listOf("origin/main", "origin/master", "main", "master")
            .firstOrNull { git(dir, "rev-parse", "--verify", "--quiet", it) != null }
            ?.takeIf { it != branch }
    }

    private fun setConfig(
        dir: File,
        key: String,
        value: String,
    ) {
        if (run(dir, listOf("git", "config", key, value)).code != 0) throw IOException("git config $key failed")
    }

    /** Runs git; its output, or null when it failed. */
    private const val UNTRACKED_SHOWN = 256_000L

    internal fun git(
        dir: File,
        vararg args: String,
    ): String? {
        val result = run(dir, listOf("git") + args)
        return if (result.code == 0) result.output else null
    }

    internal class Result(
        val code: Int,
        val output: String,
    )

    internal fun run(
        dir: File,
        command: List<String>,
        timeoutSeconds: Long = 60,
    ): Result =
        try {
            val process =
                ProcessBuilder(command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Result(-1, "${command.first()} timed out")
            } else {
                Result(process.exitValue(), output)
            }
        } catch (e: IOException) {
            Result(-1, e.message ?: "${command.first()} could not run")
        }

    const val KEY_REPO = "nostr-repo"
    const val KEY_ISSUE = "nostr-issue"
    const val KEY_PROPOSAL = "nostr-proposal"
    const val KEY_BASE = "amethyst-base"
    const val KEY_REVIEWED = "nostr-reviewed"

    private val SHORTSTAT_FILES = Regex("(\\d+) files? changed")
    private val SHORTSTAT_INS = Regex("(\\d+) insertions?")
    private val SHORTSTAT_DEL = Regex("(\\d+) deletions?")
}
