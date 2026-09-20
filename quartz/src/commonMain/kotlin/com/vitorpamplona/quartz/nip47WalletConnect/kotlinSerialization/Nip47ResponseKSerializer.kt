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

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.anyMapOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.anyToJsonElement
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.decodeRootJsonObject
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.longOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objectListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.toAnyMap
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
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorCode
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcUnknownResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaySuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ReceiveSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageSuccessResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object Nip47ResponseKSerializer : KSerializer<Response> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Nip47Response")

    override fun serialize(
        encoder: Encoder,
        value: Response,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject =
            buildJsonObject {
                put("result_type", value.resultType)
                when (value) {
                    is NwcUnknownResponse -> {
                        value.result?.let { put("result", anyToJsonElement(it)) }
                    }

                    is NwcErrorResponse -> {
                        value.error?.let { put("error", serializeNwcError(it)) }
                    }

                    is PayInvoiceSuccessResponse -> {
                        value.result?.let { put("result", serializePayInvoiceResult(it)) }
                    }

                    is PaySuccessResponse -> {
                        value.result?.let { put("result", serializePayResult(it)) }
                    }

                    is ReceiveSuccessResponse -> {
                        value.result?.let { put("result", serializeReceiveResult(it)) }
                    }

                    is PayInvoiceErrorResponse -> {
                        value.error?.let { put("error", serializePayInvoiceErrorParams(it)) }
                    }

                    is PayKeysendSuccessResponse -> {
                        value.result?.let { put("result", serializePayKeysendResult(it)) }
                    }

                    is MakeInvoiceSuccessResponse -> {
                        serializeTransaction(value.result)?.let { put("result", it) }
                    }

                    is LookupInvoiceSuccessResponse -> {
                        serializeTransaction(value.result)?.let { put("result", it) }
                    }

                    is ListTransactionsSuccessResponse -> {
                        value.result?.let { put("result", serializeListTransactionsResult(it)) }
                    }

                    is GetBalanceSuccessResponse -> {
                        value.result?.let { put("result", serializeGetBalanceResult(it)) }
                    }

                    is GetInfoSuccessResponse -> {
                        value.result?.let { put("result", serializeGetInfoResult(it)) }
                    }

                    is GetBudgetSuccessResponse -> {
                        value.result?.let { put("result", serializeGetBudgetResult(it)) }
                    }

                    is SignMessageSuccessResponse -> {
                        value.result?.let { put("result", serializeSignMessageResult(it)) }
                    }

                    is CreateConnectionSuccessResponse -> {
                        value.result?.let { put("result", serializeCreateConnectionResult(it)) }
                    }

                    is MakeHoldInvoiceSuccessResponse -> {
                        serializeTransaction(value.result)?.let { put("result", it) }
                    }

                    is CancelHoldInvoiceSuccessResponse -> {
                        put("result", buildJsonObject {})
                    }

                    is SettleHoldInvoiceSuccessResponse -> {
                        put("result", buildJsonObject {})
                    }
                }
            }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    private fun serializeNwcError(error: NwcError): JsonObject =
        buildJsonObject {
            error.code?.let { put("code", it.name) }
            error.message?.let { put("message", it) }
        }

    private fun serializePayInvoiceResult(result: PayInvoiceSuccessResponse.PayInvoiceResultParams): JsonObject =
        buildJsonObject {
            result.preimage?.let { put("preimage", it) }
            result.fees_paid?.let { put("fees_paid", it) }
        }

    private fun serializePayInvoiceErrorParams(error: PayInvoiceErrorResponse.PayInvoiceErrorParams): JsonObject =
        buildJsonObject {
            error.code?.let { put("code", it.name) }
            error.message?.let { put("message", it) }
        }

    private fun serializePayResult(result: PaySuccessResponse.PayResult): JsonObject =
        buildJsonObject {
            result.transaction_id?.let { put("transaction_id", it) }
            result.state?.let { put("state", it) }
            result.instruction_type?.let { put("instruction_type", it) }
            result.amount?.let { put("amount", it) }
            result.fees_paid?.let { put("fees_paid", it) }
            result.payment_hash?.let { put("payment_hash", it) }
            result.preimage?.let { put("preimage", it) }
            result.payer_proof?.let { put("payer_proof", it) }
            result.txid?.let { put("txid", it) }
            result.failure_reason?.let { put("failure_reason", it) }
            result.created_at?.let { put("created_at", it) }
            result.settled_at?.let { put("settled_at", it) }
        }

    private fun serializeReceiveResult(result: ReceiveSuccessResponse.ReceiveResult): JsonObject =
        buildJsonObject {
            result.bip321?.let { put("bip321", it) }
            result.transaction_id?.let { put("transaction_id", it) }
        }

    private fun serializePayKeysendResult(result: PayKeysendSuccessResponse.PayKeysendResult): JsonObject =
        buildJsonObject {
            result.preimage?.let { put("preimage", it) }
            result.fees_paid?.let { put("fees_paid", it) }
        }

    private fun serializeListTransactionsResult(result: ListTransactionsSuccessResponse.ListTransactionsResult): JsonObject =
        buildJsonObject {
            result.transactions?.let { transactions ->
                put(
                    "transactions",
                    buildJsonArray {
                        transactions.forEach { serializeTransaction(it)?.let { t -> add(t) } }
                    },
                )
            }
            result.total_count?.let { put("total_count", it) }
        }

    private fun serializeGetBalanceResult(result: GetBalanceSuccessResponse.GetBalanceResult): JsonObject =
        buildJsonObject {
            result.balance?.let { put("balance", it) }
        }

    private fun serializeGetInfoResult(result: GetInfoSuccessResponse.GetInfoResult): JsonObject =
        buildJsonObject {
            result.alias?.let { put("alias", it) }
            result.color?.let { put("color", it) }
            result.pubkey?.let { put("pubkey", it) }
            result.network?.let { put("network", it) }
            result.block_height?.let { put("block_height", it) }
            result.block_hash?.let { put("block_hash", it) }
            result.methods?.let { methods ->
                put("methods", buildJsonArray { methods.forEach { add(it) } })
            }
            result.notifications?.let { notifications ->
                put("notifications", buildJsonArray { notifications.forEach { add(it) } })
            }
            result.metadata?.let { put("metadata", anyToJsonElement(it)) }
            result.lud16?.let { put("lud16", it) }
        }

    private fun serializeGetBudgetResult(result: GetBudgetSuccessResponse.GetBudgetResult): JsonObject =
        buildJsonObject {
            result.used_budget?.let { put("used_budget", it) }
            result.total_budget?.let { put("total_budget", it) }
            result.renews_at?.let { put("renews_at", it) }
            result.renewal_period?.let { put("renewal_period", it) }
        }

    private fun serializeSignMessageResult(result: SignMessageSuccessResponse.SignMessageResult): JsonObject =
        buildJsonObject {
            result.message?.let { put("message", it) }
            result.signature?.let { put("signature", it) }
        }

    private fun serializeCreateConnectionResult(result: CreateConnectionSuccessResponse.CreateConnectionResult): JsonObject =
        buildJsonObject {
            result.wallet_pubkey?.let { put("wallet_pubkey", it) }
        }

    override fun deserialize(decoder: Decoder): Response {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = decoder.decodeRootJsonObject("An NWC response")
        val resultType = jsonObject.stringOrNull("result_type")
        val hasError = jsonObject["error"]?.let { it !is JsonNull } ?: false
        val hasResult = jsonObject["result"]?.let { it !is JsonNull } ?: false

        if (hasError) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> {
                    parsePayInvoiceError(jsonObject)
                }

                else -> {
                    val error = jsonObject.objOrNull("error")?.let { parseNwcError(it) }
                    NwcErrorResponse(resultType ?: "", error)
                }
            }
        }

        if (hasResult || resultType != null) {
            return when (resultType) {
                NwcMethod.PAY_INVOICE -> {
                    parsePayInvoiceSuccess(jsonObject)
                }

                NwcMethod.PAY -> {
                    parsePaySuccess(jsonObject)
                }

                NwcMethod.RECEIVE -> {
                    parseReceiveSuccess(jsonObject)
                }

                NwcMethod.PAY_KEYSEND -> {
                    parsePayKeysendSuccess(jsonObject)
                }

                NwcMethod.MAKE_INVOICE -> {
                    MakeInvoiceSuccessResponse(parseTransaction(jsonObject.objOrNull("result")))
                }

                NwcMethod.LOOKUP_INVOICE -> {
                    LookupInvoiceSuccessResponse(parseTransaction(jsonObject.objOrNull("result")))
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
                    MakeHoldInvoiceSuccessResponse(parseTransaction(jsonObject.objOrNull("result")))
                }

                NwcMethod.CANCEL_HOLD_INVOICE -> {
                    CancelHoldInvoiceSuccessResponse()
                }

                NwcMethod.SETTLE_HOLD_INVOICE -> {
                    SettleHoldInvoiceSuccessResponse()
                }

                else -> {
                    // backward compatibility: guess by result content
                    val resultObj = jsonObject.objOrNull("result")
                    if (resultObj?.containsKey("preimage") == true) {
                        return parsePayInvoiceSuccess(jsonObject)
                    }
                    // A result_type from a newer NIP-47, or an extension we do not
                    // implement. The response is well-formed; hand it back with the
                    // result intact rather than failing the parse.
                    NwcUnknownResponse(resultType ?: "", resultObj?.toAnyMap())
                }
            }
        }

        // Neither result nor error nor result_type: nothing to dispatch on, but the
        // payload was still a valid JSON object, so surface it rather than throw.
        return NwcUnknownResponse(resultType ?: "", jsonObject.objOrNull("result")?.toAnyMap())
    }

    private fun parseNwcError(obj: JsonObject): NwcError {
        val code =
            obj.stringOrNull("code")?.let { codeName ->
                try {
                    NwcErrorCode.valueOf(codeName)
                } catch (_: Exception) {
                    null
                }
            }
        return NwcError(code, obj.stringOrNull("message"))
    }

    fun serializeTransaction(transaction: NwcTransaction?): JsonObject? {
        if (transaction == null) return null
        return buildJsonObject {
            transaction.type?.let { put("type", it) }
            transaction.state?.let { put("state", it) }
            transaction.invoice?.let { put("invoice", it) }
            transaction.description?.let { put("description", it) }
            transaction.description_hash?.let { put("description_hash", it) }
            transaction.preimage?.let { put("preimage", it) }
            transaction.payment_hash?.let { put("payment_hash", it) }
            transaction.amount?.let { put("amount", it) }
            transaction.fees_paid?.let { put("fees_paid", it) }
            transaction.created_at?.let { put("created_at", it) }
            transaction.expires_at?.let { put("expires_at", it) }
            transaction.settled_at?.let { put("settled_at", it) }
            transaction.settle_deadline?.let { put("settle_deadline", it) }
            transaction.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }
    }

    fun parseTransaction(obj: JsonObject?): NwcTransaction? {
        if (obj == null) return null
        return NwcTransaction(
            type = obj.stringOrNull("type"),
            state = obj.stringOrNull("state"),
            invoice = obj.stringOrNull("invoice"),
            description = obj.stringOrNull("description"),
            description_hash = obj.stringOrNull("description_hash"),
            preimage = obj.stringOrNull("preimage"),
            payment_hash = obj.stringOrNull("payment_hash"),
            amount = obj.longOrNull("amount"),
            fees_paid = obj.longOrNull("fees_paid"),
            created_at = obj.longOrNull("created_at"),
            expires_at = obj.longOrNull("expires_at"),
            settled_at = obj.longOrNull("settled_at"),
            settle_deadline = obj.longOrNull("settle_deadline"),
            metadata = obj.anyMapOrNull("metadata"),
        )
    }

    private fun parsePayInvoiceSuccess(json: JsonObject): PayInvoiceSuccessResponse {
        val result = json.objOrNull("result")
        return PayInvoiceSuccessResponse(
            result?.let {
                PayInvoiceSuccessResponse.PayInvoiceResultParams(
                    preimage = it.stringOrNull("preimage"),
                    fees_paid = it.longOrNull("fees_paid"),
                )
            },
        )
    }

    private fun parsePayInvoiceError(json: JsonObject): PayInvoiceErrorResponse {
        val error = json.objOrNull("error")
        return PayInvoiceErrorResponse(
            error?.let {
                PayInvoiceErrorResponse.PayInvoiceErrorParams(
                    code =
                        it.stringOrNull("code")?.let { codeName ->
                            try {
                                NwcErrorCode.valueOf(codeName)
                            } catch (_: Exception) {
                                null
                            }
                        },
                    message = it.stringOrNull("message"),
                )
            },
        )
    }

    private fun parsePaySuccess(json: JsonObject): PaySuccessResponse {
        val result = json.objOrNull("result")
        return PaySuccessResponse(
            result?.let {
                // contentOrNull, not content: an explicit JSON `null` (which Jackson writes
                // for every null field) must read back as a real null, not the string "null".
                PaySuccessResponse.PayResult(
                    transaction_id = it.stringOrNull("transaction_id"),
                    state = it.stringOrNull("state"),
                    instruction_type = it.stringOrNull("instruction_type"),
                    amount = it.longOrNull("amount"),
                    fees_paid = it.longOrNull("fees_paid"),
                    payment_hash = it.stringOrNull("payment_hash"),
                    preimage = it.stringOrNull("preimage"),
                    payer_proof = it.stringOrNull("payer_proof"),
                    txid = it.stringOrNull("txid"),
                    failure_reason = it.stringOrNull("failure_reason"),
                    created_at = it.longOrNull("created_at"),
                    settled_at = it.longOrNull("settled_at"),
                )
            },
        )
    }

    private fun parseReceiveSuccess(json: JsonObject): ReceiveSuccessResponse {
        val result = json.objOrNull("result")
        return ReceiveSuccessResponse(
            result?.let {
                ReceiveSuccessResponse.ReceiveResult(
                    bip321 = it.stringOrNull("bip321"),
                    transaction_id = it.stringOrNull("transaction_id"),
                )
            },
        )
    }

    private fun parsePayKeysendSuccess(json: JsonObject): PayKeysendSuccessResponse {
        val result = json.objOrNull("result")
        return PayKeysendSuccessResponse(
            result?.let {
                PayKeysendSuccessResponse.PayKeysendResult(
                    preimage = it.stringOrNull("preimage"),
                    fees_paid = it.longOrNull("fees_paid"),
                )
            },
        )
    }

    private fun parseListTransactionsSuccess(json: JsonObject): ListTransactionsSuccessResponse {
        val result = json.objOrNull("result")
        return ListTransactionsSuccessResponse(
            result?.let {
                ListTransactionsSuccessResponse.ListTransactionsResult(
                    transactions = it.objectListOrNull("transactions")?.mapNotNull { t -> parseTransaction(t) },
                    total_count = it.longOrNull("total_count"),
                )
            },
        )
    }

    private fun parseGetBalanceSuccess(json: JsonObject): GetBalanceSuccessResponse {
        val result = json.objOrNull("result")
        return GetBalanceSuccessResponse(
            result?.let {
                GetBalanceSuccessResponse.GetBalanceResult(
                    balance = it.longOrNull("balance"),
                )
            },
        )
    }

    private fun parseGetInfoSuccess(json: JsonObject): GetInfoSuccessResponse {
        val result = json.objOrNull("result")
        return GetInfoSuccessResponse(
            result?.let {
                GetInfoSuccessResponse.GetInfoResult(
                    alias = it.stringOrNull("alias"),
                    color = it.stringOrNull("color"),
                    pubkey = it.stringOrNull("pubkey"),
                    network = it.stringOrNull("network"),
                    block_height = it.longOrNull("block_height"),
                    block_hash = it.stringOrNull("block_hash"),
                    methods = it.stringListOrNull("methods"),
                    notifications = it.stringListOrNull("notifications"),
                    metadata = it.anyMapOrNull("metadata"),
                    lud16 = it.stringOrNull("lud16"),
                )
            },
        )
    }

    private fun parseGetBudgetSuccess(json: JsonObject): GetBudgetSuccessResponse {
        val result = json.objOrNull("result")
        return GetBudgetSuccessResponse(
            result?.let {
                GetBudgetSuccessResponse.GetBudgetResult(
                    used_budget = it.longOrNull("used_budget"),
                    total_budget = it.longOrNull("total_budget"),
                    renews_at = it.longOrNull("renews_at"),
                    renewal_period = it.stringOrNull("renewal_period"),
                )
            },
        )
    }

    private fun parseSignMessageSuccess(json: JsonObject): SignMessageSuccessResponse {
        val result = json.objOrNull("result")
        return SignMessageSuccessResponse(
            result?.let {
                SignMessageSuccessResponse.SignMessageResult(
                    message = it.stringOrNull("message"),
                    signature = it.stringOrNull("signature"),
                )
            },
        )
    }

    private fun parseCreateConnectionSuccess(json: JsonObject): CreateConnectionSuccessResponse {
        val result = json.objOrNull("result")
        return CreateConnectionSuccessResponse(
            result?.let {
                CreateConnectionSuccessResponse.CreateConnectionResult(
                    wallet_pubkey = it.stringOrNull("wallet_pubkey"),
                )
            },
        )
    }
}
