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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One block of a message in an agent's conversation: text, reasoning, a tool call or its result,
 * an image. Each agent's client reads its own protocol into these; what an agent sends that has
 * no class of its own keeps its JSON in [Other].
 */
sealed interface ContentBlock {
    data class Text(
        val text: String,
    ) : ContentBlock

    /** Reasoning. [text] is empty when the model returns it omitted, which is the default. */
    data class Thinking(
        val text: String,
    ) : ContentBlock

    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject,
    ) : ContentBlock {
        /** What the call acts on, in one line: see [toolInputSummary]. */
        val summary: String? get() = toolInputSummary(input)
    }

    data class ToolResult(
        val toolUseId: String,
        val text: String,
        val isError: Boolean,
    ) : ContentBlock

    data class Image(
        val mediaType: String?,
    ) : ContentBlock

    data class Other(
        val type: String?,
        val raw: JsonElement,
    ) : ContentBlock
}

fun toolInputSummary(input: JsonObject): String? =
    input.string("command")
        ?: input.string("file_path")
        ?: input.string("notebook_path")
        ?: input.string("url")
        ?: input.string("pattern")
        ?: input.string("query")
        ?: input.string("description")
        ?: input.string("prompt")?.lineSequence()?.firstOrNull()
