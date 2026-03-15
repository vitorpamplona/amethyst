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
package com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization

import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.CreateConnectionSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetBudgetSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcError
import com.vitorpamplona.quartz.nip47WalletConnect.NwcErrorCode
import com.vitorpamplona.quartz.nip47WalletConnect.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.SignMessageSuccessResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object Nip47ResponseKSerializer : KSerializer<Response> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Nip47Response")

    override fun serialize(
        encoder: Encoder,
        value: Response,
    ): Unit = throw UnsupportedOperationException("NIP-47 Response serialization not supported")

    override fun deserialize(decoder: Decoder): Response {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val resultType = jsonObject["result_type"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
        val hasError = jsonObject["error"]?.let { it !is JsonNull } ?: false
        val hasResult = jsonObject["result"]?.let { it !is JsonNull } ?: false

        if (hasError) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> {
                    parsePayInvoiceError(jsonObject)
                }

                else -> {
                    val error = jsonObject["error"]?.jsonObject?.let { parseNwcError(it) }
                    NwcErrorResponse(resultType ?: "", error)
                }
            }
        }

        if (hasResult || resultType != null) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> {
                    parsePayInvoiceSuccess(jsonObject)
                }

                NwcMethod.PAY_KEYSEND -> {
                    parsePayKeysendSuccess(jsonObject)
                }

                NwcMethod.MAKE_INVOICE -> {
                    MakeInvoiceSuccessResponse(parseTransaction(jsonObject["result"]?.jsonObject))
                }

                NwcMethod.LOOKUP_INVOICE -> {
                    LookupInvoiceSuccessResponse(parseTransaction(jsonObject["result"]?.jsonObject))
                }

                NwcMethod.LIST_TRANSACTIONS -> {
                    parseListTransactionsSuccess(jsonObject)
                }

                NwcMethod.GET_BALANCE -> {
                    parseGetBalanceSuccess(jsonObject)
                }

                NwcMethod.GET_INFO -> {
                    parseGetInfoSuccess(jsonObject)
                }

                NwcMethod.GET_BUDGET -> {
                    parseGetBudgetSuccess(jsonObject)
                }

                NwcMethod.SIGN_MESSAGE -> {
                    parseSignMessageSuccess(jsonObject)
                }

                NwcMethod.CREATE_CONNECTION -> {
                    parseCreateConnectionSuccess(jsonObject)
                }

                NwcMethod.MAKE_HOLD_INVOICE -> {
                    MakeHoldInvoiceSuccessResponse(parseTransaction(jsonObject["result"]?.jsonObject))
                }

                NwcMethod.CANCEL_HOLD_INVOICE -> {
                    CancelHoldInvoiceSuccessResponse()
                }

                NwcMethod.SETTLE_HOLD_INVOICE -> {
                    SettleHoldInvoiceSuccessResponse()
                }

                else -> {
                    // backward compatibility: guess by result content
                    val resultObj = jsonObject["result"]?.jsonObject
                    if (resultObj?.containsKey("preimage") == true) {
                        return parsePayInvoiceSuccess(jsonObject)
                    }
                    throw IllegalArgumentException("Unknown NWC response type: $resultType")
                }
            }
        }

        throw IllegalArgumentException("NWC response has neither result nor error")
    }

    private fun parseNwcError(obj: JsonObject): NwcError {
        val code =
            obj["code"]?.jsonPrimitive?.content?.let { codeName ->
                try {
                    NwcErrorCode.valueOf(codeName)
                } catch (_: Exception) {
                    null
                }
            }
        return NwcError(code, obj["message"]?.jsonPrimitive?.content)
    }

    fun parseTransaction(obj: JsonObject?): NwcTransaction? {
        if (obj == null) return null
        return NwcTransaction(
            type = obj["type"]?.jsonPrimitive?.content,
            state = obj["state"]?.jsonPrimitive?.content,
            invoice = obj["invoice"]?.jsonPrimitive?.content,
            description = obj["description"]?.jsonPrimitive?.content,
            description_hash = obj["description_hash"]?.jsonPrimitive?.content,
            preimage = obj["preimage"]?.jsonPrimitive?.content,
            payment_hash = obj["payment_hash"]?.jsonPrimitive?.content,
            amount = obj["amount"]?.jsonPrimitive?.longOrNull,
            fees_paid = obj["fees_paid"]?.jsonPrimitive?.longOrNull,
            created_at = obj["created_at"]?.jsonPrimitive?.longOrNull,
            expires_at = obj["expires_at"]?.jsonPrimitive?.longOrNull,
            settled_at = obj["settled_at"]?.jsonPrimitive?.longOrNull,
            settle_deadline = obj["settle_deadline"]?.jsonPrimitive?.longOrNull,
        )
    }

    private fun parsePayInvoiceSuccess(json: JsonObject): PayInvoiceSuccessResponse {
        val result = json["result"]?.jsonObject
        return PayInvoiceSuccessResponse(
            result?.let {
                PayInvoiceSuccessResponse.PayInvoiceResultParams(
                    preimage = it["preimage"]?.jsonPrimitive?.content,
                    fees_paid = it["fees_paid"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parsePayInvoiceError(json: JsonObject): PayInvoiceErrorResponse {
        val error = json["error"]?.jsonObject
        return PayInvoiceErrorResponse(
            error?.let {
                PayInvoiceErrorResponse.PayInvoiceErrorParams(
                    code =
                        it["code"]?.jsonPrimitive?.content?.let { codeName ->
                            try {
                                NwcErrorCode.valueOf(codeName)
                            } catch (_: Exception) {
                                null
                            }
                        },
                    message = it["message"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parsePayKeysendSuccess(json: JsonObject): PayKeysendSuccessResponse {
        val result = json["result"]?.jsonObject
        return PayKeysendSuccessResponse(
            result?.let {
                PayKeysendSuccessResponse.PayKeysendResult(
                    preimage = it["preimage"]?.jsonPrimitive?.content,
                    fees_paid = it["fees_paid"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parseListTransactionsSuccess(json: JsonObject): ListTransactionsSuccessResponse {
        val result = json["result"]?.jsonObject
        return ListTransactionsSuccessResponse(
            result?.let {
                ListTransactionsSuccessResponse.ListTransactionsResult(
                    transactions = it["transactions"]?.jsonArray?.mapNotNull { t -> parseTransaction(t.jsonObject) },
                    total_count = it["total_count"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parseGetBalanceSuccess(json: JsonObject): GetBalanceSuccessResponse {
        val result = json["result"]?.jsonObject
        return GetBalanceSuccessResponse(
            result?.let {
                GetBalanceSuccessResponse.GetBalanceResult(
                    balance = it["balance"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parseGetInfoSuccess(json: JsonObject): GetInfoSuccessResponse {
        val result = json["result"]?.jsonObject
        return GetInfoSuccessResponse(
            result?.let {
                GetInfoSuccessResponse.GetInfoResult(
                    alias = it["alias"]?.jsonPrimitive?.content,
                    color = it["color"]?.jsonPrimitive?.content,
                    pubkey = it["pubkey"]?.jsonPrimitive?.content,
                    network = it["network"]?.jsonPrimitive?.content,
                    block_height = it["block_height"]?.jsonPrimitive?.longOrNull,
                    block_hash = it["block_hash"]?.jsonPrimitive?.content,
                    methods = it["methods"]?.jsonArray?.map { m -> m.jsonPrimitive.content },
                    notifications = it["notifications"]?.jsonArray?.map { n -> n.jsonPrimitive.content },
                    lud16 = it["lud16"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseGetBudgetSuccess(json: JsonObject): GetBudgetSuccessResponse {
        val result = json["result"]?.jsonObject
        return GetBudgetSuccessResponse(
            result?.let {
                GetBudgetSuccessResponse.GetBudgetResult(
                    used_budget = it["used_budget"]?.jsonPrimitive?.longOrNull,
                    total_budget = it["total_budget"]?.jsonPrimitive?.longOrNull,
                    renews_at = it["renews_at"]?.jsonPrimitive?.longOrNull,
                    renewal_period = it["renewal_period"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseSignMessageSuccess(json: JsonObject): SignMessageSuccessResponse {
        val result = json["result"]?.jsonObject
        return SignMessageSuccessResponse(
            result?.let {
                SignMessageSuccessResponse.SignMessageResult(
                    message = it["message"]?.jsonPrimitive?.content,
                    signature = it["signature"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseCreateConnectionSuccess(json: JsonObject): CreateConnectionSuccessResponse {
        val result = json["result"]?.jsonObject
        return CreateConnectionSuccessResponse(
            result?.let {
                CreateConnectionSuccessResponse.CreateConnectionResult(
                    wallet_pubkey = it["wallet_pubkey"]?.jsonPrimitive?.content,
                )
            },
        )
    }
}
