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

import com.vitorpamplona.quartz.experimental.clink.manage.ManageResponse
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The CLINK half of [com.vitorpamplona.quartz.nip47WalletConnect.Nip47MalformedInputTest]:
 * a payment service is somebody else's software too, and CLINK is experimental, so its
 * wire shapes are still moving.
 */
class ClinkMalformedInputTest {
    private inline fun <reified T : OptimizedSerializable> parse(json: String): T = KotlinSerializationMapper.fromJsonTo<T>(json)

    @Test
    fun anOfferRequestParsesFromNothingButTheOffer() {
        val req = parse<OfferRequest>("""{"offer":"lno1abc"}""")

        assertEquals("lno1abc", req.offer)
        assertNull(req.amount_sats)
        assertNull(req.payer_data)
    }

    @Test
    fun unknownFieldsAreIgnoredAndWrongShapesCostOnlyTheirField() {
        val req =
            parse<OfferRequest>(
                """{"offer":"lno1abc","amount_sats":{"not":"a number"},
                   "description":"dinner","invented_by_a_newer_service":[1,2]}""",
            )

        assertEquals("lno1abc", req.offer)
        assertEquals("dinner", req.description)
        assertNull(req.amount_sats)
    }

    @Test
    fun amountsQuotedAsStringsStillParse() {
        val req = parse<OfferRequest>("""{"offer":"lno1abc","amount_sats":"2500"}""")

        assertEquals(2500L, req.amount_sats)
    }

    @Test
    fun anErrorResponseSurvivesACodeSentAsAString() {
        val res = parse<OfferResponse>("""{"error":"Invalid Amount","code":"5"}""")

        assertEquals(5, res.code)
        assertEquals("Invalid Amount", res.error)
        assertTrue(!res.isSuccess())
    }

    @Test
    fun aLoneDetailsObjectIsReadAsAOneElementList() {
        // Jackson's ACCEPT_SINGLE_VALUE_AS_ARRAY was enabled for exactly this: a service
        // answers with a bare object for one result and an array for several.
        val res = parse<ManageResponse>("""{"res":"ok","resource":"offer","details":{"offer_id":"a1"}}""")

        assertEquals(1, res.details?.size)
        assertTrue(res.isOk())
    }

    @Test
    fun aBrokenEntryInDetailsDoesNotLoseTheGoodOnes() {
        val res =
            parse<ManageResponse>(
                """{"res":"ok","resource":"offer","details":[{"offer_id":"a1"},"junk",{"offer_id":"a2"}]}""",
            )

        assertEquals(2, res.details?.size)
    }

    @Test
    fun explicitNullsReadAsAbsent() {
        val res = parse<OfferResponse>("""{"bolt11":null,"error":null,"code":null}""")

        assertNull(res.bolt11)
        assertNull(res.error)
        assertNull(res.code)
    }

    @Test
    fun anEmptyObjectIsNotAnException() {
        val res = parse<OfferResponse>("""{}""")

        assertNull(res.bolt11)
        assertTrue(!res.isSuccess())
    }

    @Test
    fun aRangeOfTheWrongShapeDoesNotFailTheResponse() {
        val res = parse<OfferResponse>("""{"error":"Invalid Amount","code":5,"range":"1-100"}""")

        assertEquals(5, res.code)
        assertNull(res.range)
    }
}
