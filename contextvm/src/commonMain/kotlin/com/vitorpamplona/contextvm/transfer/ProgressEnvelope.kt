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
package com.vitorpamplona.contextvm.transfer

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.contextvm.mcp.McpMethods
import com.vitorpamplona.contextvm.mcp.McpParams
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * An MCP progress token: the transfer/stream identifier for CEP-22 and CEP-41.
 *
 * MCP allows a string or a number. The distinction is kept rather than
 * normalised for the same reason as `JsonRpcId`: a faithful round trip.
 */
sealed interface ProgressToken {
    data class Num(
        val value: Long,
    ) : ProgressToken

    data class Text(
        val value: String,
    ) : ProgressToken
}

/** Thrown when a `notifications/progress` payload is not a usable transfer frame. */
class TransferFrameException(
    message: String,
) : IllegalArgumentException(message)

/**
 * The `notifications/progress` envelope shared by CEP-22 and CEP-41.
 *
 * MCP owns `progressToken`, `progress`, `total` and `message`; ContextVM adds
 * the `cvm` object carrying the frame. `total` and `message` are UX hints and
 * explicitly do not define transfer correctness, so nothing below reads them
 * for control flow.
 */
data class ProgressEnvelope(
    val token: ProgressToken,
    val progress: Double,
    val total: Double? = null,
    val message: String? = null,
    val cvm: JsonObject,
) {
    val type: String? get() = cvm[TYPE]?.asStringOrNull()
    val frameType: String? get() = cvm[FRAME_TYPE]?.asStringOrNull()

    fun toNotification(): JsonRpcNotification =
        JsonRpcNotification(
            McpMethods.PROGRESS,
            buildJsonObject {
                put(McpParams.PROGRESS_TOKEN, token.toPrimitive())
                put(PROGRESS, JsonPrimitive(progress))
                total?.let { put(TOTAL, JsonPrimitive(it)) }
                message?.let { put(MESSAGE, JsonPrimitive(it)) }
                put(CVM, cvm)
            },
        )

    companion object {
        const val PROGRESS = "progress"
        const val TOTAL = "total"
        const val MESSAGE = "message"
        const val CVM = "cvm"
        const val TYPE = "type"
        const val FRAME_TYPE = "frameType"

        /** CEP-22's `cvm.type`. */
        const val TYPE_OVERSIZED = "oversized-transfer"

        /** CEP-41's `cvm.type`. */
        const val TYPE_OPEN_STREAM = "open-stream"

        /**
         * Reads the envelope out of a notification, or returns null when this is
         * an ordinary MCP progress notification rather than a ContextVM frame.
         *
         * Returning null rather than throwing is deliberate: a peer may send
         * plain MCP progress for the same request, and that is not an error.
         */
        fun parseOrNull(notification: JsonRpcNotification): ProgressEnvelope? {
            if (notification.method != McpMethods.PROGRESS) return null
            val params = notification.params ?: return null
            val cvm = params[CVM] as? JsonObject ?: return null

            val token = params[McpParams.PROGRESS_TOKEN]?.let { parseToken(it) } ?: return null
            val progress =
                (params[PROGRESS] as? JsonPrimitive)?.doubleOrNull
                    ?: throw TransferFrameException("progress must be a number")

            return ProgressEnvelope(
                token = token,
                progress = progress,
                total = (params[TOTAL] as? JsonPrimitive)?.doubleOrNull,
                message = params[MESSAGE]?.asStringOrNull(),
                cvm = cvm,
            )
        }

        private fun parseToken(element: kotlinx.serialization.json.JsonElement): ProgressToken? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive.isString) return ProgressToken.Text(primitive.content)
            return primitive.longOrNull?.let { ProgressToken.Num(it) }
        }

        internal fun ProgressToken.toPrimitive() =
            when (this) {
                is ProgressToken.Num -> JsonPrimitive(value)
                is ProgressToken.Text -> JsonPrimitive(value)
            }

        internal fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? {
            val primitive = this as? JsonPrimitive ?: return null
            return if (primitive.isString) primitive.content else null
        }

        internal fun kotlinx.serialization.json.JsonElement.asLongOrNull(): Long? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
    }
}
