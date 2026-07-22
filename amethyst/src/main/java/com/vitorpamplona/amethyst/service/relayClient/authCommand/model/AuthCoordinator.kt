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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

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
            signWithAllLoggedInUsers = { relayUrl, authTemplate, interactive ->
                // Concord plane traffic is gated behind NIP-42 as the derived *stream key*, not the
                // user: a relay serves a plane's kind-1059 wraps only to a connection authenticated
                // as that stream key. These AUTHs expose no user identity (ephemeral derived keys)
                // and are signed locally, so we always attach them — independent of the per-account
                // policy below — or Concord channels/messages never load. No-op for non-Concord relays.
                val streamAuths = signConcordStreamAuths(relayUrl, authTemplate)

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
                                // account that also reaches ASK on this same relay. But never block the
                                // derived stream-key AUTH behind that dialog: on a relay that hosts our
                                // Concord planes we DISMISS the user-auth ASK (skip account auth) so the
                                // stream AUTHs return immediately instead of waiting on a prompt.
                                //
                                // A non-[interactive] pass is an automatic re-auth off an `auth-required:`
                                // CLOSED (e.g. a Concord channel-plane REQ refused because the connection
                                // AUTHed before the control plane folded in its channel stream keys). It
                                // must never raise a fresh dialog: DISMISS the account ASK and let only the
                                // already-approved identities (ledger-ALLOW accounts + stream keys) re-send.
                                val choice =
                                    askChoice ?: (
                                        if (streamAuths.isNotEmpty() || !interactive) {
                                            UserAuthChoice.DISMISS
                                        } else {
                                            promptBus.requestDecision(relayUrl, context.purposes)
                                        }
                                    ).also { askChoice = it }
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
                            signed.add(account.signer.sign(buzzAugmented(authTemplate, account.pubKey, relayUrl)))
                        } catch (e: Exception) {
                            Log.e("AuthCoordinator", "Failed trying to authenticate a writeable account", e)
                        }
                    }
                }

                signed + streamAuths
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
                // Cache the signer by secret so we don't re-derive the secp256k1 keypair for every
                // plane on every relay challenge/reconnect.
                streamSigners.getOrPut(secret.toHexKey()) { NostrSignerSync(KeyPair(privKey = secret)) }.sign(authTemplate)
            } catch (e: Exception) {
                Log.e("AuthCoordinator", "Failed to sign a Concord stream-key AUTH", e)
                null
            }
        }
    }

    /**
     * If [relayUrl] speaks the Buzz dialect and this device holds a NIP-OA attestation
     * authorizing [accountPubKey], returns [template] with the owner-signed `auth` tag
     * appended — so the relay grants virtual membership to an un-enrolled agent key while
     * its owner stays a member. Otherwise returns [template] unchanged.
     *
     * Applied ONLY to an account's own AUTH (the caller passes the account pubkey), never
     * to the Concord stream-key AUTHs that share the same [template] object, and it is a
     * no-op on non-Buzz relays and for accounts with no held attestation — so it can never
     * add an `auth` tag where one isn't wanted.
     */
    private fun buzzAugmented(
        template: EventTemplate<RelayAuthEvent>,
        accountPubKey: HexKey,
        relayUrl: NormalizedRelayUrl,
    ): EventTemplate<RelayAuthEvent> {
        if (!BuzzRelayDialect.isBuzz(relayUrl)) return template
        val authTag = BuzzHeldAttestations.authTagFor(accountPubKey) ?: return template
        return EventTemplate(template.createdAt, template.kind, template.tags + arrayOf(authTag), template.content)
    }

    // stream secret (hex) -> its local signer. Bounded by joined communities × channels.
    private val streamSigners = ConcurrentHashMap<HexKey, NostrSignerSync>()

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
