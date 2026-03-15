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

import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.CreateConnectionParams
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetBudgetMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsParams
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendParams
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceParams
import com.vitorpamplona.quartz.nip47WalletConnect.SignMessageMethod
import com.vitorpamplona.quartz.nip47WalletConnect.SignMessageParams
import com.vitorpamplona.quartz.nip47WalletConnect.TlvRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object Nip47RequestKSerializer : KSerializer<Request> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Nip47Request")

    override fun serialize(
        encoder: Encoder,
        value: Request,
    ): Unit = throw UnsupportedOperationException("NIP-47 Request serialization not supported")

    override fun deserialize(decoder: Decoder): Request {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val method = jsonObject["method"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

        return when (method) {
            NwcMethod.PAY_INVOICE -> parsePayInvoice(jsonObject)
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
        val params = json["params"]?.jsonObject
        return PayInvoiceMethod(
            params?.let {
                PayInvoiceParams(
                    invoice = it["invoice"]?.jsonPrimitive?.content,
                    amount = it["amount"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parsePayKeysend(json: JsonObject): PayKeysendMethod {
        val params = json["params"]?.jsonObject
        return PayKeysendMethod(
            params?.let {
                PayKeysendParams(
                    amount = it["amount"]?.jsonPrimitive?.longOrNull,
                    pubkey = it["pubkey"]?.jsonPrimitive?.content,
                    preimage = it["preimage"]?.jsonPrimitive?.content,
                    tlv_records =
                        it["tlv_records"]?.jsonArray?.map { record ->
                            val obj = record.jsonObject
                            TlvRecord(
                                type = obj["type"]?.jsonPrimitive?.longOrNull,
                                value = obj["value"]?.jsonPrimitive?.content,
                            )
                        },
                )
            },
        )
    }

    private fun parseMakeInvoice(json: JsonObject): MakeInvoiceMethod {
        val params = json["params"]?.jsonObject
        return MakeInvoiceMethod(
            params?.let {
                MakeInvoiceParams(
                    amount = it["amount"]?.jsonPrimitive?.longOrNull,
                    description = it["description"]?.jsonPrimitive?.content,
                    description_hash = it["description_hash"]?.jsonPrimitive?.content,
                    expiry = it["expiry"]?.jsonPrimitive?.longOrNull,
                )
            },
        )
    }

    private fun parseLookupInvoice(json: JsonObject): LookupInvoiceMethod {
        val params = json["params"]?.jsonObject
        return LookupInvoiceMethod(
            params?.let {
                LookupInvoiceParams(
                    payment_hash = it["payment_hash"]?.jsonPrimitive?.content,
                    invoice = it["invoice"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseListTransactions(json: JsonObject): ListTransactionsMethod {
        val params = json["params"]?.jsonObject
        return ListTransactionsMethod(
            params?.let {
                ListTransactionsParams(
                    from = it["from"]?.jsonPrimitive?.longOrNull,
                    until = it["until"]?.jsonPrimitive?.longOrNull,
                    limit = it["limit"]?.jsonPrimitive?.intOrNull,
                    offset = it["offset"]?.jsonPrimitive?.intOrNull,
                    unpaid = it["unpaid"]?.jsonPrimitive?.booleanOrNull,
                    unpaid_outgoing = it["unpaid_outgoing"]?.jsonPrimitive?.booleanOrNull,
                    unpaid_incoming = it["unpaid_incoming"]?.jsonPrimitive?.booleanOrNull,
                    type = it["type"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseSignMessage(json: JsonObject): SignMessageMethod {
        val params = json["params"]?.jsonObject
        return SignMessageMethod(
            params?.let {
                SignMessageParams(
                    message = it["message"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseCreateConnection(json: JsonObject): CreateConnectionMethod {
        val params = json["params"]?.jsonObject
        return CreateConnectionMethod(
            params?.let {
                CreateConnectionParams(
                    pubkey = it["pubkey"]?.jsonPrimitive?.content,
                    name = it["name"]?.jsonPrimitive?.content,
                    request_methods = it["request_methods"]?.jsonArray?.map { m -> m.jsonPrimitive.content },
                    notification_types = it["notification_types"]?.jsonArray?.map { n -> n.jsonPrimitive.content },
                    max_amount = it["max_amount"]?.jsonPrimitive?.longOrNull,
                    budget_renewal = it["budget_renewal"]?.jsonPrimitive?.content,
                    expires_at = it["expires_at"]?.jsonPrimitive?.longOrNull,
                    isolated = it["isolated"]?.jsonPrimitive?.booleanOrNull,
                )
            },
        )
    }

    private fun parseMakeHoldInvoice(json: JsonObject): MakeHoldInvoiceMethod {
        val params = json["params"]?.jsonObject
        return MakeHoldInvoiceMethod(
            params?.let {
                MakeHoldInvoiceParams(
                    amount = it["amount"]?.jsonPrimitive?.longOrNull,
                    description = it["description"]?.jsonPrimitive?.content,
                    description_hash = it["description_hash"]?.jsonPrimitive?.content,
                    expiry = it["expiry"]?.jsonPrimitive?.longOrNull,
                    payment_hash = it["payment_hash"]?.jsonPrimitive?.content,
                    min_cltv_expiry_delta = it["min_cltv_expiry_delta"]?.jsonPrimitive?.intOrNull,
                )
            },
        )
    }

    private fun parseCancelHoldInvoice(json: JsonObject): CancelHoldInvoiceMethod {
        val params = json["params"]?.jsonObject
        return CancelHoldInvoiceMethod(
            params?.let {
                CancelHoldInvoiceParams(
                    payment_hash = it["payment_hash"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private fun parseSettleHoldInvoice(json: JsonObject): SettleHoldInvoiceMethod {
        val params = json["params"]?.jsonObject
        return SettleHoldInvoiceMethod(
            params?.let {
                SettleHoldInvoiceParams(
                    preimage = it["preimage"]?.jsonPrimitive?.content,
                )
            },
        )
    }
}
