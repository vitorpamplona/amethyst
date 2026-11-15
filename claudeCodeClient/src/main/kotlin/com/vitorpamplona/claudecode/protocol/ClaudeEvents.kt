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
package com.vitorpamplona.claudecode.protocol

import com.vitorpamplona.agents.AgentEvent

/** What each of Claude Code's messages means for the chat, as an [AgentEvent]; null for what the chat doesn't use. */
object ClaudeEvents {
    fun of(message: ClaudeMessage): AgentEvent? =
        when (message) {
            is ClaudeMessage.Init -> AgentEvent.SessionStarted(message.sessionId, message.model, message.permissionMode)
            is ClaudeMessage.Assistant ->
                message.parentToolUseId?.let { AgentEvent.Subagent(it, message.content, fromAgent = true) }
                    ?: AgentEvent.Reply(message.content, message.contextTokens)
            is ClaudeMessage.User ->
                message.parentToolUseId?.let { AgentEvent.Subagent(it, message.content, fromAgent = false) }
                    ?: AgentEvent.ToolResults(message.content)
            is ClaudeMessage.StreamEvent ->
                when {
                    // A subagent's reply streams too; the chat shows its messages once they are whole.
                    message.parentToolUseId != null -> null
                    message.eventType == "message_start" || message.eventType == "content_block_start" -> AgentEvent.ReplyStarted
                    else -> message.textDelta?.let { AgentEvent.ReplyDelta(it) }
                }
            is ClaudeMessage.ControlRequest -> message.permission?.let { AgentEvent.ApprovalRequested(it) }
            is ClaudeMessage.ControlCancel -> AgentEvent.ApprovalWithdrawn(message.requestId)
            is ClaudeMessage.System ->
                when (message.subtype) {
                    "task_summary" -> message.detail?.let { AgentEvent.Activity(it) }
                    // A subagent running in the background, until it reports back.
                    "task_started", "task_progress" -> message.toolUseId?.let { AgentEvent.SubagentRunning(it, true) }
                    "task_notification" -> message.toolUseId?.let { AgentEvent.SubagentRunning(it, false) }
                    else -> null
                }
            is ClaudeMessage.Result -> {
                val interrupted = message.isError && (message.subtype == "error_during_execution" || message.terminalReason?.startsWith("aborted") == true)
                AgentEvent.TurnEnded(
                    interrupted = interrupted,
                    failure = if (message.isError && !interrupted) message.result ?: message.subtype else null,
                    costUsd = message.totalCostUsd,
                    contextWindow = message.contextWindow,
                )
            }
            else -> null
        }
}
