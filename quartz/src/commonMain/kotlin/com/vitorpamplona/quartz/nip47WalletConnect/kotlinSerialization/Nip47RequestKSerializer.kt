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
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.booleanOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.decodeRootJsonObject
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.intOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.longOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objectListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CancelHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CancelHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBudgetMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ReceiveMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ReceiveParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageParams
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.TlvRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object Nip47RequestKSerializer : KSerializer<Request> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Nip47Request")

    override fun serialize(
        encoder: Encoder,
        value: Request,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject =
            buildJsonObject {
                put("method", value.method)
                when (value) {
                    is PayInvoiceMethod -> {
                        value.params?.let { put("params", serializePayInvoiceParams(it)) }
                    }

                    is PayMethod -> {
                        value.params?.let { put("params", serializePayParams(it)) }
                    }

                    is ReceiveMethod -> {
                        value.params?.let { put("params", serializeReceiveParams(it)) }
                    }

                    is PayKeysendMethod -> {
                        value.params?.let { put("params", serializePayKeysendParams(it)) }
                    }

                    is MakeInvoiceMethod -> {
                        value.params?.let { put("params", serializeMakeInvoiceParams(it)) }
                    }

                    is LookupInvoiceMethod -> {
                        value.params?.let { put("params", serializeLookupInvoiceParams(it)) }
                    }

                    is ListTransactionsMethod -> {
                        value.params?.let { put("params", serializeListTransactionsParams(it)) }
                    }

                    is GetBalanceMethod -> {}

                    is GetInfoMethod -> {}

                    is GetBudgetMethod -> {}

                    is SignMessageMethod -> {
                        value.params?.let { put("params", serializeSignMessageParams(it)) }
                    }

                    is CreateConnectionMethod -> {
                        value.params?.let { put("params", serializeCreateConnectionParams(it)) }
                    }

                    is MakeHoldInvoiceMethod -> {
                        value.params?.let { put("params", serializeMakeHoldInvoiceParams(it)) }
                    }

                    is CancelHoldInvoiceMethod -> {
                        value.params?.let { put("params", serializeCancelHoldInvoiceParams(it)) }
                    }

                    is SettleHoldInvoiceMethod -> {
                        value.params?.let { put("params", serializeSettleHoldInvoiceParams(it)) }
                    }
                }
            }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    private fun serializePayInvoiceParams(params: PayInvoiceParams): JsonObject =
        buildJsonObject {
            params.invoice?.let { put("invoice", it) }
            params.amount?.let { put("amount", it) }
            params.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }

    private fun serializePayParams(params: PayParams): JsonObject =
        buildJsonObject {
            params.payment?.let { put("payment", it) }
            params.amount?.let { put("amount", it) }
            params.payer_note?.let { put("payer_note", it) }
            params.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }

    private fun serializeReceiveParams(params: ReceiveParams): JsonObject =
        buildJsonObject {
            params.amount?.let { put("amount", it) }
            params.description?.let { put("description", it) }
            params.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }

    private fun serializePayKeysendParams(params: PayKeysendParams): JsonObject =
        buildJsonObject {
            params.amount?.let { put("amount", it) }
            params.pubkey?.let { put("pubkey", it) }
            params.preimage?.let { put("preimage", it) }
            params.tlv_records?.let { records ->
                put(
                    "tlv_records",
                    buildJsonArray {
                        records.forEach { record ->
                            add(
                                buildJsonObject {
                                    record.type?.let { put("type", it) }
                                    record.value?.let { put("value", it) }
                                },
                            )
                        }
                    },
                )
            }
        }

    private fun serializeMakeInvoiceParams(params: MakeInvoiceParams): JsonObject =
        buildJsonObject {
            params.amount?.let { put("amount", it) }
            params.description?.let { put("description", it) }
            params.description_hash?.let { put("description_hash", it) }
            params.expiry?.let { put("expiry", it) }
            params.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }

    private fun serializeLookupInvoiceParams(params: LookupInvoiceParams): JsonObject =
        buildJsonObject {
            params.payment_hash?.let { put("payment_hash", it) }
            params.invoice?.let { put("invoice", it) }
        }

    private fun serializeListTransactionsParams(params: ListTransactionsParams): JsonObject =
        buildJsonObject {
            params.from?.let { put("from", it) }
            params.until?.let { put("until", it) }
            params.limit?.let { put("limit", it) }
            params.offset?.let { put("offset", it) }
            params.unpaid?.let { put("unpaid", it) }
            params.unpaid_outgoing?.let { put("unpaid_outgoing", it) }
            params.unpaid_incoming?.let { put("unpaid_incoming", it) }
            params.type?.let { put("type", it) }
        }

    private fun serializeSignMessageParams(params: SignMessageParams): JsonObject =
        buildJsonObject {
            params.message?.let { put("message", it) }
        }

    private fun serializeCreateConnectionParams(params: CreateConnectionParams): JsonObject =
        buildJsonObject {
            params.pubkey?.let { put("pubkey", it) }
            params.name?.let { put("name", it) }
            params.request_methods?.let { methods ->
                put("request_methods", buildJsonArray { methods.forEach { add(it) } })
            }
            params.notification_types?.let { types ->
                put("notification_types", buildJsonArray { types.forEach { add(it) } })
            }
            params.max_amount?.let { put("max_amount", it) }
            params.budget_renewal?.let { put("budget_renewal", it) }
            params.expires_at?.let { put("expires_at", it) }
            params.isolated?.let { put("isolated", it) }
            params.metadata?.let { put("metadata", anyToJsonElement(it)) }
        }

    private fun serializeMakeHoldInvoiceParams(params: MakeHoldInvoiceParams): JsonObject =
        buildJsonObject {
            params.amount?.let { put("amount", it) }
            params.description?.let { put("description", it) }
            params.description_hash?.let { put("description_hash", it) }
            params.expiry?.let { put("expiry", it) }
            params.payment_hash?.let { put("payment_hash", it) }
            params.min_cltv_expiry_delta?.let { put("min_cltv_expiry_delta", it) }
        }

    private fun serializeCancelHoldInvoiceParams(params: CancelHoldInvoiceParams): JsonObject =
        buildJsonObject {
            params.payment_hash?.let { put("payment_hash", it) }
        }

    private fun serializeSettleHoldInvoiceParams(params: SettleHoldInvoiceParams): JsonObject =
        buildJsonObject {
            params.preimage?.let { put("preimage", it) }
        }

    override fun deserialize(decoder: Decoder): Request {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = decoder.decodeRootJsonObject("An NWC request")
        val method = jsonObject.stringOrNull("method")

        return when (method) {
            NwcMethod.PAY_INVOICE -> parsePayInvoice(jsonObject)
            NwcMethod.PAY -> parsePay(jsonObject)
            NwcMethod.RECEIVE -> parseReceive(jsonObject)
            NwcMethod.PAY_KEYSEND -> parsePayKeysend(jsonObject)
            NwcMethod.MAKE_INVOICE -> parseMakeInvoice(jsonObject)
            NwcMethod.LOOKUP_INVOICE -> parseLookupInvoice(jsonObject)
            NwcMethod.LIST_TRANSACTIONS -> parseListTransactions(jsonObject)
            NwcMethod.GET_BALANCE -> GetBalanceMethod()
            NwcMethod.GET_INFO -> GetInfoMethod()
            NwcMethod.GET_BUDGET -> GetBudgetMethod()
            NwcMethod.SIGN_MESSAGE -> parseSignMessage(jsonObject)
            NwcMethod.CREATE_CONNECTION -> parseCreateConnection(jsonObject)
            NwcMethod.MAKE_HOLD_INVOICE -> parseMakeHoldInvoice(jsonObject)
            NwcMethod.CANCEL_HOLD_INVOICE -> parseCancelHoldInvoice(jsonObject)
            NwcMethod.SETTLE_HOLD_INVOICE -> parseSettleHoldInvoice(jsonObject)
            else -> throw IllegalArgumentException("Unknown NWC method: $method")
        }
    }

    private fun parsePayInvoice(json: JsonObject): PayInvoiceMethod {
        val params = json.objOrNull("params")
        return PayInvoiceMethod(
            params?.let {
                PayInvoiceParams(
                    invoice = it.stringOrNull("invoice"),
                    amount = it.longOrNull("amount"),
                    metadata = it.anyMapOrNull("metadata"),
                )
            },
        )
    }

    private fun parsePay(json: JsonObject): PayMethod {
        val params = json.objOrNull("params")
        return PayMethod(
            params?.let {
                // contentOrNull / `as? JsonObject` treat an explicit JSON `null` as absent —
                // Jackson (JVM/Android) writes null-valued keys, so a native/iOS peer parsing
                // that output must not read `JsonNull` as the string "null" or crash on it.
                PayParams(
                    payment = it.stringOrNull("payment"),
                    amount = it.longOrNull("amount"),
                    payer_note = it.stringOrNull("payer_note"),
                    metadata = it.anyMapOrNull("metadata"),
                )
            },
        )
    }

    private fun parseReceive(json: JsonObject): ReceiveMethod {
        val params = json.objOrNull("params")
        return ReceiveMethod(
            params?.let {
                ReceiveParams(
                    amount = it.longOrNull("amount"),
                    description = it.stringOrNull("description"),
                    metadata = it.anyMapOrNull("metadata"),
                )
            },
        )
    }

    private fun parsePayKeysend(json: JsonObject): PayKeysendMethod {
        val params = json.objOrNull("params")
        return PayKeysendMethod(
            params?.let {
                PayKeysendParams(
                    amount = it.longOrNull("amount"),
                    pubkey = it.stringOrNull("pubkey"),
                    preimage = it.stringOrNull("preimage"),
                    tlv_records =
                        it.objectListOrNull("tlv_records")?.map { record ->
                            TlvRecord(
                                type = record.longOrNull("type"),
                                value = record.stringOrNull("value"),
                            )
                        },
                )
            },
        )
    }

    private fun parseMakeInvoice(json: JsonObject): MakeInvoiceMethod {
        val params = json.objOrNull("params")
        return MakeInvoiceMethod(
            params?.let {
                MakeInvoiceParams(
                    amount = it.longOrNull("amount"),
                    description = it.stringOrNull("description"),
                    description_hash = it.stringOrNull("description_hash"),
                    expiry = it.longOrNull("expiry"),
                    metadata = it.anyMapOrNull("metadata"),
                )
            },
        )
    }

    private fun parseLookupInvoice(json: JsonObject): LookupInvoiceMethod {
        val params = json.objOrNull("params")
        return LookupInvoiceMethod(
            params?.let {
                LookupInvoiceParams(
                    payment_hash = it.stringOrNull("payment_hash"),
                    invoice = it.stringOrNull("invoice"),
                )
            },
        )
    }

    private fun parseListTransactions(json: JsonObject): ListTransactionsMethod {
        val params = json.objOrNull("params")
        return ListTransactionsMethod(
            params?.let {
                ListTransactionsParams(
                    from = it.longOrNull("from"),
                    until = it.longOrNull("until"),
                    limit = it.intOrNull("limit"),
                    offset = it.intOrNull("offset"),
                    unpaid = it.booleanOrNull("unpaid"),
                    unpaid_outgoing = it.booleanOrNull("unpaid_outgoing"),
                    unpaid_incoming = it.booleanOrNull("unpaid_incoming"),
                    type = it.stringOrNull("type"),
                )
            },
        )
    }

    private fun parseSignMessage(json: JsonObject): SignMessageMethod {
        val params = json.objOrNull("params")
        return SignMessageMethod(
            params?.let {
                SignMessageParams(
                    message = it.stringOrNull("message"),
                )
            },
        )
    }

    private fun parseCreateConnection(json: JsonObject): CreateConnectionMethod {
        val params = json.objOrNull("params")
        return CreateConnectionMethod(
            params?.let {
                CreateConnectionParams(
                    pubkey = it.stringOrNull("pubkey"),
                    name = it.stringOrNull("name"),
                    request_methods = it.stringListOrNull("request_methods"),
                    notification_types = it.stringListOrNull("notification_types"),
                    max_amount = it.longOrNull("max_amount"),
                    budget_renewal = it.stringOrNull("budget_renewal"),
                    expires_at = it.longOrNull("expires_at"),
                    isolated = it.booleanOrNull("isolated"),
                    metadata = it.anyMapOrNull("metadata"),
                )
            },
        )
    }

    private fun parseMakeHoldInvoice(json: JsonObject): MakeHoldInvoiceMethod {
        val params = json.objOrNull("params")
        return MakeHoldInvoiceMethod(
            params?.let {
                MakeHoldInvoiceParams(
                    amount = it.longOrNull("amount"),
                    description = it.stringOrNull("description"),
                    description_hash = it.stringOrNull("description_hash"),
                    expiry = it.longOrNull("expiry"),
                    payment_hash = it.stringOrNull("payment_hash"),
                    min_cltv_expiry_delta = it.intOrNull("min_cltv_expiry_delta"),
                )
            },
        )
    }

    private fun parseCancelHoldInvoice(json: JsonObject): CancelHoldInvoiceMethod {
        val params = json.objOrNull("params")
        return CancelHoldInvoiceMethod(
            params?.let {
                CancelHoldInvoiceParams(
                    payment_hash = it.stringOrNull("payment_hash"),
                )
            },
        )
    }

    private fun parseSettleHoldInvoice(json: JsonObject): SettleHoldInvoiceMethod {
        val params = json.objOrNull("params")
        return SettleHoldInvoiceMethod(
            params?.let {
                SettleHoldInvoiceParams(
                    preimage = it.stringOrNull("preimage"),
                )
            },
        )
    }
}
