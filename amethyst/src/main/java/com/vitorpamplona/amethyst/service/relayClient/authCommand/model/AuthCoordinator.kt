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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
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
                // Concord plane traffic is gated behind NIP-42 as the derived *stream key*, not the
                // user: a relay serves a plane's kind-1059 wraps only to a connection authenticated
                // as that stream key. These AUTHs expose no user identity (ephemeral derived keys)
                // and are signed locally, so we always attach them — independent of the user-auth
                // policy below — or Concord channels/messages never load. No-op for non-Concord relays.
                val streamAuths = signConcordStreamAuths(relayUrl, authTemplate)

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

                val userAuths =
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

                        // Always auth, even with random keys (unless we're only here for stream auth).
                        if (results.isNotEmpty()) {
                            results
                        } else if (streamAuths.isEmpty()) {
                            listOf(tempAccount.sign(authTemplate))
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                streamAuths + userAuths
            },
        )

    /**
     * Signs one kind-22242 AUTH per Concord plane stream key hosted on [relayUrl], across every
     * watched account. Signed locally from the derived stream secret (a raw [KeyPair] via
     * [NostrSignerSync]) — never the account signer, and never surfacing the user's identity.
     */
    private suspend fun signConcordStreamAuths(
        relayUrl: NormalizedRelayUrl,
        authTemplate: EventTemplate<RelayAuthEvent>,
    ): List<RelayAuthEvent> {
        val secrets = authWithAccounts.distinct().flatMap { it.concordSessions.streamAuthSecretsFor(relayUrl) }
        if (secrets.isEmpty()) return emptyList()
        return secrets.mapNotNull { secret ->
            try {
                NostrSignerSync(KeyPair(privKey = secret)).sign(authTemplate)
            } catch (e: Exception) {
                Log.e("AuthCoordinator", "Failed to sign a Concord stream-key AUTH", e)
                null
            }
        }
    }

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
