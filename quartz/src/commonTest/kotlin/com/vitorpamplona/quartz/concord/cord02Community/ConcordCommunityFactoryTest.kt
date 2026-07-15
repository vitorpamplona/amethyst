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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcordCommunityFactoryTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun createsSelfCertifyingCommunityWithGenesisEditions() =
        runTest {
            val community =
                ConcordCommunityFactory.create(
                    ownerSigner = owner,
                    name = "Nostrichs",
                    createdAt = 1_700_000_000L,
                    description = "a cozy place",
                    relays = listOf("wss://relay.example"),
                )

            // community_id is the self-certifying commitment to owner + salt
            assertContentEquals(
                ConcordKeyDerivation.communityId(owner.pubKey.hexToByteArray(), community.ownerSalt),
                community.communityId,
            )

            // Two genesis wraps, both authored by the Control Plane address.
            assertEquals(2, community.genesisWraps.size)
            community.genesisWraps.forEach {
                assertEquals(ConcordStreamEnvelope.KIND_WRAP, it.kind)
                assertEquals(community.controlPlane.publicKeyHex, it.pubKey)
            }

            // Genesis wraps open with plaintext (20014) seals, authored by the owner.
            val opened = community.genesisWraps.map { ConcordStreamEnvelope.open(it, community.controlPlane) }
            opened.forEach {
                assertEquals(ConcordStreamEnvelope.KIND_SEAL_PLAINTEXT, it.sealKind)
                assertEquals(owner.pubKey, it.author)
            }
        }

    @Test
    fun genesisFoldsToLiveCommunityStateWithGeneralChannelAndOwnerAuthority() =
        runTest {
            val community =
                ConcordCommunityFactory.create(owner, name = "Gamers", createdAt = 1L, relays = listOf("wss://r.example"))

            val state = ConcordCommunityState.fold(community.genesisEditions, community.ownerPubKey)

            assertEquals("Gamers", state.metadata?.name)
            assertEquals(listOf("wss://r.example"), state.metadata?.relays)

            val general = state.channels[community.generalChannelIdHex]
            assertNotNull(general)
            assertEquals(ConcordCommunityFactory.GENERAL_CHANNEL_NAME, general.definition.name)
            assertFalse(general.definition.private)

            // The owner is supreme from genesis; no channels are private, none deleted.
            assertTrue(state.authority.isOwner(owner.pubKey))
            assertEquals(0L, state.authority.rank(owner.pubKey))
            assertFalse(state.dissolved)
        }

    @Test
    fun differentCommunitiesFromSameOwnerHaveDistinctIds() =
        runTest {
            val a = ConcordCommunityFactory.create(owner, "A", 1L)
            val b = ConcordCommunityFactory.create(owner, "B", 1L)
            // distinct salts ⇒ distinct ids (one owner, many communities)
            assertFalse(a.communityIdHex == b.communityIdHex)
            assertFalse(a.communityRoot.toHexKey() == b.communityRoot.toHexKey())
        }
}
