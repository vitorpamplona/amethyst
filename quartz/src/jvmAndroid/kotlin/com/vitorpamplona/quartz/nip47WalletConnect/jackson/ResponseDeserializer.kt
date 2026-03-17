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
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CancelHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBudgetSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcError
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageSuccessResponse
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
                NwcMethod.PAY_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
                }

                else -> {
                    val error = jp.codec.treeToValue(jsonObject.get("error"), NwcError::class.java)
                    NwcErrorResponse(resultType ?: "", error)
                }
            }
        }

        if (hasResult || resultType != null) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
                }

                NwcMethod.PAY_KEYSEND -> {
                    jp.codec.treeToValue(jsonObject, PayKeysendSuccessResponse::class.java)
                }

                NwcMethod.MAKE_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, MakeInvoiceSuccessResponse::class.java)
                }

                NwcMethod.LOOKUP_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, LookupInvoiceSuccessResponse::class.java)
                }

                NwcMethod.LIST_TRANSACTIONS -> {
                    jp.codec.treeToValue(jsonObject, ListTransactionsSuccessResponse::class.java)
                }

                NwcMethod.GET_BALANCE -> {
                    jp.codec.treeToValue(jsonObject, GetBalanceSuccessResponse::class.java)
                }

                NwcMethod.GET_INFO -> {
                    jp.codec.treeToValue(jsonObject, GetInfoSuccessResponse::class.java)
                }

                NwcMethod.GET_BUDGET -> {
                    jp.codec.treeToValue(jsonObject, GetBudgetSuccessResponse::class.java)
                }

                NwcMethod.SIGN_MESSAGE -> {
                    jp.codec.treeToValue(jsonObject, SignMessageSuccessResponse::class.java)
                }

                NwcMethod.CREATE_CONNECTION -> {
                    jp.codec.treeToValue(jsonObject, CreateConnectionSuccessResponse::class.java)
                }

                NwcMethod.MAKE_HOLD_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, MakeHoldInvoiceSuccessResponse::class.java)
                }

                NwcMethod.CANCEL_HOLD_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, CancelHoldInvoiceSuccessResponse::class.java)
                }

                NwcMethod.SETTLE_HOLD_INVOICE -> {
                    jp.codec.treeToValue(jsonObject, SettleHoldInvoiceSuccessResponse::class.java)
                }

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
