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

import androidx.compose.runtime.Immutable

/**
 * Minimal view of a confirmed or mempool Bitcoin transaction — just the
 * fields NIP-BC verification needs.
 *
 * @property txid 64-char lowercase hex transaction id
 * @property outputs Output list (index = vout)
 * @property confirmations 0 if unconfirmed; height-based when known
 * @property blockHashHex Hash of the block that confirmed this tx, if any
 * @property blockHeight Height of the block that confirmed this tx, if any
 */
@Immutable
data class BitcoinTx(
    val txid: String,
    val outputs: List<BitcoinTxOutput>,
    val confirmations: Int,
    val blockHashHex: String? = null,
    val blockHeight: Long? = null,
)

/**
 * One output of a Bitcoin transaction.
 *
 * @property index vout index
 * @property valueSats output value in satoshis
 * @property scriptPubKeyHex lowercase-hex scriptPubKey bytes
 */
@Immutable
data class BitcoinTxOutput(
    val index: Int,
    val valueSats: Long,
    val scriptPubKeyHex: String,
)

/**
 * An unspent transaction output the wallet can spend.
 *
 * @property txid 64-char lowercase hex of the funding transaction
 * @property vout output index in the funding transaction
 * @property valueSats value in satoshis
 * @property confirmations confirmation count (0 for mempool)
 */
@Immutable
data class Utxo(
    val txid: String,
    val vout: Int,
    val valueSats: Long,
    val confirmations: Int,
)

/**
 * Recommended fee rates in sats per vbyte, as reported by the backend.
 */
@Immutable
data class FeeEstimates(
    val fastSatPerVbyte: Double,
    val normalSatPerVbyte: Double,
    val slowSatPerVbyte: Double,
)
