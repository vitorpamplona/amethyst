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
package com.vitorpamplona.contextvm.cep08Payments

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcError
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.contextvm.mcp.McpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A payment the server is asking for.
 *
 * Carried by `notifications/payment_required` in the transparent lifecycle and
 * as one entry of `error.data.payment_options` under explicit gating — the
 * fields are identical, which is why one type covers both.
 */
data class PaymentRequest(
    val amount: Double,
    val pmi: Pmi,
    /**
     * The settlement payload, opaque here.
     *
     * Its format is PMI-defined, so a handler must match [pmi] before trying to
     * interpret it. Treating it as a BOLT11 invoice because it starts with "ln"
     * is exactly the guess the PMI exists to prevent.
     */
    val payRequest: String,
    val description: String? = null,
    /** Seconds. Absent means the PMI or the implementation defines expiry. */
    val ttl: Long? = null,
    val meta: JsonObject? = null,
) {
    companion object {
        const val AMOUNT = "amount"
        const val PAY_REQ = "pay_req"
        const val PMI = "pmi"
        const val DESCRIPTION = "description"
        const val TTL = "ttl"
        const val META = "_meta"

        fun parseOrNull(params: JsonObject): PaymentRequest? {
            val amount = (params[AMOUNT] as? JsonPrimitive)?.doubleOrNull ?: return null
            val pmi = (params[PMI] as? JsonPrimitive)?.contentOrNullIfNotString()?.let { Pmi.parseOrNull(it) } ?: return null
            val payRequest = (params[PAY_REQ] as? JsonPrimitive)?.contentOrNullIfNotString() ?: return null

            return PaymentRequest(
                amount = amount,
                pmi = pmi,
                payRequest = payRequest,
                description = (params[DESCRIPTION] as? JsonPrimitive)?.contentOrNullIfNotString(),
                ttl = (params[TTL] as? JsonPrimitive)?.longOrNull,
                meta = params[META] as? JsonObject,
            )
        }
    }
}

/** Server confirmation that payment was accepted (transparent lifecycle). */
data class PaymentAccepted(
    val amount: Double,
    val pmi: Pmi,
    val meta: JsonObject? = null,
)

/** Server rejection of an attempted payment (transparent lifecycle). */
data class PaymentRejected(
    val pmi: Pmi,
    /** For a bearer asset, the amount actually required. */
    val amount: Double? = null,
    val message: String? = null,
)

/** Parsers for the CEP-8 notifications and errors. */
object PaymentMessages {
    /** Reads a `notifications/payment_required`, or null if this is another notification. */
    fun paymentRequired(notification: JsonRpcNotification): PaymentRequest? {
        if (notification.method != McpMethods.PAYMENT_REQUIRED) return null
        return notification.params?.let { PaymentRequest.parseOrNull(it) }
    }

    fun paymentAccepted(notification: JsonRpcNotification): PaymentAccepted? {
        if (notification.method != McpMethods.PAYMENT_ACCEPTED) return null
        val params = notification.params ?: return null
        val amount = (params[PaymentRequest.AMOUNT] as? JsonPrimitive)?.doubleOrNull ?: return null
        val pmi =
            (params[PaymentRequest.PMI] as? JsonPrimitive)
                ?.contentOrNullIfNotString()
                ?.let { Pmi.parseOrNull(it) } ?: return null
        return PaymentAccepted(amount, pmi, params[PaymentRequest.META] as? JsonObject)
    }

    fun paymentRejected(notification: JsonRpcNotification): PaymentRejected? {
        if (notification.method != McpMethods.PAYMENT_REJECTED) return null
        val params = notification.params ?: return null
        val pmi =
            (params[PaymentRequest.PMI] as? JsonPrimitive)
                ?.contentOrNullIfNotString()
                ?.let { Pmi.parseOrNull(it) } ?: return null
        return PaymentRejected(
            pmi = pmi,
            amount = (params[PaymentRequest.AMOUNT] as? JsonPrimitive)?.doubleOrNull,
            message = (params["message"] as? JsonPrimitive)?.contentOrNullIfNotString(),
        )
    }

    /**
     * The payment options carried by a `-32042 Payment Required` error.
     *
     * Returns null when [error] is a different failure, and an empty list when
     * the error claims payment is required but offers no way to make it — which
     * the caller should treat as malformed rather than as "nothing to pay".
     */
    fun paymentOptions(error: JsonRpcError): List<PaymentRequest>? {
        if (error.code != JsonRpcError.PAYMENT_REQUIRED) return null
        val data = error.data as? JsonObject ?: return emptyList()
        val options = data[PAYMENT_OPTIONS] as? JsonArray ?: return emptyList()
        return options.mapNotNull { (it as? JsonObject)?.let(PaymentRequest::parseOrNull) }
    }

    /** Seconds the server suggests waiting before retrying a `-32043 Payment Pending`. */
    fun retryAfter(error: JsonRpcError): Long? {
        if (error.code != JsonRpcError.PAYMENT_PENDING) return null
        val data = error.data as? JsonObject ?: return null
        return (data[RETRY_AFTER] as? JsonPrimitive)?.longOrNull
    }

    /** Human-readable guidance a payment error carries. */
    fun instructions(error: JsonRpcError): String? =
        (error.data as? JsonObject)
            ?.get(INSTRUCTIONS)
            ?.let { (it as? JsonPrimitive)?.contentOrNullIfNotString() }

    const val PAYMENT_OPTIONS = "payment_options"
    const val RETRY_AFTER = "retry_after"
    const val INSTRUCTIONS = "instructions"
}

private fun JsonPrimitive.contentOrNullIfNotString() = if (isString) content else null
