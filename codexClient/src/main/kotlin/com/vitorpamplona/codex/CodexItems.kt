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
package com.vitorpamplona.codex

import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.TranscriptEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Codex's thread items as the chat's content blocks. Tool-like items become a tool call and
 * its result, named so the chat's renderers know them: a command is `Bash` (`command`), a
 * patch is [APPLY_PATCH] (`changes`, each with a unified diff), an MCP tool `mcp__server__tool`,
 * a web search `WebSearch`, an image the agent looked at `Read`.
 */
object CodexItems {
    /** The tool name a file change shows under. */
    const val APPLY_PATCH = "ApplyPatch"

    fun type(item: JsonObject): String? = item.text("type")

    fun id(item: JsonObject): String = item.text("id").orEmpty()

    /** The agent's words, for an `agentMessage`. */
    fun agentText(item: JsonObject): String? = if (type(item) == "agentMessage") item.text("text") else null

    /** What the user typed, for a `userMessage`: its text inputs, joined. */
    fun userText(item: JsonObject): String? {
        if (type(item) != "userMessage") return null
        return (item["content"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.takeIf { input -> input.text("type") == "text" }?.text("text") }
            .joinToString("\n")
    }

    /** How many images a `userMessage` carried. */
    fun userImages(item: JsonObject): Int =
        (item["content"] as? JsonArray).orEmpty().count {
            val kind = (it as? JsonObject)?.text("type")
            kind == "image" || kind == "localImage"
        }

    /** The call a tool-like item makes, or null for an item that isn't one. */
    fun toolUse(item: JsonObject): ContentBlock.ToolUse? {
        val id = id(item)
        return when (type(item)) {
            "commandExecution" ->
                ContentBlock.ToolUse(
                    id,
                    "Bash",
                    buildJsonObject {
                        put("command", item.text("command").orEmpty())
                        item.text("cwd")?.let { put("cwd", it) }
                    },
                )
            "fileChange" -> ContentBlock.ToolUse(id, APPLY_PATCH, buildJsonObject { put("changes", item["changes"] ?: JsonArray(emptyList())) })
            "mcpToolCall" ->
                ContentBlock.ToolUse(
                    id,
                    "mcp__${item.text("server")}__${item.text("tool")}",
                    item["arguments"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            "webSearch" -> ContentBlock.ToolUse(id, "WebSearch", buildJsonObject { put("query", item.text("query").orEmpty()) })
            "imageView" -> ContentBlock.ToolUse(id, "Read", buildJsonObject { put("file_path", item.text("path").orEmpty()) })
            "collabAgentToolCall" ->
                ContentBlock.ToolUse(
                    id,
                    "Agent",
                    buildJsonObject {
                        put(
                            "description",
                            item
                                .text("prompt")
                                .orEmpty()
                                .lineSequence()
                                .firstOrNull()
                                .orEmpty(),
                        )
                    },
                )
            else -> null
        }
    }

    /** What a finished tool-like item gave back, or null while it runs or for an item that isn't one. */
    fun toolResult(item: JsonObject): ContentBlock.ToolResult? {
        if (toolUse(item) == null) return null
        val status = item.text("status")
        if (status == "inProgress") return null
        val failed = status == "failed" || status == "declined"
        val id = id(item)
        return when (type(item)) {
            "commandExecution" -> {
                val exit = (item["exitCode"] as? JsonPrimitive)?.intOrNull
                ContentBlock.ToolResult(id, item.text("aggregatedOutput").orEmpty(), failed || (exit != null && exit != 0))
            }
            "fileChange" -> ContentBlock.ToolResult(id, if (failed) "The change was not applied ($status)." else "", failed)
            "mcpToolCall" -> {
                val error = (item["error"] as? JsonObject)?.text("message")
                val content = ((item["result"] as? JsonObject)?.get("content") as? JsonArray).orEmpty()
                val text = content.mapNotNull { (it as? JsonObject)?.text("text") }.joinToString("\n")
                ContentBlock.ToolResult(id, error ?: text, failed || error != null)
            }
            else -> ContentBlock.ToolResult(id, status.orEmpty(), failed)
        }
    }

    /**
     * A thread's saved turns as the chat's history. Each prompt's [TranscriptEntry.parentUuid]
     * is the turn before it, so a copy forked through that turn (`thread/fork` with
     * `lastTurnId`) leaves the prompt out.
     */
    fun history(thread: JsonObject): List<TranscriptEntry> {
        val entries = ArrayList<TranscriptEntry>()
        var previousTurn: String? = null
        (thread["turns"] as? JsonArray).orEmpty().forEach { element ->
            val turn = element as? JsonObject ?: return@forEach
            val turnId = turn.text("id")
            (turn["items"] as? JsonArray).orEmpty().forEach item@{ itemElement ->
                val item = itemElement as? JsonObject ?: return@item
                val id = id(item)
                userText(item)?.let { text ->
                    val blocks = List(userImages(item)) { ContentBlock.Image(null) } + listOfNotNull(text.takeIf { it.isNotBlank() }?.let { ContentBlock.Text(it) })
                    entries.add(TranscriptEntry(id, TranscriptEntry.Role.USER, blocks, null, null, parentUuid = previousTurn))
                    return@item
                }
                agentText(item)?.let { text ->
                    entries.add(TranscriptEntry(id, TranscriptEntry.Role.ASSISTANT, listOf(ContentBlock.Text(text)), null, null))
                    return@item
                }
                toolUse(item)?.let { call ->
                    entries.add(TranscriptEntry(id, TranscriptEntry.Role.ASSISTANT, listOf(call), null, null))
                    toolResult(item)?.let { entries.add(TranscriptEntry("$id-result", TranscriptEntry.Role.USER, listOf(it), null, null)) }
                }
            }
            if (turnId != null) previousTurn = turnId
        }
        return entries
    }

    /** A user input carrying an image, as `turn/start` takes it: a data URL. */
    fun imageInput(
        mediaType: String,
        base64: String,
    ) = buildJsonObject {
        put("type", "image")
        put("url", "data:$mediaType;base64,$base64")
    }

    fun textInput(text: String) =
        buildJsonObject {
            put("type", "text")
            put("text", text)
            put("text_elements", buildJsonArray { })
        }

    internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
