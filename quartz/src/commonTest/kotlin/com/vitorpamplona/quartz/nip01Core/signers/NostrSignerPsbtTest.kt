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
package com.vitorpamplona.quartz.nip01Core.signers

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtFinalizer
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TaprootSigHash
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapKeySig
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests that `NostrSigner.signPsbt` is wired correctly across the hierarchy. */
class NostrSignerPsbtTest {
    private val senderPriv = "0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()
    private val recipientPriv = "000000000000000000000000000000000000000000000000000000000000000d".hexToByteArray()

    private fun xOnly(priv: ByteArray) = Secp256k1Instance.compressedPubKeyFor(priv).copyOfRange(1, 33).toHexKey()

    @Test
    fun internalSignerSignsAndFinalizes() =
        runTest {
            val signer = NostrSignerInternal(KeyPair(senderPriv))
            val senderPubKey = xOnly(senderPriv)
            val recipientPubKey = xOnly(recipientPriv)

            val built =
                OnchainZapBuilder.build(
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = 50_000L,
                    feeRateSatPerVByte = 4.0,
                    availableUtxos = listOf(Utxo("1".repeat(64), 0, 250_000L, 6)),
                )

            val signedHex = signer.signPsbt(built.psbt.toHex())
            val signedPsbt = Psbt.parse(signedHex)

            assertTrue(PsbtFinalizer.isFullySigned(signedPsbt))

            val finalTx = PsbtFinalizer.finalize(signedPsbt)
            val senderScript = TaprootAddress.scriptPubKeyForRecipient(senderPubKey)
            val senderOutputKey = TaprootAddress.tweakOutputKey(senderPubKey.hexToByteArray())
            val spent = built.selectedUtxos.map { TxOut(it.valueSats, senderScript) }

            finalTx.inputs.forEachIndexed { i, input ->
                val sigHash = TaprootSigHash.compute(finalTx, i, spent, TaprootSigHash.SIGHASH_DEFAULT)
                assertTrue(
                    Secp256k1Instance.verifySchnorr(input.witness[0], sigHash, senderOutputKey),
                    "input $i must verify after NostrSigner.signPsbt",
                )
            }
        }

    @Test
    fun signPsbtIsIdempotentlySafeOnAlreadySignedInputs() =
        runTest {
            val signer = NostrSignerInternal(KeyPair(senderPriv))
            val built =
                OnchainZapBuilder.build(
                    xOnly(senderPriv),
                    xOnly(recipientPriv),
                    20_000L,
                    3.0,
                    listOf(Utxo("2".repeat(64), 1, 100_000L, 3)),
                )
            val once = signer.signPsbt(built.psbt.toHex())
            val twice = signer.signPsbt(once)
            // Re-signing must not clobber or duplicate the existing signature.
            assertEquals(once, twice)
            assertEquals(64, Psbt.parse(twice).inputTapKeySig(0)!!.size)
        }

    @Test
    fun readOnlySignerCannotSignPsbt() =
        runTest {
            // A key-less (watch-only) keypair.
            val pubOnly = Secp256k1Instance.compressedPubKeyFor(senderPriv).copyOfRange(1, 33)
            val signer = NostrSignerInternal(KeyPair(pubKey = pubOnly))
            val built =
                OnchainZapBuilder.build(
                    xOnly(senderPriv),
                    xOnly(recipientPriv),
                    20_000L,
                    3.0,
                    listOf(Utxo("3".repeat(64), 0, 100_000L, 3)),
                )
            assertFailsWith<SignerExceptions> {
                signer.signPsbt(built.psbt.toHex())
            }
        }
}
