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

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordDissolutionTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())
    private val communityX = ConcordKeyDerivation.communityId(owner.pubKey.hexToByteArray(), ByteArray(32) { 1 }).toHexKey()
    private val communityY = ConcordKeyDerivation.communityId(owner.pubKey.hexToByteArray(), ByteArray(32) { 2 }).toHexKey()

    @Test
    fun tombstoneWireShapeIsChainlessAndBound() {
        val rumor = ConcordDissolution.rumor(owner.pubKey, communityX, createdAt = 1725000000)
        assertEquals(ControlEditionEvent.KIND, rumor.kind)
        assertEquals("", rumor.content)
        assertEquals(listOf(listOf("vsk", "10"), listOf("eid", communityX)), rumor.tags.map { it.toList() })
    }

    @Test
    fun addressDerivesFromTheCommunityIdAlone() {
        // A.6: `concord/dissolved`, ikm = community_id, id = 0…0, no epoch.
        val expected = ConcordKeyDerivation.groupKey("concord/dissolved", communityX.hexToByteArray(), ByteArray(32))
        assertEquals(expected.publicKeyHex, ConcordDissolution.planeKey(communityX).publicKeyHex)
    }

    @Test
    fun ownerTombstoneDissolvesItsOwnCommunity() =
        runTest {
            val wrap = ConcordDissolution.build(owner, communityX)
            assertEquals(ConcordDissolution.planeKey(communityX).publicKeyHex, wrap.pubKey)
            assertTrue(ConcordDissolution.isDissolved(listOf(wrap), communityX, owner.pubKey))
        }

    @Test
    fun nonOwnerTombstoneIsNoise() =
        runTest {
            val wrap = ConcordDissolution.build(stranger, communityX)
            assertFalse(ConcordDissolution.isDissolved(listOf(wrap), communityX, owner.pubKey))
        }

    @Test
    fun aTombstoneForXReWrappedAtYDoesNotKillY() =
        runTest {
            // The replay CORD-02 §9 closes: lift X's genuine owner seal and re-wrap it verbatim at Y's
            // (public) dissolved address. The seal still verifies, but its eid names X.
            val planeX = ConcordDissolution.planeKey(communityX)
            val sealForX = ConcordStreamEnvelope.seal(ConcordDissolution.rumor(owner.pubKey, communityX), planeX, owner, encrypted = false)
            val reWrappedAtY = ConcordStreamEnvelope.wrapSeal(sealForX, ConcordDissolution.planeKey(communityY))
            assertFalse(ConcordDissolution.isDissolved(listOf(reWrappedAtY), communityY, owner.pubKey))
        }

    @Test
    fun theLegacyAllZeroEidIsRefused() =
        runTest {
            val plane = ConcordDissolution.planeKey(communityX)
            val legacy =
                RumorAssembler.assembleRumor(
                    owner.pubKey,
                    eventTemplate<ControlEditionEvent>(ControlEditionEvent.KIND, "") {
                        add(arrayOf("vsk", ControlEntityKind.DISSOLVED.wire))
                        add(arrayOf("eid", "00".repeat(32)))
                    },
                )
            val wrap = ConcordStreamEnvelope.wrap(legacy, plane, owner, encrypted = false)
            assertFalse(ConcordDissolution.isDissolved(listOf(wrap), communityX, owner.pubKey))
        }

    @Test
    fun anEncryptedSealIsRefused() =
        runTest {
            val plane = ConcordDissolution.planeKey(communityX)
            val wrap = ConcordStreamEnvelope.wrap(ConcordDissolution.rumor(owner.pubKey, communityX), plane, owner, encrypted = true)
            assertFalse(ConcordDissolution.isDissolved(listOf(wrap), communityX, owner.pubKey))
        }
}
