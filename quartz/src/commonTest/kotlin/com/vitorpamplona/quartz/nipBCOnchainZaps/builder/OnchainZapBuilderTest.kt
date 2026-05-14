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
package com.vitorpamplona.quartz.nipBCOnchainZaps.builder

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtFinalizer
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtSigner
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TaprootSigHash
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnchainZapBuilderTest {
    private val senderPrivKey = "0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()
    private val senderPubKey = Secp256k1Instance.compressedPubKeyFor(senderPrivKey).copyOfRange(1, 33).toHexKey()
    private val recipientPubKey =
        Secp256k1Instance
            .compressedPubKeyFor("000000000000000000000000000000000000000000000000000000000000000b".hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    private val senderScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(senderPubKey).lowercase()
    private val recipientScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(recipientPubKey).lowercase()

    private fun utxo(
        valueSats: Long,
        index: Int,
        confirmations: Int = 6,
    ) = Utxo(txid = index.toString().padStart(64, '0'), vout = 0, valueSats = valueSats, confirmations = confirmations)

    @Test
    fun buildsZapWithChangeAndBalances() {
        val utxos = listOf(utxo(100_000L, 1), utxo(50_000L, 2))
        val result =
            OnchainZapBuilder.build(
                senderPubKey = senderPubKey,
                recipientPubKey = recipientPubKey,
                amountSats = 25_000L,
                feeRateSatPerVByte = 5.0,
                availableUtxos = utxos,
            )

        // One UTXO of 100k covers 25k + change + fee — greedy picks the largest first.
        assertEquals(1, result.selectedUtxos.size)
        assertEquals(25_000L, result.recipientSats)
        assertTrue(result.changeSats > 0, "should have a change output")
        assertTrue(result.feeSats > 0)

        // inputs == outputs + fee
        val inputSum = result.selectedUtxos.sumOf { it.valueSats }
        assertEquals(inputSum, result.recipientSats + result.changeSats + result.feeSats)

        val tx = result.psbt.unsignedTx
        assertEquals(2, tx.outputs.size)
        assertEquals(25_000L, tx.outputs[0].valueSats)
        assertEquals(recipientScriptHex, tx.outputs[0].scriptPubKey.toHexKey())
        assertEquals(result.changeSats, tx.outputs[1].valueSats)
        assertEquals(senderScriptHex, tx.outputs[1].scriptPubKey.toHexKey())
    }

    @Test
    fun signedTransactionVerifies() {
        val utxos = listOf(utxo(200_000L, 1))
        val result =
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 40_000L, 8.0, utxos)

        val signed = PsbtSigner.signKeyPathInputs(result.psbt, senderPrivKey)
        assertEquals(result.selectedUtxos.size, signed)
        assertTrue(PsbtFinalizer.isFullySigned(result.psbt))

        val finalTx = PsbtFinalizer.finalize(result.psbt)

        // Every input's witness signature must verify against the sender's
        // taproot output key over the BIP-341 sighash.
        val senderOutputKey = TaprootAddress.tweakOutputKey(senderPubKey.hexToByteArray())
        val spentOutputs = result.selectedUtxos.map { TxOut(it.valueSats, senderScriptHex.hexToByteArray()) }
        finalTx.inputs.forEachIndexed { i, input ->
            assertEquals(1, input.witness.size)
            val sigHash = TaprootSigHash.compute(finalTx, i, spentOutputs, TaprootSigHash.SIGHASH_DEFAULT)
            assertTrue(
                Secp256k1Instance.verifySchnorr(input.witness[0], sigHash, senderOutputKey),
                "input $i signature must verify",
            )
        }

        // Broadcast hex parses back to the same txid.
        val rebroadcast =
            com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
                .parse(PsbtFinalizer.finalizeToHex(result.psbt))
        assertEquals(finalTx.txid(), rebroadcast.txid())
    }

    @Test
    fun dropsChangeWhenItWouldBeDust() {
        // 30_000 utxo, pay 29_800: after the ~154-sat with-change fee the
        // leftover change (~46 sats) is below the 330-sat dust threshold, so
        // the builder must fold it into the fee instead of creating dust.
        val utxos = listOf(utxo(30_000L, 1))
        val result =
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 29_800L, 1.0, utxos)
        assertEquals(0L, result.changeSats, "tiny leftover must be absorbed into the fee")
        assertEquals(1, result.psbt.unsignedTx.outputs.size, "no change output")
        assertEquals(30_000L, result.recipientSats + result.feeSats)
    }

    @Test
    fun combinesMultipleUtxosWhenNeeded() {
        val utxos = listOf(utxo(20_000L, 1), utxo(20_000L, 2), utxo(20_000L, 3))
        val result =
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 45_000L, 2.0, utxos)
        assertTrue(result.selectedUtxos.size >= 3, "needs all three 20k UTXOs to cover 45k + fee")
    }

    @Test
    fun throwsOnInsufficientFunds() {
        val utxos = listOf(utxo(10_000L, 1))
        assertFailsWith<InsufficientFundsException> {
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 1_000_000L, 5.0, utxos)
        }
    }

    @Test
    fun rejectsSelfZap() {
        assertFailsWith<IllegalArgumentException> {
            OnchainZapBuilder.build(senderPubKey, senderPubKey, 25_000L, 5.0, listOf(utxo(100_000L, 1)))
        }
    }

    @Test
    fun rejectsDustAmount() {
        assertFailsWith<IllegalArgumentException> {
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 100L, 5.0, listOf(utxo(100_000L, 1)))
        }
    }

    @Test
    fun excludesUnconfirmedUtxosByDefault() {
        // Only a 0-conf UTXO is available — by default it must not be spent,
        // so there are effectively no funds.
        val unconfirmed = listOf(utxo(250_000L, 1, confirmations = 0))
        assertFailsWith<InsufficientFundsException> {
            OnchainZapBuilder.build(senderPubKey, recipientPubKey, 25_000L, 5.0, unconfirmed)
        }
    }

    @Test
    fun spendsUnconfirmedUtxosOnlyWhenOptedIn() {
        val unconfirmed = listOf(utxo(250_000L, 1, confirmations = 0))
        val result =
            OnchainZapBuilder.build(
                senderPubKey,
                recipientPubKey,
                25_000L,
                5.0,
                unconfirmed,
                allowUnconfirmed = true,
            )
        assertEquals(1, result.selectedUtxos.size)
        assertEquals(0, result.selectedUtxos[0].confirmations)
    }

    @Test
    fun prefersConfirmedUtxosAndSkipsUnconfirmedOnes() {
        // A large unconfirmed UTXO is ignored; the build falls back to the
        // smaller confirmed ones.
        val utxos =
            listOf(
                utxo(1_000_000L, 1, confirmations = 0),
                utxo(30_000L, 2, confirmations = 3),
                utxo(30_000L, 3, confirmations = 3),
            )
        val result = OnchainZapBuilder.build(senderPubKey, recipientPubKey, 25_000L, 2.0, utxos)
        assertTrue(result.selectedUtxos.all { it.confirmations > 0 }, "must not select the 0-conf UTXO")
    }
}
