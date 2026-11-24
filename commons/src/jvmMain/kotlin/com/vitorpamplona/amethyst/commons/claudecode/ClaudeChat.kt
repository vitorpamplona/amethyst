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
import com.vitorpamplona.agents.AgentConnection
import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.AgentInfo
import com.vitorpamplona.agents.AgentNotInstalledException
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.ModelChoice
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.SlashCommand
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class ChatStatus {
    /** A saved session shown from its file; no process yet. It starts on the first message. */
    NOT_STARTED,
    STARTING,

    /** A turn is running. */
    WORKING,

    /** A tool call waits for the user's answer. */
    NEEDS_APPROVAL,

    /** The process is up and waiting for the next message. */
    READY,

    /** The process ended; the next message starts it again on the same session. */
    ENDED,
}

/** Why a chat couldn't run, worded by the UI. */
@Immutable
sealed interface ChatError {
    /** No `claude` executable was found. */
    data object NotInstalled : ChatError

    data class StartFailed(
        val message: String?,
    ) : ChatError

    /** The process ended on its own with an error; [stderr] is its last output. */
    data class Exited(
        val code: Int?,
        val stderr: String,
    ) : ChatError

    /** The session's folder is gone (a removed worktree): it can be read, not continued. */
    data object FolderGone : ChatError

    /** Another Claude Code has the session open: a second process would mix its turns in. */
    data object HeldElsewhere : ChatError

    /** The workstream's repositories could not be cloned; [message] is git's. */
    data class CloneFailed(
        val message: String?,
    ) : ChatError
}

/**
 * The agent waits on the user: for an approval, or to read how a turn they didn't watch ended.
 * The list's rows and the drawer's badge both ask this, so they can't disagree.
 */
fun needsUser(
    status: ChatStatus?,
    unseenFinish: Boolean,
): Boolean = status == ChatStatus.NEEDS_APPROVAL || (unseenFinish && status != ChatStatus.WORKING && status != ChatStatus.STARTING)

/** What a chat is for: work on code in a folder, or the settings assistant (no files, only settings tools). */
enum class ChatKind { CODE, SETTINGS }

@Immutable
data class ClaudeChatState(
    val sessionId: String?,
    val cwd: String,
    val title: String?,
    val items: List<ChatItem> = emptyList(),
    /** The plan the agent keeps with its task tools, read back from [items]. */
    val tasks: List<AgentTask> = emptyList(),
    /** Text of the reply being written, before its message is complete. */
    val streamingText: String = "",
    val pendingPermissions: List<PermissionRequest> = emptyList(),
    val status: ChatStatus = ChatStatus.NOT_STARTED,
    val permissionMode: PermissionMode = PermissionMode.DEFAULT,
    val model: String? = null,
    /** What the turns run in this app have cost so far, as the CLI reports it. */
    val costUsd: Double? = null,
    val error: ChatError? = null,
    val historyLoaded: Boolean = false,
    /** What the CLI says it is doing in the running turn ("Writing hello.txt"). */
    val activity: String? = null,
    /** When the running turn started, for an elapsed-time label. */
    val turnStartedAt: Long? = null,
    /** The folder's git state, when it is a git checkout. */
    val git: GitWorkInfo? = null,
    /**
     * A copy of another session that hasn't started yet: [sessionId] is still the source's, and
     * the first message branches it into a session of its own.
     */
    val forking: Boolean = false,
    /** How much of the model's context the conversation fills, and how much it has, as the CLI last said. */
    val contextTokens: Long? = null,
    val contextWindow: Long? = null,
    /**
     * What a pending call to one of the chat's own tools would do, in words, by request id
     * ("Theme: SYSTEM → DARK"): the approval card shows it in place of the tool's input.
     */
    val permissionNotes: Map<String, String> = emptyMap(),
    val kind: ChatKind = ChatKind.CODE,
    /** The slash commands the CLI offers in this folder, once it said. */
    val commands: List<SlashCommand> = emptyList(),
    /** The models the user can pick, once the CLI said. */
    val models: List<ModelChoice> = emptyList(),
    /** The model the user picked ([ModelChoice.value]), or null for the CLI's default. */
    val modelChoice: String? = null,
    /** How many repositories a new workstream is still cloning; a message sent meanwhile waits for them. */
    val cloning: Int? = null,
    /** When the last turn run here ended: from then on the agent waits for the user. */
    val turnEndedAt: Long? = null,
    /** How long that turn ran, when it was timed. */
    val lastTurnMillis: Long? = null,
    /** That turn ended while no screen showed the chat: the user hasn't seen how it ended. */
    val unseenFinish: Boolean = false,
    /** That turn ended in an error (the agent's, or its process dying), not with an answer. */
    val lastTurnFailed: Boolean = false,
) {
    val isBusy: Boolean get() = status == ChatStatus.STARTING || status == ChatStatus.WORKING || status == ChatStatus.NEEDS_APPROVAL

    /** What the agent waits on is an answer to its questions, not an approval. */
    val isAsking: Boolean get() = pendingPermissions.firstOrNull()?.questions != null

    val needsUser: Boolean get() = needsUser(status, unseenFinish)
}

/**
 * One Claude Code conversation in the app: its history, and the CLI process that continues it.
 * Lives in [ClaudeCodeService], not in a screen, so a turn keeps running while the user reads
 * something else and the chat shows its progress when they come back.
 */
class ClaudeChat internal constructor(
    /** The chat's key in the service: the session id, or a temporary id for a new chat. */
    val key: String,
    resumeSessionId: String?,
    cwd: File,
    title: String?,
    permissionMode: PermissionMode,
    model: String?,
    /** The agent the chat talks to: its saved sessions, and how to start it. */
    val backend: AgentBackend,
    private val scope: CoroutineScope,
    private val onSessionChanged: () -> Unit,
    /** Reads the folder's git state; tests replace it. */
    private val inspectGit: suspend (File) -> GitWorkInfo? = { GitWorkspace.inspect(it) },
    /** The agent workstream [File] is in, if any: its pushes are guarded, to the branches made for it. */
    private val workstreamOf: (File) -> AgentWorkspace? = { null },
    /** The push scope each session first started with, kept across app restarts. */
    private val pushScopes: PushScopeStore = PushScopeStore.NONE,
    /** The push rules a guarded session starts with, for its repositories and its scope; tests replace it. */
    private val pushRulesFor: (repos: List<File>, scope: PushScope) -> PushGuard.PushRules = { repos, scope -> PushGuard.environment(repos = repos, scope = scope) },
    /** What to tell the agent about the folder it works in (a workstream's clones), if anything. */
    private val folderBrief: (File) -> String? = { null },
    /** Starts as a copy of [resumeSessionId]: its history, and a session of its own from the first message. */
    fork: Boolean = false,
    /** Whether another Claude Code process has this session open now. Called off the main thread. */
    private val heldElsewhere: (sessionId: String) -> Boolean = { false },
    /** Tools the app runs itself in this chat ([HostToolServer]), e.g. the settings assistant's. */
    private val mcpServers: List<HostToolServer> = emptyList(),
    /** Changes to how the agent starts for this chat: which tools it has, its instructions. */
    private val launchOptions: (AgentStartOptions) -> AgentStartOptions = { it },
    kind: ChatKind = ChatKind.CODE,
    /**
     * With [fork]: the copy is cut just before this message (its uuid), to ask it again
     * differently. The CLI cuts at the message's parent ([TranscriptEntry.parentUuid]).
     */
    private val forkBefore: TranscriptEntry? = null,
    /**
     * False when the session never said where it ran (a saved file with no cwd): [cwd] is then
     * only somewhere to show, and the chat refuses to start rather than resume there.
     */
    private val folderKnown: Boolean = true,
    /**
     * Runs before the agent starts: brings back the folder's clones when its workstream was freed,
     * telling the callback how many repositories it makes. A failure ends the chat with its words.
     */
    private val restoreFolder: suspend (File, (Int) -> Unit) -> Unit = { _, _ -> },
) {
    private val workDir = cwd

    /** Whether the agent may run in [workDir]: a folder the session named, and still there. */
    private fun canRunHere() = folderKnown && workDir.isDirectory

    /** Text for the composer when the chat first shows (a message to ask again); the screen takes it once. */
    @Volatile
    var draft: String? = null

    /** What `--model` starts the process with; [setModel] changes it, and the running process. */
    @Volatile
    private var model: String? = model
    private val transcript = ChatTranscript()
    private val lock = Mutex()

    /** How many screens show the chat now: a turn that ends while one does is seen. */
    private val viewers = AtomicInteger(0)

    /** The last message as it went out, images and all: what [retry] sends again. */
    private class SentPrompt(
        val text: String,
        val images: List<ImageInput>,
        val files: List<File?>,
    )

    @Volatile private var lastSent: SentPrompt? = null

    /** Sends on their way but not yet running as a turn: a second Try again meanwhile would send twice. */
    private val sendsInFlight = AtomicInteger(0)
    private var connection: AgentConnection? = null

    /** Until the CLI names the copy's own session, the process starts with `--fork-session`. */
    private var forkOnStart = fork && resumeSessionId != null

    /** Set by [kill]: a process that finishes starting afterwards is stopped at once. */
    @Volatile
    private var killed = false

    /** What the session may push ([PushGuard]), fixed at its first start and reused by every later one. */
    @Volatile
    private var pushScope: PushScope? = null

    /** Set while a new workstream's repositories clone ([waitFor]): the agent starts in the folder once they are in. */
    @Volatile
    private var preparation: Deferred<Unit>? = null

    /** Done once a resumed session's history is in: a prompt sent earlier waits, so it lands after it. */
    private val historyReady = CompletableDeferred<Unit>().apply { if (resumeSessionId == null) complete(Unit) }

    /** Streamed text, gathered here and shown every [STREAM_PUBLISH_MS] rather than on every delta. */
    private val streamBuffer = StringBuilder()
    private var streamPublishedAt = 0L

    /** The name a new chat was given, for the CLI's session once it exists. */
    @Volatile
    private var titleToApply: String? = if (resumeSessionId == null) title else null

    private val _state =
        MutableStateFlow(
            ClaudeChatState(
                sessionId = resumeSessionId,
                cwd = cwd.absolutePath,
                title = title,
                permissionMode = permissionMode,
                model = model,
                modelChoice = model,
                historyLoaded = resumeSessionId == null,
                forking = fork && resumeSessionId != null,
                kind = kind,
            ),
        )
    val state: StateFlow<ClaudeChatState> = _state.asStateFlow()

    init {
        refreshGit()
        if (resumeSessionId != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val history = backend.transcript(resumeSessionId).let { all -> if (forkBefore != null) all.cutBefore(forkBefore.uuid) else all }
                    val subagents = backend.subagentTranscripts(resumeSessionId)
                    lock.withLock {
                        transcript.addHistory(history)
                        transcript.addSubagentHistory(subagents)
                        publish { it.copy(historyLoaded = true) }
                    }
                } finally {
                    historyReady.complete(Unit)
                }
            }
        }
    }

    private val _reviewed = MutableStateFlow<Set<String>>(emptySet())

    /** Review comments on the linked proposal already sent to Claude (event ids). */
    val reviewed: StateFlow<Set<String>> = _reviewed.asStateFlow()

    /** Sends reviewers' comments on the proposal as the next message, once each. */
    fun addressReview(
        ids: Collection<String>,
        prompt: String,
    ) {
        _reviewed.update { it + ids }
        send(prompt)
        // Kept on the branch, so the next session (or the next start of the app) doesn't offer them again.
        val branch = _state.value.git?.branch ?: return
        scope.launch {
            try {
                GitWorkspace.markReviewed(workDir, branch, ids)
            } catch (e: IOException) {
                Log.w(TAG, "Could not remember the reviewed comments", e)
            }
        }
    }

    /**
     * A screen shows the chat until the returned function runs. What the agent finished before is
     * seen now; what it finishes meanwhile is seen as it happens.
     */
    fun viewing(): () -> Unit {
        viewers.incrementAndGet()
        _state.update { if (it.unseenFinish) it.copy(unseenFinish = false) else it }
        val released = AtomicBoolean(false)
        return { if (released.compareAndSet(false, true)) viewers.decrementAndGet() }
    }

    /**
     * Sends the user's last message again, for a turn that ended in an error: as it went out, images
     * included. False when it can't go out the same way (a saved session keeps no images): the
     * caller offers its text to send by hand. A retry while one is already on its way does nothing.
     */
    fun retry(): Boolean {
        if (_state.value.isBusy || sendsInFlight.get() > 0) return true
        lastSent?.let {
            send(it.text, it.images, it.files)
            return true
        }
        val last = _state.value.items.lastOrNull { it is ChatItem.UserText } as? ChatItem.UserText ?: return true
        if (last.images.isNotEmpty()) return false
        send(last.text)
        return true
    }

    /** The user hasn't seen how a turn ended unless a screen showed the chat as it did. */
    private fun unseen(): Boolean = viewers.get() == 0

    /**
     * Makes the chat wait for [cloning] before the agent starts: the workstream's [repositories] are
     * cloning into its folder. A failure ends the chat with git's words.
     */
    fun waitFor(
        cloning: Deferred<Unit>,
        repositories: Int,
    ) {
        preparation = cloning
        publish { it.copy(cloning = repositories) }
        scope.launch {
            try {
                cloning.await()
                publish { it.copy(cloning = null) }
                refreshGit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish { it.copy(cloning = null, status = ChatStatus.ENDED, error = ChatError.CloneFailed(e.message)) }
            }
        }
    }

    /** Re-reads the folder's git state: after a turn, and when the chat is shown. */
    fun refreshGit() {
        scope.launch {
            val info = inspectGit(workDir)
            _state.update { it.copy(git = info) }
            info?.reviewed?.let { saved -> _reviewed.update { it + saved } }
        }
    }

    /**
     * Sends a message, starting the process first if it isn't running. [images] go with it
     * (read with [ImageInput.fromFile]); [imageFiles] are where they came from, to show them.
     */
    fun send(
        text: String,
        images: List<ImageInput> = emptyList(),
        imageFiles: List<File?> = emptyList(),
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && images.isEmpty()) return
        val sent = images.mapIndexed { i, image -> SentImage(image.name, imageFiles.getOrNull(i)?.absolutePath) }
        lastSent = SentPrompt(trimmed, images, imageFiles)
        sendsInFlight.incrementAndGet()
        scope.launch {
            try {
                sendNow(trimmed, images, sent)
            } finally {
                sendsInFlight.decrementAndGet()
            }
        }
    }

    private suspend fun sendNow(
        trimmed: String,
        images: List<ImageInput>,
        sent: List<SentImage>,
    ) {
        historyReady.await()
        // Every way of sending lands here (the composer, review comments, questions from the
        // Changes screen), so this is where a session held elsewhere is kept read-only.
        val current = _state.value
        val id = current.sessionId
        if (id != null && !current.forking && connection == null && heldElsewhere(id)) {
            publish { it.copy(error = ChatError.HeldElsewhere) }
            return
        }
        lock.withLock {
            transcript.addPrompt(trimmed, sent)
            publish {
                it.copy(
                    status = ChatStatus.WORKING,
                    error = null,
                    activity = null,
                    turnStartedAt = it.turnStartedAt ?: System.currentTimeMillis(),
                    unseenFinish = false,
                    lastTurnFailed = false,
                )
            }
        }
        // A workstream still cloning: the prompt shows now, and goes out once its folder is ready.
        try {
            preparation?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Said here too: this prompt marked the chat as working after [waitFor] ended it.
            endTurnWithError(ChatError.CloneFailed(e.message)) { it.copy(cloning = null) }
            return
        }
        // A freed workstream: its clones come back first, shown as cloning.
        try {
            var restored = false
            restoreFolder(workDir) { repositories ->
                restored = true
                publish { it.copy(cloning = repositories) }
            }
            if (restored) {
                publish { it.copy(cloning = null) }
                refreshGit()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            endTurnWithError(ChatError.CloneFailed(e.message)) { it.copy(cloning = null) }
            return
        }
        val c = ensureStarted() ?: return
        try {
            c.send(trimmed, images)
        } catch (e: IOException) {
            Log.w(TAG, "Could not send to ${backend.displayName}", e)
            endTurnWithError(ChatError.StartFailed(e.message))
        }
    }

    fun approve(
        request: PermissionRequest,
        always: Boolean,
    ) = answer(request, PermissionDecision.Allow(updatedPermissions = if (always) request.suggestions else null))

    fun deny(request: PermissionRequest) = answer(request, PermissionDecision.Deny("The user declined this tool call."))

    /** Answers the agent's questions ([PermissionRequest.questions]). */
    fun answerQuestions(
        request: PermissionRequest,
        answers: PermissionDecision.Answer,
    ) = answer(request, answers)

    /** Closes the agent's questions unanswered; the agent goes on without them. */
    fun dismissQuestions(request: PermissionRequest) = answer(request, PermissionDecision.Deny("The user dismissed the questions without answering them."))

    private fun answer(
        request: PermissionRequest,
        decision: PermissionDecision,
    ) {
        scope.launch {
            val c = connection ?: return@launch
            publish { state ->
                val left = state.pendingPermissions.filter { it.requestId != request.requestId }
                state.copy(pendingPermissions = left, status = if (left.isEmpty()) ChatStatus.WORKING else ChatStatus.NEEDS_APPROVAL)
            }
            try {
                c.respond(request, decision)
            } catch (e: IOException) {
                Log.w(TAG, "Could not answer Claude Code", e)
            }
        }
    }

    /** Stops the running turn; the process stays up for the next message. */
    fun interrupt() {
        scope.launch {
            val c = connection ?: return@launch
            publish { it.copy(pendingPermissions = emptyList()) }
            c.interrupt()
        }
    }

    /**
     * Uses [value] (a [ModelChoice.value], null for the default) from the next message on: the
     * running process switches at once, and a new one starts with it.
     */
    fun setModel(value: String?) {
        model = value
        publish { it.copy(modelChoice = value) }
        scope.launch {
            val c = connection ?: return@launch
            try {
                c.setModel(value)
            } catch (e: IOException) {
                Log.w(TAG, "Could not switch the model", e)
            }
        }
    }

    /**
     * Learns the CLI's commands and models before the first message, so the composer can offer
     * them: from an earlier start in the same folder, or by starting the CLI briefly.
     */
    fun loadCliInfo() {
        if (_state.value.commands.isNotEmpty() || connection != null) return
        scope.launch(Dispatchers.IO) {
            val info = cliInfoCache[infoKey()] ?: probe() ?: return@launch
            remember(info)
            lock.withLock { publish { if (it.commands.isEmpty()) it.copy(commands = info.commands, models = info.models) else it } }
        }
    }

    private suspend fun probe(): AgentInfo? {
        if (!canRunHere() || killed) return null
        val options = AgentStartOptions(cwd = workDir, hostTools = mcpServers, probeOnly = true).let(launchOptions)
        val c =
            try {
                backend.start(options, scope)
            } catch (e: AgentNotInstalledException) {
                return null
            } catch (e: IOException) {
                Log.w(TAG, "Could not ask Claude Code for its commands", e)
                return null
            }
        // No message was sent, so no session is saved: closing stdin just ends it.
        return c.info.also { c.close() }
    }

    /** Each agent offers its own commands and models, per folder. */
    private fun infoKey() = backend.id + ":" + workDir.absolutePath

    private fun remember(info: AgentInfo) {
        cliInfoCache[infoKey()] = info
    }

    fun setPermissionMode(mode: PermissionMode) {
        publish { it.copy(permissionMode = mode) }
        scope.launch { connection?.setPermissionMode(mode) }
    }

    /** Renames through the running process when there is one, so the CLI keeps the title. */
    internal suspend fun renameLive(title: String): Boolean {
        val renamed = connection?.rename(title) ?: return false
        if (renamed) publish { it.copy(title = title) }
        return renamed
    }

    internal fun titleChanged(title: String) = publish { it.copy(title = title) }

    /** A name for a session that doesn't exist yet: shown now, given to the session when the CLI creates it. */
    internal fun nameBeforeStart(title: String) {
        titleToApply = title
        publish { it.copy(title = title) }
    }

    /** Ends the process after the running turn. */
    fun close() {
        scope.launch { connection?.close() }
    }

    /**
     * Ends the process now (app exit, session deleted), and one still starting as soon as it is
     * up. Blocks briefly while the process exits: call it off the main thread.
     */
    fun kill() {
        killed = true
        connection?.kill()
    }

    private suspend fun ensureStarted(): AgentConnection? =
        lock.withLock {
            connection?.let { if (it.isRunning) return@withLock it }
            if (killed) return@withLock null
            // A removed worktree, or a session that never named its folder: resuming elsewhere
            // (the home folder) would run the session there.
            if (!canRunHere()) {
                endTurnWithError(ChatError.FolderGone)
                return@withLock null
            }
            publish { it.copy(status = ChatStatus.STARTING) }
            val current = _state.value
            // A nostr checkout, or an agent workstream: its pushes go through the guard, merging is the user's.
            // A session once guarded stays so, with the scope it first started with, whatever the folder has become.
            val workstream = workstreamOf(workDir)
            val known = pushScope ?: current.sessionId?.let { id -> withContext(Dispatchers.IO) { pushScopes.load(id) } }
            val nostrCheckout = known != null || workstream != null || (current.git ?: inspectGit(workDir))?.repo != null
            val pushRules =
                if (nostrCheckout) {
                    withContext(Dispatchers.IO) {
                        val repos = repos(workDir)
                        val scope = PushGuard.scopeFor(known, current.sessionId, pushScopes) { PushGuard.capture(repos, workstream) }
                        pushScope = scope
                        pushRulesFor(repos, scope)
                    }
                } else {
                    null
                }
            val options =
                AgentStartOptions(
                    cwd = workDir,
                    resumeSessionId = current.sessionId,
                    forkSession = forkOnStart,
                    forkAt = if (forkOnStart) forkBefore?.parentUuid else null,
                    model = model,
                    permissionMode = current.permissionMode,
                    // As on Claude Code on the web: the agent pushes branches and opens pull requests when asked;
                    // merging, tags, deleting branches and moving the default branch stay the user's.
                    blockedCommands = if (nostrCheckout) NO_MERGING else emptyList(),
                    // The same, read finer and in every permission mode: gh api writes, flags anywhere, the guard's own config.
                    commandGuard = if (nostrCheckout) SessionCommandGuard else null,
                    // The SDK too, so a Gradle build the agent runs finds it as a terminal's would.
                    environment = AndroidSdk.environment() + pushRules?.environment.orEmpty(),
                    // The git the guard was checked with, ahead of an older one the agent's PATH may find first.
                    pathFirst = pushRules?.pathFirst.orEmpty(),
                    hostTools = mcpServers,
                    instructions = listOfNotNull(folderBrief(workDir), pushRules?.note).joinToString("\n\n").ifEmpty { null },
                ).let(launchOptions)
            val started =
                try {
                    backend.start(options, scope)
                } catch (e: AgentNotInstalledException) {
                    endTurnWithError(ChatError.NotInstalled)
                    return@withLock null
                } catch (e: IOException) {
                    Log.w(TAG, "Could not start Claude Code", e)
                    endTurnWithError(ChatError.StartFailed(e.message))
                    return@withLock null
                }
            if (killed) {
                started.kill()
                return@withLock null
            }
            connection = started
            val info = started.info
            if (info != null) remember(info)
            publish { it.copy(status = ChatStatus.WORKING, commands = info?.commands ?: it.commands, models = info?.models ?: it.models) }
            scope.launch { collect(started) }
            started
        }

    private suspend fun collect(c: AgentConnection) {
        c.events.collect { event -> lock.withLock { onEvent(event) } }
        // stdout closed: the process ended.
        val code = c.close()
        lock.withLock {
            // A newer process already took over (the user sent a message as this one died): its
            // turn is not this one's to end.
            if (connection !== c) return@withLock
            connection = null
            val wasBusy = _state.value.isBusy
            val error = if (code != null && code != 0 && wasBusy) ChatError.Exited(code, c.stderrTail().takeLast(STDERR_SHOWN)) else null
            if (wasBusy) {
                // A turn the process's end cut short is over too, and failed: the user has to look.
                transcript.addNotice(NoticeKind.PROCESS_ENDED)
                finishTurn(failed = true) { it.copy(status = ChatStatus.ENDED, error = error ?: it.error) }
            } else {
                publish { it.copy(status = ChatStatus.ENDED, pendingPermissions = emptyList(), streamingText = "", activity = null, turnStartedAt = null) }
            }
        }
        onSessionChanged()
    }

    private fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.SessionStarted -> {
                val isNewSession = _state.value.sessionId != event.sessionId
                // The copy has its own session now: later starts resume it, not the source.
                if (isNewSession) forkOnStart = false
                // The new session (or the copy) keeps the push scope it started with, for its later starts.
                if (isNewSession) pushScope?.let { kept -> scope.launch(Dispatchers.IO) { pushScopes.save(event.sessionId, kept) } }
                publish {
                    // A turn the agent started by itself (a background subagent reported back).
                    val ownTurn = it.status == ChatStatus.READY
                    it.copy(
                        status = if (ownTurn) ChatStatus.WORKING else it.status,
                        turnStartedAt = if (ownTurn) System.currentTimeMillis() else it.turnStartedAt,
                        forking = it.forking && !isNewSession,
                        sessionId = event.sessionId,
                        model = event.model ?: it.model,
                        permissionMode = event.permissionMode ?: it.permissionMode,
                    )
                }
                if (isNewSession) onSessionChanged()
                titleToApply?.let { name ->
                    titleToApply = null
                    scope.launch { if (!renameLive(name)) Log.w(TAG) { "Could not name the session $name" } }
                }
            }
            is AgentEvent.Reply -> {
                transcript.addAssistantBlocks(event.content)
                streamBuffer.setLength(0)
                val used = event.contextTokens
                publish { it.copy(streamingText = "", contextTokens = used ?: it.contextTokens) }
            }
            is AgentEvent.ToolResults -> {
                transcript.addUserBlocks(event.content, typedByUser = false)
                publish { it }
            }
            is AgentEvent.Subagent -> {
                // A subagent's own messages go inside the call that started it.
                if (event.fromAgent) transcript.addSubagentAssistant(event.toolUseId, event.content) else transcript.addSubagentUser(event.toolUseId, event.content)
                publish { it }
            }
            AgentEvent.ReplyStarted -> {
                streamBuffer.setLength(0)
                publish { it.copy(streamingText = "") }
            }
            is AgentEvent.ReplyDelta -> {
                // Many small deltas a second: each would re-lay the whole reply out.
                streamBuffer.append(event.text)
                val now = System.currentTimeMillis()
                if (now - streamPublishedAt >= STREAM_PUBLISH_MS) {
                    streamPublishedAt = now
                    val text = streamBuffer.toString()
                    publish { it.copy(streamingText = text) }
                }
            }
            is AgentEvent.ApprovalRequested -> {
                val permission = event.request
                // A call to one of this chat's own tools: say what it would do, from the tool itself.
                val note =
                    mcpServers.firstNotNullOfOrNull { server ->
                        val prefix = server.qualifiedName("")
                        if (permission.toolName.startsWith(prefix)) server.describe(permission.toolName.removePrefix(prefix), permission.input) else null
                    }
                publish {
                    it.copy(
                        pendingPermissions = it.pendingPermissions + permission,
                        status = ChatStatus.NEEDS_APPROVAL,
                        permissionNotes = if (note != null) it.permissionNotes + (permission.requestId to note) else it.permissionNotes,
                    )
                }
            }
            is AgentEvent.ApprovalWithdrawn -> {
                publish { state ->
                    val left = state.pendingPermissions.filter { it.requestId != event.requestId }
                    state.copy(pendingPermissions = left, status = if (left.isEmpty() && state.status == ChatStatus.NEEDS_APPROVAL) ChatStatus.WORKING else state.status)
                }
            }
            is AgentEvent.Activity -> publish { it.copy(activity = event.text) }
            is AgentEvent.ContextUsage -> publish { it.copy(contextTokens = event.tokens ?: it.contextTokens, contextWindow = event.window ?: it.contextWindow) }
            is AgentEvent.PlanUpdated -> {
                transcript.setPlan(event.steps)
                publish { it }
            }
            is AgentEvent.SubagentRunning -> {
                transcript.setSubagentRunning(event.toolUseId, event.running)
                publish { it }
            }
            is AgentEvent.TurnEnded -> {
                when {
                    event.interrupted -> transcript.addNotice(NoticeKind.INTERRUPTED)
                    event.failure != null -> transcript.addNotice(NoticeKind.TURN_FAILED, event.failure)
                }
                // A turn the user stopped needs no telling.
                finishTurn(failed = event.failure != null, tellUser = !event.interrupted) {
                    it.copy(status = ChatStatus.READY, costUsd = event.costUsd ?: it.costUsd, contextWindow = event.contextWindow ?: it.contextWindow)
                }
                refreshGit()
                onSessionChanged()
            }
        }
    }

    /**
     * Ends the running turn, however it ended: an answer, an error from the agent, the process
     * dying, or a start that never happened. Times it, says whether it [failed], and marks it for
     * the user when no screen showed it end ([tellUser]).
     */
    private fun finishTurn(
        failed: Boolean,
        tellUser: Boolean = true,
        change: (ClaudeChatState) -> ClaudeChatState,
    ) {
        val now = System.currentTimeMillis()
        publish {
            val started = it.turnStartedAt
            change(it).copy(
                activity = null,
                streamingText = "",
                pendingPermissions = emptyList(),
                turnStartedAt = null,
                turnEndedAt = now,
                lastTurnMillis = started?.let { start -> now - start },
                unseenFinish = tellUser && unseen(),
                lastTurnFailed = failed,
            )
        }
        // Opened as the turn ended: [viewing] may have cleared the flag before this set it.
        if (!unseen()) _state.update { if (it.unseenFinish) it.copy(unseenFinish = false) else it }
    }

    /** A turn that ended before the agent could answer: it couldn't start, a clone failed, the message didn't go out. */
    private fun endTurnWithError(
        error: ChatError,
        change: (ClaudeChatState) -> ClaudeChatState = { it },
    ) = finishTurn(failed = true) { change(it).copy(status = ChatStatus.ENDED, error = error) }

    /** Applies [change] and refreshes the rows from the transcript. */
    private fun publish(change: (ClaudeChatState) -> ClaudeChatState) {
        _state.update { change(it).copy(items = transcript.snapshot(), tasks = transcript.tasks()) }
    }

    companion object {
        private const val TAG = "ClaudeChat"

        /** What the CLI offered in each folder: commands and models change rarely, and asking starts a process. */
        private val cliInfoCache = ConcurrentHashMap<String, AgentInfo>()

        /** The history before the message [uuid], or all of it when it isn't there. */
        internal fun List<TranscriptEntry>.cutBefore(uuid: String): List<TranscriptEntry> {
            val at = indexOfFirst { it.uuid == uuid }
            return if (at < 0) this else subList(0, at)
        }

        private const val STREAM_PUBLISH_MS = 50L

        /**
         * What a session on a nostr repository or in a workstream may not run (command prefixes):
         * merging a pull request or proposal, marking or closing one, publishing the repository's
         * announcement or state (its branches and tags, signed), releases (tags), moving the
         * default branch outside git push, and skipping the push guard. Pushing a branch and
         * opening a pull request or proposal are allowed, as on Claude Code on the web.
         *
         * Prefixes catch a slip, not an agent set on getting around them: a reordered
         * `gh api … -X PUT …/merge` gets past them, which [SessionCommandGuard] catches; a script
         * the agent wrote gets past both.
         */
        val NO_MERGING =
            listOf(
                "gh pr merge",
                "gh pr review",
                "gh pr close",
                "gh release",
                "gh repo sync",
                "gh repo edit",
                "gh repo delete",
                "gh repo rename",
                "gh repo archive",
                "gh api -X PUT",
                "gh api -XPUT",
                "gh api --method PUT",
                "git push --no-verify",
                "ngit pr merge",
                "ngit merge",
                "ngit pr close",
                "ngit pr applied",
                "ngit sync",
                "ngit init",
                "ngit repo",
                "ngit release",
                "ngit account export",
                "amy git init",
                "amy git announce",
                "amy git state",
                "amy git merge",
                "amy git applied",
                "amy git merged",
                "amy git resolved",
                "amy git close",
            )
        private const val STDERR_SHOWN = 2_000

        /**
         * Git config, through the environment, that sends the session's pushes to a transport
         * that doesn't exist: an empty `pushInsteadOf` prefix matches every URL, and one per
         * URL in [remoteUrls] outranks the user's own rewrites (git takes the longest prefix, so
         * `url.git@github.com:.pushInsteadOf https://github.com/` would otherwise win). Unlike
         * [NO_MERGING]'s command rules, `git -C . push`, `/usr/bin/git push`, a script or
         * `--no-verify` don't get around it. The app's own push runs in its own process and is
         * untouched. Fetching still works.
         *
         * It guards against a push by habit or by mistake, not against an agent set on pushing:
         * git config the session writes itself wins (a `remote.<name>.pushurl` skips
         * `pushInsteadOf`, a local rewrite as long as ours ties and is read first), as does a
         * token passed on the command line. Only the permission prompts stand in the way of that.
         *
         * Added after any config [parent] already passes this way (a proxy's URL rewrites,
         * credential settings), which a fresh count would drop.
         */
        fun noPushEnvironment(
            parent: Map<String, String> = System.getenv(),
            remoteUrls: Collection<String> = emptyList(),
        ): Map<String, String> {
            val n = parent["GIT_CONFIG_COUNT"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val prefixes = listOf("") + remoteUrls.filter { it.isNotBlank() }.distinct()
            return buildMap {
                put("GIT_CONFIG_COUNT", (n + prefixes.size).toString())
                prefixes.forEachIndexed { i, prefix ->
                    put("GIT_CONFIG_KEY_${n + i}", "url.$NO_PUSH_URL.pushInsteadOf")
                    put("GIT_CONFIG_VALUE_${n + i}", prefix)
                }
            }
        }

        private const val NO_PUSH_URL = "publishing-is-the-users-step-in-amethyst://"

        /** The repository in [dir], or the clones one folder down (a workspace's). */
        fun repos(dir: File): List<File> = (listOf(dir) + dir.listFiles().orEmpty().filter { it.isDirectory }).filter { File(it, ".git").exists() }

        /** The remote URLs of [repos] of [dir], push URLs included, as [noPushEnvironment] wants them. */
        fun remoteUrls(dir: File): List<String> = repos(dir).flatMap { GitWorkspace.remoteUrls(it) }.distinct()
    }
}
