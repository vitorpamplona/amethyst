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

import com.vitorpamplona.agents.LineTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Codex answered a request with an error. */
class CodexRpcException(
    val code: Long?,
    message: String,
) : IOException(message)

/**
 * JSON-RPC 2.0 with `codex app-server`, one JSON object per line both ways. Codex answers the
 * host's requests, sends notifications, and sends requests of its own (approvals) that the host
 * answers with [reply].
 */
class CodexRpc(
    private val transport: LineTransport,
    scope: CoroutineScope,
) {
    /** A notification, or a request Codex waits on, in the order they came. */
    sealed interface Incoming {
        val method: String
        val params: JsonObject

        data class Notification(
            override val method: String,
            override val params: JsonObject,
        ) : Incoming

        data class Request(
            val id: JsonElement,
            override val method: String,
            override val params: JsonObject,
        ) : Incoming
    }

    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val output = Channel<Incoming>(Channel.UNLIMITED)

    /** Everything Codex sends that isn't an answer. Collect it from one place. */
    val incoming: Flow<Incoming> = output.receiveAsFlow()

    /** Ends when stdout closes. */
    val reader: Job =
        scope.launch {
            try {
                transport.lines.collect { line -> read(line) }
            } finally {
                val ended = IOException("Codex ended")
                pending.values.forEach { it.completeExceptionally(ended) }
                pending.clear()
                output.close()
            }
        }

    private suspend fun read(line: String) {
        val obj =
            try {
                json.parseToJsonElement(line) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: return
        val method = (obj["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = obj["id"]
        val params = obj["params"] as? JsonObject ?: JsonObject(emptyMap())
        when {
            method != null && id != null -> output.send(Incoming.Request(id, method, params))
            method != null -> output.send(Incoming.Notification(method, params))
            id != null -> (id as? JsonPrimitive)?.longOrNull?.let { pending.remove(it)?.complete(obj) }
        }
    }

    /** Sends a request and waits for its result; throws [CodexRpcException] for an error, [IOException] when Codex ended or timed out. */
    suspend fun request(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Long = 60_000,
    ): JsonObject {
        val id = ids.incrementAndGet()
        val answer = CompletableDeferred<JsonObject>()
        pending[id] = answer
        try {
            write(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    put("params", params)
                },
            )
            val reply = withTimeoutOrNull(timeoutMs) { answer.await() } ?: throw IOException("Codex did not answer $method")
            (reply["error"] as? JsonObject)?.let { error ->
                throw CodexRpcException((error["code"] as? JsonPrimitive)?.longOrNull, (error["message"] as? JsonPrimitive)?.content ?: "$method failed")
            }
            return reply["result"] as? JsonObject ?: JsonObject(emptyMap())
        } finally {
            pending.remove(id)
        }
    }

    suspend fun notify(
        method: String,
        params: JsonObject? = null,
    ) = write(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            params?.let { put("params", it) }
        },
    )

    /** Answers one of Codex's own requests. */
    suspend fun reply(
        id: JsonElement,
        result: JsonObject,
    ) = write(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        },
    )

    /** Refuses one of Codex's own requests, so it never waits on an answer that won't come. */
    suspend fun replyError(
        id: JsonElement,
        message: String,
    ) = write(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put(
                "error",
                buildJsonObject {
                    put("code", -32601)
                    put("message", message)
                },
            )
        },
    )

    private suspend fun write(obj: JsonObject) = transport.write(obj.toString())

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
