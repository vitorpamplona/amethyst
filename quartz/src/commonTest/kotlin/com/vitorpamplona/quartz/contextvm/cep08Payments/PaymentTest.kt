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
package com.vitorpamplona.quartz.contextvm.cep08Payments

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcError
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.nip01Core.core.Tag
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `CVM-8-*` and `CVM-21-*`: pricing, payment lifecycles and canonical identity. */
class PaymentTest {
    private val lightning = Pmi.LIGHTNING_BOLT11
    private val cashuDirect = Pmi("bitcoin-cashu-v4-direct")

    private fun params(json: String): JsonObject = (JsonRpcCodec.decode("""{"jsonrpc":"2.0","id":1,"method":"m","params":$json}""") as JsonRpcRequest).params!!

    private fun request(
        method: String,
        json: String,
    ) = JsonRpcRequest(JsonRpcId.Num(1), method, params(json))

    // --- CEP-21 PMI ---

    @Test
    fun `CVM-21-01 accepts the W3C PMI format and rejects anything else`() {
        assertEquals("bitcoin-lightning-bolt11", lightning.value)
        assertNull(Pmi.parseOrNull("Bitcoin-Lightning"))
        assertNull(Pmi.parseOrNull("bitcoin_lightning"))
        assertFailsWith<IllegalArgumentException> { Pmi("UPPER") }
    }

    @Test
    fun `CVM-21-02 detects the -direct bearer settlement suffix`() {
        assertTrue(cashuDirect.supportsDirectPayment)
        assertFalse(lightning.supportsDirectPayment)
    }

    // --- cap tag ---

    @Test
    fun `CVM-8-01 parses a fixed price`() {
        val tag = CapTag.parse(arrayOf("cap", "tool:get_weather", "100", "sats"))!!
        assertEquals(CapabilityKind.TOOL, tag.kind)
        assertEquals("get_weather", tag.name)
        assertEquals(Price.Fixed(100), tag.price)
        assertEquals("sats", tag.unit)
    }

    @Test
    fun `CVM-8-02 parses an inclusive range price`() {
        val tag = CapTag.parse(arrayOf("cap", "prompt:summarize", "100-1000", "sats"))!!
        assertEquals(CapabilityKind.PROMPT, tag.kind)
        assertEquals(Price.Range(100, 1000), tag.price)
        assertTrue(tag.price.includes(500))
        assertFalse(tag.price.includes(1001))
    }

    @Test
    fun `CVM-8-03 round-trips a cap tag`() {
        val tag = CapTag(CapabilityKind.RESOURCE, "file://x", Price.Range(1, 2), "usd")
        assertEquals(tag, CapTag.parse(tag.toTag()))
    }

    @Test
    fun `CVM-8-04 rejects a cap tag without a typed capability prefix`() {
        assertNull(CapTag.parse(arrayOf("cap", "get_weather", "100", "sats")))
    }

    // --- canonical invocation identity ---

    @Test
    fun `CVM-8-10 excludes _meta so a regenerated progressToken still matches`() {
        // MCP regenerates progressToken on every callTool. Without the exclusion
        // a retry could never match a paid authorization.
        val first =
            request(
                "tools/call",
                """{"name":"get_weather","arguments":{"location":"NY"},"_meta":{"progressToken":"a"}}""",
            )
        val retry =
            request(
                "tools/call",
                """{"name":"get_weather","arguments":{"location":"NY"},"_meta":{"progressToken":"b"}}""",
            )
        assertEquals(CanonicalInvocation.identityOf(first), CanonicalInvocation.identityOf(retry))
    }

    @Test
    fun `CVM-8-11 is unaffected by the JSON-RPC id`() {
        val a = JsonRpcRequest(JsonRpcId.Num(1), "tools/call", params("""{"name":"x"}"""))
        val b = JsonRpcRequest(JsonRpcId.Text("other"), "tools/call", params("""{"name":"x"}"""))
        assertEquals(CanonicalInvocation.identityOf(a), CanonicalInvocation.identityOf(b))
    }

    @Test
    fun `CVM-8-12 is unaffected by params member order`() {
        val a = request("tools/call", """{"name":"x","arguments":{"a":1,"b":2}}""")
        val b = request("tools/call", """{"arguments":{"b":2,"a":1},"name":"x"}""")
        assertEquals(CanonicalInvocation.identityOf(a), CanonicalInvocation.identityOf(b))
    }

    @Test
    fun `CVM-8-13 changes when the semantic arguments change`() {
        val ny = request("tools/call", """{"name":"get_weather","arguments":{"location":"NY"}}""")
        val sf = request("tools/call", """{"name":"get_weather","arguments":{"location":"SF"}}""")
        assertNotEquals(CanonicalInvocation.identityOf(ny), CanonicalInvocation.identityOf(sf))
    }

    @Test
    fun `CVM-8-14 changes when the method changes`() {
        assertNotEquals(
            CanonicalInvocation.identityOf(request("tools/call", """{"name":"x"}""")),
            CanonicalInvocation.identityOf(request("prompts/get", """{"name":"x"}""")),
        )
    }

    @Test
    fun `CVM-8-15 binds the authorization to the requesting client`() {
        val call = request("tools/call", """{"name":"x"}""")
        assertNotEquals(
            CanonicalInvocation.authorizationKey("aa".repeat(32), call),
            CanonicalInvocation.authorizationKey("bb".repeat(32), call),
        )
    }

    @Test
    fun `CVM-8-16 semanticParams strips only _meta and leaves the rest intact`() {
        // The exclusion is for identity only: the handler still needs the full
        // params at execution time, so this must not mutate the original.
        val original = params("""{"name":"x","arguments":{"a":1},"_meta":{"progressToken":"t"}}""")
        val semantic = CanonicalInvocation.semanticParams(original)

        assertFalse(semantic.containsKey("_meta"))
        assertTrue(semantic.containsKey("name"))
        assertTrue(semantic.containsKey("arguments"))
        assertTrue(original.containsKey("_meta"), "the source params must not be mutated")
    }

    // --- lifecycle negotiation ---

    @Test
    fun `CVM-8-20 an absent payment_interaction tag means transparent`() {
        val session = PaymentSession()
        session.observeServerTags(emptyArray())
        assertEquals(PaymentInteraction.TRANSPARENT, session.effectiveMode)
        assertFalse(session.negotiationFailed)
    }

    @Test
    fun `CVM-8-21 explicit gating is in force once the server echoes it`() {
        val session = PaymentSession(requested = PaymentInteraction.EXPLICIT_GATING)
        session.observeServerTags(arrayOf(PaymentTags.paymentInteraction(PaymentInteraction.EXPLICIT_GATING)))
        assertEquals(PaymentInteraction.EXPLICIT_GATING, session.effectiveMode)
        assertFalse(session.negotiationFailed)
    }

    @Test
    fun `CVM-8-22 a server that ignores the request is a failed negotiation, not a downgrade`() {
        val session = PaymentSession(requested = PaymentInteraction.EXPLICIT_GATING)
        session.observeServerTags(emptyArray())
        assertTrue(session.negotiationFailed, "silent fallback must be visible to the caller")
    }

    @Test
    fun `CVM-8-23 a client needing visible payments will not auto-pay after a failed negotiation`() {
        // The client half of the no-silent-fallback rule: a handler that simply
        // pays whatever it is asked would violate it.
        val session =
            PaymentSession(
                requested = PaymentInteraction.EXPLICIT_GATING,
                requiresVisiblePayments = true,
                supportedPmis = listOf(lightning),
            )
        session.observeServerTags(emptyArray())

        val demand = PaymentRequest(amount = 100.0, pmi = lightning, payRequest = "lnbc...")
        assertFalse(session.mayAutoPay(demand))
    }

    @Test
    fun `CVM-8-24 the same client does auto-pay once explicit gating is accepted`() {
        val session =
            PaymentSession(
                requested = PaymentInteraction.EXPLICIT_GATING,
                requiresVisiblePayments = true,
                supportedPmis = listOf(lightning),
            )
        session.observeServerTags(arrayOf(PaymentTags.paymentInteraction(PaymentInteraction.EXPLICIT_GATING)))
        assertTrue(session.mayAutoPay(PaymentRequest(100.0, lightning, "lnbc...")))
    }

    @Test
    fun `CVM-8-25 never auto-pays a PMI it cannot settle`() {
        val session = PaymentSession(supportedPmis = listOf(lightning))
        session.observeServerTags(emptyArray())
        assertFalse(session.mayAutoPay(PaymentRequest(100.0, cashuDirect, "cashuB...")))
    }

    @Test
    fun `CVM-8-26 a later payment_interaction tag upserts the session mode`() {
        val session = PaymentSession(requested = PaymentInteraction.EXPLICIT_GATING)
        session.observeServerTags(emptyArray())
        assertEquals(PaymentInteraction.TRANSPARENT, session.effectiveMode)

        session.observeLaterServerTags(arrayOf(PaymentTags.paymentInteraction(PaymentInteraction.EXPLICIT_GATING)))
        assertEquals(PaymentInteraction.EXPLICIT_GATING, session.effectiveMode)
    }

    @Test
    fun `CVM-8-27 an absent tag on a later message inherits the current mode`() {
        val session = PaymentSession()
        session.observeServerTags(arrayOf(PaymentTags.paymentInteraction(PaymentInteraction.EXPLICIT_GATING)))
        session.observeLaterServerTags(emptyArray())
        assertEquals(PaymentInteraction.EXPLICIT_GATING, session.effectiveMode)
    }

    @Test
    fun `CVM-8-28 advertises the requested mode and its PMIs on the first message`() {
        val session =
            PaymentSession(
                requested = PaymentInteraction.EXPLICIT_GATING,
                supportedPmis = listOf(lightning, cashuDirect),
            )
        val tags: List<Tag> = session.negotiationTags()
        assertContentEquals(arrayOf("payment_interaction", "explicit_gating"), tags[0])
        assertContentEquals(arrayOf("pmi", lightning.value), tags[1])
        assertContentEquals(arrayOf("pmi", cashuDirect.value), tags[2])
    }

    @Test
    fun `CVM-8-29 omits the tag when requesting the default mode`() {
        assertTrue(PaymentSession().negotiationTags().isEmpty())
    }

    @Test
    fun `CVM-8-30 intersects PMIs preserving the server's preference order`() {
        val session = PaymentSession(supportedPmis = listOf(cashuDirect, lightning))
        assertEquals(listOf(lightning, cashuDirect), session.intersectPmis(listOf(lightning, cashuDirect)))
        assertEquals(listOf(lightning), session.intersectPmis(listOf(Pmi("unknown-rail"), lightning)))
    }

    // --- messages ---

    @Test
    fun `CVM-8-40 parses a payment_required notification`() {
        val notification =
            JsonRpcNotification(
                "notifications/payment_required",
                params(
                    """{"amount":100,"pay_req":"lnbc...","pmi":"bitcoin-lightning-bolt11",
                       "description":"tool run","ttl":600,"_meta":{"note":"x"}}""",
                ),
            )
        val parsed = PaymentMessages.paymentRequired(notification)!!
        assertEquals(100.0, parsed.amount)
        assertEquals(lightning, parsed.pmi)
        assertEquals("lnbc...", parsed.payRequest)
        assertEquals(600L, parsed.ttl)
        assertEquals("tool run", parsed.description)
    }

    @Test
    fun `CVM-8-41 parses accepted and rejected notifications`() {
        val accepted =
            PaymentMessages.paymentAccepted(
                JsonRpcNotification(
                    "notifications/payment_accepted",
                    params("""{"amount":100,"pmi":"bitcoin-lightning-bolt11"}"""),
                ),
            )!!
        assertEquals(100.0, accepted.amount)

        val rejected =
            PaymentMessages.paymentRejected(
                JsonRpcNotification(
                    "notifications/payment_rejected",
                    params("""{"pmi":"bitcoin-cashu-v4-direct","message":"Insufficient","amount":150}"""),
                ),
            )!!
        assertEquals(cashuDirect, rejected.pmi)
        assertEquals(150.0, rejected.amount)
    }

    @Test
    fun `CVM-8-42 reads payment options off a -32042 error`() {
        val error =
            JsonRpcError(
                JsonRpcError.PAYMENT_REQUIRED,
                "Payment Required",
                params(
                    """{"instructions":"pay then retry","payment_options":[
                       {"amount":100,"pmi":"bitcoin-lightning-bolt11","pay_req":"lnbc..."}]}""",
                ),
            )
        val options = PaymentMessages.paymentOptions(error)!!
        assertEquals(1, options.size)
        assertEquals(lightning, options[0].pmi)
        assertEquals("pay then retry", PaymentMessages.instructions(error))
    }

    @Test
    fun `CVM-8-43 reads retry_after off a -32043 error`() {
        val error =
            JsonRpcError(
                JsonRpcError.PAYMENT_PENDING,
                "Payment Pending",
                params("""{"instructions":"retry later","retry_after":5}"""),
            )
        assertEquals(5L, PaymentMessages.retryAfter(error))
        assertNull(PaymentMessages.paymentOptions(error), "a pending error carries no options")
    }

    @Test
    fun `CVM-8-44 ignores a non-payment error`() {
        val error = JsonRpcError(JsonRpcError.INVALID_PARAMS, "Unsupported payment_interaction")
        assertNull(PaymentMessages.paymentOptions(error))
        assertNull(PaymentMessages.retryAfter(error))
    }

    @Test
    fun `CVM-8-45 selects the first offered option it can settle`() {
        val session = PaymentSession(supportedPmis = listOf(lightning))
        val options =
            listOf(
                PaymentRequest(100.0, cashuDirect, "cashuB..."),
                PaymentRequest(100.0, lightning, "lnbc..."),
            )
        assertEquals(lightning, session.selectPayable(options)?.pmi)
        assertNull(session.selectPayable(listOf(PaymentRequest(1.0, cashuDirect, "x"))))
    }

    @Test
    fun `CVM-8-46 parses direct_payment offers in request order and the change tag`() {
        val tags =
            arrayOf<Tag>(
                PaymentTags.directPayment(cashuDirect, "token-a"),
                PaymentTags.directPayment(lightning, "token-b"),
            )
        assertEquals(listOf(cashuDirect to "token-a", lightning to "token-b"), PaymentTags.parseDirectPayments(tags))
        assertEquals(cashuDirect to "rest", PaymentTags.parseChange(arrayOf(PaymentTags.change(cashuDirect, "rest"))))
    }
}
