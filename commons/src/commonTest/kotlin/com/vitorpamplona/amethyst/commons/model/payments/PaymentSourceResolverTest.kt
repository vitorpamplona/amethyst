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
package com.vitorpamplona.amethyst.commons.model.payments

import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentSourceResolverTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
    private val pubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

    private fun nwc(id: String) = NwcWalletEntryNorm(id, "nwc-$id", Nip47WalletConnect.Nip47URINorm(pubKey, relay, secret = "ab".repeat(32)))

    private fun debit(id: String) = ClinkDebitWalletEntryNorm(id, "debit-$id", NDebit(pubKey, listOf(relay), "pointer-$id", null))

    @Test
    fun allListsNwcBeforeDebits() {
        val sources = PaymentSourceResolver.all(listOf(nwc("a")), listOf(debit("b")), cashuWalletConfigured = false)
        assertTrue(sources[0] is PaymentSource.Nwc)
        assertTrue(sources[1] is PaymentSource.ClinkDebit)
    }

    @Test
    fun explicitDefaultSelectsAcrossEitherType() {
        val nwcWallets = listOf(nwc("a"))
        val debits = listOf(debit("b"))

        // a debit can be the unified default even when an NWC wallet exists
        val resolved = PaymentSourceResolver.resolveDefault(nwcWallets, debits, defaultId = "b")
        assertTrue(resolved is PaymentSource.ClinkDebit)
        assertEquals("b", resolved.id)
    }

    @Test
    fun fallsBackToFirstNwcWhenNoExplicitDefault() {
        val resolved = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), listOf(debit("b")), defaultId = null)
        assertTrue(resolved is PaymentSource.Nwc)
        assertEquals("a", resolved.id)
    }

    @Test
    fun fallsBackToFirstDebitWhenNoNwc() {
        val resolved = PaymentSourceResolver.resolveDefault(emptyList(), listOf(debit("b"), debit("c")), defaultId = null)
        assertTrue(resolved is PaymentSource.ClinkDebit)
        assertEquals("b", resolved.id)
    }

    @Test
    fun staleDefaultIdFallsBackToFirst() {
        val resolved = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), listOf(debit("b")), defaultId = "deleted")
        assertEquals("a", resolved?.id)
    }

    @Test
    fun noSourcesResolvesToNull() {
        assertNull(PaymentSourceResolver.resolveDefault(emptyList(), emptyList(), defaultId = null))
    }

    @Test
    fun debitSourceCannotShowBalance() {
        assertTrue(PaymentSource.Nwc(nwc("a")).canShowBalance)
        assertTrue(!PaymentSource.ClinkDebit(debit("b")).canShowBalance)
    }

    @Test
    fun cashuOnlyWhenWalletConfigured() {
        val with = PaymentSourceResolver.all(emptyList(), emptyList(), cashuWalletConfigured = true)
        assertEquals(listOf<PaymentSource>(PaymentSource.Cashu), with)
        val without = PaymentSourceResolver.all(emptyList(), emptyList(), cashuWalletConfigured = false)
        assertTrue(without.isEmpty())
    }

    @Test
    fun cashuIsTheImplicitDefaultOnlyWhenNothingElseIsConfigured() {
        // No other source: the cashu wallet pays (better than an external wallet app).
        val alone = PaymentSourceResolver.resolveDefault(emptyList(), emptyList(), defaultId = null, cashuWalletConfigured = true)
        assertTrue(alone is PaymentSource.Cashu)

        // An NWC wallet is configured: cashu stays out of the way.
        val withNwc = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), emptyList(), defaultId = null, cashuWalletConfigured = true)
        assertTrue(withNwc is PaymentSource.Nwc)

        // A CLINK debit is configured: same.
        val withDebit = PaymentSourceResolver.resolveDefault(emptyList(), listOf(debit("b")), defaultId = null, cashuWalletConfigured = true)
        assertTrue(withDebit is PaymentSource.ClinkDebit)
    }

    @Test
    fun explicitCashuDefaultBeatsOtherSources() {
        val resolved = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), listOf(debit("b")), defaultId = PaymentSource.Cashu.ID, cashuWalletConfigured = true)
        assertTrue(resolved is PaymentSource.Cashu)

        // A "cashu" default with no wallet behind it self-heals to the first source.
        val stale = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), emptyList(), defaultId = PaymentSource.Cashu.ID, cashuWalletConfigured = false)
        assertEquals("a", stale?.id)
    }

    @Test
    fun cashuStaysOutOfTheImplicitDefaultWhenAsked() {
        // A signer that prompts per event (Amber, a bunker) with only a cashu wallet: no default,
        // so the zap falls through to an external wallet app as before the cashu rail existed.
        assertNull(
            PaymentSourceResolver.resolveDefault(
                emptyList(),
                emptyList(),
                defaultId = null,
                cashuWalletConfigured = true,
                cashuAsImplicitDefault = false,
            ),
        )

        // Other sources keep their implicit pick, and cashu still loses to them.
        val withNwc = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), emptyList(), defaultId = null, cashuWalletConfigured = true, cashuAsImplicitDefault = false)
        assertEquals("a", withNwc?.id)
    }

    @Test
    fun aPromptingSignerCanStillPickCashuExplicitly() {
        val picked = PaymentSourceResolver.resolveDefault(emptyList(), emptyList(), defaultId = PaymentSource.Cashu.ID, cashuWalletConfigured = true, cashuAsImplicitDefault = false)
        assertTrue(picked is PaymentSource.Cashu)

        // A stale explicit "cashu" with no wallet behind it still self-heals (to nothing here).
        val stale = PaymentSourceResolver.resolveDefault(emptyList(), emptyList(), defaultId = PaymentSource.Cashu.ID, cashuWalletConfigured = false, cashuAsImplicitDefault = false)
        assertNull(stale)
    }

    @Test
    fun anotherWalletAppPickedAsTheDefaultIsNoSourceEvenWithWalletsConfigured() {
        // An explicit pick, not a missing one: it must not fall through to the implicit default.
        val resolved =
            PaymentSourceResolver.resolveDefault(
                listOf(nwc("a")),
                listOf(debit("b")),
                defaultId = EXTERNAL_WALLET_APP_ID,
                cashuWalletConfigured = true,
            )

        assertNull(resolved)
    }
}
