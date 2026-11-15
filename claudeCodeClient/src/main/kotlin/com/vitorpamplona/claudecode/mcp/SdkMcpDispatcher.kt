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
package com.vitorpamplona.claudecode.mcp

import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Answers the JSON-RPC messages Claude Code sends an [HostToolServer]. */
object SdkMcpDispatcher {
    /**
     * The JSON-RPC response to [message], or the acknowledgement Claude Code expects for a
     * notification (a message without an id), which has no response of its own.
     */
    suspend fun handle(
        server: HostToolServer,
        message: JsonObject,
    ): JsonObject {
        val id = message["id"]
        if (id == null) return ack()
        val method = (message["method"] as? JsonPrimitive)?.content
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" ->
                result(id) {
                    // Speak whichever version the client asked for: this server's needs are the basics.
                    put("protocolVersion", params["protocolVersion"] ?: JsonPrimitive(DEFAULT_PROTOCOL))
                    put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                    put(
                        "serverInfo",
                        buildJsonObject {
                            put("name", server.name)
                            put("version", server.version)
                        },
                    )
                }
            "tools/list" ->
                result(id) {
                    put(
                        "tools",
                        buildJsonArray {
                            server.tools.forEach { tool ->
                                add(
                                    buildJsonObject {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("inputSchema", tool.inputSchema)
                                    },
                                )
                            }
                        },
                    )
                }
            "tools/call" -> {
                val name = (params["name"] as? JsonPrimitive)?.content
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val outcome =
                    if (name == null || server.tools.none { it.name == name }) {
                        HostToolResult("No tool named $name", isError = true)
                    } else {
                        try {
                            server.call(name, arguments)
                        } catch (e: IllegalArgumentException) {
                            HostToolResult(e.message ?: "Invalid arguments", isError = true)
                        }
                    }
                result(id) {
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", outcome.text)
                                },
                            )
                        },
                    )
                    if (outcome.isError) put("isError", true)
                }
            }
            "ping" -> result(id) {}
            else -> error(id, -32601, "Method not found: $method")
        }
    }

    private fun ack() =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("result", buildJsonObject {})
            put("id", 0)
        }

    private fun result(
        id: JsonElement,
        body: JsonObjectBuilder.() -> Unit,
    ) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", buildJsonObject(body))
    }

    private fun error(
        id: JsonElement,
        code: Int,
        text: String,
    ) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", text)
            },
        )
    }

    /** The `--mcp-config` entry that declares [servers] as living in the host. */
    fun mcpConfig(servers: List<HostToolServer>): String =
        buildJsonObject {
            put(
                "mcpServers",
                buildJsonObject {
                    servers.forEach { server ->
                        put(
                            server.name,
                            buildJsonObject {
                                put("type", "sdk")
                                put("name", server.name)
                            },
                        )
                    }
                },
            )
        }.toString()

    /** [servers]' names, for the `initialize` request's `sdkMcpServers`. */
    fun names(servers: List<HostToolServer>): JsonArray = JsonArray(servers.map { JsonPrimitive(it.name) })

    private const val DEFAULT_PROTOCOL = "2025-06-18"
}
