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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOverlayNetwork

/**
 * Which relays fall into each Tor-routing category, as one value.
 *
 * Grouped deliberately rather than passed as four loose sets. Consumers that must react when the
 * categories change — `RelayProxyClientConnector` re-dials the relays whose transport flipped —
 * previously compared the sets field by field, so adding a fifth category meant remembering to add
 * a fifth `||`. Forgetting it fails silently: relays keep a socket on a transport the policy has
 * already moved them off. Structural equality on one object makes that impossible to forget.
 */
data class RelayClassification(
    /** Relays the user actually put in one of their own lists. */
    val trusted: Set<NormalizedRelayUrl> = emptySet(),
    /** NIP-17 DM relays. */
    val dm: Set<NormalizedRelayUrl> = emptySet(),
    /** NIP-47 wallet and CLINK debit relays, including ad-hoc registrations. */
    val moneyOp: Set<NormalizedRelayUrl> = emptySet(),
    /**
     * Relays the app is guessing on the user's behalf while their own lists are unknown. Empties
     * itself as soon as any of their events arrive; see `AssumedRelayListsState`.
     */
    val assumed: Set<NormalizedRelayUrl> = emptySet(),
)

class TorRelayEvaluation(
    val torSettings: TorRelaySettings,
    val classification: RelayClassification = RelayClassification(),
) {
    fun useTor(relay: NormalizedRelayUrl): Boolean =
        if (torSettings.torType == TorType.OFF) {
            false
        } else {
            if (relay.isLocalHost()) {
                false
            } else if (relay.isOverlayNetwork()) {
                // An overlay-mesh relay (0200::/7, e.g. Yggdrasil) is reachable only through the
                // local mesh interface: Tor cannot route the range at all, so proxying it would
                // guarantee failure rather than privacy. The overlay already encrypts end to end.
                false
            } else if (relay.isOnion()) {
                // .onion is only reachable over Tor regardless of any other classification.
                torSettings.onionRelaysViaTor
            } else if (relay in classification.moneyOp) {
                // Relays used for money operations (NIP-47 wallets, CLINK offer/debit services)
                // follow the dedicated money-operations preference, taking precedence over the
                // generic DM/trusted/new classification so a payment never silently inherits a
                // different Tor policy than the one the user set for money.
                torSettings.moneyOperationsViaTor
            } else if (relay in classification.dm) {
                torSettings.dmRelaysViaTor
            } else if (relay in classification.trusted) {
                torSettings.trustedRelaysViaTor
            } else if (relay in classification.assumed) {
                // Last resort before treating it as a stranger. Sits below every other
                // classification on purpose: .onion, money-operation and DM relays keep their own
                // policy even while we are guessing, because this branch can only ever capture
                // relays that would otherwise have fallen through to `newRelaysViaTor`.
                torSettings.trustedRelaysViaTor
            } else {
                torSettings.newRelaysViaTor
            }
        }
}
