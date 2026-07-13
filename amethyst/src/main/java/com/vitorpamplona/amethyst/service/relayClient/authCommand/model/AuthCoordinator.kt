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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope

class ScreenAuthAccount(
    val account: Account,
)

@Stable
class AuthCoordinator(
    val client: INostrClient,
    scope: CoroutineScope,
    val promptBus: RelayAuthPromptBus = RelayAuthPromptBus(),
) {
    private val authWithAccounts = ListWithUniqueSetCache<ScreenAuthAccount, Account> { it.account }

    val receiver =
        RelayAuthenticator(
            client,
            scope,
            signWithAllLoggedInUsers = { relayUrl, authTemplate ->
                // Reconstruct *why* this relay wants auth from what the shared client is doing with
                // it. Built lazily so accounts that fail the first-party gate below don't pay for it.
                val context by
                    lazy(LazyThreadSafetyMode.NONE) {
                        RelayAuthContext(
                            relayUrl = relayUrl.url,
                            purposes =
                                RelayAuthPurposeDeriver.derive(
                                    pendingEvents = client.activeOutboxEvents(relayUrl),
                                    activeFilters = client.activeRequests(relayUrl),
                                ),
                        )
                    }

                // One socket is shared by every logged-in account, so an AUTH challenge is not tied
                // to any single one of them. We answer PER ACCOUNT: an account only reveals its
                // identity to a relay it has a first-party reason to be on (its own inbox/outbox
                // traffic, or a relay it configured) AND its own ledger verdict allows it. This is
                // what stops account B — or a throwaway key — being billed / de-anonymized on a
                // relay only account A uses (the inbox.nostr.wine over-AUTH bug): unlike the old
                // "any account ALLOWs → sign with everyone (else a random key)" path, a bystander
                // account never signs, and there is no random-key fallback.
                val signed = mutableListOf<RelayAuthEvent>()
                var askChoice: UserAuthChoice? = null

                authWithAccounts.distinctValues().forEach forEachAccount@{ screen ->
                    val account = screen.account
                    if (!account.signer.isWriteable()) return@forEachAccount
                    if (!isFirstParty(account, relayUrl)) return@forEachAccount

                    val approve =
                        when (account.relayAuthLedger.decide(context)) {
                            RelayAuthVerdict.ALLOW -> true
                            RelayAuthVerdict.DENY -> false
                            RelayAuthVerdict.ASK -> {
                                // Prompt at most once per challenge; reuse the answer for any other
                                // account that also reaches ASK on this same relay.
                                val choice = askChoice ?: promptBus.requestDecision(relayUrl, context.purposes).also { askChoice = it }
                                when (choice) {
                                    UserAuthChoice.ALLOW_ONCE -> true
                                    UserAuthChoice.ALWAYS_ALLOW -> {
                                        account.relayAuthLedger.setDecision(relayUrl.url, RelayAuthDecision.ALLOW)
                                        true
                                    }
                                    UserAuthChoice.BLOCK -> {
                                        account.relayAuthLedger.setDecision(relayUrl.url, RelayAuthDecision.DENY)
                                        false
                                    }
                                    UserAuthChoice.DISMISS -> false
                                }
                            }
                        }

                    if (approve) {
                        // Remember why we granted this relay so the settings screen can explain it.
                        account.relayAuthLedger.recordGrant(context)
                        try {
                            signed.add(account.signer.sign(authTemplate))
                        } catch (e: Exception) {
                            Log.e("AuthCoordinator", "Failed trying to authenticate a writeable account", e)
                        }
                    }
                }

                signed
            },
        )

    /**
     * True when [account] has a first-party reason to authenticate with [relayUrl] on the shared
     * client: it is publishing its own event there, a subscription there is reading its own
     * inbox/outbox (`#p` or `authors` names its pubkey), or the relay is in its own relay list.
     *
     * Merely *following* the counterparty of someone else's traffic is deliberately NOT first-party:
     * that is exactly how a bystander account got dragged into a paid inbox relay's AUTH (the shared
     * auth context carries the OTHER account's counterparties, evaluated against this account's
     * follow graph). Reads of a followed author's outbox on an auth-gated relay this account doesn't
     * use are therefore no longer auto-authed — a deliberate privacy-positive trade-off.
     */
    private fun isFirstParty(
        account: Account,
        relayUrl: NormalizedRelayUrl,
    ): Boolean =
        RelayAuthFirstParty.hasReason(
            me = account.pubKey,
            relayUrl = relayUrl,
            pendingEvents = client.activeOutboxEvents(relayUrl),
            myRelays = account.trustedRelays.flow.value,
        )

    fun destroy() {
        receiver.destroy()
    }

    // This is called by main. Keep it really fast.
    fun subscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d("AuthCoordinator") { "Watch $account" }
        }

        authWithAccounts.add(account)
    }

    // This is called by main. Keep it really fast.
    fun unsubscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d("AuthCoordinator") { "Unwatch $account" }
        }

        authWithAccounts.remove(account)
    }
}
