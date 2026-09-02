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

import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentTargetTypesTest {
    @Test
    fun canonicalTrimsAndLowercases() {
        assertEquals("venmo", PaymentTargetTypes.canonical("  VenMo "))
        assertEquals("iban", PaymentTargetTypes.canonical("IBAN"))
    }

    @Test
    fun canonicalCollapsesAliasesWithinARailOnly() {
        assertEquals("bitcoin", PaymentTargetTypes.canonical("btc"))
        assertEquals("bitcoin", PaymentTargetTypes.canonical("onchain"))
        assertEquals("lightning", PaymentTargetTypes.canonical("ln"))
        assertEquals("lightning", PaymentTargetTypes.canonical("lnurl"))
        assertEquals("monero", PaymentTargetTypes.canonical("xmr"))
        // Different rails must never collapse together.
        assertTrue(PaymentTargetTypes.canonical("monero") != PaymentTargetTypes.canonical("bitcoin"))
    }

    @Test
    fun unknownTypesPassThroughUntouched() {
        assertEquals("pix", PaymentTargetTypes.canonical("pix"))
        assertEquals("upi", PaymentTargetTypes.canonical("upi"))
    }

    @Test
    fun walletCoveredIsExactlyTheLightningAndBitcoinFamilies() {
        listOf("lightning", "ln", "LNURL", "bitcoin", "btc", " onchain ").forEach {
            assertTrue(PaymentTargetTypes.isWalletCovered(it), "$it should be wallet covered")
        }
        listOf("venmo", "monero", "iban", "liquid", "ethereum").forEach {
            assertFalse(PaymentTargetTypes.isWalletCovered(it), "$it should not be wallet covered")
        }
    }

    @Test
    fun uriUsesTheDedicatedSchemeWhenThereIsOne() {
        assertEquals("bitcoin:bc1qxyz", PaymentTargetTypes.uriFor("btc", "bc1qxyz"))
        assertEquals("lightning:me@ln.tips", PaymentTargetTypes.uriFor("lnurl", "me@ln.tips"))
        assertEquals("liquidnetwork:lq1abc", PaymentTargetTypes.uriFor("liquid", "lq1abc"))
        assertEquals("monero:4Aaddr", PaymentTargetTypes.uriFor("XMR", "4Aaddr"))
    }

    @Test
    fun webTypesBecomeHttpsPages() {
        assertEquals("https://venmo.com/vitor", PaymentTargetTypes.uriFor("venmo", "vitor"))
        assertEquals("https://cash.app/\$vitor", PaymentTargetTypes.uriFor("cashapp", "\$vitor"))
        assertTrue(PaymentTargetTypes.isWebTarget("PayPal"))
        assertFalse(PaymentTargetTypes.isWebTarget("iban"))
    }

    @Test
    fun unknownTypesFallBackToPayto() {
        assertEquals("payto://iban/DE75512108001245126199", PaymentTargetTypes.uriFor("IBAN", "DE75512108001245126199"))
        assertEquals("payto://upi/vitor@bank", PaymentTargetTypes.uriFor("upi", " vitor@bank "))
    }

    @Test
    fun probeKeyKeepsHostSoPaytoTypesDoNotShareOneAnswer() {
        // An app may declare scheme="payto" host="iban"; a scheme-only key would
        // then wrongly report that payto://upi is handled too.
        assertEquals("payto://iban/", PaymentTargetTypes.probeKeyFor("iban"))
        assertEquals("payto://upi/", PaymentTargetTypes.probeKeyFor("upi"))
        assertTrue(PaymentTargetTypes.probeKeyFor("iban") != PaymentTargetTypes.probeKeyFor("upi"))
        // Aliases of one rail share a key, as they share a scheme.
        assertEquals(PaymentTargetTypes.probeKeyFor("btc"), PaymentTargetTypes.probeKeyFor("bitcoin"))
    }
}

class PayToRailMatcherTest {
    private fun t(
        type: String,
        authority: String = "handle",
    ) = PaymentTarget(type, authority)

    @Test
    fun noSenderTargetsMeansNoChips() {
        assertEquals(emptyList(), PayToRailMatcher.match(emptyList(), listOf(t("venmo"))))
    }

    @Test
    fun noRecipientTargetsMeansNoChips() {
        assertEquals(emptyList(), PayToRailMatcher.match(listOf(t("venmo")), emptyList()))
    }

    @Test
    fun noSharedProtocolMeansNoChips() {
        assertEquals(emptyList(), PayToRailMatcher.match(listOf(t("venmo")), listOf(t("paypal"))))
    }

    @Test
    fun sharedProtocolMatchesAcrossCaseAndWhitespace() {
        val out = PayToRailMatcher.match(listOf(t(" VENMO ")), listOf(t("venmo", "vitor")))
        assertEquals(listOf(t("venmo", "vitor")), out)
    }

    @Test
    fun walletCoveredTypesNeverProduceAChip() {
        // Both sides publish lightning and bitcoin, but those ARE the existing
        // rails — matching them would draw a second bolt beside the first.
        val both = listOf(t("lightning", "a@b.c"), t("btc", "bc1q"), t("ln", "x@y.z"))
        assertEquals(emptyList(), PayToRailMatcher.match(both, both))
    }

    @Test
    fun aliasesOnEitherSideStillMatch() {
        val out = PayToRailMatcher.match(listOf(t("xmr", "mine")), listOf(t("monero", "theirs")))
        assertEquals(listOf(t("monero", "theirs")), out)
    }

    @Test
    fun oneChipPerProtocolKeepingTheFirst() {
        val recipient = listOf(t("venmo", "first"), t("venmo", "second"), t("monero", "xmr1"))
        val out = PayToRailMatcher.match(listOf(t("venmo"), t("monero")), recipient)
        assertEquals(listOf(t("venmo", "first"), t("monero", "xmr1")), out)
    }

    @Test
    fun blankAuthoritiesAreSkipped() {
        assertEquals(emptyList(), PayToRailMatcher.match(listOf(t("venmo")), listOf(t("venmo", "   "))))
    }

    @Test
    fun recipientOrderIsPreserved() {
        val recipient = listOf(t("monero", "m"), t("venmo", "v"))
        val out = PayToRailMatcher.match(listOf(t("venmo"), t("monero")), recipient)
        assertEquals(listOf("monero", "venmo"), out.map { it.type })
    }
}

/** The gates on the hand-off chip, exercised without a Note or a PackageManager. */
class PayToRailGateTest {
    private fun t(
        type: String,
        authority: String = "handle",
    ) = PaymentTarget(type, authority)

    private val mine = listOf(t("venmo", "me"), t("monero", "myxmr"))
    private val theirs = listOf(t("venmo", "them"), t("monero", "theirxmr"))
    private val anyAppOpens: (PaymentTarget) -> Boolean = { true }

    private fun select(
        enabled: Boolean = true,
        hasAuthor: Boolean = true,
        hasZapSplit: Boolean = false,
        sender: List<PaymentTarget> = mine,
        recipient: List<PaymentTarget> = theirs,
        canOpen: (PaymentTarget) -> Boolean = anyAppOpens,
    ) = PayToRailMatcher.selectFor(enabled, hasAuthor, hasZapSplit, sender, recipient, canOpen)

    @Test
    fun offeredWhenEveryGatePasses() {
        assertEquals(listOf("venmo", "monero"), select().map { it.type })
    }

    @Test
    fun settingOffHidesIt() {
        assertEquals(emptyList(), select(enabled = false))
    }

    @Test
    fun aZapSplitHidesIt() {
        // payto leaves with one authority and returns no receipt, so it cannot
        // honour a note that asks for the zap to be divided.
        assertEquals(emptyList(), select(hasZapSplit = true))
    }

    @Test
    fun noAuthorHidesIt() {
        assertEquals(emptyList(), select(hasAuthor = false))
    }

    @Test
    fun aTargetNoInstalledAppCanOpenIsDropped() {
        val out = select(canOpen = { it.type == "venmo" })
        assertEquals(listOf("venmo"), out.map { it.type })
    }

    @Test
    fun noInstalledAppAtAllHidesIt() {
        assertEquals(emptyList(), select(canOpen = { false }))
    }

    @Test
    fun cappedSoThePopupCannotGrowWithoutBound() {
        val many = listOf(t("venmo"), t("monero"), t("pix"), t("upi"), t("iban"))
        assertEquals(PayToRailMatcher.MAX_CHIPS, select(sender = many, recipient = many).size)
    }

    @Test
    fun lightningAndBitcoinStayWithTheirOwnRails() {
        val wallets = listOf(t("lightning", "a@b.c"), t("btc", "bc1q"))
        assertEquals(emptyList(), select(sender = wallets, recipient = wallets))
    }
}
