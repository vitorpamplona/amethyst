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
package com.vitorpamplona.contextvm.jsonrpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Encodes and decodes the JSON-RPC 2.0 messages carried in a ContextVM event's
 * `content`.
 *
 * Decoding is deliberately strict. ContextVM's only framing is "the content is a
 * JSON-RPC message", so a malformed payload has to be rejected here or it becomes
 * a confusing failure several layers up. Every rejection below is a rule in
 * `quartz/plans/2026-09-17-cordn-interop.md` §6.5 (`CVM-CORE-*`) and has a test.
 */
object JsonRpcCodec {
    private const val JSONRPC = "jsonrpc"
    private const val ID = "id"
    private const val METHOD = "method"
    private const val PARAMS = "params"
    private const val RESULT = "result"
    private const val ERROR = "error"
    private const val CODE = "code"
    private const val MESSAGE = "message"
    private const val DATA = "data"

    /**
     * Lenient only about *unknown* members: MCP grows fields, and CEP-35 tells us
     * to preserve what we do not understand rather than fail. The structural
     * checks in [decode] are not relaxed.
     */
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(message: JsonRpcMessage): String = json.encodeToString(JsonObject.serializer(), toJsonObject(message))

    fun toJsonObject(message: JsonRpcMessage): JsonObject =
        buildJsonObject {
            put(JSONRPC, JsonPrimitive(JsonRpcMessage.VERSION))
            when (message) {
                is JsonRpcRequest -> {
                    put(ID, message.id.toPrimitive())
                    put(METHOD, JsonPrimitive(message.method))
                    message.params?.let { put(PARAMS, it) }
                }

                is JsonRpcNotification -> {
                    put(METHOD, JsonPrimitive(message.method))
                    message.params?.let { put(PARAMS, it) }
                }

                is JsonRpcSuccess -> {
                    put(ID, message.id.toPrimitive())
                    put(RESULT, message.result)
                }

                is JsonRpcFailure -> {
                    put(ID, message.id?.toPrimitive() ?: JsonPrimitive(null as String?))
                    put(
                        ERROR,
                        buildJsonObject {
                            put(CODE, JsonPrimitive(message.error.code))
                            put(MESSAGE, JsonPrimitive(message.error.message))
                            message.error.data?.let { put(DATA, it) }
                        },
                    )
                }
            }
        }

    fun decode(text: String): JsonRpcMessage {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                throw JsonRpcFormatException("content is not valid JSON: ${e.message}")
            }

        if (root !is JsonObject) throw JsonRpcFormatException("JSON-RPC message must be an object")
        return decode(root)
    }

    fun decode(root: JsonObject): JsonRpcMessage {
        val version = root[JSONRPC]?.asStringOrNull()
        if (version != JsonRpcMessage.VERSION) {
            throw JsonRpcFormatException("unsupported jsonrpc version: $version")
        }

        val hasMethod = root.containsKey(METHOD)
        val hasResult = root.containsKey(RESULT)
        val hasError = root.containsKey(ERROR)

        // A response is exactly one of result or error. Carrying both is
        // ambiguous about whether the call succeeded, so it cannot be repaired
        // by preferring one -- reject it.
        if (hasResult && hasError) {
            throw JsonRpcFormatException("response carries both result and error")
        }
        if (hasMethod && (hasResult || hasError)) {
            throw JsonRpcFormatException("message carries both a method and a response body")
        }

        // `id` may legitimately be JSON null on a failure, so distinguish
        // "absent" (a notification) from "present but null".
        val idElement = root[ID]
        val id = if (idElement == null || idElement.isJsonNull()) null else parseId(idElement)

        return when {
            hasMethod -> {
                val method =
                    root[METHOD]?.asStringOrNull()
                        ?: throw JsonRpcFormatException("method must be a string")
                val params = root[PARAMS]?.let { requireObject(it, PARAMS) }
                if (id == null) {
                    JsonRpcNotification(method, params)
                } else {
                    JsonRpcRequest(id, method, params)
                }
            }

            hasResult -> {
                if (id == null) throw JsonRpcFormatException("success response requires an id")
                JsonRpcSuccess(id, root.getValue(RESULT))
            }

            hasError -> JsonRpcFailure(id, parseError(root.getValue(ERROR)))

            else -> throw JsonRpcFormatException("message has no method, result or error")
        }
    }

    private fun parseError(element: JsonElement): JsonRpcError {
        val obj = requireObject(element, ERROR)
        val code =
            obj[CODE]?.jsonPrimitive?.longOrNull
                ?: throw JsonRpcFormatException("error.code must be a number")
        val message =
            obj[MESSAGE]?.asStringOrNull()
                ?: throw JsonRpcFormatException("error.message must be a string")
        return JsonRpcError(code.toInt(), message, obj[DATA])
    }

    private fun parseId(element: JsonElement): JsonRpcId {
        val primitive =
            (element as? JsonPrimitive)
                ?: throw JsonRpcFormatException("id must be a string or a number")

        if (primitive.isString) return JsonRpcId.Text(primitive.content)

        return primitive.longOrNull?.let { JsonRpcId.Num(it) }
            ?: throw JsonRpcFormatException("id must be a string or an integral number")
    }

    private fun requireObject(
        element: JsonElement,
        field: String,
    ): JsonObject =
        element as? JsonObject
            ?: throw JsonRpcFormatException("$field must be an object")

    private fun JsonRpcId.toPrimitive() =
        when (this) {
            is JsonRpcId.Num -> JsonPrimitive(value)
            is JsonRpcId.Text -> JsonPrimitive(value)
        }

    private fun JsonElement.isJsonNull() = this is JsonPrimitive && !isString && content == "null"

    private fun JsonElement.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }
}
