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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CachingOnchainBackendTest {
    /** Counts every delegate call so the cache's hit/miss behaviour is observable. */
    private class CountingBackend(
        var txByTxid: (String) -> BitcoinTx? = { null },
    ) : OnchainBackend {
        var getTxCalls = 0
        var tipCalls = 0
        var feeCalls = 0

        override suspend fun getTx(txid: String): BitcoinTx? {
            getTxCalls++
            return txByTxid(txid)
        }

        override suspend fun getUtxosForAddress(address: String): List<Utxo> = emptyList()

        override suspend fun getTxsForAddress(
            address: String,
            afterTxid: String?,
        ): List<BitcoinAddressTx> = emptyList()

        override suspend fun broadcast(rawTxHex: String): String = "broadcast"

        override suspend fun tipHeight(): Long {
            tipCalls++
            return 800_000L
        }

        override suspend fun feeEstimates(): FeeEstimates {
            feeCalls++
            return FeeEstimates(20.0, 10.0, 5.0)
        }
    }

    private fun confirmedTx(txid: String) = BitcoinTx(txid = txid, outputs = emptyList(), confirmations = 1, blockHeight = 799_000L)

    private fun mempoolTx(txid: String) = BitcoinTx(txid = txid, outputs = emptyList(), confirmations = 0)

    @Test
    fun confirmedTransactionIsCachedIndefinitely() =
        runTest {
            val delegate = CountingBackend { confirmedTx(it) }
            var clock = 1_000L
            val cache = CachingOnchainBackend(delegate, nowSeconds = { clock })

            cache.getTx("aa")
            clock += 100_000 // way past any TTL
            cache.getTx("aa")

            assertEquals(1, delegate.getTxCalls, "a confirmed tx must be served from cache forever")
        }

    @Test
    fun unconfirmedTransactionIsRefetchedAfterTtl() =
        runTest {
            val delegate = CountingBackend { mempoolTx(it) }
            var clock = 1_000L
            val cache = CachingOnchainBackend(delegate, unconfirmedTxTtlSeconds = 60, nowSeconds = { clock })

            cache.getTx("bb")
            clock += 30 // within TTL
            cache.getTx("bb")
            assertEquals(1, delegate.getTxCalls, "still fresh — served from cache")

            clock += 60 // now past TTL
            cache.getTx("bb")
            assertEquals(2, delegate.getTxCalls, "stale unconfirmed tx must be re-fetched")
        }

    @Test
    fun notFoundIsNeverCached() =
        runTest {
            val delegate = CountingBackend { null }
            val cache = CachingOnchainBackend(delegate, nowSeconds = { 1_000L })

            assertNull(cache.getTx("cc"))
            assertNull(cache.getTx("cc"))
            assertEquals(2, delegate.getTxCalls, "a not-found result must not be cached")
        }

    @Test
    fun tipHeightAndFeesAreCachedWithinTtl() =
        runTest {
            val delegate = CountingBackend()
            var clock = 1_000L
            val cache =
                CachingOnchainBackend(
                    delegate,
                    tipHeightTtlSeconds = 60,
                    feeEstimatesTtlSeconds = 60,
                    nowSeconds = { clock },
                )

            cache.tipHeight()
            cache.feeEstimates()
            cache.tipHeight()
            cache.feeEstimates()
            assertEquals(1, delegate.tipCalls)
            assertEquals(1, delegate.feeCalls)

            clock += 61
            cache.tipHeight()
            cache.feeEstimates()
            assertEquals(2, delegate.tipCalls, "tip height must be re-fetched after its TTL")
            assertEquals(2, delegate.feeCalls, "fee estimates must be re-fetched after its TTL")
        }

    @Test
    fun txCacheIsBoundedAndEvictsTheOldest() =
        runTest {
            val delegate = CountingBackend { confirmedTx(it) }
            var clock = 1_000L
            val cache = CachingOnchainBackend(delegate, maxCachedTxs = 2, nowSeconds = { clock })

            cache.getTx("t1") // fetched at 1000
            clock += 1
            cache.getTx("t2") // fetched at 1001
            clock += 1
            cache.getTx("t3") // fetched at 1002 → cache full (2), evicts oldest (t1)

            // t1 was evicted → re-fetch.
            cache.getTx("t1")
            // t3 is still cached → no re-fetch.
            cache.getTx("t3")

            assertEquals(4, delegate.getTxCalls, "t1/t2/t3 + a re-fetch of evicted t1")
        }
}
