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
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * End-to-end signing tests for the PSBT pipeline. Anchored on the BIP-341
 * `keyPathSpending` test vector (input 0) so the BIP-341 tweak, the BIP-341
 * sighash, and the BIP-340 signature are all validated against an authoritative
 * source.
 */
class PsbtSignerTest {
    private val unsignedTxHex =
        "02000000097de20cbff686da83a54981d2b9bab3586f4ca7e48f57f5b55963115f3b334e9c0100000000" +
            "00000000d7b7cab57b1393ace2d064f4d4a2cb8af6def61273e127517d44759b6dafdd9900000000" +
            "00fffffffff8e1f583384333689228c5d28eac13366be082dc57441760d957275419a41842000000" +
            "0000fffffffff0689180aa63b30cb162a73c6d2a38b7eeda2a83ece74310fda0843ad604853b0100" +
            "000000feffffffaa5202bdf6d8ccd2ee0f0202afbbb7461d9264a25e5bfd3c5a52ee1239e0ba6c00" +
            "00000000feffffff956149bdc66faa968eb2be2d2faa29718acbfe3941215893a2a3446d32acd050" +
            "000000000000000000e664b9773b88c09c32cb70a2a3e4da0ced63b7ba3b22f848531bbb1d5d5f4c" +
            "94010000000000000000e9aa6b8e6c9de67619e6a3924ae25696bb7b694bb677a632a74ef7eadfd4" +
            "eabf0000000000ffffffffa778eb6a263dc090464cd125c466b5a99667720b1c110468831d058aa1" +
            "b82af10100000000ffffffff0200ca9a3b000000001976a91406afd46bcdfd22ef94ac122aa11f24" +
            "1244a37ecc88ac807840cb0000000020ac9a87f5594be208f8532db38cff670c450ed2fea8fcdefc" +
            "c9a663f78bab962b0065cd1d"

    private val spentOutputs =
        listOf(
            TxOut(420_000_000L, "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343".hexToByteArray()),
            TxOut(462_000_000L, "5120147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3".hexToByteArray()),
            TxOut(294_000_000L, "76a914751e76e8199196d454941c45d1b3a323f1433bd688ac".hexToByteArray()),
            TxOut(504_000_000L, "5120e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e".hexToByteArray()),
            TxOut(630_000_000L, "512091b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605".hexToByteArray()),
            TxOut(378_000_000L, "00147dd65592d0ab2fe0d0257d571abf032cd9db93dc".hexToByteArray()),
            TxOut(672_000_000L, "512075169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831".hexToByteArray()),
            TxOut(546_000_000L, "5120712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5".hexToByteArray()),
            TxOut(588_000_000L, "512077e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220".hexToByteArray()),
        )

    // BIP-341 keyPathSpending input 0.
    private val internalPrivKey0 = "6b973d88838f27366ed61c9ad6367663045cb456e28335c109e30717ae0c6baa"
    private val internalPubKey0 = "d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d"
    private val tweakedPrivKey0 = "2405b971772ad26915c8dcdf10f238753a9b837e5f8e6a86fd7c0cce5b7296d9"
    private val outputKey0 = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"
    private val sigHashSingle0 = "2514a6272f85cfa0f45eb907fcb0d121b808ed37c6ea160a5a9046ed5526d555"

    // BIP-341 keyPathSpending input 0 — the full expected witness (64-byte
    // signature + the SIGHASH_SINGLE 0x03 byte).
    private val expectedWitness0 =
        "ed7c1647cb97379e76892be0cacff57ec4a7102aa24296ca39af7541246d8ff1" +
            "4d38958d4cc1e2e478e4d4a764bbfd835b16d4e314b72937b29833060b87276c03"

    @Test
    fun producesExactBip341VectorSignature() {
        // Pins the full BIP-340 signing pipeline (and its nonce determinism) to
        // the authoritative BIP-341 wallet test vector: signing the vector's
        // sighash with the vector's tweaked key must reproduce the vector's
        // witness signature byte-for-byte.
        val sig =
            Secp256k1Instance.signSchnorr(
                sigHashSingle0.hexToByteArray(),
                tweakedPrivKey0.hexToByteArray(),
            )
        // Witness is the 64-byte signature; the trailing 0x03 is the sighash type
        // appended by the PSBT signer, not part of the BIP-340 signature itself.
        assertEquals(expectedWitness0.substring(0, 128), sig.toHexKey())
    }

    private fun psbtForVectorTx(): Psbt {
        val psbt = Psbt.fromUnsignedTx(BitcoinTransaction.parse(unsignedTxHex))
        spentOutputs.forEachIndexed { i, utxo -> psbt.setInputWitnessUtxo(i, utxo) }
        psbt.setInputTapInternalKey(0, internalPubKey0.hexToByteArray())
        return psbt
    }

    @Test
    fun tweakSecretKeyMatchesBip341Vector() {
        val tweaked = TaprootAddress.tweakSecretKey(internalPrivKey0.hexToByteArray())
        assertEquals(tweakedPrivKey0, tweaked.toHexKey())
    }

    @Test
    fun signsAndProducesVerifiableSignature_sighashSingle() {
        val psbt = psbtForVectorTx()
        psbt.setInputSighashType(0, TaprootSigHash.SIGHASH_SINGLE)

        val count = PsbtSigner.signKeyPathInputs(psbt, internalPrivKey0.hexToByteArray())
        assertEquals(1, count, "exactly input 0 should be signed")

        val sig = psbt.inputTapKeySig(0)!!
        // Non-default sighash → 65-byte signature with the type byte appended.
        assertEquals(65, sig.size)
        assertEquals(TaprootSigHash.SIGHASH_SINGLE, sig[64].toInt())

        // The 64-byte BIP-340 signature must verify against the vector's exact
        // SIGHASH_SINGLE sighash and the input's taproot output key.
        val ok =
            Secp256k1Instance.verifySchnorr(
                sig.copyOfRange(0, 64),
                sigHashSingle0.hexToByteArray(),
                outputKey0.hexToByteArray(),
            )
        assertTrue(ok, "signature must verify against the BIP-341 vector sighash + output key")
    }

    @Test
    fun signsDefaultSighashHappyPath() {
        // The path NIP-BC actually uses: SIGHASH_DEFAULT, bare 64-byte signature.
        val psbt = psbtForVectorTx()
        PsbtSigner.signKeyPathInputs(psbt, internalPrivKey0.hexToByteArray())

        val sig = psbt.inputTapKeySig(0)!!
        assertEquals(64, sig.size, "SIGHASH_DEFAULT → bare 64-byte signature")

        val expectedSigHash =
            TaprootSigHash.compute(psbt.unsignedTx, 0, spentOutputs, TaprootSigHash.SIGHASH_DEFAULT)
        val ok = Secp256k1Instance.verifySchnorr(sig, expectedSigHash, outputKey0.hexToByteArray())
        assertTrue(ok, "default-sighash signature must verify against the output key")
    }

    @Test
    fun skipsInputsTheKeyDoesNotControl() {
        // A wrong internal key on input 0 → nothing to sign.
        val psbt = Psbt.fromUnsignedTx(BitcoinTransaction.parse(unsignedTxHex))
        spentOutputs.forEachIndexed { i, utxo -> psbt.setInputWitnessUtxo(i, utxo) }
        psbt.setInputTapInternalKey(0, "ab".repeat(32).hexToByteArray())

        val count = PsbtSigner.signKeyPathInputs(psbt, internalPrivKey0.hexToByteArray())
        assertEquals(0, count)
    }

    @Test
    fun failsWhenWitnessUtxoMissing() {
        val psbt = Psbt.fromUnsignedTx(BitcoinTransaction.parse(unsignedTxHex))
        psbt.setInputTapInternalKey(0, internalPubKey0.hexToByteArray())
        // No witness UTXOs set → sighash cannot be computed.
        assertFailsWith<PsbtSigningException> {
            PsbtSigner.signKeyPathInputs(psbt, internalPrivKey0.hexToByteArray())
        }
    }

    @Test
    fun finalizeMovesSignatureIntoWitness() {
        // A minimal self-contained 1-in / 1-out taproot spend.
        val privKey = "0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray()
        val xOnly = Secp256k1Instance.compressedPubKeyFor(privKey).copyOfRange(1, 33)
        val outputKey = TaprootAddress.tweakOutputKey(xOnly)
        val prevScript = TaprootAddress.outputKeyToScriptPubKey(outputKey)

        val tx =
            BitcoinTransaction(
                version = 2L,
                inputs = listOf(TxIn(OutPoint("a".repeat(64), 0L), sequence = 0xFFFFFFFFL)),
                outputs = listOf(TxOut(90_000L, prevScript)),
                lockTime = 0L,
            )
        val psbt = Psbt.fromUnsignedTx(tx)
        psbt.setInputWitnessUtxo(0, TxOut(100_000L, prevScript))
        psbt.setInputTapInternalKey(0, xOnly)

        assertTrue(!PsbtFinalizer.isFullySigned(psbt))
        PsbtSigner.signKeyPathInputs(psbt, privKey)
        assertTrue(PsbtFinalizer.isFullySigned(psbt))

        val finalTx = PsbtFinalizer.finalize(psbt)
        assertEquals(1, finalTx.inputs[0].witness.size)
        assertEquals(64, finalTx.inputs[0].witness[0].size)
        assertTrue(finalTx.hasWitness)
        // txid is witness-stripped — unchanged by finalization.
        assertEquals(tx.txid(), finalTx.txid())

        // The signature in the witness must verify.
        val sigHash = TaprootSigHash.compute(tx, 0, listOf(TxOut(100_000L, prevScript)), TaprootSigHash.SIGHASH_DEFAULT)
        assertTrue(
            Secp256k1Instance.verifySchnorr(finalTx.inputs[0].witness[0], sigHash, outputKey),
        )
    }
}
