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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.appComponents.CurrentProfileGroupFactory
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamQuicPolicyV1
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A joiner has to learn the group's `nostr_group_id` from the Welcome itself —
 * it is the `h` tag every kind-445 event in the group carries, so without it
 * the joiner cannot even subscribe.
 *
 * Reading it only from the legacy `0xF2EE` extension made us unable to join
 * ANY group a current-profile client created: MDK's welcome arrived, decrypted,
 * and was then thrown away with "GroupContext is missing the NostrGroupData
 * extension". The routing id had been there the whole time, in the
 * `marmot.transport.nostr.routing.v1` component.
 */
class CurrentProfileWelcomeTest {
    private fun signer(seed: Byte) = NostrSignerInternal(KeyPair(ByteArray(32) { seed }))

    private val nostrGroupId = ByteArray(32) { 0x4d }

    private fun aGroup(agentTextStream: AgentTextStreamQuicPolicyV1? = null) =
        runBlocking {
            CurrentProfileGroupFactory.createGroup(
                signer = signer(0x11),
                nostrGroupId = nostrGroupId,
                relays = listOf("wss://relay.example"),
                profile = GroupProfileV1("Routing", ""),
                agentTextStream = agentTextStream,
            )
        }

    @Test
    fun theRoutingIdComesFromTheCurrentProfileComponent() {
        val group = aGroup()
        assertEquals(nostrGroupId.toHexKey(), group.currentNostrGroupId())
        // The legacy extension is genuinely absent — this is not a group that
        // happens to carry both.
        assertNull(group.currentMarmotData())
    }

    @Test
    fun aJoinerReadsTheSameRoutingIdOutOfTheWelcome() =
        runBlocking<Unit> {
            val group = aGroup()
            val inviteeSigner = signer(0x33)
            val invitee = CurrentProfileGroupFactory.createKeyPackage(inviteeSigner)

            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val commit = group.commit()
            val welcome = assertNotNull(commit.welcomeBytes, "adding a member must produce a Welcome")

            val joined = MlsGroup.processWelcome(welcome, invitee)
            assertEquals(nostrGroupId.toHexKey(), joined.currentNostrGroupId())
            assertEquals(group.currentGroupState().profile?.name, joined.currentGroupState().profile?.name)
        }

    /**
     * The `0x8006` policy names MLS leaf capabilities every member must
     * advertise. Our current-profile leaf advertises `receive` only, so a
     * group that also requires `send` must be refused at join rather than
     * joined into a state where every commit we make is rejected by peers.
     */
    @Test
    fun aJoinerRefusesAGroupWhoseStreamRolesItCannotFill() =
        runBlocking<Unit> {
            val group =
                aGroup(
                    AgentTextStreamQuicPolicyV1(
                        requiredMemberRoles = AgentTextStreamRolesFixture.RECEIVE_AND_SEND,
                        allowedMemberRoles = AgentTextStreamRolesFixture.RECEIVE_AND_SEND,
                        maxPlaintextFrameLen = 4096,
                        replayTtlSecs = 0,
                        paddingBucketBytes = 0,
                    ),
                )
            val invitee = CurrentProfileGroupFactory.createKeyPackage(signer(0x44))
            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val welcome = assertNotNull(group.commit().welcomeBytes)

            val failure = assertFailsWith<IllegalArgumentException> { MlsGroup.processWelcome(welcome, invitee) }
            assertTrue(
                failure.message.orEmpty().contains("agent text stream roles"),
                "expected a role-capability refusal, got: ${failure.message}",
            )
        }

    @Test
    fun aJoinerAcceptsAGroupRequiringOnlyTheReceiveRole() =
        runBlocking<Unit> {
            val group = aGroup(AgentTextStreamQuicPolicyV1.userToAgentDefault())
            val invitee = CurrentProfileGroupFactory.createKeyPackage(signer(0x55))
            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val welcome = assertNotNull(group.commit().welcomeBytes)

            val joined = MlsGroup.processWelcome(welcome, invitee)
            assertEquals(
                AgentTextStreamQuicPolicyV1.userToAgentDefault(),
                joined.currentGroupState().agentTextStream,
            )
            // Both sides derive the same per-epoch stream secret.
            assertEquals(group.agentTextStreamSecret().toHexKey(), joined.agentTextStreamSecret().toHexKey())
        }
}

private object AgentTextStreamRolesFixture {
    const val RECEIVE_AND_SEND = 0x03
}
