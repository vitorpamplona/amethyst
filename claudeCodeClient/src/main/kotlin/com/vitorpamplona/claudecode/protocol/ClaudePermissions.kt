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

import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.PermissionSuggestion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Reads Claude Code's permission prompts (`can_use_tool`) and what it suggests with them. */
object ClaudePermissions {
    fun request(
        requestId: String,
        request: JsonObject,
    ): PermissionRequest {
        val suggestions = request.array("permission_suggestions") ?: JsonArray(emptyList())
        return PermissionRequest(
            requestId = requestId,
            toolName = request.string("tool_name") ?: "",
            displayName = request.string("display_name"),
            description = request.string("description"),
            title = request.string("title"),
            input = request.obj("input") ?: JsonObject(emptyMap()),
            toolUseId = request.string("tool_use_id"),
            decisionReason = request.string("decision_reason")?.let(::stripAnsi),
            blockedPath = request.string("blocked_path"),
            suggestions = suggestions,
            suppressAlwaysAllow = request.boolean("suppress_always_allow_rule") ?: false,
            defaultToNo = request.boolean("default_to_no") ?: false,
            agentId = request.string("agent_id"),
            parsedSuggestions = suggestions.map { suggestion(it) },
        )
    }

    fun suggestion(element: JsonElement): PermissionSuggestion {
        val obj = element as? JsonObject ?: return PermissionSuggestion.Other(null, null)
        val destination = obj.string("destination")
        return when (val type = obj.string("type")) {
            "setMode" -> PermissionSuggestion.SetMode(PermissionMode.fromWire(obj.string("mode")), destination)
            "addRules", "replaceRules" ->
                PermissionSuggestion.AddRules(
                    rules =
                        obj.array("rules").orEmpty().mapNotNull { rule ->
                            val r = rule as? JsonObject ?: return@mapNotNull null
                            PermissionSuggestion.Rule(r.string("toolName") ?: return@mapNotNull null, r.string("ruleContent"))
                        },
                    behavior = obj.string("behavior"),
                    destination = destination,
                )
            "addDirectories" -> PermissionSuggestion.AddDirectories(obj.strings("directories"), destination)
            else -> PermissionSuggestion.Other(type, destination)
        }
    }

    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    fun stripAnsi(text: String) = ansi.replace(text, "")
}
