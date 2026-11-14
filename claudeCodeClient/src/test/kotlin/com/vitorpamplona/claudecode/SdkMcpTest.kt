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
import com.vitorpamplona.claudecode.mcp.SdkMcpDispatcher
import com.vitorpamplona.claudecode.mcp.SdkMcpServer
import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdkMcpTest {
    private class Echo : SdkMcpServer {
        override val name = "echo"
        override val tools = listOf(McpTool("say", "Says it back", obj("""{"type":"object","properties":{"text":{"type":"string"}}}""")))

        override suspend fun call(
            tool: String,
            arguments: JsonObject,
        ): McpToolResult {
            val text = arguments["text"]!!.jsonPrimitive.content
            require(text.isNotBlank()) { "Say something." }
            return McpToolResult("you said $text")
        }
    }

    @Test
    fun answersTheMcpHandshakeListAndCalls() =
        runTest {
            val server = Echo()
            val init = SdkMcpDispatcher.handle(server, obj("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""))
            assertEquals("2025-06-18", init["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
            assertEquals(
                "echo",
                init["result"]!!
                    .jsonObject["serverInfo"]!!
                    .jsonObject["name"]!!
                    .jsonPrimitive.content,
            )

            // A notification has no response of its own; Claude Code still expects an acknowledgement.
            val ack = SdkMcpDispatcher.handle(server, obj("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
            assertEquals("0", ack["id"]!!.jsonPrimitive.content)

            val list = SdkMcpDispatcher.handle(server, obj("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""))
            assertEquals(
                "say",
                list["result"]!!
                    .jsonObject["tools"]!!
                    .jsonArray
                    .single()
                    .jsonObject["name"]!!
                    .jsonPrimitive.content,
            )

            val call = SdkMcpDispatcher.handle(server, obj("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"say","arguments":{"text":"hi"}}}"""))
            assertEquals(
                "you said hi",
                call["result"]!!
                    .jsonObject["content"]!!
                    .jsonArray
                    .single()
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
            )

            // A bad argument is the tool's error for Claude to read, not a protocol failure.
            val bad = SdkMcpDispatcher.handle(server, obj("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"say","arguments":{"text":" "}}}"""))
            assertEquals("true", bad["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content)
            assertEquals(
                "Say something.",
                bad["result"]!!
                    .jsonObject["content"]!!
                    .jsonArray
                    .single()
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun theConnectionAnswersTheCliToolRequestsAndDeclaresItsServers() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope, listOf(Echo()))
            transport.onWrite = { line ->
                if (line.contains("\"subtype\":\"initialize\"")) {
                    val id = Regex("\"request_id\":\"([^\"]+)\"").find(line)!!.groupValues[1]
                    transport.emit("""{"type":"control_response","response":{"subtype":"success","request_id":"$id","response":{}}}""")
                }
            }
            connection.initialize()
            assertTrue(transport.written.first().contains("\"sdkMcpServers\":[\"echo\"]"), transport.written.first())

            transport.emit(
                """{"type":"control_request","request_id":"m1","request":{"subtype":"mcp_message","server_name":"echo","message":{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"say","arguments":{"text":"yo"}}}}}""",
            )
            // The call runs on the connection's scope, beside the stream: wait for its answer.
            val replyLine = withTimeout(5_000) { transport.written.firstOrNullAwait { it.contains("\"request_id\":\"m1\"") } }
            val reply = obj(replyLine)
            val response = reply["response"]!!.jsonObject
            assertEquals("m1", response["request_id"]!!.jsonPrimitive.content)
            val mcp = response["response"]!!.jsonObject["mcp_response"]!!.jsonObject
            assertEquals("7", mcp["id"]!!.jsonPrimitive.content)
            assertTrue(mcp.toString().contains("you said yo"))
        }

    private suspend fun List<String>.firstOrNullAwait(match: (String) -> Boolean): String {
        while (true) {
            firstOrNull(match)?.let { return it }
            yield()
        }
    }

    private companion object {
        fun obj(json: String) = Json.parseToJsonElement(json).jsonObject
    }
}
