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
package com.vitorpamplona.quartz.marmot.mls.components

import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.GroupLifecycleV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.Proposal
import com.vitorpamplona.quartz.marmot.mls.messages.ProposalType
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `app_data_update` proposal (`0x0008`): wire format, and how a commit
 * carrying one changes the GroupContext `app_data_dictionary`.
 *
 * The group-level assertions matter more than they look. The resulting
 * GroupContext feeds the epoch key schedule, so any disagreement with the
 * reference about *which bytes* result — extension position, whether an
 * emptied dictionary keeps its extension, what happens when a commit carries
 * both a GroupContextExtensions and an AppDataUpdate — desynchronizes the
 * group rather than merely looking different.
 */
class AppDataUpdateProposalTest {
    private val creator = "11".repeat(32).hexToByteArray()

    // Real component payloads rather than placeholder bytes: these ids have
    // decoders now, and every commit reads the dictionary to resolve the admin
    // set, so junk under a known id would fail the commit rather than the
    // assertion under test.
    private val profileA = GroupProfileV1("A", "").encode()
    private val profileB = GroupProfileV1("B", "").encode()
    private val lifecycleActive = GroupLifecycleV1.ACTIVE.encode()
    private val adminPolicy = AdminPolicyV1.of(listOf(creator)).encode()

    private fun encode(proposal: Proposal): ByteArray {
        val writer = TlsWriter()
        proposal.encodeTls(writer)
        return writer.toByteArray()
    }

    private fun roundTrip(proposal: Proposal): Proposal = Proposal.decodeTls(TlsReader(encode(proposal)))

    @Test
    fun updateWireFormat() {
        val proposal = Proposal.AppDataUpdate.update(AppComponentIds.GROUP_PROFILE_V1, byteArrayOf(0x0a, 0x0b))
        assertEquals(ProposalType.APP_DATA_UPDATE, proposal.proposalType)

        // 0008     proposal type
        // 8001     component id
        // 01       op = update
        // 02 0a0b  opaque update<V> (QUIC varint length, then the data)
        assertEquals("0008" + "8001" + "01" + "02" + "0a0b", encode(proposal).toHexKey())
    }

    @Test
    fun removeWireFormat() {
        val proposal = Proposal.AppDataUpdate.remove(AppComponentIds.GROUP_LIFECYCLE_V1)
        // 0008 800c 02 — a remove carries no payload at all, not an empty one.
        assertEquals("0008800c02", encode(proposal).toHexKey())
    }

    @Test
    fun bothOperationsRoundTrip() {
        val update = Proposal.AppDataUpdate.update(AppComponentIds.ADMIN_POLICY_V1, ByteArray(40) { it.toByte() })
        assertEquals(update, roundTrip(update))

        val remove = Proposal.AppDataUpdate.remove(AppComponentIds.NOSTR_ROUTING_V1)
        assertEquals(remove, roundTrip(remove))

        val empty = Proposal.AppDataUpdate.update(AppComponentIds.GROUP_PROFILE_V1, ByteArray(0))
        assertEquals(empty, roundTrip(empty))
        assertTrue(
            encode(empty).size < encode(remove).size + 2,
            "an empty update is not the same encoding as a remove",
        )
        assertTrue(!encode(empty).contentEquals(encode(remove)))
    }

    @Test
    fun anUnknownOperationIsRejected() {
        val hostile = "0008800103".hexToByteArray()
        assertFailsWith<IllegalArgumentException> { Proposal.decodeTls(TlsReader(hostile)) }
    }

    // --- group application ----------------------------------------------------

    @Test
    fun aCommitInstallsAndReplacesComponents() {
        val group = MlsGroup.create(creator)
        assertTrue(group.appDataDictionary().isEmpty)

        group.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileA)
        group.proposeAppDataUpdate(AppComponentIds.GROUP_LIFECYCLE_V1, lifecycleActive)
        group.commit()

        val first = group.appDataDictionary()
        assertEquals(
            listOf(AppComponentIds.GROUP_PROFILE_V1, AppComponentIds.GROUP_LIFECYCLE_V1),
            first.componentIds,
        )
        assertContentEquals(profileA, first[AppComponentIds.GROUP_PROFILE_V1])

        // A second update to the same id replaces rather than duplicates.
        group.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileB)
        group.commit()

        val second = group.appDataDictionary()
        assertEquals(first.componentIds, second.componentIds)
        assertContentEquals(profileB, second[AppComponentIds.GROUP_PROFILE_V1])
    }

    @Test
    fun removingTheLastComponentKeepsAnEmptyDictionaryExtension() {
        // Matching openmls: the dictionary extension is added-or-replaced and
        // never dropped. An absent extension and an empty one are different
        // GroupContexts, so dropping it here would fork us from the reference.
        val group = MlsGroup.create(creator)
        group.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileA)
        group.commit()
        assertTrue(group.appDataDictionary().contains(AppComponentIds.GROUP_PROFILE_V1))

        group.proposeAppDataRemoval(AppComponentIds.GROUP_PROFILE_V1)
        group.commit()

        val dictionary = group.appDataDictionary()
        assertTrue(dictionary.isEmpty)
        assertNotNull(
            group.groupContextExtensionsSnapshot().firstOrNull {
                it.extensionType == AppDataDictionary.EXTENSION_TYPE
            },
            "an emptied dictionary keeps its extension",
        )
    }

    @Test
    fun removingAnAbsentComponentIsANoOp() {
        val group = MlsGroup.create(creator)
        group.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileA)
        group.commit()

        group.proposeAppDataRemoval(AppComponentIds.NOSTR_ROUTING_V1)
        group.commit()

        assertEquals(listOf(AppComponentIds.GROUP_PROFILE_V1), group.appDataDictionary().componentIds)
        assertNull(group.appDataDictionary()[AppComponentIds.NOSTR_ROUTING_V1])
    }

    @Test
    fun anAppDataUpdateAppliesOnTopOfAGroupContextExtensionsInTheSameCommit() {
        // openmls applies AppDataUpdate to the extensions a
        // GroupContextExtensions proposal already produced, regardless of the
        // order the two appear in the proposal list. Propose them in the
        // "wrong" order to prove we do not simply follow list order.
        val group = MlsGroup.create(creator)
        group.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileA)
        group.proposeGroupContextExtensions(
            listOf(AppDataDictionary(listOf(ComponentData(AppComponentIds.ADMIN_POLICY_V1, adminPolicy))).toExtension()),
        )
        group.commit()

        val dictionary = group.appDataDictionary()
        assertEquals(
            listOf(AppComponentIds.GROUP_PROFILE_V1, AppComponentIds.ADMIN_POLICY_V1).sorted(),
            dictionary.componentIds,
            "the update lands on the dictionary the GCE installed, not on the pre-commit one",
        )
        assertContentEquals(adminPolicy, dictionary[AppComponentIds.ADMIN_POLICY_V1])
        assertContentEquals(profileA, dictionary[AppComponentIds.GROUP_PROFILE_V1])
    }

    @Test
    fun aSecondMemberConvergesOnTheSameDictionary() {
        // The receiving path is separate code from the committing path, and a
        // divergence between them is a group split rather than a rendering bug.
        val (alice, bob) = twoMemberGroup()

        alice.proposeAppDataUpdate(AppComponentIds.GROUP_PROFILE_V1, profileA)
        alice.proposeAppDataUpdate(AppComponentIds.GROUP_LIFECYCLE_V1, lifecycleActive)
        val commit = alice.commit()
        bob.processFramedCommit(commit.framedCommitBytes)

        assertEquals(alice.epoch, bob.epoch)
        assertEquals(alice.appDataDictionary(), bob.appDataDictionary())
        assertContentEquals(profileA, bob.appDataDictionary()[AppComponentIds.GROUP_PROFILE_V1])

        alice.proposeAppDataRemoval(AppComponentIds.GROUP_PROFILE_V1)
        val removal = alice.commit()
        bob.processFramedCommit(removal.framedCommitBytes)

        assertEquals(alice.appDataDictionary(), bob.appDataDictionary())
        assertNull(bob.appDataDictionary()[AppComponentIds.GROUP_PROFILE_V1])
        assertTrue(bob.appDataDictionary().contains(AppComponentIds.GROUP_LIFECYCLE_V1))
    }

    private fun twoMemberGroup(): Pair<MlsGroup, MlsGroup> {
        val alice = MlsGroup.create(creator)
        val bobBundle = alice.createKeyPackage("22".repeat(32).hexToByteArray(), ByteArray(0))
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)
        return alice to bob
    }
}
