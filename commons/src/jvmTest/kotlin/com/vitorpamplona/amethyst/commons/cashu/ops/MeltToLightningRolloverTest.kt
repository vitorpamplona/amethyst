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
package com.vitorpamplona.amethyst.commons.cashu.ops

import com.vitorpamplona.amethyst.commons.cashu.CashuWalletReader
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.TokenReference
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CashuWalletOps.meltToLightning] rollover behaviour against a fake mint that really signs.
 *
 * A melt that has to pre-swap its inputs down to the quote publishes the keep side as its own
 * kind:7375 *before* melting. Regression tests for the incident where the melt's change event
 * `del`-ed that keep event and the NIP-09 delete removed it from relays too, so
 * [CashuWalletReader.computeUnspent] dropped the keep proofs — the bulk of the balance — even
 * though the mint never spent them and they were only recoverable via NUT-09.
 *
 * Setup mirrors the production incident: a 64-sat wallet melting a 21-sat invoice with a
 * 2-sat fee reserve (target 23, keep 41, refund 2).
 */
class MeltToLightningRolloverTest {
    private val mintUrl = "https://mint.example.com"
    private val keysetId = "01182e5f5e35aecd"

    private val me = NostrSignerInternal(KeyPair())
    private val published = mutableListOf<Event>()

    private fun walletOps(mint: FakeMint) = CashuWalletOps(signer = me, publish = { published += it }, mintTransport = mint.Transport())

    private fun quote(
        amount: Long,
        feeReserve: Long,
    ) = MeltQuoteBolt11ResponseDto(quote = "q1", amount = amount, feeReserve = feeReserve, state = "UNPAID")

    /** One kind:7375 token event holding [amounts] sats, as the wallet would see it from relays. */
    private suspend fun tokenEntry(vararg amounts: Long): TokenEntry {
        val proofs =
            amounts.map { amount ->
                CashuProof(
                    id = keysetId,
                    amount = amount,
                    secret = RandomInstance.bytes(32).toHexKey(),
                    // The fake mint never verifies input signatures, so any valid point works as C.
                    c = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(RandomInstance.bytes(32))).toHexKey(),
                )
            }
        val content = TokenContent(mint = mintUrl, proofs = proofs)
        return TokenEntry(me.sign(CashuTokenEvent.build(content, me)), content)
    }

    /** What the wallet shows after the melt, given the source event and everything published. */
    private suspend fun unspent(source: TokenEntry): List<TokenEntry> {
        val events = listOf(source.event) + published.filterIsInstance<CashuTokenEvent>()
        val contents = CashuWalletReader(me).decryptTokens(events)
        return CashuWalletReader.computeUnspent(events, contents)
    }

    private fun deletes() = published.filterIsInstance<DeletionRequestEvent>()

    @Test
    fun `pre-swap melt keeps the keep side and the refund in the unspent view`() {
        runBlocking {
            val mint = FakeMint(keysetId, invoiceAmount = 21)
            val source = tokenEntry(64)

            val completed = walletOps(mint).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))

            // The swap-down replaced the 64-sat proof with fresh melt inputs totalling the target.
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )
            assertEquals(23L, mint.meltedTotal)

            assertEquals(21L, completed.paidAmount)
            val changeEvent = checkNotNull(completed.newTokenEvent)
            // 64 spent - 41 kept - 2 refunded - 21 invoice = 0 fee.
            assertEquals(0L, completed.fees)

            // The change event carries only the 2-sat refund and never names the keep event.
            val changeContent = changeEvent.tokenContent(me)!!
            assertEquals(2L, changeContent.totalAmount())
            assertEquals(listOf(source.event.id), changeContent.del)

            val keepEvent = (published.filterIsInstance<CashuTokenEvent>() - changeEvent).single()
            val keepContent = keepEvent.tokenContent(me)!!
            assertEquals(41L, keepContent.totalAmount())
            assertEquals(listOf(source.event.id), keepContent.del)

            // The keep proofs were never touched by the swap or the melt: only the
            // source secret and the 23-sat send bucket were consumed at the mint.
            keepContent.proofs.forEach { assertFalse(mint.isSpent(it.secret)) }
            changeContent.proofs.forEach { assertFalse(mint.isSpent(it.secret)) }

            // The NIP-09 delete names only the spent source event, never the keep event.
            assertEquals(listOf(source.event.id), deletes().single().deleteEventIds())

            // History marks the source destroyed and both survivors created.
            val history = published.filterIsInstance<CashuSpendingHistoryEvent>().single()
            val historyRefs = history.tokenReferences(me)
            val destroyed = historyRefs.filter { it.marker == TokenReference.MARKER_DESTROYED }.map { it.eventId }
            val created = historyRefs.filter { it.marker == TokenReference.MARKER_CREATED }.map { it.eventId }
            assertEquals(listOf(source.event.id), destroyed)
            assertEquals(setOf(keepEvent.id, changeEvent.id), created.toSet())

            // The wallet view keeps keep + refund; only the source rolls over.
            val unspentView = unspent(source)
            assertEquals(setOf(keepEvent.id, changeEvent.id), unspentView.map { it.event.id }.toSet())
            assertEquals(43L, unspentView.sumOf { it.content.totalAmount() })
        }
    }

    @Test
    fun `pre-swap melt without refund leaves the keep side alone`() {
        runBlocking {
            val mint = FakeMint(keysetId)
            val source = tokenEntry(64)

            val completed = walletOps(mint).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))

            assertNull(completed.newTokenEvent)
            // The mint kept the whole 2-sat fee reserve.
            assertEquals(2L, completed.fees)

            val keepEvent = published.filterIsInstance<CashuTokenEvent>().single()
            val keepContent = keepEvent.tokenContent(me)!!
            assertEquals(41L, keepContent.totalAmount())
            keepContent.proofs.forEach { assertFalse(mint.isSpent(it.secret)) }

            assertEquals(listOf(source.event.id), deletes().single().deleteEventIds())
            assertEquals(listOf(keepEvent.id), unspent(source).map { it.event.id })
        }
    }

    @Test
    fun `exact-amount melt rolls the refund over the source event`() {
        runBlocking {
            val mint = FakeMint(keysetId, invoiceAmount = 21)
            val source = tokenEntry(23)

            val completed = walletOps(mint).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))

            // No pre-swap: the source proofs went straight to the melt.
            assertEquals(23L, mint.meltedTotal)
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )

            val changeEvent = checkNotNull(completed.newTokenEvent)
            val changeContent = changeEvent.tokenContent(me)!!
            assertEquals(2L, changeContent.totalAmount())
            assertEquals(listOf(source.event.id), changeContent.del)
            assertEquals(0L, completed.fees)

            assertEquals(listOf(source.event.id), deletes().single().deleteEventIds())
            assertEquals(listOf(changeEvent.id), unspent(source).map { it.event.id })
        }
    }

    @Test
    fun `the refund is the mint's split of what it didn't spend`() {
        runBlocking {
            // 3 sats reserved, 1 kept by the backend: the 2 that come back are the amount the
            // mint decides, in the split it decides, not what the wallet reserved or asked for.
            val mint = FakeMint(keysetId, invoiceAmount = 21, lightningFee = 1)
            val source = tokenEntry(24)

            val completed = walletOps(mint).meltToLightning(mintUrl, quote(21, feeReserve = 3), listOf(source))

            assertEquals(24L, mint.meltedTotal)
            val changeContent = checkNotNull(completed.newTokenEvent).tokenContent(me)!!
            assertEquals(listOf(2L), changeContent.proofs.map { it.amount })
            assertEquals(1L, completed.fees)
            assertEquals(2L, unspent(source).sumOf { it.content.totalAmount() })
        }
    }

    /**
     * Relays echo a published kind:7375 straight back. Without the hand-off the wallet decrypts
     * its own event through the signer — on Amber or a bunker one more prompt per event, landing
     * while the melt's own prompts are still open.
     */
    @Test
    fun `the wallet is handed the plaintext of every token event it builds`() {
        runBlocking {
            val mint = FakeMint(keysetId, invoiceAmount = 21)
            val built = mutableMapOf<HexKey, TokenContent>()
            val ops =
                CashuWalletOps(
                    signer = me,
                    publish = { published += it },
                    mintTransport = mint.Transport(),
                    onTokenBuilt = { id, content -> built[id] = content },
                )
            val source = tokenEntry(64)

            ops.meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))

            // One hand-off per published kind:7375, each the content of the event under that id.
            val tokenEvents = published.filterIsInstance<CashuTokenEvent>()
            assertEquals(2, tokenEvents.size)
            assertEquals(tokenEvents.map { it.id }.toSet(), built.keys)
            tokenEvents.forEach { assertEquals(it.tokenContent(me), built.getValue(it.id)) }
        }
    }
}
