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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One block of a Messages API message, as Claude Code writes it on stdout and in its session
 * files. Only the kinds a client renders get their own class; the rest keep their JSON in
 * [Other] so nothing is lost.
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

    companion object {
        /** A message's `content`: a plain string (a typed prompt) or an array of blocks. */
        fun listOf(content: JsonElement?): List<ContentBlock> =
            when (content) {
                null -> emptyList()
                is JsonPrimitive -> if (content.isString) listOf(Text(content.content)) else emptyList()
                is JsonArray -> content.map { parse(it) }
                is JsonObject -> listOf(parse(content))
            }

        fun parse(element: JsonElement): ContentBlock {
            val obj = element as? JsonObject ?: return Other(null, element)
            return when (val type = obj.string("type")) {
                "text" -> Text(obj.string("text") ?: "")
                "thinking" -> Thinking(obj.string("thinking") ?: "")
                "redacted_thinking" -> Thinking("")
                "tool_use", "server_tool_use" ->
                    ToolUse(
                        id = obj.string("id") ?: "",
                        name = obj.string("name") ?: "",
                        input = obj["input"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                "tool_result" ->
                    ToolResult(
                        toolUseId = obj.string("tool_use_id") ?: "",
                        text = toolResultText(obj["content"]),
                        isError = obj.boolean("is_error") ?: false,
                    )
                "image" -> Image((obj["source"] as? JsonObject)?.string("media_type"))
                else -> Other(type, obj)
            }
        }

        /** A tool result's content is a string or an array of text and image blocks. */
        private fun toolResultText(content: JsonElement?): String =
            when (content) {
                null -> ""
                is JsonPrimitive -> if (content.isString) content.content else content.toString()
                is JsonArray ->
                    content.joinToString("\n") { block ->
                        val obj = block as? JsonObject
                        when (obj?.string("type")) {
                            "text" -> obj.string("text") ?: ""
                            "image" -> "[image]"
                            else -> block.toString()
                        }
                    }
                is JsonObject -> content.toString()
            }
    }
}

/**
 * What a tool call acts on in one line, for a list: the command, the path, the URL or the
 * search. Null when the input names none of them.
 */
fun toolInputSummary(input: JsonObject): String? =
    input.string("command")
        ?: input.string("file_path")
        ?: input.string("notebook_path")
        ?: input.string("url")
        ?: input.string("pattern")
        ?: input.string("query")
        ?: input.string("description")
        ?: input.string("prompt")?.lineSequence()?.firstOrNull()
