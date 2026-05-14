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

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An [OnchainBackend] decorator that caches read-only lookups so a feed full
 * of NIP-BC zaps doesn't fan out into one HTTP request per event.
 *
 * Caching policy:
 * - `getTx`: a **confirmed** transaction is immutable, so it's cached
 *   indefinitely; an unconfirmed one is cached only briefly (its confirmation
 *   status will change). `null` (not-found) is never cached — the tx may
 *   appear later.
 * - `tipHeight` / `feeEstimates`: cached with a short TTL.
 * - `getUtxosForAddress`: never cached — wallet balance must be fresh.
 * - `broadcast`: never cached.
 *
 * The delegate call is made **outside** the lock, so concurrent lookups still
 * run in parallel; a brief window where two callers fetch the same txid is
 * accepted (it only wastes a request, never returns wrong data).
 */
class CachingOnchainBackend(
    private val delegate: OnchainBackend,
    private val unconfirmedTxTtlSeconds: Long = 60,
    private val tipHeightTtlSeconds: Long = 60,
    private val feeEstimatesTtlSeconds: Long = 60,
    private val nowSeconds: () -> Long = { TimeUtils.now() },
) : OnchainBackend {
    private class Stamped<T>(
        val value: T,
        val fetchedAt: Long,
    )

    private val mutex = Mutex()
    private val txCache = mutableMapOf<String, Stamped<BitcoinTx>>()
    private var tipCache: Stamped<Long>? = null
    private var feeCache: Stamped<FeeEstimates>? = null

    override suspend fun getTx(txid: String): BitcoinTx? {
        mutex.withLock {
            val cached = txCache[txid]
            if (cached != null) {
                val stillFresh =
                    cached.value.confirmations > 0 ||
                        nowSeconds() - cached.fetchedAt < unconfirmedTxTtlSeconds
                if (stillFresh) return cached.value
            }
        }

        val fetched = delegate.getTx(txid) ?: return null

        mutex.withLock { txCache[txid] = Stamped(fetched, nowSeconds()) }
        return fetched
    }

    override suspend fun getUtxosForAddress(address: String): List<Utxo> = delegate.getUtxosForAddress(address)

    override suspend fun broadcast(rawTxHex: String): String = delegate.broadcast(rawTxHex)

    override suspend fun tipHeight(): Long {
        mutex.withLock {
            tipCache?.let {
                if (nowSeconds() - it.fetchedAt < tipHeightTtlSeconds) return it.value
            }
        }
        val fetched = delegate.tipHeight()
        mutex.withLock { tipCache = Stamped(fetched, nowSeconds()) }
        return fetched
    }

    override suspend fun feeEstimates(): FeeEstimates {
        mutex.withLock {
            feeCache?.let {
                if (nowSeconds() - it.fetchedAt < feeEstimatesTtlSeconds) return it.value
            }
        }
        val fetched = delegate.feeEstimates()
        mutex.withLock { feeCache = Stamped(fetched, nowSeconds()) }
        return fetched
    }
}
