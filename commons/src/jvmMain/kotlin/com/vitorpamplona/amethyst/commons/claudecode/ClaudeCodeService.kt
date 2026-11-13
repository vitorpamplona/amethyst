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
import com.vitorpamplona.claudecode.process.ClaudeCodeLauncher
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.process.ClaudeExecutable
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.session.ClaudeLiveSession
import com.vitorpamplona.claudecode.session.ClaudeSessionInfo
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A row of the session list: a saved session, a chat open in this app, or both. */
@Immutable
data class ClaudeSessionRow(
    /** What to open: the chat's key in [ClaudeCodeService]. */
    val key: String,
    val sessionId: String?,
    val title: String,
    val lastPrompt: String?,
    val cwd: String?,
    val gitBranch: String?,
    val lastModified: Long,
    /** Set while the chat is open in this app. */
    val appStatus: ChatStatus?,
    /** Set while another Claude Code process (a terminal) has the session open. */
    val openElsewhere: ClaudeLiveSession?,
    /** The repository (or folder) the session belongs to, worktrees included: the list's sections. */
    val project: String,
)

/** What a folder is part of: a NIP-34 repository, a git repository, or just itself. */
@Immutable
data class ProjectInfo(
    val name: String,
    val repo: NostrRepoRef?,
)

/**
 * Claude Code on this computer: the sessions it saved and the chats open in the app. One per
 * process ([shared]), so the chats outlive the screens that show them.
 */
class ClaudeCodeService(
    val store: ClaudeSessionStore = ClaudeSessionStore(),
    private val launcher: ClaudeCodeStarter = ClaudeCodeLauncher(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val findExecutable: () -> File? = { ClaudeExecutable.find() },
) {
    private val chats = MutableStateFlow<Map<String, ClaudeChat>>(emptyMap())
    private val watchers = ConcurrentHashMap<String, Job>()
    private val saved = MutableStateFlow<List<ClaudeSessionInfo>>(emptyList())
    private val elsewhere = MutableStateFlow<Map<String, ClaudeLiveSession>>(emptyMap())

    private val _rows = MutableStateFlow<List<ClaudeSessionRow>>(emptyList())

    /** Every session, newest first. Call [refresh] to re-read the disk. */
    val rows: StateFlow<List<ClaudeSessionRow>> = _rows.asStateFlow()

    private val projects = ConcurrentHashMap<String, ProjectInfo>()

    private fun projectOf(cwd: String?): String = cwd?.let { projects[it]?.name ?: folderProject(File(it)) } ?: ""

    /**
     * A folder's project when git can't say (it was removed): for the app's own worktrees,
     * `<repo>-worktrees/<branch>`, the repository; else the folder's name.
     */
    private fun folderProject(dir: File): String =
        dir.absoluteFile.parentFile
            ?.name
            ?.removeSuffix(WORKTREES_SUFFIX)
            ?.takeIf {
                dir.absoluteFile.parentFile.name
                    .endsWith(WORKTREES_SUFFIX) && it.isNotEmpty()
            }
            ?: dir.name.ifEmpty { dir.path }

    /**
     * The project of a folder: its nostr repository's name, or the name of the main checkout
     * its worktree belongs to, or the folder's own name. Null when the folder doesn't exist.
     */
    private fun computeProject(cwd: String): ProjectInfo? {
        val dir = File(cwd)
        // Not cached: a worktree removed now may be made again, in a repository.
        if (!dir.isDirectory) return null
        val common = GitWorkspace.git(dir, "rev-parse", "--path-format=absolute", "--git-common-dir")?.trim()
        val mainCheckout = common?.let { File(it).parentFile }
        val branch = GitWorkspace.git(dir, "branch", "--show-current")?.trim()?.takeIf { it.isNotEmpty() }
        val repo =
            branch?.let { NostrRepoRef.fromConfig(GitWorkspace.git(dir, "config", "--get", "branch.$it.${GitWorkspace.KEY_REPO}")?.trim()) }
                ?: GitWorkspace.nostrRemoteRepo(dir)
        return ProjectInfo(repo?.identifier ?: mainCheckout?.name ?: dir.name, repo)
    }

    private val _executable = MutableStateFlow<File?>(null)

    /** The `claude` the chats run, or null when it isn't installed (or not found yet). */
    val executable: StateFlow<File?> = _executable.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Re-reads the saved sessions and which ones are open elsewhere. */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            _executable.value = findExecutable()
            saved.value =
                try {
                    store.listSessions()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not list Claude Code sessions", e)
                    emptyList()
                }
            (saved.value.mapNotNull { it.cwd } + chats.value.values.map { it.state.value.cwd })
                .distinct()
                .filter { !projects.containsKey(it) }
                .forEach { cwd -> computeProject(cwd)?.let { projects[cwd] = it } }
            elsewhere.value =
                try {
                    store.liveSessions()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not read the running Claude Code sessions", e)
                    emptyMap()
                }
        }
        _loaded.value = true
        rebuildRows()
    }

    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    /** The chat for [key]: an open one, or a saved session opened from its file. */
    fun chat(key: String): ClaudeChat? {
        chats.value[key]?.let { return it }
        chats.value.values
            .firstOrNull { it.state.value.sessionId == key }
            ?.let { return it }
        if (!ClaudeSessionStore.isSafeId(key)) return null
        val info = saved.value.firstOrNull { it.sessionId == key } ?: store.session(key) ?: return null
        val cwd = info.cwd?.let { File(it) }?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        return open(key, info.sessionId, cwd, info.title, PermissionMode.DEFAULT, null)
    }

    /**
     * A new chat in [cwd]; its process starts with the first message. A [title] names the
     * session from the start, instead of its first message.
     */
    fun newChat(
        cwd: File,
        permissionMode: PermissionMode = PermissionMode.DEFAULT,
        model: String? = null,
        title: String? = null,
    ): ClaudeChat = open("new-${UUID.randomUUID()}", null, cwd, title?.trim()?.ifEmpty { null }, permissionMode, model)

    private fun open(
        key: String,
        sessionId: String?,
        cwd: File,
        title: String?,
        mode: PermissionMode,
        model: String?,
    ): ClaudeChat {
        val chat = ClaudeChat(key, sessionId, cwd, title, mode, model, store, launcher, scope, onSessionChanged = { refreshInBackground() })
        chats.update { it + (key to chat) }
        // The list only shows these; streamed text must not rebuild it.
        watchers[key] =
            scope.launch {
                chat.state
                    .map { Triple(it.sessionId, it.title, it.status) to (it.items.firstOrNull() as? ChatItem.UserText) }
                    .distinctUntilChanged()
                    .collect { if (chats.value[key] === chat) rebuildRows() }
            }
        return chat
    }

    suspend fun rename(
        row: ClaudeSessionRow,
        title: String,
    ) {
        val clean = title.trim().ifEmpty { return }
        val chat = chats.value[row.key]
        val renamedLive = chat?.renameLive(clean) ?: false
        val sessionId = row.sessionId
        if (!renamedLive && sessionId != null) {
            withContext(Dispatchers.IO) { store.rename(sessionId, clean) }
            chat?.titleChanged(clean)
        }
        refresh()
    }

    /** Ends the session's chat here and deletes its file. */
    suspend fun delete(row: ClaudeSessionRow) {
        chats.value[row.key]?.let { chat ->
            chat.kill()
            watchers.remove(row.key)?.cancel()
            chats.update { it - row.key }
            rebuildRows()
        }
        val sessionId = row.sessionId
        if (sessionId != null) withContext(Dispatchers.IO) { store.delete(sessionId) }
        refresh()
    }

    /** Where NIP-34 repositories are kept on this computer. */
    val workspaces = NostrWorkspaces()

    /** The folders sessions ran in, newest first: where a repository's checkout is looked for. */
    fun knownFolders(): List<File> =
        saved.value
            .mapNotNull { it.cwd }
            .distinct()
            .map(::File)

    /**
     * Starts a session on a NIP-34 repository: a worktree on [branch] made from [checkout], and,
     * when [prompt] isn't blank, its first message. The session is named [title], the issue's
     * or proposal's subject, rather than after the long first message.
     */
    suspend fun startFromNostr(
        checkout: File,
        target: NostrWorkTarget,
        branch: String,
        permissionMode: PermissionMode,
        prompt: String,
        title: String? = null,
    ): ClaudeChat {
        val workspace = workspaces.prepare(checkout, target, branch)
        val chat = newChat(workspace.dir, permissionMode, title = title)
        if (prompt.isNotBlank()) chat.send(prompt)
        return chat
    }

    /** Stops every process this app started. */
    fun shutdown() {
        chats.value.values.forEach { it.kill() }
    }

    @Synchronized
    private fun rebuildRows() {
        val open = chats.value
        val openBySession =
            open.values
                .mapNotNull { chat ->
                    chat.state.value.sessionId
                        ?.let { it to chat }
                }.toMap()
        val live = elsewhere.value
        val ownPids =
            ProcessHandle
                .current()
                .descendants()
                .map { it.pid() }
                .toList()
                .toSet()

        val fromDisk =
            saved.value.map { info ->
                val chat = openBySession[info.sessionId]
                ClaudeSessionRow(
                    key = chat?.key ?: info.sessionId,
                    sessionId = info.sessionId,
                    title = chat?.state?.value?.title ?: info.title,
                    lastPrompt = info.lastPrompt,
                    cwd = info.cwd,
                    gitBranch = info.gitBranch,
                    lastModified = info.lastModified,
                    appStatus = chat?.state?.value?.status,
                    openElsewhere = live[info.sessionId]?.takeIf { it.pid !in ownPids },
                    project = projectOf(info.cwd),
                )
            }
        val savedIds = saved.value.mapTo(HashSet()) { it.sessionId }
        // New chats before their file exists.
        val unsaved =
            open.values
                .filter { chat ->
                    chat.state.value.sessionId
                        .let { it == null || it !in savedIds }
                }.map { chat ->
                    val state = chat.state.value
                    ClaudeSessionRow(
                        key = chat.key,
                        sessionId = state.sessionId,
                        title = state.title ?: (state.items.firstOrNull() as? ChatItem.UserText)?.text?.lineSequence()?.first() ?: "",
                        lastPrompt = null,
                        cwd = state.cwd,
                        gitBranch = state.git?.branch,
                        lastModified = System.currentTimeMillis(),
                        appStatus = state.status,
                        openElsewhere = null,
                        project = projectOf(state.cwd),
                    )
                }
        _rows.value = (unsaved + fromDisk).sortedByDescending { it.lastModified }
    }

    companion object {
        private const val TAG = "ClaudeCodeService"
        private const val WORKTREES_SUFFIX = "-worktrees"

        /** The app's one service. Its processes end with the app. */
        val shared: ClaudeCodeService by lazy {
            ClaudeCodeService().also { service ->
                Runtime.getRuntime().addShutdownHook(Thread({ service.shutdown() }, "claude-code-shutdown"))
            }
        }
    }
}
