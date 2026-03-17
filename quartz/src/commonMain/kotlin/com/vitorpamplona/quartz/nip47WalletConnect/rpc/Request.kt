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
package com.vitorpamplona.quartz.nip47WalletConnect.rpc

import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

// REQUEST OBJECTS
abstract class Request(
    var method: String? = null,
) : OptimizedSerializable

// pay_invoice
class PayInvoiceParams(
    var invoice: String? = null,
    var amount: Long? = null,
    var metadata: Map<String, Any?>? = null,
)

class PayInvoiceMethod(
    var params: PayInvoiceParams? = null,
) : Request(NwcMethod.PAY_INVOICE) {
    companion object {
        fun create(bolt11: String): PayInvoiceMethod = PayInvoiceMethod(PayInvoiceParams(bolt11))

        fun create(
            bolt11: String,
            amount: Long,
        ): PayInvoiceMethod = PayInvoiceMethod(PayInvoiceParams(bolt11, amount))
    }
}

// pay_keysend
class PayKeysendParams(
    var amount: Long? = null,
    var pubkey: String? = null,
    var preimage: String? = null,
    var tlv_records: List<TlvRecord>? = null,
)

class PayKeysendMethod(
    var params: PayKeysendParams? = null,
) : Request(NwcMethod.PAY_KEYSEND) {
    companion object {
        fun create(
            amount: Long,
            pubkey: String,
            preimage: String? = null,
            tlvRecords: List<TlvRecord>? = null,
        ): PayKeysendMethod = PayKeysendMethod(PayKeysendParams(amount, pubkey, preimage, tlvRecords))
    }
}

// make_invoice
class MakeInvoiceParams(
    var amount: Long? = null,
    var description: String? = null,
    var description_hash: String? = null,
    var expiry: Long? = null,
    var metadata: Map<String, Any?>? = null,
)

class MakeInvoiceMethod(
    var params: MakeInvoiceParams? = null,
) : Request(NwcMethod.MAKE_INVOICE) {
    companion object {
        fun create(
            amount: Long,
            description: String? = null,
            descriptionHash: String? = null,
            expiry: Long? = null,
        ): MakeInvoiceMethod = MakeInvoiceMethod(MakeInvoiceParams(amount, description, descriptionHash, expiry))
    }
}

// lookup_invoice
class LookupInvoiceParams(
    var payment_hash: String? = null,
    var invoice: String? = null,
)

class LookupInvoiceMethod(
    var params: LookupInvoiceParams? = null,
) : Request(NwcMethod.LOOKUP_INVOICE) {
    companion object {
        fun createByHash(paymentHash: String): LookupInvoiceMethod = LookupInvoiceMethod(LookupInvoiceParams(payment_hash = paymentHash))

        fun createByInvoice(invoice: String): LookupInvoiceMethod = LookupInvoiceMethod(LookupInvoiceParams(invoice = invoice))
    }
}

// list_transactions
class ListTransactionsParams(
    var from: Long? = null,
    var until: Long? = null,
    var limit: Int? = null,
    var offset: Int? = null,
    var unpaid: Boolean? = null,
    var unpaid_outgoing: Boolean? = null,
    var unpaid_incoming: Boolean? = null,
    var type: String? = null,
)

class ListTransactionsMethod(
    var params: ListTransactionsParams? = null,
) : Request(NwcMethod.LIST_TRANSACTIONS) {
    companion object {
        fun create(
            from: Long? = null,
            until: Long? = null,
            limit: Int? = null,
            offset: Int? = null,
            unpaid: Boolean? = null,
            type: String? = null,
            unpaid_outgoing: Boolean? = null,
            unpaid_incoming: Boolean? = null,
        ): ListTransactionsMethod =
            ListTransactionsMethod(
                ListTransactionsParams(from, until, limit, offset, unpaid, unpaid_outgoing, unpaid_incoming, type),
            )
    }
}

// get_balance
class GetBalanceMethod : Request(NwcMethod.GET_BALANCE) {
    companion object {
        fun create(): GetBalanceMethod = GetBalanceMethod()
    }
}

// get_info
class GetInfoMethod : Request(NwcMethod.GET_INFO) {
    companion object {
        fun create(): GetInfoMethod = GetInfoMethod()
    }
}

// make_hold_invoice
class MakeHoldInvoiceParams(
    var amount: Long? = null,
    var description: String? = null,
    var description_hash: String? = null,
    var expiry: Long? = null,
    var payment_hash: String? = null,
    var min_cltv_expiry_delta: Int? = null,
)

class MakeHoldInvoiceMethod(
    var params: MakeHoldInvoiceParams? = null,
) : Request(NwcMethod.MAKE_HOLD_INVOICE) {
    companion object {
        fun create(
            amount: Long,
            paymentHash: String,
            description: String? = null,
            descriptionHash: String? = null,
            expiry: Long? = null,
            minCltvExpiryDelta: Int? = null,
        ): MakeHoldInvoiceMethod =
            MakeHoldInvoiceMethod(
                MakeHoldInvoiceParams(amount, description, descriptionHash, expiry, paymentHash, minCltvExpiryDelta),
            )
    }
}

// cancel_hold_invoice
class CancelHoldInvoiceParams(
    var payment_hash: String? = null,
)

class CancelHoldInvoiceMethod(
    var params: CancelHoldInvoiceParams? = null,
) : Request(NwcMethod.CANCEL_HOLD_INVOICE) {
    companion object {
        fun create(paymentHash: String): CancelHoldInvoiceMethod = CancelHoldInvoiceMethod(CancelHoldInvoiceParams(paymentHash))
    }
}

// settle_hold_invoice
class SettleHoldInvoiceParams(
    var preimage: String? = null,
)

class SettleHoldInvoiceMethod(
    var params: SettleHoldInvoiceParams? = null,
) : Request(NwcMethod.SETTLE_HOLD_INVOICE) {
    companion object {
        fun create(preimage: String): SettleHoldInvoiceMethod = SettleHoldInvoiceMethod(SettleHoldInvoiceParams(preimage))
    }
}

// get_budget
class GetBudgetMethod : Request(NwcMethod.GET_BUDGET) {
    companion object {
        fun create(): GetBudgetMethod = GetBudgetMethod()
    }
}

// sign_message
class SignMessageParams(
    var message: String? = null,
)

class SignMessageMethod(
    var params: SignMessageParams? = null,
) : Request(NwcMethod.SIGN_MESSAGE) {
    companion object {
        fun create(message: String): SignMessageMethod = SignMessageMethod(SignMessageParams(message))
    }
}

// create_connection
class CreateConnectionParams(
    var pubkey: String? = null,
    var name: String? = null,
    var request_methods: List<String>? = null,
    var notification_types: List<String>? = null,
    var max_amount: Long? = null,
    var budget_renewal: String? = null,
    var expires_at: Long? = null,
    var isolated: Boolean? = null,
    var metadata: Map<String, Any?>? = null,
)

class CreateConnectionMethod(
    var params: CreateConnectionParams? = null,
) : Request(NwcMethod.CREATE_CONNECTION) {
    companion object {
        fun create(
            pubkey: String,
            name: String,
            requestMethods: List<String>? = null,
            notificationTypes: List<String>? = null,
            maxAmount: Long? = null,
            budgetRenewal: String? = null,
            expiresAt: Long? = null,
            isolated: Boolean? = null,
            metadata: Map<String, Any?>? = null,
        ): CreateConnectionMethod =
            CreateConnectionMethod(
                CreateConnectionParams(pubkey, name, requestMethods, notificationTypes, maxAmount, budgetRenewal, expiresAt, isolated, metadata),
            )
    }
}
