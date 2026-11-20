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
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    /** The agents installed on this computer, to start the session with. */
    val installed = service.installed

    private val _checkout = MutableStateFlow<CheckoutState>(CheckoutState.Looking)
    val checkout: StateFlow<CheckoutState> = _checkout.asStateFlow()

    private val _error = MutableStateFlow<StartError?>(null)
    val error: StateFlow<StartError?> = _error.asStateFlow()

    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    private var lookedFor: Pair<NostrRepoRef, List<String>>? = null

    /**
     * Looks for the user's checkout of [repo]. Called again when the announcement loads with
     * its clone URLs, which can match a checkout the first look (without them) missed.
     */
    fun find(
        repo: NostrRepoRef,
        cloneUrls: List<String>,
    ) {
        val key = repo to cloneUrls
        if (lookedFor == key || _checkout.value is CheckoutState.Found) return
        lookedFor = key
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
        title: String?,
        agentId: String? = null,
        onReady: (String) -> Unit,
    ) {
        val dir = (_checkout.value as? CheckoutState.Found)?.dir ?: return
        if (_starting.value) return
        _starting.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                onReady(service.startFromNostr(dir, target, branch.trim(), permissionMode, prompt, title, agentId?.let { service.backend(it) } ?: service.backend).key)
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

/** Which changes the Changes screen shows. */
@Immutable
sealed interface ChangesScope {
    /** What isn't committed yet, new files included: what Claude just did. */
    data object Uncommitted : ChangesScope

    /** Everything the branch changed since its base, uncommitted work included. */
    data object Branch : ChangesScope

    data class Commit(
        val sha: String,
    ) : ChangesScope
}

/** What a chat's branch changed. */
@Immutable
sealed interface ChangesState {
    data object Loading : ChangesState

    data class Loaded(
        val info: GitWorkInfo?,
        val scope: ChangesScope,
        val patch: ParsedPatch,
        val untracked: List<String>,
    ) : ChangesState
}

class ClaudeCodeChangesViewModel(
    service: ClaudeCodeService,
    key: String,
    /** A repository's folder inside the chat's folder (a workspace's), or null for the chat's folder itself. */
    val repo: String? = null,
) : ViewModel() {
    val chat: ClaudeChat? = service.chat(key)

    private val _state = MutableStateFlow<ChangesState>(ChangesState.Loading)
    val state: StateFlow<ChangesState> = _state.asStateFlow()

    private var scope: ChangesScope? = null

    /** The folder whose changes show: the chat's, or [repo] inside it. */
    private val dir: File? =
        chat
            ?.state
            ?.value
            ?.cwd
            ?.let(::File)
            ?.let { cwd -> repo?.let { File(cwd, it) } ?: cwd }

    /** What the branch is, read once per [reload]: switching scopes doesn't change it. */
    private class Snapshot(
        val info: GitWorkInfo?,
        val untracked: List<String>,
    )

    private var snapshot: Snapshot? = null
    private var reloading: Job? = null
    private var showing: Job? = null

    /** Each scope's parsed diff since the last [reload], so switching back and forth is instant. */
    private val patches = HashMap<ChangesScope, Deferred<ParsedPatch>>()

    init {
        reload()
        // A turn that ends may have changed files: what was read before is stale.
        chat?.let { chat ->
            viewModelScope.launch {
                chat.state
                    .map { it.status == ChatStatus.WORKING }
                    .distinctUntilChanged()
                    .drop(1)
                    .filter { working -> !working }
                    .collect { reload() }
            }
        }
    }

    /** Shows [next]'s changes. */
    fun show(next: ChangesScope) {
        scope = next
        // A reload in flight shows the scope picked meanwhile.
        val snapshot = snapshot ?: return
        val dir = dir ?: return
        showing?.cancel()
        showing = viewModelScope.launch { publish(dir, snapshot, next) }
    }

    /** Reads the branch and its changes again. */
    fun reload() {
        val dir = dir ?: return
        reloading?.cancel()
        showing?.cancel()
        patches.values.forEach { it.cancel() }
        patches.clear()
        snapshot = null
        reloading =
            viewModelScope.launch {
                val info = GitWorkspace.inspect(dir)
                val read = Snapshot(info, GitWorkspace.untracked(dir))
                snapshot = read
                // First open: what isn't committed when there is some (what Claude just did), else the branch.
                val shown = scope ?: (if ((info?.uncommitted ?: 0) > 0) ChangesScope.Uncommitted else ChangesScope.Branch).also { scope = it }
                // The picker's other choice reads meanwhile, so it is ready when picked.
                listOf(ChangesScope.Uncommitted, ChangesScope.Branch).forEach { patchOf(dir, read, it) }
                publish(dir, read, shown)
            }
    }

    private suspend fun publish(
        dir: File,
        snapshot: Snapshot,
        shown: ChangesScope,
    ) {
        val patch = patchOf(dir, snapshot, shown).await()
        // The user may have picked another scope while this one read.
        if (scope == shown && this.snapshot === snapshot) {
            _state.value = ChangesState.Loaded(snapshot.info, shown, patch, if (shown is ChangesScope.Commit) emptyList() else snapshot.untracked)
        }
    }

    private fun patchOf(
        dir: File,
        snapshot: Snapshot,
        scope: ChangesScope,
    ): Deferred<ParsedPatch> =
        patches.getOrPut(scope) {
            viewModelScope.async {
                val diff =
                    when (scope) {
                        ChangesScope.Uncommitted -> GitWorkspace.uncommittedDiff(dir) + GitWorkspace.untrackedDiff(dir, snapshot.untracked)
                        ChangesScope.Branch -> GitWorkspace.diff(dir) + GitWorkspace.untrackedDiff(dir, snapshot.untracked)
                        is ChangesScope.Commit -> GitWorkspace.commitDiff(dir, scope.sha)
                    }
                withContext(Dispatchers.Default) { UnifiedDiffParser.parse(diff) }
            }
        }

    /** Puts a question about [line] of [path] to the chat, quoting the line, and sends it. */
    fun askAbout(
        path: String,
        line: Int?,
        code: String,
        question: String,
    ) {
        // The agent works in the workspace's folder: a repository's paths start with its folder.
        val relative = repo?.let { "$it/$path" } ?: path
        val where = if (line != null) "$relative:$line" else relative
        chat?.send("$where\n```\n$code\n```\n$question")
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

    /** The user stopped it; publishing waits for a run that passes. */
    data class Cancelled(
        val tail: String,
    ) : GateState
}

/** A title and description drafted for the proposal. */
@Immutable
data class ProposalDraft(
    val title: String,
    val description: String,
)

/** A git remote of the checkout, and the URL others would fetch it from. */
@Immutable
data class PushRemote(
    val name: String,
    val url: String,
    /** [url] as a public https clone URL, when it is an ssh one of a known shape. */
    val cloneUrl: String,
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
    private val service: ClaudeCodeService,
    private val key: String,
    /** A repository's folder in the chat's workspace, or null for the chat's folder itself. */
    repo: String? = null,
) : ViewModel() {
    val chat: ClaudeChat? = service.chat(key)
    private val dir: File? =
        chat
            ?.state
            ?.value
            ?.cwd
            ?.let(::File)
            ?.let { cwd -> repo?.let { File(cwd, it) } ?: cwd }

    private val _info = MutableStateFlow<GitWorkInfo?>(null)
    val info: StateFlow<GitWorkInfo?> = _info.asStateFlow()

    private val _draft = MutableStateFlow<ProposalDraft?>(null)
    val draft: StateFlow<ProposalDraft?> = _draft.asStateFlow()

    private val _drafting = MutableStateFlow(false)

    /** The chat's agent is writing the title and description ([draftWithAgent]). */
    val drafting: StateFlow<Boolean> = _drafting.asStateFlow()

    private val _draftFailed = MutableStateFlow(false)

    /** The last draft the agent was asked for never came back. */
    val draftFailed: StateFlow<Boolean> = _draftFailed.asStateFlow()

    /** How the screen names the agent that drafts: "Claude", "Codex". */
    val agentName: String = chat?.backend?.shortName ?: "Claude"

    private var draftJob: Job? = null

    private val _gate = MutableStateFlow<GateState>(GateState.NotRun)
    val gate: StateFlow<GateState> = _gate.asStateFlow()

    private var gateJob: Job? = null

    private val _ngitAccount = MutableStateFlow<Ngit.Account?>(null)

    /** The account the user's own ngit signs with, when ngit is installed and logged in. */
    val ngitAccount: StateFlow<Ngit.Account?> = _ngitAccount.asStateFlow()

    init {
        viewModelScope.launch {
            val dir = dir ?: return@launch
            val accounts = Ngit.accounts(dir)
            _ngitAccount.value = accounts.firstOrNull { it.active } ?: accounts.firstOrNull()
        }
        viewModelScope.launch {
            val dir = dir ?: return@launch
            val info = GitWorkspace.inspect(dir)
            _info.value = info
            if (!gateScript(dir).isFile) _gate.value = GateState.None
            // A first draft from the commits themselves, shown while the agent writes a better one.
            if (_draft.value == null && info != null) _draft.value = draftFromCommits(info)
            // A new proposal is described as the screen opens; an update keeps its description.
            if (info != null && info.proposalId == null && info.commits.isNotEmpty()) draftWithAgent()
        }
    }

    private fun gateScript(dir: File) = File(GitWorkspace.git(dir, "rev-parse", "--show-toplevel")?.trim() ?: dir.path, ".git-hooks/pre-push")

    private var gateProcess: Process? = null

    /**
     * The last [GATE_LOG_MAX] characters of the run, as lines. Old lines drop off the front one
     * at a time, where trimming one big string moved all of it on every line.
     */
    private val gateLog = ArrayDeque<String>()
    private var gateLogChars = 0

    private fun appendGateLine(line: String) =
        synchronized(gateLog) {
            gateLog.addLast(line)
            gateLogChars += line.length + 1
            while (gateLogChars > GATE_LOG_MAX && gateLog.size > 1) gateLogChars -= gateLog.removeFirst().length + 1
        }

    /** The last [GATE_TAIL] characters, for the screen. */
    private fun gateTail(): String =
        synchronized(gateLog) {
            val lines = ArrayList<String>()
            var chars = 0
            for (i in gateLog.indices.reversed()) {
                val line = gateLog[i]
                if (chars + line.length + 1 > GATE_TAIL && lines.isNotEmpty()) break
                lines.add(line)
                chars += line.length + 1
            }
            lines.asReversed().joinToString("\n", postfix = "\n")
        }

    private val _gateLogFile = MutableStateFlow<File?>(null)

    /** Where the last run's whole output is, for an editor or a terminal. */
    val gateLogFile: StateFlow<File?> = _gateLogFile.asStateFlow()

    /** The last run's output, up to the last [GATE_LOG_MAX] characters. */
    fun fullGateLog(): String = synchronized(gateLog) { gateLog.joinToString("\n", postfix = "\n") }

    fun runGate() {
        val dir = dir ?: return
        if (gateJob?.isActive == true) return
        _gate.value = GateState.Running("")
        synchronized(gateLog) {
            gateLog.clear()
            gateLogChars = 0
        }
        gateJob =
            viewModelScope.launch(Dispatchers.IO) {
                val script = gateScript(dir)
                val root = script.parentFile.parentFile
                // In the git dir: a build/ folder the repository does not ignore would show as a change.
                val logFile = File(GitWorkspace.privateDir(root) ?: File(root, "build"), "claude-code-gate.log")
                if (!logFile.parentFile.isDirectory && !logFile.parentFile.mkdirs()) Log.w(TAG) { "could not create ${logFile.parentFile}" }
                _gateLogFile.value = logFile
                val code =
                    try {
                        val process =
                            ProcessBuilder("bash", script.absolutePath)
                                .directory(root)
                                .redirectErrorStream(true)
                                .apply { environment()["PREPUSH_KMP_METADATA"] = "1" }
                                .start()
                        gateProcess = process
                        // A pre-push hook reads the pushed refs on stdin; there are none.
                        process.outputStream.close()
                        logFile.bufferedWriter().use { log ->
                            process.inputStream.bufferedReader().useLines { lines ->
                                // A build prints thousands of lines a second: the screen gets the tail every so often.
                                var shownAt = 0L
                                lines.forEach { line ->
                                    log.write(line)
                                    log.newLine()
                                    appendGateLine(line)
                                    val now = System.currentTimeMillis()
                                    if (now - shownAt >= GATE_SHOW_MS && _gate.value is GateState.Running) {
                                        shownAt = now
                                        _gate.value = GateState.Running(gateTail())
                                    }
                                }
                            }
                        }
                        runInterruptible { process.waitFor() }
                    } catch (e: IOException) {
                        appendGateLine(e.message ?: "the checks could not run")
                        -1
                    } finally {
                        gateProcess = null
                    }
                val tail = gateTail()
                _gate.value =
                    when {
                        _gate.value is GateState.Cancelled -> GateState.Cancelled(tail)
                        code == 0 -> GateState.Passed(tail)
                        else -> GateState.Failed(tail)
                    }
            }
    }

    /** Stops the running gate and everything it started (Gradle and its workers included). */
    fun cancelGate() {
        val process = gateProcess ?: return
        _gate.value = GateState.Cancelled((gate.value as? GateState.Running)?.tail.orEmpty())
        // The script first, listing its children while they are still its own: stopped after them,
        // it would see its Gradle end and go on to its next step.
        val children = process.descendants().toList()
        process.destroy()
        children.forEach { it.destroy() }
    }

    override fun onCleared() {
        cancelGate()
        super.onCleared()
    }

    /** The user's own text. An agent draft still being written stops: what was typed stays. */
    fun setDraft(draft: ProposalDraft) {
        _draft.value = draft
        stopDrafting()
    }

    /** Stops the draft being written; the text so far stays. */
    fun stopDrafting() {
        draftJob?.cancel()
        draftJob = null
        _drafting.value = false
    }

    /**
     * Asks the chat's own agent for the title and description, on a copy of its session
     * ([ClaudeCodeService.askAside]): it knows why the work was done, not only the diff. It
     * follows the repository's pull-request template when there is one. Leaving the screen
     * clears this ViewModel, which stops the agent and throws the copy away.
     */
    fun draftWithAgent() {
        val dir = dir ?: return
        val info = _info.value ?: return
        draftJob?.cancel()
        _drafting.value = true
        _draftFailed.value = false
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val prompt = draftPrompt(dir, info)
                    val draft = service.askAside(key, prompt)?.let(::parseDraftAnswer)
                    if (draft != null) _draft.value = draft else _draftFailed.value = true
                } finally {
                    // A newer draft took over: it says when it is done.
                    if (draftJob === coroutineContext[Job]) {
                        draftJob = null
                        _drafting.value = false
                    }
                }
            }
        draftJob = job
        job.start()
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

    /**
     * Sends the branch through the user's ngit, signed by their ngit account: the commits after
     * [mergeBase] (null: what ngit works out itself) as a proposal to [repo], or a new version of
     * the branch's own proposal.
     */
    suspend fun sendWithNgit(
        repo: String,
        mergeBase: String?,
        title: String,
        description: String,
    ): Ngit.SendResult {
        val dir = dir ?: return Ngit.SendResult.Failed("no folder")
        val base = mergeBase ?: GitWorkspace.git(dir, "merge-base", "HEAD", "@{upstream}")?.trim() ?: return Ngit.SendResult.Failed("Can't tell where the branch starts")
        return Ngit.send(dir, repo, base, title, description, _info.value?.proposalId)
    }

    suspend fun objects(tip: LocalTip) = LocalGitObjects.between(dir ?: throw IOException("no folder"), tip.mergeBase, tip.tip)

    private val _remotes = MutableStateFlow<List<PushRemote>>(emptyList())

    /** The checkout's remotes, for a repository with no GRASP server to push the branch to. */
    val remotes: StateFlow<List<PushRemote>> = _remotes.asStateFlow()

    fun loadRemotes() {
        val dir = dir ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _remotes.value =
                GitWorkspace
                    .git(dir, "remote", "-v")
                    .orEmpty()
                    .lines()
                    .mapNotNull { line ->
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 3 || parts[2] != "(push)") null else PushRemote(parts[0], parts[1], httpsCloneUrl(parts[1]))
                    }.filter { !it.url.startsWith("nostr://") }
        }
    }

    /**
     * Pushes the branch to [remote] as [branch]: the user's own step, with their git credentials,
     * for a repository whose proposal must point at a host that serves the commits. Null when it
     * worked, else git's message.
     */
    suspend fun pushBranch(
        remote: String,
        branch: String,
    ): String? {
        val dir = dir ?: return "no folder"
        val result = withContext(Dispatchers.IO) { GitWorkspace.run(dir, listOf("git", "push", remote, "HEAD:refs/heads/$branch"), timeoutSeconds = 600) }
        return if (result.code == 0) {
            null
        } else {
            result.output
                .trim()
                .lines()
                .takeLast(4)
                .joinToString("\n")
                .ifBlank { "git push failed" }
        }
    }

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
        private const val GATE_LOG_MAX = 2_000_000
        private const val GATE_SHOW_MS = 100L
        private const val DRAFT_DIFF_CHARS = 60_000
        private const val TEMPLATE_CHARS = 20_000

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

        /** The `{"title", "description"}` object in the agent's answer, which may have words around it. */
        fun parseDraftAnswer(answer: String): ProposalDraft? {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val obj = Json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject ?: return null
                val title = (obj["title"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                ProposalDraft(title, (obj["description"] as? JsonPrimitive)?.content?.trim().orEmpty())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /** Where forges look for a repository's pull-request template, in their order. */
        private val TEMPLATE_PATHS =
            listOf(
                ".github/pull_request_template.md",
                ".github/PULL_REQUEST_TEMPLATE.md",
                "pull_request_template.md",
                "PULL_REQUEST_TEMPLATE.md",
                "docs/pull_request_template.md",
                "docs/PULL_REQUEST_TEMPLATE.md",
            )

        /** The repository's pull-request template, if it has one. */
        fun pullRequestTemplate(root: File): String? =
            TEMPLATE_PATHS
                .map { File(root, it) }
                .firstOrNull { it.isFile }
                ?.readText()
                ?.take(TEMPLATE_CHARS)
                ?.takeIf { it.isNotBlank() }

        /** What the agent is asked: the commits and diff, and the repository's template if any. */
        suspend fun draftPrompt(
            dir: File,
            info: GitWorkInfo,
        ): String =
            withContext(Dispatchers.IO) {
                val diff = GitWorkspace.diff(dir).take(DRAFT_DIFF_CHARS)
                val log = info.commits.joinToString("\n\n") { "${it.subject}\n${it.body}".trim() }
                val root = GitWorkspace.git(dir, "rev-parse", "--show-toplevel")?.trim()?.let(::File) ?: dir
                val template = pullRequestTemplate(root)
                val shape =
                    if (template != null) {
                        "Fill in the repository's pull-request template below: keep its headings, follow its comments (<!-- -->) and then leave them out, drop the sections that don't apply, and leave unticked any box only a person can vouch for."
                    } else {
                        "The description says what changed and why, then how it was tested, in plain markdown."
                    }
                buildString {
                    appendLine("Write the title and description of a pull request for the work on this branch, for a reviewer who hasn't seen this conversation.")
                    appendLine("Take the why from this conversation: what was asked, what was decided and what was ruled out. Take what changed from the commits and the diff below, and the testing from what was actually run.")
                    appendLine("The title is one line under 72 characters, in the style of the commits.")
                    appendLine(shape)
                    appendLine("Don't use tools and don't change anything. Answer with only a JSON object: {\"title\": \"...\", \"description\": \"...\"}")
                    appendLine()
                    appendLine("Commits:")
                    appendLine(log)
                    appendLine()
                    appendLine("Diff (may be cut short):")
                    appendLine(diff)
                    if (template != null) {
                        appendLine()
                        appendLine("Template:")
                        appendLine(template)
                    }
                }
            }
    }
}

/**
 * A clone URL others can fetch: an https URL as it is, and the usual ssh forms of a forge
 * (`git@host:owner/repo.git`, `ssh://git@host/owner/repo.git`) as their https twin.
 */
fun httpsCloneUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed
    Regex("^(?:ssh://)?[^@/]+@([^:/]+)[:/](.+)$").find(trimmed)?.let { m ->
        return "https://${m.groupValues[1]}/${m.groupValues[2].removePrefix("/")}"
    }
    return trimmed
}

/** [path] with a leading `~` (only a leading one) as the home folder. */
fun expandHome(path: String): String = path.trim().replaceFirst(Regex("^~(?=/|$)"), Regex.escapeReplacement(System.getProperty("user.home")))
