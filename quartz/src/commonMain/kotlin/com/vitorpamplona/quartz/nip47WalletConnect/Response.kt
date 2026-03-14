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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

// RESPONSE OBJECTS
abstract class Response(
    val resultType: String,
) : OptimizedSerializable

// Generic error response for any method
class NwcErrorResponse(
    resultType: String,
    val error: NwcError? = null,
) : Response(resultType)

// pay_invoice success response
class PayInvoiceSuccessResponse(
    val result: PayInvoiceResultParams? = null,
) : Response(NwcMethod.PAY_INVOICE) {
    class PayInvoiceResultParams(
        val preimage: String? = null,
        val fees_paid: Long? = null,
    )
}

// pay_invoice error response (kept for backward compatibility)
class PayInvoiceErrorResponse(
    val error: PayInvoiceErrorParams? = null,
) : Response(NwcMethod.PAY_INVOICE) {
    class PayInvoiceErrorParams(
        val code: NwcErrorCode? = null,
        val message: String? = null,
    )
}

// pay_keysend success response
class PayKeysendSuccessResponse(
    val result: PayKeysendResult? = null,
) : Response(NwcMethod.PAY_KEYSEND) {
    class PayKeysendResult(
        val preimage: String? = null,
        val fees_paid: Long? = null,
    )
}

// make_invoice success response
class MakeInvoiceSuccessResponse(
    val result: NwcTransaction? = null,
) : Response(NwcMethod.MAKE_INVOICE)

// lookup_invoice success response
class LookupInvoiceSuccessResponse(
    val result: NwcTransaction? = null,
) : Response(NwcMethod.LOOKUP_INVOICE)

// list_transactions success response
class ListTransactionsSuccessResponse(
    val result: ListTransactionsResult? = null,
) : Response(NwcMethod.LIST_TRANSACTIONS) {
    class ListTransactionsResult(
        val transactions: List<NwcTransaction>? = null,
        val total_count: Long? = null,
    )
}

// get_balance success response
class GetBalanceSuccessResponse(
    val result: GetBalanceResult? = null,
) : Response(NwcMethod.GET_BALANCE) {
    class GetBalanceResult(
        val balance: Long? = null,
    )
}

// get_info success response
class GetInfoSuccessResponse(
    val result: GetInfoResult? = null,
) : Response(NwcMethod.GET_INFO) {
    class GetInfoResult(
        val alias: String? = null,
        val color: String? = null,
        val pubkey: String? = null,
        val network: String? = null,
        val block_height: Long? = null,
        val block_hash: String? = null,
        val methods: List<String>? = null,
        val notifications: List<String>? = null,
        val metadata: Any? = null,
        val lud16: String? = null,
    )
}

// make_hold_invoice success response
class MakeHoldInvoiceSuccessResponse(
    val result: NwcTransaction? = null,
) : Response(NwcMethod.MAKE_HOLD_INVOICE)

// cancel_hold_invoice success response
class CancelHoldInvoiceSuccessResponse(
    val result: Any? = null,
) : Response(NwcMethod.CANCEL_HOLD_INVOICE)

// settle_hold_invoice success response
class SettleHoldInvoiceSuccessResponse(
    val result: Any? = null,
) : Response(NwcMethod.SETTLE_HOLD_INVOICE)

// get_budget success response
class GetBudgetSuccessResponse(
    val result: GetBudgetResult? = null,
) : Response(NwcMethod.GET_BUDGET) {
    class GetBudgetResult(
        val used_budget: Long? = null,
        val total_budget: Long? = null,
        val renews_at: Long? = null,
        val renewal_period: String? = null,
    )
}

// sign_message success response
class SignMessageSuccessResponse(
    val result: SignMessageResult? = null,
) : Response(NwcMethod.SIGN_MESSAGE) {
    class SignMessageResult(
        val message: String? = null,
        val signature: String? = null,
    )
}

// create_connection success response
class CreateConnectionSuccessResponse(
    val result: CreateConnectionResult? = null,
) : Response(NwcMethod.CREATE_CONNECTION) {
    class CreateConnectionResult(
        val wallet_pubkey: String? = null,
    )
}
