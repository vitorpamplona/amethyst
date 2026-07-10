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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcordSubscriptionPlannerTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun controlAndChannelSubsMatchDerivedAddresses() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )

            // Control-plane sub address must equal the derived control plane pk.
            val controlSubs = ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry))
            assertEquals(1, controlSubs.size)
            assertEquals(community.controlPlane.publicKeyHex, controlSubs[0].pubKeyHex)
            assertTrue(controlSubs[0].channelId == null)

            // Channel-plane subs cover the folded #general channel.
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.ownerPubKey)
            val channelSubs = ConcordSubscriptionPlanner.channelPlaneSubs(entry, state)
            val general = channelSubs.firstOrNull { it.channelId?.channelId == community.generalChannelIdHex }
            assertTrue(general != null)
            assertEquals(
                ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch).publicKeyHex,
                general.pubKeyHex,
            )

            // filtersByRelay collapses to one kind-1059 author filter per relay.
            val filters = ConcordSubscriptionPlanner.filtersByRelay(controlSubs + channelSubs)
            assertEquals(1, filters.size) // single relay
            val filter = filters.values.first().first()
            assertEquals(listOf(1059), filter.kinds)
            assertTrue(filter.authors!!.contains(community.controlPlane.publicKeyHex))
            assertTrue(filter.authors!!.contains(general.pubKeyHex))
        }
}
