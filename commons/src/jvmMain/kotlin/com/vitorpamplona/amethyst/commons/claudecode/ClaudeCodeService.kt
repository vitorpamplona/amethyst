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
import com.vitorpamplona.agents.AgentBackend
import com.vitorpamplona.agents.AgentLiveSession
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.amethyst.commons.util.appDataDir
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.codex.CodexBackend
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val openElsewhere: AgentLiveSession?,
    /** The repository (or folder) the session belongs to, worktrees included: the list's sections. */
    val project: String,
    /** Which agent the session is with ([AgentBackend.id]) and its name. */
    val agentId: String = "",
    val agentName: String = "",
    /** Its folder is gone (a removed worktree): kept for its history, out of the way. */
    val archived: Boolean = false,
    /** The workspace of fresh clones the session works in, when it has one. */
    val workspace: AgentWorkspace? = null,
)

/** A chat that wants the user: it waits on an approval, or its turn ended. */
@Immutable
data class ChatAttention(
    val key: String,
    val title: String,
    val kind: Kind,
    /** The agent, as a sentence names it ("Codex finished"). */
    val agent: String = "Claude",
) {
    enum class Kind {
        NEEDS_APPROVAL,
        FINISHED,
    }
}

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
    /** The agents the chats can talk to; the first is the default. */
    val backends: List<AgentBackend> = listOf(ClaudeBackend(), CodexBackend()),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Each session's own clones of a group of repositories. */
    val agentWorkspaces: AgentWorkspaces = AgentWorkspaces(),
) {
    constructor(
        backend: AgentBackend,
        scope: CoroutineScope,
        agentWorkspaces: AgentWorkspaces,
    ) : this(listOf(backend), scope, agentWorkspaces)

    /** The default agent: Claude Code. */
    val backend: AgentBackend get() = backends.first()

    /** Which agent saved each session, as of the last [refresh]. */
    private val owners = ConcurrentHashMap<String, AgentBackend>()

    private fun backendOf(sessionId: String?): AgentBackend = sessionId?.let { owners[it] } ?: backend

    fun backend(id: String): AgentBackend? = backends.firstOrNull { it.id == id }

    private val _installed = MutableStateFlow<List<AgentBackend>>(emptyList())

    /** The agents installed on this computer, as of the last [refresh]. */
    val installed: StateFlow<List<AgentBackend>> = _installed.asStateFlow()

    private val chats = MutableStateFlow<Map<String, ClaudeChat>>(emptyMap())
    private val watchers = ConcurrentHashMap<String, Job>()
    private val saved = MutableStateFlow<List<AgentSessionInfo>>(emptyList())
    private val elsewhere = MutableStateFlow<Map<String, AgentLiveSession>>(emptyMap())

    /** Session folders that are gone, checked on each [refresh] rather than on every list rebuild. */
    @Volatile
    private var goneFolders: Set<String> = emptySet()

    private val _rows = MutableStateFlow<List<ClaudeSessionRow>>(emptyList())

    /** Every session, newest first. Call [refresh] to re-read the disk. */
    val rows: StateFlow<List<ClaudeSessionRow>> = _rows.asStateFlow()

    private val projects = ConcurrentHashMap<String, ProjectInfo>()

    private val _attention = MutableSharedFlow<ChatAttention>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Chats asking for the user, as it happens: what an OS notification tells them about. */
    val attention: SharedFlow<ChatAttention> = _attention.asSharedFlow()

    private val _needsYou = MutableStateFlow(0)

    /** How many chats wait on an approval right now. */
    val needsYou: StateFlow<Int> = _needsYou.asStateFlow()

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

    /** The workspaces as of the last [refresh] or change: rows look theirs up without reading the disk. */
    @Volatile
    private var knownWorkspaces: List<AgentWorkspace> = emptyList()

    private fun workspaceOf(cwd: String?): AgentWorkspace? {
        cwd ?: return null
        val path = File(cwd).absoluteFile.normalize().path
        return knownWorkspaces.firstOrNull { path == it.dir || path.startsWith(it.dir + File.separator) }
    }

    /** When each workspace's pull requests were last asked of GitHub, by its id. */
    private val prCheckedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var prCheck: Job? = null

    /**
     * Asks GitHub, now and then, about the pull requests of the workspaces whose branch went to
     * their remote: so a merged one shows as merged in the list. In the background; never blocks.
     */
    private fun checkPullRequests() {
        if (prCheck?.isActive == true) return
        val now = System.currentTimeMillis()
        // A workspace whose every pull request is merged or closed has nothing more to learn.
        val due =
            knownWorkspaces.filter { ws ->
                now - (prCheckedAt[ws.id] ?: 0L) > PR_CHECK_MS && !ws.repos.all { ws.pullRequests[it.name]?.isOpen == false }
            }
        if (due.isEmpty()) return
        prCheck =
            scope.launch {
                val gh = GitHubPullRequests.find() ?: return@launch
                var changed = false
                due.forEach { workspace ->
                    prCheckedAt[workspace.id] = System.currentTimeMillis()
                    workspace.repos.forEach { repo ->
                        val dir = File(workspace.dir, repo.name)
                        val saved = workspace.pullRequests[repo.name]
                        // A branch never pushed has no pull request to look for.
                        val pushed = saved != null || withContext(Dispatchers.IO) { GitWorkspace.git(dir, "rev-parse", "--verify", "--quiet", "refs/remotes/origin/${repo.branch}") } != null
                        if (pushed && saved?.isOpen != false) {
                            val pr = GitHubPullRequests.state(dir, repo.branch, gh)
                            // No answer is not "no pull request": gh may be offline. Keep what was seen.
                            if (pr != null && (saved?.state != pr.state || saved.url != pr.url || saved.isDraft != pr.isDraft)) {
                                recordPullRequest(workspace.id, repo.name, pr, notify = false)
                                changed = true
                            }
                        }
                    }
                }
                if (changed) rebuildRows()
            }
    }

    /** Saves what GitHub said about [repo]'s pull request in workspace [id], for the list's badge. */
    fun recordPullRequest(
        id: String,
        repo: String,
        pr: PullRequestInfo?,
        notify: Boolean = true,
    ) {
        val updated = agentWorkspaces.recordPullRequest(id, repo, pr) ?: return
        knownWorkspaces = knownWorkspaces.map { if (it.id == id) updated else it }
        if (notify) rebuildRows()
    }

    /**
     * Ends the chats working in [workspace] and removes its clones and record; the mirrors stay.
     * The sessions' histories stay too, listed with the archived ones.
     */
    suspend fun deleteWorkspace(workspace: AgentWorkspace) {
        val inside =
            chats.value.values.filter { chat ->
                val path = File(chat.state.value.cwd).absoluteFile.normalize().path
                path == workspace.dir || path.startsWith(workspace.dir + File.separator)
            }
        // Waits for the processes to exit: not on the main thread.
        withContext(Dispatchers.IO) { inside.forEach { it.kill() } }
        inside.forEach { chat ->
            watchers.remove(chat.key)?.cancel()
            chats.update { it - chat.key }
        }
        recountNeedsYou()
        agentWorkspaces.delete(workspace)
        knownWorkspaces = knownWorkspaces.filter { it.id != workspace.id }
        refresh()
    }

    /** The agent to offer first for a new session: the one the last session started with, if installed. */
    fun preferredAgent(): AgentBackend? {
        val installed = _installed.value
        return agentWorkspaces.lastAgent?.let { id -> installed.firstOrNull { it.id == id } } ?: installed.firstOrNull()
    }

    private fun rememberAgent(agent: AgentBackend) {
        if (agentWorkspaces.lastAgent != agent.id) agentWorkspaces.lastAgent = agent.id
    }

    private val _executable = MutableStateFlow<File?>(null)

    /** The `claude` the chats run, or null when it isn't installed (or not found yet). */
    val executable: StateFlow<File?> = _executable.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Re-reads the saved sessions and which ones are open elsewhere. */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val found = backends.filter { it.executable() != null }
            _installed.value = found
            _executable.value = found.firstNotNullOfOrNull { it.executable() }
            // Every agent: Claude's saved sessions are files, readable even without `claude`.
            saved.value =
                backends
                    .flatMap { agent ->
                        try {
                            agent.listSessions().onEach { owners[it.sessionId] = agent }
                        } catch (e: IOException) {
                            Log.w(TAG, "Could not list ${agent.displayName} sessions", e)
                            emptyList()
                        }
                    }.sortedByDescending { it.lastModified }
            goneFolders =
                saved.value
                    .mapNotNull { it.cwd }
                    .distinct()
                    .filterTo(HashSet()) { !File(it).isDirectory }
            (saved.value.mapNotNull { it.cwd } + chats.value.values.map { it.state.value.cwd })
                .distinct()
                .filter { !projects.containsKey(it) }
                .forEach { cwd -> computeProject(cwd)?.let { projects[cwd] = it } }
            elsewhere.value = liveSessions()
            knownWorkspaces = agentWorkspaces.list()
        }
        _loaded.value = true
        rebuildRows()
        checkPullRequests()
    }

    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    /** The chat for [key]: an open one, or a saved session opened from its file. */
    fun chat(key: String): ClaudeChat? {
        chats.value[key]?.let { return it }
        if (key.startsWith(WORKSPACE_KEY)) return workspaceChat(key)
        chats.value.values
            .firstOrNull { it.state.value.sessionId == key }
            ?.let { return it }
        val info =
            saved.value.firstOrNull { it.sessionId == key }
                ?: backends.firstNotNullOfOrNull { agent -> agent.session(key)?.also { owners[it.sessionId] = agent } }
                ?: return null
        // A folder that is gone stays the chat's folder: the history reads, and the chat refuses
        // to start rather than resume in some other folder.
        val cwd = info.cwd?.let { File(it) } ?: File(System.getProperty("user.home"))
        return open(key, info.sessionId, cwd, info.title, PermissionMode.DEFAULT, null, agent = backendOf(info.sessionId))
    }

    /**
     * A chat for a workspace whose session never started (no message was sent before the app
     * closed): the one open in it, or a new one with the agent it was made for.
     */
    private fun workspaceChat(key: String): ClaudeChat? {
        val workspace = knownWorkspaces.firstOrNull { WORKSPACE_KEY + it.id == key } ?: return null
        chats.value.values
            .firstOrNull { it.state.value.cwd == workspace.dir }
            ?.let { return it }
        val agent = workspace.agentId?.let { backend(it) } ?: backend
        return newChat(File(workspace.dir), title = workspace.title, agent = agent)
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
        agent: AgentBackend = backend,
    ): ClaudeChat {
        rememberAgent(agent)
        return open("new-${UUID.randomUUID()}", null, cwd, title?.trim()?.ifEmpty { null }, permissionMode, model, agent = agent)
    }

    /**
     * A new chat in a workspace of its own: fresh clones of [urls], each on a new branch. Blocks
     * while the mirrors fetch and the clones are made; throws [IOException] when one fails.
     */
    suspend fun newWorkspaceChat(
        urls: List<String>,
        title: String?,
        permissionMode: PermissionMode,
        agent: AgentBackend = backend,
    ): ClaudeChat {
        val workspace = agentWorkspaces.create(urls, title, agentId = agent.id)
        agentWorkspaces.saveGroup(RepoGroup(LAST_GROUP, workspace.repos.map { it.url }))
        knownWorkspaces = listOf(workspace) + knownWorkspaces.filter { it.id != workspace.id }
        return newChat(File(workspace.dir), permissionMode, title = workspace.title, agent = agent)
    }

    /**
     * A new chat that starts from [key]'s history and branches off it with its first message:
     * the way to go on with a session another Claude Code holds, without both writing to it.
     */
    fun fork(key: String): ClaudeChat? {
        val source = chats.value[key]?.state?.value
        val sessionId = source?.sessionId ?: saved.value.firstOrNull { it.sessionId == key }?.sessionId ?: return null
        val cwd = source?.cwd ?: saved.value.firstOrNull { it.sessionId == sessionId }?.cwd ?: return null
        val agent = chats.value[key]?.backend ?: backendOf(sessionId)
        return open("fork-${UUID.randomUUID()}", sessionId, File(cwd), null, source?.permissionMode ?: PermissionMode.DEFAULT, null, fork = true, agent = agent)
    }

    /**
     * A new chat to ask [message] again differently: a copy of [key]'s session cut just before
     * that message, or, when it has nothing before it (or isn't in the saved history), a fresh
     * session in the same folder. Its text waits in the new chat's composer ([ClaudeChat.draft]).
     * Blocking: reads the session's file.
     */
    fun editFrom(
        key: String,
        message: ChatItem.UserText,
    ): ClaudeChat? {
        val chat = chats.value[key]
        val source = chat?.state?.value
        val sessionId = source?.sessionId ?: saved.value.firstOrNull { it.sessionId == key }?.sessionId ?: return null
        val cwd = source?.cwd ?: saved.value.firstOrNull { it.sessionId == sessionId }?.cwd ?: return null
        val mode = source?.permissionMode ?: PermissionMode.DEFAULT
        val model = source?.modelChoice
        // The same prompt can be sent more than once: the one with as many before it as on screen.
        val repeats = source?.items?.takeWhile { it.id != message.id }?.count { it is ChatItem.UserText && it.text == message.text } ?: 0
        val agent = chat?.backend ?: backendOf(sessionId)
        val prompt = promptsIn(agent.transcript(sessionId)).filter { it.second == message.text }.getOrNull(repeats)?.first
        val copy =
            if (prompt?.parentUuid == null) {
                open("new-${UUID.randomUUID()}", null, File(cwd), null, mode, model, agent = agent)
            } else {
                open("fork-${UUID.randomUUID()}", sessionId, File(cwd), null, mode, model, fork = true, forkBefore = prompt, agent = agent)
            }
        copy.draft = message.text
        return copy
    }

    /** The saved prompts the user typed, with their text as the chat shows it. */
    private fun promptsIn(history: List<TranscriptEntry>): List<Pair<TranscriptEntry, String>> =
        history.mapNotNull { entry ->
            if (entry.role != TranscriptEntry.Role.USER) return@mapNotNull null
            val text = entry.content.firstNotNullOfOrNull { (it as? ContentBlock.Text)?.text?.trim() } ?: return@mapNotNull null
            if (text.isEmpty() || text.startsWith("<") || text.startsWith("[")) null else entry to text
        }

    private fun open(
        key: String,
        sessionId: String?,
        cwd: File,
        title: String?,
        mode: PermissionMode,
        model: String?,
        fork: Boolean = false,
        forkBefore: TranscriptEntry? = null,
        mcpServers: List<HostToolServer> = emptyList(),
        launchOptions: (AgentStartOptions) -> AgentStartOptions = { it },
        kind: ChatKind = ChatKind.CODE,
        agent: AgentBackend = backend,
    ): ClaudeChat {
        val chat =
            ClaudeChat(
                key,
                sessionId,
                cwd,
                title,
                mode,
                model,
                agent,
                scope,
                onSessionChanged = { refreshInBackground() },
                fork = fork,
                heldElsewhere = ::isHeldElsewhere,
                mcpServers = mcpServers,
                launchOptions = launchOptions,
                kind = kind,
                forkBefore = forkBefore,
                publishingIsTheUsers = { agentWorkspaces.of(it) != null },
            )
        chats.update { it + (key to chat) }
        watchers[key] =
            scope.launch {
                // The list only shows these; streamed text must not rebuild it.
                launch {
                    chat.state
                        .map { Triple(it.sessionId, it.title, it.status) to it.items.firstPromptLine() }
                        .distinctUntilChanged()
                        .collect { if (chats.value[key] === chat) rebuildRows() }
                }
                var last: ChatStatus? = null
                chat.state
                    .map { it.status }
                    .distinctUntilChanged()
                    .collect { status ->
                        val wasRunning = last == ChatStatus.WORKING || last == ChatStatus.NEEDS_APPROVAL
                        when {
                            status == ChatStatus.NEEDS_APPROVAL -> ChatAttention.Kind.NEEDS_APPROVAL
                            wasRunning && (status == ChatStatus.READY || status == ChatStatus.ENDED) -> ChatAttention.Kind.FINISHED
                            else -> null
                        }?.let { _attention.tryEmit(ChatAttention(chat.key, titleOf(chat.state.value), it, chat.backend.shortName)) }
                        last = status
                        recountNeedsYou()
                    }
            }
        return chat
    }

    private fun recountNeedsYou() {
        _needsYou.value = chats.value.values.count { it.state.value.status == ChatStatus.NEEDS_APPROVAL }
    }

    /**
     * Whether a Claude Code process other than this app's own has [sessionId] open, read from the
     * CLI's registry now rather than from the last [refresh]: a terminal may have just opened it.
     */
    private fun liveSessions(): Map<String, AgentLiveSession> =
        backends
            .filter { it.capabilities.liveSessions }
            .flatMap { agent ->
                try {
                    agent.liveSessions().entries
                } catch (e: IOException) {
                    Log.w(TAG, "Could not read the running ${agent.displayName} sessions", e)
                    emptySet()
                }
            }.associate { it.key to it.value }

    private fun isHeldElsewhere(sessionId: String): Boolean {
        val agent = backendOf(sessionId)
        if (!agent.capabilities.liveSessions) return false
        val live =
            try {
                agent.liveSessions()[sessionId]
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the running ${agent.displayName} sessions", e)
                null
            } ?: return false
        return ProcessHandle.current().descendants().noneMatch { it.pid() == live.pid }
    }

    /**
     * Re-reads only which sessions other processes hold: cheap (a few small files), for the
     * screens that show a session's read-only state while the full [refresh] isn't running.
     */
    suspend fun refreshLive() {
        withContext(Dispatchers.IO) {
            elsewhere.value = liveSessions()
        }
        rebuildRows()
    }

    /**
     * Names the chat [key]. A session that exists is renamed the way the CLI does it; a chat
     * whose session doesn't exist yet (new, or a copy before its first message) keeps the name
     * and gives it to the session once the CLI creates it, never to the copy's source.
     */
    suspend fun renameChat(
        key: String,
        title: String,
    ) {
        val clean = title.trim().ifEmpty { return }
        val chat = chats.value[key] ?: return
        val state = chat.state.value
        val sessionId = state.sessionId
        if (sessionId == null || state.forking) {
            chat.nameBeforeStart(clean)
            return
        }
        if (!chat.renameLive(clean)) {
            withContext(Dispatchers.IO) { backendOf(sessionId).rename(sessionId, clean) }
            chat.titleChanged(clean)
        }
        refresh()
    }

    private fun titleOf(state: ClaudeChatState): String =
        state.title?.takeIf { it.isNotBlank() }
            ?: state.items.firstPromptLine()
            ?: folderProject(File(state.cwd))

    suspend fun rename(
        row: ClaudeSessionRow,
        title: String,
    ) {
        val clean = title.trim().ifEmpty { return }
        // An open chat knows whether its session exists yet.
        if (chats.value[row.key] != null) return renameChat(row.key, clean)
        val chat = chats.value[row.key]
        val renamedLive = chat?.renameLive(clean) ?: false
        val sessionId = row.sessionId
        if (!renamedLive && sessionId != null) {
            withContext(Dispatchers.IO) { backendOf(sessionId).rename(sessionId, clean) }
            chat?.titleChanged(clean)
        }
        refresh()
    }

    /** Ends the session's chat here and deletes its file. */
    suspend fun delete(row: ClaudeSessionRow) {
        chats.value[row.key]?.let { chat ->
            // Waits for the process to exit: not on the main thread.
            withContext(Dispatchers.IO) { chat.kill() }
            watchers.remove(row.key)?.cancel()
            chats.update { it - row.key }
            // Its own status watcher is gone: a chat deleted while waiting must leave the count.
            recountNeedsYou()
            rebuildRows()
        }
        val sessionId = row.sessionId
        if (sessionId != null) withContext(Dispatchers.IO) { backendOf(sessionId).delete(sessionId) }
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
        agent: AgentBackend = backend,
    ): ClaudeChat {
        val workspace = workspaces.prepare(checkout, target, branch)
        val chat = newChat(workspace.dir, permissionMode, title = title, agent = agent)
        if (prompt.isNotBlank()) chat.send(prompt)
        return chat
    }

    /**
     * The settings assistant: a chat whose only tools are [settings] (no files, no shell, none of
     * the user's own MCP servers), in a folder of its own. Reading a setting runs without asking;
     * every change waits for the user's approval. The same chat comes back while it is open.
     */
    fun settingsAssistant(
        settings: SettingsToolServer,
        title: String,
    ): ClaudeChat {
        settingsAssistantKey?.let { key -> chats.value[key]?.let { return it } }
        val dir = File(appDataDir, "claude-settings-assistant")
        if (!dir.isDirectory && !dir.mkdirs()) Log.w(TAG) { "Could not create $dir" }
        val chat =
            open(
                "settings-${UUID.randomUUID()}",
                null,
                dir,
                title,
                PermissionMode.DEFAULT,
                null,
                kind = ChatKind.SETTINGS,
                mcpServers = listOf(settings),
                agent = backends.firstOrNull { it.capabilities.hostTools } ?: backend,
                launchOptions = { options ->
                    options.copy(
                        onlyHostTools = true,
                        preApprovedTools = listOf(SettingsToolServer.SEARCH, SettingsToolServer.GET, SettingsToolServer.OPEN).map(settings::qualifiedName),
                        instructions = SettingsToolServer.SYSTEM_PROMPT,
                    )
                },
            )
        settingsAssistantKey = chat.key
        return chat
    }

    @Volatile
    private var settingsAssistantKey: String? = null

    /** Stops every process this app started. */
    fun shutdown() {
        chats.value.values.forEach { it.kill() }
    }

    @Synchronized
    private fun rebuildRows() {
        val open = chats.value
        val openBySession =
            open.values
                // A fork that hasn't started still carries its source's id; the row is the source's.
                .filter { !it.state.value.forking }
                .mapNotNull { chat ->
                    chat.state.value.sessionId
                        ?.let { it to chat }
                }.toMap()
        val live = elsewhere.value
        val gone = goneFolders
        // Walking the process tree reads every process on the machine: only when a session is
        // open elsewhere and the question is whether that is one of this app's own.
        val ownPids by lazy {
            ProcessHandle
                .current()
                .descendants()
                .map { it.pid() }
                .toList()
                .toSet()
        }

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
                    archived = info.cwd in gone,
                    workspace = workspaceOf(info.cwd),
                    agentId = backendOf(info.sessionId).id,
                    agentName = backendOf(info.sessionId).displayName,
                )
            }
        val savedIds = saved.value.mapTo(HashSet()) { it.sessionId }
        // New chats before their file exists.
        val unsaved =
            open.values
                .filter { chat ->
                    chat.state.value.forking ||
                        chat.state.value.sessionId
                            .let { it == null || it !in savedIds }
                }.map { chat ->
                    val state = chat.state.value
                    ClaudeSessionRow(
                        key = chat.key,
                        // A fork that hasn't started still holds its source's id: the row must not
                        // let a rename or a delete reach the source's file.
                        sessionId = state.sessionId.takeIf { !state.forking },
                        title = state.title ?: state.items.firstPromptLine() ?: "",
                        lastPrompt = null,
                        cwd = state.cwd,
                        gitBranch = state.git?.branch,
                        lastModified = System.currentTimeMillis(),
                        appStatus = state.status,
                        openElsewhere = null,
                        project = projectOf(state.cwd),
                        workspace = workspaceOf(state.cwd),
                        agentId = chat.backend.id,
                        agentName = chat.backend.displayName,
                    )
                }
        // Workspaces no session works in: made, but the app closed before their first message.
        // Listed so they can be opened, or deleted, rather than sit unseen on disk.
        val rowDirs = (unsaved + fromDisk).mapNotNull { it.cwd?.let { cwd -> File(cwd).absoluteFile.normalize().path } }
        val idle =
            knownWorkspaces
                .filter { ws -> rowDirs.none { it == ws.dir || it.startsWith(ws.dir + File.separator) } }
                .map { ws ->
                    val agent = ws.agentId?.let { backend(it) } ?: backend
                    ClaudeSessionRow(
                        key = WORKSPACE_KEY + ws.id,
                        sessionId = null,
                        title = ws.title,
                        lastPrompt = null,
                        cwd = ws.dir,
                        gitBranch = ws.repos.firstOrNull()?.branch,
                        lastModified = ws.createdAt,
                        appStatus = null,
                        openElsewhere = null,
                        project = ws.title,
                        workspace = ws,
                        agentId = agent.id,
                        agentName = agent.displayName,
                    )
                }
        _rows.value = (unsaved + fromDisk + idle).sortedByDescending { it.lastModified }
    }

    companion object {
        private const val TAG = "ClaudeCodeService"

        /** The repositories of the last workspace, offered first next time. */
        const val LAST_GROUP = "Last used"
        private const val WORKTREES_SUFFIX = "-worktrees"
        private const val PR_CHECK_MS = 10 * 60_000L

        /** The key of a workspace's row when no session works in it yet. */
        const val WORKSPACE_KEY = "workspace:"

        /** The app's one service. Its processes end with the app. */
        val shared: ClaudeCodeService by lazy {
            ClaudeCodeService().also { service ->
                Runtime.getRuntime().addShutdownHook(Thread({ service.shutdown() }, "claude-code-shutdown"))
            }
        }
    }
}
