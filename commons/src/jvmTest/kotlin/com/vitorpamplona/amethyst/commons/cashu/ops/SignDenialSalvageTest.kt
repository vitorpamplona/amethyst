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
import com.vitorpamplona.amethyst.commons.notices.CashuMeltNotice
import com.vitorpamplona.amethyst.commons.notices.NutzapNotice
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip60Cashu.history.TokenReference
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to a spend when the signer denies a prompt after the mint round-trip already
 * consumed the source proofs. Without a rollback, both swap sides would live only in the
 * stack frame while the NUT-07 scrub silently dropped the spent sources: the balance would
 * fall with no outgoing transaction.
 *
 * The wallet rolls the change back into a fresh kind:7375 `del`-tagging the spent sources,
 * NIP-09-deletes them and writes an OUT history row for what was lost, then throws an
 * exception that says exactly what happened.
 */
class SignDenialSalvageTest {
    private val mintUrl = "https://mint.example.com"
    private val keysetId = "01182e5f5e35aecd"

    private val me = NostrSignerInternal(KeyPair())
    private val recipient = KeyPair()
    private val published = mutableListOf<Event>()

    private fun walletOps(
        mint: FakeMint,
        signer: NostrSigner = me,
    ) = CashuWalletOps(signer = signer, publish = { published += it }, mintTransport = mint.Transport())

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

    private suspend fun unspent(source: TokenEntry): List<TokenEntry> {
        val events = listOf(source.event) + published.filterIsInstance<CashuTokenEvent>()
        val contents = CashuWalletReader(me).decryptTokens(events)
        return CashuWalletReader.computeUnspent(events, contents)
    }

    private suspend fun sendNutzap(
        ops: CashuWalletOps,
        source: TokenEntry,
    ) = ops.sendNutzap(
        mintUrl = mintUrl,
        amountSats = 21,
        recipientPubKey = recipient.pubKey.toHexKey(),
        recipientP2pkPubkeyHex = recipient.pubKey.toHexKey(),
        zappedEvent = null,
        message = "",
        available = listOf(source),
    )

    @Test
    fun `denied nutzap sign rolls the change back and records the loss`() {
        runBlocking {
            val mint = FakeMint(keysetId)
            val source = tokenEntry(64)
            val signer = DenyingSigner(me) { kind, _ -> kind == NutzapEvent.KIND }

            val error = runCatching { sendNutzap(walletOps(mint, signer), source) }.exceptionOrNull()!!

            // The mint consumed the source proof before the signer was ever asked.
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )

            // The change (64 - 21) came back as a keep event that replaces the source.
            val keepEvent = published.filterIsInstance<CashuTokenEvent>().single()
            val keepContent = keepEvent.tokenContent(me)!!
            assertEquals(43L, keepContent.totalAmount())
            assertEquals(listOf(source.event.id), keepContent.del)
            keepContent.proofs.forEach { assertTrue(!mint.isSpent(it.secret)) }

            // The spent source is deleted from relays, so the scrub has nothing to drop silently.
            assertEquals(
                listOf(source.event.id),
                published.filterIsInstance<DeletionRequestEvent>().single().deleteEventIds(),
            )

            // The loss is visible accounting: an OUT row of the 21 locked sats.
            val history = published.filterIsInstance<CashuSpendingHistoryEvent>().single()
            val refs = history.tokenReferences(me)
            assertEquals(
                listOf(source.event.id),
                refs.filter { it.marker == TokenReference.MARKER_DESTROYED }.map { it.eventId },
            )
            assertEquals(
                listOf(keepEvent.id),
                refs.filter { it.marker == TokenReference.MARKER_CREATED }.map { it.eventId },
            )
            assertEquals(21L, history.amount(me))
            assertEquals(SpendingDirection.OUT, history.direction(me))

            // The wallet view: only the keep event survives, with the rolled-back change.
            assertEquals(listOf(keepEvent.id), unspent(source).map { it.event.id })
            assertEquals(43L, unspent(source).sumOf { it.content.totalAmount() })

            // The error tells the user what was rolled back and what was lost.
            assertEquals(
                NutzapNotice.SendAborted(sent = false, amountSats = 21, returnedSats = 43, unrecoverableSats = 21, recoverableFromSeedSats = 0L),
                (error as NutzapSendAbortedException).notice,
            )
            assertTrue(error.message!!, error.message!!.contains("rolled back"))
            assertTrue(error.message!!, error.message!!.contains("21"))
        }
    }

    @Test
    fun `a signer that denies everything points at seed recovery`() {
        runBlocking {
            val mint = FakeMint(keysetId)
            val source = tokenEntry(64)
            val signer = DenyingSigner(me) { _, _ -> true }

            val error = runCatching { sendNutzap(walletOps(mint, signer), source) }.exceptionOrNull()!!

            // Mint-side consumption happened before the first prompt: the swap is irreversible.
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )

            // Nothing could be published; the only way back is NUT-09.
            assertTrue(published.isEmpty())
            assertEquals(
                NutzapNotice.SendAborted(sent = false, amountSats = 21, returnedSats = 0, unrecoverableSats = 21, recoverableFromSeedSats = 43),
                (error as NutzapSendAbortedException).notice,
            )
            val message = error.message ?: ""
            assertTrue(message, message.contains("Recover from seed"))
            assertTrue(message, message.contains("64"))
        }
    }

    @Test
    fun `denied post-melt sign salvages the refund`() {
        runBlocking {
            val mint = FakeMint(keysetId, invoiceAmount = 21)
            val source = tokenEntry(64)
            // Signs in order: pre-swap keep (7375, allowed), refund change (7375, denied here).
            val signer = DenyingSigner(me) { kind, ordinal -> kind == CashuTokenEvent.KIND && ordinal == 2 }

            val error =
                runCatching {
                    walletOps(mint, signer).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))
                }.exceptionOrNull()!!

            // The invoice was paid: 23 sats of melt inputs (21 + 2 reserve).
            assertEquals(23L, mint.meltedTotal)

            // The pre-swap keep (41) and the salvaged refund (2) both survive.
            val changeEvents = published.filterIsInstance<CashuTokenEvent>()
            assertEquals(setOf(41L, 2L), changeEvents.map { it.tokenContent(me)!!.totalAmount() }.toSet())
            changeEvents.forEach { event ->
                event.tokenContent(me)!!.proofs.forEach { assertTrue(!mint.isSpent(it.secret)) }
            }
            assertEquals(43L, unspent(source).sumOf { it.content.totalAmount() })

            // The payment itself is recorded: an OUT history row of the invoice amount.
            val history = published.filterIsInstance<CashuSpendingHistoryEvent>().single()
            assertEquals(21L, history.amount(me))

            // And the error says the payment went through and the change was kept.
            assertEquals(CashuMeltNotice.PaidUnrecorded(paidSats = 21), (error as MeltRecordAbortedException).failure)
            val message = error.message ?: ""
            assertTrue(message, message.contains("paid"))
            assertTrue(message, message.contains("2"))
        }
    }

    @Test
    fun `denied pre-swap keep sign rolls the whole swap back`() {
        runBlocking {
            val mint = FakeMint(keysetId)
            val source = tokenEntry(64)
            // Signs in order: pre-swap keep (7375, ordinal 1 — DENIED here), salvage
            // events (7375 ordinal 2, kind 5, kind 7376 — all allowed).
            val signer = DenyingSigner(me) { kind, ordinal -> kind == CashuTokenEvent.KIND && ordinal == 1 }

            val error =
                runCatching {
                    walletOps(mint, signer).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))
                }.exceptionOrNull()!!

            // The swap consumed the source proof before the first prompt — but the
            // melt never ran.
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )
            assertEquals(0L, mint.meltedTotal)

            // Both swap sides (41 keep + 23 send) come back as ONE fresh kind:7375
            // del-tagging the source: nothing left the wallet.
            val rollback = published.filterIsInstance<CashuTokenEvent>().single()
            val rollbackContent = rollback.tokenContent(me)!!
            assertEquals(64L, rollbackContent.totalAmount())
            assertEquals(listOf(source.event.id), rollbackContent.del)
            rollbackContent.proofs.forEach { assertTrue(!mint.isSpent(it.secret)) }

            assertEquals(
                listOf(source.event.id),
                published.filterIsInstance<DeletionRequestEvent>().single().deleteEventIds(),
            )

            assertEquals(listOf(rollback.id), unspent(source).map { it.event.id })
            assertEquals(64L, unspent(source).sumOf { it.content.totalAmount() })

            // Aborted (not "not paid", not "paid"): the user denied, so the caller
            // must not silently retry on another rail.
            assertTrue(error is MeltAbortedException)
            assertEquals(CashuMeltNotice.Aborted(returnedSats = 64), (error as MeltAbortedException).failure)
            val message = error.message ?: ""
            assertTrue(message, message.contains("rolled back"))
            assertTrue(message, !message.contains("Recover from seed"))
        }
    }

    @Test
    fun `a mint that refuses the melt after the pre-swap gets its inputs back`() {
        runBlocking {
            val mint = FakeMint(keysetId, refuseMelt = true)
            val source = tokenEntry(64)

            val error =
                runCatching {
                    walletOps(mint).meltToLightning(mintUrl, quote(21, feeReserve = 2), listOf(source))
                }.exceptionOrNull()!!

            // The swap consumed the source, but the melt refused: nothing was paid.
            assertTrue(
                mint.isSpent(
                    source.content.proofs
                        .single()
                        .secret,
                ),
            )
            assertEquals(0L, mint.meltedTotal)

            // The pre-swap keep (41) and the re-published send side (23) both survive,
            // so the wallet still sees the full 64.
            val changeEvents = published.filterIsInstance<CashuTokenEvent>()
            assertEquals(setOf(41L, 23L), changeEvents.map { it.tokenContent(me)!!.totalAmount() }.toSet())
            assertEquals(64L, unspent(source).sumOf { it.content.totalAmount() })

            // "Not paid" with the wallet intact: the caller may pay this invoice
            // through another rail.
            assertTrue(error is MeltNotPaidException)
            assertEquals(23L, ((error as MeltNotPaidException).failure as CashuMeltNotice.Refunded).returnedSats)
            val message = error.message ?: ""
            assertTrue(message, message.contains("returned to the wallet"))
            assertTrue(message, message.contains("23"))
        }
    }

    /** Signs with [inner] but throws a NIP-46-style denial whenever [deny] says so. */
    private class DenyingSigner(
        private val inner: NostrSignerInternal,
        private val deny: (kind: Int, kindOrdinal: Int) -> Boolean,
    ) : NostrSigner(inner.pubKey) {
        private val kindCounts = HashMap<Int, Int>()

        override fun isWriteable(): Boolean = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T {
            val ordinal = (kindCounts[kind] ?: 0) + 1
            kindCounts[kind] = ordinal
            if (deny(kind, ordinal)) throw SignerExceptions.ManuallyUnauthorizedException("User has rejected the request.")
            return inner.sign(createdAt, kind, tags, content)
        }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = inner.nip44Decrypt(ciphertext, fromPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey): HexKey = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String): String = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport(): Boolean = true
    }
}
