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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.payments.EXTERNAL_WALLET_APP_ID
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one decision behind both the zap button and the Wallet tab's default marker. */
class DefaultPaymentSourceTest {
    private val keyPair = KeyPair()
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
    private val pubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

    private fun nwc(id: String) = NwcWalletEntryNorm(id, "nwc-$id", Nip47WalletConnect.Nip47URINorm(pubKey, relay, secret = "ab".repeat(32)))

    private fun debit(id: String) = ClinkDebitWalletEntryNorm(id, "debit-$id", NDebit(pubKey, listOf(relay), "pointer-$id", null))

    @Test
    fun aLocalKeyAccountWithOnlyACashuWalletPaysFromItByDefault() {
        val settings = AccountSettings(keyPair = keyPair)

        assertEquals(PaymentSource.Cashu, settings.defaultPaymentSource(cashuWalletConfigured = true))
    }

    @Test
    fun anAmberAccountWithOnlyACashuWalletHasNoDefaultUntilItPicksCashu() {
        val settings = AccountSettings(keyPair = keyPair, externalSignerPackageName = "com.greenart7c3.nostrsigner")

        assertNull(settings.defaultPaymentSource(cashuWalletConfigured = true))

        settings.defaultPaymentSourceId.value = PaymentSource.Cashu.ID

        assertEquals(PaymentSource.Cashu, settings.defaultPaymentSource(cashuWalletConfigured = true))
    }

    @Test
    fun aBunkerAccountIsTreatedLikeAnAmberOne() {
        val settings =
            AccountSettings(
                keyPair = keyPair,
                remoteSignerBunkerUri = "bunker://example",
                remoteSignerTransportKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            )

        assertNull(settings.defaultPaymentSource(cashuWalletConfigured = true))
    }

    @Test
    fun anotherWalletAppCanBePickedAsTheDefaultAndIsNoSource() {
        val settings = AccountSettings(keyPair = keyPair)
        settings.defaultPaymentSourceId.value = PaymentSource.Cashu.ID

        assertTrue(settings.setDefaultPaymentSource(EXTERNAL_WALLET_APP_ID))

        // Kept as the pick, so it survives a restart, and resolved to nothing: every payment
        // then hands the invoice to a wallet app.
        assertEquals(EXTERNAL_WALLET_APP_ID, settings.defaultPaymentSourceId.value)
        assertNull(settings.defaultPaymentSource(cashuWalletConfigured = true))
    }

    @Test
    fun anUnpickableIdIsStillRefused() {
        val settings = AccountSettings(keyPair = keyPair)

        assertFalse(settings.setDefaultPaymentSource("no-such-source"))
        assertNull(settings.defaultPaymentSourceId.value)
    }

    /**
     * The previous version stored the id of an NWC wallet or of a CLINK debit, or nothing. The
     * cashu wallet and "Another wallet app" are new candidates: neither may move a default the
     * account already has.
     */
    @Test
    fun upgradingKeepsTheDefaultTheAccountAlreadyHas() {
        val nwc = nwc("a5f0")
        val debit = debit("c71e")
        val settings = AccountSettings(keyPair = keyPair)
        settings.nwcWallets.value = listOf(nwc)
        settings.clinkDebitWallets.value = listOf(debit)

        settings.defaultPaymentSourceId.value = nwc.id
        assertEquals(PaymentSource.Nwc(nwc), settings.defaultPaymentSource(cashuWalletConfigured = true))

        settings.defaultPaymentSourceId.value = debit.id
        assertEquals(PaymentSource.ClinkDebit(debit), settings.defaultPaymentSource(cashuWalletConfigured = true))

        // Nothing picked: the first wallet still pays, and resolving it picks nothing.
        settings.defaultPaymentSourceId.value = null
        assertEquals(PaymentSource.Nwc(nwc), settings.defaultPaymentSource(cashuWalletConfigured = true))
        assertFalse(settings.isDefaultPaymentSource(PaymentSource.Cashu))
        assertFalse(settings.isDefaultPaymentSource(PaymentSource.ClinkDebit(debit)))
        assertNull(settings.defaultPaymentSourceId.value)
    }

    @Test
    fun withoutACashuWalletTheMarkerIsNeverDrawnForIt() {
        val settings = AccountSettings(keyPair = keyPair)

        assertFalse(settings.isDefaultPaymentSource(PaymentSource.Cashu))
    }
}
