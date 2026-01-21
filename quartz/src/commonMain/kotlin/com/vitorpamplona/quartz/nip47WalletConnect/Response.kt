/**
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

// PayInvoice Call

class PayInvoiceSuccessResponse(
    val result: PayInvoiceResultParams? = null,
) : Response("pay_invoice") {
    class PayInvoiceResultParams(
        val preimage: String? = null,
    )
}

class PayInvoiceErrorResponse(
    val error: PayInvoiceErrorParams? = null,
) : Response("pay_invoice") {
    class PayInvoiceErrorParams(
        val code: ErrorType? = null,
        val message: String? = null,
    )

    enum class ErrorType {
        RATE_LIMITED,

        // The client is sending commands too fast. It should retry in a few seconds.
        NOT_IMPLEMENTED,

        // The command is not known or is intentionally not implemented.
        INSUFFICIENT_BALANCE,

        // The command is not known or is intentionally not implemented.
        PAYMENT_FAILED,

        // The wallet does not have enough funds to cover a fee reserve or the payment amount.
        QUOTA_EXCEEDED,

        // The wallet has exceeded its spending quota.
        RESTRICTED,

        // This public key is not allowed to do this operation.
        UNAUTHORIZED,

        // This public key has no wallet connected.
        INTERNAL,

        // An internal error.
        OTHER, // Other error.
    }
}
