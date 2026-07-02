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
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope

class ScreenAuthAccount(
    val account: Account,
)

@Stable
class AuthCoordinator(
    client: INostrClient,
    scope: CoroutineScope,
    val promptBus: RelayAuthPromptBus = RelayAuthPromptBus(),
) {
    private val authWithAccounts = ListWithUniqueSetCache<ScreenAuthAccount, Account> { it.account }
    private val tempAccount by lazy {
        NostrSignerSync()
    }

    @Volatile private var relayLedgers: List<RelayAuthPermissionLedger> = emptyList()

    fun subscribeLedger(ledger: RelayAuthPermissionLedger) {
        synchronized(this) { relayLedgers = relayLedgers + ledger }
    }

    fun unsubscribeLedger(ledger: RelayAuthPermissionLedger) {
        synchronized(this) { relayLedgers = relayLedgers - ledger }
    }

    val receiver =
        RelayAuthenticator(
            client,
            scope,
            signWithAllLoggedInUsers = { relayUrl, authTemplate ->
                // Reconstruct *why* this relay wants auth from what we're doing with it, so each
                // account's ledger can apply follow-based trust and (later) explain the prompt.
                // Built lazily so the no-ledgers auto-allow path below doesn't pay for it.
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
                val currentLedgers = relayLedgers
                // Ask the user (only in the ASK case) and fold every account's verdict into one
                // decision plus an optional per-relay override to remember.
                val outcome =
                    AuthDecisionResolver.resolve(currentLedgers.map { it.decide(context) }) {
                        promptBus.requestDecision(relayUrl, context.purposes)
                    }
                outcome.remember?.let { decision ->
                    currentLedgers.firstOrNull()?.setDecision(relayUrl.url, decision)
                }
                val shouldAuth = outcome.shouldAuth

                if (shouldAuth) {
                    // Remember why we granted this relay so the settings screen can explain it.
                    currentLedgers.firstOrNull()?.recordGrant(context)

                    // distinct() returns Set<Account> (the key type U of ListWithUniqueSetCache)
                    val results =
                        authWithAccounts.distinct().mapNotNull {
                            if (it.signer.isWriteable()) {
                                try {
                                    it.signer.sign(authTemplate)
                                } catch (e: Exception) {
                                    Log.e("AuthCoordinator", "Failed trying to authenticate a writeable account", e)
                                    null
                                }
                            } else {
                                null
                            }
                        }

                    // Always auth, even with random keys
                    if (results.isNotEmpty()) results else listOf(tempAccount.sign(authTemplate))
                } else {
                    emptyList()
                }
            },
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
