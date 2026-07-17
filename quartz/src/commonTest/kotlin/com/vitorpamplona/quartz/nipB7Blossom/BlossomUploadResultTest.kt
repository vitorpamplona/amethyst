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
package com.vitorpamplona.quartz.nipB7Blossom

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlossomUploadResultTest {
    @Test
    fun parsesMinimalDescriptor() {
        val json =
            """
            {
              "url": "https://cdn.example.com/b167.png",
              "sha256": "b167",
              "size": 184292,
              "type": "image/png",
              "uploaded": 1725105921
            }
            """.trimIndent()

        val result = JsonMapper.fromJson<BlossomUploadResult>(json)
        assertEquals("https://cdn.example.com/b167.png", result.url)
        assertEquals("b167", result.sha256)
        assertEquals(184292, result.size)
        assertEquals("image/png", result.type)
        assertNull(result.ox)
        assertNull(result.nip94)
    }

    @Test
    fun parsesMediaDescriptorWithOriginalHashAndNip94() {
        // BUD-05 /media returns the optimized blob's hash in `sha256` and the
        // original in `ox`; BUD-08 adds the `nip94` tag array.
        val json =
            """
            {
              "url": "https://cdn.example.com/opt.png",
              "sha256": "optimizedhash",
              "ox": "originalhash",
              "size": 123,
              "type": "image/png",
              "uploaded": 1725105921,
              "nip94": [
                ["url", "https://cdn.example.com/opt.png"],
                ["m", "image/png"],
                ["x", "optimizedhash"],
                ["size", "123"]
              ]
            }
            """.trimIndent()

        val result = JsonMapper.fromJson<BlossomUploadResult>(json)
        assertEquals("optimizedhash", result.sha256)
        assertEquals("originalhash", result.ox)
        assertEquals(4, result.nip94?.size)
        assertEquals(listOf("m", "image/png"), result.nip94?.get(1))
    }

    @Test
    fun ignoresUnknownFields() {
        val json = """{"url":"https://x/y","sha256":"a","serverSpecific":{"foo":1},"extra":"z"}"""
        val result = JsonMapper.fromJson<BlossomUploadResult>(json)
        assertEquals("a", result.sha256)
    }

    @Test
    fun readsBud07PaymentHeaders() {
        val headers =
            mapOf(
                BlossomServerUrl.X_CASHU_HEADER to "\"cashuBToken...\"",
                BlossomServerUrl.X_LIGHTNING_HEADER to "lnbc10n1...",
                BlossomServerUrl.REASON_HEADER to "Payment required: 10 sats",
            )
        val payment = BlossomPaymentRequired.fromHeaders { headers[it] }

        assertEquals("cashuBToken...", payment.cashu)
        assertEquals("lnbc10n1...", payment.lightning)
        assertEquals("Payment required: 10 sats", payment.reason)
        assertEquals(true, payment.hasPaymentOption())
    }

    @Test
    fun paymentWithNoMethodsIsNotPayable() {
        val payment = BlossomPaymentRequired.fromHeaders { null }
        assertEquals(false, payment.hasPaymentOption())
    }
}
