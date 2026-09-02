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

/**
 * Picks the NIP-A3 payment targets a sender can plausibly use to pay a
 * recipient: the recipient's targets whose protocol the sender also publishes.
 *
 * The symmetry rule is a proxy for "I can actually pay this way" and is exactly
 * right for closed loops — both parties need Venmo accounts for a Venmo
 * transfer to mean anything. It is arguably too strict for open protocols
 * (paying a Monero address needs a wallet, not a published address of one), but
 * it starts conservative: relaxing it later only ever adds chips.
 *
 * It also bounds the installed-app probe. Because only protocols the *sender*
 * declares can ever be shown, the probe set is the sender's own target list —
 * a handful of entries — rather than anything that grows with the feed.
 */
object PayToRailMatcher {
    /**
     * Recipient targets payable by symmetry, de-duplicated by canonical type and
     * in the recipient's published order.
     *
     * Wallet-covered types (lightning, bitcoin) are dropped: those are the
     * picker's existing Lightning and on-chain rails, and re-offering them as a
     * hand-off would draw a second bolt icon beside the first.
     */
    fun match(
        senderTargets: List<PaymentTarget>,
        recipientTargets: List<PaymentTarget>,
    ): List<PaymentTarget> {
        if (senderTargets.isEmpty() || recipientTargets.isEmpty()) return emptyList()

        val senderTypes =
            senderTargets
                .asSequence()
                .map { PaymentTargetTypes.canonical(it.type) }
                .filterNot { it.isEmpty() || PaymentTargetTypes.isWalletCovered(it) }
                .toSet()

        if (senderTypes.isEmpty()) return emptyList()

        val seen = mutableSetOf<String>()
        return recipientTargets.filter { target ->
            val type = PaymentTargetTypes.canonical(target.type)
            type.isNotEmpty() &&
                target.authority.isNotBlank() &&
                !PaymentTargetTypes.isWalletCovered(type) &&
                type in senderTypes &&
                seen.add(type)
        }
    }

    /** With discovery filtering, 0-1 is the normal case; the cap stops a wide popup. */
    const val MAX_CHIPS = 2

    /**
     * Every gate on the hand-off chip, as one pure decision.
     *
     * Kept free of `Note`, `Context` and the availability singleton so the gates
     * are testable on their own — the caller supplies what it read from those.
     *
     * @param hasAuthor a note with no author pubkey has nobody to pay.
     * @param hasZapSplit a `payto` hand-off leaves with one authority and returns
     *   no receipt, so it cannot honour a note that asks to divide the zap.
     * @param canOpen whether an installed app resolves this target's URI.
     * @param recipientTargets read lazily. Parsing the recipient's kind:10133 walks
     *   its tag array and allocates, and the common case is that a gate has already
     *   failed — the setting is off, or the note carries a split — so the cheap
     *   checks must run first. `peek` is on the one-tap zap path too.
     */
    fun selectFor(
        enabled: Boolean,
        hasAuthor: Boolean,
        hasZapSplit: Boolean,
        senderTargets: List<PaymentTarget>,
        canOpen: (PaymentTarget) -> Boolean,
        recipientTargets: () -> List<PaymentTarget>,
    ): List<PaymentTarget> {
        if (!enabled || !hasAuthor || hasZapSplit || senderTargets.isEmpty()) return emptyList()
        return match(senderTargets, recipientTargets()).filter(canOpen).take(MAX_CHIPS)
    }
}
