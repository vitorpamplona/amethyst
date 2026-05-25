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
package com.vitorpamplona.quartz.nipBCOnchainZaps.chain

import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the JSON-parsing layer of [EsploraBackend] against representative
 * mempool.space / blockstream.info response bodies. The parse functions are
 * pure (string in, model out) so no HTTP client is exercised.
 */
class EsploraBackendTest {
    private val backend = EsploraBackend({ "https://example.test/api" }, OkHttpClient())

    @Test
    fun parsesConfirmedTransaction() {
        val json =
            """
            {
              "txid": "7e3ab0f...not validated here",
              "version": 2,
              "locktime": 0,
              "vin": [],
              "vout": [
                { "scriptpubkey": "512053A1F6E454DF1AA2776A2814A721372D6258050DE330B3C6D10EE8F4E0DDA343",
                  "scriptpubkey_type": "v1_p2tr", "value": 25000 },
                { "scriptpubkey": "0014abababababababababababababababababababab", "value": 99000 }
              ],
              "status": {
                "confirmed": true,
                "block_height": 800000,
                "block_hash": "00000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "block_time": 1690000000
              }
            }
            """.trimIndent()

        val tx = backend.parseTx(json)
        assertEquals(2, tx.outputs.size)
        assertEquals(25000L, tx.outputs[0].valueSats)
        // scriptPubKey is normalized to lowercase for byte-comparison with our own.
        assertEquals(
            "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343",
            tx.outputs[0].scriptPubKeyHex,
        )
        assertEquals(0, tx.outputs[0].index)
        assertEquals(1, tx.outputs[1].index)
        assertEquals(99000L, tx.outputs[1].valueSats)
        assertEquals(800000L, tx.blockHeight)
        assertEquals(1, tx.confirmations, "a confirmed tx must report ≥1 confirmation")
    }

    @Test
    fun parsesUnconfirmedTransaction() {
        val json =
            """
            { "txid": "abc", "version": 2, "locktime": 0, "vin": [],
              "vout": [ { "scriptpubkey": "5120ff", "value": 1000 } ],
              "status": { "confirmed": false } }
            """.trimIndent()
        val tx = backend.parseTx(json)
        assertEquals(0, tx.confirmations, "an unconfirmed tx must report 0 confirmations")
        assertNull(tx.blockHeight)
    }

    @Test
    fun parsesUtxoList() {
        val json =
            """
            [
              { "txid": "1111111111111111111111111111111111111111111111111111111111111111",
                "vout": 0, "value": 100000,
                "status": { "confirmed": true, "block_height": 799000 } },
              { "txid": "2222222222222222222222222222222222222222222222222222222222222222",
                "vout": 3, "value": 50000,
                "status": { "confirmed": false } }
            ]
            """.trimIndent()

        val utxos = backend.parseUtxoList(json)
        assertEquals(2, utxos.size)
        assertEquals(100000L, utxos[0].valueSats)
        assertEquals(0, utxos[0].vout)
        assertEquals(1, utxos[0].confirmations)
        assertEquals(50000L, utxos[1].valueSats)
        assertEquals(3, utxos[1].vout)
        assertEquals(0, utxos[1].confirmations, "unconfirmed UTXO must report 0 confirmations")
    }

    @Test
    fun parsesAddressTxList_incomingAndOutgoing() {
        // Two transactions:
        //  - tx1: pays 25_000 to our address (incoming, sender = bc1qSENDER)
        //  - tx2: spends 100_000 from our address; 30_000 to bc1qOUT and 65_000 back as change (outgoing)
        val ours = "bc1pours"
        val json =
            """
            [
              {
                "txid": "1111111111111111111111111111111111111111111111111111111111111111",
                "vin": [
                  { "prevout": { "scriptpubkey_address": "bc1qsender", "value": 50000 } }
                ],
                "vout": [
                  { "scriptpubkey_address": "$ours", "value": 25000 },
                  { "scriptpubkey_address": "bc1qsender", "value": 24500 }
                ],
                "status": { "confirmed": true, "block_height": 800000, "block_time": 1700000000 }
              },
              {
                "txid": "2222222222222222222222222222222222222222222222222222222222222222",
                "vin": [
                  { "prevout": { "scriptpubkey_address": "$ours", "value": 100000 } }
                ],
                "vout": [
                  { "scriptpubkey_address": "bc1qout", "value": 30000 },
                  { "scriptpubkey_address": "$ours", "value": 65000 }
                ],
                "status": { "confirmed": false }
              }
            ]
            """.trimIndent()

        val txs = backend.parseAddressTxList(json, ours)
        assertEquals(2, txs.size)

        // tx1: incoming, +25_000 sats.
        assertEquals(25_000L, txs[0].netValueSats)
        assertEquals(1, txs[0].confirmations)
        assertEquals(800_000L, txs[0].blockHeight)
        assertEquals(1_700_000_000L, txs[0].blockTime)
        assertEquals(listOf("bc1qsender"), txs[0].counterpartyAddresses)

        // tx2: outgoing, net = 65_000 - 100_000 = -35_000 (includes fee).
        assertEquals(-35_000L, txs[1].netValueSats)
        assertEquals(0, txs[1].confirmations)
        assertEquals(listOf("bc1qout"), txs[1].counterpartyAddresses)
    }

    @Test
    fun parsesAddressTxList_emptyResponse() {
        val txs = backend.parseAddressTxList("[]", "bc1pignored")
        assertEquals(0, txs.size)
    }

    @Test
    fun parsesMempoolSpaceRecommendedFees() {
        // mempool.space /v1/fees/recommended
        val json =
            """{ "fastestFee": 25, "halfHourFee": 15, "hourFee": 10, "economyFee": 5, "minimumFee": 1 }"""
        val fees = backend.parseRecommendedFees(json)
        assertEquals(25.0, fees.fastSatPerVbyte)
        assertEquals(15.0, fees.normalSatPerVbyte)
        assertEquals(10.0, fees.slowSatPerVbyte)
    }

    @Test
    fun parsesBlockstreamFeeEstimates() {
        // blockstream.info / standard Esplora /fee-estimates: block target → sat/vB.
        val json =
            """
            { "1": 87.0, "2": 87.0, "3": 81.0, "4": 76.0, "6": 68.0,
              "10": 50.0, "144": 1.027, "504": 1.0, "1008": 1.0 }
            """.trimIndent()
        val fees = backend.parseFeeEstimates(json)
        assertEquals(87.0, fees.fastSatPerVbyte, "fast = 1-block target")
        assertEquals(76.0, fees.normalSatPerVbyte, "normal = 4-block target")
        assertEquals(1.027, fees.slowSatPerVbyte, "slow = 144-block target")
    }

    @Test
    fun feeParsersFallBackWhenSchemaUnexpected() {
        val recommended = backend.parseRecommendedFees("""{ "unexpected": true }""")
        assertEquals(20.0, recommended.fastSatPerVbyte)
        val estimates = backend.parseFeeEstimates("""{ "unexpected": true }""")
        assertEquals(20.0, estimates.fastSatPerVbyte)
    }
}
