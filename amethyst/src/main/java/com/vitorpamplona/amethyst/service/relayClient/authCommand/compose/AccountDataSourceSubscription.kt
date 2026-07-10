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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull

/** The owner pubkey of an addressable venue (`kind:pubkey:dTag`), or null for a bare channel id. */
private fun venueOwnerPubkey(venueId: String): String? = Address.parse(venueId)?.pubKeyHex

@Composable
fun RelayAuthSubscription(accountViewModel: AccountViewModel) = RelayAuthSubscription(accountViewModel, Amethyst.instance.authCoordinator)

@Composable
fun RelayAuthSubscription(
    accountViewModel: AccountViewModel,
    dataSource: AuthCoordinator,
) {
    val account = accountViewModel.account

    val state =
        remember(accountViewModel) {
            ScreenAuthAccount(account)
        }

    val ledger =
        remember(accountViewModel) {
            RelayAuthPermissionLedger(
                store = Amethyst.instance.relayAuthPermissionStore,
                globalPolicy = { account.settings.defaultRelayAuthPolicy.value },
                customToggles = {
                    RelayAuthCustomToggles(
                        myRelaysAndVenues = account.settings.relayAuthTrustMyRelaysAndVenues.value,
                        readFollows = account.settings.relayAuthTrustReadFollows.value,
                        messageFollows = account.settings.relayAuthTrustMessageFollows.value,
                        messageStrangers = account.settings.relayAuthTrustMessageStrangers.value,
                    )
                },
                isInMyRelayList = { relayUrl ->
                    val normalized = relayUrl.normalizeRelayUrlOrNull() ?: return@RelayAuthPermissionLedger false
                    normalized in account.trustedRelays.flow.value
                },
                isBlocked = { relayUrl ->
                    val normalized = relayUrl.normalizeRelayUrlOrNull() ?: return@RelayAuthPermissionLedger false
                    normalized in account.blockedRelayList.flow.value
                },
                // Any follow list (kind 3, follow sets, etc.) counts as trusting the counterparty
                // enough to reveal our identity to a relay that serves them.
                isFollowed = { pubkey -> pubkey in account.allFollows.flow.value.authors },
                // A venue (public chat / community / live stream) is trusted if we've joined it, or
                // its owner — the pubkey in a `kind:pubkey:dTag` address — is someone we follow.
                isTrustedVenue = { venueId ->
                    venueId in account.publicChatList.flowSet.value ||
                        venueId in account.communityList.flowSet.value ||
                        venueOwnerPubkey(venueId)?.let { it in account.allFollows.flow.value.authors } == true
                },
            )
        }

    DisposableEffect(state, ledger) {
        dataSource.subscribe(state)
        dataSource.subscribeLedger(ledger)
        onDispose {
            dataSource.unsubscribe(state)
            dataSource.unsubscribeLedger(ledger)
        }
    }
}
