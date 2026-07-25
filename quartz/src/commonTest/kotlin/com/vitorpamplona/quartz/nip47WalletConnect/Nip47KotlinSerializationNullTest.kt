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

import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47RequestKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47ResponseKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaySuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ReceiveMethod
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Exercises the **kotlinx** (native/iOS) NWC serializers directly — not through
 * `OptimizedJsonMapper`, whose JVM actual is Jackson — to cover the cross-backend
 * asymmetry: Jackson (JVM/Android) writes explicit `null` for every null field, and a
 * native peer parsing that output must read those as real nulls, not the string "null",
 * and must not crash on a null `metadata` object.
 */
class Nip47KotlinSerializationNullTest {
    @Test
    fun payRequestWithExplicitNullMetadataParsesWithoutCrashing() {
        val input = """{"method":"pay","params":{"payment":"bitcoin:?lno=lno1abc","amount":21000,"payer_note":"note","metadata":null}}"""
        val req = Json.decodeFromString(Nip47RequestKSerializer, input)
        assertIs<PayMethod>(req)
        assertEquals("bitcoin:?lno=lno1abc", req.params?.payment)
        assertEquals("note", req.params?.payer_note)
        assertNull(req.params?.metadata)
    }

    @Test
    fun receiveRequestWithExplicitNullMetadataParsesWithoutCrashing() {
        val input = """{"method":"receive","params":{"amount":21000,"description":null,"metadata":null}}"""
        val req = Json.decodeFromString(Nip47RequestKSerializer, input)
        assertIs<ReceiveMethod>(req)
        assertNull(req.params?.description)
        assertNull(req.params?.metadata)
    }

    @Test
    fun paySuccessWithExplicitNullFieldsYieldsRealNullsNotTheStringNull() {
        val input =
            """{"result_type":"pay","result":{"state":"settled","preimage":"abc","transaction_id":null,"txid":null,"failure_reason":null,"payer_proof":null,"instruction_type":null}}"""
        val resp = Json.decodeFromString(Nip47ResponseKSerializer, input)
        assertIs<PaySuccessResponse>(resp)
        assertEquals("settled", resp.result?.state)
        assertEquals("abc", resp.result?.preimage)
        assertNull(resp.result?.transaction_id)
        assertNull(resp.result?.txid)
        assertNull(resp.result?.failure_reason)
        assertNull(resp.result?.payer_proof)
        assertNull(resp.result?.instruction_type)
    }
}
