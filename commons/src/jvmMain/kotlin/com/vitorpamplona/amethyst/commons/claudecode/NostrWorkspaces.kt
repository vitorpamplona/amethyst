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

import com.vitorpamplona.amethyst.commons.util.appDataDir
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException

/** What a session started from nostr works on: the repository, and maybe one issue or proposal. */
data class NostrWorkTarget(
    val repo: NostrRepoRef,
    /** The repository's clone URLs, GRASP servers first. */
    val cloneUrls: List<String>,
    /** The maintainers' default branch (from their signed state), e.g. `main`. */
    val defaultBranch: String?,
    val issueId: String? = null,
    /** A proposal to continue: its event id, the commit it is at, where that commit is served, its branch. */
    val proposalId: String? = null,
    val proposalTip: String? = null,
    val proposalCloneUrls: List<String> = emptyList(),
    val proposalBranch: String? = null,
)

/** A worktree ready for a session, and the branch it is on. */
data class PreparedWorkspace(
    val dir: File,
    val branch: String,
    val base: String,
)

/**
 * Local checkouts of NIP-34 repositories, and the worktrees sessions run in. The mapping from a
 * repository to the folder the user keeps it in is remembered in the app's data folder; a checkout
 * whose `nostr://` remote (ngit) or clone URL names the repository is found without asking.
 */
class NostrWorkspaces(
    private val file: File = File(appDataDir, "claude-code-checkouts.json"),
) {
    private val json = Json { prettyPrint = true }

    @Synchronized
    private fun load(): Map<String, String> =
        try {
            if (!file.isFile) {
                emptyMap()
            } else {
                (json.parseToJsonElement(file.readText()) as? JsonObject)
                    ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
                    ?.toMap()
                    .orEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $file", e)
            emptyMap()
        }

    @Synchronized
    fun remember(
        repo: NostrRepoRef,
        dir: File,
    ) {
        val all = load() + (repo.toConfig() to dir.absolutePath)
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create $parent")
        file.writeText(json.encodeToString(JsonObject.serializer(), JsonObject(all.mapValues { JsonPrimitive(it.value) })))
    }

    /**
     * The folder this computer keeps [repo] in: the one remembered, else one of [candidates] (the
     * folders sessions ran in) whose remotes name it.
     */
    suspend fun find(
        repo: NostrRepoRef,
        cloneUrls: List<String>,
        candidates: List<File>,
    ): File? =
        withContext(Dispatchers.IO) {
            load()[repo.toConfig()]?.let { File(it) }?.takeIf { isRepository(it) }?.let { return@withContext it }
            val wanted = cloneUrls.map(::normalize).toSet()
            candidates
                .mapNotNull { GitWorkspace.git(it, "rev-parse", "--show-toplevel")?.trim()?.let(::File) }
                .distinct()
                .firstOrNull { dir ->
                    GitWorkspace.nostrRemoteRepo(dir) == repo || GitWorkspace.remoteUrls(dir).any { normalize(it) in wanted }
                }?.also { remember(repo, it) }
        }

    /** Whether [dir] is inside a git checkout. */
    fun isRepository(dir: File): Boolean = dir.isDirectory && GitWorkspace.git(dir, "rev-parse", "--show-toplevel") != null

    /** Clones [repo] from the first of [cloneUrls] that answers into [into], and remembers it. */
    suspend fun clone(
        repo: NostrRepoRef,
        cloneUrls: List<String>,
        into: File,
    ): File =
        withContext(Dispatchers.IO) {
            if (into.exists() && (into.list()?.isNotEmpty() == true)) throw IOException("$into is not empty")
            val parent = into.absoluteFile.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create $parent")
            var lastError = "no clone URL"
            for (url in cloneUrls) {
                val result = GitWorkspace.run(parent ?: File("."), listOf("git", "clone", url, into.absolutePath), timeoutSeconds = 1800)
                if (result.code == 0) {
                    remember(repo, into)
                    return@withContext into
                }
                lastError = result.output
                    .trim()
                    .lines()
                    .lastOrNull() ?: "git clone failed"
            }
            throw IOException(lastError)
        }

    /**
     * Makes the worktree a session runs in: fetches the repository's [NostrWorkTarget.defaultBranch]
     * into `refs/remotes/nostr/<branch>`, and creates [branch] from it, or from the proposal's tip
     * when continuing one, in `<checkout>-worktrees/<branch>`. The branch remembers the repository,
     * issue or proposal, and its base, in its git config.
     */
    suspend fun prepare(
        checkout: File,
        target: NostrWorkTarget,
        branch: String,
    ): PreparedWorkspace =
        withContext(Dispatchers.IO) {
            val root = GitWorkspace.git(checkout, "rev-parse", "--show-toplevel")?.trim()?.let(::File) ?: throw IOException("$checkout is not a git checkout")
            val mainBranch = target.defaultBranch ?: "main"
            val base = "nostr/$mainBranch"
            val fetched =
                target.cloneUrls.any { url ->
                    GitWorkspace.run(root, listOf("git", "fetch", "--no-tags", url, "+refs/heads/$mainBranch:refs/remotes/$base"), timeoutSeconds = 600).code == 0
                }
            val baseRef =
                if (fetched) {
                    base
                } else {
                    // Offline or no server answered: start from what the checkout has.
                    listOf("origin/$mainBranch", mainBranch).firstOrNull { GitWorkspace.git(root, "rev-parse", "--verify", "--quiet", it) != null }
                        ?: throw IOException("Could not fetch $mainBranch from the repository's servers")
                }

            val startPoint =
                target.proposalTip?.let { tip ->
                    fetchCommit(root, tip, target.proposalCloneUrls + target.cloneUrls, target.proposalId)
                    tip
                } ?: baseRef

            val worktrees = File(root.absoluteFile.parentFile, root.name + "-worktrees")
            if (!worktrees.isDirectory && !worktrees.mkdirs()) throw IOException("Could not create $worktrees")
            val dir = File(worktrees, branch.replace('/', '-'))
            if (dir.exists()) throw IOException("$dir already exists")
            val add = GitWorkspace.run(root, listOf("git", "worktree", "add", "-b", branch, dir.absolutePath, startPoint))
            if (add.code != 0) throw IOException(add.output.trim().ifEmpty { "git worktree add failed" })

            GitWorkspace.link(dir, branch, repo = target.repo, issueId = target.issueId, proposalId = target.proposalId, base = baseRef)
            PreparedWorkspace(dir, branch, baseRef)
        }

    /**
     * Gets [commit] into the checkout. GRASP servers serve a proposal under `refs/nostr/<event id>`;
     * other hosts usually let a client fetch any commit they hold by its id.
     */
    private fun fetchCommit(
        root: File,
        commit: String,
        urls: List<String>,
        proposalId: String?,
    ) {
        fun has() = GitWorkspace.git(root, "cat-file", "-e", "$commit^{commit}") != null
        if (has()) return
        for (url in urls.distinct()) {
            GitWorkspace.run(root, listOf("git", "fetch", "--no-tags", url, commit), timeoutSeconds = 600)
            if (has()) return
            // Every refs/nostr/* that points at the commit (the proposal's, or one of its updates').
            val refs =
                GitWorkspace
                    .git(root, "ls-remote", url, "refs/nostr/*")
                    .orEmpty()
                    .lines()
                    .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[0] == commit }?.get(1) }
            val wanted = refs.ifEmpty { listOfNotNull(proposalId?.let { "refs/nostr/$it" }) }
            wanted.forEach { ref -> GitWorkspace.run(root, listOf("git", "fetch", "--no-tags", url, ref), timeoutSeconds = 600) }
            if (has()) return
        }
        throw IOException("Could not fetch the proposal's commit ${commit.take(8)}")
    }

    companion object {
        private const val TAG = "NostrWorkspaces"

        /** `pr/feat/<words of the title>`, as ngit names proposal branches. */
        fun branchFor(title: String?): String {
            val slug =
                title
                    .orEmpty()
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                    .split('-')
                    .filter { it.isNotEmpty() }
                    .take(6)
                    .joinToString("-")
            return "pr/feat/" + slug.ifEmpty { "claude-" + System.currentTimeMillis() / 1000 }
        }

        /** A clone URL compared without scheme, credentials, `.git` or a trailing slash. */
        fun normalize(url: String): String =
            url
                .trim()
                .lowercase()
                .substringAfter("://")
                .substringAfter('@')
                .replace(':', '/')
                .removeSuffix("/")
                .removeSuffix(".git")
    }
}
