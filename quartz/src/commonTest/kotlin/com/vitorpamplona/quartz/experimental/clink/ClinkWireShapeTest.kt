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
package com.vitorpamplona.quartz.experimental.clink

import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitRequest
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.manage.ManageRequest
import com.vitorpamplona.quartz.experimental.clink.manage.ManageResponse
import com.vitorpamplona.quartz.experimental.clink.offers.OfferReceipt
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden wire-shape fixtures: the literal decrypted JSON payload bodies documented in the
 * CLINK specs (`shocknet/CLINK/specs/clink-{offers,debits,manage}.md`, declared public domain)
 * must deserialize into our DTOs with the right fields. These guard the request/response shapes
 * we exchange with every CLINK service against drift — they are the encrypted-content half that
 * the bech32 pointer vectors (`ClinkInteropTest`) don't cover.
 *
 * Placeholders shown as `<…>` in the spec markdown are substituted with realistic concrete
 * values; numeric placeholders (`actual_delta_ms`, `retry_after`) use numbers, not the
 * markdown's quoted strings, matching what real services emit.
 */
class ClinkWireShapeTest {
    private inline fun <reified T : OptimizedSerializable> parse(json: String): T = OptimizedJsonMapper.fromJsonTo<T>(json)

    // ---------- Offers (kind 21001) ----------

    @Test
    fun offerRequest() {
        val req =
            parse<OfferRequest>(
                """{"offer":"coffee","amount_sats":21000,"payer_data":{},"zap":"{...}","expires_in_seconds":3600,"description":"A coffee"}""",
            )
        assertEquals("coffee", req.offer)
        assertEquals(21000L, req.amount_sats)
        assertEquals(3600L, req.expires_in_seconds)
        assertEquals("A coffee", req.description)
        assertEquals("{...}", req.zap)
        assertNotNull(req.payer_data)
    }

    @Test
    fun offerSuccessBolt11() {
        val res = parse<OfferResponse>("""{"bolt11":"lnbc10u1pexample"}""")
        assertTrue(res.isSuccess())
        assertEquals("lnbc10u1pexample", res.bolt11)
    }

    @Test
    fun offerErrorInvalidOffer() {
        val res = parse<OfferResponse>("""{"error":"Invalid Offer","code":1}""")
        assertFalse(res.isSuccess())
        assertEquals(1, res.code)
        assertEquals("Invalid Offer", res.error)
        assertNull(res.bolt11)
    }

    @Test
    fun offerErrorExpiredNoForwarding() {
        val res = parse<OfferResponse>("""{"error":"Offer has expired.","code":3}""")
        assertEquals(3, res.code)
        assertNull(res.latest)
    }

    @Test
    fun offerErrorMovedWithLatest() {
        val res =
            parse<OfferResponse>(
                """{"error":"Offer has been replaced or moved.","code":3,"latest":"noffer1qqsmoved"}""",
            )
        assertEquals(3, res.code)
        assertEquals("noffer1qqsmoved", res.latest)
    }

    @Test
    fun offerErrorInvalidAmountRange() {
        val res = parse<OfferResponse>("""{"error":"Invalid Amount","code":5,"range":{"min":10,"max":10000000}}""")
        assertEquals(5, res.code)
        assertEquals(10L, res.range?.min)
        assertEquals(10000000L, res.range?.max)
    }

    @Test
    fun offerReceiptWithPreimage() {
        val receipt = parse<OfferReceipt>("""{"res":"ok","preimage":"${"ab".repeat(32)}"}""")
        assertTrue(receipt.isOk())
        assertEquals("ab".repeat(32), receipt.preimage)
    }

    @Test
    fun offerReceiptInternalSettlement() {
        val receipt = parse<OfferReceipt>("""{"res":"ok"}""")
        assertTrue(receipt.isOk())
        assertNull(receipt.preimage)
    }

    // ---------- Debits (kind 21002) ----------

    @Test
    fun debitDirectPaymentRequest() {
        val req =
            parse<DebitRequest>(
                """{"pointer":"app-7","amount_sats":10000,"bolt11":"lnbc100n1pexample","description":"zap","k1":"${"4c".repeat(32)}"}""",
            )
        assertEquals("app-7", req.pointer)
        assertEquals(10000L, req.amount_sats)
        assertEquals("lnbc100n1pexample", req.bolt11)
        assertEquals("4c".repeat(32), req.k1)
        assertNull(req.frequency)
    }

    @Test
    fun debitBudgetRequest() {
        val req =
            parse<DebitRequest>(
                """{"pointer":"app-7","amount_sats":50000,"frequency":{"number":1,"unit":"month"},"description":"sub"}""",
            )
        assertEquals(50000L, req.amount_sats)
        assertEquals(1, req.frequency?.number)
        assertEquals(DebitFrequency.UNIT_MONTH, req.frequency?.unit)
        assertNull(req.bolt11)
    }

    @Test
    fun debitSuccessWithPreimage() {
        val res = parse<DebitResponse>("""{"res":"ok","preimage":"${"cd".repeat(32)}"}""")
        assertTrue(res.isOk())
        assertEquals("cd".repeat(32), res.preimage)
    }

    @Test
    fun debitSuccessInternalOrBudgetApproval() {
        val res = parse<DebitResponse>("""{"res":"ok"}""")
        assertTrue(res.isOk())
        assertNull(res.preimage)
    }

    @Test
    fun debitGfyRequestDenied() {
        val res = parse<DebitResponse>("""{"res":"GFY","code":1,"error":"Request Denied"}""")
        assertFalse(res.isOk())
        assertEquals(1, res.code)
        assertEquals("Request Denied", res.error)
    }

    @Test
    fun debitGfyExpiredWithDelta() {
        val res =
            parse<DebitResponse>(
                """{"res":"GFY","code":3,"error":"Expired Request","delta":{"max_delta_ms":30000,"actual_delta_ms":31200}}""",
            )
        assertEquals(3, res.code)
        assertEquals(30000L, res.delta?.max_delta_ms)
        assertEquals(31200L, res.delta?.actual_delta_ms)
    }

    @Test
    fun debitGfyRateLimitedRetryAfter() {
        val res = parse<DebitResponse>("""{"res":"GFY","code":4,"error":"Rate Limited","retry_after":1750000000}""")
        assertEquals(4, res.code)
        assertEquals(1750000000L, res.retry_after)
    }

    @Test
    fun debitGfyInvalidAmountRange() {
        val res = parse<DebitResponse>("""{"res":"GFY","code":5,"error":"Invalid Amount","range":{"min":1000,"max":500000}}""")
        assertEquals(5, res.code)
        assertEquals(1000L, res.range?.min)
        assertEquals(500000L, res.range?.max)
    }

    @Test
    fun debitGfyInvalidRequest() {
        val res = parse<DebitResponse>("""{"res":"GFY","code":6,"error":"Invalid Request: K1 already processed"}""")
        assertEquals(6, res.code)
        assertEquals("Invalid Request: K1 already processed", res.error)
    }

    // ---------- Manage (kind 21003) ----------
    // Requests use the nested `offer.fields` shape (the form the reference SDK, Lightning.Pub,
    // and clink-demo exchange — see ManageMessages.kt), not the spec's inline-create example.

    @Test
    fun manageUpdateRequestNestedFields() {
        val req =
            parse<ManageRequest>(
                """{"resource":"offer","action":"update","offer":{"id":"off-1","fields":{"label":"Updated Product X","price_sats":23456,"callback_url":"https://m.app/cb","payer_data":["email","shipping_address"]}}}""",
            )
        assertEquals(ManageRequest.RESOURCE_OFFER, req.resource)
        assertEquals(ManageRequest.ACTION_UPDATE, req.action)
        assertEquals("off-1", req.offer?.id)
        assertEquals("Updated Product X", req.offer?.fields?.label)
        assertEquals(23456L, req.offer?.fields?.price_sats)
        assertEquals(listOf("email", "shipping_address"), req.offer?.fields?.payer_data)
    }

    @Test
    fun manageListRequest() {
        val req = parse<ManageRequest>("""{"resource":"offer","action":"list"}""")
        assertEquals(ManageRequest.ACTION_LIST, req.action)
        assertNull(req.offer)
    }

    @Test
    fun manageDeleteRequest() {
        val req = parse<ManageRequest>("""{"resource":"offer","action":"delete","offer":{"id":"off-1"}}""")
        assertEquals(ManageRequest.ACTION_DELETE, req.action)
        assertEquals("off-1", req.offer?.id)
    }

    @Test
    fun manageSuccessSingleDetailsObjectCoercesToList() {
        // create/update/get return a bare object for `details`; it must coerce into our list.
        val res =
            parse<ManageResponse>(
                """{"res":"ok","resource":"offer","details":{"id":"off-1","label":"Product X","price_sats":12345,"callback_url":"https://m.app/cb","payer_data":["email"],"noffer":"noffer1qqsabc"}}""",
            )
        assertTrue(res.isOk())
        assertEquals(1, res.details?.size)
        assertEquals("off-1", res.details?.first()?.id)
        assertEquals("Product X", res.details?.first()?.label)
        assertEquals(12345L, res.details?.first()?.price_sats)
        assertEquals("noffer1qqsabc", res.details?.first()?.noffer)
    }

    @Test
    fun manageSuccessListDetailsArray() {
        val res =
            parse<ManageResponse>(
                """{"res":"ok","resource":"offer","details":[{"id":"off-1","label":"Product X","price_sats":12345,"noffer":"noffer1qqsabc"}]}""",
            )
        assertTrue(res.isOk())
        assertEquals(1, res.details?.size)
        assertEquals("off-1", res.details?.first()?.id)
    }

    @Test
    fun manageDeleteSuccessNoDetails() {
        val res = parse<ManageResponse>("""{"res":"ok","resource":"offer"}""")
        assertTrue(res.isOk())
        assertNull(res.details)
    }

    @Test
    fun manageGfyInvalidFieldWithFieldAndRange() {
        val res =
            parse<ManageResponse>(
                """{"res":"GFY","code":5,"error":"Invalid Field/Value","field":"price_sats","range":{"min":1000,"max":1000000}}""",
            )
        assertFalse(res.isOk())
        assertEquals(5, res.code)
        assertEquals("price_sats", res.field)
        assertEquals(1000L, res.range?.min)
        assertEquals(1000000L, res.range?.max)
    }

    @Test
    fun manageGfyRateLimitedRetryAfter() {
        val res = parse<ManageResponse>("""{"res":"GFY","code":4,"error":"Rate Limited","retry_after":600}""")
        assertEquals(4, res.code)
        assertEquals(600L, res.retry_after)
    }
}
