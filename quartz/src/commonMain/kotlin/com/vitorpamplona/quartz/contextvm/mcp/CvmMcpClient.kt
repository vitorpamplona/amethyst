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
package com.vitorpamplona.quartz.contextvm.mcp

import com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer.OversizedFrame
import com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer.OversizedLimits
import com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer.OversizedProgressResult
import com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer.OversizedTransferReceiver
import com.vitorpamplona.quartz.contextvm.cep41OpenStreams.OpenStreamEvent
import com.vitorpamplona.quartz.contextvm.cep41OpenStreams.OpenStreamFrame
import com.vitorpamplona.quartz.contextvm.cep41OpenStreams.OpenStreamPolicy
import com.vitorpamplona.quartz.contextvm.cep41OpenStreams.OpenStreamReceiver
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFailure
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.contextvm.transfer.ProgressEnvelope
import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.contextvm.transport.TimeoutMode
import com.vitorpamplona.quartz.nip01Core.core.Tag
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A tool call's outcome, with anything a transfer profile delivered alongside it. */
data class ToolCallResult(
    val result: JsonElement?,
    val error: com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcError? = null,
    /** Fragments a CEP-41 stream delivered while the call was in flight. */
    val streamed: List<String> = emptyList(),
    /**
     * Whether the response arrived reassembled over CEP-22 rather than in the
     * single event that carried the rest.
     *
     * A caller does not need this to read the result - that is the point of the
     * profile. It is here so a live interop run can assert the profile actually
     * fired, instead of passing whether or not the server ever chunked.
     */
    val viaOversizedTransfer: Boolean = false,
) {
    val isError get() = error != null
}

/**
 * A minimal MCP client over ContextVM.
 *
 * Implements the surface the CEPs define — lifecycle, tool listing and calling —
 * rather than all of MCP. Sampling, roots and elicitation are deliberately out
 * of scope; nothing in ContextVM or its CEPs needs them, and a partial version
 * would be worse than none.
 *
 * Transfer profiles are handled here because they are request-scoped: a call
 * carries a `progressToken`, and CEP-22 or CEP-41 frames for that token arrive
 * as notifications while the call is open.
 */
class CvmMcpClient(
    private val transport: CvmTransport,
    private val clientName: String = "amethyst-contextvm",
    private val clientVersion: String = "0.1.0",
    private val oversizedLimits: OversizedLimits = OversizedLimits(),
    private val streamPolicy: OpenStreamPolicy = OpenStreamPolicy(),
) {
    private var nextId = 0L

    /**
     * Performs the MCP handshake.
     *
     * Optional per the spec — servers may operate statelessly — but it is the
     * natural place to exchange CEP-35 discovery tags, so a client that can
     * afford the round trip should do it.
     */
    suspend fun initialize(
        capabilityTags: List<Tag> = transport.selfDiscoveryTags(),
        protocolVersion: String = PROTOCOL_VERSION,
    ): JsonRpcMessage {
        val response =
            transport.request(
                message =
                    JsonRpcRequest(
                        id = nextId(),
                        method = McpMethods.INITIALIZE,
                        params =
                            buildJsonObject {
                                put("protocolVersion", JsonPrimitive(protocolVersion))
                                put("capabilities", buildJsonObject {})
                                put(
                                    "clientInfo",
                                    buildJsonObject {
                                        put("name", JsonPrimitive(clientName))
                                        put("version", JsonPrimitive(clientVersion))
                                    },
                                )
                            },
                    ),
                discoveryTags = capabilityTags,
            )

        // The server is only allowed to assume readiness after this.
        transport.notify(JsonRpcNotification(McpMethods.INITIALIZED))
        return response
    }

    suspend fun listTools(cursor: String? = null): JsonRpcMessage =
        transport.request(
            JsonRpcRequest(
                id = nextId(),
                method = McpMethods.TOOLS_LIST,
                params = cursor?.let { buildJsonObject { put("cursor", JsonPrimitive(it)) } },
            ),
        )

    /**
     * Calls a tool.
     *
     * A `progressToken` is always attached: without it a server MUST NOT start
     * either transfer profile, so omitting it would silently cap every response
     * at one relay event.
     */
    suspend fun callTool(
        name: String,
        arguments: JsonObject = buildJsonObject {},
        identity: DualSigner.Identity = DualSigner.Identity.EPHEMERAL,
        timeoutMs: Long = CvmTransport.DEFAULT_TIMEOUT_MS,
        timeoutMode: TimeoutMode = TimeoutMode.IDLE,
        onStreamFragment: (String) -> Unit = {},
    ): ToolCallResult {
        val id = nextId()
        val token = ProgressToken.Text("call-${id.value}")

        var oversized: OversizedTransferReceiver? = null
        var stream: OpenStreamReceiver? = null
        var reassembled: JsonRpcMessage? = null
        val streamed = mutableListOf<String>()

        val response =
            transport.request(
                message =
                    JsonRpcRequest(
                        id = id,
                        method = McpMethods.TOOLS_CALL,
                        params =
                            buildJsonObject {
                                put(McpParams.NAME, JsonPrimitive(name))
                                put(McpParams.ARGUMENTS, arguments)
                                put(
                                    McpParams.META,
                                    buildJsonObject {
                                        put(McpParams.PROGRESS_TOKEN, JsonPrimitive("call-${id.value}"))
                                    },
                                )
                            },
                    ),
                identity = identity,
                timeoutMs = timeoutMs,
                timeoutMode = timeoutMode,
            ) { notification ->
                val envelope = ProgressEnvelope.parseOrNull(notification) ?: return@request null
                if (envelope.token != token) return@request null

                when (envelope.type) {
                    ProgressEnvelope.TYPE_OVERSIZED -> {
                        val receiver =
                            oversized ?: OversizedTransferReceiver(token, oversizedLimits, requireAccept = false)
                                .also { oversized = it }
                        OversizedFrame.parseOrNull(envelope)?.let { frame ->
                            val result = receiver.accept(frame)
                            if (result is OversizedProgressResult.Completed) {
                                reassembled = result.message
                                oversizedTransfersCompleted++
                            }
                        }
                    }

                    ProgressEnvelope.TYPE_OPEN_STREAM -> {
                        val receiver =
                            stream ?: OpenStreamReceiver(token, streamPolicy, requireAccept = false)
                                .also { stream = it }
                        OpenStreamFrame.parseOrNull(envelope)?.let { frame ->
                            val event = receiver.accept(frame)
                            if (event is OpenStreamEvent.Delivered) {
                                streamed += event.fragments
                                event.fragments.forEach(onStreamFragment)
                            }
                        }
                    }

                    else -> Unit
                }

                // A completed CEP-22 transfer IS the response, so handing it
                // back ends the call. A CEP-41 stream never does: `close` says
                // no more frames, not that the request is answered.
                reassembled
            }

        // A CEP-22 transfer replaces the response that could not be published
        // directly; a CEP-41 stream does not, since `close` never completes the
        // JSON-RPC request.
        val chunked = reassembled != null
        return when (val effective = reassembled ?: response) {
            is JsonRpcSuccess -> ToolCallResult(effective.result, streamed = streamed, viaOversizedTransfer = chunked)
            is JsonRpcFailure -> ToolCallResult(null, effective.error, streamed, chunked)
            else -> ToolCallResult(null, streamed = streamed, viaOversizedTransfer = chunked)
        }
    }

    private fun nextId(): JsonRpcId.Num = JsonRpcId.Num(nextId++)

    /**
     * How many responses this client has reassembled over CEP-22.
     *
     * Diagnostics, not control flow. Nothing decides anything on it; it exists
     * so a live run can tell a server that chunked from one that never had to.
     */
    var oversizedTransfersCompleted: Int = 0
        private set

    companion object {
        /** The MCP revision the ContextVM spec's examples use. */
        const val PROTOCOL_VERSION = "2025-07-02"
    }
}
