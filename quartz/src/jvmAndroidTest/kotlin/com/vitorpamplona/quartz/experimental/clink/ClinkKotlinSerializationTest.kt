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

import com.vitorpamplona.quartz.experimental.clink.common.GfyDelta
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitRequest
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.manage.ManageOffer
import com.vitorpamplona.quartz.experimental.clink.manage.ManageRequest
import com.vitorpamplona.quartz.experimental.clink.manage.ManageResponse
import com.vitorpamplona.quartz.experimental.clink.manage.OfferData
import com.vitorpamplona.quartz.experimental.clink.manage.OfferFields
import com.vitorpamplona.quartz.experimental.clink.offers.OfferReceipt
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the kotlinx-serialization path for the CLINK DTOs — the code path that
 * native targets (iOS) use via `OptimizedJsonMapper` — and cross-checks it against
 * Jackson (the JVM/Android path) so both backends agree on the wire shapes.
 */
class ClinkKotlinSerializationTest {
    @Test
    fun offerRequestRoundTripAndCrossParse() {
        val request =
            OfferRequest(
                offer = "coffee",
                amount_sats = 21000,
                payer_data = mapOf("email" to "a@b.c", "count" to 2L),
                zap = "{...}",
                expires_in_seconds = 3600,
                description = "A coffee",
            )

        val kotlinJson = KotlinSerializationMapper.toJson(request)
        val parsed = KotlinSerializationMapper.fromJsonTo<OfferRequest>(kotlinJson)
        assertEquals("coffee", parsed.offer)
        assertEquals(21000L, parsed.amount_sats)
        assertEquals("a@b.c", parsed.payer_data?.get("email"))
        // payer_data is an opaque pass-through map; numeric boxing differs between
        // backends (Jackson: Int, kotlinx via toAnyValue(): Double), so compare values.
        assertEquals(2L, (parsed.payer_data?.get("count") as Number).toLong())
        assertEquals("{...}", parsed.zap)
        assertEquals(3600L, parsed.expires_in_seconds)
        assertEquals("A coffee", parsed.description)

        // Jackson-serialized payload parses with kotlinx and vice versa.
        val fromJackson = KotlinSerializationMapper.fromJsonTo<OfferRequest>(JacksonMapper.toJson(request))
        assertEquals("coffee", fromJackson.offer)
        val jacksonParsed = JacksonMapper.fromJsonTo<OfferRequest>(kotlinJson)
        assertEquals(21000L, jacksonParsed.amount_sats)
    }

    @Test
    fun offerResponseParsesRangeAndLatest() {
        val parsed =
            KotlinSerializationMapper.fromJsonTo<OfferResponse>(
                """{"error":"Invalid Amount","code":5,"range":{"min":1000,"max":50000},"latest":"noffer1qqsmoved"}""",
            )
        assertEquals(5, parsed.code)
        assertEquals(1000L, parsed.range?.min)
        assertEquals(50000L, parsed.range?.max)
        assertEquals("noffer1qqsmoved", parsed.latest)

        val success = KotlinSerializationMapper.fromJsonTo<OfferResponse>("""{"bolt11":"lnbc1..."}""")
        assertTrue(success.isSuccess())
        assertEquals(
            "lnbc1...",
            KotlinSerializationMapper.fromJsonTo<OfferResponse>(KotlinSerializationMapper.toJson(success)).bolt11,
        )
    }

    @Test
    fun offerReceiptRoundTrip() {
        val receipt = OfferReceipt(res = OfferReceipt.OK, preimage = "ab".repeat(32))
        val parsed = KotlinSerializationMapper.fromJsonTo<OfferReceipt>(KotlinSerializationMapper.toJson(receipt))
        assertTrue(parsed.isOk())
        assertEquals("ab".repeat(32), parsed.preimage)
    }

    @Test
    fun debitRequestRoundTripWithFrequency() {
        val request =
            DebitRequest(
                pointer = "ndebit1...",
                amount_sats = 1000,
                description = "budget",
                k1 = "4caa9ee5",
                frequency = DebitFrequency(1, DebitFrequency.UNIT_MONTH),
            )
        val parsed = KotlinSerializationMapper.fromJsonTo<DebitRequest>(KotlinSerializationMapper.toJson(request))
        assertEquals("ndebit1...", parsed.pointer)
        assertEquals(1000L, parsed.amount_sats)
        assertEquals("4caa9ee5", parsed.k1)
        assertEquals(1, parsed.frequency?.number)
        assertEquals(DebitFrequency.UNIT_MONTH, parsed.frequency?.unit)
    }

    @Test
    fun debitResponseParsesGfyExtras() {
        val response =
            DebitResponse(
                res = DebitResponse.GFY,
                code = 3,
                error = "Expired Request",
                range = SatRange(10, 10000000),
                retry_after = 1717000000,
                delta = GfyDelta(30000, 45000),
            )
        val parsed = KotlinSerializationMapper.fromJsonTo<DebitResponse>(KotlinSerializationMapper.toJson(response))
        assertEquals(3, parsed.code)
        assertEquals(10L, parsed.range?.min)
        assertEquals(1717000000L, parsed.retry_after)
        assertEquals(30000L, parsed.delta?.max_delta_ms)
        assertEquals(45000L, parsed.delta?.actual_delta_ms)
    }

    @Test
    fun manageRequestRoundTripsNestedOfferFields() {
        val request =
            ManageRequest(
                resource = ManageRequest.RESOURCE_OFFER,
                action = ManageRequest.ACTION_UPDATE,
                offer =
                    ManageOffer(
                        id = "offer-1",
                        fields = OfferFields("Coffee", 1500L, "https://x/cb", listOf("email", "name")),
                    ),
            )
        val json = KotlinSerializationMapper.toJson(request)
        val parsed = KotlinSerializationMapper.fromJsonTo<ManageRequest>(json)
        assertEquals("offer-1", parsed.offer?.id)
        assertEquals("Coffee", parsed.offer?.fields?.label)
        assertEquals(1500L, parsed.offer?.fields?.price_sats)
        assertEquals(listOf("email", "name"), parsed.offer?.fields?.payer_data)

        // Jackson reads the kotlinx output identically.
        val jacksonParsed = JacksonMapper.fromJsonTo<ManageRequest>(json)
        assertEquals("Coffee", jacksonParsed.offer?.fields?.label)
    }

    @Test
    fun manageResponseCoercesSingleDetailsObjectToList() {
        val single =
            KotlinSerializationMapper.fromJsonTo<ManageResponse>(
                """{"res":"ok","resource":"offer","details":{"id":"o1","noffer":"noffer1...","label":"Coffee","price_sats":1500}}""",
            )
        assertEquals(1, single.details?.size)
        assertEquals("o1", single.details?.first()?.id)

        val array =
            KotlinSerializationMapper.fromJsonTo<ManageResponse>(
                """{"res":"ok","resource":"offer","details":[{"id":"o1"},{"id":"o2"}]}""",
            )
        assertEquals(listOf("o1", "o2"), array.details?.map { it.id })
    }

    @Test
    fun manageResponseRoundTripWithDetails() {
        val response =
            ManageResponse(
                res = ManageResponse.OK,
                resource = "offer",
                details = listOf(OfferData(id = "o1", noffer = "noffer1...", label = "Coffee", price_sats = 1500)),
            )
        val json = KotlinSerializationMapper.toJson(response)
        val parsed = KotlinSerializationMapper.fromJsonTo<ManageResponse>(json)
        assertTrue(parsed.isOk())
        assertEquals("o1", parsed.details?.first()?.id)
        assertEquals(1500L, parsed.details?.first()?.price_sats)

        val jacksonParsed = JacksonMapper.fromJsonTo<ManageResponse>(json)
        assertEquals("noffer1...", jacksonParsed.details?.first()?.noffer)
    }
}
