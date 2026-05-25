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
package com.vitorpamplona.quartz.nip57Zaps.validate

import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LnZapReceiptValidatorTest {
    // Mainnet 100,000 sat invoice (= 100_000_000 msats). From LnInvoiceUtilTest.
    private val invoice100kSats =
        "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"

    private val providerPubkey = "be1d89794bf92de5dd64c1e60f6a2c70c140abac15c14fda99e75b6db4eaab86"
    private val senderPubkey = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e2c3"
    private val recipientPubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    private val lnurlOfRecipient = "user@example.com"
    private val lnurlUrl = "https://example.com/.well-known/lnurlp/user"

    private fun zapRequestJson(
        amountMillisats: Long? = 100_000_000L,
        lnurl: String? = lnurlOfRecipient,
    ): String {
        val tags = mutableListOf<List<String>>()
        tags.add(listOf("e", "a".repeat(64)))
        tags.add(listOf("p", recipientPubkey))
        tags.add(listOf("relays", "wss://relay.example.com/"))
        if (amountMillisats != null) tags.add(listOf("amount", amountMillisats.toString()))
        if (lnurl != null) tags.add(listOf("lnurl", lnurl))

        val tagsJson =
            tags.joinToString(",", prefix = "[", postfix = "]") { row ->
                row.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
            }

        return """{"id":"${"d".repeat(64)}","pubkey":"$senderPubkey","created_at":1700000000,""" +
            """"kind":9734,"tags":$tagsJson,"content":"","sig":"${"e".repeat(128)}"}"""
    }

    private fun receipt(
        signerPubkey: String = providerPubkey,
        bolt11: String? = invoice100kSats,
        description: String? = zapRequestJson(),
    ): LnZapEvent {
        val tags = mutableListOf<Array<String>>()
        tags.add(arrayOf("p", recipientPubkey))
        if (bolt11 != null) tags.add(arrayOf("bolt11", bolt11))
        if (description != null) tags.add(arrayOf("description", description))

        return LnZapEvent(
            id = "f".repeat(64),
            pubKey = signerPubkey,
            createdAt = 1700000001,
            tags = tags.toTypedArray(),
            content = "",
            sig = "0".repeat(128),
        )
    }

    @Test
    fun `valid receipt passes all checks`() {
        val result = LnZapReceiptValidator.validate(receipt(), providerPubkey, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `missing zap request fails with MISSING_ZAP_REQUEST`() {
        val r = receipt(description = null)
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISSING_ZAP_REQUEST, invalid.reason)
    }

    @Test
    fun `wrong signer fails with MISMATCHED_NOSTR_PUBKEY`() {
        val attackerPubkey = "1".repeat(64)
        val result = LnZapReceiptValidator.validate(receipt(signerPubkey = attackerPubkey), providerPubkey, lnurlOfRecipient)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISMATCHED_NOSTR_PUBKEY, invalid.reason)
    }

    @Test
    fun `null expected pubkey skips the check`() {
        val attackerPubkey = "1".repeat(64)
        val result = LnZapReceiptValidator.validate(receipt(signerPubkey = attackerPubkey), null, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `missing bolt11 fails with MISSING_OR_BAD_BOLT11`() {
        val result = LnZapReceiptValidator.validate(receipt(bolt11 = null), providerPubkey, lnurlOfRecipient)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISSING_OR_BAD_BOLT11, invalid.reason)
    }

    @Test
    fun `mismatched amount fails with MISMATCHED_AMOUNT`() {
        // bolt11 is 100,000,000 msats; request claims 50,000,000.
        val r = receipt(description = zapRequestJson(amountMillisats = 50_000_000L))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISMATCHED_AMOUNT, invalid.reason)
    }

    @Test
    fun `missing amount tag is accepted when not strict`() {
        val r = receipt(description = zapRequestJson(amountMillisats = null))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient, strictAmount = false)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `missing amount tag fails when strict`() {
        val r = receipt(description = zapRequestJson(amountMillisats = null))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient, strictAmount = true)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISMATCHED_AMOUNT, invalid.reason)
    }

    @Test
    fun `mismatched lnurl fails with MISMATCHED_LNURL`() {
        val r = receipt(description = zapRequestJson(lnurl = "other@example.org"))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        val invalid = assertIs<LnZapReceiptValidator.Result.Invalid>(result)
        assertEquals(LnZapReceiptValidator.Result.Reason.MISMATCHED_LNURL, invalid.reason)
    }

    @Test
    fun `missing lnurl tag is accepted`() {
        val r = receipt(description = zapRequestJson(lnurl = null))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `lnurl tag in URL form matches lud16 expectation`() {
        val r = receipt(description = zapRequestJson(lnurl = lnurlUrl))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `lnurl tag in bech32 form matches lud16 expectation`() {
        val bech32 = LnurlForm.urlToBech32(lnurlUrl)
        val r = receipt(description = zapRequestJson(lnurl = bech32))
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }

    @Test
    fun `pubkey check is case-insensitive`() {
        val r = receipt(signerPubkey = providerPubkey.uppercase())
        val result = LnZapReceiptValidator.validate(r, providerPubkey, lnurlOfRecipient)
        assertEquals(LnZapReceiptValidator.Result.Valid, result)
    }
}

class LnurlFormTest {
    @Test
    fun `lud16 to url`() {
        assertEquals("https://example.com/.well-known/lnurlp/vitor", LnurlForm.toUrl("vitor@example.com"))
    }

    @Test
    fun `https url passthrough`() {
        val url = "https://example.com/.well-known/lnurlp/vitor"
        assertEquals(url, LnurlForm.toUrl(url))
    }

    @Test
    fun `unrecognized input returns null`() {
        assertEquals(null, LnurlForm.toUrl("not-a-lnurl"))
        assertEquals(null, LnurlForm.toUrl(""))
    }

    @Test
    fun `matches across forms`() {
        assertTrue(LnurlForm.matches("vitor@example.com", "https://example.com/.well-known/lnurlp/vitor"))
        assertTrue(LnurlForm.matches("vitor@example.com", "vitor@example.com"))
    }

    @Test
    fun `does not match different recipients`() {
        assertEquals(false, LnurlForm.matches("alice@example.com", "bob@example.com"))
    }
}
