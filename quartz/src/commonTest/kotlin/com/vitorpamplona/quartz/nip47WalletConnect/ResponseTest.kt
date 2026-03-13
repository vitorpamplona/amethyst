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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResponseTest {
    // --- PayInvoice Success ---

    @Test
    fun testPayInvoiceSuccessDeserialization() {
        val json = """{"result_type":"pay_invoice","result":{"preimage":"0123456789abcdef"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceSuccessResponse>(response)
        assertEquals("0123456789abcdef", response.result?.preimage)
    }

    @Test
    fun testPayInvoiceSuccessWithFeesPaid() {
        val json = """{"result_type":"pay_invoice","result":{"preimage":"abc","fees_paid":100}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceSuccessResponse>(response)
        assertEquals("abc", response.result?.preimage)
        assertEquals(100L, response.result?.fees_paid)
    }

    @Test
    fun testPayInvoiceSuccessGuessWithoutResultType() {
        val json = """{"result":{"preimage":"0123456789abcdef"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceSuccessResponse>(response)
        assertEquals("0123456789abcdef", response.result?.preimage)
    }

    // --- PayInvoice Error ---

    @Test
    fun testPayInvoiceErrorDeserialization() {
        val json = """{"result_type":"pay_invoice","error":{"code":"INSUFFICIENT_BALANCE","message":"Not enough funds"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.INSUFFICIENT_BALANCE, response.error?.code)
        assertEquals("Not enough funds", response.error?.message)
    }

    @Test
    fun testPayInvoicePaymentFailedError() {
        val json = """{"result_type":"pay_invoice","error":{"code":"PAYMENT_FAILED","message":"Route not found"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayInvoiceErrorResponse>(response)
        assertEquals(NwcErrorCode.PAYMENT_FAILED, response.error?.code)
    }

    // --- PayKeysend Success ---

    @Test
    fun testPayKeysendSuccessDeserialization() {
        val json = """{"result_type":"pay_keysend","result":{"preimage":"abc123","fees_paid":50}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<PayKeysendSuccessResponse>(response)
        assertEquals("abc123", response.result?.preimage)
        assertEquals(50L, response.result?.fees_paid)
    }

    // --- MakeInvoice Success ---

    @Test
    fun testMakeInvoiceSuccessDeserialization() {
        val json =
            """{"result_type":"make_invoice","result":{"type":"incoming","invoice":"lnbc50n1...","description":"test","payment_hash":"abc","amount":5000,"fees_paid":0,"created_at":1693876497,"expires_at":1694876497}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<MakeInvoiceSuccessResponse>(response)
        assertNotNull(response.result)
        assertEquals("incoming", response.result?.type)
        assertEquals("lnbc50n1...", response.result?.invoice)
        assertEquals("test", response.result?.description)
        assertEquals("abc", response.result?.payment_hash)
        assertEquals(5000L, response.result?.amount)
        assertEquals(1693876497L, response.result?.created_at)
        assertEquals(1694876497L, response.result?.expires_at)
    }

    // --- LookupInvoice Success ---

    @Test
    fun testLookupInvoiceSuccessDeserialization() {
        val json = """{"result_type":"lookup_invoice","result":{"type":"incoming","state":"settled","invoice":"lnbc...","payment_hash":"hash123","amount":1000,"settled_at":1694876497}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<LookupInvoiceSuccessResponse>(response)
        assertNotNull(response.result)
        assertEquals("incoming", response.result?.type)
        assertEquals("settled", response.result?.state)
        assertEquals("hash123", response.result?.payment_hash)
        assertEquals(1000L, response.result?.amount)
        assertEquals(1694876497L, response.result?.settled_at)
    }

    // --- ListTransactions Success ---

    @Test
    fun testListTransactionsSuccessDeserialization() {
        val json =
            """{"result_type":"list_transactions","result":{"transactions":[{"type":"incoming","invoice":"lnbc1...","amount":100,"created_at":1000},{"type":"outgoing","invoice":"lnbc2...","amount":200,"created_at":2000}]}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<ListTransactionsSuccessResponse>(response)
        assertNotNull(response.result?.transactions)
        assertEquals(2, response.result?.transactions?.size)
        assertEquals("incoming", response.result?.transactions?.get(0)?.type)
        assertEquals(100L, response.result?.transactions?.get(0)?.amount)
        assertEquals("outgoing", response.result?.transactions?.get(1)?.type)
        assertEquals(200L, response.result?.transactions?.get(1)?.amount)
    }

    @Test
    fun testListTransactionsEmptyResult() {
        val json = """{"result_type":"list_transactions","result":{"transactions":[]}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<ListTransactionsSuccessResponse>(response)
        assertNotNull(response.result?.transactions)
        assertEquals(0, response.result?.transactions?.size)
    }

    // --- GetBalance Success ---

    @Test
    fun testGetBalanceSuccessDeserialization() {
        val json = """{"result_type":"get_balance","result":{"balance":21000}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBalanceSuccessResponse>(response)
        assertEquals(21000L, response.result?.balance)
    }

    @Test
    fun testGetBalanceZero() {
        val json = """{"result_type":"get_balance","result":{"balance":0}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetBalanceSuccessResponse>(response)
        assertEquals(0L, response.result?.balance)
    }

    // --- GetInfo Success ---

    @Test
    fun testGetInfoSuccessDeserialization() {
        val json =
            """{"result_type":"get_info","result":{"alias":"MyNode","color":"#ff9900","pubkey":"abc123","network":"mainnet","block_height":800000,"block_hash":"hash","methods":["pay_invoice","get_balance"],"notifications":["payment_received"]}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<GetInfoSuccessResponse>(response)
        assertNotNull(response.result)
        assertEquals("MyNode", response.result?.alias)
        assertEquals("#ff9900", response.result?.color)
        assertEquals("abc123", response.result?.pubkey)
        assertEquals("mainnet", response.result?.network)
        assertEquals(800000L, response.result?.block_height)
        assertEquals("hash", response.result?.block_hash)
        assertEquals(listOf("pay_invoice", "get_balance"), response.result?.methods)
        assertEquals(listOf("payment_received"), response.result?.notifications)
    }

    // --- MakeHoldInvoice Success ---

    @Test
    fun testMakeHoldInvoiceSuccessDeserialization() {
        val json = """{"result_type":"make_hold_invoice","result":{"type":"incoming","invoice":"lnbc...","payment_hash":"hash","amount":5000,"created_at":1000,"expires_at":2000}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<MakeHoldInvoiceSuccessResponse>(response)
        assertNotNull(response.result)
        assertEquals("lnbc...", response.result?.invoice)
        assertEquals("hash", response.result?.payment_hash)
    }

    // --- CancelHoldInvoice Success ---

    @Test
    fun testCancelHoldInvoiceSuccessDeserialization() {
        val json = """{"result_type":"cancel_hold_invoice","result":{}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<CancelHoldInvoiceSuccessResponse>(response)
    }

    // --- SettleHoldInvoice Success ---

    @Test
    fun testSettleHoldInvoiceSuccessDeserialization() {
        val json = """{"result_type":"settle_hold_invoice","result":{}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<SettleHoldInvoiceSuccessResponse>(response)
    }

    // --- Generic Error Response ---

    @Test
    fun testGenericErrorForGetBalance() {
        val json = """{"result_type":"get_balance","error":{"code":"UNAUTHORIZED","message":"No wallet connected"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals("get_balance", response.resultType)
        assertEquals(NwcErrorCode.UNAUTHORIZED, response.error?.code)
        assertEquals("No wallet connected", response.error?.message)
    }

    @Test
    fun testGenericErrorForGetInfo() {
        val json = """{"result_type":"get_info","error":{"code":"NOT_IMPLEMENTED","message":"Not supported"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals("get_info", response.resultType)
        assertEquals(NwcErrorCode.NOT_IMPLEMENTED, response.error?.code)
    }

    @Test
    fun testGenericErrorForMakeInvoice() {
        val json = """{"result_type":"make_invoice","error":{"code":"QUOTA_EXCEEDED","message":"Spending limit reached"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals(NwcErrorCode.QUOTA_EXCEEDED, response.error?.code)
    }

    @Test
    fun testGenericErrorRateLimited() {
        val json = """{"result_type":"pay_keysend","error":{"code":"RATE_LIMITED","message":"Too many requests"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        assertIs<NwcErrorResponse>(response)
        assertEquals(NwcErrorCode.RATE_LIMITED, response.error?.code)
    }

    @Test
    fun testGenericErrorUnsupportedEncryption() {
        val json = """{"result_type":"pay_invoice","error":{"code":"UNSUPPORTED_ENCRYPTION","message":"Use nip44"}}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        // pay_invoice errors go to PayInvoiceErrorResponse for backward compat
        assertIs<PayInvoiceErrorResponse>(response)
    }

    // --- Null/missing result ---

    @Test
    fun testResponseWithNoResultOrError() {
        val json = """{"result_type":"pay_invoice"}"""
        val response = OptimizedJsonMapper.fromJsonTo<Response>(json)
        // Should still deserialize since result_type is present
        assertIs<PayInvoiceSuccessResponse>(response)
        assertNull(response.result)
    }
}
