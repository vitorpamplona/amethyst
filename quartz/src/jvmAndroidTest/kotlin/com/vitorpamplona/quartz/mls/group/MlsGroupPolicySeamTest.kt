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
package com.vitorpamplona.quartz.mls.group

import com.vitorpamplona.quartz.mls.tree.Capabilities
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [MlsGroupPolicy] seam itself, with no binding involved.
 *
 * The engine used to carry Marmot's authorization rules and capability
 * defaults inline, so a plain RFC 9420 group could not be created at all —
 * every group came out requiring `marmot_group_data`. These tests pin the two
 * halves of the fix: the default really is RFC 9420 as written, and a policy
 * that refuses really is consulted rather than advisory.
 */
class MlsGroupPolicySeamTest {
    private val alice = "alice".encodeToByteArray()

    /** Records what the engine asked, and refuses on demand. */
    private class RecordingPolicy(
        val refuseCommit: Boolean = false,
        val refuseSelfRemove: Boolean = false,
        override val commitExporter: MlsExporterLabel? = null,
    ) : MlsGroupPolicy {
        var commitsSeen = 0
        var selfRemovesSeen = 0
        var lastCommitter: Int? = null

        override fun authorizeCommit(
            group: GroupView,
            proposals: List<PendingProposal>,
            committerLeafIndex: Int,
        ) {
            commitsSeen++
            lastCommitter = committerLeafIndex
            if (refuseCommit) throw IllegalStateException("refused by policy")
        }

        override fun authorizeSelfRemove(group: GroupView) {
            selfRemovesSeen++
            if (refuseSelfRemove) throw IllegalStateException("no leaving")
        }
    }

    @Test
    fun aDefaultGroupRequiresNothingBeyondRfc9420() {
        val group = MlsGroup.create(alice)

        assertFalse(
            group.extensions.any { it.extensionType == MlsGroup.REQUIRED_CAPABILITIES_EXTENSION_TYPE },
            "the default profile must not install required_capabilities — that was Marmot's, not RFC 9420's",
        )
        assertEquals(
            Capabilities(),
            MlsGroupPolicy.Permissive.defaultLeafCapabilities,
            "RFC 9420 §7.2 forbids advertising DEFAULT types, so the neutral leaf advertises nothing",
        )
    }

    @Test
    fun authorizeCommitIsConsultedWithTheLocalCommitter() {
        val policy = RecordingPolicy()
        val group = MlsGroup.create(alice, policy = policy)

        group.proposeGroupContextExtensions(group.extensions)
        group.commit()

        assertEquals(1, policy.commitsSeen)
        assertEquals(group.leafIndex, policy.lastCommitter)
    }

    @Test
    fun aRefusedCommitDoesNotAdvanceTheEpoch() {
        val policy = RecordingPolicy(refuseCommit = true)
        val group = MlsGroup.create(alice, policy = policy)
        val epochBefore = group.epoch

        group.proposeGroupContextExtensions(group.extensions)
        assertFailsWith<IllegalStateException> { group.commit() }

        assertEquals(
            epochBefore,
            group.epoch,
            "a policy refusal must abort before any mutation, or the group diverges from every peer",
        )
    }

    @Test
    fun authorizeSelfRemoveGatesTheProposalAtItsSender() {
        val allowed = RecordingPolicy()
        MlsGroup.create(alice, policy = allowed).proposeSelfRemove()
        assertEquals(1, allowed.selfRemovesSeen)

        val refused = RecordingPolicy(refuseSelfRemove = true)
        val group = MlsGroup.create(alice, policy = refused)
        assertFailsWith<IllegalStateException> { group.proposeSelfRemove() }
        assertTrue(group.pendingProposalsSnapshot().isEmpty(), "a refused proposal must not be staged")
    }

    @Test
    fun theCommitExporterSecretComesFromThePolicy() {
        val none = MlsGroup.create(alice, policy = RecordingPolicy())
        none.proposeGroupContextExtensions(none.extensions)
        assertEquals(
            0,
            none.commit().preCommitExporterSecret.size,
            "a binding that seals nothing outside MLS gets no secret",
        )

        val label = MlsExporterLabel("myapp", "group-event".encodeToByteArray(), 32)
        val bound = MlsGroup.create(alice, policy = RecordingPolicy(commitExporter = label))
        val expected = bound.exporterSecret(label.label, label.context, label.length)
        bound.proposeGroupContextExtensions(bound.extensions)

        assertContentEquals(
            expected,
            bound.commit().preCommitExporterSecret,
            "the engine must derive it under the policy's label, not one of its own",
        )
    }
}
