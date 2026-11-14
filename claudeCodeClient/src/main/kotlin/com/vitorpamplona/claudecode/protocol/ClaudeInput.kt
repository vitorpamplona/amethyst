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
package com.vitorpamplona.claudecode.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The lines a host writes on Claude Code's stdin in `--input-format stream-json` mode. */
object ClaudeInput {
    /** A prompt typed by the user. */
    fun userText(text: String): JsonObject = userMessage(JsonPrimitive(text))

    /** A prompt with [images]: the images first, then the text, as the Messages API advises. */
    fun userText(
        text: String,
        images: List<ImageInput>,
    ): JsonObject {
        if (images.isEmpty()) return userText(text)
        val blocks =
            buildJsonArray {
                images.forEach { image ->
                    addJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", image.mediaType)
                            put("data", image.base64)
                        }
                    }
                }
                if (text.isNotBlank()) {
                    addJsonObject {
                        put("type", "text")
                        put("text", text)
                    }
                }
            }
        return userMessage(blocks)
    }

    private fun userMessage(content: JsonElement): JsonObject =
        buildJsonObject {
            put("type", "user")
            put(
                "message",
                buildJsonObject {
                    put("role", "user")
                    put("content", content)
                },
            )
            put("parent_tool_use_id", JsonNull)
        }

    /** A control request; [fields] go next to `subtype` inside `request`. */
    fun controlRequest(
        requestId: String,
        subtype: String,
        fields: Map<String, JsonElement> = emptyMap(),
    ): JsonObject =
        buildJsonObject {
            put("type", "control_request")
            put("request_id", requestId)
            put(
                "request",
                JsonObject(mapOf("subtype" to JsonPrimitive(subtype)) + fields),
            )
        }

    /** A successful answer to a control request the CLI sent. */
    fun controlSuccess(
        requestId: String,
        response: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("type", "control_response")
            put(
                "response",
                buildJsonObject {
                    put("subtype", "success")
                    put("request_id", requestId)
                    put("response", response)
                },
            )
        }

    /** A refusal of a control request the CLI sent (one this host doesn't handle). */
    fun controlError(
        requestId: String,
        error: String,
    ): JsonObject =
        buildJsonObject {
            put("type", "control_response")
            put(
                "response",
                buildJsonObject {
                    put("subtype", "error")
                    put("request_id", requestId)
                    put("error", error)
                },
            )
        }

    /** The answer to a `can_use_tool` request. */
    fun permissionResponse(
        request: PermissionRequest,
        decision: PermissionDecision,
    ): JsonObject =
        controlSuccess(
            request.requestId,
            when (decision) {
                is PermissionDecision.Allow ->
                    buildJsonObject {
                        put("behavior", "allow")
                        // Sent even when unchanged: older CLIs read the input to run from here.
                        put("updatedInput", decision.updatedInput ?: request.input)
                        decision.updatedPermissions?.takeIf { it.isNotEmpty() }?.let { put("updatedPermissions", it) }
                        request.toolUseId?.let { put("toolUseID", it) }
                    }
                is PermissionDecision.Deny ->
                    buildJsonObject {
                        put("behavior", "deny")
                        put("message", decision.message)
                        if (decision.interrupt) put("interrupt", true)
                        request.toolUseId?.let { put("toolUseID", it) }
                    }
            },
        )
}
