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
}

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
) {
    private val workDir = cwd
    private val transcript = ChatTranscript()
    private val lock = Mutex()
    private var connection: ClaudeCodeConnection? = null

    private val _state =
        MutableStateFlow(
            ClaudeChatState(
                sessionId = resumeSessionId,
                cwd = cwd.absolutePath,
                title = title,
                permissionMode = permissionMode,
                model = model,
                historyLoaded = resumeSessionId == null,
            ),
        )
    val state: StateFlow<ClaudeChatState> = _state.asStateFlow()

    init {
        if (resumeSessionId != null) {
            scope.launch(Dispatchers.IO) {
                val history = store.transcript(resumeSessionId)
                lock.withLock {
                    transcript.addHistory(history)
                    publish { it.copy(historyLoaded = true) }
                }
            }
        }
    }

    /** Sends a message, starting the process first if it isn't running. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            lock.withLock {
                transcript.addPrompt(trimmed)
                publish { it.copy(status = ChatStatus.WORKING, error = null) }
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

    /** Ends the process now (app exit, session deleted). */
    fun kill() {
        connection?.kill()
    }

    private suspend fun ensureStarted(): ClaudeCodeConnection? =
        lock.withLock {
            connection?.let { if (it.reader.isActive) return@withLock it }
            publish { it.copy(status = ChatStatus.STARTING) }
            val current = _state.value
            val options =
                ClaudeCodeOptions(
                    cwd = workDir,
                    resumeSessionId = current.sessionId,
                    model = model,
                    permissionMode = current.permissionMode,
                )
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
            if (connection === c) connection = null
            val wasBusy = _state.value.isBusy
            val error = if (code != null && code != 0 && wasBusy) ChatError.Exited(code, c.stderrTail().takeLast(STDERR_SHOWN)) else null
            if (wasBusy) transcript.addNotice(NoticeKind.PROCESS_ENDED)
            publish { it.copy(status = ChatStatus.ENDED, pendingPermissions = emptyList(), streamingText = "", error = error ?: it.error) }
        }
        onSessionChanged()
    }

    private fun onMessage(message: ClaudeMessage) {
        when (message) {
            is ClaudeMessage.Init -> {
                val isNewSession = _state.value.sessionId != message.sessionId
                publish {
                    it.copy(
                        sessionId = message.sessionId,
                        model = message.model ?: it.model,
                        permissionMode = message.permissionMode ?: it.permissionMode,
                    )
                }
                if (isNewSession) onSessionChanged()
            }
            is ClaudeMessage.Assistant -> {
                // A subagent's own messages stay inside its tool call.
                if (message.parentToolUseId != null) return
                transcript.addAssistantBlocks(message.content)
                publish { it.copy(streamingText = "") }
            }
            is ClaudeMessage.User -> {
                if (message.parentToolUseId != null) return
                transcript.addUserBlocks(message.content, typedByUser = false)
                publish { it }
            }
            is ClaudeMessage.StreamEvent -> {
                if (message.parentToolUseId != null) return
                when (message.eventType) {
                    "message_start", "content_block_start" -> publish { it.copy(streamingText = "") }
                    else -> message.textDelta?.let { delta -> publish { it.copy(streamingText = it.streamingText + delta) } }
                }
            }
            is ClaudeMessage.ControlRequest -> {
                val permission = message.permission ?: return
                publish { it.copy(pendingPermissions = it.pendingPermissions + permission, status = ChatStatus.NEEDS_APPROVAL) }
            }
            is ClaudeMessage.ControlCancel -> {
                publish { state ->
                    val left = state.pendingPermissions.filter { it.requestId != message.requestId }
                    state.copy(pendingPermissions = left, status = if (left.isEmpty() && state.status == ChatStatus.NEEDS_APPROVAL) ChatStatus.WORKING else state.status)
                }
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
                        streamingText = "",
                        pendingPermissions = emptyList(),
                        costUsd = message.totalCostUsd ?: it.costUsd,
                    )
                }
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
        private const val STDERR_SHOWN = 2_000
    }
}
