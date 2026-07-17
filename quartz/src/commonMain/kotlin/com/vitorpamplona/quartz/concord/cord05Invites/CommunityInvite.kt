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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord02Community.LenientImagePointerSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A channel grant carried in an invite: its id, delivered [key], [epoch], and [name].
 *
 * [key] defaults to empty: a public channel (e.g. an unencrypted `general`) carries no
 * delivered grant key, and some reference clients omit the field entirely. Treat an empty
 * [key] as "no grant" rather than rejecting the whole bundle.
 */
@Serializable
class InviteChannel(
    val id: String,
    val key: String = "",
    val epoch: Long,
    val name: String = "",
)

/**
 * The contents of a Concord invite (CORD-05) — everything a joiner needs to
 * become a member: the self-certifying [communityId] with its [owner]/[ownerSalt]
 * proof, the access [communityRoot] at [rootEpoch], per-[channels] grants,
 * bootstrap [relays], display [name]/[icon], optional [expiresAt] and creator
 * attribution.
 *
 * Field names are pinned to the Concord v2 reference client (snake_case on the
 * wire) so bundles interoperate. This object is JSON-serialized and encrypted —
 * into a kind-33301 bundle (link invites) or a NIP-59 giftwrap (direct invites).
 */
@Serializable
class CommunityInvite(
    @SerialName("community_id") val communityId: String,
    val owner: String,
    @SerialName("owner_salt") val ownerSalt: String,
    @SerialName("community_root") val communityRoot: String,
    @SerialName("root_epoch") val rootEpoch: Long = 0,
    val channels: List<InviteChannel> = emptyList(),
    val relays: List<String> = emptyList(),
    val name: String = "",
    @Serializable(with = LenientImagePointerSerializer::class) val icon: ImagePointer? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("creator_npub") val creatorNpub: String? = null,
    val label: String? = null,
)
