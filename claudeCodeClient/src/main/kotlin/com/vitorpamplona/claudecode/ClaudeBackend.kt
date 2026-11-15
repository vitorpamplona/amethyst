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
package com.vitorpamplona.claudecode

import com.vitorpamplona.agents.AgentBackend
import com.vitorpamplona.agents.AgentCapabilities
import com.vitorpamplona.agents.AgentConnection
import com.vitorpamplona.agents.AgentLiveSession
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.claudecode.process.ClaudeCodeLauncher
import com.vitorpamplona.claudecode.process.ClaudeCodeOptions
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.process.ClaudeExecutable
import com.vitorpamplona.claudecode.session.ClaudeSessionInfo
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Claude Code as an [AgentBackend]: the user's `claude`, its saved sessions, and how to start it. */
class ClaudeBackend(
    val store: ClaudeSessionStore = ClaudeSessionStore(),
    private val launcher: ClaudeCodeStarter = ClaudeCodeLauncher(),
    private val findExecutable: () -> File? = { ClaudeExecutable.find() },
) : AgentBackend {
    override val id = ID
    override val displayName = "Claude Code"
    override val shortName = "Claude"

    override val capabilities =
        AgentCapabilities(
            slashCommands = true,
            editFromMessage = true,
            subagents = true,
            hostTools = true,
            modes = listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN, PermissionMode.AUTO),
            liveSessions = true,
        )

    override fun executable(): File? = findExecutable()

    override suspend fun start(
        options: AgentStartOptions,
        scope: CoroutineScope,
    ): AgentConnection = launcher.start(claudeOptions(options), scope)

    override fun listSessions(cwd: String?) = store.listSessions(cwd).map { it.toAgent() }

    override fun session(sessionId: String) = store.session(sessionId)?.toAgent()

    override fun transcript(sessionId: String): List<TranscriptEntry> = store.transcript(sessionId)

    override fun subagentTranscripts(sessionId: String) = store.subagentTranscripts(sessionId)

    override fun rename(
        sessionId: String,
        title: String,
    ) = store.rename(sessionId, title)

    override fun delete(sessionId: String) = store.delete(sessionId)

    override fun liveSessions(): Map<String, AgentLiveSession> =
        store.liveSessions().mapValues { (_, live) ->
            AgentLiveSession(live.sessionId, live.pid, live.cwd, live.status, live.kind, live.name)
        }

    companion object {
        const val ID = "claude"

        /** The flags for [options]: Claude Code takes them all on its command line. */
        fun claudeOptions(options: AgentStartOptions) =
            ClaudeCodeOptions(
                cwd = options.cwd,
                resumeSessionId = options.resumeSessionId,
                forkSession = options.forkSession,
                resumeSessionAt = options.forkAt,
                model = options.model,
                permissionMode = options.permissionMode,
                disallowedTools = options.blockedCommands.map { "Bash($it:*)" },
                environment = options.environment,
                sdkMcpServers = options.hostTools,
                strictMcpConfig = options.onlyHostTools,
                tools = if (options.onlyHostTools) emptyList() else null,
                allowedTools = options.preApprovedTools,
                appendSystemPrompt = options.instructions,
            )

        private fun ClaudeSessionInfo.toAgent() = AgentSessionInfo(sessionId, title, customTitle, firstPrompt, lastPrompt, cwd, gitBranch, createdAt, lastModified)
    }
}
