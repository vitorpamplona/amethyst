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
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * NIP-47 marks request parameters optional, and a wallet may type one strictly.
 * Sending an absent parameter as an explicit `null` earned
 * `Invalid list_transactions params: from must be an integer` from a real wallet
 * and failed the request outright.
 *
 * The two serialization backends had disagreed since they were written: kotlinx
 * omits nulls (`params.x?.let { put(...) }`), Jackson wrote them reflectively. The
 * same request was two different documents depending on the platform, and only the
 * JVM/Android one was broken.
 */
class Nip47NullParamOmissionTest {
    private val requests: List<Pair<String, Request>> =
        listOf(
            // The reported failure: every field but three is absent.
            "list_transactions" to ListTransactionsMethod.create(limit = 20, offset = 0, unpaid = false),
            "pay_invoice" to PayInvoiceMethod.create("lnbc50n1abc"),
            "make_invoice" to MakeInvoiceMethod.create(amount = 21000L),
            "lookup_invoice" to LookupInvoiceMethod.createByHash("31afdf1"),
            "pay_keysend" to PayKeysendMethod.create(amount = 21000L, pubkey = "0266e4"),
            "create_connection" to CreateConnectionMethod.create(pubkey = "abc123", name = "app"),
            "get_balance" to GetBalanceMethod.create(),
        )

    @Test
    fun noRequestEverSendsANullParam() {
        requests.forEach { (name, request) ->
            val json = OptimizedJsonMapper.toJson(request)
            val params = Json.parseToJsonElement(json).jsonObject["params"] as? JsonObject

            params?.forEach { (key, value) ->
                assertTrue(value !is JsonNull, "$name sent \"$key\": null - omit it instead. Full: $json")
            }
        }
    }

    /** The exact document the failing wallet rejected, now minimal. */
    @Test
    fun listTransactionsCarriesOnlyWhatWasAsked() {
        val json = OptimizedJsonMapper.toJson(ListTransactionsMethod.create(limit = 20, offset = 0, unpaid = false))

        assertEquals(
            """{"method":"list_transactions","params":{"limit":20,"offset":0,"unpaid":false}}""",
            json,
        )
    }

    /**
     * The invariant the bug broke: both backends must produce the same document.
     * Compared as parsed objects, since key ORDER is each backend's own business.
     */
    @Test
    fun bothBackendsProduceTheSameDocument() {
        requests.forEach { (name, request) ->
            val viaJackson = Json.parseToJsonElement(OptimizedJsonMapper.toJson(request)).jsonObject
            val viaKotlinx = Json.parseToJsonElement(Json.encodeToString(Nip47RequestKSerializer, request)).jsonObject

            assertEquals(viaKotlinx, viaJackson, "$name differs between backends")
        }
    }

    /** Omitting a field must not change how the request reads back. */
    @Test
    fun anOmittedParamStillParsesBack() {
        val json = OptimizedJsonMapper.toJson(ListTransactionsMethod.create(limit = 20, offset = 0, unpaid = false))
        val back = OptimizedJsonMapper.fromJsonTo<Request>(json)

        assertIs<ListTransactionsMethod>(back)
        assertEquals(20, back.params?.limit)
        assertEquals(0, back.params?.offset)
        assertEquals(false, back.params?.unpaid)
        assertEquals(null, back.params?.from)
    }
}
