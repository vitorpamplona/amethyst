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
     * Pushes [dir]'s branch to its `origin` and opens a pull request into [base]. The push runs
     * as the user (their git credentials); the pull request as their `gh` login.
     */
    suspend fun open(
        dir: File,
        base: String,
        branch: String,
        title: String,
        body: String,
        draft: Boolean,
        gh: File? = find(),
    ): Result =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext Result.Failed("The GitHub CLI (gh) is not installed")
            val push = GitWorkspace.run(dir, listOf("git", "push", "--set-upstream", "origin", branch), timeoutSeconds = 300)
            if (push.code != 0) return@withContext Result.Failed(push.output.trim().takeLast(2000))
            val command =
                buildList {
                    add(gh.path)
                    add("pr")
                    add("create")
                    add("--base")
                    add(base)
                    add("--head")
                    add(branch)
                    add("--title")
                    add(title)
                    add("--body")
                    add(body)
                    if (draft) add("--draft")
                }
            val created = GitWorkspace.run(dir, command, timeoutSeconds = 120)
            when (val result = parseCreated(created.code, created.output)) {
                is Result.Failed -> result.copy(pushed = true)
                is Result.Opened -> result
            }
        }

    /** The pull request [branch] opened, or null when it has none (or `gh` can't say). */
    suspend fun state(
        dir: File,
        branch: String,
        gh: File? = find(),
    ): PullRequestInfo? =
        withContext(Dispatchers.IO) {
            gh ?: return@withContext null
            val result = GitWorkspace.run(dir, listOf(gh.path, "pr", "view", branch, "--json", "url,state,isDraft"), timeoutSeconds = 60)
            if (result.code != 0) null else parseState(result.output)
        }

    /** `gh pr create` prints the new pull request's URL last. */
    internal fun parseCreated(
        code: Int,
        output: String,
    ): Result {
        val url = output.lines().lastOrNull { it.trim().startsWith("https://") }?.trim()
        return if (code == 0 && url != null) Result.Opened(url) else Result.Failed(output.trim().ifEmpty { "gh exited with code $code" }.takeLast(2000))
    }

    internal fun parseState(output: String): PullRequestInfo? {
        val start = output.indexOf('{')
        if (start < 0) return null
        val obj =
            try {
                Json.parseToJsonElement(output.substring(start)) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return null
        val url = (obj["url"] as? JsonPrimitive)?.content ?: return null
        return PullRequestInfo(url, (obj["state"] as? JsonPrimitive)?.content ?: "OPEN", (obj["isDraft"] as? JsonPrimitive)?.booleanOrNull ?: false)
    }
}
