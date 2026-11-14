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
package com.vitorpamplona.claudecode

import com.vitorpamplona.claudecode.mcp.McpTool
import com.vitorpamplona.claudecode.mcp.McpToolResult
import com.vitorpamplona.claudecode.mcp.SdkMcpServer
import com.vitorpamplona.claudecode.process.ClaudeCodeLauncher
import com.vitorpamplona.claudecode.process.ClaudeCodeOptions
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.PermissionDecision
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real `claude` (a few cents of Haiku). Off unless `CLAUDE_CODE_LIVE=1`:
 *
 *     CLAUDE_CODE_LIVE=1 ./gradlew :claudeCodeClient:test --tests '*LiveClaudeCodeTest*'
 */
class LiveClaudeCodeTest {
    private val enabled = System.getenv("CLAUDE_CODE_LIVE") == "1"

    @Test
    fun approveAWriteThenFindTheSessionOnDisk() {
        if (!enabled) return
        val dir = Files.createTempDirectory("claude-live").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                withTimeout(180_000) {
                    val connection = ClaudeCodeLauncher().start(ClaudeCodeOptions(cwd = dir, model = "haiku"), scope)
                    connection.send("Use the Write tool to create hello.txt containing exactly: hi. Then reply with the word done.")

                    var sessionId: String? = null
                    var asked = false
                    val result =
                        connection.messages.first { message ->
                            when (message) {
                                is ClaudeMessage.Init -> sessionId = message.sessionId
                                is ClaudeMessage.ControlRequest ->
                                    message.permission?.let {
                                        asked = true
                                        connection.respond(it, PermissionDecision.Allow())
                                    }
                                else -> {}
                            }
                            message is ClaudeMessage.Result
                        } as ClaudeMessage.Result

                    assertTrue(asked, "the CLI asked the host before writing")
                    assertTrue(!result.isError, result.raw.toString())
                    assertEquals("hi", File(dir, "hello.txt").readText().trim())

                    assertTrue(connection.rename("Live test")?.isSuccess == true)
                    connection.close()

                    val store = ClaudeSessionStore()
                    val info = assertNotNull(store.session(assertNotNull(sessionId)))
                    assertEquals("Live test", info.title)
                    assertTrue(store.transcript(sessionId!!).isNotEmpty())
                    store.delete(sessionId!!)
                }
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    /** A tool the test runs in-process: Claude reaches it over the session's own pipe. */
    private class Counter : SdkMcpServer {
        override val name = "probe"
        val calls = mutableListOf<JsonObject>()
        override val tools =
            listOf(
                McpTool(
                    name = "secret_number",
                    description = "Returns the secret number for a given word.",
                    inputSchema =
                        Json
                            .parseToJsonElement(
                                """{"type":"object","properties":{"word":{"type":"string"}},"required":["word"]}""",
                            ).jsonObject,
                ),
            )

        override suspend fun call(
            tool: String,
            arguments: JsonObject,
        ): McpToolResult {
            calls.add(arguments)
            return McpToolResult("The secret number is 7341.")
        }

        override fun describe(
            tool: String,
            arguments: JsonObject,
        ) = "secret number for ${arguments["word"]}"
    }

    @Test
    fun aToolTheHostRunsIsCalledOverTheSessionPipe() {
        if (!enabled) return
        val dir = Files.createTempDirectory("claude-live-mcp").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = Counter()
        try {
            runBlocking {
                withTimeout(180_000) {
                    val connection =
                        ClaudeCodeLauncher().start(
                            ClaudeCodeOptions(
                                cwd = dir,
                                model = "haiku",
                                sdkMcpServers = listOf(server),
                                strictMcpConfig = true,
                                tools = emptyList(),
                                includePartialMessages = false,
                            ),
                            scope,
                        )
                    connection.send("Call the secret_number tool with the word apple, then reply with only the number it gave you.")

                    var askedFor: String? = null
                    val result =
                        connection.messages.first { message ->
                            if (message is ClaudeMessage.ControlRequest) {
                                message.permission?.let {
                                    askedFor = it.toolName
                                    connection.respond(it, PermissionDecision.Allow())
                                }
                            }
                            message is ClaudeMessage.Result
                        } as ClaudeMessage.Result

                    assertEquals("mcp__probe__secret_number", askedFor, "the CLI asks the host before running a host tool")
                    assertEquals("apple", (server.calls.single()["word"] as JsonPrimitive).content)
                    assertTrue(result.result.orEmpty().contains("7341"), result.raw.toString())
                    connection.close()
                    result.sessionId?.let { ClaudeSessionStore().delete(it) }
                }
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
