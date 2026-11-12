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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Claude Code asking whether a tool call may run (`can_use_tool`). The CLI only asks the host
 * when it was started with `--permission-prompt-tool stdio`; otherwise it denies by itself.
 */
data class PermissionRequest(
    val requestId: String,
    val toolName: String,
    val displayName: String?,
    /** The CLI's own short wording of the call, e.g. the file name for a Write. */
    val description: String?,
    val title: String?,
    val input: JsonObject,
    val toolUseId: String?,
    /** Why the call needs an answer; may carry ANSI escapes, so strip them before showing it. */
    val decisionReason: String?,
    val blockedPath: String?,
    /**
     * Permission updates the CLI suggests for "don't ask again" (a rule, or a mode such as
     * accept-edits for this session). Sent back as-is in [PermissionDecision.Allow.updatedPermissions].
     */
    val suggestions: JsonArray,
    /** The host must not offer a persistent "always allow" for this call. */
    val suppressAlwaysAllow: Boolean,
    /** The host must not pre-select approve. */
    val defaultToNo: Boolean,
    val agentId: String?,
) {
    /** What the call does in one line: the command, the path or the URL when the input names one. */
    val summary: String get() = toolInputSummary(input) ?: description ?: input.toString()

    companion object {
        fun parse(
            requestId: String,
            request: JsonObject,
        ) = PermissionRequest(
            requestId = requestId,
            toolName = request.string("tool_name") ?: "",
            displayName = request.string("display_name"),
            description = request.string("description"),
            title = request.string("title"),
            input = request.obj("input") ?: JsonObject(emptyMap()),
            toolUseId = request.string("tool_use_id"),
            decisionReason = request.string("decision_reason")?.let(::stripAnsi),
            blockedPath = request.string("blocked_path"),
            suggestions = request.array("permission_suggestions") ?: JsonArray(emptyList()),
            suppressAlwaysAllow = request.boolean("suppress_always_allow_rule") ?: false,
            defaultToNo = request.boolean("default_to_no") ?: false,
            agentId = request.string("agent_id"),
        )

        private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

        fun stripAnsi(text: String) = ansi.replace(text, "")
    }
}

/** The host's answer to a [PermissionRequest]. */
sealed interface PermissionDecision {
    /**
     * Run the call. [updatedPermissions] carries the request's suggestions when the user picked
     * "don't ask again"; [updatedInput] replaces the tool input when the user edited it.
     */
    data class Allow(
        val updatedInput: JsonObject? = null,
        val updatedPermissions: JsonArray? = null,
    ) : PermissionDecision

    /** Refuse the call; [message] goes back to the model. [interrupt] also stops the turn. */
    data class Deny(
        val message: String,
        val interrupt: Boolean = false,
    ) : PermissionDecision
}
