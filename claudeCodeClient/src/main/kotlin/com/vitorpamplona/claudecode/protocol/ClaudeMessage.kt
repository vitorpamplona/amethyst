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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One line Claude Code writes on stdout in `--output-format stream-json` mode. Every class keeps
 * the line's JSON in [raw]: the CLI adds message types and fields between releases, and a
 * client that only shows what it knows should not lose the rest.
 */
sealed interface ClaudeMessage {
    val raw: JsonObject

    /** The first message of a run: which session, folder, model and permission mode it uses. */
    data class Init(
        val sessionId: String,
        val cwd: String?,
        val model: String?,
        val permissionMode: PermissionMode?,
        val tools: List<String>,
        val claudeCodeVersion: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage

    /** `session_state_changed`: whether the session is idle, running or waiting on an answer. */
    data class SessionState(
        val sessionId: String?,
        val state: String,
        override val raw: JsonObject,
    ) : ClaudeMessage

    /** Any other `system` message (status, summaries, a refused tool call, hook output...). */
    data class System(
        val subtype: String?,
        val sessionId: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage {
        /** A one-line description some subtypes carry (`task_summary`, `post_turn_summary`, `permission_denied`). */
        val detail: String? get() = raw.string("detail") ?: raw.string("status_detail") ?: raw.string("message")
    }

    /**
     * A message from the model. With partial messages on, the CLI sends one per content block,
     * all sharing [messageId].
     */
    data class Assistant(
        val uuid: String?,
        val sessionId: String?,
        val messageId: String?,
        val model: String?,
        val content: List<ContentBlock>,
        val parentToolUseId: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage {
        /**
         * How much of the context window the conversation fills after this message: its input
         * (fresh, cached and written to the cache) plus what it wrote. Null when the CLI didn't say.
         */
        val contextTokens: Long?
            get() {
                val usage = raw.obj("message")?.obj("usage") ?: return null
                val parts = listOf("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens", "output_tokens").mapNotNull { usage.long(it) }
                return if (parts.isEmpty()) null else parts.sum()
            }
    }

    /** A user-side message: tool results, the CLI's own notes, or an echo of a typed prompt. */
    data class User(
        val uuid: String?,
        val sessionId: String?,
        val content: List<ContentBlock>,
        val parentToolUseId: String?,
        val isSynthetic: Boolean,
        override val raw: JsonObject,
    ) : ClaudeMessage

    /** The end of a turn. [isError] is true for an interrupted or failed one. */
    data class Result(
        val subtype: String?,
        val isError: Boolean,
        val result: String?,
        val sessionId: String?,
        val totalCostUsd: Double?,
        val numTurns: Long?,
        val durationMs: Long?,
        val stopReason: String?,
        val terminalReason: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage {
        /** The model's context window, from the turn's per-model usage. */
        val contextWindow: Long?
            get() =
                (raw.obj("model_usage_since_last_result") ?: raw.obj("modelUsage"))
                    ?.values
                    ?.firstNotNullOfOrNull { (it as? JsonObject)?.long("contextWindow") }
    }

    /** A raw API streaming event (`--include-partial-messages`): text arriving as it is written. */
    data class StreamEvent(
        val sessionId: String?,
        val parentToolUseId: String?,
        val event: JsonObject,
        override val raw: JsonObject,
    ) : ClaudeMessage {
        val eventType: String? get() = event.string("type")

        /** The text a `content_block_delta` adds, if it is a text delta. */
        val textDelta: String?
            get() =
                if (eventType == "content_block_delta") {
                    event.obj("delta")?.takeIf { it.string("type") == "text_delta" }?.string("text")
                } else {
                    null
                }
    }

    /** The CLI asks the host something and waits for a [ControlResponse] with the same id. */
    data class ControlRequest(
        val requestId: String,
        val subtype: String?,
        val request: JsonObject,
        override val raw: JsonObject,
    ) : ClaudeMessage {
        /** Set when this is a permission prompt (`can_use_tool`). */
        val permission: PermissionRequest? get() = if (subtype == "can_use_tool") PermissionRequest.parse(requestId, request) else null
    }

    /** The answer to a control request the host sent. */
    data class ControlResponse(
        val requestId: String,
        val isSuccess: Boolean,
        val response: JsonObject?,
        val error: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage

    /** The CLI withdrew a control request it sent (the prompt no longer needs an answer). */
    data class ControlCancel(
        val requestId: String,
        override val raw: JsonObject,
    ) : ClaudeMessage

    /** A type this client doesn't model (rate limits, goals, task updates...). */
    data class Other(
        val type: String?,
        override val raw: JsonObject,
    ) : ClaudeMessage

    companion object {
        internal val json = Json { ignoreUnknownKeys = true }

        /** Parses one stdout line; null for a line that isn't a JSON object (stray output). */
        fun parse(line: String): ClaudeMessage? {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) return null
            val obj =
                try {
                    json.parseToJsonElement(trimmed) as? JsonObject
                } catch (e: IllegalArgumentException) {
                    null
                } ?: return null
            return parse(obj)
        }

        fun parse(obj: JsonObject): ClaudeMessage {
            val sessionId = obj.string("session_id")
            return when (val type = obj.string("type")) {
                "system" ->
                    when (val subtype = obj.string("subtype")) {
                        "init" ->
                            Init(
                                sessionId = sessionId ?: "",
                                cwd = obj.string("cwd"),
                                model = obj.string("model"),
                                permissionMode = PermissionMode.fromWire(obj.string("permissionMode")),
                                tools = obj.strings("tools"),
                                claudeCodeVersion = obj.string("claude_code_version"),
                                raw = obj,
                            )
                        "session_state_changed" -> SessionState(sessionId, obj.string("state") ?: "", obj)
                        else -> System(subtype, sessionId, obj)
                    }
                "assistant" -> {
                    val message = obj.obj("message")
                    Assistant(
                        uuid = obj.string("uuid"),
                        sessionId = sessionId,
                        messageId = message?.string("id"),
                        model = message?.string("model"),
                        content = ContentBlock.listOf(message?.get("content")),
                        parentToolUseId = obj.string("parent_tool_use_id"),
                        raw = obj,
                    )
                }
                "user" ->
                    User(
                        uuid = obj.string("uuid"),
                        sessionId = sessionId,
                        content = ContentBlock.listOf(obj.obj("message")?.get("content")),
                        parentToolUseId = obj.string("parent_tool_use_id"),
                        isSynthetic = obj.boolean("isSynthetic") ?: false,
                        raw = obj,
                    )
                "result" ->
                    Result(
                        subtype = obj.string("subtype"),
                        isError = obj.boolean("is_error") ?: false,
                        result = obj.string("result"),
                        sessionId = sessionId,
                        totalCostUsd = obj.double("total_cost_usd"),
                        numTurns = obj.long("num_turns"),
                        durationMs = obj.long("duration_ms"),
                        stopReason = obj.string("stop_reason"),
                        terminalReason = obj.string("terminal_reason"),
                        raw = obj,
                    )
                "stream_event" -> StreamEvent(sessionId, obj.string("parent_tool_use_id"), obj.obj("event") ?: JsonObject(emptyMap()), obj)
                "control_request" -> {
                    val request = obj.obj("request") ?: JsonObject(emptyMap())
                    ControlRequest(obj.string("request_id") ?: "", request.string("subtype"), request, obj)
                }
                "control_response" -> {
                    val response = obj.obj("response") ?: JsonObject(emptyMap())
                    ControlResponse(
                        requestId = response.string("request_id") ?: "",
                        isSuccess = response.string("subtype") == "success",
                        response = response.obj("response"),
                        error = response.string("error"),
                        raw = obj,
                    )
                }
                "control_cancel_request" -> ControlCancel(obj.string("request_id") ?: "", obj)
                else -> Other(type, obj)
            }
        }
    }
}
