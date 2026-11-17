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

import com.vitorpamplona.agents.ContentBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads the content blocks of Claude's messages (the Messages API's shape). */
object ClaudeContent {
    /** A message's `content`: a plain string (a typed prompt) or an array of blocks. */
    fun listOf(content: JsonElement?): List<ContentBlock> =
        when (content) {
            null -> emptyList()
            is JsonPrimitive -> if (content.isString) listOf(ContentBlock.Text(content.content)) else emptyList()
            is JsonArray -> content.map { parse(it) }
            is JsonObject -> listOf(parse(content))
        }

    fun parse(element: JsonElement): ContentBlock {
        val obj = element as? JsonObject ?: return ContentBlock.Other(null, element)
        return when (val type = obj.string("type")) {
            "text" -> ContentBlock.Text(obj.string("text") ?: "")
            "thinking" -> ContentBlock.Thinking(obj.string("thinking") ?: "")
            "redacted_thinking" -> ContentBlock.Thinking("")
            "tool_use", "server_tool_use" ->
                ContentBlock.ToolUse(
                    id = obj.string("id") ?: "",
                    name = obj.string("name") ?: "",
                    input = obj["input"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            "tool_result" ->
                ContentBlock.ToolResult(
                    toolUseId = obj.string("tool_use_id") ?: "",
                    text = toolResultText(obj["content"]),
                    isError = obj.boolean("is_error") ?: false,
                )
            "image" -> ContentBlock.Image((obj["source"] as? JsonObject)?.string("media_type"))
            else -> ContentBlock.Other(type, obj)
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
