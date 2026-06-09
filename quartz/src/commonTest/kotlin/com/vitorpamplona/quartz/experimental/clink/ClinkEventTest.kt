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

import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic + JSON (de)serialization tests for the CLINK message kinds. The actual
 * NIP-44 encrypt/decrypt round-trip lives in androidDeviceTest because NIP-44 crypto
 * requires lazysodium, which is unavailable in JVM unit tests.
 */
class ClinkEventTest {
    private val payer = NostrSignerInternal(KeyPair())
    private val service = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private fun buildEvent(
        author: String,
        recipient: String,
        requestId: String? = null,
    ) = OfferEvent(
        id = "a".repeat(64),
        pubKey = author,
        createdAt = 1L,
        tags =
            buildList {
                add(arrayOf("p", recipient))
                if (requestId != null) add(arrayOf("e", requestId))
                add(Clink.versionTag())
            }.toTypedArray(),
        content = "encrypted-placeholder",
        sig = "b".repeat(128),
    )

    // --- tag logic ---

    @Test
    fun requestHasNoEventTag() {
        val request = buildEvent(payer.pubKey, service.pubKey)
        assertFalse(request.isResponse())
        assertNull(request.requestId())
        assertEquals(service.pubKey, request.recipientPubKey())
        assertEquals(Clink.VERSION, request.version())
    }

    @Test
    fun responseReferencesRequest() {
        val response = buildEvent(service.pubKey, payer.pubKey, requestId = "c".repeat(64))
        assertTrue(response.isResponse())
        assertEquals("c".repeat(64), response.requestId())
    }

    @Test
    fun canDecryptOnlyByEitherParty() {
        val request = buildEvent(payer.pubKey, service.pubKey)
        assertTrue(request.canDecrypt(payer))
        assertTrue(request.canDecrypt(service))
        assertFalse(request.canDecrypt(stranger))
    }

    // --- JSON DTOs ---

    @Test
    fun offerRequestJsonRoundTrip() {
        val request = OfferRequest(offer = "abc", amount_sats = 1500, description = "coffee")
        val parsed = OptimizedJsonMapper.fromJsonTo<OfferRequest>(OptimizedJsonMapper.toJson(request))

        assertEquals("abc", parsed.offer)
        assertEquals(1500, parsed.amount_sats)
        assertEquals("coffee", parsed.description)
    }

    @Test
    fun offerResponseInvoiceParses() {
        val parsed = OptimizedJsonMapper.fromJsonTo<OfferResponse>("""{"bolt11":"lnbc1..."}""")
        assertTrue(parsed.isSuccess())
        assertEquals("lnbc1...", parsed.bolt11)
    }

    @Test
    fun offerResponseInvalidAmountParsesRange() {
        val parsed =
            OptimizedJsonMapper.fromJsonTo<OfferResponse>(
                """{"error":"Invalid Amount","code":5,"range":{"min":1000,"max":50000}}""",
            )
        assertFalse(parsed.isSuccess())
        assertEquals(5, parsed.code)
        assertEquals(1000, parsed.range?.min)
        assertEquals(50000, parsed.range?.max)
    }

    @Test
    fun debitGfyResponseParses() {
        val parsed =
            OptimizedJsonMapper.fromJsonTo<DebitResponse>(
                """{"res":"GFY","code":4,"error":"Rate Limited","retry_after":1717000000}""",
            )
        assertFalse(parsed.isOk())
        assertEquals(4, parsed.code)
        assertEquals(1717000000, parsed.retry_after)
    }

    @Test
    fun debitOkResponseParses() {
        val parsed = OptimizedJsonMapper.fromJsonTo<DebitResponse>("""{"res":"ok","preimage":"deadbeef"}""")
        assertTrue(parsed.isOk())
        assertEquals("deadbeef", parsed.preimage)
    }
}
