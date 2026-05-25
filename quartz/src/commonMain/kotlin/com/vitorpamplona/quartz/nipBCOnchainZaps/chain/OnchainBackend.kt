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

/**
 * Pluggable Bitcoin chain data source.
 *
 * Implementations talk to an Esplora-compatible HTTP API, a Bitcoin Core
 * node, an Electrum server, or a local SPV verifier.
 */
interface OnchainBackend {
    /** Fetch a transaction by txid. Returns null if the backend has no record of it. */
    suspend fun getTx(txid: String): BitcoinTx?

    /** Fetch the spendable UTXOs paying `address` (bech32m taproot for NIP-BC). */
    suspend fun getUtxosForAddress(address: String): List<Utxo>

    /**
     * Fetch a page of transactions touching `address`, projected onto the
     * address's perspective (see [BitcoinAddressTx.netValueSats]).
     *
     * The first call (`afterTxid == null`) returns the most recent mempool
     * transactions followed by the first page of confirmed transactions, newest
     * first. Subsequent calls pass the txid of the last entry of the previous
     * confirmed page to get the next page. An empty list means no more pages.
     */
    suspend fun getTxsForAddress(
        address: String,
        afterTxid: String? = null,
    ): List<BitcoinAddressTx>

    /** Broadcast a fully signed transaction (lowercase hex). Returns the txid. */
    suspend fun broadcast(rawTxHex: String): String

    /** Current chain tip height. */
    suspend fun tipHeight(): Long

    /** Recommended fee rates from the backend. */
    suspend fun feeEstimates(): FeeEstimates
}

/**
 * Thrown by [OnchainBackend] implementations when the underlying network or
 * remote API fails. Wraps the original cause where useful.
 */
class OnchainBackendException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
