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
import com.vitorpamplona.claudecode.mcp.SdkMcpServer
import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.process.ClaudeCodeNotFoundException
import com.vitorpamplona.claudecode.process.ClaudeCodeOptions
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.PermissionDecision
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.protocol.PermissionRequest
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

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
}

/** What a chat is for: work on code in a folder, or the settings assistant (no files, only settings tools). */
enum class ChatKind { CODE, SETTINGS }

@Immutable
data class ClaudeChatState(
    val sessionId: String?,
    val cwd: String,
    val title: String?,
    val items: List<ChatItem> = emptyList(),
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
) {
    val isBusy: Boolean get() = status == ChatStatus.STARTING || status == ChatStatus.WORKING || status == ChatStatus.NEEDS_APPROVAL
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
    private val model: String?,
    private val store: ClaudeSessionStore,
    private val launcher: ClaudeCodeStarter,
    private val scope: CoroutineScope,
    private val onSessionChanged: () -> Unit,
    /** Reads the folder's git state; tests replace it. */
    private val inspectGit: suspend (File) -> GitWorkInfo? = { GitWorkspace.inspect(it) },
    /** Starts as a copy of [resumeSessionId]: its history, and a session of its own from the first message. */
    fork: Boolean = false,
    /** Whether another Claude Code process has this session open now. Called off the main thread. */
    private val heldElsewhere: (sessionId: String) -> Boolean = { false },
    /** Tools the app runs itself in this chat ([SdkMcpServer]), e.g. the settings assistant's. */
    private val mcpServers: List<SdkMcpServer> = emptyList(),
    /** Changes to how the CLI starts for this chat: which tools it has, its system prompt. */
    private val launchOptions: (ClaudeCodeOptions) -> ClaudeCodeOptions = { it },
    kind: ChatKind = ChatKind.CODE,
) {
    private val workDir = cwd
    private val transcript = ChatTranscript()
    private val lock = Mutex()
    private var connection: ClaudeCodeConnection? = null

    /** Until the CLI names the copy's own session, the process starts with `--fork-session`. */
    private var forkOnStart = fork && resumeSessionId != null

    /** Set by [kill]: a process that finishes starting afterwards is stopped at once. */
    @Volatile
    private var killed = false

    /** Done once a resumed session's history is in: a prompt sent earlier waits, so it lands after it. */
    private val historyReady = CompletableDeferred<Unit>().apply { if (resumeSessionId == null) complete(Unit) }

    /** Streamed text, gathered here and shown every [STREAM_PUBLISH_MS] rather than on every delta. */
    private val streamBuffer = StringBuilder()
    private var streamPublishedAt = 0L

    /** The name a new chat was given, for the CLI's session once it exists. */
    private var titleToApply: String? = if (resumeSessionId == null) title else null

    private val _state =
        MutableStateFlow(
            ClaudeChatState(
                sessionId = resumeSessionId,
                cwd = cwd.absolutePath,
                title = title,
                permissionMode = permissionMode,
                model = model,
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
                    val history = store.transcript(resumeSessionId)
                    lock.withLock {
                        transcript.addHistory(history)
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

    /** Re-reads the folder's git state: after a turn, and when the chat is shown. */
    fun refreshGit() {
        scope.launch {
            val info = inspectGit(workDir)
            _state.update { it.copy(git = info) }
            info?.reviewed?.let { saved -> _reviewed.update { it + saved } }
        }
    }

    /** Sends a message, starting the process first if it isn't running. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            historyReady.await()
            // Every way of sending lands here (the composer, review comments, questions from the
            // Changes screen), so this is where a session held elsewhere is kept read-only.
            val current = _state.value
            val id = current.sessionId
            if (id != null && !current.forking && connection == null && heldElsewhere(id)) {
                publish { it.copy(error = ChatError.HeldElsewhere) }
                return@launch
            }
            lock.withLock {
                transcript.addPrompt(trimmed)
                publish { it.copy(status = ChatStatus.WORKING, error = null, activity = null, turnStartedAt = it.turnStartedAt ?: System.currentTimeMillis()) }
            }
            val c = ensureStarted() ?: return@launch
            try {
                c.send(trimmed)
            } catch (e: IOException) {
                Log.w(TAG, "Could not send to Claude Code", e)
                publish { it.copy(status = ChatStatus.ENDED, error = ChatError.StartFailed(e.message)) }
            }
        }
    }

    fun approve(
        request: PermissionRequest,
        always: Boolean,
    ) = answer(request, PermissionDecision.Allow(updatedPermissions = if (always) request.suggestions else null))

    fun deny(request: PermissionRequest) = answer(request, PermissionDecision.Deny("The user declined this tool call."))

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

    fun setPermissionMode(mode: PermissionMode) {
        publish { it.copy(permissionMode = mode) }
        scope.launch { connection?.setPermissionMode(mode) }
    }

    /** Renames through the running process when there is one, so the CLI keeps the title. */
    internal suspend fun renameLive(title: String): Boolean {
        val answer = connection?.rename(title) ?: return false
        if (answer.isSuccess) publish { it.copy(title = title) }
        return answer.isSuccess
    }

    internal fun titleChanged(title: String) = publish { it.copy(title = title) }

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

    private suspend fun ensureStarted(): ClaudeCodeConnection? =
        lock.withLock {
            connection?.let { if (it.reader.isActive) return@withLock it }
            if (killed) return@withLock null
            // A removed worktree: resuming elsewhere (the home folder) would run the session there.
            if (!workDir.isDirectory) {
                publish { it.copy(status = ChatStatus.ENDED, error = ChatError.FolderGone) }
                return@withLock null
            }
            publish { it.copy(status = ChatStatus.STARTING) }
            val current = _state.value
            val nostrCheckout = (current.git ?: inspectGit(workDir))?.repo != null
            val options =
                ClaudeCodeOptions(
                    cwd = workDir,
                    resumeSessionId = current.sessionId,
                    forkSession = forkOnStart,
                    model = model,
                    permissionMode = current.permissionMode,
                    // A NIP-34 checkout: publishing is the user's step (CONTRIBUTING-WITH-AI.md), never the agent's.
                    disallowedTools = if (nostrCheckout) NO_PUBLISHING else emptyList(),
                    environment = if (nostrCheckout) noPushEnvironment() else emptyMap(),
                    sdkMcpServers = mcpServers,
                ).let(launchOptions)
            val started =
                try {
                    launcher.start(options, scope)
                } catch (e: ClaudeCodeNotFoundException) {
                    publish { it.copy(status = ChatStatus.ENDED, error = ChatError.NotInstalled) }
                    return@withLock null
                } catch (e: IOException) {
                    Log.w(TAG, "Could not start Claude Code", e)
                    publish { it.copy(status = ChatStatus.ENDED, error = ChatError.StartFailed(e.message)) }
                    return@withLock null
                }
            if (killed) {
                started.kill()
                return@withLock null
            }
            connection = started
            publish { it.copy(status = ChatStatus.WORKING) }
            scope.launch { collect(started) }
            started
        }

    private suspend fun collect(c: ClaudeCodeConnection) {
        c.messages.collect { message -> lock.withLock { onMessage(message) } }
        // stdout closed: the process ended.
        val code = c.close()
        lock.withLock {
            // A newer process already took over (the user sent a message as this one died): its
            // turn is not this one's to end.
            if (connection !== c) return@withLock
            connection = null
            val wasBusy = _state.value.isBusy
            val error = if (code != null && code != 0 && wasBusy) ChatError.Exited(code, c.stderrTail().takeLast(STDERR_SHOWN)) else null
            if (wasBusy) transcript.addNotice(NoticeKind.PROCESS_ENDED)
            publish { it.copy(status = ChatStatus.ENDED, pendingPermissions = emptyList(), streamingText = "", activity = null, turnStartedAt = null, error = error ?: it.error) }
        }
        onSessionChanged()
    }

    private fun onMessage(message: ClaudeMessage) {
        when (message) {
            is ClaudeMessage.Init -> {
                val isNewSession = _state.value.sessionId != message.sessionId
                // The copy has its own session now: later starts resume it, not the source.
                if (isNewSession) forkOnStart = false
                publish {
                    it.copy(
                        forking = it.forking && !isNewSession,
                        sessionId = message.sessionId,
                        model = message.model ?: it.model,
                        permissionMode = message.permissionMode ?: it.permissionMode,
                    )
                }
                if (isNewSession) onSessionChanged()
                titleToApply?.let { name ->
                    titleToApply = null
                    scope.launch { if (!renameLive(name)) Log.w(TAG) { "Could not name the session $name" } }
                }
            }
            is ClaudeMessage.Assistant -> {
                // A subagent's own messages stay inside its tool call.
                if (message.parentToolUseId != null) return
                transcript.addAssistantBlocks(message.content)
                streamBuffer.setLength(0)
                val used = message.contextTokens
                publish { it.copy(streamingText = "", contextTokens = used ?: it.contextTokens) }
            }
            is ClaudeMessage.User -> {
                if (message.parentToolUseId != null) return
                transcript.addUserBlocks(message.content, typedByUser = false)
                publish { it }
            }
            is ClaudeMessage.StreamEvent -> {
                if (message.parentToolUseId != null) return
                when (message.eventType) {
                    "message_start", "content_block_start" -> {
                        streamBuffer.setLength(0)
                        publish { it.copy(streamingText = "") }
                    }
                    else ->
                        message.textDelta?.let { delta ->
                            // Many small deltas a second: each would re-lay the whole reply out.
                            streamBuffer.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - streamPublishedAt >= STREAM_PUBLISH_MS) {
                                streamPublishedAt = now
                                val text = streamBuffer.toString()
                                publish { it.copy(streamingText = text) }
                            }
                        }
                }
            }
            is ClaudeMessage.ControlRequest -> {
                val permission = message.permission ?: return
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
            is ClaudeMessage.ControlCancel -> {
                publish { state ->
                    val left = state.pendingPermissions.filter { it.requestId != message.requestId }
                    state.copy(pendingPermissions = left, status = if (left.isEmpty() && state.status == ChatStatus.NEEDS_APPROVAL) ChatStatus.WORKING else state.status)
                }
            }
            is ClaudeMessage.System -> {
                if (message.subtype == "task_summary") message.detail?.let { detail -> publish { it.copy(activity = detail) } }
            }
            is ClaudeMessage.Result -> {
                if (message.isError) {
                    if (message.subtype == "error_during_execution" || message.terminalReason?.startsWith("aborted") == true) {
                        transcript.addNotice(NoticeKind.INTERRUPTED)
                    } else {
                        transcript.addNotice(NoticeKind.TURN_FAILED, message.result ?: message.subtype)
                    }
                }
                publish {
                    it.copy(
                        status = ChatStatus.READY,
                        activity = null,
                        turnStartedAt = null,
                        streamingText = "",
                        pendingPermissions = emptyList(),
                        costUsd = message.totalCostUsd ?: it.costUsd,
                        contextWindow = message.contextWindow ?: it.contextWindow,
                    )
                }
                refreshGit()
                onSessionChanged()
            }
            else -> {}
        }
    }

    /** Applies [change] and refreshes the rows from the transcript. */
    private fun publish(change: (ClaudeChatState) -> ClaudeChatState) {
        _state.update { change(it).copy(items = transcript.snapshot()) }
    }

    companion object {
        private const val TAG = "ClaudeChat"
        private const val STREAM_PUBLISH_MS = 50L

        /** What a session on a nostr repository may not run: pushing and publishing proposals. */
        val NO_PUBLISHING =
            listOf(
                "Bash(git push:*)",
                "Bash(ngit send:*)",
                "Bash(ngit pr merge:*)",
                "Bash(ngit sync:*)",
                "Bash(amy git pr:*)",
                "Bash(amy git pr-update:*)",
            )
        private const val STDERR_SHOWN = 2_000

        /**
         * Git config, through the environment, that sends every push from the session's process
         * tree to a transport that doesn't exist: an empty `pushInsteadOf` prefix matches every
         * URL. Unlike [NO_PUBLISHING]'s command rules, `git -C . push`, `/usr/bin/git push`, a
         * script or `--no-verify` don't get around it. The app's own push runs in its own
         * process and is untouched. Fetching still works.
         *
         * Added after any config [parent] already passes this way (a proxy's URL rewrites,
         * credential settings), which a fresh count would drop.
         */
        fun noPushEnvironment(parent: Map<String, String> = System.getenv()): Map<String, String> {
            val n = parent["GIT_CONFIG_COUNT"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return mapOf(
                "GIT_CONFIG_COUNT" to (n + 1).toString(),
                "GIT_CONFIG_KEY_$n" to "url.publishing-is-the-users-step-in-amethyst://.pushInsteadOf",
                "GIT_CONFIG_VALUE_$n" to "",
            )
        }
    }
}
