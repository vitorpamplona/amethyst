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
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A folder sessions ran in, with how many. */
@Immutable
data class ClaudeProject(
    val cwd: String,
    val sessions: Int,
)

/** Why starting a new chat failed, worded by the UI. */
@Immutable
sealed interface NewChatError {
    data object NotAFolder : NewChatError

    data object NotARepository : NewChatError

    data class WorktreeFailed(
        val message: String?,
    ) : NewChatError

    /** Cloning a repository for a workspace failed: git's words. */
    data class CloneFailed(
        val message: String?,
    ) : NewChatError
}

/** The session list: every saved session, the chats open here, and the folders they ran in. */
class ClaudeCodeSessionsViewModel(
    private val service: ClaudeCodeService,
) : ViewModel() {
    private val _projectFilter = MutableStateFlow<String?>(null)
    val projectFilter: StateFlow<String?> = _projectFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val loaded = service.loaded
    val executable = service.executable

    val projects: StateFlow<List<ClaudeProject>> =
        service.rows
            .map { rows ->
                rows
                    .mapNotNull { it.cwd }
                    .groupingBy { it }
                    .eachCount()
                    .map { (cwd, count) -> ClaudeProject(cwd, count) }
                    .sortedByDescending { it.sessions }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rows: StateFlow<List<ClaudeSessionRow>> =
        combine(service.rows, _projectFilter, _query) { rows, project, query ->
            rows.filter { row ->
                (project == null || row.cwd == project) &&
                    (query.isBlank() || row.title.contains(query, ignoreCase = true) || row.lastPrompt?.contains(query, ignoreCase = true) == true)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _newChatError = MutableStateFlow<NewChatError?>(null)
    val newChatError: StateFlow<NewChatError?> = _newChatError.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    /**
     * Re-reads the sessions every few seconds, so ones started in a terminal show up, for as
     * long as the caller runs it: the screen, while it is shown, not while a chat covers it.
     */
    suspend fun poll() {
        while (currentCoroutineContext().isActive) {
            service.refresh()
            delay(REFRESH_MS)
        }
    }

    fun refresh() {
        viewModelScope.launch { service.refresh() }
    }

    fun filterProject(cwd: String?) {
        _projectFilter.value = cwd
    }

    fun search(text: String) {
        _query.value = text
    }

    fun rename(
        row: ClaudeSessionRow,
        title: String,
    ) {
        viewModelScope.launch {
            try {
                service.rename(row, title)
            } catch (e: IOException) {
                Log.w(TAG, "Could not rename the session", e)
            }
        }
    }

    /** Archives the row's session, or brings it back. */
    fun archive(
        row: ClaudeSessionRow,
        archived: Boolean,
    ) {
        viewModelScope.launch {
            try {
                service.archive(row, archived)
            } catch (e: IOException) {
                Log.w(TAG, "Could not archive the session", e)
            }
        }
    }

    fun delete(row: ClaudeSessionRow) {
        viewModelScope.launch {
            try {
                service.delete(row)
            } catch (e: IOException) {
                Log.w(TAG, "Could not delete the session", e)
            }
        }
    }

    fun clearNewChatError() {
        _newChatError.value = null
    }

    /** The agents installed on this computer, to start a session with. */
    val installed = service.installed

    private val savedGroups = MutableStateFlow(service.agentWorkspaces.groups())

    /** The repository groups saved before, the last used first. */
    val repoGroups: StateFlow<List<RepoGroup>> = savedGroups.asStateFlow()

    /** Saves [urls] under [name], to pick together next time. */
    fun saveGroup(
        name: String,
        urls: List<String>,
    ) {
        val clean = name.trim().ifEmpty { return }
        try {
            service.agentWorkspaces.saveGroup(RepoGroup(clean, urls))
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the group", e)
        }
        savedGroups.value = service.agentWorkspaces.groups()
    }

    fun deleteGroup(name: String) {
        try {
            service.agentWorkspaces.deleteGroup(name)
        } catch (e: IOException) {
            Log.w(TAG, "Could not remove the group", e)
        }
        savedGroups.value = service.agentWorkspaces.groups()
    }

    private val repoQuery = MutableStateFlow("")
    private val localRepos = MutableStateFlow<List<RepoCandidate>>(emptyList())
    private val myGitHubRepos = MutableStateFlow<List<RepoCandidate>>(emptyList())
    private val foundGitHubRepos = MutableStateFlow<List<RepoCandidate>>(emptyList())
    private val nostrRepos = MutableStateFlow<List<RepoCandidate>>(emptyList())
    private var searchJob: Job? = null

    /** When [loadRepoCatalog] last read the sources, to read them again only after [CATALOG_TTL_MS]. */
    private var catalogLoadedAt = 0L

    private val _searchingGitHub = MutableStateFlow(false)

    /** Whether GitHub is being searched for what was typed. */
    val searchingGitHub: StateFlow<Boolean> = _searchingGitHub.asStateFlow()

    /**
     * The repositories to pick from for what is typed: the ones cloned here first, most used
     * first; then the user's GitHub repositories and GitHub's search; then NIP-34 ones.
     */
    val repoCandidates: StateFlow<List<RepoCandidate>> =
        combine(repoQuery, localRepos, myGitHubRepos, foundGitHubRepos, nostrRepos) { query, local, mine, found, nostr ->
            RepoCatalog.merge(query, local, mine, nostr, found)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Reads what the picker offers: the mirrors and their use, the NIP-34 repositories seen, the user's GitHub ones. */
    fun loadRepoCatalog() {
        // `gh repo list` goes to the network: once in a while, not on every New workstream.
        val now = System.currentTimeMillis()
        if (now - catalogLoadedAt < CATALOG_TTL_MS) return
        catalogLoadedAt = now
        viewModelScope.launch {
            localRepos.value = withContext(Dispatchers.IO) { RepoCatalog.local(service.agentWorkspaces) }
            nostrRepos.value = withContext(Dispatchers.Default) { RepoCatalog.nostr() }
            myGitHubRepos.value = RepoCatalog.github()
        }
    }

    /** Filters the picker by [text], and searches GitHub for it once the typing pauses. */
    fun searchRepos(text: String) {
        repoQuery.value = text
        searchJob?.cancel()
        foundGitHubRepos.value = emptyList()
        val query = text.trim()
        // A URL is added as it is; a short word would match half of GitHub.
        if (query.length < 3 || query.contains("://") || query.contains('@')) {
            _searchingGitHub.value = false
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DELAY_MS)
                _searchingGitHub.value = true
                try {
                    foundGitHubRepos.value = RepoCatalog.searchGitHub(query)
                } finally {
                    // A search the typing replaced must not hide the spinner of the one now running.
                    if (searchJob === currentCoroutineContext()[Job]) _searchingGitHub.value = false
                }
            }
    }

    /** The agent a new session starts with unless the user picks another: the last one used. */
    fun preferredAgentId(): String? = service.preferredAgent()?.id

    /** Ends the row's session and removes the workspace of clones it works in. */
    fun deleteWorkspace(row: ClaudeSessionRow) {
        val workspace = row.workspace ?: return
        viewModelScope.launch {
            try {
                service.deleteWorkspace(workspace)
            } catch (e: IOException) {
                Log.w(TAG, "Could not remove the workspace", e)
            }
        }
    }

    /**
     * A new workstream called [name] on fresh clones of [urls]: its chat opens at once ([onReady]) while
     * they clone, which can take minutes the first time a repository is used.
     */
    fun newWorkstream(
        urls: List<String>,
        name: String,
        agentId: String? = null,
        /** Null for the workstream's own hands-off mode ([workstreamModeOf]). */
        permissionMode: PermissionMode? = null,
        onReady: (String) -> Unit,
    ) {
        if (_creating.value) return
        _creating.value = true
        _newChatError.value = null
        viewModelScope.launch {
            try {
                val agent = agentId?.let { service.backend(it) } ?: service.backend
                onReady(service.newWorkstream(urls, name.trim(), agent, permissionMode ?: workstreamModeOf(agent.capabilities.modes)).key)
            } catch (e: IOException) {
                Log.w(TAG, "Could not create the workstream", e)
                _newChatError.value = NewChatError.CloneFailed(e.message)
            } finally {
                _creating.value = false
            }
        }
    }

    /**
     * Starts a chat in [folder], or in a new worktree of the repository it is in when
     * [inWorktree], and hands its key to [onReady].
     */
    fun newChat(
        folder: String,
        inWorktree: Boolean,
        permissionMode: PermissionMode,
        agentId: String? = null,
        onReady: (String) -> Unit,
    ) {
        val dir = File(expandHome(folder))
        if (!dir.isDirectory) {
            _newChatError.value = NewChatError.NotAFolder
            return
        }
        viewModelScope.launch {
            _creating.value = true
            try {
                val cwd =
                    if (inWorktree) {
                        val repo = GitWorktrees.repositoryRoot(dir)
                        if (repo == null) {
                            _newChatError.value = NewChatError.NotARepository
                            return@launch
                        }
                        try {
                            GitWorktrees.create(repo)
                        } catch (e: IOException) {
                            _newChatError.value = NewChatError.WorktreeFailed(e.message)
                            return@launch
                        }
                    } else {
                        dir
                    }
                _newChatError.value = null
                onReady(service.newChat(cwd, permissionMode, agent = agentId?.let { service.backend(it) } ?: service.backend).key)
            } finally {
                _creating.value = false
            }
        }
    }

    companion object {
        private const val TAG = "ClaudeCodeSessions"
        private const val REFRESH_MS = 5_000L
        private const val SEARCH_DELAY_MS = 400L
        private const val CATALOG_TTL_MS = 5 * 60_000L
    }
}

/** One chat: its state, and what the user can do in it. */
class ClaudeCodeChatViewModel(
    private val service: ClaudeCodeService,
    key: String,
) : ViewModel() {
    /** Null when [key] names no session (deleted, or from another machine's settings). */
    val chat: ClaudeChat? = service.chat(key)

    private val _runningApp = MutableStateFlow(false)

    /** Whether a desktop app this chat started from its folder is still running. */
    val runningApp: StateFlow<Boolean> = _runningApp.asStateFlow()

    private val _runError = MutableStateFlow<String?>(null)
    val runError: StateFlow<String?> = _runError.asStateFlow()

    fun send(text: String) = chat?.send(text)

    /** Sends [text] with images already read for sending, and the files they came from. */
    fun send(
        text: String,
        images: List<ImageInput>,
        files: List<File?>,
    ) = chat?.send(text, images, files)

    fun approve(
        request: PermissionRequest,
        always: Boolean,
    ) = chat?.approve(request, always)

    fun deny(request: PermissionRequest) = chat?.deny(request)

    fun interrupt() = chat?.interrupt()

    fun setPermissionMode(mode: PermissionMode) = chat?.setPermissionMode(mode)

    fun endSession() = chat?.close()

    /** Opens a chat to ask [message] again differently, with the history before it. */
    fun editFrom(
        message: ChatItem.UserText,
        onReady: (String) -> Unit,
    ) {
        val key = chat?.key ?: return
        viewModelScope.launch {
            // Reads the session's file to find where to cut.
            val copy = withContext(Dispatchers.IO) { service.editFrom(key, message) }
            copy?.let { onReady(it.key) }
        }
    }

    fun setModel(value: String?) = chat?.setModel(value)

    /** Names the session, or, before it exists, the session it will become. */
    fun rename(title: String) {
        val key = chat?.key ?: return
        viewModelScope.launch {
            try {
                service.renameChat(key, title)
            } catch (e: IOException) {
                Log.w("ClaudeCodeChatViewModel", "Could not rename the session", e)
            }
        }
    }

    fun loadCliInfo() = chat?.loadCliInfo()

    /** The text a chat opened to ask a message again starts with, once. */
    fun takeDraft(): String? = chat?.draft?.also { chat.draft = null }

    /** Opens a copy of this session to go on with, while another Claude Code holds this one. */
    fun fork(onReady: (String) -> Unit) {
        val key = chat?.key ?: return
        service.fork(key)?.let { onReady(it.key) }
    }

    private val _cleanupError = MutableStateFlow<String?>(null)

    /** Why removing the worktree failed, as git said it. */
    val cleanupError: StateFlow<String?> = _cleanupError.asStateFlow()

    /**
     * Ends the session and removes its worktree: the proposal was merged. Without [force], a
     * worktree with uncommitted files stays and [cleanupError] says why.
     */
    fun removeWorktree(
        force: Boolean = false,
        onDone: () -> Unit,
    ) {
        val cwd = chat?.state?.value?.cwd ?: return
        viewModelScope.launch {
            try {
                _cleanupError.value = null
                chat.close()
                GitWorktrees.remove(File(cwd), force)
                onDone()
            } catch (e: IOException) {
                _cleanupError.value = e.message ?: cwd
            }
        }
    }

    /** Whether the chat's folder is an Amethyst checkout the app can be run from. */
    fun canRunApp(): Boolean =
        chat
            ?.state
            ?.value
            ?.cwd
            ?.let { AmethystCheckout.isCheckout(File(it)) } ?: false

    /** Starts the desktop app from the chat's folder, to try the session's changes. */
    fun runApp() {
        val cwd = chat?.state?.value?.cwd ?: return
        if (_runningApp.value) return
        viewModelScope.launch {
            try {
                _runError.value = null
                val process = AmethystCheckout.runDesktopApp(File(cwd))
                _runningApp.value = true
                process.onExit().thenRun { _runningApp.value = false }
            } catch (e: IOException) {
                Log.w(TAG, "Could not start the app from $cwd", e)
                _runError.value = e.message ?: cwd
            }
        }
    }

    companion object {
        private const val TAG = "ClaudeCodeChat"
    }
}
