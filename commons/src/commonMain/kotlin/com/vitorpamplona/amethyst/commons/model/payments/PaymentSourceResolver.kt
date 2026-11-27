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
 * Builds the unified list of configured [PaymentSource]s (NWC + CLINK debit + the cashu
 * wallet) and resolves which one is the default for "pay this invoice" paths.
 *
 * The default is a single id spanning every list (random UUIDs, unique across types; the
 * cashu wallet uses [PaymentSource.Cashu.ID]), so one selector picks the spend rail
 * regardless of type. When no explicit default is set, the first configured source wins:
 * NWC wallets before debits, and the cashu wallet only when nothing else is configured —
 * those users would otherwise fall through to an external wallet app while holding
 * spendable ecash. Deleting the source the id points at self-heals through the `null` fallback.
 * Picking another wallet app ([EXTERNAL_WALLET_APP_ID]) is a pick like any other and answers
 * `null`.
 */
object PaymentSourceResolver {
    /** [cashuWalletConfigured] has no default on purpose: a picker that forgets it lists the cashu wallet's id but cannot resolve it. */
    fun all(
        nwcWallets: List<NwcWalletEntryNorm>,
        debitWallets: List<ClinkDebitWalletEntryNorm>,
        cashuWalletConfigured: Boolean,
    ): List<PaymentSource> =
        nwcWallets.map { PaymentSource.Nwc(it) } +
            debitWallets.map { PaymentSource.ClinkDebit(it) } +
            if (cashuWalletConfigured) listOf(PaymentSource.Cashu) else emptyList()

    /**
     * [cashuWalletConfigured] makes the cashu wallet a candidate, so an explicit default id
     * naming it resolves only while a wallet exists. [cashuAsImplicitDefault] decides whether
     * it may be picked when no default is set: a signer that prompts for every event turns one
     * cashu-paid zap into 4+ approvals, so those accounts only melt when they picked the wallet.
     */
    fun resolveDefault(
        nwcWallets: List<NwcWalletEntryNorm>,
        debitWallets: List<ClinkDebitWalletEntryNorm>,
        defaultId: String?,
        cashuWalletConfigured: Boolean = false,
        cashuAsImplicitDefault: Boolean = cashuWalletConfigured,
    ): PaymentSource? {
        // An explicit "another wallet app" is a pick, not a missing one: it must not fall
        // through to the implicit default.
        if (defaultId == EXTERNAL_WALLET_APP_ID) return null
        val sources = all(nwcWallets, debitWallets, cashuWalletConfigured)
        defaultId?.let { id -> sources.firstOrNull { it.id == id } }?.let { return it }
        if (cashuAsImplicitDefault) return sources.firstOrNull()
        return sources.firstOrNull { it !is PaymentSource.Cashu }
    }

    fun resolveDefault(
        sources: List<PaymentSource>,
        defaultId: String?,
    ): PaymentSource? = defaultId?.let { id -> sources.firstOrNull { it.id == id } } ?: sources.firstOrNull()
}
