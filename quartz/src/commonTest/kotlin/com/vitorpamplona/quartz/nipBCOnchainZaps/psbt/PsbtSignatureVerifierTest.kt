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
package com.vitorpamplona.quartz.nipBCOnchainZaps.psbt

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PsbtSignatureVerifierTest {
    private val senderPriv = "0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()
    private val senderPubKey = Secp256k1Instance.compressedPubKeyFor(senderPriv).copyOfRange(1, 33).toHexKey()
    private val recipientPubKey =
        Secp256k1Instance
            .compressedPubKeyFor("000000000000000000000000000000000000000000000000000000000000000b".hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    private fun signedPsbt(): Psbt {
        val built =
            OnchainZapBuilder.build(
                senderPubKey,
                recipientPubKey,
                40_000L,
                5.0,
                listOf(Utxo("1".repeat(64), 0, 250_000L, 6)),
            )
        PsbtSigner.signKeyPathInputs(built.psbt, senderPriv)
        return built.psbt
    }

    @Test
    fun acceptsAProperlySignedPsbt() {
        assertTrue(PsbtSignatureVerifier.verifyAllKeyPathInputs(signedPsbt()))
    }

    @Test
    fun rejectsWhenAnInputIsUnsigned() {
        val psbt = signedPsbt()
        psbt.inputs[0].remove(Psbt.PSBT_IN_TAP_KEY_SIG)
        assertFalse(PsbtSignatureVerifier.verifyAllKeyPathInputs(psbt))
    }

    @Test
    fun rejectsAGarbageSignature() {
        val psbt = signedPsbt()
        psbt.setInputTapKeySig(0, ByteArray(64) { 0x11 })
        assertFalse(PsbtSignatureVerifier.verifyAllKeyPathInputs(psbt))
    }

    @Test
    fun rejectsSignaturesOverATamperedTransaction() {
        // Sign tx A, then graft A's signed input maps onto a PSBT whose
        // transaction has a mutated output. The signatures no longer commit to
        // the transaction being verified — exactly the substitution attack the
        // verifier exists to catch.
        val signed = signedPsbt()
        val original = signed.unsignedTx
        val tamperedTx =
            original.copy(
                outputs =
                    original.outputs.mapIndexed { i, o ->
                        if (i == 0) o.copy(valueSats = o.valueSats + 10_000L) else o
                    },
            )
        val tamperedGlobal = PsbtMap()
        tamperedGlobal.put(Psbt.PSBT_GLOBAL_UNSIGNED_TX, tamperedTx.serializeForId())
        val tamperedPsbt = Psbt(tamperedGlobal, signed.inputs, signed.outputs)

        assertFalse(PsbtSignatureVerifier.verifyAllKeyPathInputs(tamperedPsbt))
    }

    @Test
    fun rejectsWhenWitnessUtxoMissing() {
        val psbt = signedPsbt()
        psbt.inputs[0].remove(Psbt.PSBT_IN_WITNESS_UTXO)
        assertFalse(PsbtSignatureVerifier.verifyAllKeyPathInputs(psbt))
    }
}
