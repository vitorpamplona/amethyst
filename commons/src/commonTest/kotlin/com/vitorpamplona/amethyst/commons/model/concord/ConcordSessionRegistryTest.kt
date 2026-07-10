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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordSessionRegistryTest {
    private val owner = NostrSignerInternal(KeyPair())

    private fun entryFor(
        community: NewConcordCommunity,
        name: String,
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = community.communityRoot.toHexKey(),
        rootEpoch = community.rootEpoch,
        relays = listOf("wss://r.example"),
        name = name,
    )

    @Test
    fun syncsSessionsRoutesWrapsAndDropsDepartedCommunities() =
        runTest {
            val alpha = ConcordCommunityFactory.create(owner, "Alpha", createdAt = 1L, relays = listOf("wss://r.example"))
            val beta = ConcordCommunityFactory.create(owner, "Beta", createdAt = 1L, relays = listOf("wss://r.example"))

            val registry = ConcordSessionRegistry()

            // First sync creates a session for each joined community.
            val created = registry.sync(listOf(entryFor(alpha, "Alpha"), entryFor(beta, "Beta")), owner.pubKey)
            assertEquals(setOf(alpha.communityIdHex, beta.communityIdHex), created)
            assertNotNull(registry.sessionFor(alpha.communityIdHex))
            assertNotNull(registry.sessionFor(beta.communityIdHex))

            // Both control-plane addresses are in the subscribe set from the entries alone.
            assertTrue(registry.subscribeAddresses().contains(alpha.controlPlane.publicKeyHex))
            assertTrue(registry.subscribeAddresses().contains(beta.controlPlane.publicKeyHex))

            // A genesis control wrap routes to Alpha's session and folds it.
            alpha.genesisWraps.forEach { assertTrue(registry.ingest(it)) }
            val alphaState = registry.sessionFor(alpha.communityIdHex)!!.state.value
            assertEquals("Alpha", alphaState?.metadata?.name)

            // After the fold, Alpha's #general channel plane joins the subscribe set.
            val general = ConcordActions.publicChannel(alpha.communityRoot, alpha.generalChannelId, alpha.rootEpoch)
            assertTrue(registry.subscribeAddresses().contains(general.publicKeyHex))

            // A channel message routes to Alpha's #general flow, not Beta.
            val msg = ConcordActions.buildChannelMessage(owner, general, alpha.generalChannelIdHex, alpha.rootEpoch, "gm", 2L)
            assertTrue(registry.ingest(msg))
            assertEquals(
                1,
                registry
                    .sessionFor(alpha.communityIdHex)!!
                    .messagesFlow(alpha.generalChannelIdHex)
                    .value.size,
            )

            // A re-sync that keeps Alpha but drops Beta preserves Alpha's folded state and removes Beta.
            val createdAgain = registry.sync(listOf(entryFor(alpha, "Alpha")), owner.pubKey)
            assertTrue(createdAgain.isEmpty())
            assertNotNull(registry.sessionFor(alpha.communityIdHex))
            assertNull(registry.sessionFor(beta.communityIdHex))
            assertEquals(
                "Alpha",
                registry
                    .sessionFor(alpha.communityIdHex)!!
                    .state.value
                    ?.metadata
                    ?.name,
            )

            // A wrap from an unknown community is routed nowhere.
            val gamma = ConcordCommunityFactory.create(owner, "Gamma", createdAt = 1L, relays = listOf("wss://r.example"))
            assertFalse(registry.ingest(gamma.genesisWraps.first()))
        }
}
