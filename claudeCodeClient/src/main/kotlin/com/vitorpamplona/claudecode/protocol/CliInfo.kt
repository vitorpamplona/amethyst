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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A slash command the CLI offers in a folder: a built-in, a skill, a plugin's or the user's own. */
data class SlashCommand(
    val name: String,
    val description: String,
    val argumentHint: String = "",
    val aliases: List<String> = emptyList(),
)

/** A model the user can pick: [value] goes to `--model` and `set_model`. */
data class ModelChoice(
    val value: String,
    val displayName: String,
    val description: String,
    /** The model [value] stands for now ("default" and aliases move with releases). */
    val resolvedModel: String?,
)

/** What the CLI answered `initialize` with: its commands and models, for this folder and account. */
data class CliInfo(
    val commands: List<SlashCommand>,
    val models: List<ModelChoice>,
    val version: String?,
) {
    companion object {
        fun parse(response: JsonObject?): CliInfo? {
            response ?: return null
            val commands =
                (response["commands"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    SlashCommand(
                        name = obj.text("name") ?: return@mapNotNull null,
                        description = obj.text("description").orEmpty(),
                        argumentHint = obj.text("argumentHint").orEmpty(),
                        aliases = (obj["aliases"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                    )
                }
            val models =
                (response["models"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    ModelChoice(
                        value = obj.text("value") ?: return@mapNotNull null,
                        displayName = obj.text("displayName") ?: obj.text("value")!!,
                        description = obj.text("description").orEmpty(),
                        resolvedModel = obj.text("resolvedModel"),
                    )
                }
            return CliInfo(commands, models, response.text("claude_code_version"))
        }

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
