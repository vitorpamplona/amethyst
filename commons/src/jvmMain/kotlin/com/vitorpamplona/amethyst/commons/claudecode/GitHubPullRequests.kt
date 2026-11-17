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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File

/** A pull request a workspace branch opened. */
@Immutable
data class PullRequestInfo(
    val url: String,
    /** `OPEN`, `MERGED` or `CLOSED`, as GitHub says. */
    val state: String,
    val isDraft: Boolean,
) {
    val isOpen get() = state == "OPEN"
    val isMerged get() = state == "MERGED"
}

/**
 * GitHub pull requests through the user's own `gh` (GitHub CLI), signed in as them. Only the
 * user's click runs these: an agent may neither push nor open a pull request
 * ([ClaudeChat.NO_PUBLISHING]).
 */
object GitHubPullRequests {
    sealed interface Result {
        data class Opened(
            val url: String,
            /** The fork the branch went to, `owner/name`, when the user can't push to the repository. */
            val fork: String? = null,
        ) : Result

        data class Failed(
            val reason: String,
            /** The branch reached the remote before `gh` failed: only the pull request is missing. */
            val pushed: Boolean = false,
        ) : Result
    }

    /** The `gh` program: on PATH, or where its installers put it. */
    fun find(environment: Map<String, String> = System.getenv()): File? {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val names = if (windows) listOf("gh.exe", "gh") else listOf("gh")
        val dirs =
            environment["PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() } +
                listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "C:\\Program Files\\GitHub CLI")
        return dirs.firstNotNullOfOrNull { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() } }
    }

    /**
     * Pushes [dir]'s branch and opens a pull request into [base]. The push runs as the user (their
     * git credentials); the pull request as their `gh` login. When [upstream] (`owner/name` on
     * GitHub) is one the user can't push to, the branch goes to their fork of it, made if needed,
     * and the pull request opens from there.
     */
    suspend fun open(
        dir: File,
        base: String,
        branch: String,
        title: String,
        body: String,
        draft: Boolean,
        upstream: String? = null,
        gh: File? = find(),
        forkUrl: (String) -> String = { "https://github.com/$it.git" },
    ): Result =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext Result.Failed("The GitHub CLI (gh) is not installed")
            val fork =
                if (upstream != null && needsFork(permission(dir, upstream, gh))) {
                    fork(dir, upstream, gh) ?: return@withContext Result.Failed("Could not fork $upstream")
                } else {
                    null
                }
            val push =
                if (fork != null) {
                    // A remote of its own: `origin` stays the repository the clone came from.
                    val url = forkUrl(fork)
                    if (GitWorkspace.run(dir, listOf("git", "remote", "set-url", FORK_REMOTE, url)).code != 0) {
                        GitWorkspace.run(dir, listOf("git", "remote", "add", FORK_REMOTE, url))
                    }
                    GitWorkspace.run(dir, listOf("git", "push", "--set-upstream", FORK_REMOTE, branch), timeoutSeconds = 300)
                } else {
                    GitWorkspace.run(dir, listOf("git", "push", "--set-upstream", "origin", branch), timeoutSeconds = 300)
                }
            if (push.code != 0) return@withContext Result.Failed(push.output.trim().takeLast(2000))
            val command =
                buildList {
                    add(gh.path)
                    add("pr")
                    add("create")
                    if (upstream != null) {
                        add("--repo")
                        add(upstream)
                    }
                    add("--base")
                    add(base)
                    add("--head")
                    add(if (fork != null) fork.substringBefore('/') + ":" + branch else branch)
                    add("--title")
                    add(title)
                    add("--body")
                    add(body)
                    if (draft) add("--draft")
                }
            val created = runGh(dir, command, 120)
            when (val result = parseCreated(created.code, created.output)) {
                is Result.Failed -> result.copy(pushed = true)
                is Result.Opened -> result.copy(fork = fork)
            }
        }

    /** What the user may do on [upstream] (`ADMIN`, `MAINTAIN`, `WRITE`, `TRIAGE`, `READ`), or null when `gh` can't say. */
    suspend fun permission(
        dir: File,
        upstream: String,
        gh: File? = find(),
    ): String? =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext null
            val result = runGh(dir, listOf(gh.path, "repo", "view", upstream, "--json", "viewerPermission", "--jq", ".viewerPermission"), 60)
            result.output
                .trim()
                .takeIf { result.code == 0 && it.isNotEmpty() && it.all { c -> c.isLetter() || c == '_' } }
        }

    /** Whether a branch must go to a fork: the user can't push to the repository. Unknown is not "can't". */
    fun needsFork(permission: String?): Boolean = permission != null && permission !in PUSH_PERMISSIONS

    /**
     * The user's fork of [upstream], `owner/name`: made now, or the one they have. `gh repo fork`
     * answers with the existing fork when there is one.
     */
    suspend fun fork(
        dir: File,
        upstream: String,
        gh: File? = find(),
    ): String? =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext null
            val forked = runGh(dir, listOf(gh.path, "repo", "fork", upstream, "--clone=false", "--remote=false"), 120)
            if (forked.code != 0) return@withContext null
            val login =
                runGh(dir, listOf(gh.path, "api", "user", "--jq", ".login"), 60)
                    .takeIf { it.code == 0 }
                    ?.output
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.contains(' ') } ?: return@withContext null
            // gh names the fork after the repository; it says so when it already existed under that name.
            parseForkName(forked.output) ?: (login + "/" + upstream.substringAfter('/'))
        }

    /** The fork `gh repo fork` names in its output ("Created fork me/app", "me/app already exists"). */
    internal fun parseForkName(output: String): String? =
        Regex("""Created fork\s+([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)""").find(output)?.groupValues?.get(1)
            ?: Regex("""([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)\s+already exists""").find(output)?.groupValues?.get(1)

    /**
     * The pull request [branch] opened, or null when it has none (or `gh` can't say). One seen
     * before ([knownUrl]) is asked for by its URL. Otherwise, with [upstream], it is looked up
     * there by branch, whether the branch was pushed to the repository itself or to a fork; and
     * only among heads in the repository or the user's fork, since anyone's fork may have a
     * branch of the same name.
     */
    suspend fun state(
        dir: File,
        branch: String,
        gh: File? = find(),
        upstream: String? = null,
        knownUrl: String? = null,
    ): PullRequestInfo? =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext null
            val owners = if (knownUrl == null && upstream != null) headOwners(dir, upstream) else null
            val command =
                when {
                    knownUrl != null -> listOf(gh.path, "pr", "view", knownUrl, "--json", "url,state,isDraft")
                    upstream != null -> listOf(gh.path, "pr", "list", "--repo", upstream, "--head", branch, "--state", "all", "--limit", "30", "--json", "url,state,isDraft,headRepositoryOwner")
                    else -> listOf(gh.path, "pr", "view", branch, "--json", "url,state,isDraft")
                }
            val result = runGh(dir, command, 60)
            if (result.code != 0) null else parseState(result.output, owners)
        }

    /** Whose branches can be [dir]'s pull request head: [upstream]'s owner, and the owner of the clone's fork, if it has one. */
    internal fun headOwners(
        dir: File,
        upstream: String,
    ): Set<String> =
        listOfNotNull(
            upstream.substringBefore('/'),
            GitWorkspace
                .git(dir, "remote", "get-url", FORK_REMOTE)
                ?.trim()
                ?.let(::gitHubRepo)
                ?.substringBefore('/'),
        ).mapTo(HashSet()) { it.lowercase() }

    /** `owner/name` of a GitHub repository URL, or null for any other host. */
    fun gitHubRepo(url: String): String? {
        val clean = url.trim().lowercase()
        val onGitHub = clean.startsWith("https://github.com/") || clean.startsWith("http://github.com/") || clean.startsWith("git@github.com:") || clean.startsWith("ssh://git@github.com/")
        return if (onGitHub) AgentWorkspaces.ownerAndName(url).takeIf { it.count { c -> c == '/' } == 1 } else null
    }

    /** How `gh` runs: the user's program. Tests stand in for it; git itself always runs for real. */
    @Volatile
    internal var runGh: (File, List<String>, Long) -> GitWorkspace.Result = { dir, command, timeout -> GitWorkspace.run(dir, command, timeoutSeconds = timeout) }

    private fun headOwnerOf(pr: JsonObject): String? = ((pr["headRepositoryOwner"] as? JsonObject)?.get("login") as? JsonPrimitive)?.content

    private const val FORK_REMOTE = "fork"
    private val PUSH_PERMISSIONS = setOf("ADMIN", "MAINTAIN", "WRITE")

    /** `gh pr create` prints the new pull request's URL last. */
    internal fun parseCreated(
        code: Int,
        output: String,
    ): Result {
        val url = output.lines().lastOrNull { it.trim().startsWith("https://") }?.trim()
        return if (code == 0 && url != null) Result.Opened(url) else Result.Failed(output.trim().ifEmpty { "gh exited with code $code" }.takeLast(2000))
    }

    /**
     * `gh pr view`'s object, or the first of `gh pr list`'s array whose head is in a repository
     * one of [owners] owns (any, when null).
     */
    internal fun parseState(
        output: String,
        owners: Set<String>? = null,
    ): PullRequestInfo? {
        val start = output.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val element =
            try {
                Json.parseToJsonElement(output.substring(start))
            } catch (e: IllegalArgumentException) {
                null
            }
        val obj =
            (element as? JsonObject) ?: (element as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.firstOrNull { pr -> owners == null || headOwnerOf(pr)?.lowercase() in owners } ?: return null
        val url = (obj["url"] as? JsonPrimitive)?.content ?: return null
        return PullRequestInfo(url, (obj["state"] as? JsonPrimitive)?.content ?: "OPEN", (obj["isDraft"] as? JsonPrimitive)?.booleanOrNull ?: false)
    }
}
