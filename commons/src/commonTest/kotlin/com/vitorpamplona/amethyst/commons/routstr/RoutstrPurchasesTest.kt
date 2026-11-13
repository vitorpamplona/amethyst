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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The purchase state machine with every network call faked: the money rules, one by one. */
class RoutstrPurchasesTest {
    private val account = "a".repeat(64)
    private val node = RoutstrTarget("https://node.example", "http://" + "a".repeat(56) + ".onion", "Example")

    /** A node that answers as the test says, and remembers what it was asked. */
    private class FakeNode : RoutstrApi {
        var invoiceCount = 0
        val bases = mutableListOf<String>()
        var invoiceExpiresAt: Long? = 2_000L
        val status = HashMap<String, () -> RoutstrInvoiceStatus>()
        var recover: (String) -> RoutstrInvoiceStatus = { throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND)) }
        var cashu: (String, String?) -> RoutstrCashuResult = { token, _ -> RoutstrCashuResult("sk-" + token.takeLast(4), 21_000, null, null, null) }
        val topupKeys = mutableListOf<String?>()
        val tokensSent = mutableListOf<String>()

        override suspend fun info(base: String) = error("not used")

        override suspend fun models(
            base: String,
            apiKey: String?,
        ) = error("not used")

        override suspend fun balance(
            base: String,
            apiKey: String,
        ) = error("not used")

        override suspend fun createInvoice(
            base: String,
            amountSats: Long,
            purpose: RoutstrPurpose,
            apiKey: String?,
        ): RoutstrInvoice {
            bases += base
            topupKeys += apiKey
            invoiceCount++
            return RoutstrInvoice("inv$invoiceCount", "lnbc${amountSats}fake$invoiceCount", amountSats, invoiceExpiresAt, "hash")
        }

        override suspend fun invoiceStatus(
            base: String,
            invoiceId: String,
        ): RoutstrInvoiceStatus {
            bases += base
            return status[invoiceId]?.invoke() ?: throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND))
        }

        override suspend fun recover(
            base: String,
            bolt11: String,
        ) = recover.invoke(bolt11)

        override suspend fun createWithCashu(
            base: String,
            token: String,
        ): RoutstrCashuResult {
            tokensSent += token
            return cashu(token, null)
        }

        override suspend fun topupWithCashu(
            base: String,
            apiKey: String,
            token: String,
        ): RoutstrCashuResult {
            tokensSent += token
            return cashu(token, apiKey)
        }
    }

    private class FakeWallet : RoutstrCashuWallet {
        val received = mutableListOf<String>()
        var accepts = true
        var minted = 0

        override fun balances(mints: List<String>) = mints.associateWith { 10_000L }

        override suspend fun mintToken(
            mint: String,
            amountSats: Long,
        ): String {
            minted++
            return "cashuBminted$amountSats"
        }

        override suspend fun receive(token: String): Boolean {
            if (accepts) received += token
            return accepts
        }
    }

    private fun pending(status: String = "pending") = RoutstrInvoiceStatus(status, null, 1000, null, 2_000L)

    private fun paid(key: String? = "sk-bought") = RoutstrInvoiceStatus("paid", key, 1000, 1_500L, 2_000L)

    private class Setup(
        val api: FakeNode = FakeNode(),
        val store: InMemoryRoutstrPurchaseStore = InMemoryRoutstrPurchaseStore(),
        val wallet: FakeWallet = FakeWallet(),
        var saves: Boolean = true,
        var now: Long = 1_000L,
        var tor: Boolean = false,
    ) {
        val saved = mutableListOf<AgentKeyTag>()
        private var ids = 0

        fun purchases(account: String) =
            RoutstrPurchases(
                account = account,
                api = api,
                store = store,
                saveKey = { tag ->
                    if (saves) saved += tag
                    saves
                },
                wallet = { wallet },
                torEnabled = { tor },
                now = { now },
                newId = { "id${++ids}" },
            )
    }

    @Test
    fun theInvoiceIsKeptBeforeItIsShownThenTheKeyIsSaved() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val purchase = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            // Kept on the device before anyone could pay it.
            assertEquals(listOf(purchase), s.store.saved[account])
            assertEquals(RoutstrStage.AWAITING_PAYMENT, purchase.stage)
            assertEquals("lnbc1000fake1", purchase.bolt11)

            s.api.status["inv1"] = { pending() }
            assertEquals(RoutstrPurchaseOutcome.Waiting, purchases.check(purchase.id))

            s.api.status["inv1"] = { paid() }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.check(purchase.id))
            assertEquals(listOf(AgentKeyTag("sk-bought", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), s.store.saved[account])
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.outcomes.value[purchase.id])
        }

    @Test
    fun anInvoiceThisDeviceCantKeepIsNeverShown() =
        runTest {
            val s = Setup()
            s.store.failNextSave = IllegalStateException("disk full")
            val purchases = s.purchases(account)
            assertFailsWith<RoutstrNotPersistedException> { purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE) }
            assertEquals(emptyList(), purchases.pending.value)
        }

    @Test
    fun onLaunchAPendingPurchaseIsResumedAndRecoveredByBolt11() =
        runTest {
            val s = Setup()
            val bought = s.purchases(account).buyWithLightning(node, 1000, RoutstrPurpose.CREATE)

            // The app restarts; the node no longer knows the id, but the bolt11 still names the payment.
            s.api.recover = { bolt11 -> if (bolt11 == bought.bolt11) paid("sk-recovered") else throw RoutstrException(RoutstrError(404, RoutstrFailure.NOT_FOUND)) }
            val relaunched = s.purchases(account)
            relaunched.resume(backgroundScope)
            runCurrent()

            assertEquals(listOf(AgentKeyTag("sk-recovered", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), relaunched.pending.value)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aKeyThatCantBeSavedStaysPendingUntilItIs() =
        runTest {
            val s = Setup(saves = false)
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { paid("sk-held") }

            assertEquals(RoutstrPurchaseOutcome.KeyNotSaved, purchases.check(bought.id))
            val held = s.store.saved[account]!!.single()
            assertEquals(RoutstrStage.KEY_UNSAVED, held.stage)
            assertEquals("sk-held", held.apiKey)
            // The key never shows in a log line.
            assertTrue("sk-held" !in held.toString())

            // The next launch tries again, and this time kind 12000 takes it.
            s.saves = true
            val relaunched = s.purchases(account)
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), relaunched.check(bought.id))
            assertEquals(emptyList(), s.store.saved[account])
            // Never bought twice.
            assertEquals(1, s.api.invoiceCount)
        }

    @Test
    fun anUnpaidInvoiceIsDroppedADayAfterItExpires() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { pending("expired") }

            // Expired is not final: the node still settles it for 24 hours.
            s.now = 2_000L + 60
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(bought.id))
            assertEquals(1, purchases.pending.value.size)

            s.now = 2_000L + RoutstrPurchases.LATE_SETTLEMENT_SECONDS + 1
            assertEquals(RoutstrPurchaseOutcome.Expired, purchases.check(bought.id))
            assertEquals(emptyList(), purchases.pending.value)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun anInvoiceOurWalletPaidIsNeverDropped() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            purchases.markWalletPaid(bought.id)
            s.api.status["inv1"] = { pending("expired") }
            s.now = 2_000L + RoutstrPurchases.LATE_SETTLEMENT_SECONDS * 3

            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            assertEquals(RoutstrStage.PAID, purchases.get(bought.id)?.stage)
            assertEquals(false, purchases.abandon(bought.id))
            assertEquals(1, s.store.saved[account]!!.size)
        }

    @Test
    fun aSettlingOrKeylessPaidInvoiceIsRecoveredNotRebought() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)

            s.api.status["inv1"] = { pending("settlement_pending") }
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            s.api.status["inv1"] = { paid(key = null) }
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))
            // Once seen paid, an "expired" later doesn't drop it.
            s.api.status["inv1"] = { pending("expired") }
            s.now = 10_000_000L
            assertEquals(RoutstrPurchaseOutcome.PaidNoKeyYet, purchases.check(bought.id))

            s.api.recover = { paid("sk-late") }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.check(bought.id))
            assertEquals(1, s.api.invoiceCount)
        }

    @Test
    fun watchPollsUntilPaidOnVirtualTime() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            var polls = 0
            s.api.status["inv1"] = { if (++polls < 4) pending() else paid() }
            purchases.follow(backgroundScope, bought.id)
            advanceTimeBy(RoutstrPurchases.POLL_MS * 2 + 1)
            assertEquals(3, polls)
            assertEquals(emptyList(), s.saved)
            advanceTimeBy(RoutstrPurchases.POLL_MS)
            runCurrent()
            assertEquals(1, s.saved.size)
        }

    @Test
    fun aTopUpSendsItsKeyAndTorUsesTheOnion() =
        runTest {
            val s = Setup(tor = true)
            val purchases = s.purchases(account)
            val key = AgentKeyTag("sk-existing", "https://node.example/v1")
            val bought = purchases.buyWithLightning(node, 5000, RoutstrPurpose.TOPUP, key)
            assertEquals("sk-existing", s.api.topupKeys.single())
            assertTrue(
                s.api.bases
                    .single()
                    .endsWith(".onion"),
            )
            // Its record says which key, but never holds it.
            assertTrue("sk-existing" !in s.store.saved[account].toString())
            assertNull(bought.apiKey)

            s.api.status["inv1"] = { RoutstrInvoiceStatus("paid", null, 5000, 1_500, 2_000) }
            assertEquals(RoutstrPurchaseOutcome.ToppedUp, purchases.check(bought.id))
            assertEquals(emptyList(), s.saved)
        }

    @Test
    fun aCashuTokenIsNeverStoredAndItsKeyIsSaved() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val outcome = purchases.buyWithCashu(node, "cashuBpasted1234", RoutstrPurpose.CREATE)
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), outcome)
            assertEquals(listOf(AgentKeyTag("sk-1234", "https://node.example/v1")), s.saved)
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun aLostAnswerHandsTheTokenBackToSendAgain() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE)) }
            val lost = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyWithCashu(node, "cashuBpasted1234", RoutstrPurpose.CREATE))
            assertEquals("cashuBpasted1234", lost.token)
            // The token was never written anywhere.
            assertTrue(
                s.store.saved[account]
                    .orEmpty()
                    .none { "cashuBpasted1234" in (it.token ?: "") },
            )
            assertTrue("cashuBpasted1234" !in lost.toString())

            // Sent again, the node gives the same key: sk- + sha256(token).
            s.api.cashu = { token, _ -> RoutstrCashuResult("sk-" + token.takeLast(4), 21_000, null, null, null) }
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.buyWithCashu(node, lost.token!!, RoutstrPurpose.CREATE))
        }

    @Test
    fun aRefusedWalletTokenGoesBackToTheWallet() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> throw RoutstrException(RoutstrError(400, RoutstrFailure.TOKEN_REJECTED)) }
            val refused = assertIs<RoutstrPurchaseOutcome.TokenNotTaken>(purchases.buyFromWallet(node, "https://mint.example", 2000, RoutstrPurpose.CREATE))
            assertNull(refused.token)
            assertEquals(listOf("cashuBminted2000"), s.wallet.received)
        }

    @Test
    fun changeIsHandedBackToTheWallet() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            s.api.cashu = { _, key -> RoutstrCashuResult(if (key == null) "sk-new" else null, 20_000, "cashuBchange", 3, "sat") }
            val key = AgentKeyTag("sk-existing", "https://node.example/v1")

            assertEquals(RoutstrPurchaseOutcome.ToppedUp, purchases.buyWithCashu(node, "cashuBforeign", RoutstrPurpose.TOPUP, key))
            assertEquals(listOf("cashuBchange"), s.wallet.received)
            assertEquals(emptyList(), purchases.pending.value)

            // Create returns change too, now and then: handled the same way.
            assertEquals(RoutstrPurchaseOutcome.KeySaved("https://node.example/v1"), purchases.buyWithCashu(node, "cashuBagain", RoutstrPurpose.CREATE))
            assertEquals(listOf("cashuBchange", "cashuBchange"), s.wallet.received)
        }

    @Test
    fun changeTheWalletWontTakeIsKeptUntilItDoes() =
        runTest {
            val s = Setup()
            s.wallet.accepts = false
            val purchases = s.purchases(account)
            s.api.cashu = { _, _ -> RoutstrCashuResult(null, 20_000, "cashuBchange", 3, "sat") }
            purchases.buyWithCashu(node, "cashuBforeign", RoutstrPurpose.TOPUP, AgentKeyTag("sk-existing", "https://node.example/v1"))

            val kept = s.store.saved[account]!!.single()
            assertEquals(RoutstrStage.CHANGE_UNCLAIMED, kept.stage)
            assertEquals("cashuBchange", kept.token)
            assertEquals(3L, kept.amountSats)

            s.wallet.accepts = true
            assertEquals(RoutstrPurchaseOutcome.ChangeReturned, purchases.check(kept.id))
            assertEquals(emptyList(), s.store.saved[account])
        }

    @Test
    fun pendingPurchasesArePerAccount() =
        runTest {
            val s = Setup()
            s.purchases(account).buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            assertEquals(emptyList(), s.purchases("b".repeat(64)).load())
            assertEquals(1, s.purchases(account).load().size)
        }

    @Test
    fun anAbandonedUnpaidInvoiceGoes() =
        runTest {
            val s = Setup()
            val purchases = s.purchases(account)
            val bought = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv1"] = { pending() }
            assertTrue(purchases.abandon(bought.id))
            assertEquals(emptyList(), purchases.pending.value)

            // A paid one is kept, whatever the user asked.
            val second = purchases.buyWithLightning(node, 1000, RoutstrPurpose.CREATE)
            s.api.status["inv2"] = { paid(key = null) }
            assertEquals(false, purchases.abandon(second.id))
            assertEquals(1, purchases.pending.value.size)
        }
}
