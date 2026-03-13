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
package com.vitorpamplona.quartz.nip47WalletConnect.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcError
import com.vitorpamplona.quartz.nip47WalletConnect.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.utils.asTextOrNull

class ResponseDeserializer : StdDeserializer<Response>(Response::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Response? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val resultType = jsonObject.get("result_type")?.asTextOrNull()
        val hasError = jsonObject.has("error") && !jsonObject.get("error").isNull
        val hasResult = jsonObject.has("result") && !jsonObject.get("result").isNull

        if (hasError) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
                else -> {
                    val error = jp.codec.treeToValue(jsonObject.get("error"), NwcError::class.java)
                    NwcErrorResponse(resultType ?: "", error)
                }
            }
        }

        if (hasResult || resultType != null) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
                NwcMethod.PAY_KEYSEND -> jp.codec.treeToValue(jsonObject, PayKeysendSuccessResponse::class.java)
                NwcMethod.MAKE_INVOICE -> jp.codec.treeToValue(jsonObject, MakeInvoiceSuccessResponse::class.java)
                NwcMethod.LOOKUP_INVOICE -> jp.codec.treeToValue(jsonObject, LookupInvoiceSuccessResponse::class.java)
                NwcMethod.LIST_TRANSACTIONS -> jp.codec.treeToValue(jsonObject, ListTransactionsSuccessResponse::class.java)
                NwcMethod.GET_BALANCE -> jp.codec.treeToValue(jsonObject, GetBalanceSuccessResponse::class.java)
                NwcMethod.GET_INFO -> jp.codec.treeToValue(jsonObject, GetInfoSuccessResponse::class.java)
                NwcMethod.MAKE_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, MakeHoldInvoiceSuccessResponse::class.java)
                NwcMethod.CANCEL_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, CancelHoldInvoiceSuccessResponse::class.java)
                NwcMethod.SETTLE_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, SettleHoldInvoiceSuccessResponse::class.java)
                else -> {
                    // tries to guess for backward compatibility
                    if (jsonObject.get("result")?.get("preimage") != null) {
                        return jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
                    }
                    null
                }
            }
        }

        return null
    }
}
