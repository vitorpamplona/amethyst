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

import kotlin.test.Test
import kotlin.test.assertEquals

class NwcMethodTest {
    @Test
    fun testMethodConstants() {
        assertEquals("pay_invoice", NwcMethod.PAY_INVOICE)
        assertEquals("pay_keysend", NwcMethod.PAY_KEYSEND)
        assertEquals("make_invoice", NwcMethod.MAKE_INVOICE)
        assertEquals("lookup_invoice", NwcMethod.LOOKUP_INVOICE)
        assertEquals("list_transactions", NwcMethod.LIST_TRANSACTIONS)
        assertEquals("get_balance", NwcMethod.GET_BALANCE)
        assertEquals("get_info", NwcMethod.GET_INFO)
        assertEquals("get_budget", NwcMethod.GET_BUDGET)
        assertEquals("sign_message", NwcMethod.SIGN_MESSAGE)
        assertEquals("create_connection", NwcMethod.CREATE_CONNECTION)
        assertEquals("make_hold_invoice", NwcMethod.MAKE_HOLD_INVOICE)
        assertEquals("cancel_hold_invoice", NwcMethod.CANCEL_HOLD_INVOICE)
        assertEquals("settle_hold_invoice", NwcMethod.SETTLE_HOLD_INVOICE)
    }

    @Test
    fun testNotificationTypeConstants() {
        assertEquals("payment_received", NwcNotificationType.PAYMENT_RECEIVED)
        assertEquals("payment_sent", NwcNotificationType.PAYMENT_SENT)
        assertEquals("hold_invoice_accepted", NwcNotificationType.HOLD_INVOICE_ACCEPTED)
    }

    @Test
    fun testErrorCodeValues() {
        val codes = NwcErrorCode.entries
        assertEquals(13, codes.size)
        assertEquals(NwcErrorCode.RATE_LIMITED, NwcErrorCode.valueOf("RATE_LIMITED"))
        assertEquals(NwcErrorCode.NOT_IMPLEMENTED, NwcErrorCode.valueOf("NOT_IMPLEMENTED"))
        assertEquals(NwcErrorCode.INSUFFICIENT_BALANCE, NwcErrorCode.valueOf("INSUFFICIENT_BALANCE"))
        assertEquals(NwcErrorCode.PAYMENT_FAILED, NwcErrorCode.valueOf("PAYMENT_FAILED"))
        assertEquals(NwcErrorCode.QUOTA_EXCEEDED, NwcErrorCode.valueOf("QUOTA_EXCEEDED"))
        assertEquals(NwcErrorCode.RESTRICTED, NwcErrorCode.valueOf("RESTRICTED"))
        assertEquals(NwcErrorCode.UNAUTHORIZED, NwcErrorCode.valueOf("UNAUTHORIZED"))
        assertEquals(NwcErrorCode.INTERNAL, NwcErrorCode.valueOf("INTERNAL"))
        assertEquals(NwcErrorCode.UNSUPPORTED_ENCRYPTION, NwcErrorCode.valueOf("UNSUPPORTED_ENCRYPTION"))
        assertEquals(NwcErrorCode.BAD_REQUEST, NwcErrorCode.valueOf("BAD_REQUEST"))
        assertEquals(NwcErrorCode.NOT_FOUND, NwcErrorCode.valueOf("NOT_FOUND"))
        assertEquals(NwcErrorCode.EXPIRED, NwcErrorCode.valueOf("EXPIRED"))
        assertEquals(NwcErrorCode.OTHER, NwcErrorCode.valueOf("OTHER"))
    }

    @Test
    fun testTransactionTypeConstants() {
        assertEquals("incoming", NwcTransactionType.INCOMING)
        assertEquals("outgoing", NwcTransactionType.OUTGOING)
    }

    @Test
    fun testTransactionStateConstants() {
        assertEquals("PENDING", NwcTransactionState.PENDING)
        assertEquals("SETTLED", NwcTransactionState.SETTLED)
        assertEquals("FAILED", NwcTransactionState.FAILED)
        assertEquals("ACCEPTED", NwcTransactionState.ACCEPTED)
    }

    @Test
    fun testNwcError() {
        val error = NwcError(NwcErrorCode.UNAUTHORIZED, "not allowed")
        assertEquals(NwcErrorCode.UNAUTHORIZED, error.code)
        assertEquals("not allowed", error.message)
    }
}
