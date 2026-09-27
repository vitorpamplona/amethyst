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
package com.vitorpamplona.quartz.marmot.groups

import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamQuicPolicyV1
import com.vitorpamplona.quartz.marmot.groups.MarmotCapabilities
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.mls.group.GroupView
import com.vitorpamplona.quartz.mls.group.MlsExporterLabel
import com.vitorpamplona.quartz.mls.group.MlsGroupPolicy
import com.vitorpamplona.quartz.mls.group.PendingProposal
import com.vitorpamplona.quartz.mls.messages.Proposal
import com.vitorpamplona.quartz.mls.tree.Capabilities
import com.vitorpamplona.quartz.mls.tree.Extension

/**
 * Marmot's authorization rules (MIP-01 and MIP-03), as an [MlsGroupPolicy].
 *
 * These used to live inside `MlsGroup`, which meant the RFC 9420 engine knew
 * the word "admin" — a concept RFC 9420 does not have. They are unchanged
 * here; only where they run has moved.
 *
 * Stateless, so one instance serves every group.
 */
object MarmotGroupPolicy : MlsGroupPolicy {
    /**
     * The MIP-era leaf set. A current-profile group names
     * [MarmotCapabilities.currentProfileLeaf] explicitly instead: the two
     * profiles differ in what they advertise but share these authorization
     * rules, so the policy carries the older default and the factory that
     * knows it is building a current-profile group overrides it.
     */
    override val defaultLeafCapabilities: Capabilities get() = MarmotCapabilities.mipLeaf()

    override val defaultRequiredCapabilities: Extension get() = MarmotCapabilities.mipRequired()

    /**
     * Marmot's two group-context extension types, exempted from the §13.4
     * all-members-support check.
     *
     * Both are advertised by [MarmotCapabilities.mipLeaf] and by the current
     * profile, so a group built entirely by this client passes the check on
     * capabilities alone. The exemption is for the groups that are already out
     * there, whose older leaves predate the advertisement - dropping it would
     * make this client refuse to apply a GroupContextExtensions proposal in a
     * group it is happily a member of.
     */
    override val knownExtensionTypes: Set<Int> =
        setOf(MarmotCapabilities.MARMOT_GROUP_DATA_EXTENSION_TYPE, AppDataDictionary.EXTENSION_TYPE)

    /**
     * `MLS-Exporter("marmot", "group-event", 32)` — the outer
     * ChaCha20-Poly1305 key for a kind:445 GroupEvent. A commit must be sealed
     * under the PRE-commit epoch so members still at epoch N can open it.
     */
    override val commitExporter: MlsExporterLabel
        get() = MlsExporterLabel("marmot", "group-event".encodeToByteArray(), 32)

    override fun authorizeCommit(
        group: GroupView,
        proposals: List<PendingProposal>,
        committerLeafIndex: Int,
    ) {
        enforceAuthorizedProposalSet(group, proposals, committerLeafIndex)
        enforceNoAdminDepletion(group, proposals)
    }

    override fun authorizeSelfRemove(group: GroupView) {
        check(!isLocalAdmin(group)) {
            "Admin must self-demote via GroupContextExtensions before SelfRemove (MIP-01)"
        }
    }

    override fun validateJoin(group: GroupView) {
        requireAgentTextStreamRoles(group)
    }

    /**
     * The admin set named by [extensions], preferring the current profile.
     *
     * Decodes ONLY the admin policy, never the whole component set. Authorization
     * must not depend on the validity of components it does not read: a
     * malformed group profile is a defect worth surfacing where the profile is
     * used, but it must not make the group un-committable by taking the admin
     * check down with it.
     *
     * Reads whichever profile this group is on: the current profile's
     * `marmot.group.admin-policy.v1` component (`0x8003`) when present,
     * otherwise MIP-01's `admin_pubkeys` field inside `marmot_group_data`
     * (`0xF2EE`). Empty means the group names no admins at all, which happens
     * during bootstrap and in groups that carry neither.
     */
    fun adminIdentitiesIn(extensions: List<Extension>): Set<String> {
        val policyBytes = AppDataDictionary.fromExtensionsOrEmpty(extensions)[AdminPolicyV1.COMPONENT_ID]
        if (policyBytes != null) return AdminPolicyV1.decode(policyBytes).adminHexKeys.toSet()
        return MarmotGroupData
            .fromExtensions(extensions)
            ?.adminPubkeys
            ?.toSet()
            .orEmpty()
    }

    /** True if the member at [leafIndex] is an ACTIVE admin: named in the admin set and still holding a leaf. */
    fun isLeafAdmin(
        group: GroupView,
        leafIndex: Int,
    ): Boolean {
        val id = group.memberIdentityHex(leafIndex) ?: return false
        return id in adminIdentitiesIn(group.extensions)
    }

    /** True if the local member is an active admin. */
    fun isLocalAdmin(group: GroupView): Boolean = isLeafAdmin(group, group.myLeafIndex)

    /**
     * MIP-03: a non-admin may commit only a single self-Update, or a set made
     * entirely of their own SelfRemoves.
     *
     * The "self-only" rule is checked against the committer; when the committer
     * is an admin the rule is skipped entirely so admin-folded inbound proposals
     * (e.g. another member's `SelfRemove` referenced by an admin's GCE commit)
     * are accepted.
     */
    internal fun enforceAuthorizedProposalSet(
        group: GroupView,
        proposals: List<PendingProposal>,
        committerLeafIndex: Int,
    ) {
        if (proposals.isEmpty()) return
        // Reads whichever profile the group is on: the admin-policy component
        // (0x8003) for current-profile groups, `marmot_group_data` (0xF2EE)
        // for legacy ones. An empty set means bootstrap — no admins named yet —
        // and the gate stays open, mirroring MlsGroupManager.updateGroupExtensions.
        val admins = adminIdentitiesIn(group.extensions)
        if (admins.isEmpty() || isLeafAdmin(group, committerLeafIndex)) return

        val allSelfRemove =
            proposals.all { it.proposal is Proposal.SelfRemove && it.senderLeafIndex == committerLeafIndex }
        if (allSelfRemove) return

        val singleSelfUpdate =
            proposals.size == 1 &&
                proposals[0].proposal is Proposal.Update &&
                proposals[0].senderLeafIndex == committerLeafIndex
        if (singleSelfUpdate) return

        throw IllegalStateException(
            "MIP-03: non-admin members may only commit a single self-Update or SelfRemove-only " +
                "proposals; got ${proposals.map { it.proposal::class.simpleName }} from leaf $committerLeafIndex",
        )
    }

    /**
     * Reject any commit that would leave the group without at least one member
     * still listed in `admin_pubkeys` (MIP-03 admin depletion guard).
     *
     * We simulate the post-commit member set and the post-commit `admin_pubkeys`
     * list, then require a non-empty intersection. The guard is only active
     * once the group has a configured admin set — it does not kick in during
     * bootstrap before any admin is named.
     */
    internal fun enforceNoAdminDepletion(
        group: GroupView,
        proposals: List<PendingProposal>,
    ) {
        val currentAdmins = adminIdentitiesIn(group.extensions)
        if (currentAdmins.isEmpty()) return // Bootstrap: no admins yet, nothing to deplete.

        // Resolve the effective admin list after this commit. Three carriers can
        // change it, and they are checked in the order the commit applies them:
        // an AppDataUpdate on 0x8003 (current profile), then a
        // GroupContextExtensions proposal replacing the whole extension list
        // (either profile). AppDataUpdate is resolved last because
        // `applyAppDataUpdateProposals` runs after the rest of the list.
        val gce =
            proposals
                .asSequence()
                .map { it.proposal }
                .filterIsInstance<Proposal.GroupContextExtensions>()
                .lastOrNull()
        val extensionsAfterGce = gce?.extensions ?: group.extensions

        val adminUpdate =
            proposals
                .asSequence()
                .map { it.proposal }
                .filterIsInstance<Proposal.AppDataUpdate>()
                .lastOrNull { it.componentId == AdminPolicyV1.COMPONENT_ID }

        val adminSet =
            when (val operation = adminUpdate?.operation) {
                is Proposal.AppDataUpdate.Operation.Update ->
                    AdminPolicyV1.decode(operation.data).adminHexKeys.toSet()

                // Removing the admin policy is never valid — it is the sole
                // admin authority for the group's lifetime — so an empty set
                // here trips the depletion check below, which is the outcome
                // we want.
                Proposal.AppDataUpdate.Operation.Remove -> emptySet()

                null -> adminIdentitiesIn(extensionsAfterGce)
            }
        check(adminSet.isNotEmpty()) {
            "commit would leave the group with no admins (admin depletion)"
        }

        // Compute which leaves remain after applying Removes/SelfRemoves.
        val removedLeaves = mutableSetOf<Int>()
        for (pending in proposals) {
            when (val p = pending.proposal) {
                is Proposal.Remove -> removedLeaves.add(p.removedLeafIndex)
                is Proposal.SelfRemove -> removedLeaves.add(pending.senderLeafIndex)
                else -> Unit
            }
        }

        val remainingAdminIdentities = mutableSetOf<String>()
        for (i in 0 until group.leafCount) {
            if (i in removedLeaves) continue
            val id = group.memberIdentityHex(i) ?: continue
            if (id in adminSet) remainingAdminIdentities.add(id)
        }

        check(remainingAdminIdentities.isNotEmpty()) {
            "MIP-03: commit would leave the group without any admin members"
        }
    }

    /**
     * Enforce the `0x8006` component's `required_member_roles` mask over
     * the joining tree.
     *
     * A group carrying the agent-text-stream component requires each named
     * role as an MLS leaf capability (`0xF2D1` receive, `0xF2D2` send,
     * `0xF2D4` fanout). Advertising the component id alone is not enough —
     * that only says "understands the component"; the role capability says
     * "can actually do this".
     */
    private fun requireAgentTextStreamRoles(group: GroupView) {
        val policy =
            AppDataDictionary
                .fromExtensionsOrEmpty(group.extensions)[AgentTextStreamQuicPolicyV1.COMPONENT_ID]
                ?.let { AgentTextStreamQuicPolicyV1.decode(it) } ?: return
        val required = policy.requiredRoleCapabilities()
        if (required.isEmpty()) return

        val myLeaf = group.leafCapabilities(group.myLeafIndex)
        requireNotNull(myLeaf) { "Joiner's leaf is blank after tree reconstruction" }
        val missing = required.filterNot { myLeaf.extensions.contains(it) }
        require(missing.isEmpty()) {
            "Joiner does not advertise agent text stream roles this group requires: " +
                missing.joinToString { AppComponentIds.toHex(it) }
        }
    }
}
