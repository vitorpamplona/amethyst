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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies that [DesktopLocalCache] routes a NIP-17 DM relay list (kind 10050)
 * into the cache so the [com.vitorpamplona.amethyst.commons.model.User] model
 * reflects it. The desktop NIP-17 send path, the DM inbox resolver's local
 * lookup, and tier-1 AUTH all read `User.dmInboxRelaysStrict()`; before the
 * kind:10050 routing + `UserContext` wiring, `route` dropped kind 10050 and the
 * User model was backed by a map nothing populated, so every recipient looked
 * "unreachable via NIP-17" until an indexer fan-out ran.
 */
class DesktopDmRelayListConsumeTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    private val dmInbox =
        listOf(
            NormalizedRelayUrl("wss://inbox1.example/"),
            NormalizedRelayUrl("wss://inbox2.example/"),
        )

    private fun signedDmRelayList(
        signer: NostrSignerSync,
        relays: List<NormalizedRelayUrl> = dmInbox,
        createdAt: Long = 1_700_000_000,
    ): ChatMessageRelayListEvent = ChatMessageRelayListEvent.create(relays, signer, createdAt)

    @Test
    fun `consume routes kind 10050 into the User model`() {
        val cache = DesktopLocalCache()
        val signer = NostrSignerSync(KeyPair())
        val event = signedDmRelayList(signer)

        cache.consume(event, relayUrl)

        val user = cache.getOrCreateUser(signer.pubKey)
        assertEquals(dmInbox, user.dmInboxRelaysStrict())
    }

    @Test
    fun `an unknown recipient has no strict DM relays`() {
        val cache = DesktopLocalCache()
        val stranger = NostrSignerSync(KeyPair()).pubKey

        assertNull(cache.getOrCreateUser(stranger).dmInboxRelaysStrict())
    }

    @Test
    fun `a newer kind 10050 replaces an older one`() {
        val cache = DesktopLocalCache()
        val signer = NostrSignerSync(KeyPair())

        cache.consume(signedDmRelayList(signer, dmInbox, createdAt = 1_700_000_000), relayUrl)

        val newerRelays = listOf(NormalizedRelayUrl("wss://moved.example/"))
        cache.consume(signedDmRelayList(signer, newerRelays, createdAt = 1_700_000_100), relayUrl)

        assertEquals(newerRelays, cache.getOrCreateUser(signer.pubKey).dmInboxRelaysStrict())
    }

    @Test
    fun `an older kind 10050 does not overwrite a newer one`() {
        val cache = DesktopLocalCache()
        val signer = NostrSignerSync(KeyPair())

        cache.consume(signedDmRelayList(signer, dmInbox, createdAt = 1_700_000_100), relayUrl)

        val staleRelays = listOf(NormalizedRelayUrl("wss://stale.example/"))
        cache.consume(signedDmRelayList(signer, staleRelays, createdAt = 1_700_000_000), relayUrl)

        assertEquals(dmInbox, cache.getOrCreateUser(signer.pubKey).dmInboxRelaysStrict())
    }
}
