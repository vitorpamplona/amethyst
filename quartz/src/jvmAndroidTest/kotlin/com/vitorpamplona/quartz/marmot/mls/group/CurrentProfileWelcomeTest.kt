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
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRoles
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519KeyPair
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
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
     * advertise, and a group that requires one we do not advertise has to be
     * refused at join — joining anyway lands us in a group where peers reject
     * every commit we make.
     *
     * The gate is tested against a deliberately reduced leaf rather than
     * against our own KeyPackage, because our own now advertises all three
     * defined roles (see [aJoinerFillsEveryRoleTheProfileDefines]) and so
     * cannot fail this check. Testing "we refuse what we cannot fill" through
     * our own capability set would silently stop testing anything the moment
     * that set changed — which is exactly what happened here.
     */
    @Test
    fun aJoinerRefusesAGroupWhoseStreamRolesItsLeafDoesNotAdvertise() =
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
            val invitee = receiveOnlyKeyPackage(signer(0x44))
            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val welcome = assertNotNull(group.commit().welcomeBytes)

            val failure = assertFailsWith<IllegalArgumentException> { MlsGroup.processWelcome(welcome, invitee) }
            assertTrue(
                failure.message.orEmpty().contains("agent text stream roles"),
                "expected a role-capability refusal, got: ${failure.message}",
            )
        }

    /**
     * Our published leaf advertises `receive`, `send` AND `fanout` — the same
     * set MDK puts on every KeyPackage — so a group that requires any of them
     * admits us.
     */
    @Test
    fun aLeafAdvertisingEveryRoleFillsAGroupThatRequiresThem() =
        runBlocking<Unit> {
            val group =
                aGroup(
                    AgentTextStreamQuicPolicyV1(
                        requiredMemberRoles = AgentTextStreamRoles.MASK,
                        allowedMemberRoles = AgentTextStreamRoles.MASK,
                        maxPlaintextFrameLen = 4096,
                        replayTtlSecs = 0,
                        paddingBucketBytes = 0,
                    ),
                )
            val invitee = allRolesKeyPackage(signer(0x66))
            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val welcome = assertNotNull(group.commit().welcomeBytes)

            val joined = MlsGroup.processWelcome(welcome, invitee)
            assertEquals(nostrGroupId.toHexKey(), joined.currentNostrGroupId())
        }

    /**
     * Our published KeyPackage advertises NO agent-stream role, and is
     * therefore refused by a group that requires one.
     *
     * That refusal is the deliberate cost of not advertising, so it is asserted
     * rather than discovered: the implementation is still here and still
     * tested, but a capability is a standing promise to every peer that reads
     * the KeyPackage, and we do not make one for a path nothing uses. If this
     * test starts failing because the default advertises a role again, that is
     * a decision to take on purpose, not a drift to absorb.
     */
    @Test
    fun ourDefaultLeafAdvertisesNoStreamRoleAndIsRefusedByAGroupThatNeedsOne() =
        runBlocking<Unit> {
            val group = aGroup(AgentTextStreamQuicPolicyV1.userToAgentDefault())
            val invitee = CurrentProfileGroupFactory.createKeyPackage(signer(0x77))

            assertTrue(
                invitee.keyPackage.leafNode.capabilities.extensions
                    .none { it in AgentTextStreamRoles.ALL_CAPABILITIES },
                "the default leaf must carry no agent-stream role, got ${invitee.keyPackage.leafNode.capabilities.extensions}",
            )

            group.proposeAdd(invitee.keyPackage.toTlsBytes())
            val welcome = assertNotNull(group.commit().welcomeBytes)
            val failure = assertFailsWith<IllegalArgumentException> { MlsGroup.processWelcome(welcome, invitee) }
            assertTrue(
                failure.message.orEmpty().contains("agent text stream roles"),
                "expected a role-capability refusal, got: ${failure.message}",
            )
        }

    /** A current-profile leaf carrying the `receive` role and nothing beyond it. */
    private suspend fun receiveOnlyKeyPackage(signer: NostrSignerInternal): KeyPackageBundle = keyPackageAdvertising(signer, listOf(AgentTextStreamRoles.RECEIVE_CAPABILITY))

    /** A current-profile leaf carrying every role the profile defines. */
    private suspend fun allRolesKeyPackage(signer: NostrSignerInternal): KeyPackageBundle =
        keyPackageAdvertising(
            signer,
            listOf(
                AgentTextStreamRoles.RECEIVE_CAPABILITY,
                AgentTextStreamRoles.SEND_CAPABILITY,
                AgentTextStreamRoles.FANOUT_CAPABILITY,
            ),
        )

    /**
     * A current-profile KeyPackage whose leaf advertises exactly [roles] on top
     * of the default capability set.
     *
     * The default set no longer carries any agent-stream role, so these tests
     * build the leaf they need instead of relying on it. That is the right
     * shape regardless: a test that asserted the gate through OUR default was
     * really asserting the default, and stopped testing the gate the moment the
     * default changed — which is exactly what happened.
     */
    private suspend fun keyPackageAdvertising(
        signer: NostrSignerInternal,
        roles: List<Int>,
    ): KeyPackageBundle {
        val full = CurrentProfileGroupFactory.createKeyPackage(signer)
        val reduced =
            MlsGroup.currentProfileLeafCapabilities().let {
                Capabilities(
                    extensions = it.extensions + roles,
                    proposals = it.proposals,
                )
            }
        val identity = signer.pubKey.hexToByteArray()
        // The SAME signature keypair: the leaf's account identity proof covers
        // its own signature key, so a fresh one would fail proof validation
        // before the role gate is ever reached and the test would pass for the
        // wrong reason.
        val leafKeys =
            Ed25519KeyPair(
                privateKey = full.signaturePrivateKey,
                publicKey = Ed25519.publicFromPrivate(full.signaturePrivateKey),
            )
        return MlsGroup
            .create(identity)
            .createKeyPackage(
                identity = identity,
                signingKey = full.signaturePrivateKey,
                leafSignatureKeyPair = leafKeys,
                leafExtensions = full.keyPackage.leafNode.extensions,
                capabilities = reduced,
                keyPackageExtensions = full.keyPackage.extensions,
            )
    }

    @Test
    fun aLeafAdvertisingReceiveJoinsAGroupRequiringOnlyThatRole() =
        runBlocking<Unit> {
            val group = aGroup(AgentTextStreamQuicPolicyV1.userToAgentDefault())
            val invitee = receiveOnlyKeyPackage(signer(0x55))
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
