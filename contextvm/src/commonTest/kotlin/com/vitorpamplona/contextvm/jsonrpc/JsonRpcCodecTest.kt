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

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `CVM-CORE-01`: the JSON-RPC layer round-trips every message class faithfully,
 * and rejects payloads that are not well-formed JSON-RPC 2.0.
 *
 * Rule ids come from `quartz/plans/2026-09-17-cordn-interop.md` §6.5. When a CEP
 * revises, the failing test names say what changed.
 */
class JsonRpcCodecTest {
    private val toolsCall =
        JsonRpcRequest(
            id = JsonRpcId.Num(2),
            method = "tools/call",
            params =
                buildJsonObject {
                    put("name", JsonPrimitive("kp_publish"))
                    put(
                        "arguments",
                        buildJsonObject {
                            put("kp_ref", JsonPrimitive("abc"))
                            put("kp_64", JsonPrimitive("BASE64"))
                        },
                    )
                },
        )

    @Test
    fun `CVM-CORE-01 round-trips a request`() {
        assertEquals(toolsCall, JsonRpcCodec.decode(JsonRpcCodec.encode(toolsCall)))
    }

    @Test
    fun `CVM-CORE-01 round-trips a notification`() {
        val notification =
            JsonRpcNotification(
                method = "notifications/progress",
                params =
                    buildJsonObject {
                        put("progressToken", JsonPrimitive("req-123"))
                        put("progress", JsonPrimitive(1))
                    },
            )
        assertEquals(notification, JsonRpcCodec.decode(JsonRpcCodec.encode(notification)))
    }

    @Test
    fun `CVM-CORE-01 round-trips a success response`() {
        val success =
            JsonRpcSuccess(
                id = JsonRpcId.Num(2),
                result = buildJsonObject { put("cursor", JsonPrimitive(7)) },
            )
        assertEquals(success, JsonRpcCodec.decode(JsonRpcCodec.encode(success)))
    }

    @Test
    fun `CVM-CORE-01 round-trips an error response with data`() {
        val failure =
            JsonRpcFailure(
                id = JsonRpcId.Num(2),
                error =
                    JsonRpcError(
                        code = JsonRpcError.PAYMENT_REQUIRED,
                        message = "Payment Required",
                        data = buildJsonObject { put("instructions", JsonPrimitive("pay then retry")) },
                    ),
            )
        assertEquals(failure, JsonRpcCodec.decode(JsonRpcCodec.encode(failure)))
    }

    @Test
    fun `CVM-CORE-01 preserves a string id distinctly from a numeric one`() {
        // MCP implementations use both. Normalising to String would make a
        // string "2" and a numeric 2 indistinguishable on the wire, so the two
        // must stay separate types through a round trip.
        val text = toolsCall.copy(id = JsonRpcId.Text("2"))
        val encodedText = JsonRpcCodec.encode(text)
        val encodedNum = JsonRpcCodec.encode(toolsCall)

        assertTrue(encodedText.contains("\"id\":\"2\""), "string id must encode quoted: $encodedText")
        assertTrue(encodedNum.contains("\"id\":2"), "numeric id must encode bare: $encodedNum")
        assertEquals(text, JsonRpcCodec.decode(encodedText))
        assertEquals(toolsCall, JsonRpcCodec.decode(encodedNum))
    }

    @Test
    fun `CVM-CORE-01 omits absent params rather than emitting null`() {
        val encoded = JsonRpcCodec.encode(JsonRpcNotification("notifications/initialized"))
        assertEquals("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", encoded)
    }

    @Test
    fun `CVM-CORE-01 keeps an unknown member out of the way of decoding`() {
        // MCP grows fields and CEP-35 tells us to tolerate what we do not know.
        val decoded =
            JsonRpcCodec.decode(
                """{"jsonrpc":"2.0","id":1,"method":"ping","futureField":{"x":1}}""",
            )
        assertEquals(JsonRpcRequest(JsonRpcId.Num(1), "ping"), decoded)
    }

    @Test
    fun `CVM-CORE-01 accepts a null id on an error response`() {
        // JSON-RPC allows a null id when the request could not be parsed.
        val decoded =
            JsonRpcCodec.decode(
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}""",
            )
        assertTrue(decoded is JsonRpcFailure)
        assertNull(decoded.id)
        assertEquals(JsonRpcError.PARSE_ERROR, decoded.error.code)
    }

    @Test
    fun `CVM-CORE-01 treats a method without an id as a notification`() {
        val decoded = JsonRpcCodec.decode("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertEquals(JsonRpcNotification("notifications/initialized"), decoded)
    }

    @Test
    fun `CVM-CORE-01 preserves the params tree untouched`() {
        // ContextVM transports MCP unmodified, so anything inside params has to
        // survive verbatim -- including nesting we assign no meaning to.
        val decoded = JsonRpcCodec.decode(JsonRpcCodec.encode(toolsCall)) as JsonRpcRequest
        val arguments = decoded.params!!["arguments"]!!.jsonObject
        assertEquals("BASE64", arguments["kp_64"]!!.jsonPrimitive.content)
    }

    // --- rejections: CVM-CORE-05 ---

    @Test
    fun `CVM-CORE-05 rejects a wrong jsonrpc version`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"1.0","id":1,"method":"ping"}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a missing jsonrpc member`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"id":1,"method":"ping"}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a response carrying both result and error`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode(
                """{"jsonrpc":"2.0","id":1,"result":{},"error":{"code":-1,"message":"x"}}""",
            )
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a message with neither method nor result nor error`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a method combined with a response body`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1,"method":"ping","result":{}}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a success response without an id`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","result":{}}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects a non-object payload`() {
        assertFailsWith<JsonRpcFormatException> { JsonRpcCodec.decode("""["jsonrpc","2.0"]""") }
    }

    @Test
    fun `CVM-CORE-05 rejects malformed JSON`() {
        assertFailsWith<JsonRpcFormatException> { JsonRpcCodec.decode("""{"jsonrpc":"2.0",""") }
    }

    @Test
    fun `CVM-CORE-05 rejects a fractional id`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1.5,"method":"ping"}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects non-object params`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1,"method":"ping","params":[1,2]}""")
        }
    }

    @Test
    fun `CVM-CORE-05 rejects an error object missing its code`() {
        assertFailsWith<JsonRpcFormatException> {
            JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1,"error":{"message":"x"}}""")
        }
    }
}
