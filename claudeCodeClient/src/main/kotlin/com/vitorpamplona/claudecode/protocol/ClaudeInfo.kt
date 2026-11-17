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

import com.vitorpamplona.agents.AgentInfo
import com.vitorpamplona.agents.ModelChoice
import com.vitorpamplona.agents.SlashCommand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reads what Claude Code answered `initialize` with: its commands and models, for this folder and account. */
object ClaudeInfo {
    fun parse(response: JsonObject?): AgentInfo? {
        response ?: return null
        val commands =
            (response["commands"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                SlashCommand(
                    name = obj.string("name") ?: return@mapNotNull null,
                    description = obj.string("description").orEmpty(),
                    argumentHint = obj.string("argumentHint").orEmpty(),
                    aliases = (obj["aliases"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                )
            }
        val models =
            (response["models"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val value = obj.string("value") ?: return@mapNotNull null
                ModelChoice(
                    value = value,
                    displayName = obj.string("displayName") ?: value,
                    description = obj.string("description").orEmpty(),
                    resolvedModel = obj.string("resolvedModel"),
                )
            }
        return AgentInfo(commands, models, response.string("claude_code_version"))
    }
}
