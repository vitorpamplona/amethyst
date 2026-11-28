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

/**
 * A user-configured way to pay a BOLT-11 — the unit the zap button (and any other
 * "pay this invoice" path) selects a default from. Today either a NIP-47 NWC wallet
 * or a CLINK Debits pointer; no source (nothing configured, or another wallet app
 * picked through [EXTERNAL_WALLET_APP_ID]) means falling back to an external wallet app
 * (intent).
 *
 * [canShowBalance] is the honest capability marker: NWC can report balance/history,
 * a CLINK debit cannot, so the UI renders the two rows differently.
 */
sealed interface PaymentSource {
    val id: String
    val name: String
    val canShowBalance: Boolean

    /**
     * The NIP-60 ecash wallet. A singleton rather than a per-wallet entry: its [id] is the
     * stable [ID], persisted as the default-payment-source id like any NWC or CLINK id.
     * Paying through it melts proofs against the invoice and settles on the preimage.
     */
    data object Cashu : PaymentSource {
        /** Stable id persisted in synced settings — not a wallet UUID like NWC/CLINK ids. */
        const val ID = "cashu"

        override val id: String get() = ID

        /** Plain label; the Wallet tab row shows the localized `cashu_wallet_title` instead. */
        override val name: String get() = "Cashu"

        override val canShowBalance: Boolean get() = true
    }

    data class Nwc(
        val wallet: NwcWalletEntryNorm,
    ) : PaymentSource {
        override val id: String get() = wallet.id
        override val name: String get() = wallet.name
        override val canShowBalance: Boolean get() = true
    }

    data class ClinkDebit(
        val wallet: ClinkDebitWalletEntryNorm,
    ) : PaymentSource {
        override val id: String get() = wallet.id
        override val name: String get() = wallet.name
        override val canShowBalance: Boolean get() = false
    }
}

/**
 * The id stored when the user picks "another wallet app" as the default payment source: the
 * one choice that is no [PaymentSource]. [PaymentSourceResolver.resolveDefault] answers `null`
 * for it, which every payment path already handles by handing the invoice to a wallet app.
 */
const val EXTERNAL_WALLET_APP_ID = "external-wallet-app"
