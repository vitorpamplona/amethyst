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
package com.vitorpamplona.quartz.nipBCOnchainZaps.verify

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTxOutput
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.AmountTag
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.BitcoinTxIdTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verification logic tests using an in-memory fake [OnchainBackend].
 *
 * Crypto-derived recipient scripts come from the real `TaprootAddress`
 * implementation so the tests cover the full sum-matching-outputs path.
 */
class OnchainZapVerifierTest {
    // Two arbitrary x-only Nostr pubkeys.
    private val senderHex = "d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d"
    private val recipientHex = "8b34731183a85a4fc1a3ea9e8caa14e72ff31716bf6dd0d2f8c93f5b14e44f5d"

    private val recipientScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(recipientHex).lowercase()
    private val senderScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(senderHex).lowercase()

    private val txid = "a".repeat(64)

    private fun mkEvent(
        sender: HexKey = senderHex,
        recipient: HexKey = recipientHex,
        amountSats: Long = 25000L,
        txidValue: String = txid,
    ): OnchainZapEvent {
        val tags =
            arrayOf(
                BitcoinTxIdTag.assemble(txidValue),
                arrayOf("p", recipient),
                AmountTag.assemble(amountSats),
                arrayOf("alt", "Onchain zap"),
            )
        return OnchainZapEvent(
            id = "b".repeat(64),
            pubKey = sender,
            createdAt = 0L,
            tags = tags,
            content = "",
            sig = "c".repeat(128),
        )
    }

    private class FakeBackend(
        private val tx: BitcoinTx?,
        private val tip: Long = 800_006L,
    ) : OnchainBackend {
        override suspend fun getTx(txid: String): BitcoinTx? = if (tx?.txid == txid) tx else null

        override suspend fun getUtxosForAddress(address: String): List<Utxo> = emptyList()

        override suspend fun broadcast(rawTxHex: String): String = throw UnsupportedOperationException()

        override suspend fun tipHeight(): Long = tip

        override suspend fun feeEstimates(): FeeEstimates = FeeEstimates(20.0, 10.0, 5.0)
    }

    @Test
    fun confirmedZap() =
        runTest {
            val tx =
                BitcoinTx(
                    txid = txid,
                    outputs =
                        listOf(
                            BitcoinTxOutput(0, 25000L, recipientScriptHex),
                            BitcoinTxOutput(1, 99000L, senderScriptHex), // change
                        ),
                    confirmations = 1,
                    blockHashHex = "f".repeat(64),
                    blockHeight = 800_000L,
                )
            val verifier = OnchainZapVerifier(FakeBackend(tx, tip = 800_006L))
            val result = verifier.verify(mkEvent())

            assertIs<VerifiedOnchainZap.Confirmed>(result)
            assertEquals(25000L, result.verifiedSats)
            assertEquals(recipientHex, result.recipientPubKey)
            assertEquals(800_000L, result.blockHeight)
            // Real depth computed from the chain tip: 800006 - 800000 + 1 = 7.
            assertEquals(7, result.confirmations)
        }

    @Test
    fun sumsMultipleOutputsToSameRecipient() =
        runTest {
            val tx =
                BitcoinTx(
                    txid = txid,
                    outputs =
                        listOf(
                            BitcoinTxOutput(0, 10000L, recipientScriptHex),
                            BitcoinTxOutput(1, 15000L, recipientScriptHex),
                            BitcoinTxOutput(2, 50000L, senderScriptHex),
                        ),
                    confirmations = 1,
                )
            val verifier = OnchainZapVerifier(FakeBackend(tx))
            val result = verifier.verify(mkEvent())

            assertIs<VerifiedOnchainZap.Confirmed>(result)
            assertEquals(25000L, result.verifiedSats)
        }

    @Test
    fun rejectsSelfZap() =
        runTest {
            val verifier = OnchainZapVerifier(FakeBackend(tx = null))
            val result = verifier.verify(mkEvent(sender = recipientHex, recipient = recipientHex))

            assertIs<VerifiedOnchainZap.Rejected>(result)
            assertEquals(VerifiedOnchainZap.Rejected.Reason.SELF_ZAP, result.reason)
        }

    @Test
    fun rejectsMissingTransaction() =
        runTest {
            val verifier = OnchainZapVerifier(FakeBackend(tx = null))
            val result = verifier.verify(mkEvent())

            assertIs<VerifiedOnchainZap.Rejected>(result)
            assertEquals(VerifiedOnchainZap.Rejected.Reason.TX_NOT_FOUND, result.reason)
        }

    @Test
    fun rejectsZeroVerifiedAmount() =
        runTest {
            // TX exists but pays no output to the recipient.
            val tx =
                BitcoinTx(
                    txid = txid,
                    outputs =
                        listOf(
                            BitcoinTxOutput(0, 99000L, senderScriptHex),
                        ),
                    confirmations = 5,
                )
            val verifier = OnchainZapVerifier(FakeBackend(tx))
            val result = verifier.verify(mkEvent())

            assertIs<VerifiedOnchainZap.Rejected>(result)
            assertEquals(VerifiedOnchainZap.Rejected.Reason.ZERO_VERIFIED_AMOUNT, result.reason)
        }

    @Test
    fun pendingWhenUnconfirmed() =
        runTest {
            val tx =
                BitcoinTx(
                    txid = txid,
                    outputs = listOf(BitcoinTxOutput(0, 7000L, recipientScriptHex)),
                    confirmations = 0,
                )
            val verifier = OnchainZapVerifier(FakeBackend(tx))
            val result = verifier.verify(mkEvent(amountSats = 7000L))

            assertIs<VerifiedOnchainZap.Pending>(result)
            assertEquals(7000L, result.verifiedSats)
        }

    @Test
    fun capsClaimedAmountAtVerified() =
        runTest {
            // Sender claims 1,000,000 but only 5,000 went to the recipient.
            val tx =
                BitcoinTx(
                    txid = txid,
                    outputs = listOf(BitcoinTxOutput(0, 5000L, recipientScriptHex)),
                    confirmations = 1,
                )
            val verifier = OnchainZapVerifier(FakeBackend(tx))
            val result = verifier.verify(mkEvent(amountSats = 1_000_000L))

            assertIs<VerifiedOnchainZap.Confirmed>(result)
            assertEquals(5000L, result.verifiedSats, "verifier must report the on-chain amount, not the claimed amount")
            assertTrue(result.verifiedSats < 1_000_000L)
        }
}
