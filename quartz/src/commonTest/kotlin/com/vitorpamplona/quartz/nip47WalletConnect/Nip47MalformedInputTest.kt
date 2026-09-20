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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcUnknownResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a NIP-47 response parser has to survive, because the wallet on the other end
 * is somebody else's software and NIP-47 keeps growing.
 *
 * The rule every case here asserts: one bad field costs you that field, never the
 * message. Jackson used to bind these reflectively and answered a type mismatch with
 * MismatchedInputException — a `fees_paid` of `"0"` instead of `0` threw away a
 * settled payment's preimage.
 */
class Nip47MalformedInputTest {
    private fun parse(json: String): Response = KotlinSerializationMapper.fromJsonTo<Response>(json)

    @Test
    fun unknownResultTypeKeepsItsResultInsteadOfFailing() {
        // A method from a newer NIP-47, or an extension this build does not implement.
        val res = parse("""{"result_type":"make_offer","result":{"offer":"lno1abc","amount":42}}""")

        assertTrue(res is NwcUnknownResponse)
        assertEquals("make_offer", res.resultType)
        assertEquals("lno1abc", res.result?.get("offer"))
        assertEquals(42L, res.result?.get("amount"))
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        val res =
            parse(
                """{"result_type":"pay_invoice","result":{"preimage":"abc","fees_paid":3,
               "totally_new_field":{"nested":true}},"extra_root_field":[1,2,3]}""",
            )

        assertTrue(res is PayInvoiceSuccessResponse)
        assertEquals("abc", res.result?.preimage)
        assertEquals(3L, res.result?.fees_paid)
    }

    @Test
    fun numbersSentAsStringsStillParse() {
        // Several wallets quote their integers.
        val res = parse("""{"result_type":"pay_invoice","result":{"preimage":"abc","fees_paid":"12"}}""")

        assertTrue(res is PayInvoiceSuccessResponse)
        assertEquals(12L, res.result?.fees_paid)
    }

    @Test
    fun aFieldOfTheWrongShapeCostsOnlyThatField() {
        // `preimage` arrives as an object. The payment still settled; read what is there.
        val res =
            parse(
                """{"result_type":"pay_invoice","result":{"preimage":{"unexpected":"object"},"fees_paid":7}}""",
            )

        assertTrue(res is PayInvoiceSuccessResponse)
        assertNull(res.result?.preimage)
        assertEquals(7L, res.result?.fees_paid)
    }

    @Test
    fun explicitNullsReadTheSameAsAbsentKeys() {
        val res = parse("""{"result_type":"pay_invoice","result":{"preimage":null,"fees_paid":null}}""")

        assertTrue(res is PayInvoiceSuccessResponse)
        assertNull(res.result?.preimage)
        assertNull(res.result?.fees_paid)
    }

    @Test
    fun aBrokenEntryDoesNotTakeDownTheWholeList() {
        val res =
            parse(
                """{"result_type":"list_transactions","result":{"transactions":[
               {"type":"incoming","amount":1000},
               "not-an-object",
               {"type":"outgoing","amount":"2000"}]}}""",
            )

        assertTrue(res is ListTransactionsSuccessResponse)
        val txs = res.result?.transactions
        assertEquals(2, txs?.size)
        assertEquals(1000L, txs?.get(0)?.amount)
        assertEquals(2000L, txs?.get(1)?.amount)
    }

    @Test
    fun aStringListDropsNonStringEntriesRatherThanFailing() {
        val res =
            parse(
                """{"result_type":"get_info","result":{"methods":["pay_invoice",{"bad":1},"get_balance"]}}""",
            )

        assertTrue(res is GetInfoSuccessResponse)
        assertEquals(listOf("pay_invoice", "get_balance"), res.result?.methods)
    }

    @Test
    fun aLoneValueIsAcceptedWhereAListIsExpected() {
        // Jackson's ACCEPT_SINGLE_VALUE_AS_ARRAY, preserved.
        val res = parse("""{"result_type":"get_info","result":{"methods":"pay_invoice"}}""")

        assertTrue(res is GetInfoSuccessResponse)
        assertEquals(listOf("pay_invoice"), res.result?.methods)
    }

    @Test
    fun anErrorWithoutAMessageStillReportsItsCode() {
        val res = parse("""{"result_type":"pay_invoice","error":{"code":"INSUFFICIENT_BALANCE"}}""")

        assertTrue(res is com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike)
        assertEquals("INSUFFICIENT_BALANCE", res.errorMessage())
    }

    @Test
    fun anErrorWhoseCodeIsUnknownIsStillAnError() {
        val res = parse("""{"result_type":"pay_invoice","error":{"code":"SOMETHING_NEW","message":"nope"}}""")

        assertTrue(res is com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike)
        assertEquals("nope", res.errorMessage())
    }

    @Test
    fun anEmptyObjectIsNotAnException() {
        val res = parse("""{}""")

        assertTrue(res is NwcUnknownResponse)
        assertEquals("", res.resultType)
    }

    @Test
    fun anErrorTypedResponseSurvivesAnErrorFieldOfTheWrongShape() {
        // `error` present but a string, not an object: still an error response.
        val res = parse("""{"result_type":"pay_invoice","error":"boom"}""")

        assertTrue(res is NwcErrorResponse || res is com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse)
    }

    @Test
    fun aPayloadThatIsNotAnObjectFailsWithAClearMessage() {
        // The one input still refused: there is no message to salvage from an array.
        val thrown =
            try {
                parse("""["not","a","response"]""")
                null
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(thrown != null, "an array root must be rejected")
        assertTrue(thrown.message?.contains("must be a JSON object") == true, "got: ${thrown.message}")
    }
}
