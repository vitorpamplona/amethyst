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
package com.vitorpamplona.agents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException

/** What happens in an agent's session, as the chat shows it. Each agent's client reads its own protocol into these. */
sealed interface AgentEvent {
    /** The session is running: its id (new for a new session or a copy), model and permission mode. */
    data class SessionStarted(
        val sessionId: String,
        val model: String?,
        val permissionMode: PermissionMode?,
    ) : AgentEvent

    /** A finished message from the agent; [contextTokens] is how full the context is after it, when known. */
    data class Reply(
        val content: List<ContentBlock>,
        val contextTokens: Long? = null,
    ) : AgentEvent

    /** Tool results, and notes the agent writes on the user's side ("[Request interrupted by user]"). */
    data class ToolResults(
        val content: List<ContentBlock>,
    ) : AgentEvent

    /** A message from a subagent the call [toolUseId] started; its tool results come the same way. */
    data class Subagent(
        val toolUseId: String,
        val content: List<ContentBlock>,
        val fromAgent: Boolean,
    ) : AgentEvent

    /** A reply being written: a new one starts ([ReplyStarted]) and grows by [ReplyDelta]s until its [Reply]. */
    data object ReplyStarted : AgentEvent

    data class ReplyDelta(
        val text: String,
    ) : AgentEvent

    /** The agent asks whether a tool call may run. */
    data class ApprovalRequested(
        val request: PermissionRequest,
    ) : AgentEvent

    /** The agent no longer needs the answer to [requestId]. */
    data class ApprovalWithdrawn(
        val requestId: String,
    ) : AgentEvent

    /** What the agent says it is doing now ("Writing hello.txt"). */
    data class Activity(
        val text: String,
    ) : AgentEvent

    /** How full the context is: [tokens] of [window], when the agent says. */
    data class ContextUsage(
        val tokens: Long?,
        val window: Long?,
    ) : AgentEvent

    /** A subagent started by [toolUseId] runs in the background, or stopped. */
    data class SubagentRunning(
        val toolUseId: String,
        val running: Boolean,
    ) : AgentEvent

    /** The turn is over. [failure] says why one that failed did; [interrupted] when the user stopped it. */
    data class TurnEnded(
        val interrupted: Boolean,
        val failure: String?,
        val costUsd: Double? = null,
        val contextWindow: Long? = null,
    ) : AgentEvent
}

/** The agent's program isn't on this computer. */
open class AgentNotInstalledException(
    message: String,
) : IOException(message)

/** One running agent process, for one session. */
interface AgentConnection {
    /** Everything the session does, in order; completes when the process ends. */
    val events: Flow<AgentEvent>

    /** False once the process ended. */
    val isRunning: Boolean

    /** The commands and models the agent offered when it started. */
    val info: AgentInfo?

    /** Starts a turn with [text] and [images], or queues it if a turn is running. */
    suspend fun send(
        text: String,
        images: List<ImageInput> = emptyList(),
    )

    suspend fun respond(
        request: PermissionRequest,
        decision: PermissionDecision,
    )

    /** Stops the running turn; the process stays up for the next message. */
    suspend fun interrupt()

    suspend fun setPermissionMode(mode: PermissionMode)

    /** [model]: a [ModelChoice.value], or null for the agent's default. */
    suspend fun setModel(model: String?)

    /** Names the session; false when the agent couldn't. */
    suspend fun rename(title: String): Boolean

    /** Ends the process after the running turn, and gives its exit code. */
    suspend fun close(): Int?

    /** Ends the process now. */
    fun kill()

    /** The end of what the process wrote on stderr, for an error message. */
    fun stderrTail(): String
}

/** How to start an agent for a chat. Each backend maps these to its own flags or requests. */
data class AgentStartOptions(
    val cwd: File,
    val resumeSessionId: String? = null,
    /** With [resumeSessionId]: go on in a new session that starts as a copy of that one. */
    val forkSession: Boolean = false,
    /** With [forkSession]: the copy keeps the history only up to and including this message. */
    val forkAt: String? = null,
    val model: String? = null,
    val permissionMode: PermissionMode = PermissionMode.DEFAULT,
    /** Extra environment for the process and the commands it runs. */
    val environment: Map<String, String> = emptyMap(),
    /** Tools the host app runs itself. */
    val hostTools: List<HostToolServer> = emptyList(),
    /** Only [hostTools]: no files, shell or web, and none of the user's own tool servers. */
    val onlyHostTools: Boolean = false,
    /** Host tools (by [HostToolServer.qualifiedName]) that run without asking. */
    val preApprovedTools: List<String> = emptyList(),
    /** Added to the agent's own instructions, e.g. the settings assistant's role. */
    val instructions: String? = null,
    /** Shell commands (by prefix, e.g. `git push`) the agent may never run. */
    val blockedCommands: List<String> = emptyList(),
    /** Only to learn the agent's commands and models: start no session. */
    val probeOnly: Boolean = false,
)

/** A saved session, as the session list shows it. */
data class AgentSessionInfo(
    val sessionId: String,
    /** The name to show: the user's title, else the generated one, else the first prompt. */
    val title: String,
    val customTitle: String?,
    val firstPrompt: String?,
    val lastPrompt: String?,
    val cwd: String?,
    val gitBranch: String?,
    val createdAt: Long?,
    val lastModified: Long,
    /** Put away by the user: kept, but out of the way. Only agents that archive say so. */
    val archived: Boolean = false,
)

/** A session some other process of the agent has open right now (a terminal, an IDE, another app). */
data class AgentLiveSession(
    val sessionId: String,
    val pid: Long,
    val cwd: String?,
    val status: String?,
    val kind: String?,
    val name: String?,
)

/** What a backend can do beyond chatting; the screens hide what it can't. */
data class AgentCapabilities(
    val slashCommands: Boolean,
    val editFromMessage: Boolean,
    val subagents: Boolean,
    val hostTools: Boolean,
    val modes: List<PermissionMode>,
    /** Which other processes hold a session, so a chat can stay read-only meanwhile. */
    val liveSessions: Boolean,
    /** The agent keeps an archived flag of its own ([AgentBackend.archive]). */
    val archive: Boolean = false,
)

/** One coding agent the user installed: its sessions, and how to start it. */
interface AgentBackend {
    /** Stable id, saved with chats: `claude`, `codex`. */
    val id: String

    /** The agent's name for people: "Claude Code", "Codex". */
    val displayName: String

    /** How a sentence names it: "Claude wants to run a command". */
    val shortName: String get() = displayName

    val capabilities: AgentCapabilities

    /** The agent's program, or null when it isn't installed. */
    fun executable(): File?

    /** Starts the agent; throws [java.io.IOException] when it can't. */
    suspend fun start(
        options: AgentStartOptions,
        scope: CoroutineScope,
    ): AgentConnection

    /** Saved sessions, newest first; only those in [cwd] when given. Blocking. */
    fun listSessions(cwd: String? = null): List<AgentSessionInfo>

    fun session(sessionId: String): AgentSessionInfo?

    /** The session's history, oldest first. Blocking. */
    fun transcript(sessionId: String): List<TranscriptEntry>

    /** What each subagent did, by the call that started it. Blocking. */
    fun subagentTranscripts(sessionId: String): Map<String, List<TranscriptEntry>> = emptyMap()

    /** Names a saved session that no process of this app holds. Blocking. */
    fun rename(
        sessionId: String,
        title: String,
    )

    fun delete(sessionId: String): Boolean

    /**
     * Archives [sessionId] (or brings it back) where the agent keeps that itself; false when it
     * doesn't ([AgentCapabilities.archive]), and the app keeps the flag instead.
     */
    fun archive(
        sessionId: String,
        archived: Boolean,
    ): Boolean = false

    /** Sessions other processes have open now, by id. */
    fun liveSessions(): Map<String, AgentLiveSession> = emptyMap()
}
