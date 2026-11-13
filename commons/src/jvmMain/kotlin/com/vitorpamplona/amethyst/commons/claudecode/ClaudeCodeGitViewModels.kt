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
import com.vitorpamplona.claudecode.process.ClaudeExecutable
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException

/** Where the checkout for a session started from nostr stands. */
@Immutable
sealed interface CheckoutState {
    data object Looking : CheckoutState

    /** None found: the user points at one or clones the repository. */
    data object Missing : CheckoutState

    data class Found(
        val dir: File,
    ) : CheckoutState

    data object Cloning : CheckoutState
}

/** Why starting from nostr failed, worded by the UI. */
@Immutable
sealed interface StartError {
    data object NotARepository : StartError

    data class Failed(
        val message: String?,
    ) : StartError
}

/** Starts a session on a NIP-34 repository: finds or clones its checkout, then makes the worktree. */
class ClaudeCodeStartViewModel(
    private val service: ClaudeCodeService,
) : ViewModel() {
    private val _checkout = MutableStateFlow<CheckoutState>(CheckoutState.Looking)
    val checkout: StateFlow<CheckoutState> = _checkout.asStateFlow()

    private val _error = MutableStateFlow<StartError?>(null)
    val error: StateFlow<StartError?> = _error.asStateFlow()

    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    private var lookedFor: NostrRepoRef? = null

    fun find(
        repo: NostrRepoRef,
        cloneUrls: List<String>,
    ) {
        if (lookedFor == repo && _checkout.value !is CheckoutState.Looking) return
        lookedFor = repo
        viewModelScope.launch {
            service.refresh()
            val found = service.workspaces.find(repo, cloneUrls, service.knownFolders())
            _checkout.value = found?.let { CheckoutState.Found(it) } ?: CheckoutState.Missing
        }
    }

    /** The user's own checkout of the repository. */
    fun useFolder(
        repo: NostrRepoRef,
        path: String,
    ) {
        val dir = File(expandHome(path))
        if (!service.workspaces.isRepository(dir)) {
            _error.value = StartError.NotARepository
            return
        }
        _error.value = null
        try {
            service.workspaces.remember(repo, dir)
        } catch (e: IOException) {
            Log.w(TAG, "Could not remember the checkout", e)
        }
        _checkout.value = CheckoutState.Found(dir)
    }

    fun clone(
        repo: NostrRepoRef,
        cloneUrls: List<String>,
        path: String,
    ) {
        _checkout.value = CheckoutState.Cloning
        _error.value = null
        viewModelScope.launch {
            _checkout.value =
                try {
                    CheckoutState.Found(service.workspaces.clone(repo, cloneUrls, File(expandHome(path))))
                } catch (e: IOException) {
                    _error.value = StartError.Failed(e.message)
                    CheckoutState.Missing
                }
        }
    }

    fun start(
        target: NostrWorkTarget,
        branch: String,
        permissionMode: PermissionMode,
        prompt: String,
        onReady: (String) -> Unit,
    ) {
        val dir = (_checkout.value as? CheckoutState.Found)?.dir ?: return
        if (_starting.value) return
        _starting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                onReady(service.startFromNostr(dir, target, branch.trim(), permissionMode, prompt).key)
            } catch (e: IOException) {
                _error.value = StartError.Failed(e.message)
            } finally {
                _starting.value = false
            }
        }
    }

    companion object {
        private const val TAG = "ClaudeCodeStart"
    }
}

/** What a chat's branch changed. */
@Immutable
sealed interface ChangesState {
    data object Loading : ChangesState

    data class Loaded(
        val info: GitWorkInfo?,
        val patch: ParsedPatch,
        val untracked: List<String>,
    ) : ChangesState
}

class ClaudeCodeChangesViewModel(
    service: ClaudeCodeService,
    key: String,
) : ViewModel() {
    val chat: ClaudeChat? = service.chat(key)

    private val _state = MutableStateFlow<ChangesState>(ChangesState.Loading)
    val state: StateFlow<ChangesState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val dir =
            chat
                ?.state
                ?.value
                ?.cwd
                ?.let(::File) ?: return
        viewModelScope.launch {
            val info = GitWorkspace.inspect(dir)
            val diff = GitWorkspace.diff(dir)
            val patch = withContext(Dispatchers.Default) { UnifiedDiffParser.parse(diff) }
            _state.value = ChangesState.Loaded(info, patch, GitWorkspace.untracked(dir))
        }
    }
}

/** The build gate: the repository's own pre-push checks, run in the worktree before publishing. */
@Immutable
sealed interface GateState {
    /** The checkout has no gate script. */
    data object None : GateState

    data object NotRun : GateState

    data class Running(
        val tail: String,
    ) : GateState

    data class Passed(
        val tail: String,
    ) : GateState

    data class Failed(
        val tail: String,
    ) : GateState
}

/** A title and description drafted for the proposal. */
@Immutable
data class ProposalDraft(
    val title: String,
    val description: String,
)

/** What publishing needs from the checkout: the branch tip and its merge base with the target. */
@Immutable
data class LocalTip(
    val tip: String,
    val mergeBase: String?,
)

/**
 * The local half of proposing a chat's branch: the commits, a drafted description, and the build
 * gate. Signing and pushing go through the app's GitProposals, as any proposal made in the app.
 */
class ClaudeCodeProposeViewModel(
    service: ClaudeCodeService,
    key: String,
) : ViewModel() {
    val chat: ClaudeChat? = service.chat(key)
    private val dir: File? =
        chat
            ?.state
            ?.value
            ?.cwd
            ?.let(::File)

    private val _info = MutableStateFlow<GitWorkInfo?>(null)
    val info: StateFlow<GitWorkInfo?> = _info.asStateFlow()

    private val _draft = MutableStateFlow<ProposalDraft?>(null)
    val draft: StateFlow<ProposalDraft?> = _draft.asStateFlow()

    private val _drafting = MutableStateFlow(false)
    val drafting: StateFlow<Boolean> = _drafting.asStateFlow()

    private val _gate = MutableStateFlow<GateState>(GateState.NotRun)
    val gate: StateFlow<GateState> = _gate.asStateFlow()

    private var gateJob: Job? = null

    init {
        viewModelScope.launch {
            val dir = dir ?: return@launch
            val info = GitWorkspace.inspect(dir)
            _info.value = info
            if (!gateScript(dir).isFile) _gate.value = GateState.None
            // A first draft from the commits themselves; Claude can write a better one.
            if (_draft.value == null && info != null) _draft.value = draftFromCommits(info)
        }
    }

    private fun gateScript(dir: File) = File(GitWorkspace.git(dir, "rev-parse", "--show-toplevel")?.trim() ?: dir.path, ".git-hooks/pre-push")

    fun runGate() {
        val dir = dir ?: return
        if (gateJob?.isActive == true) return
        _gate.value = GateState.Running("")
        gateJob =
            viewModelScope.launch(Dispatchers.IO) {
                val script = gateScript(dir)
                val output = StringBuilder()
                val code =
                    try {
                        val process =
                            ProcessBuilder("bash", script.absolutePath)
                                .directory(script.parentFile.parentFile)
                                .redirectErrorStream(true)
                                .apply { environment()["PREPUSH_KMP_METADATA"] = "1" }
                                .start()
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                output.appendLine(line)
                                _gate.value = GateState.Running(output.takeLast(GATE_TAIL).toString())
                            }
                        }
                        runInterruptible { process.waitFor() }
                    } catch (e: IOException) {
                        output.appendLine(e.message)
                        -1
                    }
                val tail = output.takeLast(GATE_TAIL).toString()
                _gate.value = if (code == 0) GateState.Passed(tail) else GateState.Failed(tail)
            }
    }

    fun setDraft(draft: ProposalDraft) {
        _draft.value = draft
    }

    /**
     * Asks Claude, in a one-off run without tools, for a title and description from the commits
     * and the diff, in the repository's PR style.
     */
    fun draftWithClaude() {
        val dir = dir ?: return
        val info = _info.value ?: return
        if (_drafting.value) return
        _drafting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exe = ClaudeExecutable.find() ?: return@launch
                val diff = GitWorkspace.diff(dir).take(DRAFT_DIFF_CHARS)
                val log = info.commits.joinToString("\n\n") { "${it.subject}\n${it.body}".trim() }
                val prompt =
                    """
                    Write the title and description of a pull request for these changes.
                    The title is one line under 72 characters, in the conventional-commit style of the commits.
                    The description says what changed and why, then a short test plan, in plain markdown.
                    Answer with only a JSON object: {"title": "...", "description": "..."}

                    Commits:
                    $log

                    Diff (may be cut short):
                    $diff
                    """.trimIndent()
                val process =
                    ProcessBuilder(exe.absolutePath, "-p", prompt, "--output-format", "json", "--tools", "", "--no-session-persistence")
                        .directory(dir)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val out = process.inputStream.bufferedReader().readText()
                runInterruptible { process.waitFor() }
                parseDraft(out)?.let { _draft.value = it }
            } catch (e: IOException) {
                Log.w(TAG, "Could not draft the description", e)
            } finally {
                _drafting.value = false
            }
        }
    }

    /** The branch tip and its merge base with [head], the target branch's tip in the maintainers' signed state. */
    suspend fun localTip(
        head: String?,
        cloneUrls: List<String>,
        branch: String?,
    ): LocalTip? {
        val dir = dir ?: return null
        val tip = LocalGitObjects.resolve(dir, "HEAD") ?: return null
        val mergeBase = head?.let { LocalGitObjects.mergeBase(dir, it, tip, cloneUrls, branch) }
        return LocalTip(tip, mergeBase)
    }

    suspend fun objects(tip: LocalTip) = LocalGitObjects.between(dir ?: throw IOException("no folder"), tip.mergeBase, tip.tip)

    /** Remembers the published proposal on the branch, so the next version updates it. */
    fun published(proposalId: String) {
        val dir = dir ?: return
        val branch = _info.value?.branch ?: return
        viewModelScope.launch {
            try {
                if (_info.value?.proposalId == null) GitWorkspace.link(dir, branch, proposalId = proposalId)
            } catch (e: IOException) {
                Log.w(TAG, "Could not link the proposal to $branch", e)
            }
            chat?.refreshGit()
        }
    }

    companion object {
        private const val TAG = "ClaudeCodePropose"
        private const val GATE_TAIL = 6_000
        private const val DRAFT_DIFF_CHARS = 60_000

        fun draftFromCommits(info: GitWorkInfo): ProposalDraft {
            val first = info.commits.lastOrNull()
            val title =
                if (info.commits.size == 1) {
                    first?.subject.orEmpty()
                } else {
                    info.branch
                        ?.substringAfterLast('/')
                        ?.replace('-', ' ')
                        .orEmpty()
                }
            val description =
                if (info.commits.size == 1) {
                    first?.body.orEmpty()
                } else {
                    info.commits.reversed().joinToString("\n") { "- ${it.subject}" }
                }
            return ProposalDraft(title, description)
        }

        /** The JSON object Claude answered with, from `--output-format json`'s `result`. */
        fun parseDraft(output: String): ProposalDraft? {
            val json = Json { ignoreUnknownKeys = true }
            val result =
                try {
                    ((json.parseToJsonElement(output) as? JsonObject)?.get("result") as? JsonPrimitive)?.content
                } catch (e: IllegalArgumentException) {
                    null
                } ?: return null
            val start = result.indexOf('{')
            val end = result.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val obj = json.parseToJsonElement(result.substring(start, end + 1)) as? JsonObject ?: return null
                val title = (obj["title"] as? JsonPrimitive)?.content ?: return null
                ProposalDraft(title.trim(), (obj["description"] as? JsonPrimitive)?.content?.trim().orEmpty())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

internal fun expandHome(path: String): String = path.trim().replaceFirst(Regex("^~"), Regex.escapeReplacement(System.getProperty("user.home")))
