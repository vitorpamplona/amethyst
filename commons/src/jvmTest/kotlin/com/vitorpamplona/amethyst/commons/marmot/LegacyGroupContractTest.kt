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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyGroupContractTest {
    /**
     * What a legacy MIP-01 group can and cannot still do.
     *
     * Groups created by builds before the current profile existed are still on
     * disk, and they can NEVER become current-profile groups: the account
     * identity proof lives in a member's own LeafNode and covers that leaf's
     * signature key, so it cannot be added to leaves that already exist. See
     * `AccountIdentityProofV2` — "There is no fallback and no in-place
     * migration". The only route from a legacy room to a current-profile one is
     * to create a new group and re-invite.
     *
     * That makes the legacy contract worth pinning rather than discovering by
     * hand: messaging keeps working, and the operations whose state has no
     * legacy carrier refuse with a message that says why instead of failing
     * somewhere inside the commit.
     */
    @Test
    fun aLegacyGroupStillTalksButCannotDisbandOrCarryAnAvatar() =
        runBlocking<Unit> {
            val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
            val signer = NostrSignerInternal(KeyPair())
            val manager =
                MarmotManager(
                    signer,
                    ProbeStateStore(),
                    publisher = MarmotPublisher { _, _ -> true },
                )
            val gid = "b".repeat(64)
            manager.createGroup(
                gid,
                MarmotGroupData(nostrGroupId = gid, adminPubkeys = listOf(signer.pubKey), relays = listOf(relay.url)),
            )

            assertFalse(
                manager.groupView(gid)!!.isCurrentProfile,
                "createGroup builds the legacy MIP-01 shape; createCurrentProfileGroup is the other one",
            )

            // Messaging is unaffected. A legacy room is still a usable room.
            manager.buildTextMessage(gid, "hello from a legacy room")

            // Lifecycle (0x800c) and the URL avatar (0x8007) are GroupContext
            // components a legacy group has nowhere to put, so both refuse up
            // front and name the reason.
            val disband = assertFailsWith<IllegalStateException> { manager.disbandGroup(gid, listOf(relay)) }
            assertTrue(
                disband.message!!.contains("legacy MIP-01 group"),
                "a refusal a tester will read: ${disband.message}",
            )

            val avatar =
                assertFailsWith<IllegalStateException> {
                    manager.setGroupAvatarUrl(gid, GroupAvatarUrlV1("https://x.invalid/a.png"), listOf(relay))
                }
            assertTrue(
                avatar.message!!.contains("legacy MIP-01 group"),
                "a refusal a tester will read: ${avatar.message}",
            )
        }

    private class ProbeStateStore : com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore {
        private val states = mutableMapOf<String, ByteArray>()
        private val retained = mutableMapOf<String, List<ByteArray>>()

        override suspend fun save(
            nostrGroupId: String,
            state: ByteArray,
        ) {
            states[nostrGroupId] = state
        }

        override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

        override suspend fun delete(nostrGroupId: String) {
            states.remove(nostrGroupId)
        }

        override suspend fun listGroups(): List<String> = states.keys.toList()

        override suspend fun saveRetainedEpochs(
            nostrGroupId: String,
            retainedSecrets: List<ByteArray>,
        ) {
            retained[nostrGroupId] = retainedSecrets
        }

        override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
    }
}
