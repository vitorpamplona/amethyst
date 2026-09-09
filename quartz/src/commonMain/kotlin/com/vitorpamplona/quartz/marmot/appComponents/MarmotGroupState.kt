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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamQuicPolicyV1
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentsList
import com.vitorpamplona.quartz.marmot.mls.tree.Extension

/**
 * The current profile's read view of a GroupContext `app_data_dictionary` — the
 * replacement for MIP-01's monolithic `MarmotGroupData` (`0xF2EE`).
 *
 * MIP-01 packed name, description, admins, routing, image and retention into
 * one extension, so any change rewrote the whole blob. Those fields now live in
 * six independently versioned components. This type just reads them together,
 * because most callers want a coherent snapshot of one epoch rather than a
 * component at a time.
 *
 * Every field is nullable, and that is meaningful rather than defensive: a
 * group may legitimately carry no profile, no image, no retention, and — if it
 * predates the component — no lifecycle. Only [adminPolicy] is required of
 * every valid Marmot group, and its absence is a real defect rather than a
 * configuration.
 *
 * ## What this deliberately does not do
 *
 * It does not validate cross-component invariants. The admin/leaf coupling rule
 * ("every key in `admins` names an account with a current member leaf") is a
 * property of the resulting epoch's tree, not of these bytes, so it is checked
 * where the tree is in scope. Nor does it enforce that required components are
 * actually present — that is commit validation, which owns the resulting state.
 */
data class MarmotGroupState(
    /** Component ids this group requires, from the `app_components` entry. */
    val requiredComponents: List<Int>,
    val profile: GroupProfileV1?,
    val adminPolicy: AdminPolicyV1?,
    val routing: NostrRoutingV1?,
    val image: GroupBlossomImageV1?,
    val avatarUrl: GroupAvatarUrlV1?,
    val retention: MessageRetentionV1?,
    val lifecycle: GroupLifecycleV1?,
    val agentTextStream: AgentTextStreamQuicPolicyV1?,
) {
    /**
     * The avatar a renderer should show, honouring the components' documented
     * precedence: "When both are present, the URL avatar wins."
     *
     * A cleared URL avatar (present but empty) is not the same as an absent
     * one — it explicitly falls back to the Blossom image, which is why this
     * checks [GroupAvatarUrlV1.isAbsent] rather than nullness alone.
     */
    val preferredAvatar: MarmotGroupAvatar?
        get() {
            avatarUrl?.takeIf { !it.isAbsent }?.let { return MarmotGroupAvatar.Url(it) }
            return image?.let { MarmotGroupAvatar.Blossom(it) }
        }

    /** True once a disband Commit has been applied. Absorbing and terminal. */
    val isDisbanded: Boolean get() = lifecycle == GroupLifecycleV1.DISBANDED

    /**
     * True when this group is governed by the current profile at all — i.e. it
     * requires the account identity proof component. A group requiring `0xf2f1`
     * instead is a legacy group outside this profile, and one requiring neither
     * is not a valid Marmot group in either.
     */
    val isCurrentProfile: Boolean
        get() = requiredComponents.contains(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2)

    fun requires(componentId: Int): Boolean = requiredComponents.contains(componentId)

    /**
     * Whether [accountIdentity] may commit admin-gated changes in this state.
     *
     * [memberAccounts] must be the MLS-authenticated account identities holding
     * at least one leaf in the SAME state. Being listed in the admin policy is
     * not enough: the account also has to still be in the group.
     */
    fun isActiveAdmin(
        accountIdentity: ByteArray,
        memberAccounts: Collection<ByteArray>,
    ): Boolean = adminPolicy?.isActiveAdmin(accountIdentity, memberAccounts) == true

    companion object {
        /**
         * Read every known component. A component whose bytes fail to decode
         * throws rather than being dropped: invalid signed group state is a
         * defect to surface, not a field to silently treat as absent.
         */
        fun fromDictionary(dictionary: AppDataDictionary): MarmotGroupState =
            MarmotGroupState(
                requiredComponents = ComponentsList.supportedOrRequired(dictionary),
                profile = dictionary[GroupProfileV1.COMPONENT_ID]?.let { GroupProfileV1.decode(it) },
                adminPolicy = dictionary[AdminPolicyV1.COMPONENT_ID]?.let { AdminPolicyV1.decode(it) },
                routing = dictionary[NostrRoutingV1.COMPONENT_ID]?.let { NostrRoutingV1.decode(it) },
                image = dictionary[GroupBlossomImageV1.COMPONENT_ID]?.let { GroupBlossomImageV1.decode(it) },
                avatarUrl = dictionary[GroupAvatarUrlV1.COMPONENT_ID]?.let { GroupAvatarUrlV1.decode(it) },
                retention = dictionary[MessageRetentionV1.COMPONENT_ID]?.let { MessageRetentionV1.decode(it) },
                lifecycle = dictionary[GroupLifecycleV1.COMPONENT_ID]?.let { GroupLifecycleV1.decode(it) },
                agentTextStream =
                    dictionary[AgentTextStreamQuicPolicyV1.COMPONENT_ID]?.let {
                        AgentTextStreamQuicPolicyV1.decode(it)
                    },
            )

        fun fromExtensions(extensions: List<Extension>): MarmotGroupState = fromDictionary(AppDataDictionary.fromExtensionsOrEmpty(extensions))

        /**
         * Build the GroupContext dictionary for a new current-profile group.
         *
         * [requiredComponents] always gains `0x8009`: every current-profile
         * GroupContext must require the account identity proof even though its
         * data is leaf-only and never appears in this dictionary.
         */
        fun buildDictionary(
            adminPolicy: AdminPolicyV1,
            routing: NostrRoutingV1? = null,
            profile: GroupProfileV1? = null,
            image: GroupBlossomImageV1? = null,
            avatarUrl: GroupAvatarUrlV1? = null,
            retention: MessageRetentionV1? = null,
            lifecycle: GroupLifecycleV1? = GroupLifecycleV1.ACTIVE,
            agentTextStream: AgentTextStreamQuicPolicyV1? = null,
            extraRequiredComponents: Collection<Int> = emptyList(),
        ): AppDataDictionary {
            var dictionary = AppDataDictionary.EMPTY

            val required = mutableSetOf(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2, AdminPolicyV1.COMPONENT_ID)
            required.addAll(extraRequiredComponents)

            dictionary = dictionary.with(AdminPolicyV1.COMPONENT_ID, adminPolicy.encode())
            profile?.let {
                required.add(GroupProfileV1.COMPONENT_ID)
                dictionary = dictionary.with(GroupProfileV1.COMPONENT_ID, it.encode())
            }
            routing?.let {
                required.add(NostrRoutingV1.COMPONENT_ID)
                dictionary = dictionary.with(NostrRoutingV1.COMPONENT_ID, it.encode())
            }
            image?.let {
                required.add(GroupBlossomImageV1.COMPONENT_ID)
                dictionary = dictionary.with(GroupBlossomImageV1.COMPONENT_ID, it.encode())
            }
            avatarUrl?.let {
                required.add(GroupAvatarUrlV1.COMPONENT_ID)
                dictionary = dictionary.with(GroupAvatarUrlV1.COMPONENT_ID, it.encode())
            }
            retention?.let {
                required.add(MessageRetentionV1.COMPONENT_ID)
                dictionary = dictionary.with(MessageRetentionV1.COMPONENT_ID, it.encode())
            }
            lifecycle?.let {
                required.add(GroupLifecycleV1.COMPONENT_ID)
                dictionary = dictionary.with(GroupLifecycleV1.COMPONENT_ID, it.encode())
            }
            agentTextStream?.let {
                required.add(AgentTextStreamQuicPolicyV1.COMPONENT_ID)
                dictionary = dictionary.with(AgentTextStreamQuicPolicyV1.COMPONENT_ID, it.encode())
            }

            return dictionary.with(ComponentsList.APP_COMPONENTS_ID, ComponentsList.encode(required))
        }
    }
}

/** Which avatar surface a group's state resolves to, after precedence. */
sealed class MarmotGroupAvatar {
    /** `marmot.group.avatar-url.v1` — a plain https link, no key material. */
    data class Url(
        val avatar: GroupAvatarUrlV1,
    ) : MarmotGroupAvatar()

    /** `marmot.group.blossom.image.v1` — an encrypted blob on a Blossom server. */
    data class Blossom(
        val image: GroupBlossomImageV1,
    ) : MarmotGroupAvatar()
}
