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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** One repository of the workspace on the pull requests screen. */
@Immutable
data class WorkspaceRepoRow(
    val status: WorkspaceRepoStatus,
    /** The pull request its branch opened, once there is one. */
    val pullRequest: PullRequestInfo?,
    /** Title and description for a new pull request: from its commits. */
    val draftTitle: String,
    val draftBody: String,
    /** The NIP-34 repository it was cloned from: its changes go back as a proposal. */
    val nostr: NostrRepoRef? = null,
    /** `owner/name` on GitHub, when it is there: where a pull request opens. */
    val gitHub: String? = null,
    /** The user can't push to it: the branch goes to their fork. */
    val viaFork: Boolean = false,
) {
    val name get() = status.repo.name
}

/** Why a repository's pull request didn't open: `gh`'s or git's words, and whether the push got through. */
@Immutable
data class PullRequestError(
    val reason: String,
    val pushed: Boolean,
) {
    /** The first line, for the card; the rest behind "Details". */
    val summary: String get() = reason.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: reason

    val hasDetails: Boolean get() = reason.trim().contains('\n')
}

/** A workspace session's repositories and their pull requests. */
class ClaudeCodeWorkspaceViewModel(
    private val service: ClaudeCodeService,
    key: String,
) : ViewModel() {
    val chat: ClaudeChat? = service.chat(key)
    val workspace: AgentWorkspace? =
        chat
            ?.state
            ?.value
            ?.cwd
            ?.let { service.agentWorkspaces.of(File(it)) }

    private val _rows = MutableStateFlow<List<WorkspaceRepoRow>>(emptyList())
    val rows: StateFlow<List<WorkspaceRepoRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** The repository whose pull request is being opened, and why the last one failed. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = _opening.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, PullRequestError>>(emptyMap())

    /** By repository folder; the workspace's own (removing it) under "". */
    val errors: StateFlow<Map<String, PullRequestError>> = _errors.asStateFlow()

    /** Whether the user's `gh` is installed: without it, pull requests can't be opened from here. */
    val hasGh: Boolean = GitHubPullRequests.find() != null

    init {
        refresh()
    }

    fun refresh() {
        val ws = workspace ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                _rows.value =
                    service.agentWorkspaces.status(ws).map { status ->
                        val dir = File(ws.dir, status.repo.name)
                        val subjects =
                            GitWorkspace
                                .git(dir, "log", "--format=%s", "origin/${status.repo.baseBranch}..HEAD")
                                ?.lines()
                                ?.filter { it.isNotBlank() }
                                .orEmpty()
                        val gitHub = GitHubPullRequests.gitHubRepo(status.repo.url)
                        val pr = GitHubPullRequests.state(dir, status.repo.branch, upstream = gitHub, knownUrl = ws.pullRequests[status.repo.name]?.url)
                        // The session list shows it from the saved record, without asking GitHub.
                        if (pr != null) service.recordPullRequest(ws.id, status.repo.name, pr)
                        WorkspaceRepoRow(
                            status = status,
                            pullRequest = pr,
                            draftTitle = subjects.lastOrNull() ?: ws.title,
                            draftBody = subjects.reversed().joinToString("\n") { "- $it" },
                            nostr = NostrRepoRef.fromConfig(GitWorkspace.git(dir, "config", "--get", "branch.${status.repo.branch}.${GitWorkspace.KEY_REPO}")?.trim()),
                            gitHub = gitHub,
                            viaFork = gitHub != null && pr == null && GitHubPullRequests.needsFork(GitHubPullRequests.permission(dir, gitHub)),
                        )
                    }
            } finally {
                _loading.value = false
            }
        }
    }

    /** Pushes the repository's branch and opens its pull request, on the user's click. */
    fun openPullRequest(
        repo: String,
        title: String,
        body: String,
        draft: Boolean,
    ) {
        val ws = workspace ?: return
        val row = _rows.value.firstOrNull { it.name == repo } ?: return
        if (_opening.value != null) return
        _opening.value = repo
        _errors.update { it - repo }
        viewModelScope.launch {
            try {
                when (val result = GitHubPullRequests.open(File(ws.dir, repo), row.status.repo.baseBranch, row.status.repo.branch, title.trim(), body.trim(), draft, upstream = row.gitHub)) {
                    is GitHubPullRequests.Result.Failed -> {
                        _errors.update { it + (repo to PullRequestError(result.reason, result.pushed)) }
                        // gh may have failed because the pull request already exists: it shows.
                        if (result.pushed) refresh()
                    }
                    is GitHubPullRequests.Result.Opened -> refresh()
                }
            } finally {
                _opening.value = null
            }
        }
    }

    /** Asks the agent to commit what it changed in [repo], so the branch has something to propose. */
    fun askToCommit(repo: String) {
        chat?.send("Commit the changes in the `$repo` folder on the current branch, with a clear message. Don't push.")
    }

    /** Ends the session and removes its clones; the mirrors stay for the next one. */
    fun deleteWorkspace(onDone: () -> Unit) {
        val ws = workspace ?: return
        viewModelScope.launch {
            try {
                service.deleteWorkspace(ws)
                onDone()
            } catch (e: IOException) {
                Log.w("ClaudeCodeWorkspace", "Could not remove the workspace", e)
                _errors.update { it + ("" to PullRequestError(e.message ?: ws.dir, pushed = false)) }
            }
        }
    }
}
