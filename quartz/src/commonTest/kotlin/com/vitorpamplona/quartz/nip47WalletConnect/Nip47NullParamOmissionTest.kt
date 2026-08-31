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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47RequestKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CancelHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ReceiveMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.TlvRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * NIP-47 marks request parameters optional, and a wallet may type one strictly.
 * Sending an absent parameter as an explicit `null` earned
 * `Invalid list_transactions params: from must be an integer` from a real wallet
 * and failed the request outright.
 *
 * EVERY params-bearing method is listed here on purpose. The Jackson mixin
 * registrations that fix this are a hand-maintained list, and unlike the two
 * `when` blocks over the sealed `Request` they are not compiler-checked — so a
 * thirteenth method can be added, serialize correctly on native, and regress on
 * JVM/Android alone. This list is the only thing that would catch that.
 */
class Nip47NullParamOmissionTest {
    private val requests: List<Pair<String, Request>> =
        listOf(
            // The reported failure: five of eight fields absent.
            "list_transactions" to ListTransactionsMethod.create(limit = 20, offset = 0, unpaid = false),
            "pay_invoice" to PayInvoiceMethod.create("lnbc50n1abc"),
            "pay" to PayMethod.create("bitcoin:?lno=lno1abc"),
            "receive" to ReceiveMethod.create(amount = 21000L),
            "pay_keysend" to PayKeysendMethod.create(amount = 21000L, pubkey = "0266e4"),
            // Reaches the NESTED TlvRecord, with one of its two optional fields absent.
            // A record is only checked when the list is non-empty, so the plain
            // pay_keysend fixture above never executes this path.
            "pay_keysend+tlv" to
                PayKeysendMethod.create(
                    amount = 21000L,
                    pubkey = "0266e4",
                    tlvRecords = listOf(TlvRecord(type = 5482373484L)),
                ),
            "make_invoice" to MakeInvoiceMethod.create(amount = 21000L),
            "lookup_invoice" to LookupInvoiceMethod.createByHash("31afdf1"),
            "make_hold_invoice" to MakeHoldInvoiceMethod.create(amount = 21000L, paymentHash = "31afdf1"),
            "cancel_hold_invoice" to CancelHoldInvoiceMethod.create("31afdf1"),
            "settle_hold_invoice" to SettleHoldInvoiceMethod.create("0123456789abcdef"),
            "sign_message" to SignMessageMethod.create("hello"),
            "create_connection" to CreateConnectionMethod.create(pubkey = "abc123", name = "app"),
        )

    @Test
    fun noRequestEverSendsANullParam() {
        requests.forEach { (name, request) ->
            val json = OptimizedJsonMapper.toJson(request)
            val params = Json.parseToJsonElement(json).jsonObject["params"] as? JsonObject

            // RECURSIVE: `tlv_records` holds objects with optional fields of their own,
            // so a null can hide a level below the params object.
            params?.let { assertNoNulls(it, name, json) }
        }
    }

    private fun assertNoNulls(
        element: JsonElement,
        name: String,
        json: String,
    ) {
        when (element) {
            is JsonObject ->
                element.forEach { (key, value) ->
                    assertTrue(value !is JsonNull, "$name sent \"$key\": null - omit it instead. Full: $json")
                    assertNoNulls(value, name, json)
                }

            is JsonArray -> element.forEach { assertNoNulls(it, name, json) }
            else -> Unit
        }
    }

    /**
     * The document the failing wallet rejected, now minimal. Asserted as a KEY SET
     * rather than a literal string: which keys travel is the property under test,
     * while their order is each backend's own business.
     */
    @Test
    fun listTransactionsCarriesOnlyWhatWasAsked() {
        val json = OptimizedJsonMapper.toJson(ListTransactionsMethod.create(limit = 20, offset = 0, unpaid = false))
        val params = assertNotNull(Json.parseToJsonElement(json).jsonObject["params"], "no params in $json").jsonObject

        assertEquals(setOf("limit", "offset", "unpaid"), params.keys, "unexpected keys in $json")
    }

    /**
     * The invariant the bug broke: one wire format, two backends, same document.
     * This is the actual fix — the mixin is only the mechanism that restores it.
     */
    @Test
    fun bothBackendsProduceTheSameDocument() {
        requests.forEach { (name, request) ->
            val viaJackson = Json.parseToJsonElement(OptimizedJsonMapper.toJson(request)).jsonObject
            val viaKotlinx = Json.parseToJsonElement(Json.encodeToString(Nip47RequestKSerializer, request)).jsonObject

            assertEquals(viaKotlinx, viaJackson, "$name differs between backends")
        }
    }
}
