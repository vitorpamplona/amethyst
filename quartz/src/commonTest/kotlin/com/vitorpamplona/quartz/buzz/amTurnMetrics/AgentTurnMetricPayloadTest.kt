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
package com.vitorpamplona.quartz.buzz.amTurnMetrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTurnMetricPayloadTest {
    @Test
    fun decodesPricingIdentityWithCamelCaseFields() {
        val payload =
            AgentTurnMetricPayload.decodeFromJson(
                """{"harness":"goose","timestamp":"2026-09-01T00:00:00Z","model":"sonnet",""" +
                    """"pricingIdentity":{"authority":"api.anthropic.com","model":"claude-sonnet-4-5","cacheClass":"ephemeral"}}""",
            )
        val identity = payload.pricingIdentity!!
        assertEquals("api.anthropic.com", identity.authority)
        assertEquals("claude-sonnet-4-5", identity.model)
        assertEquals("ephemeral", identity.cacheClass)
        assertTrue(identity.isRegisteredAuthority())
        // The session model keeps its own, non-billing meaning.
        assertEquals("sonnet", payload.model)
    }

    @Test
    fun omittedPricingIdentityMeansPriceUnknown() {
        val payload = AgentTurnMetricPayload.decodeFromJson("""{"harness":"goose","timestamp":"t"}""")
        assertNull(payload.pricingIdentity)
        // Never serialized as null either.
        assertFalse(payload.encodeToJson().contains("pricingIdentity"))
    }

    @Test
    fun cacheClassIsOmittedNotNullWhenAbsent() {
        val payload = AgentTurnMetricPayload(harness = "h", timestamp = "t", pricingIdentity = PricingIdentity("openrouter.ai", "x/y"))
        val json = payload.encodeToJson()
        assertTrue(json.contains(""""pricingIdentity":{"authority":"openrouter.ai","model":"x/y"}"""), json)
        assertEquals(payload, AgentTurnMetricPayload.decodeFromJson(json))
    }

    /** Like serde on the Rust side: a present identity without its required strings fails the payload. */
    @Test
    fun pricingIdentityRequiresAuthorityAndModel() {
        assertFailsWith<Exception> {
            AgentTurnMetricPayload.decodeFromJson("""{"harness":"h","timestamp":"t","pricingIdentity":{"model":"m"}}""")
        }
    }

    /** The Rust parser accepts any authority string; only the registered set is priceable. */
    @Test
    fun unregisteredAuthorityParsesButIsFlagged() {
        val identity = PricingIdentity("https://api.anthropic.com/", "m")
        assertFalse(identity.isRegisteredAuthority())
        assertTrue(PricingIdentity("api.openai.com", "gpt").isRegisteredAuthority())
    }

    @Test
    fun explicitZeroCacheIsKeptAndOmittedIsNull() {
        val payload =
            AgentTurnMetricPayload.decodeFromJson(
                """{"harness":"h","timestamp":"t","turn":{"inputTokens":10,"cacheReadTokens":0}}""",
            )
        assertEquals(0L, payload.turn?.cacheReadTokens)
        assertNull(payload.turn?.cacheWriteTokens)
    }
}
