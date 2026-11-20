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

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleAccountRelayAuthTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val relay = NormalizedRelayUrl("wss://search.brainstorm.world/")
    private val template = RelayAuthEvent.build(relay, "challenge")

    // What the trust sync's REQ derives to: reading a provider's cards (an `authors` filter).
    private val readingProvider =
        RelayAuthContext(relay.url, listOf(AuthPurpose(AuthPurposeKind.OTHER)))

    private fun ledger(
        policy: RelayAuthPolicy,
        myRelays: Set<String> = emptySet(),
        store: InMemoryRelayAuthPermissionStore = InMemoryRelayAuthPermissionStore(),
    ) = RelayAuthPermissionLedger(
        store = store,
        globalPolicy = { policy },
        sessionGrants = RelayAuthSessionGrants(),
        customToggles = { RelayAuthCustomToggles(myRelaysAndVenues = true) },
        isInMyRelayList = { it in myRelays },
    )

    @Test
    fun alwaysSignsAsTheAccount() =
        runTest {
            val signed = signAuthAsOneAccount(signer, ledger(RelayAuthPolicy.ALWAYS), readingProvider, isFirstParty = true, template)
            assertEquals(listOf(signer.pubKey), signed.map { it.pubKey })
            assertEquals("challenge", signed.single().challenge())
        }

    @Test
    fun neverSignsNothing() =
        runTest {
            assertTrue(signAuthAsOneAccount(signer, ledger(RelayAuthPolicy.NEVER), readingProvider, isFirstParty = true, template).isEmpty())
        }

    @Test
    fun aRelayTheUserBlockedForAuthStaysUnsigned() =
        runTest {
            val store = InMemoryRelayAuthPermissionStore()
            store.storeDecision(relay.url, RelayAuthDecision.DENY)
            assertTrue(signAuthAsOneAccount(signer, ledger(RelayAuthPolicy.ALWAYS, store = store), readingProvider, isFirstParty = true, template).isEmpty())
        }

    @Test
    fun customSignsOnTheUsersOwnRelay() =
        runTest {
            // The provider relay named in the user's kind 10040 is part of their trusted relays.
            val signed = signAuthAsOneAccount(signer, ledger(RelayAuthPolicy.CUSTOM, myRelays = setOf(relay.url)), readingProvider, isFirstParty = true, template)
            assertEquals(listOf(signer.pubKey), signed.map { it.pubKey })
        }

    @Test
    fun customNeverPromptsFromTheBackground() =
        runTest {
            // A relay outside the user's lists would ASK on the shared pool; a side client has no
            // one to ask, so it goes unsigned instead of hanging on a dialog.
            assertTrue(signAuthAsOneAccount(signer, ledger(RelayAuthPolicy.CUSTOM), readingProvider, isFirstParty = false, template).isEmpty())
        }

    @Test
    fun aReadOnlyAccountCannotSign() =
        runTest {
            val readOnly = NostrSignerInternal(KeyPair(pubKey = signer.keyPair.pubKey))
            assertTrue(signAuthAsOneAccount(readOnly, ledger(RelayAuthPolicy.ALWAYS), readingProvider, isFirstParty = true, template).isEmpty())
        }
}
