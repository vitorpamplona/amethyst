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
package com.vitorpamplona.claudecode.process

import com.vitorpamplona.claudecode.protocol.ClaudeInput
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.PermissionDecision
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.protocol.PermissionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One running Claude Code session over its stream-json pipe.
 *
 * Everything the CLI writes comes out of [messages], in order, including the permission prompts
 * ([ClaudeMessage.ControlRequest] with a [ClaudeMessage.ControlRequest.permission]) the host
 * answers with [respond]. Control requests this client doesn't handle (hooks, SDK MCP servers,
 * dialogs) are refused at once, so the CLI never waits on an answer that won't come.
 *
 * The process takes any number of turns: [send] a prompt, read until a
 * [ClaudeMessage.Result], send the next. [close] ends it.
 */
class ClaudeCodeConnection(
    private val transport: ClaudeTransport,
    scope: CoroutineScope,
) {
    private val output = Channel<ClaudeMessage>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ClaudeMessage.ControlResponse>>()
    private val openPrompts = ConcurrentHashMap<String, PermissionRequest>()
    private val ids = AtomicLong()

    /** Every message, once. Collect it from one place. */
    val messages: Flow<ClaudeMessage> = output.receiveAsFlow()

    /** Ends when stdout closes: the process exited or was killed. */
    val reader: Job =
        scope.launch {
            try {
                transport.lines.collect { line ->
                    val message = ClaudeMessage.parse(line) ?: return@collect
                    handle(message)
                    output.send(message)
                }
            } finally {
                pending.values.forEach { it.cancel() }
                pending.clear()
                output.close()
            }
        }

    private suspend fun handle(message: ClaudeMessage) {
        when (message) {
            is ClaudeMessage.ControlResponse -> pending.remove(message.requestId)?.complete(message)
            is ClaudeMessage.ControlCancel -> openPrompts.remove(message.requestId)
            is ClaudeMessage.ControlRequest -> {
                val permission = message.permission
                if (permission != null) {
                    openPrompts[message.requestId] = permission
                } else {
                    writeQuietly(ClaudeInput.controlError(message.requestId, "Not supported by this client: ${message.subtype}"))
                }
            }
            else -> {}
        }
    }

    /** The permission prompts still waiting on an answer. */
    fun openPermissionRequests(): List<PermissionRequest> = openPrompts.values.toList()

    /** Sends the handshake; the CLI answers with its commands, models and pending prompts. */
    suspend fun initialize(): ClaudeMessage.ControlResponse? = request("initialize")

    /** Starts a turn with [text], or queues it if a turn is running. */
    suspend fun send(text: String) = write(ClaudeInput.userText(text))

    /** Answers a permission prompt. */
    suspend fun respond(
        request: PermissionRequest,
        decision: PermissionDecision,
    ) {
        openPrompts.remove(request.requestId)
        write(ClaudeInput.permissionResponse(request, decision))
    }

    /** Stops the running turn. */
    suspend fun interrupt(): ClaudeMessage.ControlResponse? = request("interrupt")

    suspend fun setPermissionMode(mode: PermissionMode): ClaudeMessage.ControlResponse? = request("set_permission_mode", mapOf("mode" to JsonPrimitive(mode.wire)))

    /** Switches the model for the next turns; null goes back to the session's default. */
    suspend fun setModel(model: String?): ClaudeMessage.ControlResponse? = request("set_model", mapOf("model" to (model?.let { JsonPrimitive(it) } ?: JsonNull)))

    /** Names the session; the CLI writes it to the session file as its title. */
    suspend fun rename(title: String): ClaudeMessage.ControlResponse? =
        request(
            "rename_session",
            mapOf("title" to JsonPrimitive(title), "source" to JsonPrimitive("host")),
        )

    /**
     * Sends a control request and waits for its answer; null if the process ended or did not
     * answer within [timeoutMs].
     */
    suspend fun request(
        subtype: String,
        fields: Map<String, JsonElement> = emptyMap(),
        timeoutMs: Long = 30_000,
    ): ClaudeMessage.ControlResponse? {
        val id = "host-${ids.incrementAndGet()}"
        val answer = CompletableDeferred<ClaudeMessage.ControlResponse>()
        pending[id] = answer
        return try {
            write(ClaudeInput.controlRequest(id, subtype, fields))
            withTimeoutOrNull(timeoutMs) { answer.await() }
        } catch (e: IOException) {
            null
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun write(obj: JsonObject) = transport.write(obj.toString())

    private suspend fun writeQuietly(obj: JsonObject) {
        try {
            write(obj)
        } catch (_: IOException) {
            // The process is gone; there is nobody left to answer.
        }
    }

    /** Ends the session after the running turn: closes stdin and waits for the process. */
    suspend fun close(): Int? {
        transport.closeInput()
        return transport.awaitExit()
    }

    /** Stops the process now. */
    fun kill() = transport.kill()

    fun stderrTail(): String = transport.stderrTail()
}
