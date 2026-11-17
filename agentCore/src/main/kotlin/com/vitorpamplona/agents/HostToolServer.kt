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

import kotlinx.serialization.json.JsonObject

/** A tool the host app offers the agent: its name, what it does, and the JSON schema of its input. */
data class HostTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

/** What a tool call gave back: text for the agent to read; [isError] when it failed. */
data class HostToolResult(
    val text: String,
    val isError: Boolean = false,
)

/**
 * Tools that run inside the host app, offered to the agent over its own connection (Claude Code
 * reaches them as an in-process MCP server; Codex as dynamic tools). The settings assistant's
 * tools are one.
 */
interface HostToolServer {
    val name: String
    val version: String get() = "1.0"
    val tools: List<HostTool>

    suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): HostToolResult

    /**
     * The call in words, for the approval prompt ("Theme: System → Dark"), or null to show its
     * input. Called before the call runs, so it can show what would change.
     */
    fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? = null

    /** `mcp__<server>__<tool>`: how the chat names [tool], whichever agent calls it. */
    fun qualifiedName(tool: String) = "mcp__${name}__$tool"
}
