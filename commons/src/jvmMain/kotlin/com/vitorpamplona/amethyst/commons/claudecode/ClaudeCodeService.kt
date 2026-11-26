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
import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.AgentLiveSession
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.amethyst.commons.androiddevices.AndroidDevices
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceToolServer
import com.vitorpamplona.amethyst.commons.util.appDataDir
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.codex.CodexBackend
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    /** The user archived it (rather than its folder being gone): it can be brought back. */
    val userArchived: Boolean = false,
    /** The disk its workspace's clones use, once measured. */
    val workspaceSize: Long? = null,
    /** Its last turn here ended while the user wasn't looking: the agent waits for them. */
    val unseenFinish: Boolean = false,
    /** Its last turn here ended in an error. */
    val lastTurnFailed: Boolean = false,
    /** It waits on the user's answers to its questions ([ChatState.isAsking]). */
    val asking: Boolean = false,
) {
    /** The agent waits on the user: for an approval, or to read how its turn ended. */
    val needsYou: Boolean get() = needsUser(appStatus, unseenFinish)
}

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

        /** The agent asked the user questions. */
        ASKS,
        FINISHED,

        /** The agent asked the user to open a pull request ([PublishToolServer]). */
        REQUESTS_PUBLISH,
    }
}

/** What a folder is part of: a NIP-34 repository, a git repository, or just itself. */
@Immutable
data class ProjectInfo(
    val name: String,
    val repo: NostrRepoRef?,
)

/**
 * The mode a workstream runs in: Claude's Auto; Codex, which has none, works freely inside its sandbox
 * and asks only to leave it. A workstream's clones are its own and pushes are blocked, so neither asks
 * about edits in them.
 */
fun workstreamModeOf(modes: List<PermissionMode>): PermissionMode =
    when {
        PermissionMode.AUTO in modes -> PermissionMode.AUTO
        PermissionMode.ACCEPT_EDITS in modes -> PermissionMode.ACCEPT_EDITS
        else -> PermissionMode.DEFAULT
    }

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
    /** Where the settings assistant's sessions run: a folder of its own, so a session saved there is the assistant's. */
    val assistantDir: File = File(appDataDir, ASSISTANT_FOLDER),
    /** The assistant on the user's own AI key, for when no agent program here can run it. */
    val online: OnlineAgentBackend = OnlineAgentBackend(OnlineSessionStore(File(appDataDir, ONLINE_SESSIONS_FOLDER))),
    /** Whether coding chats get the Android device tools: when an Android SDK is installed here. */
    private val deviceTools: Boolean = AndroidSdk.home() != null,
) {
    /** Every agent whose sessions the list shows: the coding agents, and the assistant's online one. */
    private val allAgents: List<AgentBackend> get() = backends + online

    constructor(
        backend: AgentBackend,
        scope: CoroutineScope,
        agentWorkspaces: AgentWorkspaces,
    ) : this(listOf(backend), scope, agentWorkspaces, deviceTools = false)

    /** The default agent: Claude Code. */
    val backend: AgentBackend get() = backends.first()

    /** Which agent saved each session, as of the last [refresh]. */
    private val owners = ConcurrentHashMap<String, AgentBackend>()

    private fun backendOf(sessionId: String?): AgentBackend = sessionId?.let { owners[it] } ?: backend

    fun backend(id: String): AgentBackend? = allAgents.firstOrNull { it.id == id }

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

    /** How many chats wait on the user right now: on an approval, or with a turn they haven't seen end. */
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

    /**
     * The workspaces as of the last [refresh] or change: rows look theirs up without reading the
     * disk. Changed only through [MutableStateFlow.update]: frees, restores and pull request checks
     * change it from several threads, and none may undo another's change.
     */
    private val workspacesState = MutableStateFlow<List<AgentWorkspace>>(emptyList())

    private val knownWorkspaces: List<AgentWorkspace> get() = workspacesState.value

    /** Puts [updated] in place of the workspace of its id. */
    private fun replaceWorkspace(updated: AgentWorkspace) = workspacesState.update { list -> list.map { if (it.id == updated.id) updated else it } }

    /** The workstream [cwd] is in, as of the last refresh or change; for screens, which must not read the disk. */
    fun workspaceAt(cwd: String?): AgentWorkspace? = workspaceOf(cwd)

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
                        // Only GitHub has pull requests to ask `gh` about.
                        val upstream = GitHubPullRequests.gitHubRepo(repo.url) ?: return@forEach
                        val clone = File(workspace.dir, repo.name)
                        // A freed workstream has no clone: `gh` runs in its folder, and asks by URL or by repository.
                        val dir = if (clone.isDirectory) clone else File(workspace.dir)
                        val saved = workspace.pullRequests[repo.name]
                        // A branch never pushed has no pull request to look for.
                        val pushed = saved != null || withContext(Dispatchers.IO) { GitWorkspace.git(clone, "rev-parse", "--verify", "--quiet", "refs/remotes/origin/${repo.branch}") } != null
                        if (pushed && saved?.isOpen != false) {
                            val pr = GitHubPullRequests.state(dir, repo.branch, gh, upstream, knownUrl = saved?.url)
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

    /**
     * Saves what the agent asked to publish from [repo] in workspace [id], or clears it (null) once
     * the user opened or dismissed it: the list and the Pull requests screen show it.
     */
    fun recordPublishRequest(
        id: String,
        repo: String,
        request: PublishRequest?,
    ) {
        val updated = agentWorkspaces.recordPublishRequest(id, repo, request) ?: return
        replaceWorkspace(updated)
        rebuildRows()
    }

    /** Saves what GitHub said about [repo]'s pull request in workspace [id], for the list's badge. */
    fun recordPullRequest(
        id: String,
        repo: String,
        pr: PullRequestInfo?,
        notify: Boolean = true,
    ) {
        val updated = agentWorkspaces.recordPullRequest(id, repo, pr) ?: return
        replaceWorkspace(updated)
        if (notify) rebuildRows()
    }

    /**
     * Ends the chats working in [workspace] and removes its clones and record; the mirrors stay.
     * The sessions' histories stay too, listed with the archived ones.
     */
    suspend fun deleteWorkspace(
        workspace: AgentWorkspace,
        /** False when the caller removes several and refreshes once after. */
        thenRefresh: Boolean = true,
    ) {
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
        workspacesState.update { list -> list.filter { it.id != workspace.id } }
        // Its emulator copies held only its work.
        AndroidDevices.shared.releaseOwner(workspace.id)
        if (thenRefresh) refresh()
    }

    /** The agent to offer first for a new session: the one the last session started with, if installed. */
    fun preferredAgent(): AgentBackend? {
        val installed = _installed.value
        return agentWorkspaces.lastAgent?.let { id -> installed.firstOrNull { it.id == id } } ?: installed.firstOrNull()
    }

    private fun rememberAgent(agent: AgentBackend) {
        if (agentWorkspaces.lastAgent != agent.id) agentWorkspaces.lastAgent = agent.id
    }

    private val _preferences = MutableStateFlow(AgentPreferences())

    /** The user's choices about workspaces, as last read or changed. */
    val preferences: StateFlow<AgentPreferences> = _preferences.asStateFlow()

    /** Turns the automatic cleanup of finished workspaces on or off. */
    fun setAutoCleanup(on: Boolean) {
        _preferences.value = agentWorkspaces.updatePreferences { it.copy(autoCleanup = on) }
    }

    /** The disk each workspace's clones use, by its id, and when that was measured. */
    private val workspaceSizes = ConcurrentHashMap<String, Pair<Long, Long>>()

    @Volatile
    private var sizing: Job? = null

    /** Measures the workspaces whose size is unknown or old, in the background, then shows it. */
    private fun measureWorkspaces(force: Boolean = false) {
        if (sizing?.isActive == true) return
        val now = System.currentTimeMillis()
        val due = knownWorkspaces.filter { force || now - (workspaceSizes[it.id]?.second ?: 0L) > SIZE_CHECK_MS }
        if (due.isEmpty()) return
        sizing =
            scope.launch {
                due.forEach { workspaceSizes[it.id] = agentWorkspaces.sizeOf(it) to System.currentTimeMillis() }
                rebuildRows()
            }
    }

    /** The disk [workspace]'s clones use, measured now. */
    suspend fun measure(workspace: AgentWorkspace): Long = agentWorkspaces.sizeOf(workspace).also { workspaceSizes[workspace.id] = it to System.currentTimeMillis() }

    /** The workspaces whose work is done: every pull request merged or closed. */
    fun finishedWorkspaces(): List<AgentWorkspace> = knownWorkspaces.filter { it.finishedAt != null }

    /** The chats open in [workspace]'s folder. */
    private fun chatsIn(workspace: AgentWorkspace): List<ClaudeChat> =
        chats.value.values.filter { chat ->
            val path = File(chat.state.value.cwd).absoluteFile.normalize().path
            path == workspace.dir || path.startsWith(workspace.dir + File.separator)
        }

    /** Whether a chat is busy in [workspace]: it must not be removed under the agent. */
    private fun isBusy(workspace: AgentWorkspace): Boolean = chatsIn(workspace).any { it.state.value.isBusy }

    /** One free or restore at a time per workstream, by its id: neither runs under the other. */
    private val workspaceLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockOf(workspace: AgentWorkspace): Mutex = workspaceLocks.getOrPut(workspace.id) { Mutex() }

    /** [workspace] as last saved or changed here: a screen may hold an older copy. */
    private fun latest(workspace: AgentWorkspace): AgentWorkspace = knownWorkspaces.firstOrNull { it.id == workspace.id } ?: workspace

    /**
     * Frees the clones of [workspaces] that are safe to free ([freeWorkspace]): no busy chat, no
     * work a bundle can't carry. Their sessions are archived; a message to one brings the clones
     * back. Returns those freed.
     */
    suspend fun cleanUp(workspaces: List<AgentWorkspace>): List<AgentWorkspace> {
        val freed = workspaces.filter { !latest(it).isFreed && freeWorkspace(it) }
        if (freed.isNotEmpty()) {
            val ids = freed.mapTo(HashSet()) { it.id }
            val appArchived = preferences.value.archivedSessions
            setArchived(
                saved.value
                    .filter { workspaceOf(it.cwd)?.id in ids && !it.archived && it.sessionId !in appArchived }
                    .map { it.sessionId },
                archived = true,
            )
            refresh()
        }
        return freed
    }

    /**
     * Gives [workspace]'s disk back ([AgentWorkspaces.free]) unless a chat is busy in it or it
     * holds work only its folder has; then nothing changes, its chats included. Else the idle chats
     * there end first; their next message brings the clones back ([restoreBeforeStart]). Whether
     * it is freed.
     */
    private suspend fun freeWorkspace(workspace: AgentWorkspace): Boolean =
        lockOf(workspace).withLock {
            val current = latest(workspace)
            if (current.isFreed) return@withLock true
            // A message sent meanwhile marks its chat busy before it waits for this lock.
            if (isBusy(current) || agentWorkspaces.cannotFree(current)) return@withLock false
            // Idle, each ends at once; the folder they started in stays, only the clones go.
            chatsIn(current).forEach { it.close() }
            try {
                val freed = agentWorkspaces.free(current)
                replaceWorkspace(freed)
                // Idle long enough to give its disk back: its emulator copies go too.
                AndroidDevices.shared.releaseOwner(freed.id)
                workspaceSizes[freed.id] = 0L to System.currentTimeMillis()
                true
            } catch (e: IOException) {
                // UnsavedWorkException included: work that appeared since the check.
                Log.w(TAG, "Kept the clones of ${current.dir}", e)
                // As the disk has it: a free that failed late may have saved the record as freed.
                val onDisk = withContext(Dispatchers.IO) { agentWorkspaces.get(current.id) }
                onDisk?.let(::replaceWorkspace)
                if (onDisk?.isFreed == true) workspaceSizes.remove(onDisk.id)
                onDisk?.isFreed == true
            }
        }

    /**
     * Makes a freed [workspace]'s clones again ([AgentWorkspaces.restore]), on the service's scope:
     * a screen closed meanwhile doesn't stop it halfway. Under the workstream's lock: a second
     * caller waits for the first, then finds it restored. Throws [IOException] when it can't.
     */
    suspend fun restoreWorkspace(workspace: AgentWorkspace): AgentWorkspace {
        if (!latest(workspace).isFreed && !lockOf(workspace).isLocked) return latest(workspace)
        return scope
            .async(Dispatchers.IO) {
                lockOf(workspace).withLock {
                    val current = latest(workspace)
                    if (!current.isFreed) return@withLock current
                    agentWorkspaces.restore(current).also { restored ->
                        replaceWorkspace(restored)
                        workspaceSizes.remove(restored.id)
                    }
                }
            }.await()
            .also {
                rebuildRows()
                measureWorkspaces()
            }
    }

    /**
     * For a chat in [cwd]: when its workstream is freed, or being freed, brings the clones back
     * before the agent starts, telling [restoring] how many repositories it makes. The chat is
     * already busy, so no new free starts after this looks.
     */
    private suspend fun restoreBeforeStart(
        cwd: File,
        restoring: (Int) -> Unit,
    ) {
        val workspace = workspaceOf(cwd.path) ?: return
        // A free in progress finishes first: then whether the clones are there is known.
        val current = lockOf(workspace).withLock { latest(workspace) }
        if (!current.isFreed) return
        restoring(current.repos.size)
        restoreWorkspace(current)
    }

    @Volatile
    private var lastAutoCleanup = 0L

    /**
     * When the user lets it ([AgentPreferences.autoCleanup]): a day after a workspace's pull
     * requests are all merged or closed, or after the user last brought it back, its clones go
     * ([AgentWorkspace.idleSince]); and mirrors nobody used for long go too. At most once an hour.
     */
    private fun autoCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCleanup < AUTO_CLEANUP_EVERY_MS) return
        lastAutoCleanup = now
        if (!preferences.value.autoCleanup) return
        val due = dueForCleanup(now)
        scope.launch {
            if (due.isNotEmpty()) {
                val removed = cleanUp(due)
                if (removed.isNotEmpty()) Log.i(TAG) { "Freed ${removed.size} finished workspaces" }
            }
            val mirrors = agentWorkspaces.removeUnusedMirrors()
            if (mirrors > 0) Log.i(TAG) { "Removed $mirrors unused mirrors" }
        }
    }

    /** The workstreams whose clones nobody needed for [CLEANUP_AFTER_MS] as of [now]: what the automatic cleanup frees. */
    internal fun dueForCleanup(now: Long): List<AgentWorkspace> = knownWorkspaces.filter { ws -> !ws.isFreed && idleSince(ws)?.let { now - it > CLEANUP_AFTER_MS } == true }

    /**
     * Since when nobody used [workspace]'s clones: its work done, or brought back
     * ([AgentWorkspace.idleSince]), or a session there last moved, whichever came last. Null while
     * its work isn't done, or a chat there is working now.
     */
    private fun idleSince(workspace: AgentWorkspace): Long? {
        val done = workspace.idleSince ?: return null
        if (isBusy(workspace)) return null
        val lastSession =
            saved.value
                .filter { workspaceOf(it.cwd)?.id == workspace.id }
                .maxOfOrNull { it.lastModified } ?: 0L
        return maxOf(done, lastSession)
    }

    /**
     * Archives the row's session, or brings it back ([setArchived]).
     *
     * Archived, its chat ends when idle (the next message starts it again), and, when the user lets
     * the app clean up on its own ([AgentPreferences.autoCleanup]), its workstream's clones are
     * freed once every session there is archived ([freeWorkspace]). Brought back, it is
     * listed again as it is: a freed workstream's clones come back with its next message.
     */
    suspend fun archive(
        row: ClaudeSessionRow,
        archived: Boolean,
    ) {
        val sessionId = row.sessionId ?: return
        setArchived(listOf(sessionId), archived)
        if (archived) {
            // An idle chat would sit there "Ready" under Archived, holding a process.
            chats.value[row.key]?.takeIf { !it.state.value.isBusy }?.close()
            // Only when the user lets the app give disk back on its own ([AgentPreferences.autoCleanup]).
            if (preferences.value.autoCleanup) workspaceOf(row.cwd)?.takeIf { everySessionArchived(it, sessionId) }?.let { freeWorkspace(it) }
        }
        refresh()
    }

    /** Archives [sessionIds], or brings them back: with each agent's own archive when it keeps one, else with the app's. */
    private suspend fun setArchived(
        sessionIds: Collection<String>,
        archived: Boolean,
    ) {
        val appSide =
            sessionIds.filterNot { id ->
                val agent = backendOf(id)
                agent.capabilities.archive && withContext(Dispatchers.IO) { agent.archive(id, archived) }
            }
        if (appSide.isEmpty()) return
        _preferences.value =
            agentWorkspaces.updatePreferences { prefs ->
                prefs.copy(archivedSessions = if (archived) prefs.archivedSessions + appSide else prefs.archivedSessions - appSide.toSet())
            }
    }

    /** Whether every saved session in [workspace] is archived, [justArchived] counted as one. */
    private fun everySessionArchived(
        workspace: AgentWorkspace,
        justArchived: String,
    ): Boolean {
        val appArchived = preferences.value.archivedSessions
        return saved.value
            .filter { workspaceOf(it.cwd)?.id == workspace.id }
            .all { it.sessionId == justArchived || it.archived || it.sessionId in appArchived }
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
                allAgents
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
            workspacesState.value = agentWorkspaces.list()
            _preferences.value = agentWorkspaces.preferences()
        }
        _loaded.value = true
        rebuildRows()
        checkPullRequests()
        measureWorkspaces()
        autoCleanup()
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
                ?: allAgents.firstNotNullOfOrNull { agent -> agent.session(key)?.also { owners[it.sessionId] = agent } }
                ?: return null
        // A folder that is gone stays the chat's folder: the history reads, and the chat refuses
        // to start rather than resume in some other folder. A session that never named one shows
        // the home folder, and refuses the same way.
        // The assistant's session reopens as the assistant: its tools, and none of the coding ones.
        if (isAssistantFolder(info.cwd)) return openAssistant(key, info.sessionId, info.title)
        val cwd = info.cwd?.let { File(it) }
        return open(
            key,
            info.sessionId,
            cwd ?: File(System.getProperty("user.home")),
            info.title,
            savedModeOf(info.sessionId),
            null,
            agent = backendOf(info.sessionId),
            folderKnown = cwd != null,
        )
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
        // Hands-off, as when it was made: its clones are its own.
        return newChat(File(workspace.dir), workstreamModeOf(agent.capabilities.modes), title = workspace.title, agent = agent)
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
        folderKnown: Boolean = true,
    ): ClaudeChat {
        rememberAgent(agent)
        return open("new-${UUID.randomUUID()}", null, cwd, title?.trim()?.ifEmpty { null }, permissionMode, model, agent = agent)
    }

    /**
     * A new workstream called [name]: a chat in a folder of its own, open at once, while fresh clones of
     * [urls] are made into it on a new branch each. The agent starts once they are in, in its
     * most hands-off mode ([workstreamModeOf]): the clones are the workstream's alone, and pushes are the user's.
     * Throws [IOException] only when the folder can't be made; a failed clone shows in the chat.
     */
    suspend fun newWorkstream(
        urls: List<String>,
        name: String,
        agent: AgentBackend = backend,
        permissionMode: PermissionMode = workstreamModeOf(agent.capabilities.modes),
    ): ClaudeChat {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) throw IOException("No repositories")
        val dir = agentWorkspaces.claim(name, clean)
        val chat = newChat(dir, permissionMode, title = name, agent = agent)
        // On the service's scope: the workstream keeps cloning while the user reads something else.
        val cloning =
            scope.async(Dispatchers.IO) {
                // A repository Amethyst knows from a NIP-34 announcement can take a proposal back.
                val nostrRepos = clean.mapNotNull { url -> RepoCatalog.nostrRepoOf(url)?.let { url to it } }.toMap()
                val workspace = agentWorkspaces.create(clean, name, agentId = agent.id, nostrRepos = nostrRepos, claimed = dir)
                // Offered first next time; only once they cloned, so a mistyped URL isn't.
                agentWorkspaces.saveGroup(RepoGroup(LAST_GROUP, workspace.repos.map { it.url }))
                workspacesState.update { list -> listOf(workspace) + list.filter { it.id != workspace.id } }
                rebuildRows()
            }
        chat.waitFor(cloning, clean.size)
        return chat
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
        return open("fork-${UUID.randomUUID()}", sessionId, File(cwd), null, source?.permissionMode ?: savedModeOf(sessionId), null, fork = true, agent = agent)
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
        val mode = source?.permissionMode ?: savedModeOf(sessionId)
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

    /** The mode [sessionId] was last left in here, if its agent still offers it; else the default. */
    private fun savedModeOf(sessionId: String): PermissionMode =
        PermissionMode
            .fromWire(_preferences.value.sessionModes[sessionId])
            ?.takeIf { it in backendOf(sessionId).capabilities.modes }
            ?: PermissionMode.DEFAULT

    /**
     * Each guarded session's push scope, kept with the preferences so a restart doesn't capture it
     * again. Read from the file, not [_preferences], which may not be loaded yet. Called off the main thread.
     */
    private val pushScopeStore =
        object : PushScopeStore {
            override fun load(sessionId: String): PushScope? = agentWorkspaces.preferences().pushScopes[sessionId]

            override fun save(
                sessionId: String,
                scope: PushScope,
            ) {
                _preferences.value = agentWorkspaces.updatePreferences { it.copy(pushScopes = it.pushScopes + (sessionId to scope)) }
            }
        }

    /** Keeps the mode [sessionId] is in, so it reopens in it after a restart ([savedModeOf]). */
    private suspend fun rememberMode(
        sessionId: String,
        mode: PermissionMode,
    ) {
        val wire = mode.wire.takeIf { mode != PermissionMode.DEFAULT }
        if (_preferences.value.sessionModes[sessionId] == wire) return
        _preferences.value =
            withContext(Dispatchers.IO) {
                agentWorkspaces.updatePreferences { prefs ->
                    prefs.copy(sessionModes = if (wire == null) prefs.sessionModes - sessionId else prefs.sessionModes + (sessionId to wire))
                }
            }
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
        folderKnown: Boolean = true,
    ): ClaudeChat {
        // In a workstream, an agent that takes the app's tools can ask the user to publish its work.
        val publish = if (agent.capabilities.hostTools && agentWorkspaces.isInWorkstreams(cwd)) publishTool(key, cwd) else null
        // With an Android SDK here, a coding agent can drive the device panel's emulator or phone.
        val device = if (kind == ChatKind.CODE && agent.capabilities.hostTools && deviceTools) DeviceToolServer(AndroidDevices.shared, AndroidDevices.apps, owner = { deviceOwner(key, cwd.path) }, ownerLabel = { deviceOwnerLabel(key, cwd.path) }, folder = cwd) else null
        val preApproved = listOfNotNull(publish?.qualifiedName(PublishToolServer.REQUEST)) + device?.let { DeviceToolServer.PRE_APPROVED.map(it::qualifiedName) }.orEmpty()
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
                mcpServers = mcpServers + listOfNotNull(publish, device),
                launchOptions =
                    if (preApproved.isEmpty()) {
                        launchOptions
                    } else {
                        // Publishing only asks: the user decides on the Pull requests screen, so the agent needn't ask to ask.
                        // The device's looking and touching need no asking either: the user gave the chat that device.
                        { options -> launchOptions(options).let { it.copy(preApprovedTools = it.preApprovedTools + preApproved) } }
                    },
                kind = kind,
                forkBefore = forkBefore,
                workstreamOf = { agentWorkspaces.of(it) },
                pushScopes = pushScopeStore,
                restoreFolder = ::restoreBeforeStart,
                folderBrief = { dir ->
                    val workspace = agentWorkspaces.of(dir)?.brief?.let { if (publish != null) it + "\n" + PublishToolServer.BRIEF else it }
                    listOfNotNull(workspace, device?.let { DeviceToolServer.BRIEF }).joinToString("\n").ifEmpty { null }
                },
                folderKnown = folderKnown,
            )
        chats.update { it + (key to chat) }
        watchers[key] =
            scope.launch {
                // The mode the user picks outlives the app. A copy still on its source's id
                // ([ClaudeChatState.forking]) is not that session, and the settings chat is not a session to reopen.
                if (kind == ChatKind.CODE) {
                    launch {
                        chat.state
                            .map { Triple(it.sessionId, it.permissionMode, it.forking) }
                            .distinctUntilChanged()
                            .collect { (sessionId, mode, forking) -> if (sessionId != null && !forking) rememberMode(sessionId, mode) }
                    }
                }
                // The list only shows these; streamed text must not rebuild it.
                launch {
                    chat.state
                        .map { listOf(it.sessionId, it.title, it.status, it.unseenFinish, it.lastTurnFailed) to it.items.firstPromptLine() }
                        .distinctUntilChanged()
                        .collect {
                            if (chats.value[key] === chat) rebuildRows()
                            recountNeedsYou()
                        }
                }
                var last: ChatStatus? = null
                chat.state
                    .map { it.status }
                    .distinctUntilChanged()
                    .collect { status ->
                        val wasRunning = last == ChatStatus.WORKING || last == ChatStatus.NEEDS_APPROVAL
                        when {
                            status == ChatStatus.NEEDS_APPROVAL -> if (chat.state.value.isAsking) ChatAttention.Kind.ASKS else ChatAttention.Kind.NEEDS_APPROVAL
                            wasRunning && (status == ChatStatus.READY || status == ChatStatus.ENDED) -> ChatAttention.Kind.FINISHED
                            else -> null
                        }?.let { _attention.tryEmit(ChatAttention(chat.key, titleOf(chat.state.value), it, chat.backend.shortName)) }
                        last = status
                        recountNeedsYou()
                    }
            }
        return chat
    }

    /**
     * Whose device a chat uses: its workstream's, so the workstream's chats share one emulator copy
     * and other workstreams get their own; a chat outside a workstream has its own.
     */
    fun deviceOwner(
        key: String,
        cwd: String?,
    ): String = workspaceOf(cwd)?.id ?: key

    /** What the device picker calls [deviceOwner]: the workstream's title, else the chat's. */
    fun deviceOwnerLabel(
        key: String,
        cwd: String?,
    ): String? =
        workspaceOf(cwd)?.title ?: chats.value[key]
            ?.state
            ?.value
            ?.let(::titleOf)

    /** The publish tool of the chat [key] working in [cwd]: its requests are saved with the workstream, and the user is told. */
    internal fun publishTool(
        key: String,
        cwd: File,
    ) = PublishToolServer(
        folder = cwd,
        workspaceOf = { dir -> workspaceOf(dir.path) ?: agentWorkspaces.of(dir) },
        status = { agentWorkspaces.status(it) },
        request = { workspace, repo, request ->
            recordPublishRequest(workspace.id, repo.name, request)
            val chat = chats.value[key]
            _attention.tryEmit(ChatAttention(key, chat?.state?.value?.let(::titleOf) ?: workspace.title, ChatAttention.Kind.REQUESTS_PUBLISH, chat?.backend?.shortName ?: "Claude"))
        },
    )

    private fun recountNeedsYou() {
        _needsYou.value = chats.value.values.count { it.state.value.needsUser }
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
        // An open chat knows whether its session exists yet, and renames a live process itself.
        if (chats.value[row.key] != null) return renameChat(row.key, clean)
        val sessionId = row.sessionId ?: return
        withContext(Dispatchers.IO) { backendOf(sessionId).rename(sessionId, clean) }
        refresh()
    }

    /**
     * The copies [askAside] made, never listed: an agent that can't run one unsaved (Codex) saves
     * it from its first message until it is deleted, minutes later for a long answer. Kept after the delete, as a refresh
     * that read the disk before it would otherwise list the copy again until the next one.
     */
    private val asideCopies: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Asks [key]'s agent [prompt] on a copy of its session, so the answer knows the conversation,
     * and gives the reply's text, or null when the agent can't start or says nothing. The copy
     * has no tools, runs read-only and is refused every approval. Claude Code never saves it; an
     * agent that must save it has it deleted afterwards, and the list never shows it, even while
     * it runs ([asideCopies]). Cancelling stops the agent; the copy is deleted all the same.
     */
    suspend fun askAside(
        key: String,
        prompt: String,
        timeoutMs: Long = ASIDE_TIMEOUT_MS,
    ): String? {
        val chat = chats.value[key] ?: chat(key) ?: return null
        val state = chat.state.value
        val agent = chat.backend
        val options =
            AgentStartOptions(
                cwd = File(state.cwd),
                resumeSessionId = state.sessionId,
                // A copy: the session itself stays as it is, even while a turn runs in it.
                forkSession = state.sessionId != null,
                model = state.modelChoice,
                permissionMode = if (PermissionMode.PLAN in agent.capabilities.modes) PermissionMode.PLAN else PermissionMode.DEFAULT,
                onlyHostTools = true,
                // Claude Code then writes no copy at all; Codex saves it, and it is deleted below.
                persist = false,
            )
        val connection =
            try {
                agent.start(options, scope)
            } catch (e: IOException) {
                Log.w(TAG, "Could not start ${agent.displayName} to ask aside", e)
                return null
            }
        var copyId: String? = null
        var answer: String? = null
        var ended: AgentEvent.TurnEnded? = null
        try {
            withTimeout(timeoutMs) {
                coroutineScope {
                    val reading =
                        launch {
                            connection.events
                                .takeWhile { event ->
                                    // Kept: how the turn ended says why there was no answer.
                                    if (event is AgentEvent.TurnEnded) ended = event
                                    event !is AgentEvent.TurnEnded
                                }.collect { event ->
                                    when (event) {
                                        is AgentEvent.SessionStarted ->
                                            if (event.sessionId != state.sessionId) {
                                                copyId = event.sessionId
                                                // Saved while the agent writes: a refresh meanwhile would list it.
                                                if (asideCopies.add(event.sessionId)) rebuildRows()
                                            }
                                        is AgentEvent.Reply ->
                                            event.content
                                                .filterIsInstance<ContentBlock.Text>()
                                                .joinToString("") { it.text }
                                                .takeIf { it.isNotBlank() }
                                                ?.let { answer = it }
                                        is AgentEvent.ApprovalRequested -> connection.respond(event.request, PermissionDecision.Deny(ASIDE_NO_TOOLS))
                                        else -> {}
                                    }
                                }
                        }
                    connection.send(prompt)
                    reading.join()
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${agent.displayName} took too long to answer aside", e)
        } catch (e: IOException) {
            Log.w(TAG, "${agent.displayName} stopped while answering aside", e)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                connection.kill()
                // An unsaved copy has nothing to delete.
                copyId?.let { id -> if (agent.session(id) != null && !agent.delete(id)) Log.w(TAG) { "Could not delete the aside copy $id" } }
            }
        }
        if (answer == null) {
            val turn = ended
            Log.w(TAG) {
                when {
                    turn == null -> "${agent.displayName} stopped before answering aside"
                    turn.failure != null -> "${agent.displayName}'s aside turn failed: ${turn.failure}"
                    turn.interrupted -> "${agent.displayName}'s aside turn was interrupted"
                    else -> "${agent.displayName} ended its aside turn without any text"
                }
            }
        }
        return answer
    }

    /** Ends the session's chat here and deletes its file. */
    suspend fun delete(row: ClaudeSessionRow) {
        chats.value[row.key]?.let { chat ->
            // Waits for the process to exit: not on the main thread.
            withContext(Dispatchers.IO) { chat.kill() }
            watchers.remove(row.key)?.cancel()
            chats.update { it - row.key }
            // A chat outside a workstream owns its emulator copy: it goes with the chat.
            AndroidDevices.shared.releaseOwner(row.key)
            // Its own status watcher is gone: a chat deleted while waiting must leave the count.
            recountNeedsYou()
            rebuildRows()
        }
        val sessionId = row.sessionId
        if (sessionId != null) {
            withContext(Dispatchers.IO) { backendOf(sessionId).delete(sessionId) }
            rememberMode(sessionId, PermissionMode.DEFAULT)
            withContext(Dispatchers.IO) {
                if (sessionId in agentWorkspaces.preferences().pushScopes) {
                    _preferences.value = agentWorkspaces.updatePreferences { it.copy(pushScopes = it.pushScopes - sessionId) }
                }
            }
        }
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
        folderKnown: Boolean = true,
    ): ClaudeChat {
        val workspace = workspaces.prepare(checkout, target, branch)
        val chat = newChat(workspace.dir, permissionMode, title = title, agent = agent)
        if (prompt.isNotBlank()) chat.send(prompt)
        return chat
    }

    /**
     * The settings assistant: a chat whose only tools are [settings] (no files, no shell, none of
     * the user's own MCP servers), in a folder of its own. Reading a setting runs without asking;
     * every change waits for the user's approval. It stays one conversation: the chat open now, or
     * after a restart the newest session saved in [assistantDir], or else a new one.
     */
    suspend fun settingsAssistant(
        settings: SettingsToolServer,
        title: String,
        account: AccountToolServer? = null,
        access: OnlineAgentBackend.Access? = null,
        screen: ScreenToolServer? = null,
    ): ClaudeChat {
        bindAssistant(settings, account, access, screen)
        settingsAssistantKey?.let { key -> chats.value[key]?.let { return it } }
        // Finding the agent program reads the disk (each PATH folder): not on the caller's thread.
        val agent = withContext(Dispatchers.IO) { assistantAgent() }
        // Read here rather than from the last [refresh]: the assistant may open before the list ever has.
        val newest =
            withContext(Dispatchers.IO) {
                try {
                    agent.listSessions().firstOrNull { isAssistantFolder(it.cwd) }?.also { owners[it.sessionId] = agent }
                } catch (e: IOException) {
                    Log.w(TAG, "Could not list ${agent.displayName} sessions", e)
                    null
                }
            }
        val chat =
            newest?.let { info ->
                chats.value.values.firstOrNull { it.state.value.sessionId == info.sessionId }
                    ?: openAssistant(info.sessionId, info.sessionId, info.title, agent)
            } ?: openAssistant("settings-${UUID.randomUUID()}", null, title, agent)
        settingsAssistantKey = chat.key
        return chat
    }

    /**
     * Points every assistant chat at [settings] and [account]: the account and navigation of the
     * screen showing one now. Until a screen binds them, a tool call answers that Amethyst is out
     * of reach.
     */
    fun bindAssistant(
        settings: SettingsToolServer,
        account: AccountToolServer?,
        access: OnlineAgentBackend.Access? = null,
        screen: ScreenToolServer? = null,
    ) {
        settingsTools.target = settings
        accountTools.target = account
        screenTools.target = screen
        if (access != null) online.access = access
    }

    /** The open assistant chat's key, once [settingsAssistant] has opened it. */
    val assistantKey: String? get() = settingsAssistantKey

    /** Whether the chat [key] names is the assistant: open as one, or saved in its folder. */
    fun isAssistantChat(key: String): Boolean {
        chats.value[key]?.let { return it.state.value.kind == ChatKind.SETTINGS }
        chats.value.values
            .firstOrNull { it.state.value.sessionId == key }
            ?.let { return it.state.value.kind == ChatKind.SETTINGS }
        return saved.value.any { it.sessionId == key && isAssistantFolder(it.cwd) }
    }

    /** Whether [cwd] is the assistant's own folder, where only its sessions run. */
    fun isAssistantFolder(cwd: String?): Boolean = cwd != null && File(cwd).absoluteFile.normalize() == assistantDir.absoluteFile.normalize()

    /**
     * Who runs the assistant: an agent program on this computer that takes the app's tools (Claude
     * Code), else the user's own AI key, else Claude Code all the same (the chat then says it isn't installed).
     */
    private fun assistantAgent(): AgentBackend =
        backends.firstOrNull { it.capabilities.hostTools && it.executable() != null }
            ?: online.takeIf { it.isAvailable() }
            ?: backends.firstOrNull { it.capabilities.hostTools }
            ?: backend

    private fun openAssistant(
        key: String,
        sessionId: String?,
        title: String?,
        /** Who runs it; null: the agent that saved [sessionId]. */
        chosen: AgentBackend? = null,
    ): ClaudeChat {
        if (!assistantDir.isDirectory && !assistantDir.mkdirs()) Log.w(TAG) { "Could not create $assistantDir" }
        return open(
            key,
            sessionId,
            assistantDir,
            title,
            PermissionMode.DEFAULT,
            null,
            kind = ChatKind.SETTINGS,
            mcpServers = listOf(settingsTools, accountTools, screenTools),
            agent = chosen ?: sessionId?.let { backendOf(it) }?.takeIf { it.capabilities.hostTools } ?: assistantAgent(),
            launchOptions = { options ->
                options.copy(
                    onlyHostTools = true,
                    preApprovedTools =
                        listOf(SettingsToolServer.SEARCH, SettingsToolServer.GET, SettingsToolServer.OPEN).map(settingsTools::qualifiedName) +
                            AccountToolServer.READ_ONLY_TOOLS.map(accountTools::qualifiedName) +
                            ScreenToolServer.READ_ONLY_TOOLS.map(screenTools::qualifiedName),
                    instructions = SettingsToolServer.SYSTEM_PROMPT,
                )
            },
        )
    }

    /** The tools of every assistant chat, bound to the screen that shows one ([bindAssistant]). */
    private val settingsTools = BoundToolServer(SettingsToolServer.NAME, SettingsToolServer.TOOLS)
    private val accountTools = BoundToolServer(AccountToolServer.NAME, AccountToolServer.TOOLS, AccountToolServer.ALWAYS_ASK)
    private val screenTools = BoundToolServer(ScreenToolServer.NAME, ScreenToolServer.TOOLS, ScreenToolServer.ALWAYS_ASK)

    @Volatile
    private var settingsAssistantKey: String? = null

    private val gates = ConcurrentHashMap<String, BuildGate>()

    /**
     * The build gate of the checkout whose top folder is [root]: one per checkout, whichever screen
     * asks, so a run goes on when its screen closes and its result counts for every way of publishing.
     */
    fun buildGateAt(root: File): BuildGate {
        // By the real path: git names a checkout behind a symlink (macOS's /var) by where it leads.
        val path =
            try {
                root.canonicalPath
            } catch (_: IOException) {
                root.absoluteFile.normalize().path
            }
        return gates.computeIfAbsent(path) { BuildGate(File(it), scope) }
    }

    /** The build gate of the checkout [dir] is in, anywhere inside it. */
    suspend fun buildGate(dir: File): BuildGate {
        val root = withContext(Dispatchers.IO) { GitWorkspace.git(dir, "rev-parse", "--show-toplevel") }?.trim()?.takeIf { it.isNotEmpty() }
        return buildGateAt(root?.let(::File) ?: dir)
    }

    /** Stops every process this app started. */
    fun shutdown() {
        chats.value.values.forEach { it.kill() }
        gates.values.forEach { it.cancel() }
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
        val appArchived = preferences.value.archivedSessions
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
            saved.value.filter { it.sessionId !in asideCopies }.map { info ->
                val chat = openBySession[info.sessionId]
                val workspace = workspaceOf(info.cwd)
                // Its clones are gone for now: no size to show; the next message brings them back.
                val freed = workspace?.isFreed == true
                ClaudeSessionRow(
                    key = chat?.key ?: info.sessionId,
                    sessionId = info.sessionId,
                    title = chat?.state?.value?.title ?: info.title,
                    lastPrompt = info.lastPrompt,
                    cwd = info.cwd,
                    gitBranch = info.gitBranch,
                    lastModified = info.lastModified,
                    appStatus = chat?.state?.value?.status,
                    unseenFinish = chat?.state?.value?.unseenFinish == true,
                    lastTurnFailed = chat?.state?.value?.lastTurnFailed == true,
                    asking = chat?.state?.value?.isAsking == true,
                    openElsewhere = live[info.sessionId]?.takeIf { it.pid !in ownPids },
                    project = projectOf(info.cwd),
                    archived = info.cwd in gone || info.archived || info.sessionId in appArchived,
                    userArchived = info.archived || info.sessionId in appArchived,
                    workspace = workspace,
                    workspaceSize = workspace?.takeIf { !freed }?.let { workspaceSizes[it.id]?.first },
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
                        unseenFinish = state.unseenFinish,
                        lastTurnFailed = state.lastTurnFailed,
                        asking = state.isAsking,
                        openElsewhere = null,
                        project = projectOf(state.cwd),
                        workspace = workspaceOf(state.cwd),
                        workspaceSize = workspaceOf(state.cwd)?.let { workspaceSizes[it.id]?.first },
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
                        workspaceSize = workspaceSizes[ws.id]?.first,
                        agentId = agent.id,
                        agentName = agent.displayName,
                    )
                }
        _rows.value = (unsaved + fromDisk + idle).sortedByDescending { it.lastModified }
    }

    companion object {
        private const val TAG = "ClaudeCodeService"

        /** How long [askAside] waits for an answer. */
        private const val ASIDE_TIMEOUT_MS = 180_000L
        private const val ASIDE_NO_TOOLS = "No tools here: answer from the conversation and the message alone."

        /** The repositories of the last workspace, offered first next time. */
        const val LAST_GROUP = "Last used"
        private const val WORKTREES_SUFFIX = "-worktrees"
        private const val PR_CHECK_MS = 10 * 60_000L
        private const val SIZE_CHECK_MS = 10 * 60_000L
        private const val AUTO_CLEANUP_EVERY_MS = 60 * 60_000L

        /** How long after its last pull request closes a workspace is kept: time to look back. */
        const val CLEANUP_AFTER_MS = 24 * 60 * 60_000L

        /** The settings assistant's folder under the app's data: its sessions are the ones saved there. */
        const val ASSISTANT_FOLDER = "claude-settings-assistant"

        /** Where the assistant's sessions on the user's own AI key are saved, under the app's data. */
        const val ONLINE_SESSIONS_FOLDER = "assistant-sessions"

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
