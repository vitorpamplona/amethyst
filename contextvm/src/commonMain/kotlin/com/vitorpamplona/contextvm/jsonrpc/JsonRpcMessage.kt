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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A JSON-RPC 2.0 message, as carried in a ContextVM event's `content`.
 *
 * ContextVM transports MCP messages unmodified, so this models JSON-RPC itself
 * rather than any MCP-specific shape: `params` and `result` stay as a JSON tree
 * and pass through untouched. MCP semantics live a layer up.
 */
sealed interface JsonRpcMessage {
    companion object {
        /** The only version ContextVM carries. Decoding rejects anything else. */
        const val VERSION = "2.0"
    }
}

/**
 * A JSON-RPC message id.
 *
 * JSON-RPC allows a string or a number, and MCP implementations use both — the
 * TypeScript SDK numbers its requests while others use strings. Modelling the
 * distinction (rather than normalising to String) keeps decode/encode a faithful
 * round-trip, which rule `CVM-CORE-01` asserts.
 *
 * It also matters for CEP-8: the canonical invocation identity deliberately
 * excludes the id, so a retry may legitimately change both its type and value
 * and still match a paid authorization.
 */
sealed interface JsonRpcId {
    data class Num(
        val value: Long,
    ) : JsonRpcId

    data class Text(
        val value: String,
    ) : JsonRpcId
}

/** A call expecting exactly one matching [JsonRpcSuccess] or [JsonRpcFailure]. */
data class JsonRpcRequest(
    val id: JsonRpcId,
    val method: String,
    val params: JsonObject? = null,
) : JsonRpcMessage

/**
 * A one-way message with no id and no response.
 *
 * CEP-22 and CEP-41 both ride `notifications/progress` notifications, so this is
 * the carrier for every transfer frame as well as for ordinary MCP notifications.
 */
data class JsonRpcNotification(
    val method: String,
    val params: JsonObject? = null,
) : JsonRpcMessage

/** A successful response. `result` is opaque to this layer. */
data class JsonRpcSuccess(
    val id: JsonRpcId,
    val result: JsonElement,
) : JsonRpcMessage

/**
 * An error response.
 *
 * [id] is nullable because JSON-RPC permits a null id when the request could not
 * be parsed well enough to recover one.
 */
data class JsonRpcFailure(
    val id: JsonRpcId?,
    val error: JsonRpcError,
) : JsonRpcMessage

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
) {
    companion object {
        // JSON-RPC 2.0 reserved codes.
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603

        /** CEP-8 `explicit_gating`: payment is required before the call will run. */
        const val PAYMENT_REQUIRED = -32042

        /** CEP-8 `explicit_gating`: payment is in flight but not yet verified. */
        const val PAYMENT_PENDING = -32043
    }
}

/** Thrown when a payload is not a well-formed JSON-RPC 2.0 message. */
class JsonRpcFormatException(
    message: String,
) : IllegalArgumentException(message)
