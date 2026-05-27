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
package com.vitorpamplona.quartz.nip60Cashu.mintApi.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val json = Json { ignoreUnknownKeys = true }

class NutSeventeenMessagesTest {
    @Test
    fun subscribeRequestRoundTrips() {
        val req =
            WsRequest(
                method = "subscribe",
                params =
                    WsRequestParams(
                        kind = NutSeventeenKinds.BOLT11_MINT_QUOTE,
                        filters = listOf("quote-xyz"),
                        subId = "sub-1",
                    ),
                id = 0L,
            )
        val text = json.encodeToString(WsRequest.serializer(), req)
        val parsed = json.decodeFromString(WsRequest.serializer(), text)
        assertEquals(req, parsed)
        // Make sure the on-wire shape has the expected literal fields —
        // mints will reject anything that doesn't say jsonrpc="2.0".
        val tree = json.parseToJsonElement(text).jsonObject
        assertEquals("2.0", tree["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("subscribe", tree["method"]?.jsonPrimitive?.content)
    }

    @Test
    fun unsubscribeRequestOmitsKindAndFilters() {
        val req =
            WsRequest(
                method = "unsubscribe",
                params = WsRequestParams(subId = "sub-1"),
                id = 1L,
            )
        val text = json.encodeToString(WsRequest.serializer(), req)
        val tree = json.parseToJsonElement(text).jsonObject
        // params.kind / filters MUST be absent (or null) for unsubscribe —
        // some mints reject ambiguous combinations.
        val params = tree["params"]?.jsonObject
        assertNotNull(params)
        // kotlinx.serialization defaults to omitting nulls; verify that.
        assertNull(params["kind"], "kind should be omitted on unsubscribe")
        assertNull(params["filters"], "filters should be omitted on unsubscribe")
        assertEquals("sub-1", params["subId"]?.jsonPrimitive?.content)
    }

    @Test
    fun mintQuoteNotificationDeserializes() {
        val raw =
            """
            {
              "jsonrpc": "2.0",
              "method": "subscribe",
              "params": {
                "subId": "sub-1",
                "payload": {
                  "quote": "quote-xyz",
                  "request": "lnbc100n1pj...",
                  "state": "PAID"
                }
              }
            }
            """.trimIndent()
        val notif = json.decodeFromString(WsNotification.serializer(), raw)
        assertEquals("subscribe", notif.method)
        assertEquals("sub-1", notif.params.subId)
        val payload = notif.params.payload.jsonObject
        assertEquals("quote-xyz", payload["quote"]?.jsonPrimitive?.content)
        assertEquals("PAID", payload["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun proofStateNotificationDeserializes() {
        val raw =
            """
            {
              "jsonrpc": "2.0",
              "method": "subscribe",
              "params": {
                "subId": "sub-2",
                "payload": {
                  "Y": "02abc...",
                  "state": "SPENT",
                  "witness": "{\"signatures\":[\"abc\"]}"
                }
              }
            }
            """.trimIndent()
        val notif = json.decodeFromString(WsNotification.serializer(), raw)
        val proofState =
            json.decodeFromJsonElement(
                ProofStateNotificationDto.serializer(),
                notif.params.payload,
            )
        assertEquals("02abc...", proofState.y)
        assertEquals("SPENT", proofState.state)
        assertEquals("{\"signatures\":[\"abc\"]}", proofState.witness)
    }

    @Test
    fun responseWithoutErrorRoundTrips() {
        val raw =
            """{"jsonrpc":"2.0","result":{"status":"OK","subId":"sub-1"},"id":0}"""
        val resp = json.decodeFromString(WsResponse.serializer(), raw)
        assertEquals(0L, resp.id)
        assertNull(resp.error)
        val result = resp.result?.jsonObject
        assertNotNull(result)
        assertEquals("OK", result["status"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun responseWithErrorRoundTrips() {
        val raw =
            """{"jsonrpc":"2.0","error":{"code":-32600,"message":"Invalid request"},"id":7}"""
        val resp = json.decodeFromString(WsResponse.serializer(), raw)
        assertEquals(7L, resp.id)
        assertNull(resp.result)
        assertEquals(-32600, resp.error?.code)
        assertEquals("Invalid request", resp.error?.message)
    }

    @Test
    fun kindConstantsMatchSpecExactly() {
        // The wire format is normative — any rename here would break
        // interop with every mint. Belt-and-braces against accidental edits.
        assertEquals("bolt11_mint_quote", NutSeventeenKinds.BOLT11_MINT_QUOTE)
        assertEquals("bolt11_melt_quote", NutSeventeenKinds.BOLT11_MELT_QUOTE)
        assertEquals("bolt12_mint_quote", NutSeventeenKinds.BOLT12_MINT_QUOTE)
        assertEquals("bolt12_melt_quote", NutSeventeenKinds.BOLT12_MELT_QUOTE)
        assertEquals("proof_state", NutSeventeenKinds.PROOF_STATE)
    }

    @Test
    fun extraFieldsOnIncomingPayloadAreIgnored() {
        // Forwards compat: a newer mint can add fields without breaking us.
        val raw =
            """
            {
              "jsonrpc": "2.0",
              "method": "subscribe",
              "params": {
                "subId": "sub-1",
                "payload": {
                  "quote": "q",
                  "state": "PAID",
                  "future_field": "ignored",
                  "another": 42
                }
              }
            }
            """.trimIndent()
        val notif = json.decodeFromString(WsNotification.serializer(), raw)
        assertEquals("sub-1", notif.params.subId)
        val payload = notif.params.payload.jsonObject
        // Original fields still present; new fields silently kept around.
        assertEquals("q", payload["quote"]?.jsonPrimitive?.content)
        assertEquals("PAID", payload["state"]?.jsonPrimitive?.content)
        assertNotNull(payload["future_field"])
    }

    @Test
    fun encodingProducesCompactSubscribeRequest() {
        // Reference shape from NUT-17 §4 example — minus pretty-printing.
        val req =
            WsRequest(
                method = "subscribe",
                params =
                    WsRequestParams(
                        kind = NutSeventeenKinds.PROOF_STATE,
                        filters = listOf("0234abcd"),
                        subId = "subA",
                    ),
                id = 0L,
            )
        val text = json.encodeToString(WsRequest.serializer(), req)
        // Field order is implementation-defined by kotlinx.serialization but
        // is stable; just check the discriminator + ID land. Mints don't
        // care about key order.
        val tree = json.parseToJsonElement(text).jsonObject
        val params = tree["params"]?.jsonObject
        assertNotNull(params)
        assertEquals("proof_state", params["kind"]?.jsonPrimitive?.content)
        assertEquals(0L, tree["id"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun jsonObjectCanBeBuiltManuallyForTopicSpecificPayloads() {
        // Sanity check: a hand-built JsonObject payload is acceptable input
        // — useful for tests that need to spoof mint notifications without
        // first serializing/deserializing a full DTO.
        val payload =
            buildJsonObject {
                put("Y", JsonPrimitive("02ff"))
                put("state", JsonPrimitive("UNSPENT"))
            }
        val proofState =
            json.decodeFromJsonElement(ProofStateNotificationDto.serializer(), payload)
        assertEquals("02ff", proofState.y)
        assertEquals("UNSPENT", proofState.state)
        assertNull(proofState.witness)
    }

    private fun JsonObject.containsField(name: String): Boolean = this.containsKey(name)
}
