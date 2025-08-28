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

import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

// RESPONSE OBJECTS
abstract class Response(
    @field:JsonProperty("result_type") val resultType: String,
) {
    abstract fun countMemory(): Long
}

// PayInvoice Call

class PayInvoiceSuccessResponse(
    val result: PayInvoiceResultParams? = null,
) : Response("pay_invoice") {
    class PayInvoiceResultParams(
        val preimage: String? = null,
    ) {
        fun countMemory(): Long = pointerSizeInBytes + (preimage?.bytesUsedInMemory() ?: 0)
    }

    override fun countMemory(): Long = pointerSizeInBytes + (result?.countMemory() ?: 0)
}

class PayInvoiceErrorResponse(
    val error: PayInvoiceErrorParams? = null,
) : Response("pay_invoice") {
    class PayInvoiceErrorParams(
        val code: ErrorType? = null,
        val message: String? = null,
    ) {
        fun countMemory(): Long = pointerSizeInBytes + pointerSizeInBytes + (message?.bytesUsedInMemory() ?: 0)
    }

    override fun countMemory(): Long = pointerSizeInBytes + (error?.countMemory() ?: 0)

    enum class ErrorType {
        @JsonProperty(value = "RATE_LIMITED")
        RATE_LIMITED,

        // The client is sending commands too fast. It should retry in a few seconds.
        @JsonProperty(value = "NOT_IMPLEMENTED")
        NOT_IMPLEMENTED,

        // The command is not known or is intentionally not implemented.
        @JsonProperty(value = "INSUFFICIENT_BALANCE")
        INSUFFICIENT_BALANCE,

        // The wallet does not have enough funds to cover a fee reserve or the payment amount.
        @JsonProperty(value = "QUOTA_EXCEEDED")
        QUOTA_EXCEEDED,

        // The wallet has exceeded its spending quota.
        @JsonProperty(value = "RESTRICTED")
        RESTRICTED,

        // This public key is not allowed to do this operation.
        @JsonProperty(value = "UNAUTHORIZED")
        UNAUTHORIZED,

        // This public key has no wallet connected.
        @JsonProperty(value = "INTERNAL")
        INTERNAL,

        // An internal error.
        @JsonProperty(value = "OTHER")
        OTHER, // Other error.
    }
}
