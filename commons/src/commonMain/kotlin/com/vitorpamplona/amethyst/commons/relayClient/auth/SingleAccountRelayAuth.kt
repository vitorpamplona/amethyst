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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * NIP-42 for a side client: a dedicated [INostrClient] that one account opens for its own work,
 * apart from the shared pool that [AuthCoordinator] answers for every logged-in account.
 *
 * Such a client carries one account's traffic, so it signs as that account and nobody else.
 * Signing as every logged-in account would tell the relay which accounts share this device, and
 * a relay that ranks by the signed-in key (search.brainstorm.world) would answer for whichever
 * key it picked. The account's own NIP-42 policy still decides, as on the shared pool. A side
 * client runs in the background, so it never raises a prompt: a challenge the policy would ask
 * about goes unanswered, the same as a [RelayAuthVerdict.DENY].
 *
 * Constructing the returned [RelayAuthenticator] registers it on [client]. Call
 * [RelayAuthenticator.destroy] and cancel [scope] when the client closes.
 */
fun Account.authenticateSideClient(
    client: INostrClient,
    scope: CoroutineScope,
): RelayAuthenticator =
    RelayAuthenticator(client, scope) { relayUrl, template, _ ->
        val pendingEvents = client.activeOutboxEvents(relayUrl)
        val context =
            RelayAuthContext(
                relayUrl = relayUrl.url,
                purposes = RelayAuthPurposeDeriver.derive(pendingEvents, client.activeRequests(relayUrl)),
            )
        val firstParty =
            RelayAuthFirstParty.hasReason(
                me = pubKey,
                relayUrl = relayUrl,
                pendingEvents = pendingEvents,
                myRelays = trustedRelays.flow.value,
                myVenueRelays = venueHostRelays(),
            )
        signAuthAsOneAccount(signer, relayAuthLedger, context, firstParty, template)
    }

/**
 * The AUTH [signer] sends for [context], or none: only when [ledger] answers
 * [RelayAuthVerdict.ALLOW] without a prompt, and only from a signer that can sign.
 */
suspend fun signAuthAsOneAccount(
    signer: NostrSigner,
    ledger: RelayAuthPermissionLedger,
    context: RelayAuthContext,
    isFirstParty: Boolean,
    template: EventTemplate<RelayAuthEvent>,
): List<RelayAuthEvent> {
    if (!signer.isWriteable()) return emptyList()
    if (ledger.decide(context, isFirstParty) != RelayAuthVerdict.ALLOW) return emptyList()
    ledger.recordGrant(context)
    return try {
        listOf(signer.sign(template))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("SingleAccountRelayAuth", "Could not sign the AUTH for ${context.relayUrl}", e)
        emptyList()
    }
}
