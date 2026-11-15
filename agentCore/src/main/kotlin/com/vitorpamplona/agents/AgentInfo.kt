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

/** What an agent offers in a folder: its commands (or skills) and its models. */
data class AgentInfo(
    val commands: List<SlashCommand>,
    val models: List<ModelChoice>,
    val version: String?,
)
