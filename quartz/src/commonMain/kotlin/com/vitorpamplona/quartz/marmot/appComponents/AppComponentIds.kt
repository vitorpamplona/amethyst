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

/**
 * Marmot app-component ids, from the spec's `foundation/registries.md`.
 *
 * App components are the current profile's carrier for application-owned MLS
 * state: each id owns the opaque bytes stored under it in an
 * `app_data_dictionary`, which can hang off a GroupContext, a LeafNode, a
 * KeyPackage, or a GroupInfo. This replaced MIP-01's single monolithic
 * `marmot_group_data` extension (0xF2EE), whose fields were split across
 * [GROUP_PROFILE_V1], [ADMIN_POLICY_V1], [NOSTR_ROUTING_V1],
 * [GROUP_BLOSSOM_IMAGE_V1] and [MESSAGE_RETENTION_V1].
 *
 * Ids in `0x0000..0x7fff` are assigned by the MLS extensions draft; Marmot's
 * own live in the private-use range `0x8000..0xffff`. A breaking change to a
 * component gets a NEW id, never a version field inside the payload — so an
 * id is a complete statement of a wire format.
 */
object AppComponentIds {
    // ---- upstream, draft-ietf-mls-extensions-10 ----

    /** `app_components`: the supported (LeafNode) or required (GroupContext) id list. */
    const val APP_COMPONENTS = 0x0001

    /** `safe_aad`: component-separated framing for MLS `authenticated_data`. */
    const val SAFE_AAD = 0x0002

    /**
     * `last_resort_key_package`: empty-data marker in a KeyPackage's own
     * dictionary. Note this is a component, NOT an MLS extension type — the
     * MIP-era profile marked last resort with extension `0x000a`, which is now
     * the `self_remove` PROPOSAL type.
     */
    const val LAST_RESORT_KEY_PACKAGE = 0x0004

    // ---- Marmot private range ----

    /** `marmot.group.profile.v1` — name + description. */
    const val GROUP_PROFILE_V1 = 0x8001

    /** `marmot.group.blossom.image.v1` — encrypted group avatar stored on Blossom. */
    const val GROUP_BLOSSOM_IMAGE_V1 = 0x8002

    /** `marmot.group.admin-policy.v1` — the active admin account keys. */
    const val ADMIN_POLICY_V1 = 0x8003

    /** `marmot.transport.nostr.routing.v1` — `nostr_group_id` + the group relay list. */
    const val NOSTR_ROUTING_V1 = 0x8004

    /** `marmot.group.message-retention.v1` — disappearing-message duration. */
    const val MESSAGE_RETENTION_V1 = 0x8005

    /** `marmot.group.agent-text-stream.quic.v1`. */
    const val AGENT_TEXT_STREAM_QUIC_V1 = 0x8006

    /** `marmot.group.avatar-url.v1` — the plain-https alternative to Blossom images. */
    const val GROUP_AVATAR_URL_V1 = 0x8007

    /** `marmot.group.encrypted-media.v1` — frozen; new groups use [GROUP_ENCRYPTED_MEDIA_V2]. */
    const val GROUP_ENCRYPTED_MEDIA_V1 = 0x8008

    /** `marmot.member.account-identity-proof.v2` — leaf-only; see `AccountIdentityProofV2`. */
    const val ACCOUNT_IDENTITY_PROOF_V2 = 0x8009

    /** `marmot.authorization.multi-device-join.v1` — draft. */
    const val MULTI_DEVICE_JOIN_V1 = 0x800a

    /** `marmot.group.encrypted-media.v2`. */
    const val GROUP_ENCRYPTED_MEDIA_V2 = 0x800b

    /** `marmot.group.lifecycle.v1` — active/disbanded. */
    const val GROUP_LIFECYCLE_V1 = 0x800c

    /**
     * The `0x` + four-lowercase-hex-digit rendering used wherever a component
     * id appears as text: the kind-30443 `app_components` tag values, and the
     * `component` tag inside an authorization-proof signing event.
     */
    fun toHex(componentId: Int): String {
        require(componentId in 0..0xffff) { "component id $componentId is out of the uint16 range" }
        val digits = "0123456789abcdef"
        return buildString(6) {
            append("0x")
            append(digits[(componentId shr 12) and 0xf])
            append(digits[(componentId shr 8) and 0xf])
            append(digits[(componentId shr 4) and 0xf])
            append(digits[componentId and 0xf])
        }
    }
}
