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
import com.vitorpamplona.claudecode.protocol.ImageInput
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.protocol.PermissionRequest
import com.vitorpamplona.quartz.utils.Log
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

    /**
     * Starts a chat in [folder], or in a new worktree of the repository it is in when
     * [inWorktree], and hands its key to [onReady].
     */
    fun newChat(
        folder: String,
        inWorktree: Boolean,
        permissionMode: PermissionMode,
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
                onReady(service.newChat(cwd, permissionMode).key)
            } finally {
                _creating.value = false
            }
        }
    }

    companion object {
        private const val TAG = "ClaudeCodeSessions"
        private const val REFRESH_MS = 5_000L
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
